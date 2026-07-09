package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdErrorCode;
import dev.hallock.zstd.ZstdException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

class ZstdExceptionTest {

  @Test
  void testCompressionError() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocate(100);
      MemorySegment dst = arena.allocate(1);

      ZstdException ex =
          assertThrows(
              ZstdException.class,
              () -> {
                Zstd.zstd().compress(dst, 1, src, 100, Zstd.zstd().defaultCompressionLevel());
              });
      assertTrue(ex.rawValue() < 0 || ex.rawValue() > 1000000);
      assertEquals(ZstdErrorCode.DST_SIZE_TOO_SMALL, ex.errorCode());
      assertNotNull(ex.errorName());
      assertFalse(ex.errorName().isEmpty());
    }
  }

  @Test
  void testDecompressionError() {
    try (Arena arena = Arena.ofConfined()) {
      byte[] invalidData = new byte[] {1, 2, 3, 4, 5};
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, invalidData);
      MemorySegment dst = arena.allocate(100);

      assertThrows(
          ZstdException.class,
          () -> {
            Zstd.zstd().decompress(dst, 100, src, invalidData.length);
          });
    }
  }

  @Test
  void testCloseIsIdempotentAndOperationsAfterCloseThrow() {
    ZstdCompressionContext ctx = Zstd.zstd().createCompressionContext();
    ctx.close();
    // A second close is a silent no-op; using the closed context is an IllegalStateException,
    // not a ZstdException (no native call may happen on a freed context).
    assertDoesNotThrow(ctx::close);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment seg = arena.allocate(16);
      assertThrows(IllegalStateException.class, () -> ctx.compress(seg, 16, seg, 16));
    }
  }

  @Test
  void testDstSizeTooSmallMapsToEnumConstant() {
    byte[] data = new byte[4096];
    byte[] frame = Zstd.zstd().compress(data);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      MemorySegment dst = arena.allocate(1);
      ZstdException e =
          assertThrows(
              ZstdException.class, () -> Zstd.zstd().decompress(dst, 1, src, frame.length));
      assertEquals(ZstdErrorCode.DST_SIZE_TOO_SMALL, e.errorCode());
    }
  }

  @Test
  void testUnmappedNativeValuesResolveToUnknown() {
    // 2, 90, and 99 are inside the stable numeric range but have no declared ZSTD_ErrorCode;
    // negative and out-of-range values must never throw.
    assertEquals(ZstdErrorCode.UNKNOWN, ZstdErrorCode.fromNative(2));
    assertEquals(ZstdErrorCode.UNKNOWN, ZstdErrorCode.fromNative(90));
    assertEquals(ZstdErrorCode.UNKNOWN, ZstdErrorCode.fromNative(99));
    assertEquals(ZstdErrorCode.UNKNOWN, ZstdErrorCode.fromNative(-1));
    assertEquals(ZstdErrorCode.UNKNOWN, ZstdErrorCode.fromNative(Integer.MAX_VALUE));
    // Sanity anchors for the mapped end of the table.
    assertEquals(ZstdErrorCode.NO_ERROR, ZstdErrorCode.fromNative(0));
    assertEquals(ZstdErrorCode.DST_SIZE_TOO_SMALL, ZstdErrorCode.fromNative(70));
  }
}
