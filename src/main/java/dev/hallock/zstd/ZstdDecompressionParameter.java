package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Configuration parameters for Zstd decompression. These correspond to ZSTD_dParameter options in
 * the native library.
 *
 * @implNote The numeric values are part of the frozen zstd ABI and are hardcoded here so that
 *     loading this class does not force the native library to load.
 */
public enum ZstdDecompressionParameter {
  /**
   * Maximum allowed window log for decompression, expressed as a power of 2 (log2). Prevents
   * allocation of excessive native memory when decompressing malformed or malicious frames with
   * abnormally large window requirements.
   */
  WINDOW_LOG_MAX(100);

  private final int value;

  ZstdDecompressionParameter(int value) {
    this.value = value;
  }

  /**
   * Returns the raw integer value associated with this parameter.
   *
   * @return the raw parameter value
   */
  public int value() {
    return value;
  }

  /**
   * Retrieves the supported bounds for this parameter.
   *
   * @return the parameter bounds
   * @throws ZstdException if a native error occurs while querying bounds
   */
  public ZstdParameterBounds bounds() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment boundsSeg = ZSTD_h.ZSTD_dParam_getBounds(arena, this.value);
      return ZstdParameterBounds.fromNative(boundsSeg);
    }
  }
}
