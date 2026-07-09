package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;
import dev.hallock.zstd.bindings.ZstdCritical;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * A reusable Zstd decompression context.
 *
 * <p><strong>Thread Safety:</strong> Instances of this class are <strong>not thread safe</strong>
 * for concurrent operations; invoking an operation while another operation is in flight on a
 * different thread throws {@link IllegalStateException}. As the single exception, {@link #close()}
 * may be invoked from any thread: it blocks until an in flight operation on another thread
 * completes before releasing native resources.
 *
 * <p><strong>Resource Lifecycle:</strong> This class implements {@link AutoCloseable} and holds
 * native memory allocated by the Zstd library. It must be explicitly closed via {@link #close()} to
 * prevent resource leaks.
 *
 * <p><strong>Native segments:</strong> every {@link MemorySegment} passed to this API must be a
 * native (off-heap) segment; see {@link Zstd}.
 */
public final class ZstdDecompressionContext implements AutoCloseable {
  private final MemorySegment dctx;

  // Operations tryLock() and fail fast on concurrent use; close() locks blockingly to drain
  // an in flight operation before freeing the dctx.
  private final ReentrantLock lock = new ReentrantLock();
  private boolean closed;
  private @Nullable ZstdDecompressionDictionary refDict;

  ZstdDecompressionContext() {
    MemorySegment dctx = ZSTD_h.ZSTD_createDCtx();
    if (dctx.address() == 0) {
      throw new OutOfMemoryError("Failed to allocate native decompression context");
    }
    this.dctx = dctx;
    super();
  }

  /**
   * Decompresses raw data from a source MemorySegment into a destination MemorySegment.
   *
   * @param dst the destination segment where decompressed data is written (native segment)
   * @param dstCapacity the capacity limit of the destination segment
   * @param src the source segment containing compressed data (native segment)
   * @param srcSize the size of the compressed data in the source segment
   * @return the decompressed size
   * @throws ZstdException if a native decompression error occurs
   */
  public long decompress(MemorySegment dst, long dstCapacity, MemorySegment src, long srcSize) {
    Objects.requireNonNull(dst, "dst");
    Zstd.requireNative(dst, "dst");
    if (dstCapacity < 0) throw new IllegalArgumentException("dstCapacity cannot be negative");
    if (dstCapacity > dst.byteSize())
      throw new IllegalArgumentException("dstCapacity cannot be larger than dst.byteSize()");
    Objects.requireNonNull(src, "src");
    Zstd.requireNative(src, "src");
    if (srcSize < 0) throw new IllegalArgumentException("srcSize cannot be negative");
    if (srcSize > src.byteSize())
      throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
    lockOrThrow();
    try {
      ensureOpen();
      long result = ZSTD_h.ZSTD_decompressDCtx(this.dctx, dst, dstCapacity, src, srcSize);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Performs a single streaming decompression step on the input and output buffers.
   *
   * <p>The return value is {@code 0} exactly when a frame is fully decoded <em>and</em> fully
   * flushed to the output buffer. Any other value means more work remains (more input is needed, or
   * the output buffer is too full to make progress) and doubles as a hint for the preferred size of
   * the next input chunk. A nonzero return after the input is exhausted therefore indicates a
   * truncated frame; it is not an error by itself, but no more progress can be made without further
   * input.
   *
   * <p>If a streaming operation fails, the context is stuck mid-frame; call {@link
   * #reset(ZstdResetDirective)} with {@link ZstdResetDirective#SESSION_ONLY} before reusing it.
   *
   * <p>The scopes backing the buffers' source and destination segments must remain alive for the
   * duration of this call. Closed scopes are detected on a best-effort basis and reported as {@link
   * IllegalStateException}; a scope closed concurrently mid-call cannot be detected.
   *
   * @param outBuf the destination output buffer state
   * @param inBuf the source input buffer state
   * @return {@code 0} when a frame is fully decoded and flushed; otherwise a suggested next input
   *     size
   * @throws ZstdException if a native streaming decompression error occurs
   */
  public long decompressStream(ZstdOutputBuffer outBuf, ZstdInputBuffer inBuf) {
    Objects.requireNonNull(outBuf, "outBuf");
    Objects.requireNonNull(inBuf, "inBuf");
    lockOrThrow();
    try {
      ensureOpen();
      // Best-effort: the struct segments hold raw addresses of these segments, which the Linker
      // cannot validate. A scope closed concurrently after this check is not detectable.
      if (!inBuf.source().scope().isAlive()) {
        throw new IllegalStateException("input source segment's backing scope is closed");
      }
      if (!outBuf.destination().scope().isAlive()) {
        throw new IllegalStateException("output destination segment's backing scope is closed");
      }
      long result;
      try {
        result = ZSTD_h.ZSTD_decompressStream(this.dctx, outBuf.segment(), inBuf.segment());
      } finally {
        // Keep the buffers (and transitively their source/destination segments, including any
        // automatic arenas) reachable for the whole native call.
        Reference.reachabilityFence(inBuf);
        Reference.reachabilityFence(outBuf);
      }
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Loads a dictionary into the decompression context. The content is copied into the context, so
   * {@code dict} does not need to remain alive after this call. Replaces any previously loaded or
   * referenced dictionary or prefix.
   *
   * @param dict the dictionary content memory segment (native segment)
   * @param dictSize the size of the dictionary content
   * @throws ZstdException if a native error occurs while loading the dictionary
   */
  public void loadDictionary(MemorySegment dict, long dictSize) {
    Objects.requireNonNull(dict, "dict");
    Zstd.requireNative(dict, "dict");
    if (dictSize < 0) throw new IllegalArgumentException("dictSize cannot be negative");
    if (dictSize > dict.byteSize())
      throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
    lockOrThrow();
    try {
      ensureOpen();
      long result = ZSTD_h.ZSTD_DCtx_loadDictionary(this.dctx, dict, dictSize);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      releaseDictionary();
    } finally {
      lock.unlock();
    }
  }

  /**
   * References a precompiled decompression dictionary in the decompression context, replacing any
   * previously loaded or referenced dictionary or prefix.
   *
   * <p>The context retains the dictionary until the reference is replaced, dropped by a parameter
   * reset, or the context is closed; closing the dictionary while it is referenced defers the
   * release of its native resources until the last referencing context lets go.
   *
   * @param dict the precompiled decompression dictionary to reference
   * @throws ZstdException if a native error occurs while referencing the dictionary
   * @throws IllegalStateException if the dictionary has already been closed
   */
  public void refDictionary(ZstdDecompressionDictionary dict) {
    Objects.requireNonNull(dict, "dict");
    lockOrThrow();
    try {
      ensureOpen();
      MemorySegment ddict = dict.acquire();
      long result = ZSTD_h.ZSTD_DCtx_refDDict(this.dctx, ddict);
      if (ZstdCritical.isError(result)) {
        dict.release();
        throw new ZstdException(result);
      }
      releaseDictionary();
      this.refDict = dict;
    } finally {
      lock.unlock();
    }
  }

  /**
   * References a prefix buffer in the decompression context, replacing any previously loaded or
   * referenced dictionary or prefix. The prefix is used only for the next frame and must match the
   * prefix used at compression time. The segment's scope must remain alive until decompression of
   * that frame completes.
   *
   * @param prefix the prefix history segment (native segment)
   * @param prefixSize the size of the prefix history
   * @throws ZstdException if a native error occurs while referencing the prefix
   */
  public void refPrefix(MemorySegment prefix, long prefixSize) {
    Objects.requireNonNull(prefix, "prefix");
    Zstd.requireNative(prefix, "prefix");
    if (prefixSize < 0) throw new IllegalArgumentException("prefixSize cannot be negative");
    if (prefixSize > prefix.byteSize())
      throw new IllegalArgumentException("prefixSize cannot be larger than prefix.byteSize()");
    lockOrThrow();
    try {
      ensureOpen();
      long result = ZSTD_h.ZSTD_DCtx_refPrefix(this.dctx, prefix, prefixSize);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      releaseDictionary();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Configures a parameter option in the decompression context.
   *
   * @param parameter the decompression parameter to configure
   * @param value the configuration value (must be within supported parameter bounds)
   * @throws ZstdException if a native error occurs or value is out of bounds
   */
  public void parameter(ZstdDecompressionParameter parameter, int value) {
    Objects.requireNonNull(parameter, "parameter");
    lockOrThrow();
    try {
      ensureOpen();
      long result = ZSTD_h.ZSTD_DCtx_setParameter(this.dctx, parameter.value(), value);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Resets this context for reuse. See {@link ZstdResetDirective} for the semantics of each
   * directive; {@link ZstdResetDirective#SESSION_ONLY} is the required recovery path after a failed
   * or abandoned streaming operation.
   *
   * @param directive the reset directive
   * @throws ZstdException if a native error occurs (for example, resetting parameters while a
   *     session is active)
   */
  public void reset(ZstdResetDirective directive) {
    Objects.requireNonNull(directive, "directive");
    lockOrThrow();
    try {
      ensureOpen();
      long result = ZSTD_h.ZSTD_DCtx_reset(this.dctx, directive.value());
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      if (directive != ZstdResetDirective.SESSION_ONLY) {
        releaseDictionary();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Frees resources allocated by this decompression context. Subsequent calls have no effect. Other
   * operations throw {@link IllegalStateException} after closure. May be invoked from any thread;
   * blocks until an in flight operation completes.
   *
   * @throws ZstdException if a native error occurs during context destruction
   */
  @Override
  public void close() {
    lock.lock();
    try {
      if (closed) return;
      closed = true;
      // Free the dctx before releaseDictionary(): the referenced DDict must outlive the dctx.
      long result = ZSTD_h.ZSTD_freeDCtx(this.dctx);
      releaseDictionary();
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
    } finally {
      lock.unlock();
    }
  }

  private void releaseDictionary() {
    ZstdDecompressionDictionary previous = this.refDict;
    if (previous != null) {
      this.refDict = null;
      previous.release();
    }
  }

  private void lockOrThrow() {
    if (!lock.tryLock()) {
      throw new IllegalStateException("Decompression context is in use by another thread");
    }
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("Decompression context is closed");
  }
}
