
package dev.hallock.zstd.test;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdException;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class ZstdExceptionTest {

    @Test
    void testCompressionError() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment src = arena.allocate(100);
            MemorySegment dst = arena.allocate(1);

            assertThrows(ZstdException.class, () -> {
                Zstd.compress(dst, 1, src, 100, Zstd.defaultCompressionLevel()).orElseThrow();
            });
        }
    }

    @Test
    void testDecompressionError() {
        try (Arena arena = Arena.ofConfined()) {
            byte[] invalidData = new byte[]{1, 2, 3, 4, 5};
            MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, invalidData);
            MemorySegment dst = arena.allocate(100);

            assertThrows(ZstdException.class, () -> {
                Zstd.decompress(dst, 100, src, invalidData.length).orElseThrow();
            });
        }
    }

    @Test
    void testContextCloseError() throws Exception {
        ZstdCompressionContext ctx = new ZstdCompressionContext();
        ctx.close();
        assertNotNull(ctx);
    }
}