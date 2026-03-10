package dev.hallock.zstd.test;

import dev.hallock.zstd.*;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdEdgeCaseTest {

    @Test
    void testSingleByte() throws ZstdException {
        byte[] original = new byte[]{42};

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
    void testHighlyCompressibleData() throws ZstdException {
        byte[] original = new byte[10000];
        for (int i = 0; i < original.length; i++) {
            original[i] = 'A';
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);

            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                Zstd.defaultCompressionLevel()).orElseThrow();

            assertTrue(compressed.result() < original.length / 10);

            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = Zstd.decompress(decompressDst, original.length,
                dst, compressed.result()).orElseThrow();

            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testRandomData() throws ZstdException {
        byte[] original = new byte[5000];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) ((i * 17 + 31) % 256);
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
    void testAllZeros() throws ZstdException {
        byte[] original = new byte[1000];

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);

            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                Zstd.defaultCompressionLevel()).orElseThrow();

            assertTrue(compressed.result() < 100);

            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = Zstd.decompress(decompressDst, original.length,
                dst, compressed.result()).orElseThrow();

            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testUnicodeText() throws ZstdException {
        byte[] original = "Hello 世界 🌍 مرحبا мир".repeat(50).getBytes(StandardCharsets.UTF_8);

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
    void testBinaryData() throws ZstdException {
        byte[] original = new byte[2000];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (i & 0xFF);
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
    void testContextStreamCombination() throws Exception {
        byte[] original = "Context and stream test".repeat(100).getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext cctx = new ZstdCompressionContext()) {

            MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            MemorySegment outputSeg = arena.allocate(Zstd.compressBound(original.length));

            ZstdInputBuffer input = new ZstdInputBuffer(arena, inputSeg);
            ZstdOutputBuffer output = new ZstdOutputBuffer(arena, outputSeg);

            input.size(original.length);
            input.position(0);
            output.position(0);

            cctx.compressStream(output, input, ZstdEndDirective.END).orElseThrow();

            long compressedSize = output.position();
            assertTrue(compressedSize > 0);
        }
    }
}

