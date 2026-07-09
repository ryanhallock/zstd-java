package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionContext;
import dev.hallock.zstd.ZstdCompressionDictionary;
import dev.hallock.zstd.ZstdDecompressionContext;
import dev.hallock.zstd.ZstdDecompressionDictionary;
import dev.hallock.zstd.ZstdException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@ZstdTest
class ZstdDictionaryTest {

  @Test
  void testCompressionDictionary(Zstd zstd) throws Exception {
    byte[] dict = "common words: the, and, test, data".repeat(10).getBytes(StandardCharsets.UTF_8);
    byte[] original = "test data with common words".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionDictionary cdict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), zstd.defaultCompressionLevel());
        ZstdCompressionContext cctx = zstd.createCompressionContext()) {

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      cctx.refDictionary(cdict);
      long compressed = cctx.compress(dst, compressBound, src, original.length);

      assertTrue(compressed > 0);
      assertTrue(cdict.sizeOf() > 0);
    }
  }

  @Test
  void testDecompressionDictionary(Zstd zstd) throws Exception {
    byte[] dict = "common words: the, and, test, data".repeat(10).getBytes(StandardCharsets.UTF_8);
    byte[] original = "test data with common words".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionDictionary cdict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), zstd.defaultCompressionLevel());
        ZstdDecompressionDictionary ddict =
            zstd.createDecompressionDictionary(arena.allocateFrom(ValueLayout.JAVA_BYTE, dict));
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      cctx.refDictionary(cdict);
      long compressed = cctx.compress(dst, compressBound, src, original.length);

      MemorySegment decompressDst = arena.allocate(original.length);
      dctx.refDictionary(ddict);
      long decompressed = dctx.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testDictionaryWithPrefix(Zstd zstd) throws Exception {
    byte[] prefix = "prefix data".getBytes(StandardCharsets.UTF_8);
    byte[] original = "prefix data and more".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      MemorySegment prefixSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, prefix);
      cctx.refPrefix(prefixSeg, prefix.length);

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed = cctx.compress(dst, compressBound, src, original.length);

      dctx.refPrefix(prefixSeg, prefix.length);
      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = dctx.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testLoadDictionary(Zstd zstd) throws Exception {
    byte[] dict = "dictionary content".getBytes(StandardCharsets.UTF_8);
    byte[] original = "test with dictionary content".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {

      MemorySegment dictSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, dict);
      cctx.loadDictionary(dictSeg, dict.length);

      MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
      long compressBound = zstd.compressBound(original.length);
      MemorySegment dst = arena.allocate(compressBound);

      long compressed = cctx.compress(dst, compressBound, src, original.length);

      dctx.loadDictionary(dictSeg, dict.length);
      MemorySegment decompressDst = arena.allocate(original.length);
      long decompressed = dctx.decompress(decompressDst, original.length, dst, compressed);

      byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
      assertArrayEquals(original, result);
    }
  }

  @Test
  void testGetDictID(Zstd zstd) throws Exception {
    byte[] dict = "dictionary with ID".getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionDictionary cdict =
            zstd.createCompressionDictionary(
                arena.allocateFrom(ValueLayout.JAVA_BYTE, dict), zstd.defaultCompressionLevel())) {

      // Raw-content (untrained) dictionaries have no embedded ID.
      assertEquals(0, cdict.dictId());
    }
  }

  @Test
  void trainedDictionaryRoundTripAndFrameId(Zstd zstd) {
    byte[] dict = TestVectors.load("dict.bin");
    byte[] original =
        "sample data record number 7 with shared structure and common tokens\n"
            .getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdCompressionContext cctx = zstd.createCompressionContext();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      MemorySegment dictSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, dict);
      assertEquals(TestVectors.TRAINED_DICT_ID, zstd.dictIdFromDict(dictSeg, dict.length));

      try (ZstdCompressionDictionary cdict =
              zstd.createCompressionDictionary(dictSeg, zstd.defaultCompressionLevel());
          ZstdDecompressionDictionary ddict = zstd.createDecompressionDictionary(dictSeg)) {
        assertEquals(TestVectors.TRAINED_DICT_ID, cdict.dictId());
        assertEquals(TestVectors.TRAINED_DICT_ID, ddict.dictId());

        MemorySegment src = arena.allocateFrom(ValueLayout.JAVA_BYTE, original);
        long bound = zstd.compressBound(original.length);
        MemorySegment dst = arena.allocate(bound);
        cctx.refDictionary(cdict);
        long compressed = cctx.compress(dst, bound, src, original.length);

        // The frame must record the trained dictionary's ID; a silently no-op dictionary
        // attach would leave this at 0.
        assertEquals(TestVectors.TRAINED_DICT_ID, zstd.dictIdFromFrame(dst, compressed));

        // Without the dictionary, decompression must fail rather than round-trip.
        MemorySegment decompressDst = arena.allocate(original.length);
        try (ZstdDecompressionContext plain = zstd.createDecompressionContext()) {
          assertThrows(
              ZstdException.class,
              () -> plain.decompress(decompressDst, original.length, dst, compressed));
        }

        dctx.refDictionary(ddict);
        long decompressed = dctx.decompress(decompressDst, original.length, dst, compressed);
        byte[] result = decompressDst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE);
        assertArrayEquals(original, result);
      }
    }
  }

  @Test
  void decompressesCliDictionaryFrame(Zstd zstd) {
    byte[] dict = TestVectors.load("dict.bin");
    byte[] frame = TestVectors.load("dictframe.zst");
    byte[] expected = TestVectors.PLAIN_TEXT.getBytes(StandardCharsets.UTF_8);

    try (Arena arena = Arena.ofConfined();
        ZstdDecompressionContext dctx = zstd.createDecompressionContext()) {
      MemorySegment dictSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, dict);
      MemorySegment frameSeg = arena.allocateFrom(ValueLayout.JAVA_BYTE, frame);
      assertEquals(TestVectors.TRAINED_DICT_ID, zstd.dictIdFromFrame(frameSeg, frame.length));

      try (ZstdDecompressionDictionary ddict = zstd.createDecompressionDictionary(dictSeg)) {
        dctx.refDictionary(ddict);
        MemorySegment dst = arena.allocate(expected.length);
        long decompressed = dctx.decompress(dst, expected.length, frameSeg, frame.length);
        assertArrayEquals(expected, dst.asSlice(0, decompressed).toArray(ValueLayout.JAVA_BYTE));
      }
    }
  }
}
