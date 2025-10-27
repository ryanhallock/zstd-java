package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class ZstdCompressionStream implements AutoCloseable {
    private final MemorySegment zcs;
    private final int compressionLevel;

    public ZstdCompressionStream(int compressionLevel) throws ZstdException {
        this.zcs = ZSTD_h.ZSTD_createCStream();
        this.compressionLevel = compressionLevel;
        ZstdResult.check(ZSTD_h.ZSTD_initCStream(this.zcs, this.compressionLevel));
    }

    public int compressionLevel() {
        return this.compressionLevel;
    }

    public long sizeOf() {
        return ZSTD_h.ZSTD_sizeof_CStream(this.zcs);
    }

    public ZstdResult compressionStream(ZstdOutputBuffer output, ZstdInputBuffer input) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        return ZstdResult.from(ZSTD_h.ZSTD_compressStream(this.zcs, output.output(), input.input()));
    }

    /// Equivalent to flushStream
    /// @param output the output
    public ZstdResult flush(ZstdOutputBuffer output) {
        Objects.requireNonNull(output, "output");
        return ZstdResult.from(ZSTD_h.ZSTD_flushStream(this.zcs, output.output()));
    }


    /// Equivalent to endStream
    /// @param output the output
    public ZstdResult end(ZstdOutputBuffer output) {
        Objects.requireNonNull(output, "output");
        return ZstdResult.from(ZSTD_h.ZSTD_endStream(this.zcs, output.output()));
    }

    @Override
    public void close() throws ZstdException {
        ZstdResult.check(ZSTD_h.ZSTD_freeCStream(this.zcs));
    }

    /// Returns the recommended size for the input buffer
    public static long streamInSize() {
        return ZSTD_h.ZSTD_CStreamInSize();
    }

    /// Returns the recommended size for the output buffer
    public static long streamOutSize() {
        return ZSTD_h.ZSTD_CStreamOutSize();
    }
}
