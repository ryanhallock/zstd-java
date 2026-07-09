package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_bounds;
import dev.hallock.zstd.bindings.ZstdCritical;
import java.lang.foreign.MemorySegment;

/**
 * The bounds (minimum and maximum values) for a compression or decompression parameter. This class
 * is final, immutable, and value-based; instances are obtained only from parameter bounds queries.
 */
public final class ZstdParameterBounds {
  private final int lowerBound;
  private final int upperBound;

  private ZstdParameterBounds(int lowerBound, int upperBound) {
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    super();
  }

  /**
   * Parses and constructs a ZstdParameterBounds instance from a native ZSTD_bounds struct segment.
   *
   * @param boundsSeg the native ZSTD_bounds struct memory segment
   * @return the parsed parameter bounds
   * @throws ZstdException if the native bounds query returned an error
   */
  static ZstdParameterBounds fromNative(MemorySegment boundsSeg) {
    long error = ZSTD_bounds.error(boundsSeg);
    if (ZstdCritical.isError(error)) {
      throw new ZstdException(error);
    }
    return new ZstdParameterBounds(
        ZSTD_bounds.lowerBound(boundsSeg), ZSTD_bounds.upperBound(boundsSeg));
  }

  /**
   * Returns the minimum inclusive value supported for the parameter.
   *
   * @return the lower bound
   */
  public int lowerBound() {
    return lowerBound;
  }

  /**
   * Returns the maximum inclusive value supported for the parameter.
   *
   * @return the upper bound
   */
  public int upperBound() {
    return upperBound;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ZstdParameterBounds that)) return false;
    return lowerBound == that.lowerBound && upperBound == that.upperBound;
  }

  @Override
  public int hashCode() {
    int result = lowerBound;
    result = 31 * result + upperBound;
    return result;
  }

  @Override
  public String toString() {
    return "ZstdParameterBounds[" + "lowerBound=" + lowerBound + ", upperBound=" + upperBound + ']';
  }
}
