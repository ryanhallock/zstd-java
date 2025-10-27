package dev.hallock.zstd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZstdStrategyTest {

    private final byte[] testData = "Test data for different strategies".repeat(100).getBytes(StandardCharsets.UTF_8);

    public static Stream<Arguments> compressionLevelsProvider() {
        return IntStream.rangeClosed(1, Zstd.maxCompressionLevel())
                .mapToObj(Arguments::of);
    }

    @Test
    void testDefaultCompressionLevel() throws ZstdException {
        testWithLevel(Zstd.defaultCompressionLevel());
    }

    @Test
    void testMaxCompressionLevel() throws ZstdException {
        testWithLevel(Zstd.maxCompressionLevel());
    }

    @Test
    void testMinCompressionLevel() throws ZstdException {
        testWithLevel(1);
    }

    @ParameterizedTest
    @MethodSource("compressionLevelsProvider")
    void testWithLevel(int level) throws ZstdException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
            long compressBound = Zstd.compressBound(testData.length);
            MemorySegment dst = arena.allocate(compressBound);
            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, testData.length, level).orElseThrow();


            MemorySegment decompressDst = arena.allocate(testData.length);
            Zstd.decompress(decompressDst, testData.length, dst, compressed.result()).orElseThrow();

            assertArrayEquals(testData, decompressDst.toArray(ValueLayout.JAVA_BYTE), "Failed with compression level: " + level);
            assertTrue(compressed.result() > 0, "Compressed size should be positive");
            assertTrue(compressed.result() <= testData.length);
        }
    }

    @Test
    void testCompressionRatioByLevel() throws ZstdException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
            long compressBound = Zstd.compressBound(testData.length);

            int[] levels = {1, 5, 9};
            long[] sizes = new long[3];

            for (int i = 0; i < sizes.length; i++) {
                MemorySegment dst = arena.allocate(compressBound);
                ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, testData.length,
                        levels[i]).orElseThrow();
                sizes[i] = compressed.result();
            }

            // Higher levels should produce equal or better compression
            assertTrue(sizes[2] <= sizes[1]);
            assertTrue(sizes[1] <= sizes[0]);
        }
    }

    @Test
    void testCompressionLevelRange() {
        int minLevel = Zstd.minCompressionLevel();
        int maxLevel = Zstd.maxCompressionLevel();
        int defaultLevel = Zstd.defaultCompressionLevel();

        // Verify level constraints
        assertTrue(minLevel < 0, "Min level should be negative");
        assertTrue(maxLevel > 0, "Max level should be positive");
        assertTrue(defaultLevel >= minLevel && defaultLevel <= maxLevel,
                "Default level should be within range");
    }

    @Test
    void testMultipleLevelsProduceDifferentResults() throws ZstdException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
            long compressBound = Zstd.compressBound(testData.length);

            // Compress with different levels
            MemorySegment dst1 = arena.allocate(compressBound);
            MemorySegment dst2 = arena.allocate(compressBound);

            ZstdResult.Ok compressed1 = Zstd.compress(dst1, compressBound, src, testData.length, 1).orElseThrow();
            ZstdResult.Ok compressed2 = Zstd.compress(dst2, compressBound, src, testData.length, 10).orElseThrow();

            // Both should work
            assertTrue(compressed1.result() > 0);
            assertTrue(compressed2.result() > 0);

            // Level 10 should typically compress better than level 1 (or at least as well)
            assertTrue(compressed2.result() <= compressed1.result());
        }
    }
}

