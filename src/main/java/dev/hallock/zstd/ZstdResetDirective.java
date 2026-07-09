package dev.hallock.zstd;

/**
 * Directives for resetting compression or decompression contexts for reuse. These correspond to
 * ZSTD_ResetDirective options in the native library.
 *
 * @implNote The numeric values are part of the frozen zstd ABI and are hardcoded here so that
 *     loading this class does not force the native library to load.
 */
public enum ZstdResetDirective {

  /**
   * Reset the active session state only. Any frame in progress is abandoned and internal buffers
   * are cleared so a new stream can start, while all configured parameters and any loaded or
   * referenced dictionary are retained. This is the required recovery path after a streaming
   * operation fails.
   */
  SESSION_ONLY(1),

  /**
   * Reset all configured parameters to their defaults and drop any loaded or referenced dictionary
   * or prefix. Keeps the session buffers intact; the native library reports an error if parameters
   * are reset while a session is active (mid-frame).
   */
  PARAMETERS(2),

  /**
   * Fully reset both the session state and all parameters to default values. Clears all session
   * buffers, loaded dictionaries, and reverts parameters.
   */
  SESSION_AND_PARAMETERS(3);

  private final int value;

  ZstdResetDirective(int value) {
    this.value = value;
  }

  /**
   * Returns the raw integer value of the directive used in the native Zstd library.
   *
   * @return the directive value
   */
  public int value() {
    return this.value;
  }
}
