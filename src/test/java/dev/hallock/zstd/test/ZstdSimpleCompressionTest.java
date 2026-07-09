package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ZstdSimpleCompressionTest {

  @Test
  void testSimpleCompressionDecompression() throws ZstdException {
    byte[] original = "Hello World!".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          Zstd.zstd()
              .compress(
                  dst, compressBound, src, original.length, Zstd.zstd().defaultCompressionLevel());

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = Zstd.zstd().decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testDifferentCompressionLevels() throws ZstdException {
    byte[] original = "Test data for compression".repeat(100).getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);

      for (int level : new int[] {1, 3, 5, 7, 9}) {
        MemorySegment dst = arena.allocate(compressBound);
        long compressed = Zstd.zstd().compress(dst, compressBound, src, original.length, level);

        assertTrue(compressed < original.length);
        assertTrue(compressed > 0);
      }
    }
  }

  @Test
  void testEmptyData() throws ZstdException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocate(1);
      long compressBound = Zstd.zstd().compressBound(1);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          Zstd.zstd().compress(dst, compressBound, src, 0, Zstd.zstd().defaultCompressionLevel());
      assertTrue(compressed > 0);

      assertEquals(0, Zstd.zstd().frameContentSize(dst, compressed));
      MemorySegment decompressDst = arena.allocate(1);
      long decompressed = Zstd.zstd().decompress(decompressDst, 0, dst, compressed);
      assertEquals(0, decompressed);
    }
  }

  @Test
  void testLargeData() throws ZstdException {
    byte[] original = new byte[10 * 1024 * 1024];
    for (int i = 0; i < original.length; i++) {
      original[i] = (byte) (i % 256);
    }

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          Zstd.zstd()
              .compress(
                  dst, compressBound, src, original.length, Zstd.zstd().defaultCompressionLevel());

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = Zstd.zstd().decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testGetFrameContentSize() throws ZstdException {
    byte[] original = "Frame size test".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          Zstd.zstd()
              .compress(
                  dst, compressBound, src, original.length, Zstd.zstd().defaultCompressionLevel());

      long frameSize = Zstd.zstd().frameContentSize(dst, compressed);
      assertEquals(original.length, frameSize);
    }
  }
}
