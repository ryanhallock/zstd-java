package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class ZstdDecompressionContext implements AutoCloseable {
    private final MemorySegment dctx;

    public ZstdDecompressionContext() {
        this.dctx = ZSTD_h.ZSTD_createDCtx();
    }

    public ZstdResult decompress(MemorySegment dst, long dstCapacity, MemorySegment src, long srcSize) {
        Objects.requireNonNull(dst, "dst");
        if (dstCapacity > dst.byteSize())
            throw new IllegalArgumentException("dstCapacity cannot be larger than dst.byteSize()");
        Objects.requireNonNull(src, "src");
        if (srcSize > src.byteSize())
            throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_decompressDCtx(this.dctx, dst, dstCapacity, src, srcSize));
    }

    public ZstdResult loadDictionary(MemorySegment dict, long dictSize) {
        Objects.requireNonNull(dict, "dict");
        if (dictSize > dict.byteSize())
            throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_DCtx_loadDictionary(this.dctx, dict, dictSize));
    }

    /// Equivalent to refDDict
    public ZstdResult refDictionary(ZstdDecompressionDictionary dict) {
        Objects.requireNonNull(dict, "dict");
        return ZstdResult.from(ZSTD_h.ZSTD_DCtx_refDDict(this.dctx, dict.ddict()));
    }

    public ZstdResult refPrefix(MemorySegment prefix, long prefixSize) {
        Objects.requireNonNull(prefix, "prefix");
        if (prefixSize > prefix.byteSize())
            throw new IllegalArgumentException("prefixSize cannot be larger than prefix.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_DCtx_refPrefix(this.dctx, prefix, prefixSize));
    }

    public ZstdResult parameter(ZstdDecompressionParameter parameter, int value) {
        Objects.requireNonNull(parameter, "parameter");
        //TODO we could check bounds here if we wanted to
        return ZstdResult.from(ZSTD_h.ZSTD_DCtx_setParameter(this.dctx, parameter.value(), value));
    }

    @Override
    public void close() throws ZstdException {
        ZstdResult.check(ZSTD_h.ZSTD_freeDCtx(this.dctx));
    }
}
