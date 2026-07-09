package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressorOutputStream;
import dev.hallock.zstd.ZstdDecompressorInputStream;
import dev.hallock.zstd.ZstdException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Regressions pinned against concrete decode-path bugs: spurious truncation reports when a frame
 * completes exactly at an output-buffer boundary, concatenated-frame handling in the byte[] tier,
 * and unvalidated frame-header content sizes.
 */
@ZstdTest
class ZstdRegressionTest {

  /** Mixed compressible/incompressible payload so frames span multiple blocks. */
  private static byte[] payload(int size, long seed) {
    byte[] data = new byte[size];
    new Random(seed).nextBytes(data);
    Arrays.fill(data, size / 4, size / 2, (byte) 42);
    return data;
  }

  /** Compresses through the stream adapter, producing a frame without a recorded content size. */
  private static byte[] unknownSizeFrame(Zstd zstd, byte[] original) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink)) {
      out.write(original);
    }
    return sink.toByteArray();
  }

  private static void assertContentSizeUnknown(Zstd zstd, byte[] frame) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      assertEquals(
          Zstd.CONTENT_SIZE_UNKNOWN,
          zstd.frameContentSize(src, frame.length),
          "precondition: the stream adapter must not record a content size");
    }
  }

  /**
   * The decompressed size is an exact multiple of the streaming output buffer (128 KiB), so the
   * frame completes exactly when the buffer fills. The old accumulator loop in decompress(byte[])
   * issued one more streaming call, began a phantom second frame, and threw a spurious "Truncated
   * zstd frame".
   */
  @ParameterizedTest
  @ValueSource(ints = {131072, 262144})
  void byteArrayDecompressHandlesExactBufferMultiple(int size, Zstd zstd) throws IOException {
    byte[] original = payload(size, 1000 + size);
    byte[] frame = unknownSizeFrame(zstd, original);
    assertContentSizeUnknown(zstd, frame);
    assertArrayEquals(original, zstd.decompress(frame));
  }

  /** The same exact-buffer-multiple sizes through the java.io stream adapter. */
  @ParameterizedTest
  @ValueSource(ints = {131072, 262144})
  void decompressorStreamHandlesExactBufferMultiple(int size, Zstd zstd) throws IOException {
    byte[] original = payload(size, 2000 + size);
    byte[] frame = unknownSizeFrame(zstd, original);
    assertContentSizeUnknown(zstd, frame);
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(frame))) {
      assertArrayEquals(original, in.readAllBytes());
      assertEquals(-1, in.read());
    }
  }

  @Test
  void decompressorStreamRoundTripsMultiMegabyteMultiBlockStream(Zstd zstd) throws IOException {
    byte[] original = payload(5 * 1024 * 1024, 77);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink, 3)) {
      // Odd chunk sizes and a mid-stream flush force multiple blocks and refill iterations.
      out.write(original, 0, 1_234_567);
      out.flush();
      out.write(original, 1_234_567, original.length - 1_234_567);
    }
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertArrayEquals(original, in.readAllBytes());
    }
  }

  /**
   * Two concatenated frames that both record their content size: the byte[] tier must walk every
   * frame header and decode the whole sequence, not just the first frame.
   */
  @Test
  void byteArrayDecompressDecodesConcatenatedKnownSizeFrames(Zstd zstd) {
    byte[] first = payload(70_000, 31);
    byte[] second = payload(40_000, 32);
    byte[] concatenated = concat(zstd.compress(first), zstd.compress(second));
    assertArrayEquals(concat(first, second), zstd.decompress(concatenated));
  }

  /** Two concatenated frames without recorded content sizes (streaming accumulator path). */
  @Test
  void byteArrayDecompressDecodesConcatenatedUnknownSizeFrames(Zstd zstd) throws IOException {
    byte[] first = payload(70_000, 41);
    byte[] second = payload(40_000, 42);
    byte[] frameA = unknownSizeFrame(zstd, first);
    byte[] frameB = unknownSizeFrame(zstd, second);
    assertContentSizeUnknown(zstd, frameA);
    assertContentSizeUnknown(zstd, frameB);
    assertArrayEquals(concat(first, second), zstd.decompress(concat(frameA, frameB)));
  }

  /**
   * A crafted header declaring a content size of 0xFFFFFFFFFFFFFFF0, a raw unvalidated u64 that
   * surfaces as a negative long. frameContentSize must reject it with ZstdException instead of
   * returning a bogus negative value that is not the UNKNOWN sentinel.
   */
  @Test
  void frameContentSizeRejectsForgedContentSizeField(Zstd zstd) {
    byte[] original = payload(1000, 51);
    byte[] frame = zstd.compress(original);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      assertEquals(
          original.length,
          zstd.frameContentSize(src, frame.length),
          "precondition: the frame must record its content size");
    }

    byte[] crafted = withForgedContentSize(frame, 0xFFFFFFFFFFFFFFF0L);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, crafted);
      ZstdException e =
          assertThrows(ZstdException.class, () -> zstd.frameContentSize(src, crafted.length));
      // Must be the unreasonable-declared-size rejection, not a generic header parse failure.
      assertTrue(
          e.getMessage()
              .contains(
                  "unreasonable declared content size "
                      + Long.toUnsignedString(0xFFFFFFFFFFFFFFF0L)),
          e.getMessage());
    }
  }

  /**
   * Rewrites the frame header so the Frame_Content_Size field is the 8-byte encoding of {@code
   * declaredSize}, preserving all other header fields and the frame body.
   */
  private static byte[] withForgedContentSize(byte[] frame, long declaredSize) {
    int fhd = frame[4] & 0xFF;
    int fcsCode = fhd >>> 6;
    boolean singleSegment = (fhd & 0x20) != 0;
    int dictIdBytes =
        switch (fhd & 3) {
          case 0 -> 0;
          case 1 -> 1;
          case 2 -> 2;
          default -> 4;
        };
    int fcsBytes =
        switch (fcsCode) {
          case 0 -> singleSegment ? 1 : 0;
          case 1 -> 2;
          case 2 -> 4;
          default -> 8;
        };
    assertTrue(fcsBytes > 0, "precondition: the frame header must carry an FCS field");
    // magic(4) + FHD(1) + optional window descriptor + optional dictionary ID, then the FCS field.
    int fcsOffset = 5 + (singleSegment ? 0 : 1) + dictIdBytes;

    byte[] crafted = new byte[frame.length - fcsBytes + 8];
    System.arraycopy(frame, 0, crafted, 0, fcsOffset);
    crafted[4] = (byte) (fhd | 0xC0); // FCS field size code 3: 8 bytes
    for (int i = 0; i < 8; i++) {
      crafted[fcsOffset + i] = (byte) (declaredSize >>> (8 * i));
    }
    System.arraycopy(
        frame, fcsOffset + fcsBytes, crafted, fcsOffset + 8, frame.length - fcsOffset - fcsBytes);
    return crafted;
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] result = Arrays.copyOf(a, a.length + b.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }
}
