package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdInputBuffer;
import dev.hallock.zstd.ZstdOutputBuffer;
import dev.hallock.zstd.ZstdResetDirective;
import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Frame header introspection: content-size sentinels, pledged sizes, and reset recovery. */
@ZstdTest
class ZstdFrameInfoTest {

  private static final byte[] DATA =
      "frame info test data".repeat(20).getBytes(StandardCharsets.UTF_8);

  @Test
  void streamedFrameWithoutPledgeReportsUnknown(Zstd zstd) {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, DATA);
      MemorySegment dst = arena.allocate(zstd.compressBound(DATA.length));
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);

      // Split across two calls so the first END call cannot infer the total size itself.
      ZstdInputBuffer firstHalf = zstd.createInputBuffer(arena, src, DATA.length / 2, 0);
      ctx.compressStream(out, firstHalf, ZstdEndDirective.CONTINUE);
      ZstdInputBuffer rest = zstd.createInputBuffer(arena, src.asSlice(DATA.length / 2), 0, 0);
      rest.size(src.byteSize() - DATA.length / 2);
      long remaining;
      do {
        remaining = ctx.compressStream(out, rest, ZstdEndDirective.END);
      } while (remaining > 0);

      assertEquals(Zstd.CONTENT_SIZE_UNKNOWN, zstd.frameContentSize(dst, out.position()));
    }
  }

  @Test
  void pledgedSrcSizeIsWrittenToFrameHeader(Zstd zstd) {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, DATA);
      MemorySegment dst = arena.allocate(zstd.compressBound(DATA.length));
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);

      ctx.pledgedSrcSize(DATA.length);
      ZstdInputBuffer firstHalf = zstd.createInputBuffer(arena, src, DATA.length / 2, 0);
      ctx.compressStream(out, firstHalf, ZstdEndDirective.CONTINUE);
      ZstdInputBuffer rest = zstd.createInputBuffer(arena, src.asSlice(DATA.length / 2));
      long remaining;
      do {
        remaining = ctx.compressStream(out, rest, ZstdEndDirective.END);
      } while (remaining > 0);

      assertEquals(DATA.length, zstd.frameContentSize(dst, out.position()));

      MemorySegment roundTrip = arena.allocate(DATA.length);
      long size = zstd.decompress(roundTrip, DATA.length, dst, out.position());
      assertEquals(DATA.length, size);
      assertArrayEquals(DATA, roundTrip.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void pledgeMismatchFails(Zstd zstd) {
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, DATA);
      MemorySegment dst = arena.allocate(zstd.compressBound(DATA.length));
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);

      // Pledge less than we feed: zstd reports srcSize_wrong as soon as the pledge is exceeded
      // (or at latest when the frame is ended).
      ctx.pledgedSrcSize(DATA.length - 5);
      ZstdInputBuffer in = zstd.createInputBuffer(arena, src);
      assertThrows(
          ZstdException.class,
          () -> {
            ctx.compressStream(out, in, ZstdEndDirective.CONTINUE);
            long remaining;
            do {
              remaining = ctx.compressStream(out, in, ZstdEndDirective.END);
            } while (remaining > 0);
          });
    }
  }

  @Test
  void invalidFrameHeaderThrows(Zstd zstd) {
    byte[] garbage = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, garbage);
      assertThrows(ZstdException.class, () -> zstd.frameContentSize(src, garbage.length));
      assertThrows(ZstdException.class, () -> zstd.findFrameCompressedSize(src, garbage.length));
    }
  }

  @Test
  void truncatedFrameHeaderThrows(Zstd zstd) {
    byte[] frame = TestVectors.load("plain.zst");
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      assertThrows(ZstdException.class, () -> zstd.frameContentSize(src, 2));
    }
  }

  @Test
  void sessionOnlyResetRecoversFailedDecompression(Zstd zstd) {
    byte[] garbage = {1, 2, 3, 4, 5, 6, 7, 8};
    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext ctx = zstd.createDecompressionContext()) {
      MemorySegment bad = arena.allocateFrom(ValueLayout.JAVA_BYTE, garbage);
      MemorySegment dst = arena.allocate(64);
      ZstdInputBuffer badIn = zstd.createInputBuffer(arena, bad);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);
      assertThrows(ZstdException.class, () -> ctx.decompressStream(out, badIn));

      ctx.reset(ZstdResetDirective.SESSION_ONLY);

      byte[] frame = TestVectors.load("plain.zst");
      MemorySegment good = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      byte[] expected = TestVectors.PLAIN_TEXT.getBytes(StandardCharsets.UTF_8);
      MemorySegment decompressed = arena.allocate(expected.length);
      long size = ctx.decompress(decompressed, expected.length, good, frame.length);
      assertEquals(expected.length, size);
      assertArrayEquals(expected, decompressed.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void sessionOnlyResetAbandonsInFlightFrame(Zstd zstd) {
    byte[] first = "abandoned frame".getBytes(StandardCharsets.UTF_8);
    byte[] second = "fresh frame".getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, first);
      MemorySegment dst = arena.allocate(zstd.compressBound(first.length));
      ZstdInputBuffer in = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);
      ctx.compressStream(out, in, ZstdEndDirective.CONTINUE);

      ctx.reset(ZstdResetDirective.SESSION_ONLY);

      MemorySegment src2 = arena.allocateFrom(ValueLayout.JAVA_BYTE, second);
      long bound = zstd.compressBound(second.length);
      MemorySegment dst2 = arena.allocate(bound);
      long compressed = ctx.compress(dst2, bound, src2, second.length);
      MemorySegment roundTrip = arena.allocate(second.length);
      long size = zstd.decompress(roundTrip, second.length, dst2, compressed);
      assertEquals(second.length, size);
      assertArrayEquals(second, roundTrip.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void parameterResetDuringActiveSessionFails(Zstd zstd) {
    byte[] data = "active session".getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      MemorySegment dst = arena.allocate(zstd.compressBound(data.length));
      ZstdInputBuffer in = zstd.createInputBuffer(arena, src);
      ZstdOutputBuffer out = zstd.createOutputBuffer(arena, dst);
      ctx.compressStream(out, in, ZstdEndDirective.CONTINUE);

      assertThrows(ZstdException.class, () -> ctx.reset(ZstdResetDirective.PARAMETERS));

      // SESSION_AND_PARAMETERS is allowed and recovers the context.
      ctx.reset(ZstdResetDirective.SESSION_AND_PARAMETERS);
      long bound = zstd.compressBound(data.length);
      assertTrue(ctx.compress(dst, bound, src, data.length) > 0);
    }
  }

  @Test
  void forgedHugeContentSizeIsReportedButRejectedByByteArrayTier(Zstd zstd) {
    // Hand-crafted frame header: magic, FHD with an 8-byte frame content size field, window
    // descriptor, then 4 GiB declared content size. Valid header, no payload needed.
    byte[] header = {
      (byte) 0x28,
      (byte) 0xB5,
      (byte) 0x2F,
      (byte) 0xFD, // ZSTD_MAGICNUMBER (little endian)
      (byte) 0xC0, // FHD: FCS field size code 3 (8 bytes)
      0x00, // window descriptor
      0x00,
      0x00,
      0x00,
      0x00,
      0x01,
      0x00,
      0x00,
      0x00 // content size = 1L << 32
    };
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, header);
      assertEquals(1L << 32, zstd.frameContentSize(src, header.length));
    }
    ZstdException e = assertThrows(ZstdException.class, () -> zstd.decompress(header));
    assertTrue(e.getMessage().contains("maximum byte[] length"), e.getMessage());
  }

  @Test
  void truncatedUnknownSizeFrameFailsInByteArrayTier(Zstd zstd) throws Exception {
    // The stream adapter records no content size, so decompress(byte[]) takes the streaming
    // accumulator path; a cleanly truncated input must surface as a truncation error, not silence.
    byte[] original = new byte[64 * 1024];
    new Random(3).nextBytes(original);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (var out = zstd.createCompressorOutputStream(sink)) {
      out.write(original);
    }
    byte[] truncated = Arrays.copyOf(sink.toByteArray(), sink.size() / 2);

    ZstdException e = assertThrows(ZstdException.class, () -> zstd.decompress(truncated));
    assertTrue(e.getMessage().contains("Truncated"), e.getMessage());
  }

  @Test
  void resetAfterCloseThrows(Zstd zstd) {
    ZstdCompressionContext ctx = zstd.createCompressionContext();
    ctx.close();
    assertThrows(IllegalStateException.class, () -> ctx.reset(ZstdResetDirective.SESSION_ONLY));
  }
}
