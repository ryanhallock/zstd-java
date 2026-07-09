package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Behavior of the streaming end directives. The raw enum values are pinned against the native
 * header in {@link ZstdAbiDriftTest}.
 */
@ZstdTest
class ZstdDirectiveTest {

  @Test
  void testEndDirectiveContinue(Zstd zstd) {
    byte[] data = "test data".repeat(10).getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {

      MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      MemorySegment output = arena.allocate(zstd.compressBound(data.length));

      ZstdInputBuffer inBuf = zstd.createInputBuffer(arena, input);
      ZstdOutputBuffer outBuf = zstd.createOutputBuffer(arena, output);

      ctx.compressStream(outBuf, inBuf, ZstdEndDirective.CONTINUE);

      // With ample output space, CONTINUE must consume all input (it may buffer it internally
      // without producing output yet).
      assertEquals(inBuf.size(), inBuf.position(), "CONTINUE did not consume all input");
    }
  }

  @Test
  void testEndDirectiveFlush(Zstd zstd) {
    byte[] data = "flush test".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      MemorySegment output = arena.allocate(zstd.compressBound(data.length));

      ZstdInputBuffer inBuf = zstd.createInputBuffer(arena, input);
      ZstdOutputBuffer outBuf = zstd.createOutputBuffer(arena, output);

      long remaining;
      do {
        remaining = ctx.compressStream(outBuf, inBuf, ZstdEndDirective.FLUSH);
      } while (remaining > 0);

      // Everything consumed so far must be decodable at a flush boundary.
      assertEquals(inBuf.size(), inBuf.position());
      MemorySegment decompressed = arena.allocate(data.length);
      ZstdInputBuffer dIn = zstd.createInputBuffer(arena, output.asSlice(0, outBuf.position()));
      ZstdOutputBuffer dOut = zstd.createOutputBuffer(arena, decompressed);
      dctx.decompressStream(dOut, dIn);

      assertEquals(data.length, dOut.position(), "FLUSH boundary did not expose all input");
      assertArrayEquals(data, decompressed.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void testEndDirectiveEnd(Zstd zstd) {
    byte[] data = "end test".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {

      MemorySegment input = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      MemorySegment output = arena.allocate(zstd.compressBound(data.length));

      ZstdInputBuffer inBuf = zstd.createInputBuffer(arena, input);
      ZstdOutputBuffer outBuf = zstd.createOutputBuffer(arena, output);

      long remaining;
      do {
        remaining = ctx.compressStream(outBuf, inBuf, ZstdEndDirective.END);
      } while (remaining > 0);

      // END must produce a complete frame decodable by simple decompression.
      assertTrue(outBuf.position() > 0);
      MemorySegment decompressed = arena.allocate(data.length);
      long size = zstd.decompress(decompressed, data.length, output, outBuf.position());

      assertEquals(data.length, size);
      assertArrayEquals(data, decompressed.toArray(ValueLayout.JAVA_BYTE));
    }
  }
}
