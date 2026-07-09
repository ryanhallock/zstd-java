package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

@ZstdTest
class ZstdEdgeCaseTest {

  @Test
  void testSingleByte(Zstd zstd) throws ZstdException {
    byte[] original = new byte[] {42};

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          zstd.compress(dst, compressBound, src, original.length, zstd.defaultCompressionLevel());

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = zstd.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testHighlyCompressibleData(Zstd zstd) throws ZstdException {
    byte[] original = new byte[10000];
    Arrays.fill(original, (byte) 'A');

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          zstd.compress(dst, compressBound, src, original.length, zstd.defaultCompressionLevel());

      assertTrue(compressed < original.length / 10);

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = zstd.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testRandomData(Zstd zstd) throws ZstdException {
    byte[] original = new byte[5000];
    for (int i = 0; i < original.length; i++) {
      original[i] = (byte) ((i * 17 + 31) % 256);
    }

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          zstd.compress(dst, compressBound, src, original.length, zstd.defaultCompressionLevel());

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = zstd.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testAllZeros(Zstd zstd) throws ZstdException {
    byte[] original = new byte[1000];

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          zstd.compress(dst, compressBound, src, original.length, zstd.defaultCompressionLevel());

      assertTrue(compressed < 100);

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = zstd.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testUnicodeText(Zstd zstd) throws ZstdException {
    byte[] original = "Hello 世界 🌍 مرحبا мир".repeat(50).getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          zstd.compress(dst, compressBound, src, original.length, zstd.defaultCompressionLevel());

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = zstd.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testBinaryData(Zstd zstd) throws ZstdException {
    byte[] original = new byte[2000];
    for (int i = 0; i < original.length; i++) {
      original[i] = (byte) (i & 0xFF);
    }

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          zstd.compress(dst, compressBound, src, original.length, zstd.defaultCompressionLevel());

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = zstd.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testContextStreamCombination(Zstd zstd) throws Exception {
    byte[] original = "Context and stream test".repeat(100).getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext()) {

      MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      MemorySegment outputSeg = arena.allocate(zstd.compressBound(original.length));

      ZstdInputBuffer input = zstd.createInputBuffer(arena, inputSeg);
      ZstdOutputBuffer output = zstd.createOutputBuffer(arena, outputSeg);

      input.size(original.length);
      input.position(0);
      output.position(0);

      long result = cctx.compressStream(output, input, ZstdEndDirective.END);

      // With compressBound-sized output space a single END call completes the frame.
      assertEquals(0, result, "frame epilogue not fully written");
      long compressedSize = output.position();
      assertTrue(compressedSize > 0);

      MemorySegment decompressed = arena.allocate(original.length);
      long size = zstd.decompress(decompressed, original.length, outputSeg, compressedSize);
      assertEquals(original.length, size);
      assertArrayEquals(original, decompressed.toArray(ValueLayout.JAVA_BYTE));
    }
  }
}
