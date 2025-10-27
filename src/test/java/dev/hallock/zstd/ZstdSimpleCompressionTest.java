
package dev.hallock.zstd;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdSimpleCompressionTest {

    @Test
    void testSimpleCompressionDecompression() throws ZstdException {
        byte[] original = "Hello World!".getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);

            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                    Zstd.defaultCompressionLevel()).orElseThrow();

            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = Zstd.decompress(decompressDst, original.length,
                    dst, compressed.result()).orElseThrow();

            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testDifferentCompressionLevels() throws ZstdException {
        byte[] original = "Test data for compression".repeat(100).getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);

            for (int level : new int[]{1, 3, 5, 7, 9}) {
                MemorySegment dst = arena.allocate(compressBound);
                ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                        level).orElseThrow();

                assertTrue(compressed.result() < original.length);
                assertTrue(compressed.result() > 0);
            }
        }
    }

    @Test
    void testEmptyData() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocate(1);
            long compressBound = Zstd.compressBound(1);
            MemorySegment dst = arena.allocate(compressBound);

            ZstdResult compressed = Zstd.compress(dst, compressBound, src, 0,
                    Zstd.defaultCompressionLevel());
            assertInstanceOf(ZstdResult.Ok.class, compressed);
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
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);

            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                    Zstd.defaultCompressionLevel()).orElseThrow();

            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = Zstd.decompress(decompressDst, original.length,
                    dst, compressed.result()).orElseThrow();

            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testGetFrameContentSize() throws ZstdException {
        byte[] original = "Frame size test".getBytes(StandardCharsets.UTF_8);

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
}

