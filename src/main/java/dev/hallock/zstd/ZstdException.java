package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZstdCritical;
import java.io.Serial;

/**
 * Exception thrown when a native Zstd operation encounters an error.
 *
 * <p>This exception is unchecked: most zstd error codes (such as {@code dstSize_tooSmall} or {@code
 * parameter_outOfBound}) indicate programming errors rather than recoverable environmental
 * conditions. The stream adapters ({@link ZstdCompressorOutputStream}, {@link
 * ZstdDecompressorInputStream}) translate it to {@link java.io.IOException} at the {@code java.io}
 * boundary.
 */
public final class ZstdException extends RuntimeException {

  @Serial private static final long serialVersionUID = -367098480333046071L;

  /**
   * The raw {@code size_t} return value from the native Zstd function, or {@code 0} if this
   * exception was not produced from a native return value.
   *
   * @serial
   */
  private final long rawValue;

  /**
   * The resolved error code; {@link ZstdErrorCode#UNKNOWN} if the native code has no stable mapping
   * or this exception was not produced from a native return value.
   *
   * @serial
   */
  private final ZstdErrorCode errorCode;

  /**
   * The descriptive name of the native error, or the plain detail message.
   *
   * @serial
   */
  private final String errorName;

  /**
   * Constructs a new ZstdException from the raw native return value.
   *
   * @param rawValue the raw return value from the native Zstd function
   */
  public ZstdException(long rawValue) {
    this(
        rawValue,
        ZstdErrorCode.fromNative(ZstdCritical.getErrorCode(rawValue)),
        ZstdCritical.getErrorName(rawValue));
  }

  /**
   * Constructs a new ZstdException with a plain message, for error conditions that are not
   * represented by a native {@code size_t} error code. For such exceptions {@link #rawValue()}
   * returns {@code 0}, {@link #errorCode()} returns {@link ZstdErrorCode#UNKNOWN}, and {@link
   * #errorName()} returns the message.
   *
   * @param message the detail message
   */
  public ZstdException(String message) {
    this.rawValue = 0;
    this.errorCode = ZstdErrorCode.UNKNOWN;
    this.errorName = message;
    super(message);
  }

  private ZstdException(long rawValue, ZstdErrorCode errorCode, String errorName) {
    this.rawValue = rawValue;
    this.errorCode = errorCode;
    this.errorName = errorName;
    super(errorName + " (" + errorCode + ")");
  }

  /**
   * Returns the raw return value from the native Zstd function.
   *
   * @return the raw return value
   */
  public long rawValue() {
    return rawValue;
  }

  /**
   * Returns the resolved Zstd error code.
   *
   * @return the error code, or {@link ZstdErrorCode#UNKNOWN} if the native code has no stable
   *     mapping or this exception was not produced from a native return value
   */
  public ZstdErrorCode errorCode() {
    return errorCode;
  }

  /**
   * Returns the descriptive name of the native error.
   *
   * @return the error name
   */
  public String errorName() {
    return errorName;
  }
}
