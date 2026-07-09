package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.fail;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressorOutputStream;
import dev.hallock.zstd.ZstdDecompressorInputStream;
import dev.hallock.zstd.ZstdException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Deterministic fuzz-style robustness check for the decode paths: seeded random mutations (byte
 * flips, truncations, extensions) of valid frames must decode successfully or fail with exactly
 * {@link ZstdException} (byte[] tier) / {@link IOException} (stream tier), never any other
 * throwable and never an unbounded loop.
 *
 * <p>Deliberately plain JUnit rather than a jazzer dependency: the modular test setup makes a
 * fuzzing agent fragile, and a fixed seed keeps failures reproducible.
 */
@ZstdTest
class ZstdMutationRobustnessTest {

  private static final long SEED = 0x5EEDF00DL;
  private static final int MUTATIONS_PER_BASE = 350;

  /** Hard cap on decoded output per mutant so a decode of a corrupt frame stays bounded. */
  private static final int MAX_DECODED_BYTES = 32 * 1024 * 1024;

  @Test
  void mutatedFramesNeverEscapeTheDocumentedExceptions(Zstd zstd) throws IOException {
    Random random = new Random(SEED);
    for (byte[] base : baseFrames(zstd)) {
      for (int i = 0; i < MUTATIONS_PER_BASE; i++) {
        byte[] mutated = mutate(base, random);
        exerciseByteArrayTier(zstd, mutated, i);
        exerciseStreamTier(zstd, mutated, i);
      }
    }
  }

  private static byte[][] baseFrames(Zstd zstd) throws IOException {
    byte[] payload = new byte[4096];
    new Random(SEED ^ 1).nextBytes(payload);
    Arrays.fill(payload, 1024, 3072, (byte) 42);

    // A frame with recorded content size, one without (streaming path), and a two-frame concat.
    byte[] known = zstd.compress(payload, 3);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink)) {
      out.write(payload);
    }
    byte[] unknown = sink.toByteArray();
    byte[] concatenated = Arrays.copyOf(known, known.length + unknown.length);
    System.arraycopy(unknown, 0, concatenated, known.length, unknown.length);
    return new byte[][] {known, unknown, concatenated};
  }

  private static byte[] mutate(byte[] base, Random random) {
    byte[] mutated;
    switch (random.nextInt(4)) {
      case 0 -> { // flip 1-4 bytes anywhere
        mutated = base.clone();
        int flips = 1 + random.nextInt(4);
        for (int i = 0; i < flips; i++) {
          mutated[random.nextInt(mutated.length)] ^= (byte) (1 + random.nextInt(255));
        }
      }
      case 1 -> // truncate at a random point
          mutated = Arrays.copyOf(base, random.nextInt(base.length));
      case 2 -> { // extend with random bytes
        int extra = 1 + random.nextInt(16);
        mutated = Arrays.copyOf(base, base.length + extra);
        for (int i = base.length; i < mutated.length; i++) {
          mutated[i] = (byte) random.nextInt(256);
        }
      }
      default -> { // truncate and corrupt the tail
        mutated = Arrays.copyOf(base, 1 + random.nextInt(base.length));
        mutated[mutated.length - 1] ^= (byte) (1 + random.nextInt(255));
      }
    }
    return mutated;
  }

  private static void exerciseByteArrayTier(Zstd zstd, byte[] mutated, int iteration) {
    try {
      var _ = zstd.decompress(mutated);
    } catch (ZstdException expected) {
      // The only documented failure mode of the byte[] tier.
    } catch (Throwable t) {
      fail("decompress(byte[]) escaped with " + t.getClass().getName() + " at #" + iteration, t);
    }
  }

  private static void exerciseStreamTier(Zstd zstd, byte[] mutated, int iteration) {
    byte[] buffer = new byte[64 * 1024];
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(mutated))) {
      long total = 0;
      int n;
      while ((n = in.read(buffer, 0, buffer.length)) != -1) {
        total += n;
        if (total > MAX_DECODED_BYTES) {
          fail("stream decode exceeded the output cap at #" + iteration);
        }
      }
    } catch (IOException expected) {
      // The only documented failure mode of the stream tier (EOFException included).
    } catch (Throwable t) {
      fail("stream decode escaped with " + t.getClass().getName() + " at #" + iteration, t);
    }
  }
}
