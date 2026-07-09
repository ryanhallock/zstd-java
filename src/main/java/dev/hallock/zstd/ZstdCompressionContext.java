package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;
import dev.hallock.zstd.bindings.ZstdCritical;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * A reusable Zstd compression context.
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
public final class ZstdCompressionContext implements AutoCloseable {
  private final MemorySegment cctx;

  // Operations tryLock() and fail fast on concurrent use; close() locks blockingly to drain
  // an in flight operation before freeing the cctx.
  private final ReentrantLock lock = new ReentrantLock();
  private boolean closed;
  private @Nullable ZstdCompressionDictionary refDict;

  ZstdCompressionContext() {
    MemorySegment cctx = ZSTD_h.ZSTD_createCCtx();
    if (cctx.address() == 0) {
      throw new OutOfMemoryError("Failed to allocate native compression context");
    }
    this.cctx = cctx;
    super();
  }

  /**
   * Compresses raw data from a source MemorySegment into a destination MemorySegment.
   *
   * @param dst the destination segment where compressed data is written (native segment)
   * @param dstCapacity the capacity limit of the destination segment
   * @param src the source segment containing raw data (native segment)
   * @param srcSize the size of the raw data in the source segment
   * @return the compressed size
   * @throws ZstdException if a native compression error occurs
   */
  public long compress(MemorySegment dst, long dstCapacity, MemorySegment src, long srcSize) {
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
      long result = ZSTD_h.ZSTD_compress2(this.cctx, dst, dstCapacity, src, srcSize);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Performs a single streaming compression step on the input and output buffers.
   *
   * <p>The return value is the number of bytes still buffered inside the native context. With
   * {@link ZstdEndDirective#FLUSH} or {@link ZstdEndDirective#END}, call this method repeatedly
   * (providing fresh output space each time) until it returns {@code 0}; stopping earlier leaves
   * the flush incomplete or the frame truncated. With {@link ZstdEndDirective#CONTINUE} the return
   * value is only a hint and may be ignored.
   *
   * <p>If a streaming operation fails, the context is stuck mid-frame; call {@link
   * #reset(ZstdResetDirective)} with {@link ZstdResetDirective#SESSION_ONLY} before reusing it.
   *
   * <p>The scopes backing the buffers' source and destination segments must remain alive for the
   * duration of this call. Closed scopes are detected on a best-effort basis and reported as {@link
   * IllegalStateException}; a scope closed concurrently mid-call cannot be detected.
   *
   * @param output the destination output buffer state
   * @param input the source input buffer state
   * @param endDirective the directive indicating whether to continue, flush, or end the frame
   * @return the number of bytes still buffered in the context; {@code 0} means the flush or frame
   *     completed
   * @throws ZstdException if a native streaming compression error occurs
   */
  public long compressStream(
      ZstdOutputBuffer output, ZstdInputBuffer input, ZstdEndDirective endDirective) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(endDirective, "endDirective");
    lockOrThrow();
    try {
      ensureOpen();
      // Best-effort: the struct segments hold raw addresses of these segments, which the Linker
      // cannot validate. A scope closed concurrently after this check is not detectable.
      if (!input.source().scope().isAlive()) {
        throw new IllegalStateException("input source segment's backing scope is closed");
      }
      if (!output.destination().scope().isAlive()) {
        throw new IllegalStateException("output destination segment's backing scope is closed");
      }
      long result;
      try {
        result =
            ZSTD_h.ZSTD_compressStream2(
                this.cctx, output.segment(), input.segment(), endDirective.value());
      } finally {
        // Keep the buffers (and transitively their source/destination segments, including any
        // automatic arenas) reachable for the whole native call.
        Reference.reachabilityFence(input);
        Reference.reachabilityFence(output);
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
   * Loads a dictionary into the compression context. The content is copied into the context, so
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
      long result = ZSTD_h.ZSTD_CCtx_loadDictionary(this.cctx, dict, dictSize);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      releaseDictionary();
    } finally {
      lock.unlock();
    }
  }

  /**
   * References a precompiled compression dictionary in the compression context, replacing any
   * previously loaded or referenced dictionary or prefix.
   *
   * <p>The context retains the dictionary until the reference is replaced, dropped by a parameter
   * reset, or the context is closed; closing the dictionary while it is referenced defers the
   * release of its native resources until the last referencing context lets go.
   *
   * @param dict the precompiled compression dictionary to reference
   * @throws ZstdException if a native error occurs while referencing the dictionary
   * @throws IllegalStateException if the dictionary has already been closed
   */
  public void refDictionary(ZstdCompressionDictionary dict) {
    Objects.requireNonNull(dict, "dict");
    lockOrThrow();
    try {
      ensureOpen();
      MemorySegment cdict = dict.acquire();
      long result = ZSTD_h.ZSTD_CCtx_refCDict(this.cctx, cdict);
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
   * References a prefix buffer in the compression context, replacing any previously loaded or
   * referenced dictionary or prefix. The prefix is used only for the next frame. The segment's
   * scope must remain alive until compression of that frame completes.
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
      long result = ZSTD_h.ZSTD_CCtx_refPrefix(this.cctx, prefix, prefixSize);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
      releaseDictionary();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Configures a parameter option in the compression context.
   *
   * @param parameter the compression parameter to configure
   * @param value the configuration value (must be within supported parameter bounds)
   * @throws ZstdException if a native error occurs or value is out of bounds
   */
  public void parameter(ZstdCompressionParameter parameter, int value) {
    Objects.requireNonNull(parameter, "parameter");
    lockOrThrow();
    try {
      ensureOpen();
      long result = ZSTD_h.ZSTD_CCtx_setParameter(this.cctx, parameter.value(), value);
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Configures the compression strategy, a typed convenience for setting {@link
   * ZstdCompressionParameter#STRATEGY}. See {@link ZstdStrategy} for the speed/ratio ordering of
   * the strategies.
   *
   * @param strategy the compression strategy to use
   * @throws ZstdException if a native error occurs
   */
  public void strategy(ZstdStrategy strategy) {
    Objects.requireNonNull(strategy, "strategy");
    lockOrThrow();
    try {
      ensureOpen();
      long result =
          ZSTD_h.ZSTD_CCtx_setParameter(
              this.cctx, ZstdCompressionParameter.STRATEGY.value(), strategy.value());
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Pledges the total source size of a compression stream upfront. The pledged size is written to
   * the frame header (making {@link Zstd#frameContentSize(MemorySegment, long)} work on the result)
   * and validated: compressing a different total amount fails with {@code srcSize_wrong}.
   *
   * <p>Pledging {@code 0} declares an empty source. Pledging {@code -1} (equivalent to {@code
   * ZSTD_CONTENTSIZE_UNKNOWN}; see {@link Zstd#CONTENT_SIZE_UNKNOWN}) resets the pledge to
   * "unknown", the default for a new session.
   *
   * @param srcSize the total uncompressed source size, or {@code -1} to reset the pledge to unknown
   * @throws ZstdException if a native error occurs
   */
  public void pledgedSrcSize(long srcSize) {
    if (srcSize < -1) throw new IllegalArgumentException("srcSize must be at least -1");
    lockOrThrow();
    try {
      ensureOpen();
      long result = ZSTD_h.ZSTD_CCtx_setPledgedSrcSize(this.cctx, srcSize);
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
      long result = ZSTD_h.ZSTD_CCtx_reset(this.cctx, directive.value());
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
   * Frees resources allocated by this compression context. Subsequent calls have no effect. Other
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
      // Free the cctx before releaseDictionary(): the referenced CDict must outlive the cctx.
      long result = ZSTD_h.ZSTD_freeCCtx(this.cctx);
      releaseDictionary();
      if (ZstdCritical.isError(result)) {
        throw new ZstdException(result);
      }
    } finally {
      lock.unlock();
    }
  }

  private void releaseDictionary() {
    ZstdCompressionDictionary previous = this.refDict;
    if (previous != null) {
      this.refDict = null;
      previous.release();
    }
  }

  private void lockOrThrow() {
    if (!lock.tryLock()) {
      throw new IllegalStateException("Compression context is in use by another thread");
    }
  }

  private void ensureOpen() {
    if (closed) throw new IllegalStateException("Compression context is closed");
  }
}
