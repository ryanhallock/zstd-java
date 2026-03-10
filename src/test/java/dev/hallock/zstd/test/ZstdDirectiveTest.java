package dev.hallock.zstd.test;

import dev.hallock.zstd.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ZstdDirectiveTest {

    @Test
    void testEndDirectiveValues() {
        Assertions.assertEquals(0, ZstdEndDirective.CONTINUE.value());
        assertEquals(1, ZstdEndDirective.FLUSH.value());
        assertEquals(2, ZstdEndDirective.END.value());
    }

    @Test
    void testEndDirectiveFromValue() {
        assertTrue(ZstdEndDirective.fromValue(0).isPresent());
        assertTrue(ZstdEndDirective.fromValue(1).isPresent());
        assertTrue(ZstdEndDirective.fromValue(2).isPresent());
        assertFalse(ZstdEndDirective.fromValue(99).isPresent());
        
        assertEquals(ZstdEndDirective.CONTINUE, ZstdEndDirective.fromValue(0).get());
        assertEquals(ZstdEndDirective.FLUSH, ZstdEndDirective.fromValue(1).get());
        assertEquals(ZstdEndDirective.END, ZstdEndDirective.fromValue(2).get());
    }

    @Test
    void testResetDirectiveValues() {
        Assertions.assertEquals(0, ZstdResetDirective.NONE.value());
        assertEquals(1, ZstdResetDirective.SESSION_ONLY.value());
        assertEquals(2, ZstdResetDirective.PARAMETERS.value());
        assertEquals(3, ZstdResetDirective.SESSION_AND_PARAMETERS.value());
    }

    @Test
    void testResetDirectiveFromValue() {
        assertTrue(ZstdResetDirective.fromValue(0).isPresent());
        assertTrue(ZstdResetDirective.fromValue(1).isPresent());
        assertTrue(ZstdResetDirective.fromValue(2).isPresent());
        assertTrue(ZstdResetDirective.fromValue(3).isPresent());
        assertFalse(ZstdResetDirective.fromValue(99).isPresent());

        assertEquals(ZstdResetDirective.NONE, ZstdResetDirective.fromValue(0).get());
        assertEquals(ZstdResetDirective.SESSION_ONLY, ZstdResetDirective.fromValue(1).get());
        assertEquals(ZstdResetDirective.PARAMETERS, ZstdResetDirective.fromValue(2).get());
        assertEquals(ZstdResetDirective.SESSION_AND_PARAMETERS, ZstdResetDirective.fromValue(3).get());
    }

    @Test
    void testEndDirectiveContinue() throws Exception {
        byte[] data = "test data".repeat(10).getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext ctx = new ZstdCompressionContext()) {
            
            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
            MemorySegment output = arena.allocate(Zstd.compressBound(data.length));
            
            ZstdInputBuffer inBuf = new ZstdInputBuffer(arena, input);
            ZstdOutputBuffer outBuf = new ZstdOutputBuffer(arena, output);
            
            inBuf.size(data.length);
            inBuf.position(0);
            outBuf.position(0);
            
            ctx.compressStream(outBuf, inBuf, ZstdEndDirective.CONTINUE);
            assertTrue(outBuf.position() >= 0);
        }
    }

    @Test
    void testEndDirectiveFlush() throws Exception {
        byte[] data = "flush test".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext ctx = new ZstdCompressionContext()) {
            
            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
            MemorySegment output = arena.allocate(Zstd.compressBound(data.length));
            
            ZstdInputBuffer inBuf = new ZstdInputBuffer(arena, input);
            ZstdOutputBuffer outBuf = new ZstdOutputBuffer(arena, output);
            
            inBuf.size(data.length);
            inBuf.position(0);
            outBuf.position(0);
            
            ctx.compressStream(outBuf, inBuf, ZstdEndDirective.FLUSH);
            assertTrue(outBuf.position() >= 0);
        }
    }

    @Test
    void testEndDirectiveEnd() throws Exception {
        byte[] data = "end test".getBytes(StandardCharsets.UTF_8);
        
        try (Arena arena = Arena.ofConfined();
             ZstdCompressionContext ctx = new ZstdCompressionContext()) {
            
            MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
            MemorySegment output = arena.allocate(Zstd.compressBound(data.length));
            
            ZstdInputBuffer inBuf = new ZstdInputBuffer(arena, input);
            ZstdOutputBuffer outBuf = new ZstdOutputBuffer(arena, output);
            
            inBuf.size(data.length);
            inBuf.position(0);
            outBuf.position(0);
            
            ctx.compressStream(outBuf, inBuf, ZstdEndDirective.END);
            assertTrue(outBuf.position() > 0);
        }
    }
}

