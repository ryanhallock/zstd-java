package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdDecompressionParameter;
import dev.hallock.zstd.ZstdErrorCode;
import dev.hallock.zstd.ZstdException;
import dev.hallock.zstd.ZstdParameterBounds;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ZstdUtilityTest {

  @Test
  void testZstdFactoryReturnsSameInstance() {
    // The lazy holder must produce exactly one instance across calls.
    assertSame(Zstd.zstd(), Zstd.zstd());
  }

  @Test
  void testVersionInfo() {
    int versionNumber = Zstd.zstd().versionNumber();
    assertTrue(versionNumber > 0);

    String versionString = Zstd.zstd().versionString();
    assertNotNull(versionString);
    assertFalse(versionString.isEmpty());
  }

  @Test
  void testCompressionLevelLimits() {
    int minLevel = Zstd.zstd().minCompressionLevel();
    int maxLevel = Zstd.zstd().maxCompressionLevel();
    int defaultLevel = Zstd.zstd().defaultCompressionLevel();

    assertTrue(minLevel < 0);
    assertTrue(maxLevel > 0);
    assertTrue(defaultLevel >= minLevel && defaultLevel <= maxLevel);
  }

  @Test
  void testCompressBound() {
    long bound1 = Zstd.zstd().compressBound(1024);
    long bound2 = Zstd.zstd().compressBound(2048);

    assertTrue(bound1 > 1024);
    assertTrue(bound2 > 2048);
    assertTrue(bound2 > bound1);
  }

  @Test
  void testGetFrameContentSize() throws ZstdException {
    byte[] original = "Test frame content size".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          Zstd.zstd()
              .compress(
                  dst, compressBound, src, original.length, Zstd.zstd().defaultCompressionLevel());

      long frameSize = Zstd.zstd().frameContentSize(dst, compressed);
      assertEquals(original.length, frameSize);
    }
  }

  @Test
  void testFindFrameCompressedSize() throws ZstdException {
    byte[] original = "Frame size detection test".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = Zstd.zstd().compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed =
          Zstd.zstd()
              .compress(
                  dst, compressBound, src, original.length, Zstd.zstd().defaultCompressionLevel());

      long frameCompressedSize = Zstd.zstd().findFrameCompressedSize(dst, compressed);
      assertEquals(compressed, frameCompressedSize);
    }
  }

  @Test
  void testErrorHandling() {
    assertTrue(Zstd.zstd().isError(-1));
    assertFalse(Zstd.zstd().isError(0));
    assertFalse(Zstd.zstd().isError(100));
  }

  @Test
  void testErrorCodeAndNameMatchThrownException() {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment src = arena.allocate(100);
      MemorySegment dst = arena.allocate(1);
      ZstdException e =
          assertThrows(
              ZstdException.class,
              () -> Zstd.zstd().compress(dst, 1, src, 100, Zstd.zstd().defaultCompressionLevel()));

      long raw = e.rawValue();
      assertTrue(Zstd.zstd().isError(raw));
      assertEquals(e.errorCode(), Zstd.zstd().errorCode(raw));
      assertEquals(e.errorName(), Zstd.zstd().errorName(raw));
      assertFalse(Zstd.zstd().errorName(raw).isEmpty());
    }
  }

  @Test
  void testMessageOnlyExceptionCarriesNoNativeCode() {
    ZstdException e = new ZstdException("synthetic condition");
    assertEquals(0, e.rawValue());
    assertEquals(ZstdErrorCode.UNKNOWN, e.errorCode());
    assertEquals("synthetic condition", e.errorName());
    assertEquals("synthetic condition", e.getMessage());
  }

  @Test
  void testAllParameterBoundsAreSane() {
    for (ZstdCompressionParameter parameter : ZstdCompressionParameter.values()) {
      ZstdParameterBounds bounds = parameter.bounds();
      assertTrue(bounds.lowerBound() <= bounds.upperBound(), parameter.name());
    }
    for (ZstdDecompressionParameter parameter : ZstdDecompressionParameter.values()) {
      ZstdParameterBounds bounds = parameter.bounds();
      assertTrue(bounds.lowerBound() <= bounds.upperBound(), parameter.name());
    }

    // Compression level bounds must agree with the dedicated accessors.
    ZstdParameterBounds level = ZstdCompressionParameter.COMPRESSION_LEVEL.bounds();
    assertEquals(Zstd.zstd().minCompressionLevel(), level.lowerBound());
    assertEquals(Zstd.zstd().maxCompressionLevel(), level.upperBound());
  }
}
