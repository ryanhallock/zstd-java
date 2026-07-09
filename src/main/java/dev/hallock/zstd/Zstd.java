package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;
import dev.hallock.zstd.bindings.ZstdCritical;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Main entry point for Zstandard (zstd) compression and decompression operations.
 *
 * <p>This class provides:
 *
 * <ul>
 *   <li>Block based compression and decompression of memory segments
 *   <li>Streaming compression and decompression via buffer representations
 *   <li>Resource management for custom compression and decompression contexts
 *   <li>Creation of digested dictionaries to accelerate operations on small payloads
 *   <li>Convenience {@code byte[]} operations for heap-based data
 * </ul>
 *
 * <p>The singleton instance is obtained using the static {@link #zstd()} factory method.
 *
 * <p><strong>Native segments:</strong> every {@link MemorySegment} passed to this API must be a
 * native (off-heap) segment, e.g. allocated from an {@link Arena} or wrapping a direct {@link
 * java.nio.ByteBuffer}. Heap segments (from {@code MemorySegment.ofArray} or heap ByteBuffers) are
 * rejected with {@link IllegalArgumentException}; use the {@code byte[]} convenience methods for
 * heap data or perform your own copy. The scope backing each segment must remain alive for the
 * duration of the operation using it.
 */
public final class Zstd {

  /**
   * Sentinel returned by {@link #frameContentSize(MemorySegment, long)} when the frame is valid but
   * does not record its decompressed content size in the header (the default for frames produced by
   * streaming compression without a pledged source size). Mirrors {@code ZSTD_CONTENTSIZE_UNKNOWN}.
   */
  public static final long CONTENT_SIZE_UNKNOWN;

  // Dont inline javac
  static {
    CONTENT_SIZE_UNKNOWN = -1L;
  }

  private Zstd() {}

  /**
   * Returns the singleton Zstd instance, loading the native library on first use.
   *
   * <p>If loading fails, this method (and not class initialization) reports the failure: every call
   * throws {@link IllegalStateException} carrying the original loading failure as its cause. The
   * native library must be at least version 1.4.0; an older library is reported the same way.
   *
   * @return the singleton instance
   * @throws IllegalStateException if the native zstd library cannot be loaded or is older than
   *     1.4.0
   */
  public static Zstd zstd() {
    Zstd instance = Holder.INSTANCE;
    if (instance == null) {
      throw new IllegalStateException(
          "Failed to load the native zstd library. Add a platform artifact with bundled natives"
              + " to the module path, or install libzstd on the system.",
          Holder.FAILURE);
    }
    return instance;
  }

  /**
   * Decompresses raw data from a source MemorySegment into a destination MemorySegment. Handles one
   * or more concatenated frames occupying {@code compressedSize} bytes.
   *
   * @param dst the destination segment where decompressed data is written (native segment)
   * @param dstCapacity the capacity limit of the destination segment
   * @param src the source segment containing compressed data (native segment)
   * @param compressedSize the size of the compressed data in the source segment
   * @return the decompressed size
   * @throws NullPointerException if {@code dst} or {@code src} is {@code null}
   * @throws IllegalArgumentException if a segment is not native, or a size is negative or larger
   *     than its segment
   * @throws ZstdException if a native decompression error occurs
   */
  public long decompress(
      MemorySegment dst, long dstCapacity, MemorySegment src, long compressedSize) {
    Objects.requireNonNull(dst, "dst");
    requireNative(dst, "dst");
    if (dstCapacity < 0) throw new IllegalArgumentException("dstCapacity cannot be negative");
    if (dstCapacity > dst.byteSize())
      throw new IllegalArgumentException("dstCapacity cannot be larger than dst.byteSize()");
    Objects.requireNonNull(src, "src");
    requireNative(src, "src");
    if (compressedSize < 0) throw new IllegalArgumentException("compressedSize cannot be negative");
    if (compressedSize > src.byteSize())
      throw new IllegalArgumentException("compressedSize cannot be larger than src.byteSize()");
    long result = ZSTD_h.ZSTD_decompress(dst, dstCapacity, src, compressedSize);
    if (ZstdCritical.isError(result)) {
      throw new ZstdException(result);
    }
    return result;
  }

  /**
   * Compresses raw data from a source MemorySegment into a destination MemorySegment.
   *
   * @param dst the destination segment where compressed data is written (native segment)
   * @param dstCapacity the capacity limit of the destination segment
   * @param src the source segment containing raw data (native segment)
   * @param srcSize the size of the raw data in the source segment
   * @param compressionLevel the compression level (typically between 1 and 22)
   * @return the compressed size
   * @throws NullPointerException if {@code dst} or {@code src} is {@code null}
   * @throws IllegalArgumentException if a segment is not native, or a size is negative or larger
   *     than its segment
   * @throws ZstdException if a native compression error occurs
   */
  public long compress(
      MemorySegment dst, long dstCapacity, MemorySegment src, long srcSize, int compressionLevel) {
    Objects.requireNonNull(dst, "dst");
    requireNative(dst, "dst");
    if (dstCapacity < 0) throw new IllegalArgumentException("dstCapacity cannot be negative");
    if (dstCapacity > dst.byteSize())
      throw new IllegalArgumentException("dstCapacity cannot be larger than dst.byteSize()");
    Objects.requireNonNull(src, "src");
    requireNative(src, "src");
    if (srcSize < 0) throw new IllegalArgumentException("srcSize cannot be negative");
    if (srcSize > src.byteSize())
      throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
    long result = ZSTD_h.ZSTD_compress(dst, dstCapacity, src, srcSize, compressionLevel);
    if (ZstdCritical.isError(result)) {
      throw new ZstdException(result);
    }
    return result;
  }

  /**
   * Compresses a byte array by copying, using the default compression level.
   *
   * <p>This is a convenience over the segment API: the input is copied to a temporary native buffer
   * under a confined arena and the compressed bytes are copied back to the heap. For hot paths,
   * prefer the {@link MemorySegment} overloads.
   *
   * @param src the raw data to compress
   * @return the compressed frame
   * @throws NullPointerException if {@code src} is {@code null}
   * @throws ZstdException if a native compression error occurs
   */
  public byte[] compress(byte[] src) {
    return compress(src, defaultCompressionLevel());
  }

  /**
   * Compresses a byte array by copying.
   *
   * <p>This is a convenience over the segment API: the input is copied to a temporary native buffer
   * under a confined arena and the compressed bytes are copied back to the heap. For hot paths,
   * prefer the {@link MemorySegment} overloads.
   *
   * @param src the raw data to compress
   * @param compressionLevel the compression level (typically between 1 and 22)
   * @return the compressed frame
   * @throws NullPointerException if {@code src} is {@code null}
   * @throws ZstdException if a native compression error occurs
   */
  public byte[] compress(byte[] src, int compressionLevel) {
    Objects.requireNonNull(src, "src");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment srcSeg = arena.allocate(Math.max(src.length, 1));
      MemorySegment.copy(src, 0, srcSeg, ValueLayout.JAVA_BYTE, 0, src.length);
      long bound = compressBound(src.length);
      MemorySegment dst = arena.allocate(bound);
      long compressed = compress(dst, bound, srcSeg, src.length, compressionLevel);
      if (compressed > Integer.MAX_VALUE - 8) {
        throw new ZstdException(
            "Compressed size " + compressed + " exceeds the maximum byte[] length");
      }
      byte[] result = new byte[(int) compressed];
      MemorySegment.copy(dst, ValueLayout.JAVA_BYTE, 0, result, 0, result.length);
      return result;
    }
  }

  /**
   * Decompresses a byte array by copying. Handles any number of concatenated frames (including
   * skippable frames) occupying the entire input; an empty input holds zero frames and yields an
   * empty array.
   *
   * <p>If every frame header records its content size, the output is allocated exactly from the
   * summed sizes; otherwise the whole input is decompressed through a streaming context into a
   * growing accumulator. The input is copied to a temporary native buffer under a confined arena
   * and the result copied back to the heap. For hot paths, prefer the {@link MemorySegment}
   * overloads.
   *
   * <p><strong>Untrusted input:</strong> declared content sizes are read from the input; a small
   * input can request a very large allocation. Impose a limit before calling this with untrusted
   * data.
   *
   * @param src the compressed data, zero or more complete zstd frames
   * @return the decompressed data
   * @throws NullPointerException if {@code src} is {@code null}
   * @throws ZstdException if the input is not a sequence of valid zstd frames, is truncated, the
   *     declared content sizes sum to more than the maximum {@code byte[]} length, or a native
   *     decompression error occurs
   */
  public byte[] decompress(byte[] src) {
    Objects.requireNonNull(src, "src");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment srcSeg = arena.allocate(Math.max(src.length, 1));
      MemorySegment.copy(src, 0, srcSeg, ValueLayout.JAVA_BYTE, 0, src.length);
      // Walk the frame headers to size the output exactly. Skippable frames declare size 0.
      long totalContentSize = 0;
      boolean sizeKnown = true;
      for (long offset = 0; offset < src.length; ) {
        MemorySegment frame = srcSeg.asSlice(offset);
        long remaining = src.length - offset;
        long contentSize = frameContentSize(frame, remaining);
        if (contentSize == CONTENT_SIZE_UNKNOWN) {
          sizeKnown = false;
          break;
        }
        try {
          totalContentSize = Math.addExact(totalContentSize, contentSize);
        } catch (ArithmeticException e) {
          throw new ZstdException("Declared content size exceeds the maximum byte[] length");
        }
        // The declared total is known from the headers alone, so this failure wins over truncation.
        if (totalContentSize > Integer.MAX_VALUE - 8) {
          throw new ZstdException(
              "Declared content size " + totalContentSize + " exceeds the maximum byte[] length");
        }
        offset += findFrameCompressedSize(frame, remaining);
      }
      if (sizeKnown) {
        // ZSTD_decompress natively decodes all concatenated frames when dst is large enough.
        MemorySegment dst = arena.allocate(Math.max(totalContentSize, 1));
        long decompressed = decompress(dst, totalContentSize, srcSeg, src.length);
        byte[] result = new byte[(int) decompressed];
        MemorySegment.copy(dst, ValueLayout.JAVA_BYTE, 0, result, 0, result.length);
        return result;
      }
      // At least one frame does not record its content size: stream the whole input into a
      // growing accumulator.
      try (ZstdDecompressionContext ctx = createDecompressionContext()) {
        ZstdInputBuffer in = createInputBuffer(arena, srcSeg, src.length, 0);
        MemorySegment outSeg = arena.allocate(recommendedDStreamOutSize());
        ZstdOutputBuffer out = createOutputBuffer(arena, outSeg);
        ByteArrayOutputStream accumulator = new ByteArrayOutputStream();
        byte[] chunk = new byte[(int) Math.min(outSeg.byteSize(), Integer.MAX_VALUE - 8)];
        long ret;
        do {
          out.position(0);
          ret = ctx.decompressStream(out, in);
          int produced = (int) out.position();
          if (produced > 0) {
            MemorySegment.copy(outSeg, ValueLayout.JAVA_BYTE, 0, chunk, 0, produced);
            accumulator.write(chunk, 0, produced);
          }
          // Continue on a full output buffer only mid-frame; after frame end an extra call would
          // misreport a truncation.
        } while (in.position() < in.size() || (ret != 0 && out.position() == out.size()));
        if (ret != 0) {
          throw new ZstdException("Truncated zstd frame: input ended mid-frame");
        }
        return accumulator.toByteArray();
      }
    }
  }

  /**
   * Returns the maximum compressed size for a given source size.
   *
   * @param srcSize the size of the uncompressed data
   * @return the maximum compressed size bound
   * @throws IllegalArgumentException if {@code srcSize} is negative
   */
  public long compressBound(long srcSize) {
    if (srcSize < 0) throw new IllegalArgumentException("srcSize cannot be negative");
    return ZSTD_h.ZSTD_compressBound(srcSize);
  }

  /**
   * Returns the decompressed content size declared by a frame, if recorded.
   *
   * <p>Frames produced by streaming compression without a pledged source size do not record their
   * content size; for those this method returns {@link #CONTENT_SIZE_UNKNOWN} and the data must be
   * decompressed through a streaming context (or an upper bound must be known). The result is not a
   * zstd error code: do not pass it to {@link #isError(long)}.
   *
   * <p>The header field is an unvalidated 64-bit value under the control of whoever produced the
   * frame. Declared sizes of 2^63 or more (which would appear here as negative values other than
   * {@link #CONTENT_SIZE_UNKNOWN}) are rejected with {@link ZstdException}; every other return is
   * either {@link #CONTENT_SIZE_UNKNOWN} or an actual declared size.
   *
   * @param src the source segment containing the compressed frame (native segment)
   * @param srcSize the size of the source segment
   * @return the declared decompressed size, or {@link #CONTENT_SIZE_UNKNOWN} if the frame does not
   *     record it
   * @throws NullPointerException if {@code src} is {@code null}
   * @throws IllegalArgumentException if {@code src} is not a native segment, or {@code srcSize} is
   *     negative or larger than {@code src.byteSize()}
   * @throws ZstdException if the segment does not contain a valid zstd frame header, or the header
   *     declares an unreasonable content size
   */
  public long frameContentSize(MemorySegment src, long srcSize) {
    Objects.requireNonNull(src, "src");
    requireNative(src, "src");
    if (srcSize < 0) throw new IllegalArgumentException("srcSize cannot be negative");
    if (srcSize > src.byteSize())
      throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
    long result = ZSTD_h.ZSTD_getFrameContentSize(src, srcSize);
    if (result == ZSTD_h.ZSTD_CONTENTSIZE_ERROR()) {
      throw new ZstdException("Invalid zstd frame: content size could not be determined");
    }
    if (result < 0 && result != CONTENT_SIZE_UNKNOWN) {
      // Raw unvalidated u64 header field: a crafted header can declare any value.
      throw new ZstdException(
          "Invalid zstd frame: unreasonable declared content size "
              + Long.toUnsignedString(result));
    }
    return result;
  }

  /**
   * Checks if a ZSTD function return value represents an error.
   *
   * @param result the return code from a ZSTD function
   * @return true if the result represents an error
   */
  public boolean isError(long result) {
    return ZstdCritical.isError(result);
  }

  /**
   * Returns the error code for an error return value.
   *
   * @param result the return code from a ZSTD function
   * @return the resolved error code, or {@link ZstdErrorCode#UNKNOWN} if the native code has no
   *     stable mapping
   */
  public ZstdErrorCode errorCode(long result) {
    return ZstdErrorCode.fromNative(ZstdCritical.getErrorCode(result));
  }

  /**
   * Returns the error name string for a given return value.
   *
   * @param result the return code from a ZSTD function
   * @return the error name string
   */
  public String errorName(long result) {
    return ZstdCritical.getErrorName(result);
  }

  /**
   * Returns the default compression level.
   *
   * @return default compression level
   */
  public int defaultCompressionLevel() {
    return ZSTD_h.ZSTD_defaultCLevel();
  }

  /**
   * Returns the minimum supported compression level.
   *
   * @return minimum compression level
   */
  public int minCompressionLevel() {
    return ZSTD_h.ZSTD_minCLevel();
  }

  /**
   * Returns the maximum supported compression level.
   *
   * @return maximum compression level
   */
  public int maxCompressionLevel() {
    return ZSTD_h.ZSTD_maxCLevel();
  }

  /**
   * Finds the exact compressed size of the first frame in the source.
   *
   * @param src the source segment containing the compressed frame (native segment)
   * @param srcSize the size of the source segment
   * @return the exact compressed size of the first frame
   * @throws NullPointerException if {@code src} is {@code null}
   * @throws IllegalArgumentException if {@code src} is not a native segment, or {@code srcSize} is
   *     negative or larger than {@code src.byteSize()}
   * @throws ZstdException if the segment does not start with a valid, complete zstd frame
   */
  public long findFrameCompressedSize(MemorySegment src, long srcSize) {
    Objects.requireNonNull(src, "src");
    requireNative(src, "src");
    if (srcSize < 0) throw new IllegalArgumentException("srcSize cannot be negative");
    if (srcSize > src.byteSize())
      throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
    long result = ZSTD_h.ZSTD_findFrameCompressedSize(src, srcSize);
    if (ZstdCritical.isError(result)) {
      throw new ZstdException(result);
    }
    return result;
  }

  /**
   * Retrieves the dictionary ID from a raw dictionary segment.
   *
   * @param dict the dictionary memory segment (native segment)
   * @param dictSize the size of the dictionary
   * @return the dictionary ID, or 0 for a raw-content dictionary
   * @throws NullPointerException if {@code dict} is {@code null}
   * @throws IllegalArgumentException if {@code dict} is not a native segment, or {@code dictSize}
   *     is negative or larger than {@code dict.byteSize()}
   */
  public int dictIdFromDict(MemorySegment dict, long dictSize) {
    Objects.requireNonNull(dict, "dict");
    requireNative(dict, "dict");
    if (dictSize < 0) throw new IllegalArgumentException("dictSize cannot be negative");
    if (dictSize > dict.byteSize())
      throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
    return ZSTD_h.ZSTD_getDictID_fromDict(dict, dictSize);
  }

  /**
   * Retrieves the dictionary ID from a compressed frame.
   *
   * @param frame the compressed frame segment (native segment)
   * @param srcSize the size of the frame segment
   * @return the dictionary ID, or 0 if the frame was compressed without a dictionary or without
   *     recording its ID
   * @throws NullPointerException if {@code frame} is {@code null}
   * @throws IllegalArgumentException if {@code frame} is not a native segment, or {@code srcSize}
   *     is negative or larger than {@code frame.byteSize()}
   */
  public int dictIdFromFrame(MemorySegment frame, long srcSize) {
    Objects.requireNonNull(frame, "frame");
    requireNative(frame, "frame");
    if (srcSize < 0) throw new IllegalArgumentException("srcSize cannot be negative");
    if (srcSize > frame.byteSize())
      throw new IllegalArgumentException("srcSize cannot be larger than frame.byteSize()");
    return ZSTD_h.ZSTD_getDictID_fromFrame(frame, srcSize);
  }

  /**
   * Returns the native library version number.
   *
   * @return version number
   */
  public int versionNumber() {
    return ZSTD_h.ZSTD_versionNumber();
  }

  /**
   * Returns the native library version string.
   *
   * @return version string
   */
  public String versionString() {
    return ZSTD_h.ZSTD_versionString().getString(0);
  }

  /**
   * Returns the ZSTD magic number.
   *
   * @return magic number
   */
  public int magicNumber() {
    return ZSTD_h.ZSTD_MAGICNUMBER();
  }

  /**
   * Returns the recommended size for streaming compression input buffers. Sizing input buffers to
   * this value minimizes internal buffering in the native library.
   *
   * @return the recommended input buffer size in bytes
   */
  public long recommendedCStreamInSize() {
    return ZSTD_h.ZSTD_CStreamInSize();
  }

  /**
   * Returns the recommended size for streaming compression output buffers. An output buffer of at
   * least this size is guaranteed to hold a fully compressed block, so a flush always completes in
   * one call.
   *
   * @return the recommended output buffer size in bytes
   */
  public long recommendedCStreamOutSize() {
    return ZSTD_h.ZSTD_CStreamOutSize();
  }

  /**
   * Returns the recommended size for streaming decompression input buffers.
   *
   * @return the recommended input buffer size in bytes
   */
  public long recommendedDStreamInSize() {
    return ZSTD_h.ZSTD_DStreamInSize();
  }

  /**
   * Returns the recommended size for streaming decompression output buffers. An output buffer of at
   * least this size is guaranteed to hold a fully decompressed block, minimizing the number of
   * streaming iterations.
   *
   * @return the recommended output buffer size in bytes
   */
  public long recommendedDStreamOutSize() {
    return ZSTD_h.ZSTD_DStreamOutSize();
  }

  /**
   * Creates a new compression context.
   *
   * @return a new ZstdCompressionContext
   */
  public ZstdCompressionContext createCompressionContext() {
    return new ZstdCompressionContext();
  }

  /**
   * Creates a new decompression context.
   *
   * @return a new ZstdDecompressionContext
   */
  public ZstdDecompressionContext createDecompressionContext() {
    return new ZstdDecompressionContext();
  }

  /**
   * Creates a new compression dictionary. The dictionary content is copied into native structures,
   * so {@code dict} does not need to remain alive after this call.
   *
   * @param dict the raw dictionary content segment (native segment)
   * @param compressionLevel the compression level
   * @return a new ZstdCompressionDictionary
   * @throws NullPointerException if {@code dict} is {@code null}
   * @throws IllegalArgumentException if {@code dict} is not a native segment
   */
  public ZstdCompressionDictionary createCompressionDictionary(
      MemorySegment dict, int compressionLevel) {
    return new ZstdCompressionDictionary(dict, compressionLevel);
  }

  /**
   * Creates a new compression dictionary with explicit size limit. The dictionary content is copied
   * into native structures, so {@code dict} does not need to remain alive after this call.
   *
   * @param dict the raw dictionary content segment (native segment)
   * @param dictSize the dictionary size limit
   * @param compressionLevel the compression level
   * @return a new ZstdCompressionDictionary
   * @throws NullPointerException if {@code dict} is {@code null}
   * @throws IllegalArgumentException if {@code dict} is not a native segment, or {@code dictSize}
   *     is negative or larger than {@code dict.byteSize()}
   */
  public ZstdCompressionDictionary createCompressionDictionary(
      MemorySegment dict, long dictSize, int compressionLevel) {
    return new ZstdCompressionDictionary(dict, dictSize, compressionLevel);
  }

  /**
   * Creates a new decompression dictionary. The dictionary content is copied into native
   * structures, so {@code dict} does not need to remain alive after this call.
   *
   * @param dict the raw dictionary content segment (native segment)
   * @return a new ZstdDecompressionDictionary
   * @throws NullPointerException if {@code dict} is {@code null}
   * @throws IllegalArgumentException if {@code dict} is not a native segment
   */
  public ZstdDecompressionDictionary createDecompressionDictionary(MemorySegment dict) {
    return new ZstdDecompressionDictionary(dict);
  }

  /**
   * Creates a new decompression dictionary with explicit size limit. The dictionary content is
   * copied into native structures, so {@code dict} does not need to remain alive after this call.
   *
   * @param dict the raw dictionary content segment (native segment)
   * @param dictSize the dictionary size limit
   * @return a new ZstdDecompressionDictionary
   * @throws NullPointerException if {@code dict} is {@code null}
   * @throws IllegalArgumentException if {@code dict} is not a native segment, or {@code dictSize}
   *     is negative or larger than {@code dict.byteSize()}
   */
  public ZstdDecompressionDictionary createDecompressionDictionary(
      MemorySegment dict, long dictSize) {
    return new ZstdDecompressionDictionary(dict, dictSize);
  }

  /**
   * Creates a new input buffer representation spanning the entire length of the given source memory
   * segment.
   *
   * <p>The scope backing {@code source} must remain alive for as long as the buffer is used in
   * streaming operations; see {@link ZstdInputBuffer}. Size the source with {@link
   * #recommendedCStreamInSize()} / {@link #recommendedDStreamInSize()} for best streaming
   * throughput.
   *
   * @param allocator the allocator to use for allocating the native ZSTD_inBuffer struct
   * @param source the source memory segment containing data to compress/decompress (native segment)
   * @return a new ZstdInputBuffer
   * @throws NullPointerException if {@code allocator} or {@code source} is {@code null}
   * @throws IllegalArgumentException if {@code source} or the allocated struct is not a native
   *     segment
   */
  public ZstdInputBuffer createInputBuffer(SegmentAllocator allocator, MemorySegment source) {
    return new ZstdInputBuffer(allocator, source);
  }

  /**
   * Creates a new input buffer representation with explicit size and position bounds.
   *
   * <p>The scope backing {@code source} must remain alive for as long as the buffer is used in
   * streaming operations; see {@link ZstdInputBuffer}.
   *
   * @param allocator the allocator to use for allocating the native ZSTD_inBuffer struct
   * @param source the source memory segment (native segment)
   * @param size the size limit of data to be consumed (must be &lt;= source capacity)
   * @param position the current read position (must be &lt;= size)
   * @return a new ZstdInputBuffer
   * @throws NullPointerException if {@code allocator} or {@code source} is {@code null}
   * @throws IllegalArgumentException if {@code source} or the allocated struct is not a native
   *     segment, or {@code size} or {@code position} is out of bounds
   */
  public ZstdInputBuffer createInputBuffer(
      SegmentAllocator allocator, MemorySegment source, long size, long position) {
    return new ZstdInputBuffer(allocator, source, size, position);
  }

  /**
   * Creates a new output buffer representation spanning the entire length of the given destination
   * memory segment.
   *
   * <p>The scope backing {@code destination} must remain alive for as long as the buffer is used in
   * streaming operations; see {@link ZstdOutputBuffer}. Size the destination with {@link
   * #recommendedCStreamOutSize()} / {@link #recommendedDStreamOutSize()} for best streaming
   * throughput.
   *
   * @param allocator the allocator to use for allocating the native ZSTD_outBuffer struct
   * @param destination the destination memory segment where compressed/decompressed data is written
   *     (native segment)
   * @return a new ZstdOutputBuffer
   * @throws NullPointerException if {@code allocator} or {@code destination} is {@code null}
   * @throws IllegalArgumentException if {@code destination} or the allocated struct is not a native
   *     segment
   */
  public ZstdOutputBuffer createOutputBuffer(
      SegmentAllocator allocator, MemorySegment destination) {
    return new ZstdOutputBuffer(allocator, destination);
  }

  /**
   * Creates a new output buffer representation with explicit size and position bounds.
   *
   * <p>The scope backing {@code destination} must remain alive for as long as the buffer is used in
   * streaming operations; see {@link ZstdOutputBuffer}.
   *
   * @param allocator the allocator to use for allocating the native ZSTD_outBuffer struct
   * @param destination the destination memory segment (native segment)
   * @param size the size limit of data to be written (must be &lt;= destination capacity)
   * @param position the current write position (must be &lt;= size)
   * @return a new ZstdOutputBuffer
   * @throws NullPointerException if {@code allocator} or {@code destination} is {@code null}
   * @throws IllegalArgumentException if {@code destination} or the allocated struct is not a native
   *     segment, or {@code size} or {@code position} is out of bounds
   */
  public ZstdOutputBuffer createOutputBuffer(
      SegmentAllocator allocator, MemorySegment destination, long size, long position) {
    return new ZstdOutputBuffer(allocator, destination, size, position);
  }

  /**
   * Creates an {@link OutputStream} that compresses into a single zstd frame using the default
   * compression level, writing to {@code out}. See {@link ZstdCompressorOutputStream}.
   *
   * @param out the stream to write the compressed frame to
   * @return a new compressing stream
   * @throws NullPointerException if {@code out} is {@code null}
   * @throws IOException if the compression context cannot be configured
   */
  public ZstdCompressorOutputStream createCompressorOutputStream(OutputStream out)
      throws IOException {
    return new ZstdCompressorOutputStream(this, out, defaultCompressionLevel());
  }

  /**
   * Creates an {@link OutputStream} that compresses into a single zstd frame, writing to {@code
   * out}. See {@link ZstdCompressorOutputStream}.
   *
   * @param out the stream to write the compressed frame to
   * @param compressionLevel the compression level (typically between 1 and 22)
   * @return a new compressing stream
   * @throws NullPointerException if {@code out} is {@code null}
   * @throws IOException if the compression context cannot be configured
   */
  public ZstdCompressorOutputStream createCompressorOutputStream(
      OutputStream out, int compressionLevel) throws IOException {
    return new ZstdCompressorOutputStream(this, out, compressionLevel);
  }

  /**
   * Creates an {@link InputStream} that decompresses zstd-compressed data from {@code in}, handling
   * multiple concatenated frames. See {@link ZstdDecompressorInputStream}.
   *
   * @param in the stream containing zstd-compressed data
   * @return a new decompressing stream
   * @throws NullPointerException if {@code in} is {@code null}
   * @throws IOException if the decompression context cannot be created
   */
  public ZstdDecompressorInputStream createDecompressorInputStream(InputStream in)
      throws IOException {
    return new ZstdDecompressorInputStream(this, in);
  }

  static void requireNative(MemorySegment segment, String name) {
    if (!segment.isNative()) {
      throw new IllegalArgumentException(
          name
              + " must be a native memory segment (heap arrays and heap ByteBuffers are not"
              + " supported; allocate from an Arena or wrap a direct ByteBuffer)");
    }
  }

  static Throwable chain(@Nullable Throwable primary, Throwable failure) {
    if (primary == null) return failure;
    primary.addSuppressed(failure);
    return primary;
  }

  static void rethrowIfPresent(@Nullable Throwable primary) throws IOException {
    switch (primary) {
      case null -> {}
      case Error e -> throw e;
      default -> throw new IOException(primary);
    }
  }

  private static final class Holder {
    static final @Nullable Zstd INSTANCE;
    static final @Nullable Throwable FAILURE;

    // Never throw from <clinit>; record the outcome so zstd() can throw the documented
    // IllegalStateException on every call.
    static {
      Zstd instance = null;
      Throwable failure = null;
      try {
        // The version probe doubles as the load check; the 1.4.0 floor covers ZSTD_compress2,
        // ZSTD_compressStream2, and refCDict/refDDict, which this API depends on as stable API.
        if (ZSTD_h.ZSTD_versionNumber() < 10400) {
          throw new IllegalStateException(
              "Native zstd library is version "
                  + ZSTD_h.ZSTD_versionString().getString(0)
                  + ", but this API requires at least 1.4.0");
        }
        instance = new Zstd();
      } catch (Throwable t) {
        failure = t;
      }
      INSTANCE = instance;
      FAILURE = failure;
    }
  }
}
