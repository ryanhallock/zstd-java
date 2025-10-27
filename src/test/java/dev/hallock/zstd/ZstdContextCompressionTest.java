package dev.hallock.zstd;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdContextCompressionTest {

    @Test
    void testCompressionContext() throws Exception {
        byte[] original = "Context compression test".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext cctx = new ZstdCompressionContext()) {
            
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);
            
            ZstdResult.Ok compressed = cctx.compress(dst, compressBound, src, original.length).orElseThrow();
            
            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = Zstd.decompress(decompressDst, original.length,
                dst, compressed.result()).orElseThrow();
            
            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testDecompressionContext() throws Exception {
        byte[] original = "Decompression context test".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {
            
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);
            
            ZstdResult.Ok compressed = Zstd.compress(dst, compressBound, src, original.length,
                Zstd.defaultCompressionLevel()).orElseThrow();

            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = dctx.decompress(decompressDst, original.length,
                dst, compressed.result()).orElseThrow();
            
            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testContextReuse() throws Exception {
        byte[] data1 = "First data".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "Second data set".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext cctx = new ZstdCompressionContext();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {
            
            for (byte[] original : new byte[][]{data1, data2}) {
                MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
                long compressBound = Zstd.compressBound(original.length);
                MemorySegment dst = arena.allocate(compressBound);
                
                ZstdResult.Ok compressed = cctx.compress(dst, compressBound, src, original.length).orElseThrow();
                
                MemorySegment decompressDst = arena.allocate(original.length);
                ZstdResult.Ok decompressed = dctx.decompress(decompressDst, original.length,
                    dst, compressed.result()).orElseThrow();
                
                byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
                assertArrayEquals(original, result);
            }
        }
    }
}

