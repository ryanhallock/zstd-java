package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressorOutputStream;
import dev.hallock.zstd.ZstdDecompressorInputStream;
import dev.hallock.zstd.ZstdException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The byte[] convenience tier and the java.io stream adapters. */
@ZstdTest
class ZstdConvenienceTest {

  @Test
  void byteArrayRoundTrip(Zstd zstd) {
    byte[] original = "byte array convenience tier".repeat(50).getBytes(StandardCharsets.UTF_8);
    byte[] compressed = zstd.compress(original);
    assertTrue(compressed.length > 0);
    assertArrayEquals(original, zstd.decompress(compressed));
  }

  @Test
  void byteArrayRoundTripWithLevel(Zstd zstd) {
    byte[] original = new byte[128 * 1024];
    new Random(5).nextBytes(original);
    byte[] compressed = zstd.compress(original, 1);
    assertArrayEquals(original, zstd.decompress(compressed));
  }

  @Test
  void byteArrayRoundTripEmpty(Zstd zstd) {
    byte[] compressed = zstd.compress(new byte[0]);
    assertTrue(compressed.length > 0);
    assertArrayEquals(new byte[0], zstd.decompress(compressed));
  }

  @Test
  void byteArrayDecompressHandlesUnknownContentSize(Zstd zstd) throws IOException {
    // The stream adapter produces frames without a recorded content size, forcing the
    // streaming-accumulator path in decompress(byte[]).
    byte[] original = "unknown content size".repeat(1000).getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink)) {
      out.write(original);
    }
    byte[] frame = sink.toByteArray();
    assertArrayEquals(original, zstd.decompress(frame));
  }

  @Test
  void byteArrayDecompressRejectsGarbage(Zstd zstd) {
    assertThrows(ZstdException.class, () -> zstd.decompress(new byte[] {9, 8, 7, 6, 5, 4}));
    // An empty input holds zero frames and decompresses to an empty array.
    assertArrayEquals(new byte[0], zstd.decompress(new byte[0]));
  }

  @Test
  void streamRoundTripWithFlushAndOddChunks(Zstd zstd) throws IOException {
    byte[] original = new byte[300_000];
    new Random(31).nextBytes(original);
    // Make it partially compressible.
    Arrays.fill(original, 100_000, 200_000, (byte) 42);

    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink, 5)) {
      out.write(original, 0, 1);
      out.write(original, 1, 33_333);
      out.flush();
      out.write(original, 33_334, original.length - 33_334);
    }

    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      byte[] result = new byte[original.length];
      int off = 0;
      while (off < result.length) {
        int n = in.read(result, off, Math.min(7777, result.length - off));
        assertTrue(n > 0);
        off += n;
      }
      assertEquals(-1, in.read());
      assertArrayEquals(original, result);
    }
  }

  @Test
  void flushBoundaryIsImmediatelyDecodable(Zstd zstd) throws IOException {
    byte[] first = "decodable at the flush boundary".getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink)) {
      out.write(first);
      out.flush();

      // Without the frame epilogue, everything written before flush() must already decode.
      try (ZstdDecompressorInputStream in =
          zstd.createDecompressorInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
        byte[] decoded = new byte[first.length];
        int off = 0;
        while (off < decoded.length) {
          int n = in.read(decoded, off, decoded.length - off);
          assertTrue(n > 0);
          off += n;
        }
        assertArrayEquals(first, decoded);
      }
    }
  }

  @Test
  void emptyStreamProducesDecodableEmptyFrame(Zstd zstd) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    zstd.createCompressorOutputStream(sink).close();
    assertTrue(sink.size() > 0);
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(sink.toByteArray()))) {
      assertEquals(-1, in.read());
    }
  }

  @Test
  void truncatedStreamRaisesEofException(Zstd zstd) throws IOException {
    byte[] original = new byte[100_000];
    new Random(13).nextBytes(original);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink)) {
      out.write(original);
    }
    byte[] truncated = Arrays.copyOf(sink.toByteArray(), sink.size() / 2);

    assertThrows(
        EOFException.class,
        () -> {
          try (ZstdDecompressorInputStream in =
              zstd.createDecompressorInputStream(new ByteArrayInputStream(truncated))) {
            in.readAllBytes();
          }
        });
  }

  @Test
  void corruptStreamRaisesIoException(Zstd zstd) {
    byte[] garbage = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    assertThrows(
        IOException.class,
        () -> {
          try (ZstdDecompressorInputStream in =
              zstd.createDecompressorInputStream(new ByteArrayInputStream(garbage))) {
            in.readAllBytes();
          }
        });
  }

  @Test
  void finishCompletesFrameAndLeavesUnderlyingStreamUsable(Zstd zstd) throws IOException {
    byte[] original = "embedded frame payload".repeat(500).getBytes(StandardCharsets.UTF_8);
    byte[] trailer = "TRAILING PLAINTEXT".getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink);
    out.write(original);
    out.finish();
    byte[] frame = sink.toByteArray();

    // The finished frame is complete and decodes on its own.
    assertArrayEquals(original, zstd.decompress(frame));

    // The underlying stream stays usable for the caller after finish().
    sink.write(trailer);

    // Writes and flushes after finish() fail; a second finish() is a no-op.
    assertThrows(IOException.class, () -> out.write(1));
    assertThrows(IOException.class, () -> out.write(original, 0, 4));
    assertThrows(IOException.class, out::flush);
    out.finish();

    // close() after finish() must release resources without emitting more frame data.
    out.close();
    byte[] whole = sink.toByteArray();
    assertEquals(frame.length + trailer.length, whole.length);
    assertArrayEquals(trailer, Arrays.copyOfRange(whole, frame.length, whole.length));
  }

  @Test
  void finishOnFreshStreamProducesDecodableEmptyFrame(Zstd zstd) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    try (ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink)) {
      out.finish();
      assertTrue(sink.size() > 0);
      assertArrayEquals(new byte[0], zstd.decompress(sink.toByteArray()));
    }
  }

  @Test
  void brokenDecompressorStreamStaysBrokenWithoutNativeCalls(Zstd zstd) throws IOException {
    byte[] garbage = new byte[64];
    new Random(17).nextBytes(garbage);
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(garbage))) {
      IOException first = assertThrows(IOException.class, in::readAllBytes);
      assertTrue(first.getCause() instanceof ZstdException, String.valueOf(first.getCause()));

      // The stream latches into a failed state: further reads fail up front (no native decode is
      // attempted on the poisoned context) and report no buffered data.
      IOException second = assertThrows(IOException.class, in::read);
      assertTrue(second.getMessage().contains("failed state"), second.getMessage());
      assertEquals(0, in.available());
    }
  }

  @Test
  void compressorStreamLatchesBrokenWhenUnderlyingWriteFails(Zstd zstd) throws IOException {
    class FailingSink extends OutputStream {
      boolean fail = true;
      int writesAfterRecovery;

      @Override
      public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        if (fail) throw new IOException("sink failed");
        writesAfterRecovery++;
      }
    }
    FailingSink sink = new FailingSink();
    ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(sink);
    out.write("poisoned".getBytes(StandardCharsets.UTF_8));
    IOException failure = assertThrows(IOException.class, out::finish);
    assertEquals("sink failed", failure.getMessage());

    // The frame bytes the sink dropped are unrecoverable: the stream latches into a failed state
    // instead of re-running the epilogue against a recovered sink, which would emit a gapped frame.
    IOException second = assertThrows(IOException.class, () -> out.write(1));
    assertTrue(second.getMessage().contains("failed state"), second.getMessage());

    sink.fail = false;
    out.close();
    assertEquals(0, sink.writesAfterRecovery);
  }

  @Test
  void writingToClosedStreamFails(Zstd zstd) throws IOException {
    ZstdCompressorOutputStream out = zstd.createCompressorOutputStream(new ByteArrayOutputStream());
    out.close();
    assertThrows(IOException.class, () -> out.write(1));
    // Second close is a no-op, not a failure.
    out.close();
  }

  @Test
  void readingFromClosedStreamFails(Zstd zstd) throws IOException {
    byte[] frame = zstd.compress(new byte[] {1, 2, 3});
    ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(frame));
    in.close();
    assertThrows(IOException.class, in::read);
    assertEquals(0, in.available());
    in.close();
  }

  @Test
  void singleByteReadsMaskToUnsigned(Zstd zstd) throws IOException {
    byte[] original = {(byte) 0xFF, 0x00, (byte) 0x80};
    byte[] frame = zstd.compress(original);
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(frame))) {
      assertEquals(0xFF, in.read());
      assertEquals(0x00, in.read());
      assertEquals(0x80, in.read());
      assertEquals(-1, in.read());
    }
  }

  @Test
  void availableReflectsDecodedBufferedBytes(Zstd zstd) throws IOException {
    byte[] original = new byte[4096];
    Arrays.fill(original, (byte) 7);
    byte[] frame = zstd.compress(original);
    try (ZstdDecompressorInputStream in =
        zstd.createDecompressorInputStream(new ByteArrayInputStream(frame))) {
      assertEquals(0, in.available());
      assertEquals(7, in.read());
      // The rest of the decoded block is buffered and immediately available.
      assertEquals(original.length - 1, in.available());
      assertEquals(original.length - 1, in.readAllBytes().length);
      assertEquals(0, in.available());
    }
  }
}
