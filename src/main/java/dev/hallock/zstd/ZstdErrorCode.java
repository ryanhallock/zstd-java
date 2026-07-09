package dev.hallock.zstd;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Error codes reported by the native Zstd library. These correspond to the stable {@code
 * ZSTD_ErrorCode} values declared in {@code zstd_errors.h}. Values outside the stable set resolve
 * to {@link #UNKNOWN}.
 *
 * @implNote The numeric values are part of the frozen zstd ABI (pinned since zstd v1.3.1; only
 *     values below 100 are declared stable) and are hardcoded here so that loading this class does
 *     not force the native library to load.
 */
public enum ZstdErrorCode {
  /** The operation completed successfully; not an error. */
  NO_ERROR(0),

  /** An unclassified error. */
  GENERIC(1),

  /** The input does not start with a known zstd magic number. */
  PREFIX_UNKNOWN(10),

  /** The frame requires a newer version of the zstd format than this library supports. */
  VERSION_UNSUPPORTED(12),

  /** The frame header uses a feature this library build does not support. */
  FRAME_PARAMETER_UNSUPPORTED(14),

  /** The frame requires a window larger than the decoder allows. */
  FRAME_PARAMETER_WINDOW_TOO_LARGE(16),

  /** The compressed data is corrupted and could not be parsed. */
  CORRUPTION_DETECTED(20),

  /** The frame checksum does not match the decompressed content. */
  CHECKSUM_WRONG(22),

  /** A literals section header within the compressed data is invalid. */
  LITERALS_HEADER_WRONG(24),

  /** The dictionary content is invalid. */
  DICTIONARY_CORRUPTED(30),

  /** The frame requires a different dictionary than the one provided. */
  DICTIONARY_WRONG(32),

  /** Digested dictionary creation failed. */
  DICTIONARY_CREATION_FAILED(34),

  /** The parameter is not recognized. */
  PARAMETER_UNSUPPORTED(40),

  /** The combination of parameter values is not supported. */
  PARAMETER_COMBINATION_UNSUPPORTED(41),

  /** The parameter value is outside its supported bounds. */
  PARAMETER_OUT_OF_BOUND(42),

  /** An entropy table log size exceeds the maximum allowed. */
  TABLE_LOG_TOO_LARGE(44),

  /** A maximum symbol value is too large. */
  MAX_SYMBOL_VALUE_TOO_LARGE(46),

  /** A maximum symbol value is too small. */
  MAX_SYMBOL_VALUE_TOO_SMALL(48),

  /** An uncompressed block cannot be produced under the current settings. */
  CANNOT_PRODUCE_UNCOMPRESSED_BLOCK(49),

  /** A stability condition for stable input or output buffers was violated. */
  STABILITY_CONDITION_NOT_RESPECTED(50),

  /** The operation is not permitted in the current context stage. */
  STAGE_WRONG(60),

  /** The context was used before required initialization. */
  INIT_MISSING(62),

  /** A native memory allocation failed. */
  MEMORY_ALLOCATION(64),

  /** The provided workspace is too small for the operation. */
  WORK_SPACE_TOO_SMALL(66),

  /** The destination buffer is too small to hold the result. */
  DST_SIZE_TOO_SMALL(70),

  /** The source size is incorrect, e.g. a truncated frame or trailing garbage. */
  SRC_SIZE_WRONG(72),

  /** The destination buffer is null while a nonzero capacity was declared. */
  DST_BUFFER_NULL(74),

  /** The operation makes no forward progress because the destination buffer is full. */
  NO_FORWARD_PROGRESS_DEST_FULL(80),

  /** The operation makes no forward progress because the input is empty. */
  NO_FORWARD_PROGRESS_INPUT_EMPTY(82),

  /**
   * Sentinel for native values with no stable mapping, and for {@link ZstdException}s that were not
   * produced from a native error code. Its {@link #value()} is {@code -1}, which is never a native
   * error code.
   */
  UNKNOWN(-1);

  private static final @Nullable ZstdErrorCode[] BY_VALUE; // sparse array

  static {
    @Nullable ZstdErrorCode[] byValue =
        new ZstdErrorCode[NO_FORWARD_PROGRESS_INPUT_EMPTY.value + 1]; // empty
    for (ZstdErrorCode code : values()) {
      if (code != UNKNOWN) {
        byValue[code.value] = code;
      }
    }
    BY_VALUE = byValue;
  }

  private final int value;

  ZstdErrorCode(int value) {
    this.value = value;
  }

  /**
   * Returns the raw integer value of the error code used in the native Zstd library, or {@code -1}
   * for {@link #UNKNOWN}.
   *
   * @return the raw error code value
   */
  public int value() {
    return value;
  }

  /**
   * Resolves a ZstdErrorCode from its native {@code ZSTD_ErrorCode} integer value.
   *
   * @param value the native error code value
   * @return the matching ZstdErrorCode, or {@link #UNKNOWN} if the value has no stable mapping
   */
  public static ZstdErrorCode fromNative(int value) {
    if (value < 0 || value >= BY_VALUE.length) {
      return UNKNOWN;
    }
    return Objects.requireNonNullElse(BY_VALUE[value], UNKNOWN);
  }
}
