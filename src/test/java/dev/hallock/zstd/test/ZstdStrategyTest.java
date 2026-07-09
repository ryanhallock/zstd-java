package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdParameterBounds;
import dev.hallock.zstd.ZstdStrategy;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ZstdStrategyTest {

  private final byte[] testData =
      "Test data for different strategies".repeat(100).getBytes(StandardCharsets.UTF_8);

  public static Stream<Arguments> compressionLevelsProvider() {
    return IntStream.rangeClosed(1, Zstd.zstd().maxCompressionLevel()).mapToObj(Arguments::of);
  }

  @Test
  void testDefaultCompressionLevel() throws ZstdException {
    testWithLevel(Zstd.zstd().defaultCompressionLevel());
  }

  @Test
  void testMaxCompressionLevel() throws ZstdException {
    testWithLevel(Zstd.zstd().maxCompressionLevel());
  }

  @Test
  void testLevelOne() throws ZstdException {
    testWithLevel(1);
  }

  @Test
  void testNegativeCompressionLevelRoundTrip() {
    // Negative levels are the "fast" tier: valid, but they trade ratio for speed, so no size
    // expectations here, only a correct round trip.
    byte[] compressed = Zstd.zstd().compress(testData, -5);
    assertArrayEquals(testData, Zstd.zstd().decompress(compressed));
  }

  @Test
  void testMinCompressionLevel() {
    int minLevel = Zstd.zstd().minCompressionLevel();
    assertTrue(minLevel < 0, "min level should be negative");
    byte[] compressed = Zstd.zstd().compress(testData, minLevel);
    assertArrayEquals(testData, Zstd.zstd().decompress(compressed));
  }

  @ParameterizedTest
  @MethodSource("compressionLevelsProvider")
  void testWithLevel(int level) throws ZstdException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
      long compressBound = Zstd.zstd().compressBound(testData.length);
      MemorySegment dst = arena.allocate(compressBound);
      long compressed = Zstd.zstd().compress(dst, compressBound, src, testData.length, level);

      MemorySegment decompressDst = arena.allocate(testData.length);
      Zstd.zstd().decompress(decompressDst, testData.length, dst, compressed);

      assertArrayEquals(
          testData,
          decompressDst.toArray(ValueLayout.JAVA_BYTE),
          "Failed with compression level: " + level);
      assertTrue(compressed > 0, "Compressed size should be positive");
      assertTrue(compressed <= testData.length);
    }
  }

  @Test
  void testCompressionRatioByLevel() throws ZstdException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
      long compressBound = Zstd.zstd().compressBound(testData.length);

      int[] levels = {1, 5, 9};
      long[] sizes = new long[3];

      for (int i = 0; i < sizes.length; i++) {
        MemorySegment dst = arena.allocate(compressBound);
        long compressed = Zstd.zstd().compress(dst, compressBound, src, testData.length, levels[i]);
        sizes[i] = compressed;
      }

      // Higher levels should produce equal or better compression
      assertTrue(sizes[2] <= sizes[1]);
      assertTrue(sizes[1] <= sizes[0]);
    }
  }

  @Test
  void testCompressionLevelRange() {
    int minLevel = Zstd.zstd().minCompressionLevel();
    int maxLevel = Zstd.zstd().maxCompressionLevel();
    int defaultLevel = Zstd.zstd().defaultCompressionLevel();

    // Verify level constraints
    assertTrue(minLevel < 0, "Min level should be negative");
    assertTrue(maxLevel > 0, "Max level should be positive");
    assertTrue(
        defaultLevel >= minLevel && defaultLevel <= maxLevel,
        "Default level should be within range");
  }

  @Test
  void testMultipleLevelsProduceDifferentResults() throws ZstdException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
      long compressBound = Zstd.zstd().compressBound(testData.length);

      // Compress with different levels
      MemorySegment dst1 = arena.allocate(compressBound);
      MemorySegment dst2 = arena.allocate(compressBound);

      long compressed1 = Zstd.zstd().compress(dst1, compressBound, src, testData.length, 1);
      long compressed2 = Zstd.zstd().compress(dst2, compressBound, src, testData.length, 10);

      // Both should work
      assertTrue(compressed1 > 0);
      assertTrue(compressed2 > 0);

      // Level 10 should typically compress better than level 1 (or at least as well)
      assertTrue(compressed2 <= compressed1);
    }
  }

  @Test
  void testParameterBounds() throws ZstdException {
    ZstdParameterBounds bounds = ZstdCompressionParameter.COMPRESSION_LEVEL.bounds();
    assertTrue(bounds.lowerBound() < 0, "Lower bound for compression level should be negative");
    assertTrue(bounds.upperBound() > 0, "Upper bound for compression level should be positive");
  }

  @Test
  void testEveryStrategyCompressesAndRoundTrips() {
    Zstd zstd = Zstd.zstd();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
      long compressBound = zstd.compressBound(testData.length);
      MemorySegment dst = arena.allocate(compressBound);
      MemorySegment decompressDst = arena.allocate(testData.length);

      for (ZstdStrategy strategy : ZstdStrategy.values()) {
        try (ZstdCompressionContext ctx = zstd.createCompressionContext()) {
          ctx.parameter(ZstdCompressionParameter.STRATEGY, strategy.value());
          long compressed = ctx.compress(dst, compressBound, src, testData.length);
          long decompressed = zstd.decompress(decompressDst, testData.length, dst, compressed);
          assertEquals(testData.length, decompressed, "strategy " + strategy);
          assertArrayEquals(testData, decompressDst.toArray(ValueLayout.JAVA_BYTE));
        }
      }
    }
  }
}
