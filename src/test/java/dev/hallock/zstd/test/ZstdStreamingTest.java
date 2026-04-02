package dev.hallock.zstd.test;

import dev.hallock.zstd.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

public class ZstdStreamingTest {

    public static List<byte[]> provideDataSets() {
        Random random = new Random(67);

        byte[] empty = new byte[0];

        byte[] smallRandom = new byte[50];
        random.nextBytes(smallRandom);

        byte[] largeRandom = new byte[1024 * 1024]; // 1MB
        random.nextBytes(largeRandom);

        byte[] largeCompressible = "This is a highly compressible string. ".repeat(10000).getBytes(StandardCharsets.UTF_8); // ~380KB

        return Arrays.asList(empty, smallRandom, largeCompressible, largeRandom);
    }

    public static List<Integer> provideChunkSizes() {
        return Arrays.asList(1, 10, 1024, 4096);
    }

    public static Stream<Arguments> provideStreamingData() {
        return provideDataSets().stream()
                .flatMap(data -> provideChunkSizes().stream()
                        .map(chunkSize -> Arguments.of(data, chunkSize)));
    }

    @ParameterizedTest
    @MethodSource("provideStreamingData")
    public void testStreamingWithSmallOutputBuffer(byte[] testData, int chunkSize) throws ZstdException {
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext cctx = new ZstdCompressionContext();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {

            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
            MemorySegment compressedDst = arena.allocate(Zstd.compressBound(testData.length) + 1024);
            MemorySegment decompressedDst = arena.allocate(testData.length);

            ZstdInputBuffer inBuffer = new ZstdInputBuffer(arena, src);
            ZstdOutputBuffer outBuffer = new ZstdOutputBuffer(arena, compressedDst);
            outBuffer.size(chunkSize); // Sub-buffer output buffer

            while (inBuffer.position() < inBuffer.size()) {
                ZstdEndDirective directive = ZstdEndDirective.CONTINUE;
                cctx.compressStream(outBuffer, inBuffer, directive).orElseThrow();

                if (outBuffer.position() == outBuffer.size()) {
                    // Output buffer is full, expand it
                    outBuffer.size(outBuffer.position() + chunkSize);
                }
            }

            while (true) {
                long remaining = cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.END).orElseThrow().result();
                if (remaining == 0) break;

                if (outBuffer.position() == outBuffer.size()) {
                    outBuffer.size(outBuffer.position() + chunkSize);
                }
            }

            long compressedSize = outBuffer.position();
            MemorySegment actualCompressed = compressedDst.asSlice(0, compressedSize);

            ZstdInputBuffer dInBuf = new ZstdInputBuffer(arena, actualCompressed);
            ZstdOutputBuffer dOutBuf = new ZstdOutputBuffer(arena, decompressedDst);
            dOutBuf.size(chunkSize); // Sub-buffer output buffer for decompression

            while (dInBuf.position() < dInBuf.size()) {
                dctx.decompressStream(dOutBuf, dInBuf).orElseThrow();

                if (dOutBuf.position() == dOutBuf.size()) {
                    // Output buffer is full, expand it
                    dOutBuf.size(dOutBuf.position() + chunkSize);
                }
            }

            byte[] actualDecompressed = decompressedDst.asSlice(0, dOutBuf.position()).toArray(ValueLayout.JAVA_BYTE);
            Assertions.assertArrayEquals(testData, actualDecompressed);
        }
    }

    @ParameterizedTest
    @MethodSource("provideStreamingData")
    public void testStreamingWithFlush(byte[] testData, int chunkSize) throws ZstdException {
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext cctx = new ZstdCompressionContext();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {

            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
            MemorySegment compressedDst = arena.allocate(Zstd.compressBound(testData.length) + 1024);
            MemorySegment decompressedDst = arena.allocate(testData.length);

            ZstdInputBuffer inBuffer = new ZstdInputBuffer(arena, src);
            ZstdOutputBuffer outBuffer = new ZstdOutputBuffer(arena, compressedDst);
            outBuffer.size(chunkSize);

            ZstdInputBuffer dInBuf = new ZstdInputBuffer(arena, compressedDst);
            ZstdOutputBuffer dOutBuf = new ZstdOutputBuffer(arena, decompressedDst);
            dOutBuf.size(chunkSize);

            // Compress half
            long half = src.byteSize() / 2;
            inBuffer.size(half);

            while (inBuffer.position() < inBuffer.size()) {
                cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.CONTINUE).orElseThrow();
                if (outBuffer.position() == outBuffer.size()) {
                    outBuffer.size(outBuffer.position() + chunkSize);
                }
            }

            cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.FLUSH).orElseThrow();

            // Should be able to decompress this part immediately
            long partialCompressedSize = outBuffer.position();
            if (partialCompressedSize > 0) {
                dInBuf.size(partialCompressedSize);
                while (dInBuf.position() < dInBuf.size()) {
                    dctx.decompressStream(dOutBuf, dInBuf).orElseThrow();
                    if (dOutBuf.position() == dOutBuf.size()) {
                        dOutBuf.size(dOutBuf.position() + chunkSize);
                    }
                }
            }

            // Compress the rest
            inBuffer.size(src.byteSize());
            while (inBuffer.position() < inBuffer.size()) {
                cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.CONTINUE).orElseThrow();
                if (outBuffer.position() == outBuffer.size()) {
                    outBuffer.size(outBuffer.position() + chunkSize);
                }
            }

            while (true) {
                long remaining = cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.END).orElseThrow().result();
                if (remaining == 0) break;
                if (outBuffer.position() == outBuffer.size()) {
                    outBuffer.size(outBuffer.position() + chunkSize);
                }
            }

            // Decompress the rest
            long totalCompressedSize = outBuffer.position();
            if (totalCompressedSize > 0) {
                dInBuf.size(totalCompressedSize);

                while (dInBuf.position() < dInBuf.size()) {
                    dctx.decompressStream(dOutBuf, dInBuf).orElseThrow();
                    if (dOutBuf.position() == dOutBuf.size()) {
                        dOutBuf.size(dOutBuf.position() + chunkSize);
                    }
                }

                byte[] actualDecompressed = decompressedDst.asSlice(0, dOutBuf.position()).toArray(ValueLayout.JAVA_BYTE);
                Assertions.assertArrayEquals(testData, actualDecompressed);
            } else {
                Assertions.assertArrayEquals(new byte[0], testData);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("provideStreamingData")
    public void testStreamingMultipleChunks(byte[] testData, int chunkSize) throws ZstdException {
        try (Arena arena = Arena.ofConfined(); ZstdCompressionContext cctx = new ZstdCompressionContext();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {

            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
            MemorySegment compressedDst = arena.allocate(Zstd.compressBound(testData.length) + 1024);
            MemorySegment decompressedDst = arena.allocate(testData.length);

            ZstdInputBuffer inBuffer = new ZstdInputBuffer(arena, src);
            ZstdOutputBuffer outBuffer = new ZstdOutputBuffer(arena, compressedDst);

            // Compress in chunks
            while (inBuffer.position() < inBuffer.size()) {
                long remaining = inBuffer.size() - inBuffer.position();
                long currentChunk = Math.min(chunkSize, remaining);

                // Simulate smaller input size for this chunk
                long originalPosition = inBuffer.position();
                inBuffer.size(originalPosition + currentChunk);

                ZstdEndDirective directive = (inBuffer.size() == src.byteSize()) ? ZstdEndDirective.END : ZstdEndDirective.CONTINUE;
                cctx.compressStream(outBuffer, inBuffer, directive).orElseThrow();

                // Restore actual size for loop condition
                inBuffer.size(src.byteSize());
            }

            // Finish compression if we haven't already with END
            // Ensure ending works properly
            long remaining = cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.END).orElseThrow().result();
            Assertions.assertEquals(0, remaining, "Stream not fully flushed");

            long compressedSize = outBuffer.position();
            MemorySegment actualCompressed = compressedDst.asSlice(0, compressedSize);

            // Decompress in chunks
            ZstdInputBuffer dInBuf = new ZstdInputBuffer(arena, actualCompressed);
            ZstdOutputBuffer dOutBuf = new ZstdOutputBuffer(arena, decompressedDst);

            while (dInBuf.position() < dInBuf.size()) {
                long remainingIn = dInBuf.size() - dInBuf.position();
                long currentChunk = Math.min(chunkSize, remainingIn);

                long originalPosition = dInBuf.position();
                dInBuf.size(originalPosition + currentChunk);

                dctx.decompressStream(dOutBuf, dInBuf).orElseThrow();

                dInBuf.size(actualCompressed.byteSize());
            }

            byte[] actualDecompressed = decompressedDst.asSlice(0, dOutBuf.position()).toArray(ValueLayout.JAVA_BYTE);
            Assertions.assertArrayEquals(testData, actualDecompressed);
        }
    }
}
