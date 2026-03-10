package dev.hallock.zstd.test;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdResult;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdUtilityTest {

    @Test
    void testVersionInfo() {
        int versionNumber = Zstd.versionNumber();
        assertTrue(versionNumber > 0);

        String versionString = Zstd.versionString();
        assertNotNull(versionString);
        assertFalse(versionString.isEmpty());
    }

    @Test
    void testCompressionLevelLimits() {
        int minLevel = Zstd.minCompressionLevel();
        int maxLevel = Zstd.maxCompressionLevel();
        int defaultLevel = Zstd.defaultCompressionLevel();

        assertTrue(minLevel < 0);
        assertTrue(maxLevel > 0);
        assertTrue(defaultLevel >= minLevel && defaultLevel <= maxLevel);
    }

    @Test
    void testCompressBound() {
        long bound1 = Zstd.compressBound(1024);
        long bound2 = Zstd.compressBound(2048);

        assertTrue(bound1 > 1024);
        assertTrue(bound2 > 2048);
        assertTrue(bound2 > bound1);
    }

    @Test
    void testGetFrameContentSize() throws ZstdException {
        byte[] original = "Test frame content size".getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);

            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                Zstd.defaultCompressionLevel()).orElseThrow();

            long frameSize = Zstd.decompressBound(dst, compressed.result());
            assertEquals(original.length, frameSize);
        }
    }

    @Test
    void testFindFrameCompressedSize() throws ZstdException {
        byte[] original = "Frame size detection test".getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);

            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                Zstd.defaultCompressionLevel()).orElseThrow();

            long frameCompressedSize = Zstd.findFrameCompressedSize(dst, compressed.result());
            assertEquals(compressed.result(), frameCompressedSize);
        }
    }

    @Test
    void testErrorHandling() {
        assertTrue(Zstd.isError(-1));
        assertFalse(Zstd.isError(0));
        assertFalse(Zstd.isError(100));

        ZstdResult errorResult = ZstdResult.from(-1);
        assertInstanceOf(ZstdResult.Error.class, errorResult);
    }
}

