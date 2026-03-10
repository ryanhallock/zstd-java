package dev.hallock.zstd.test;

import dev.hallock.zstd.*;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdStreamingTest {

    @Test
    void testCompressionStream() throws Exception {
        byte[] original = "Streaming compression test data".repeat(100).getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionStream cStream = new ZstdCompressionStream(Zstd.defaultCompressionLevel())) {

            MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            MemorySegment outputSeg = arena.allocate(Zstd.compressBound(original.length));
            
            ZstdInputBuffer input = new ZstdInputBuffer(arena, inputSeg);
            ZstdOutputBuffer output = new ZstdOutputBuffer(arena, outputSeg);
            
            input.size(original.length);
            input.position(0);
            output.position(0);
            
            cStream.compressionStream(output, input).orElseThrow();
            
            long remainder = 1;
            while (remainder > 0) {
                ZstdResult.Ok result = cStream.end(output).orElseThrow();
                remainder = result.result();
            }
            
            long compressedSize = output.position();
            assertTrue(compressedSize > 0);
            assertTrue(compressedSize < original.length);
        }
    }

    @Test
    void testDecompressionStream() throws Exception {
        byte[] original = "Streaming decompression test".repeat(50).getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionStream cStream = new ZstdCompressionStream(Zstd.defaultCompressionLevel());
             ZstdDecompressionStream dStream = new ZstdDecompressionStream()) {
            
            MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            MemorySegment compressedSeg = arena.allocate(Zstd.compressBound(original.length));
            
            ZstdInputBuffer cInput = new ZstdInputBuffer(arena, inputSeg);
            ZstdOutputBuffer cOutput = new ZstdOutputBuffer(arena, compressedSeg);
            
            cInput.size(original.length);
            cInput.position(0);
            cOutput.position(0);
            
            cStream.compressionStream(cOutput, cInput).orElseThrow();
            long remainder = 1;
            while (remainder > 0) {
                ZstdResult.Ok result = cStream.end(cOutput).orElseThrow();
                remainder = result.result();
            }
            
            long compressedSize = cOutput.position();
            
            MemorySegment decompressedSeg = arena.allocate(original.length);
            ZstdInputBuffer dInput = new ZstdInputBuffer(arena, compressedSeg.asSlice(0, compressedSize));
            ZstdOutputBuffer dOutput = new ZstdOutputBuffer(arena, decompressedSeg);
            
            dInput.size(compressedSize);
            dInput.position(0);
            dOutput.position(0);
            
            dStream.decompressStream(dOutput, dInput).orElseThrow();
            
            byte[] result = decompressedSeg.asSlice(0, dOutput.position()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testStreamingWithFlush() throws Exception {
        byte[] chunk1 = "First chunk of data".getBytes(StandardCharsets.UTF_8);
        byte[] chunk2 = "Second chunk of data".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionStream cStream = new ZstdCompressionStream(Zstd.defaultCompressionLevel())) {

            MemorySegment outputSeg = arena.allocate(Zstd.compressBound(chunk1.length + chunk2.length));
            ZstdOutputBuffer output = new ZstdOutputBuffer(arena, outputSeg);
            output.position(0);
            
            for (byte[] chunk : new byte[][]{chunk1, chunk2}) {
                MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, chunk);
                ZstdInputBuffer input = new ZstdInputBuffer(arena, inputSeg);
                input.size(chunk.length);
                input.position(0);
                
                cStream.compressionStream(output, input).orElseThrow();
                
                long remainder = 1;
                while (remainder > 0) {
                    ZstdResult.Ok result = cStream.flush(output).orElseThrow();
                    remainder = result.result();
                }
            }
            
            long remainder = 1;
            while (remainder > 0) {
                ZstdResult.Ok result = cStream.end(output).orElseThrow();
                remainder = result.result();
            }
            
            assertTrue(output.position() > 0);
        }
    }

    @Test
    void testMultipleFramesStreaming() throws Exception {
        byte[] data1 = "Frame 1".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "Frame 2 data".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outputSeg = arena.allocate(Zstd.compressBound(data1.length) + 
                                                      Zstd.compressBound(data2.length));
            long totalCompressed = 0;
            
            for (byte[] data : new byte[][]{data1, data2}) {
                try (ZstdCompressionStream cStream = new ZstdCompressionStream(Zstd.defaultCompressionLevel())) {
                    MemorySegment inputSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
                    ZstdInputBuffer input = new ZstdInputBuffer(arena, inputSeg);
                    ZstdOutputBuffer output = new ZstdOutputBuffer(arena, outputSeg.asSlice(totalCompressed));
                    
                    input.size(data.length);
                    input.position(0);
                    output.position(0);
                    
                    cStream.compressionStream(output, input).orElseThrow();
                    
                    long remainder = 1;
                    while (remainder > 0) {
                        ZstdResult.Ok result = cStream.end(output).orElseThrow();
                        remainder = result.result();
                    }
                    
                    totalCompressed += output.position();
                }
            }
            
            assertTrue(totalCompressed > 0);
        }
    }
}

