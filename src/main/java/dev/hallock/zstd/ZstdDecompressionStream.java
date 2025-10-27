package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class ZstdDecompressionStream implements AutoCloseable {
    private final MemorySegment zds;

    public ZstdDecompressionStream() throws ZstdException {
        this.zds = ZSTD_h.ZSTD_createDStream();
        ZstdResult.check(ZSTD_h.ZSTD_initDStream(this.zds));
    }

    public ZstdResult decompressStream(ZstdOutputBuffer output, ZstdInputBuffer input) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        return ZstdResult.from(ZSTD_h.ZSTD_decompressStream(this.zds, output.output(), input.input()));
    }

    public long sizeOf() {
        return ZSTD_h.ZSTD_sizeof_DStream(this.zds);
    }

    @Override
    public void close() throws ZstdException {
        ZstdResult.check(ZSTD_h.ZSTD_freeDStream(this.zds));
    }

    /// Returns the recommended size for the input buffer
    public static long streamInSize() {
        return ZSTD_h.ZSTD_DStreamInSize();
    }

    /// Returns the recommended size for the output buffer
    public static long streamOutSize() {
        return ZSTD_h.ZSTD_DStreamOutSize();
    }
}
