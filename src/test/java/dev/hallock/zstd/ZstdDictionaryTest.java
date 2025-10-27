package dev.hallock.zstd;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdDictionaryTest {

    @Test
    void testCompressionDictionary() throws Exception {
        byte[] dict = "common words: the, and, test, data".repeat(10).getBytes(StandardCharsets.UTF_8);
        byte[] original = "test data with common words".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionDictionary cdict = new ZstdCompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), Zstd.defaultCompressionLevel());
             ZstdCompressionContext cctx = new ZstdCompressionContext()) {
            
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);
            
            cctx.refDictionary(cdict).orElseThrow();
            ZstdResult.Ok compressed = cctx.compress(dst, compressBound, src, original.length).orElseThrow();
            
            assertTrue(compressed.result() > 0);
            assertTrue(cdict.sizeOf() > 0);
        }
    }

    @Test
    void testDecompressionDictionary() throws Exception {
        byte[] dict = "common words: the, and, test, data".repeat(10).getBytes(StandardCharsets.UTF_8);
        byte[] original = "test data with common words".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionDictionary cdict = new ZstdCompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), Zstd.defaultCompressionLevel());
             ZstdDecompressionDictionary ddict = new ZstdDecompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict));
             ZstdCompressionContext cctx = new ZstdCompressionContext();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {
            
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);
            
            cctx.refDictionary(cdict).orElseThrow();
            ZstdResult.Ok compressed = cctx.compress(dst, compressBound, src, original.length).orElseThrow();
            
            MemorySegment decompressDst = arena.allocate(original.length);
            dctx.refDictionary(ddict).orElseThrow();
            ZstdResult.Ok decompressed = dctx.decompress(decompressDst, original.length,
                dst, compressed.result()).orElseThrow();
            
            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testDictionaryWithPrefix() throws Exception {
        byte[] prefix = "prefix data".getBytes(StandardCharsets.UTF_8);
        byte[] original = "prefix data and more".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext cctx = new ZstdCompressionContext();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {
            
            MemorySegment prefixSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, prefix);
            cctx.refPrefix(prefixSeg, prefix.length).orElseThrow();
            
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);
            
            ZstdResult.Ok compressed = cctx.compress(dst, compressBound, src, original.length).orElseThrow();
            
            dctx.refPrefix(prefixSeg, prefix.length).orElseThrow();
            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = dctx.decompress(decompressDst, original.length,
                dst, compressed.result()).orElseThrow();
            
            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testLoadDictionary() throws Exception {
        byte[] dict = "dictionary content".getBytes(StandardCharsets.UTF_8);
        byte[] original = "test with dictionary content".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext cctx = new ZstdCompressionContext();
             ZstdDecompressionContext dctx = new ZstdDecompressionContext()) {
            
            MemorySegment dictSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, dict);
            cctx.loadDictionary(dictSeg, dict.length).orElseThrow();
            
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
            long compressBound = Zstd.compressBound(original.length);
            MemorySegment dst = arena.allocate(compressBound);
            
            ZstdResult.Ok compressed = cctx.compress(dst, compressBound, src, original.length).orElseThrow();
            
            dctx.loadDictionary(dictSeg, dict.length).orElseThrow();
            MemorySegment decompressDst = arena.allocate(original.length);
            ZstdResult.Ok decompressed = dctx.decompress(decompressDst, original.length,
                dst, compressed.result()).orElseThrow();
            
            byte[] result = decompressDst.asSlice(0, decompressed.result()).toArray(ValueLayout.JAVA_BYTE);
            assertArrayEquals(original, result);
        }
    }

    @Test
    void testGetDictID() throws Exception {
        byte[] dict = "dictionary with ID".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionDictionary cdict = new ZstdCompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), Zstd.defaultCompressionLevel())) {

            int dictID = cdict.dictID();
            assertTrue(dictID >= 0);
        }
    }
}

