package dev.hallock.zstd;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;

class ZstdBufferTest {

    @Test
    void testInputBufferOperations() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment mem = arena.allocate(100);
            ZstdInputBuffer buffer = new ZstdInputBuffer(arena, mem);
            
            buffer.position(10);
            assertEquals(10, buffer.position());
            
            buffer.size(50);
            assertEquals(50, buffer.size());
            
            assertNotNull(buffer.input());
        }
    }

    @Test
    void testOutputBufferOperations() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment mem = arena.allocate(100);
            ZstdOutputBuffer buffer = new ZstdOutputBuffer(arena, mem);
            
            buffer.position(15);
            assertEquals(15, buffer.position());
            
            buffer.size(60);
            assertEquals(60, buffer.size());
            
            assertNotNull(buffer.output());
        }
    }

    @Test
    void testInputBufferInStream() throws Exception {
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionStream stream = new ZstdCompressionStream(Zstd.defaultCompressionLevel())) {

            MemorySegment input = arena.allocate(1000);
            MemorySegment output = arena.allocate(2000);
            
            ZstdInputBuffer inBuf = new ZstdInputBuffer(arena, input);
            ZstdOutputBuffer outBuf = new ZstdOutputBuffer(arena, output);
            
            inBuf.size(1000);
            inBuf.position(0);
            outBuf.position(0);
            
            stream.compressionStream(outBuf, inBuf);
            
            assertTrue(inBuf.position() >= 0);
            assertTrue(outBuf.position() >= 0);
        }
    }

    @Test
    void testOutputBufferInStream() throws Exception {
        try (Arena arena = Arena.ofConfined();
             ZstdDecompressionStream stream = new ZstdDecompressionStream()) {
            
            byte[] compressedData = compressTestData();
            MemorySegment input = arena.allocate(compressedData.length);
            input.copyFrom(MemorySegment.ofArray(compressedData));
            MemorySegment output = arena.allocate(1000);
            
            ZstdInputBuffer inBuf = new ZstdInputBuffer(arena, input);
            ZstdOutputBuffer outBuf = new ZstdOutputBuffer(arena, output);
            
            inBuf.size(compressedData.length);
            inBuf.position(0);
            outBuf.position(0);
            outBuf.size(1000);
            
            stream.decompressStream(outBuf, inBuf);
            
            assertTrue(outBuf.position() > 0);
        }
    }

    private byte[] compressTestData() throws ZstdException {
        try (Arena arena = Arena.ofConfined()) {
            byte[] data = "test".getBytes();
            MemorySegment src = arena.allocate(data.length);
            src.copyFrom(MemorySegment.ofArray(data));
            MemorySegment dst = arena.allocate(Zstd.compressBound(data.length));
            
            ZstdResult.Ok result = Zstd.compress(dst, Zstd.compressBound(data.length), 
                src, data.length, Zstd.defaultCompressionLevel()).orElseThrow();

            byte[] compressed = new byte[(int)result.result()];
            MemorySegment.copy(dst, 0, MemorySegment.ofArray(compressed), 0, result.result());
            return compressed;
        }
    }
}

