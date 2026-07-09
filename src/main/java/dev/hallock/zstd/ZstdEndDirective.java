package dev.hallock.zstd;

/**
 * Directives for ending or flushing streaming compression operations. These correspond to
 * ZSTD_EndDirective options in the native library.
 *
 * @implNote The numeric values are part of the frozen zstd ABI and are hardcoded here so that
 *     loading this class does not force the native library to load.
 */
public enum ZstdEndDirective {
  /**
   * Continue standard buffering. The compressor will collect data into internal buffers and write
   * output only when a full block is formed or when a flush is explicitly requested.
   */
  CONTINUE(0),

  /**
   * Flush all accumulated data. Forces the compressor to emit all currently buffered input data as
   * a completed block, making it readable on the decompressor side. This creates a flush boundary.
   */
  FLUSH(1),

  /**
   * Flush all data and end the current frame. Emits all buffered data and appends the frame footer.
   * The compressor must be called repeatedly with this directive until the return value is 0,
   * indicating the frame is fully written.
   */
  END(2);

  private final int value;

  ZstdEndDirective(int value) {
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
