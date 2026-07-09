package dev.hallock.zstd;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * An {@link OutputStream} that compresses data into a single zstd frame and writes it to an
 * underlying stream.
 *
 * <p>Instances are created via {@link Zstd#createCompressorOutputStream(OutputStream, int)}.
 *
 * <p>{@link #flush()} creates a zstd flush boundary, making everything written so far decodable on
 * the receiving side. {@link #finish()} completes the frame without closing the underlying stream,
 * so a complete zstd frame can be embedded mid-stream. {@link #close()} finishes the frame if
 * necessary and closes the underlying stream.
 *
 * <p><strong>Resource management:</strong> each instance owns native memory that is released only
 * by {@link #close()}. Abandoning an instance without closing it permanently leaks that memory;
 * there is deliberately no automatic cleaner.
 *
 * <p><strong>Thread confinement:</strong> instances allocate their working buffers from a confined
 * arena and must be used only from the thread that created them.
 */
public final class ZstdCompressorOutputStream extends OutputStream {
  private final OutputStream out;
  private final Arena arena;
  private final ZstdCompressionContext context;
  private final MemorySegment inSegment;
  private final MemorySegment outSegment;
  private final ZstdInputBuffer inBuffer;
  private final ZstdOutputBuffer outBuffer;
  private final byte[] copyBuffer;
  private final byte[] single = new byte[1];
  private boolean finished;
  private boolean broken;
  private boolean closed;

  ZstdCompressorOutputStream(Zstd zstd, OutputStream out, int compressionLevel) throws IOException {
    this.out = Objects.requireNonNull(out, "out");
    Arena arena = Arena.ofConfined();
    ZstdCompressionContext context = null;
    try {
      context = zstd.createCompressionContext();
      context.parameter(ZstdCompressionParameter.COMPRESSION_LEVEL, compressionLevel);
      MemorySegment inSegment = arena.allocate(zstd.recommendedCStreamInSize());
      MemorySegment outSegment = arena.allocate(zstd.recommendedCStreamOutSize());
      this.inSegment = inSegment;
      this.outSegment = outSegment;
      this.inBuffer = zstd.createInputBuffer(arena, inSegment, 0, 0);
      this.outBuffer = zstd.createOutputBuffer(arena, outSegment);
      this.copyBuffer = new byte[(int) outSegment.byteSize()];
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
  public void write(int b) throws IOException {
    single[0] = (byte) b;
    write(single, 0, 1);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, b.length);
    ensureOpen();
    ensureNotFinished();
    try {
      while (len > 0) {
        int chunk = (int) Math.min(len, inSegment.byteSize());
        MemorySegment.copy(b, off, inSegment, ValueLayout.JAVA_BYTE, 0, chunk);
        inBuffer.position(0);
        inBuffer.size(chunk);
        while (inBuffer.position() < inBuffer.size()) {
          outBuffer.position(0);
          context.compressStream(outBuffer, inBuffer, ZstdEndDirective.CONTINUE);
          drainOutput();
        }
        off += chunk;
        len -= chunk;
      }
    } catch (ZstdException e) {
      broken = true;
      throw new IOException(e);
    }
  }

  @Override
  public void flush() throws IOException {
    ensureOpen();
    ensureNotFinished();
    try {
      long remaining;
      do {
        outBuffer.position(0);
        remaining = context.compressStream(outBuffer, inBuffer, ZstdEndDirective.FLUSH);
        drainOutput();
      } while (remaining > 0);
    } catch (ZstdException e) {
      broken = true;
      throw new IOException(e);
    }
    out.flush();
  }

  /**
   * Finishes the zstd frame without closing or flushing the underlying stream.
   *
   * <p>All pending data and the frame epilogue are written to the underlying stream, completing the
   * frame. This enables embedding a complete zstd frame in the middle of a larger stream: the
   * underlying stream stays open and usable by the caller afterwards. Once finished, the {@code
   * write} methods and {@link #flush()} throw {@link IOException}, and {@link #close()} skips frame
   * completion, only releasing resources and closing the underlying stream.
   *
   * <p>Calling this method again after the frame has been finished has no effect.
   *
   * @throws IOException if the stream is closed or in a failed state, if a zstd error occurs, or if
   *     writing to the underlying stream fails
   */
  public void finish() throws IOException {
    ensureOpen();
    if (finished) return;
    try {
      endFrame();
    } catch (ZstdException e) {
      broken = true;
      throw new IOException(e);
    }
    finished = true;
  }

  @Override
  public void close() throws IOException {
    if (closed) return;
    closed = true;
    Throwable primary = null;
    if (!finished && !broken) {
      try {
        endFrame();
      } catch (Throwable t) {
        primary = Zstd.chain(null, t);
      }
    }
    try {
      context.close();
    } catch (Throwable t) {
      primary = Zstd.chain(primary, t);
    }
    try {
      arena.close();
    } catch (Throwable t) {
      primary = Zstd.chain(primary, t);
    }
    try {
      out.close();
    } catch (Throwable t) {
      primary = Zstd.chain(primary, t);
    }
    Zstd.rethrowIfPresent(primary);
  }

  private void endFrame() throws IOException {
    long remaining;
    do {
      outBuffer.position(0);
      remaining = context.compressStream(outBuffer, inBuffer, ZstdEndDirective.END);
      drainOutput();
    } while (remaining > 0);
  }

  private void drainOutput() throws IOException {
    int produced = (int) outBuffer.position();
    if (produced > 0) {
      MemorySegment.copy(outSegment, ValueLayout.JAVA_BYTE, 0, copyBuffer, 0, produced);
      try {
        out.write(copyBuffer, 0, produced);
      } catch (IOException e) {
        // The native context considers these bytes delivered, but the underlying stream may have
        // dropped them; retrying the frame would silently emit a gap, so poison the stream.
        broken = true;
        throw e;
      }
    }
  }

  private void ensureOpen() throws IOException {
    if (closed) throw new IOException("Stream closed");
    if (broken) throw new IOException("Stream is in a failed state after a previous error");
  }

  private void ensureNotFinished() throws IOException {
    if (finished) throw new IOException("Stream already finished");
  }
}
