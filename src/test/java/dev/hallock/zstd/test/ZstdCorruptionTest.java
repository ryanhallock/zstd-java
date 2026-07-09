package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdErrorCode;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Corrupted, truncated, and undersized-output decompression paths. */
@ZstdTest
class ZstdCorruptionTest {

  private static byte[] compressedRandom(Zstd zstd, int size) {
    byte[] data = new byte[size];
    new Random(42).nextBytes(data);
    return zstd.compress(data, 3);
  }

  @Test
  void truncatedStreamEndsWithNonzeroHintAndNoException(Zstd zstd) {
    byte[] frame = compressedRandom(zstd, 64 * 1024);
    byte[] truncated = new byte[frame.length / 2];
    System.arraycopy(frame, 0, truncated, 0, truncated.length);

    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext ctx = zstd.createDecompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, truncated);
      MemorySegment dst = arena.allocate(zstd.recommendedDStreamOutSize());
      ZstdInputBuffer in = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);

      long ret = 0;
      while (in.position() < in.size()) {
        out.position(0);
        ret = ctx.decompressStream(out, in);
        assertTrue(out.position() <= out.size(), "output overran its size limit");
      }
      // The documented contract: nonzero return after input is exhausted means the frame is
      // incomplete. Truncation alone must not raise an error.
      assertNotEquals(0, ret, "truncated frame must not report frame completion");
    }
  }

  @Test
  void bitFlippedFrameThrows(Zstd zstd) {
    // Compressible data with a checksum: a flip surfaces as a decode error or checksum_wrong.
    byte[] data = "corruptible pattern data ".repeat(4096).getBytes(StandardCharsets.UTF_8);
    byte[] frame;
    try (Arena arena = Arena.ofConfined();
        var cctx = zstd.createCompressionContext()) {
      cctx.parameter(ZstdCompressionParameter.CHECKSUM_FLAG, 1);
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      long bound = zstd.compressBound(data.length);
      MemorySegment dst = arena.allocate(bound);
      long compressed = cctx.compress(dst, bound, src, data.length);
      frame = dst.asSlice(0, compressed).toArray(ValueLayout.JAVA_BYTE);
    }
    // Corrupt a run of bytes well past the header, inside compressed block content.
    for (int i = frame.length / 2; i < frame.length / 2 + 4; i++) {
      frame[i] ^= (byte) 0xFF;
    }

    byte[] copy = frame.clone();
    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext ctx = zstd.createDecompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, copy);
      MemorySegment dst = arena.allocate(zstd.recommendedDStreamOutSize());
      ZstdInputBuffer in = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);

      assertThrows(
          ZstdException.class,
          () -> {
            while (in.position() < in.size()) {
              out.position(0);
              ctx.decompressStream(out, in);
            }
          });
    }
  }

  @Test
  void oneShotOutputTooSmallFailsCleanly(Zstd zstd) {
    byte[] data = new byte[4096];
    new Random(7).nextBytes(data);
    byte[] frame = zstd.compress(data);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      MemorySegment dst = arena.allocate(16);
      ZstdException e =
          assertThrows(ZstdException.class, () -> zstd.decompress(dst, 16, src, frame.length));
      assertEquals(ZstdErrorCode.DST_SIZE_TOO_SMALL, e.errorCode());
    }
  }

  @Test
  void streamingNeverWritesPastOutputSizeLimit(Zstd zstd) {
    byte[] data = new byte[32 * 1024];
    new Random(11).nextBytes(data);
    byte[] frame = zstd.compress(data);

    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext ctx = zstd.createDecompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      // A guard region after the small logical window: must stay untouched.
      MemorySegment dst = arena.allocate(128);
      dst.asSlice(64).fill((byte) 0x5A);
      ZstdInputBuffer in = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst, 64, 0);

      long produced = 0;
      while (in.position() < in.size() && out.position() < out.size()) {
        ctx.decompressStream(out, in);
        assertTrue(out.position() <= 64, "output overran its size limit");
        if (out.position() == out.size()) break;
        produced = out.position();
      }
      assertTrue(produced <= 64);
      byte[] guard = dst.asSlice(64).toArray(ValueLayout.JAVA_BYTE);
      for (byte b : guard) {
        assertTrue(b == (byte) 0x5A, "guard region was overwritten");
      }
    }
  }
}
