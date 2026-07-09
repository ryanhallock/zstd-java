package dev.hallock.zstd.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Golden vectors produced by the reference zstd CLI (v1.5.7), checked in under {@code
 * src/test/resources/.../vectors}. The small frames contain {@link #PLAIN_TEXT}; the structured
 * frames contain {@link #structuredPayload()}.
 */
final class TestVectors {

  /** The plaintext content of every small checked-in frame. */
  static final String PLAIN_TEXT = "Hello, zstd interop!";

  /** Dictionary ID of the checked-in trained dictionary {@code dict.bin}. */
  static final int TRAINED_DICT_ID = 1053818491;

  private TestVectors() {}

  /**
   * Reconstructs the deterministic ~1.3 MiB structured plaintext behind {@code structured.zst} and
   * {@code structured-multi.zst}. The generator is a fixed-seed LCG driving synthetic log records;
   * any change to this method invalidates the checked-in structured vectors.
   */
  static byte[] structuredPayload() {
    StringBuilder sb = new StringBuilder(1 << 21);
    long state = 0x9E3779B97F4A7C15L;
    for (int i = 0; i < 24000; i++) {
      state = state * 6364136223846793005L + 1442695040888963407L;
      long a = state >>> 40;
      long b = (state >>> 20) & 0xFFFFF;
      sb.append("id=")
          .append(i)
          .append(",user=user")
          .append(a % 500)
          .append(",session=")
          .append(Long.toHexString(b))
          .append(",metric=")
          .append(a % 100000)
          .append(",status=")
          .append((a & 3) == 0 ? "RETRY" : "OK")
          .append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.US_ASCII);
  }

  /**
   * Loads a checked-in vector resource.
   *
   * <ul>
   *   <li>{@code plain.zst}: level 19 frame with XXH64 checksum
   *   <li>{@code nocheck.zst}: level 3 frame without checksum
   *   <li>{@code multi.zst}: two concatenated {@code plain.zst} frames
   *   <li>{@code dictframe.zst}: frame compressed with the trained dictionary
   *   <li>{@code dict.bin}: zstd-trained dictionary (magic 0xEC30A437)
   *   <li>{@code structured.zst}: level 19 frame with XXH64 checksum of {@link
   *       #structuredPayload()}
   *   <li>{@code structured-multi.zst}: two concatenated level 19 checksummed frames holding the
   *       first and second halves (split at {@code length / 2}) of {@link #structuredPayload()}
   * </ul>
   */
  static byte[] load(String name) {
    try (InputStream in = TestVectors.class.getResourceAsStream("vectors/" + name)) {
      return Objects.requireNonNull(in, "missing test vector: " + name).readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
