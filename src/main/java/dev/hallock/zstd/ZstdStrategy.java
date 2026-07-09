package dev.hallock.zstd;

/**
 * Compression strategies for Zstd, representing speed vs ratio tradeoffs. Strategies are ordered
 * from fastest (lowest compression ratio) to strongest (highest compression ratio).
 *
 * @implNote The numeric values are part of the frozen zstd ABI and are hardcoded here so that
 *     loading this class does not force the native library to load.
 */
public enum ZstdStrategy {
  /**
   * Fastest strategy. Uses a simple hash table to find matches. Designed for maximum throughput and
   * minimum CPU usage.
   */
  FAST(1),

  /**
   * Double fast strategy. Uses two hash tables with different search depths to find longer matches
   * quickly.
   */
  DFAST(2),

  /**
   * Greedy strategy. Selects the first long match found at the current position without examining
   * subsequent positions.
   */
  GREEDY(3),

  /**
   * Lazy strategy. For every position, checks if the next position has a better match before
   * committing to the match found at the current position.
   */
  LAZY(4),

  /**
   * Lazy 2 strategy. Similar to LAZY, but checks two bytes ahead instead of one to find even better
   * matches.
   */
  LAZY2(5),

  /**
   * Binary tree lazy strategy. Uses double hash chains coupled with a binary tree search mechanism.
   * Much stronger compression but slower.
   */
  BTLAZY2(6),

  /**
   * Binary tree optimal parser strategy. Performs optimal parsing (looks ahead and evaluates
   * combinations of matches) using a binary tree index.
   */
  BTOPT(7),

  /**
   * Ultra strategy. Similar to BTOPT but uses more aggressive optimal parsing search parameters.
   * Provides very high compression ratio at low compression speed.
   */
  BTULTRA(8),

  /** Ultra 2 strategy. The strongest and slowest strategy. Performs exhaustive optimal parsing. */
  BTULTRA2(9);

  private final int value;

  ZstdStrategy(int value) {
    this.value = value;
  }

  /**
   * Returns the raw integer value of the strategy used in the native Zstd library.
   *
   * @return the strategy value
   */
  public int value() {
    return this.value;
  }
}
