package dev.hallock.zstd.test;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ZstdTest
class ZstdStreamingTest {

  static List<byte[]> provideDataSets() {
    Random random = new Random(67);

    byte[] empty = new byte[0];

    byte[] smallRandom = new byte[50];
    random.nextBytes(smallRandom);

    byte[] largeRandom = new byte[1024 * 1024]; // 1MB
    random.nextBytes(largeRandom);

    byte[] largeCompressible =
        "This is a highly compressible string. "
            .repeat(10000)
            .getBytes(StandardCharsets.UTF_8); // ~380KB

    return List.of(empty, smallRandom, largeCompressible, largeRandom);
  }

  static List<Integer> provideChunkSizes() {
    return List.of(1, 10, 1024, 4096);
  }

  static Stream<Arguments> provideStreamingData() {
    return provideDataSets().stream()
        .flatMap(
            data -> provideChunkSizes().stream().map(chunkSize -> Arguments.of(data, chunkSize)));
  }

  @ParameterizedTest
  @MethodSource("provideStreamingData")
  void testStreamingWithSmallOutputBuffer(byte[] testData, int chunkSize, Zstd zstd)
      throws ZstdException {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
      MemorySegment compressedDst = arena.allocate(zstd.compressBound(testData.length) + 1024);
      MemorySegment decompressedDst = arena.allocate(testData.length);

      ZstdInputBuffer inBuffer = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer outBuffer = zstd.createOutputBuffer(arena, compressedDst);
      outBuffer.size(Math.min(chunkSize, compressedDst.byteSize()));

      while (inBuffer.position() < inBuffer.size()) {
        ZstdEndDirective directive = ZstdEndDirective.CONTINUE;
        cctx.compressStream(outBuffer, inBuffer, directive);

        if (outBuffer.position() == outBuffer.size()) {
          // Output buffer is full, expand it
          outBuffer.size(Math.min(outBuffer.position() + chunkSize, compressedDst.byteSize()));
        }
      }

      while (true) {
        long remaining = cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.END);
        if (remaining == 0) break;

        if (outBuffer.position() == outBuffer.size()) {
          outBuffer.size(Math.min(outBuffer.position() + chunkSize, compressedDst.byteSize()));
        }
      }

      long compressedSize = outBuffer.position();
      MemorySegment actualCompressed = compressedDst.asSlice(0, compressedSize);

      ZstdInputBuffer dInBuf = zstd.createInputBuffer(arena, actualCompressed);
      ZstdOutputBuffer dOutBuf = zstd.createOutputBuffer(arena, decompressedDst);
      dOutBuf.size(Math.min(chunkSize, decompressedDst.byteSize()));

      while (dInBuf.position() < dInBuf.size()) {
        dctx.decompressStream(dOutBuf, dInBuf);

        if (dOutBuf.position() == dOutBuf.size()) {
          // Output buffer is full, expand it
          dOutBuf.size(Math.min(dOutBuf.position() + chunkSize, decompressedDst.byteSize()));
        }
      }

      byte[] actualDecompressed =
          decompressedDst.asSlice(0, dOutBuf.position()).toArray(ValueLayout.JAVA_BYTE);
      Assertions.assertArrayEquals(testData, actualDecompressed);
    }
  }

  @ParameterizedTest
  @MethodSource("provideStreamingData")
  void testStreamingWithFlush(byte[] testData, int chunkSize, Zstd zstd) throws ZstdException {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
      MemorySegment compressedDst = arena.allocate(zstd.compressBound(testData.length) + 1024);
      MemorySegment decompressedDst = arena.allocate(testData.length);

      ZstdInputBuffer inBuffer = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer outBuffer = zstd.createOutputBuffer(arena, compressedDst);
      outBuffer.size(Math.min(chunkSize, compressedDst.byteSize()));

      ZstdInputBuffer dInBuf = zstd.createInputBuffer(arena, compressedDst);
      ZstdOutputBuffer dOutBuf = zstd.createOutputBuffer(arena, decompressedDst);
      dOutBuf.size(Math.min(chunkSize, decompressedDst.byteSize()));

      // Compress half
      long half = src.byteSize() / 2;
      inBuffer.size(half);

      while (inBuffer.position() < inBuffer.size()) {
        cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.CONTINUE);
        if (outBuffer.position() == outBuffer.size()) {
          outBuffer.size(Math.min(outBuffer.position() + chunkSize, compressedDst.byteSize()));
        }
      }

      cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.FLUSH);

      // Should be able to decompress this part immediately
      long partialCompressedSize = outBuffer.position();
      if (partialCompressedSize > 0) {
        dInBuf.size(partialCompressedSize);
        while (dInBuf.position() < dInBuf.size()) {
          dctx.decompressStream(dOutBuf, dInBuf);
          if (dOutBuf.position() == dOutBuf.size()) {
            dOutBuf.size(Math.min(dOutBuf.position() + chunkSize, decompressedDst.byteSize()));
          }
        }
      }

      // Compress the rest
      inBuffer.size(src.byteSize());
      while (inBuffer.position() < inBuffer.size()) {
        cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.CONTINUE);
        if (outBuffer.position() == outBuffer.size()) {
          outBuffer.size(Math.min(outBuffer.position() + chunkSize, compressedDst.byteSize()));
        }
      }

      while (true) {
        long remaining = cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.END);
        if (remaining == 0) break;
        if (outBuffer.position() == outBuffer.size()) {
          outBuffer.size(Math.min(outBuffer.position() + chunkSize, compressedDst.byteSize()));
        }
      }

      // Decompress the rest
      long totalCompressedSize = outBuffer.position();
      if (totalCompressedSize > 0) {
        dInBuf.size(totalCompressedSize);

        while (dInBuf.position() < dInBuf.size()) {
          dctx.decompressStream(dOutBuf, dInBuf);
          if (dOutBuf.position() == dOutBuf.size()) {
            dOutBuf.size(Math.min(dOutBuf.position() + chunkSize, decompressedDst.byteSize()));
          }
        }

        byte[] actualDecompressed =
            decompressedDst.asSlice(0, dOutBuf.position()).toArray(ValueLayout.JAVA_BYTE);
        Assertions.assertArrayEquals(testData, actualDecompressed);
      } else {
        Assertions.assertArrayEquals(new byte[0], testData);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideStreamingData")
  void testStreamingMultipleChunks(byte[] testData, int chunkSize, Zstd zstd) throws ZstdException {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, testData);
      MemorySegment compressedDst = arena.allocate(zstd.compressBound(testData.length) + 1024);
      MemorySegment decompressedDst = arena.allocate(testData.length);

      ZstdInputBuffer inBuffer = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer outBuffer = zstd.createOutputBuffer(arena, compressedDst);

      // Compress in chunks
      while (inBuffer.position() < inBuffer.size()) {
        long remaining = inBuffer.size() - inBuffer.position();
        long currentChunk = Math.min(chunkSize, remaining);

        // Simulate smaller input size for this chunk
        long originalPosition = inBuffer.position();
        inBuffer.size(originalPosition + currentChunk);

        cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.CONTINUE);

        // Restore actual size for loop condition
        inBuffer.size(src.byteSize());
      }

      // End the frame exactly once, repeating only while zstd reports buffered output.
      long remaining;
      do {
        remaining = cctx.compressStream(outBuffer, inBuffer, ZstdEndDirective.END);
      } while (remaining != 0);

      long compressedSize = outBuffer.position();
      MemorySegment actualCompressed = compressedDst.asSlice(0, compressedSize);

      // Decompress in chunks
      ZstdInputBuffer dInBuf = zstd.createInputBuffer(arena, actualCompressed);
      ZstdOutputBuffer dOutBuf = zstd.createOutputBuffer(arena, decompressedDst);

      while (dInBuf.position() < dInBuf.size()) {
        long remainingIn = dInBuf.size() - dInBuf.position();
        long currentChunk = Math.min(chunkSize, remainingIn);

        long originalPosition = dInBuf.position();
        dInBuf.size(originalPosition + currentChunk);

        dctx.decompressStream(dOutBuf, dInBuf);

        dInBuf.size(actualCompressed.byteSize());
      }

      byte[] actualDecompressed =
          decompressedDst.asSlice(0, dOutBuf.position()).toArray(ValueLayout.JAVA_BYTE);
      Assertions.assertArrayEquals(testData, actualDecompressed);
    }
  }
}
