package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdCompressionDictionary;
import dev.hallock.zstd.ZstdDecompressionDictionary;
import dev.hallock.zstd.ZstdException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@ZstdTest
class ZstdDictionaryCoverageTest {

  @Test
  void testCompressionDictionarySize(Zstd zstd) throws Exception {
    byte[] dict = "CDictSize".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionDictionary cdict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), zstd.defaultCompressionLevel())) {

      assertEquals(dict.length, cdict.size());
      assertTrue(cdict.sizeOf() > 0);
    }
  }

  @Test
  void testCompressionDictionaryLevel(Zstd zstd) throws Exception {
    byte[] dict = "test".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionDictionary cdict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), dict.length, 9)) {

      assertEquals(9, cdict.compressionLevel());
    }
  }

  @Test
  void testCompressionDictionaryDictID(Zstd zstd) throws Exception {
    byte[] raw = "dict with id".getBytes(StandardCharsets.UTF_8);
    byte[] trained = TestVectors.load("dict.bin");

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionDictionary rawDict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, raw), zstd.defaultCompressionLevel());
        ZstdCompressionDictionary trainedDict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, trained),
                zstd.defaultCompressionLevel())) {

      // Raw-content dictionaries carry no embedded ID; trained ones report it exactly.
      assertEquals(0, rawDict.dictId());
      assertEquals(TestVectors.TRAINED_DICT_ID, trainedDict.dictId());
    }
  }

  @Test
  void testDecompressionDictionarySize(Zstd zstd) throws Exception {
    byte[] dict = "decompression dict".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionDictionary ddict =
            zstd.createDecompressionDictionary(arena.allocateFrom(ValueLayout.JAVA_BYTE, dict))) {

      assertEquals(dict.length, ddict.size());
      assertTrue(ddict.sizeOf() > 0);
    }
  }

  @Test
  void testDecompressionDictionaryWithSize(Zstd zstd) throws Exception {
    byte[] dict = "test dict data".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment dictSeg = arena.allocate(100);
      dictSeg.copyFrom(MemorySegment.ofArray(dict));

      try (ZstdDecompressionDictionary ddict =
          zstd.createDecompressionDictionary(dictSeg, dict.length)) {
        assertEquals(dict.length, ddict.size());
      }
    }
  }

  @Test
  void testDecompressionDictionaryDictID(Zstd zstd) throws Exception {
    byte[] raw = "dict id test".getBytes(StandardCharsets.UTF_8);
    byte[] trained = TestVectors.load("dict.bin");

    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionDictionary rawDict =
            zstd.createDecompressionDictionary(arena.allocateFrom(ValueLayout.JAVA_BYTE, raw));
        ZstdDecompressionDictionary trainedDict =
            zstd.createDecompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, trained))) {

      assertEquals(0, rawDict.dictId());
      assertEquals(TestVectors.TRAINED_DICT_ID, trainedDict.dictId());
    }
  }

  @Test
  void testGetDictIDFromDict(Zstd zstd) {
    byte[] raw = "raw dict".getBytes(StandardCharsets.UTF_8);
    byte[] trained = TestVectors.load("dict.bin");

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment rawSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, raw);
      assertEquals(0, zstd.dictIdFromDict(rawSeg, raw.length));
      MemorySegment trainedSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, trained);
      assertEquals(TestVectors.TRAINED_DICT_ID, zstd.dictIdFromDict(trainedSeg, trained.length));
    }
  }

  @Test
  void testLifecycleAccessorsSucceedOpenAndThrowClosed(Zstd zstd) {
    byte[] dict = "lifecycle dict".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment dictSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, dict);

      ZstdCompressionDictionary cdict =
          zstd.createCompressionDictionary(dictSeg, zstd.defaultCompressionLevel());
      assertTrue(cdict.sizeOf() > 0);
      assertEquals(0, cdict.dictId());
      cdict.close();
      assertThrows(IllegalStateException.class, cdict::sizeOf);
      assertThrows(IllegalStateException.class, cdict::dictId);

      ZstdDecompressionDictionary ddict = zstd.createDecompressionDictionary(dictSeg);
      assertTrue(ddict.sizeOf() > 0);
      assertEquals(0, ddict.dictId());
      ddict.close();
      assertThrows(IllegalStateException.class, ddict::sizeOf);
      assertThrows(IllegalStateException.class, ddict::dictId);
    }
  }

  @Test
  void testGetDictIDFromFrame(Zstd zstd) throws ZstdException {
    byte[] data = "frame with dict".getBytes(StandardCharsets.UTF_8);
    byte[] dict = "dict".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionDictionary cdict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), zstd.defaultCompressionLevel());
        ZstdCompressionContext ctx = zstd.createCompressionContext()) {

      ctx.refDictionary(cdict);

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, data);
      MemorySegment dst = arena.allocate(zstd.compressBound(data.length));

      long compressed = ctx.compress(dst, zstd.compressBound(data.length), src, data.length);

      // The referenced dictionary is raw content (ID 0), so the frame must record exactly 0;
      // the trained-dictionary case is pinned in ZstdDictionaryTest.
      assertEquals(0, zstd.dictIdFromFrame(dst, compressed));
    }
  }
}
