package dev.hallock.zstd;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * An {@link InputStream} that decompresses zstd-compressed data from an underlying stream.
 *
 * <p>Instances are created via {@link Zstd#createDecompressorInputStream(InputStream)}.
 *
 * <p>Handles multiple concatenated frames: reading continues across frame boundaries until the
 * underlying stream is exhausted. A stream that ends mid-frame raises {@link EOFException}.
 *
 * <p><strong>Resource management:</strong> each instance owns native memory that is released only
 * by {@link #close()}. Abandoning an instance without closing it permanently leaks that memory;
 * there is deliberately no automatic cleaner.
 *
 * <p><strong>Thread confinement:</strong> instances allocate their working buffers from a confined
 * arena and must be used only from the thread that created them.
 */
public final class ZstdDecompressorInputStream extends InputStream {
  private final InputStream in;
  private final Arena arena;
  private final ZstdDecompressionContext context;
  private final MemorySegment inSegment;
  private final MemorySegment outSegment;
  private final ZstdInputBuffer inBuffer;
  private final ZstdOutputBuffer outBuffer;
  private final byte[] copyBuffer;
  private final byte[] single = new byte[1];
  private int outReadPosition;
  private long lastReturn;
  private boolean underlyingEof;
  private boolean broken;
  private boolean closed;

  ZstdDecompressorInputStream(Zstd zstd, InputStream in) throws IOException {
    this.in = Objects.requireNonNull(in, "in");
    Arena arena = Arena.ofConfined();
    ZstdDecompressionContext context = null;
    try {
      context = zstd.createDecompressionContext();
      MemorySegment inSegment = arena.allocate(zstd.recommendedDStreamInSize());
      MemorySegment outSegment = arena.allocate(zstd.recommendedDStreamOutSize());
      this.inSegment = inSegment;
      this.outSegment = outSegment;
      this.inBuffer = zstd.createInputBuffer(arena, inSegment, 0, 0);
      this.outBuffer = zstd.createOutputBuffer(arena, outSegment);
      this.copyBuffer = new byte[(int) inSegment.byteSize()];
    } catch (Throwable t) {
      if (context != null) {
        context.close();
      }
      arena.close();
      if (t instanceof ZstdException) {
        throw new IOException(t);
      }
      throw t;
    }
    this.arena = arena;
    this.context = context;
    super();
  }

  @Override
  public int read() throws IOException {
    int n = read(single, 0, 1);
    return n == -1 ? -1 : single[0] & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, b.length);
    ensureOpen();
    if (len == 0) return 0;
    try {
      while (true) {
        int available = (int) outBuffer.position() - outReadPosition;
        if (available > 0) {
          int n = Math.min(available, len);
          MemorySegment.copy(outSegment, ValueLayout.JAVA_BYTE, outReadPosition, b, off, n);
          outReadPosition += n;
          return n;
        }
        outBuffer.position(0);
        outReadPosition = 0;
        if (inBuffer.position() == inBuffer.size()) {
          if (!underlyingEof) {
            int read;
            do {
              // InputStream.read may contractually return 0; retry until data or EOF.
              read = in.read(copyBuffer, 0, copyBuffer.length);
            } while (read == 0);
            if (read == -1) {
              underlyingEof = true;
            } else {
              MemorySegment.copy(copyBuffer, 0, inSegment, ValueLayout.JAVA_BYTE, 0, read);
              inBuffer.position(0);
              inBuffer.size(read);
            }
          }
          if (underlyingEof) {
            if (lastReturn == 0) return -1;
            // Nonzero with input consumed also occurs when the previous output buffer filled
            // exactly;
            // probe once before declaring truncation.
            lastReturn = context.decompressStream(outBuffer, inBuffer);
            if (outBuffer.position() > 0) continue;
            if (lastReturn == 0) return -1;
            throw new EOFException("Truncated zstd stream: input ended mid-frame");
          }
        }
        lastReturn = context.decompressStream(outBuffer, inBuffer);
      }
    } catch (ZstdException e) {
      broken = true;
      throw new IOException(e);
    }
  }

  @Override
  public int available() {
    // A broken stream reports 0 rather than buffered bytes read() will never serve.
    return (closed || broken) ? 0 : (int) outBuffer.position() - outReadPosition;
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    Throwable primary = null;
    try {
      context.close();
    } catch (Throwable t) {
      primary = Zstd.chain(null, t);
    }
    try {
      arena.close();
    } catch (Throwable t) {
      primary = Zstd.chain(primary, t);
    }
    try {
      in.close();
    } catch (Throwable t) {
      primary = Zstd.chain(primary, t);
    }
    Zstd.rethrowIfPresent(primary);
  }

  private void ensureOpen() throws IOException {
    if (closed) throw new IOException("Stream closed");
    if (broken) throw new IOException("Stream is in a failed state after a previous error");
  }
}
