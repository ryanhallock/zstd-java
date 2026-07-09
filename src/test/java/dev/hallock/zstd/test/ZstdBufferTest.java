package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@ZstdTest
class ZstdBufferTest {

  @Test
  void testInputBufferOperations(Zstd zstd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment mem = arena.allocate(100);
      ZstdInputBuffer buffer = zstd.createInputBuffer(arena, mem);

      // A whole-segment buffer starts spanning the segment with nothing consumed.
      assertEquals(100, buffer.size());
      assertEquals(0, buffer.position());
      assertSame(mem, buffer.source());

      buffer.position(10);
      assertEquals(10, buffer.position());

      buffer.size(50);
      assertEquals(50, buffer.size());
    }
  }

  @Test
  void testOutputBufferOperations(Zstd zstd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment mem = arena.allocate(100);
      ZstdOutputBuffer buffer = zstd.createOutputBuffer(arena, mem);

      assertEquals(100, buffer.size());
      assertEquals(0, buffer.position());
      assertSame(mem, buffer.destination());

      buffer.position(15);
      assertEquals(15, buffer.position());

      buffer.size(60);
      assertEquals(60, buffer.size());
    }
  }

  @Test
  void testInputBufferInStream(Zstd zstd) throws Exception {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext stream = zstd.createCompressionContext()) {
      stream.parameter(ZstdCompressionParameter.COMPRESSION_LEVEL, zstd.defaultCompressionLevel());

      MemorySegment input = arena.allocate(1000);
      MemorySegment output = arena.allocate(2000);

      ZstdInputBuffer inBuf = zstd.createInputBuffer(arena, input);
      ZstdOutputBuffer outBuf = zstd.createOutputBuffer(arena, output);

      long res = stream.compressStream(outBuf, inBuf, ZstdEndDirective.END);

      // With ample output space a single END call consumes all input and completes the frame.
      assertEquals(0, res, "frame epilogue not fully written");
      assertEquals(1000, inBuf.position());
      assertTrue(outBuf.position() > 0);

      // The struct positions must describe a real frame: it decodes back to the input.
      MemorySegment decompressed = arena.allocate(1000);
      long size = zstd.decompress(decompressed, 1000, output, outBuf.position());
      assertEquals(1000, size);
      assertArrayEquals(
          input.toArray(ValueLayout.JAVA_BYTE), decompressed.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void testOutputBufferInStream(Zstd zstd) throws Exception {
    byte[] original = "test".getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext stream = zstd.createDecompressionContext()) {

      byte[] compressedData = compressTestData(zstd);
      MemorySegment input = arena.allocate(compressedData.length);
      input.copyFrom(MemorySegment.ofArray(compressedData));
      MemorySegment output = arena.allocate(1000);

      ZstdInputBuffer inBuf = zstd.createInputBuffer(arena, input);
      ZstdOutputBuffer outBuf = zstd.createOutputBuffer(arena, output);

      long res = stream.decompressStream(outBuf, inBuf);

      // The whole frame fits: input fully consumed, frame complete, exact plaintext produced.
      assertEquals(0, res, "frame should be fully decoded and flushed");
      assertEquals(compressedData.length, inBuf.position());
      assertEquals(original.length, outBuf.position());
      assertArrayEquals(
          original, output.asSlice(0, outBuf.position()).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  private byte[] compressTestData(Zstd zstd) throws ZstdException {
    try (Arena arena = Arena.ofConfined()) {
      byte[] data = "test".getBytes(StandardCharsets.UTF_8);
      MemorySegment src = arena.allocate(data.length);
      src.copyFrom(MemorySegment.ofArray(data));
      MemorySegment dst = arena.allocate(zstd.compressBound(data.length));

      long result =
          zstd.compress(
              dst,
              zstd.compressBound(data.length),
              src,
              data.length,
              zstd.defaultCompressionLevel());

      byte[] compressed = new byte[(int) result];
      MemorySegment.copy(dst, 0, MemorySegment.ofArray(compressed), 0, result);
      return compressed;
    }
  }
}
