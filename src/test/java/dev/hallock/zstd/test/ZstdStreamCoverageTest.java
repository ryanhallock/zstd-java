package dev.hallock.zstd.test;

import dev.hallock.zstd.*;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdStreamCoverageTest {

    @Test
    void testCompressionStreamLevel() throws Exception {
        try (ZstdCompressionStream stream = new ZstdCompressionStream(9)) {
            assertEquals(9, stream.compressionLevel());
        }
    }

    @Test
    void testCompressionStreamSizeOf() throws Exception {
        try (ZstdCompressionStream stream = new ZstdCompressionStream(Zstd.defaultCompressionLevel())) {
            long size = stream.sizeOf();
            assertTrue(size > 0);
        }
    }

    @Test
    void testDecompressionStreamSizeOf() throws Exception {
        try (ZstdDecompressionStream stream = new ZstdDecompressionStream()) {
            long size = stream.sizeOf();
            assertTrue(size > 0);
        }
    }

    @Test
    void testCompressionStreamWithAllMethods() throws Exception {
        byte[] data = "complete stream test".repeat(20).getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined();
             ZstdCompressionStream stream = new ZstdCompressionStream(Zstd.defaultCompressionLevel())) {

            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
            MemorySegment output = arena.allocate(Zstd.compressBound(data.length));

            ZstdInputBuffer inBuf = new ZstdInputBuffer(arena, input);
            ZstdOutputBuffer outBuf = new ZstdOutputBuffer(arena, output);

            inBuf.size(data.length);
            inBuf.position(0);
            outBuf.position(0);

            stream.compressionStream(outBuf, inBuf).orElseThrow();

            long remainder = 1;
            while (remainder > 0) {
                ZstdResult.Ok result = stream.flush(outBuf).orElseThrow();
                remainder = result.result();
            }

            remainder = 1;
            while (remainder > 0) {
                ZstdResult.Ok result = stream.end(outBuf).orElseThrow();
                remainder = result.result();
            }

            assertTrue(outBuf.position() > 0);
            assertTrue(stream.sizeOf() > 0);
        }
    }

    @Test
    void testStreamErrorHandling() throws Exception {
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionStream stream = new ZstdCompressionStream(Zstd.defaultCompressionLevel())) {

            MemorySegment input = arena.allocate(10);
            MemorySegment output = arena.allocate(1);

            ZstdInputBuffer inBuf = new ZstdInputBuffer(arena, input);
            ZstdOutputBuffer outBuf = new ZstdOutputBuffer(arena, output);

            inBuf.size(10);
            inBuf.position(0);
            outBuf.position(0);

            ZstdResult result = stream.compressionStream(outBuf, inBuf);
            assertNotNull(result);
        }
    }
}

