package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdCompressionDictionary;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdDecompressionDictionary;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

@ZstdTest
class ZstdSafetyTest {

  @Test
  void rejectsNegativeNativeSizes(Zstd zstd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(16);

      assertThrows(IllegalArgumentException.class, () -> zstd.compressBound(-1));
      assertThrows(
          IllegalArgumentException.class,
          () -> zstd.compress(segment, -1, segment, 0, zstd.defaultCompressionLevel()));
      assertThrows(
          IllegalArgumentException.class,
          () -> zstd.compress(segment, 16, segment, -1, zstd.defaultCompressionLevel()));
      assertThrows(IllegalArgumentException.class, () -> zstd.decompress(segment, -1, segment, 0));
      assertThrows(IllegalArgumentException.class, () -> zstd.decompress(segment, 16, segment, -1));
      assertThrows(IllegalArgumentException.class, () -> zstd.frameContentSize(segment, -1));
      assertThrows(IllegalArgumentException.class, () -> zstd.findFrameCompressedSize(segment, -1));
      assertThrows(IllegalArgumentException.class, () -> zstd.dictIdFromDict(segment, -1));
      assertThrows(IllegalArgumentException.class, () -> zstd.dictIdFromFrame(segment, -1));
      assertThrows(
          IllegalArgumentException.class,
          () -> zstd.createCompressionDictionary(segment, -1, zstd.defaultCompressionLevel()));
      assertThrows(
          IllegalArgumentException.class, () -> zstd.createDecompressionDictionary(segment, -1));
    }
  }

  @Test
  void contextCloseIsIdempotentAndOperationsFailAfterClose(Zstd zstd) throws Exception {
    ZstdCompressionContext compression = zstd.createCompressionContext();
    compression.close();
    assertDoesNotThrow(compression::close);
    assertThrows(IllegalStateException.class, () -> compression.pledgedSrcSize(0));

    ZstdDecompressionContext decompression = zstd.createDecompressionContext();
    decompression.close();
    assertDoesNotThrow(decompression::close);
    assertThrows(
        IllegalStateException.class,
        () -> decompression.decompress(MemorySegment.NULL, 0, MemorySegment.NULL, 0));
  }

  @Test
  void streamingOperationsFailAfterClose(Zstd zstd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment source = arena.allocate(64);
      MemorySegment destination = arena.allocate(64);
      MemorySegment dictContent = arena.allocate(64);
      ZstdInputBuffer in = zstd.createInputBuffer(arena, source);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, destination);

      ZstdCompressionContext cctx = zstd.createCompressionContext();
      cctx.close();
      assertThrows(
          IllegalStateException.class, () -> cctx.compressStream(out, in, ZstdEndDirective.END));
      assertThrows(IllegalStateException.class, () -> cctx.loadDictionary(dictContent, 64));

      ZstdDecompressionContext dctx = zstd.createDecompressionContext();
      dctx.close();
      assertThrows(IllegalStateException.class, () -> dctx.decompressStream(out, in));
      assertThrows(IllegalStateException.class, () -> dctx.loadDictionary(dictContent, 64));
    }
  }

  @Test
  void dictionaryCloseIsIdempotentAndOperationsFailAfterClose(Zstd zstd) throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment dictionary = arena.allocate(16);

      ZstdCompressionDictionary compression =
          zstd.createCompressionDictionary(dictionary, zstd.defaultCompressionLevel());
      compression.close();
      assertDoesNotThrow(compression::close);
      assertThrows(IllegalStateException.class, compression::sizeOf);

      ZstdDecompressionDictionary decompression = zstd.createDecompressionDictionary(dictionary);
      decompression.close();
      assertDoesNotThrow(decompression::close);
      assertThrows(IllegalStateException.class, decompression::dictId);
    }
  }

  @Test
  void rejectsHeapSegments(Zstd zstd) {
    byte[] data = "heap data".getBytes(StandardCharsets.UTF_8);
    MemorySegment heap = MemorySegment.ofArray(data);
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      MemorySegment nativeSeg = arena.allocate(64);

      assertThrows(
          IllegalArgumentException.class, () -> zstd.compress(nativeSeg, 64, heap, data.length, 3));
      assertThrows(
          IllegalArgumentException.class, () -> zstd.decompress(heap, data.length, nativeSeg, 64));
      assertThrows(IllegalArgumentException.class, () -> zstd.frameContentSize(heap, data.length));
      assertThrows(IllegalArgumentException.class, () -> zstd.createInputBuffer(arena, heap));
      assertThrows(IllegalArgumentException.class, () -> zstd.createOutputBuffer(arena, heap));
      assertThrows(IllegalArgumentException.class, () -> zstd.createCompressionDictionary(heap, 3));
      assertThrows(IllegalArgumentException.class, () -> zstd.createDecompressionDictionary(heap));
      assertThrows(IllegalArgumentException.class, () -> ctx.loadDictionary(heap, data.length));
      assertThrows(IllegalArgumentException.class, () -> ctx.refPrefix(heap, data.length));
    }
  }

  @Test
  void closedArenaBuffersAreRejectedByStreamingOperations(Zstd zstd) {
    try (Arena structArena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      Arena dataArena = Arena.ofConfined();
      MemorySegment source = dataArena.allocate(64);
      MemorySegment destination = dataArena.allocate(64);
      ZstdInputBuffer in = zstd.createInputBuffer(structArena, source);
      ZstdOutputBuffer out = zstd.createOutputBuffer(structArena, destination);
      dataArena.close();

      assertThrows(
          IllegalStateException.class,
          () -> cctx.compressStream(out, in, ZstdEndDirective.CONTINUE));
      assertThrows(IllegalStateException.class, () -> dctx.decompressStream(out, in));
    }
  }

  @Test
  void refClosedDictionaryThrows(Zstd zstd) {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      MemorySegment content = arena.allocate(16);

      ZstdCompressionDictionary cdict = zstd.createCompressionDictionary(content, 3);
      cdict.close();
      assertThrows(IllegalStateException.class, () -> cctx.refDictionary(cdict));

      ZstdDecompressionDictionary ddict = zstd.createDecompressionDictionary(content);
      ddict.close();
      assertThrows(IllegalStateException.class, () -> dctx.refDictionary(ddict));
    }
  }

  @Test
  void closingReferencedDictionaryDefersNativeRelease(Zstd zstd) {
    byte[] original = "still compressible after dictionary close".getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext()) {
      MemorySegment content = arena.allocate(64);
      ZstdCompressionDictionary cdict = zstd.createCompressionDictionary(content, 3);
      cctx.refDictionary(cdict);

      // Close the handle while the context still references it: native tables must survive.
      cdict.close();
      assertThrows(IllegalStateException.class, cdict::sizeOf);

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long bound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(bound);
      long compressed = cctx.compress(dst, bound, src, original.length);
      assertTrue(compressed > 0);
    }
  }

  @Test
  void closingReferencedDecompressionDictionaryDefersNativeRelease(Zstd zstd) {
    byte[] dict = TestVectors.load("dict.bin");
    byte[] frame = TestVectors.load("dictframe.zst");
    byte[] expected = TestVectors.PLAIN_TEXT.getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      MemorySegment dictSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, dict);
      ZstdDecompressionDictionary ddict = zstd.createDecompressionDictionary(dictSeg);
      dctx.refDictionary(ddict);

      // Close the handle while the context still references it: the native tables must survive
      // and the dict-compressed frame must still decode through the referencing context.
      ddict.close();
      assertThrows(IllegalStateException.class, ddict::sizeOf);
      assertThrows(IllegalStateException.class, ddict::dictId);

      MemorySegment frameSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      MemorySegment dst = arena.allocate(expected.length);
      long size = dctx.decompress(dst, expected.length, frameSeg, frame.length);
      assertArrayEquals(expected, dst.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void bufferFactoriesValidateSizeAndPosition(Zstd zstd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(16);

      assertThrows(
          IllegalArgumentException.class, () -> zstd.createInputBuffer(arena, segment, 32, 0));
      assertThrows(
          IllegalArgumentException.class, () -> zstd.createInputBuffer(arena, segment, 16, 20));
      assertThrows(
          IllegalArgumentException.class, () -> zstd.createInputBuffer(arena, segment, -1, 0));
      assertThrows(
          IllegalArgumentException.class, () -> zstd.createOutputBuffer(arena, segment, 8, -1));

      ZstdInputBuffer in = zstd.createInputBuffer(arena, segment, 16, 8);
      assertThrows(IllegalArgumentException.class, () -> in.size(4));
      assertThrows(IllegalArgumentException.class, () -> in.position(17));
      in.size(8);
      in.position(0);
      in.size(4);

      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, segment, 16, 8);
      assertThrows(IllegalArgumentException.class, () -> out.size(4));
      assertThrows(IllegalArgumentException.class, () -> out.position(17));
    }
  }

  @Test
  void rejectsSizesLargerThanSegments(Zstd zstd) {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      MemorySegment seg = arena.allocate(16);
      int level = zstd.defaultCompressionLevel();

      assertThrows(IllegalArgumentException.class, () -> zstd.compress(seg, 32, seg, 0, level));
      assertThrows(IllegalArgumentException.class, () -> zstd.compress(seg, 16, seg, 32, level));
      assertThrows(IllegalArgumentException.class, () -> zstd.decompress(seg, 32, seg, 0));
      assertThrows(IllegalArgumentException.class, () -> zstd.decompress(seg, 16, seg, 32));
      assertThrows(IllegalArgumentException.class, () -> zstd.frameContentSize(seg, 32));
      assertThrows(IllegalArgumentException.class, () -> zstd.findFrameCompressedSize(seg, 32));
      assertThrows(IllegalArgumentException.class, () -> zstd.dictIdFromDict(seg, 32));
      assertThrows(IllegalArgumentException.class, () -> zstd.dictIdFromFrame(seg, 32));
      assertThrows(
          IllegalArgumentException.class, () -> zstd.createCompressionDictionary(seg, 32, level));
      assertThrows(
          IllegalArgumentException.class, () -> zstd.createDecompressionDictionary(seg, 32));

      assertThrows(IllegalArgumentException.class, () -> cctx.compress(seg, 32, seg, 0));
      assertThrows(IllegalArgumentException.class, () -> cctx.compress(seg, 16, seg, 32));
      assertThrows(IllegalArgumentException.class, () -> cctx.loadDictionary(seg, 32));
      assertThrows(IllegalArgumentException.class, () -> cctx.refPrefix(seg, 32));
      assertThrows(IllegalArgumentException.class, () -> cctx.pledgedSrcSize(-2));

      assertThrows(IllegalArgumentException.class, () -> dctx.decompress(seg, 32, seg, 0));
      assertThrows(IllegalArgumentException.class, () -> dctx.decompress(seg, 16, seg, 32));
      assertThrows(IllegalArgumentException.class, () -> dctx.loadDictionary(seg, 32));
      assertThrows(IllegalArgumentException.class, () -> dctx.refPrefix(seg, 32));
    }
  }

  @Test
  void closedOutputArenaIsRejectedIndependentlyOfInput(Zstd zstd) {
    try (Arena liveArena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      MemorySegment source = liveArena.allocate(64);
      ZstdInputBuffer in = zstd.createInputBuffer(liveArena, source);

      Arena outputArena = Arena.ofConfined();
      MemorySegment destination = outputArena.allocate(64);
      ZstdOutputBuffer out = zstd.createOutputBuffer(liveArena, destination);
      outputArena.close();

      assertThrows(
          IllegalStateException.class,
          () -> cctx.compressStream(out, in, ZstdEndDirective.CONTINUE));
      assertThrows(IllegalStateException.class, () -> dctx.decompressStream(out, in));
    }
  }

  @Test
  void buffersRenderTheirSegmentsInToString(Zstd zstd) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment segment = arena.allocate(16);
      ZstdInputBuffer in = zstd.createInputBuffer(arena, segment, 8, 2);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, segment, 8, 2);
      assertTrue(in.toString().contains("source="), in.toString());
      assertTrue(out.toString().contains("destination="), out.toString());
    }
  }

  @Test
  void crossThreadCloseIsSafe(Zstd zstd) throws Exception {
    byte[] data = "cross thread close".getBytes(StandardCharsets.UTF_8);
    ZstdCompressionContext ctx = zstd.createCompressionContext();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      long bound = zstd.compressBound(data.length);
      MemorySegment dst = arena.allocate(bound);
      long compressed = ctx.compress(dst, bound, src, data.length);

      byte[] roundTrip = new byte[data.length];
      MemorySegment decompressed = arena.allocate(data.length);
      Zstd.zstd().decompress(decompressed, data.length, dst, compressed);
      MemorySegment.copy(decompressed, ValueLayout.JAVA_BYTE, 0, roundTrip, 0, data.length);
      assertArrayEquals(data, roundTrip);
    }

    Thread closer = new Thread(ctx::close);
    closer.start();
    closer.join();
    assertThrows(IllegalStateException.class, () -> ctx.pledgedSrcSize(0));
    assertDoesNotThrow(ctx::close);
  }

  @Test
  void concurrentOperationOnBusyContextIsRejected(Zstd zstd) throws Exception {
    // Slowly-compressible input at a high level keeps the worker inside a single native call (with
    // the context lock held) long enough for the polling loop below to overlap it reliably.
    byte[] data = new byte[8 << 20];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) (i ^ (i >>> 7) ^ (i >>> 13));
    }
    try (ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      ctx.parameter(ZstdCompressionParameter.COMPRESSION_LEVEL, 19);

      CountDownLatch started = new CountDownLatch(1);
      Thread worker =
          new Thread(
              // The worker owns the segments in its own confined arena; a shared arena would be
              // unsupported in the default native image configuration.
              () -> {
                try (Arena arena = Arena.ofConfined()) {
                  MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
                  MemorySegment dst = arena.allocate(zstd.compressBound(data.length));
                  started.countDown();
                  while (true) {
                    try {
                      ctx.compress(dst, dst.byteSize(), src, data.length);
                      return;
                    } catch (IllegalStateException lockHeldByPoller) {
                      // The polling loop below briefly held the lock; retry until compress starts.
                      Thread.onSpinWait();
                    }
                  }
                }
              });
      worker.start();
      started.await();

      IllegalStateException rejection = null;
      while (worker.isAlive() && rejection == null) {
        try {
          // Same value as configured above, so hitting an idle window (before the worker acquires
          // the lock, or after it finishes) leaves the context state unchanged.
          ctx.parameter(ZstdCompressionParameter.COMPRESSION_LEVEL, 19);
        } catch (IllegalStateException expected) {
          rejection = expected;
        }
      }
      worker.join();
      assertNotNull(rejection, "no operation overlapped the in flight compress");
      assertTrue(
          rejection.getMessage().contains("in use by another thread"), rejection.getMessage());
    }
  }
}
