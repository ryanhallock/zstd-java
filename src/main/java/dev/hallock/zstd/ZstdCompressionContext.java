package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class ZstdCompressionContext implements AutoCloseable {
    private final MemorySegment cctx;

    public ZstdCompressionContext() {
        this.cctx = ZSTD_h.ZSTD_createCCtx();
    }

    /// @apiNote Uses compress2
    public ZstdResult compress(MemorySegment dst, long dstCapacity, MemorySegment src, long srcSize) {
        Objects.requireNonNull(dst, "dst");
        if (dstCapacity > dst.byteSize())
            throw new IllegalArgumentException("dstCapacity cannot be larger than dst.byteSize()");
        Objects.requireNonNull(src, "src");
        if (srcSize > src.byteSize())
            throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_compress2(this.cctx, dst, dstCapacity, src, srcSize));
    }

    /// @apiNote Uses compressStream2
    public ZstdResult compressStream(ZstdOutputBuffer output, ZstdInputBuffer input, ZstdEndDirective endDirective) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(endDirective, "endDirective");
        return ZstdResult.from(ZSTD_h.ZSTD_compressStream2(this.cctx, output.output(), input.input(), endDirective.value()));
    }

    public ZstdResult loadDictionary(MemorySegment dict, long dictSize) {
        Objects.requireNonNull(dict, "dict");
        if (dictSize > dict.byteSize())
            throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_CCtx_loadDictionary(this.cctx, dict, dictSize));
    }

    /// Equivalent to refCDict
    public ZstdResult refDictionary(ZstdCompressionDictionary dict) {
        Objects.requireNonNull(dict, "dict");
        return ZstdResult.from(ZSTD_h.ZSTD_CCtx_refCDict(this.cctx, dict.cdict()));
    }

    public ZstdResult refPrefix(MemorySegment prefix, long prefixSize) {
        Objects.requireNonNull(prefix, "prefix");
        if (prefixSize > prefix.byteSize())
            throw new IllegalArgumentException("prefixSize cannot be larger than prefix.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_CCtx_refPrefix(this.cctx, prefix, prefixSize));
    }

    public ZstdResult parameter(ZstdCompressionParameter parameter, int value) {
        Objects.requireNonNull(parameter, "parameter");
        //TODO we could check bounds here if we wanted to
        return ZstdResult.from(ZSTD_h.ZSTD_CCtx_setParameter(this.cctx, parameter.value(), value));
    }

    public ZstdResult pledgedSrcSize(long srcSize) {
        return ZstdResult.from(ZSTD_h.ZSTD_CCtx_setPledgedSrcSize(this.cctx, srcSize));
    }

    @Override
    public void close() throws ZstdException {
        ZstdResult.check(ZSTD_h.ZSTD_freeCCtx(this.cctx));
    }
}
