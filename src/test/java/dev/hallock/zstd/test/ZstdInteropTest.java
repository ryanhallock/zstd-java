package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdDecompressorInputStream;
import java.io.ByteArrayInputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Interop tests against golden frames produced by the reference zstd CLI. A symmetric encode +
 * decode defect in the binding (wrong struct field mapping, size plumbing, parameter constants)
 * would pass every self round-trip test but fail here.
 */
@ZstdTest
class ZstdInteropTest {

  private static final byte[] EXPECTED = TestVectors.PLAIN_TEXT.getBytes(StandardCharsets.UTF_8);

  @Test
  void decompressesCliFrameWithChecksum(Zstd zstd) {
    assertArrayEquals(EXPECTED, zstd.decompress(TestVectors.load("plain.zst")));
  }

  @Test
  void decompressesCliFrameWithoutChecksum(Zstd zstd) {
    assertArrayEquals(EXPECTED, zstd.decompress(TestVectors.load("nocheck.zst")));
  }

  @Test
  void decompressesConcatenatedCliFrames(Zstd zstd) throws Exception {
    byte[] multi = TestVectors.load("multi.zst");
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(multi))) {
      assertArrayEquals(
          (TestVectors.PLAIN_TEXT + TestVectors.PLAIN_TEXT).getBytes(StandardCharsets.UTF_8),
          in.readAllBytes());
    }
  }

  @Test
  void decompressesStructuredCliFrame(Zstd zstd) {
    assertArrayEquals(
        TestVectors.structuredPayload(), zstd.decompress(TestVectors.load("structured.zst")));
  }

  @Test
  void decompressesStructuredCliFrameThroughStream(Zstd zstd) throws Exception {
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(
            new ByteArrayInputStream(TestVectors.load("structured.zst")))) {
      assertArrayEquals(TestVectors.structuredPayload(), in.readAllBytes());
    }
  }

  @Test
  void decompressesConcatenatedStructuredCliFrames(Zstd zstd) {
    assertArrayEquals(
        TestVectors.structuredPayload(), zstd.decompress(TestVectors.load("structured-multi.zst")));
  }

  @Test
  void decompressesConcatenatedStructuredCliFramesThroughStream(Zstd zstd) throws Exception {
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(
            new ByteArrayInputStream(TestVectors.load("structured-multi.zst")))) {
      assertArrayEquals(TestVectors.structuredPayload(), in.readAllBytes());
    }
  }

  @Test
  void structuredCliFrameReportsContentSize(Zstd zstd) {
    byte[] frame = TestVectors.load("structured.zst");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      assertEquals(
          TestVectors.structuredPayload().length, zstd.frameContentSize(src, frame.length));
      assertEquals(frame.length, zstd.findFrameCompressedSize(src, frame.length));
    }
  }

  @Test
  void cliFrameReportsContentSizeAndCompressedSize(Zstd zstd) {
    byte[] frame = TestVectors.load("plain.zst");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      assertEquals(EXPECTED.length, zstd.frameContentSize(src, frame.length));
      assertEquals(frame.length, zstd.findFrameCompressedSize(src, frame.length));
    }
  }

  @Test
  void producedFramesStartWithMagicNumber(Zstd zstd) {
    byte[] frame = zstd.compress("magic number check".getBytes(StandardCharsets.UTF_8));
    int magic =
        (frame[0] & 0xFF)
            | (frame[1] & 0xFF) << 8
            | (frame[2] & 0xFF) << 16
            | (frame[3] & 0xFF) << 24;
    assertEquals(zstd.magicNumber(), magic);
  }
}
