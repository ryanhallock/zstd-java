package dev.hallock.zstd.test;

import dev.hallock.zstd.*;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdDictionaryCoverageTest {

    @Test
    void testCompressionDictionarySize() throws Exception {
        byte[] dict = "dictionary".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionDictionary cdict = new ZstdCompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), Zstd.defaultCompressionLevel())) {

            assertEquals(dict.length, cdict.size());
            assertTrue(cdict.sizeOf() > 0);
        }
    }

    @Test
    void testCompressionDictionaryLevel() throws Exception {
        byte[] dict = "test".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionDictionary cdict = new ZstdCompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), 
                 dict.length, 
                 9)) {

            assertEquals(9, cdict.compressionLevel());
        }
    }

    @Test
    void testCompressionDictionaryDictID() throws Exception {
        byte[] dict = "dict with id".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionDictionary cdict = new ZstdCompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), Zstd.defaultCompressionLevel())) {

            int dictID = cdict.dictID();
            assertTrue(dictID >= 0);
        }
    }

    @Test
    void testDecompressionDictionarySize() throws Exception {
        byte[] dict = "decompression dict".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdDecompressionDictionary ddict = new ZstdDecompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict))) {
            
            assertEquals(dict.length, ddict.size());
            assertTrue(ddict.sizeOf() > 0);
        }
    }

    @Test
    void testDecompressionDictionaryWithSize() throws Exception {
        byte[] dict = "test dict data".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dictSeg = arena.allocate(100);
            dictSeg.copyFrom(MemorySegment.ofArray(dict));
            
            try (ZstdDecompressionDictionary ddict = new ZstdDecompressionDictionary(dictSeg, dict.length)) {
                assertEquals(dict.length, ddict.size());
            }
        }
    }

    @Test
    void testDecompressionDictionaryDictID() throws Exception {
        byte[] dict = "dict id test".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdDecompressionDictionary ddict = new ZstdDecompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict))) {
            
            int dictID = ddict.dictID();
            assertTrue(dictID >= 0);
        }
    }

    @Test
    void testGetDictIDFromDict() {
        byte[] dict = "raw dict".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dictSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, dict);
            int dictID = Zstd.dictIdFromDict(dictSeg, dict.length);
            assertTrue(dictID >= 0);
        }
    }

    @Test
    void testGetDictIDFromFrame() throws ZstdException {
        byte[] data = "frame with dict".getBytes(StandardCharsets.UTF_8);
        byte[] dict = "dict".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionDictionary cdict = new ZstdCompressionDictionary(
                 arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), Zstd.defaultCompressionLevel());
             ZstdCompressionContext ctx = new ZstdCompressionContext()) {
            
            ctx.refDictionary(cdict).orElseThrow();
            
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
            MemorySegment dst = arena.allocate(Zstd.compressBound(data.length));
            
            ZstdResult.Ok compressed = ctx.compress(dst, Zstd.compressBound(data.length), 
                src, data.length).orElseThrow();
            
            int frameDict = Zstd.dictIdFromFrame(dst, compressed.result());
            assertTrue(frameDict >= 0);
        }
    }
}

