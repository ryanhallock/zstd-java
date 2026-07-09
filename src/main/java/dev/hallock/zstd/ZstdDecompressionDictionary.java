package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;
import dev.hallock.zstd.bindings.ZstdCritical;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A digested decompression dictionary, ready to start decompression operations without startup
 * delay.
 *
 * <p><strong>Thread Safety:</strong> A digested dictionary is immutable after creation and, per the
 * native library's contract, may be referenced and used by many contexts across many threads
 * concurrently. {@link #close()} is also safe to call from any thread.
 *
 * <p><strong>Resource Lifecycle:</strong> This class implements {@link AutoCloseable} and holds
 * native memory allocated by the Zstd library. It must be explicitly closed via {@link #close()};
 * abandoning an instance without closing it leaks its native memory permanently, as there is
 * deliberately no automatic cleanup.
 */
public final class ZstdDecompressionDictionary implements AutoCloseable {
  private final MemorySegment ddict;
  private final long dictSize;

  // (context references << 1) | open bit; ZSTD_freeDDict runs exactly when the state reaches 0.
  private final AtomicInteger state = new AtomicInteger(1);

  ZstdDecompressionDictionary(MemorySegment dict) {
    this(dict, dict.byteSize());
  }

  ZstdDecompressionDictionary(MemorySegment dict, long dictSize) {
    Objects.requireNonNull(dict, "dict");
    Zstd.requireNative(dict, "dict");
    if (dictSize < 0) throw new IllegalArgumentException("dictSize cannot be negative");
    if (dictSize > dict.byteSize())
      throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
    MemorySegment ddict = ZSTD_h.ZSTD_createDDict(dict, dictSize);
    if (ddict.address() == 0) {
      throw new OutOfMemoryError("Failed to allocate native decompression dictionary");
    }
    this.ddict = ddict;
    this.dictSize = dictSize;
  }

  /** Takes a context reference and returns the native DDict pointer. */
  MemorySegment acquire() {
    for (int current = state.get(); (current & 1) != 0; current = state.get()) {
      if (state.compareAndSet(current, current + 2)) {
        return ddict;
      }
    }
    throw new IllegalStateException("Decompression dictionary is closed");
  }

  /** Drops a context reference; frees the native DDict when the state reaches 0. */
  void release() {
    if (state.addAndGet(-2) == 0) {
      freeNative();
    }
  }

  private void freeNative() {
    long result = ZSTD_h.ZSTD_freeDDict(ddict);
    if (ZstdCritical.isError(result)) {
      throw new ZstdException(result);
    }
  }

  /**
   * Returns the size of the dictionary content in bytes.
   *
   * @return the dictionary size
   */
  public long size() {
    return dictSize;
  }

  /**
   * Returns the size of the compiled dictionary in bytes, including internal data structures.
   *
   * @return the size in bytes
   * @throws IllegalStateException if the dictionary has been closed
   */
  public long sizeOf() {
    MemorySegment d = acquire();
    try {
      return ZSTD_h.ZSTD_sizeof_DDict(d);
    } finally {
      release();
    }
  }

  /**
   * Returns the unique dictionary ID.
   *
   * @return the dictionary ID, or 0 for a raw-content dictionary
   * @throws IllegalStateException if the dictionary has been closed
   */
  public int dictId() {
    MemorySegment d = acquire();
    try {
      return ZSTD_h.ZSTD_getDictID_fromDDict(d);
    } finally {
      release();
    }
  }

  /**
   * Closes this dictionary. Subsequent calls have no effect, other operations throw {@link
   * IllegalStateException} after closure, and contexts can no longer reference the dictionary.
   *
   * <p>Native resources are released once no context references the dictionary any more: closing a
   * dictionary that is still referenced defers the release until the last referencing context
   * replaces the reference, is reset, or is closed. It is therefore always safe to close a
   * dictionary, even while contexts on other threads are using it.
   *
   * @throws ZstdException if a native error occurs during dictionary destruction
   */
  @Override
  public void close() {
    for (int current = state.get(); (current & 1) != 0; current = state.get()) {
      if (state.compareAndSet(current, current - 1)) {
        if (current == 1) {
          freeNative();
        }
        return;
      }
    }
  }
}
