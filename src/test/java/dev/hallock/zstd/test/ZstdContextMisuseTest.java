package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdCompressionDictionary;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdDecompressionDictionary;
import dev.hallock.zstd.ZstdDecompressionParameter;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdErrorCode;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import dev.hallock.zstd.ZstdResetDirective;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * zstd rejects parameter and dictionary mutations while a streaming session is active; these tests
 * exercise every such error path and the recovery via reset.
 */
@ZstdTest
class ZstdContextMisuseTest {

  private static final byte[] DATA = "misuse test data".repeat(50).getBytes(StandardCharsets.UTF_8);

  /** Puts the context mid-frame and returns the output buffer holding the partial frame. */
  private static ZstdOutputBuffer startSession(Zstd zstd, Arena arena, ZstdCompressionContext ctx) {
    MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, DATA);
    MemorySegment dst = arena.allocate(zstd.compressBound(DATA.length));
    ZstdInputBuffer in = zstd.createInputBuffer(arena, src);
    ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);
    ctx.compressStream(out, in, ZstdEndDirective.CONTINUE);
    return out;
  }

  @Test
  void midSessionMutationsFailOnCompressionContext(Zstd zstd) {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      startSession(zstd, arena, ctx);

      MemorySegment dictContent = arena.allocate(64);
      assertThrows(ZstdException.class, () -> ctx.loadDictionary(dictContent, 64));
      assertThrows(ZstdException.class, () -> ctx.refPrefix(dictContent, 64));
      assertThrows(ZstdException.class, () -> ctx.pledgedSrcSize(5));

      try (ZstdCompressionDictionary dict =
          zstd.createCompressionDictionary(dictContent, zstd.defaultCompressionLevel())) {
        assertThrows(ZstdException.class, () -> ctx.refDictionary(dict));
        // The failed ref must have released its reference: the dictionary stays open and usable.
        assertTrue(dict.sizeOf() > 0);
        ctx.reset(ZstdResetDirective.SESSION_ONLY);
        ctx.refDictionary(dict);
      }
    }
  }

  @Test
  void midFrameMutationsFailOnDecompressionContext(Zstd zstd) {
    byte[] frame = zstd.compress(DATA);
    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext ctx = zstd.createDecompressionContext()) {
      // Feed only half the frame so the context is mid-frame.
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      MemorySegment dst = arena.allocate(DATA.length);
      ZstdInputBuffer in = zstd.createInputBuffer(arena, src, frame.length / 2, 0);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);
      long hint = ctx.decompressStream(out, in);
      assertTrue(hint != 0, "frame unexpectedly complete");

      MemorySegment dictContent = arena.allocate(64);
      assertThrows(ZstdException.class, () -> ctx.loadDictionary(dictContent, 64));
      assertThrows(ZstdException.class, () -> ctx.refPrefix(dictContent, 64));
      assertThrows(ZstdException.class, () -> ctx.reset(ZstdResetDirective.PARAMETERS));

      // SESSION_ONLY abandons the partial frame and the context decompresses again.
      ctx.reset(ZstdResetDirective.SESSION_ONLY);
      long size = ctx.decompress(dst, DATA.length, src, frame.length);
      assertEquals(DATA.length, size);
    }
  }

  @Test
  void outOfBoundsParameterValuesFail(Zstd zstd) {
    try (ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      int aboveMax = ZstdCompressionParameter.WINDOW_LOG.bounds().upperBound() + 1;
      assertThrows(
          ZstdException.class, () -> cctx.parameter(ZstdCompressionParameter.WINDOW_LOG, aboveMax));
      int belowMin = ZstdDecompressionParameter.WINDOW_LOG_MAX.bounds().lowerBound() - 1;
      assertThrows(
          ZstdException.class,
          () -> dctx.parameter(ZstdDecompressionParameter.WINDOW_LOG_MAX, belowMin));
    }
  }

  @Test
  void windowLogMaxIsEnforcedDuringDecompression(Zstd zstd) {
    byte[] large = new byte[512 * 1024];
    for (int i = 0; i < large.length; i++) {
      large[i] = (byte) (i * 31);
    }
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, large);
      long bound = zstd.compressBound(large.length);
      MemorySegment dst = arena.allocate(bound);
      cctx.parameter(ZstdCompressionParameter.WINDOW_LOG, 19);
      long compressed = cctx.compress(dst, bound, src, large.length);

      // Output smaller than the frame content forces the windowed path where the limit is
      // consulted.
      int floor = ZstdDecompressionParameter.WINDOW_LOG_MAX.bounds().lowerBound();
      dctx.parameter(ZstdDecompressionParameter.WINDOW_LOG_MAX, floor);
      MemorySegment small = arena.allocate(4096);
      ZstdInputBuffer in = zstd.createInputBuffer(arena, dst, compressed, 0);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, small);
      ZstdException e =
          assertThrows(
              ZstdException.class,
              () -> {
                while (in.position() < in.size()) {
                  out.position(0);
                  dctx.decompressStream(out, in);
                }
              });
      assertEquals(ZstdErrorCode.FRAME_PARAMETER_WINDOW_TOO_LARGE, e.errorCode());

      // Raising the limit back (after recovering the session) makes the frame decode.
      dctx.reset(ZstdResetDirective.SESSION_ONLY);
      dctx.parameter(ZstdDecompressionParameter.WINDOW_LOG_MAX, 27);
      MemorySegment decompressDst = arena.allocate(large.length);
      long size = dctx.decompress(decompressDst, large.length, dst, compressed);
      assertEquals(large.length, size);
      assertArrayEquals(large, decompressDst.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void parameterResetDropsDecompressionDictionary(Zstd zstd) {
    byte[] dict = TestVectors.load("dict.bin");
    byte[] frame = TestVectors.load("dictframe.zst");
    byte[] expected = TestVectors.PLAIN_TEXT.getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext ctx = zstd.createDecompressionContext();
        ZstdDecompressionDictionary ddict =
            zstd.createDecompressionDictionary(arena.allocateFrom(ValueLayout.JAVA_BYTE, dict))) {
      MemorySegment frameSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      MemorySegment dst = arena.allocate(expected.length);

      ctx.refDictionary(ddict);
      ctx.reset(ZstdResetDirective.SESSION_AND_PARAMETERS);

      // The reset dropped the dictionary reference: the dict-compressed frame no longer decodes.
      assertThrows(
          ZstdException.class, () -> ctx.decompress(dst, expected.length, frameSeg, frame.length));

      // Re-referencing restores it, proving the dictionary survived the release.
      ctx.refDictionary(ddict);
      long size = ctx.decompress(dst, expected.length, frameSeg, frame.length);
      assertArrayEquals(expected, dst.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE));
    }
  }
}
