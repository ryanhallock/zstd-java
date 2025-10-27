package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class ZstdDecompressionDictionary implements AutoCloseable {
    private final MemorySegment ddict;
    private final long dictSize;

    public ZstdDecompressionDictionary(MemorySegment dict) {
        this(dict, dict.byteSize());
    }

    public ZstdDecompressionDictionary(MemorySegment dict, long dictSize) {
        Objects.requireNonNull(dict, "dict");
        if (dictSize > dict.byteSize())
            throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
        this.ddict = ZSTD_h.ZSTD_createDDict(dict, dictSize);
        this.dictSize = dictSize;
    }

    MemorySegment ddict() {
        return this.ddict;
    }

    public long size() {
        return dictSize;
    }

    public long sizeOf() {
        return ZSTD_h.ZSTD_sizeof_DDict(this.ddict);
    }

    public int dictID() {
        return ZSTD_h.ZSTD_getDictID_fromDDict(this.ddict);
    }

    @Override
    public void close() throws ZstdException {
        ZstdResult.check(ZSTD_h.ZSTD_freeDDict(this.ddict));
    }
}
