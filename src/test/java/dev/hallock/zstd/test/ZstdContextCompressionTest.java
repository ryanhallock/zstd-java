package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdStrategy;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ZstdContextCompressionTest {

  @Test
  void testCompressionContext() throws Exception {
    byte[] original = "Context compression test".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = Zstd.zstd().createCompressionContext()) {

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed = cctx.compress(dst, compressBound, src, original.length);

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = Zstd.zstd().decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testDecompressionContext() throws Exception {
    byte[] original = "Decompression context test".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext dctx = Zstd.zstd().createDecompressionContext()) {

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          Zstd.zstd()
              .compress(
                  dst, compressBound, src, original.length, Zstd.zstd().defaultCompressionLevel());

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = dctx.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testStrategyConvenienceRoundTrip() throws Exception {
    byte[] original = "typed strategy setter data".repeat(200).getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = Zstd.zstd().createCompressionContext()) {
      cctx.strategy(ZstdStrategy.BTOPT);

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long bound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(bound);
      long compressed = cctx.compress(dst, bound, src, original.length);
      assertTrue(compressed > 0);

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = Zstd.zstd().decompress(decompressDst, original.length, dst, compressed);
      assertArrayEquals(
          original, decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void testMultithreadedCompressionRoundTrip() throws Exception {
    // Single-threaded libzstd builds report an upper bound of 0 workers; skip there.
    assumeTrue(
        ZstdCompressionParameter.NB_WORKERS.bounds().upperBound() >= 2,
        "native zstd lacks multithreading support");

    byte[] original = new byte[4 * 1024 * 1024];
    new Random(23).nextBytes(original);
    Arrays.fill(original, 1024 * 1024, 3 * 1024 * 1024, (byte) 7);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = Zstd.zstd().createCompressionContext();
        ZstdDecompressionContext dctx = Zstd.zstd().createDecompressionContext()) {
      cctx.parameter(ZstdCompressionParameter.NB_WORKERS, 2);

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long bound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(bound);
      long compressed = cctx.compress(dst, bound, src, original.length);

      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = dctx.decompress(decompressDst, original.length, dst, compressed);
      assertArrayEquals(
          original, decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void testContextReuse() throws Exception {
    byte[] data1 = "First data".getBytes(StandardCharsets.UTF_8);
    byte[] data2 = "Second data set".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = Zstd.zstd().createCompressionContext();
        ZstdDecompressionContext dctx = Zstd.zstd().createDecompressionContext()) {

      for (byte[] original : new byte[][] {data1, data2}) {
        MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
        long compressBound = Zstd.zstd().compressBound(original.length);
        MemorySegment dst = arena.allocate(compressBound);

        long compressed = cctx.compress(dst, compressBound, src, original.length);

        MemorySegment decompressDst = arena.allocate(original.length);
        long decompressed = dctx.decompress(decompressDst, original.length, dst, compressed);

        byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
        assertArrayEquals(original, result);
      }
    }
  }
}
