package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class ZstdCompressionDictionary implements AutoCloseable {
    private final MemorySegment cdict;
    private final long dictSize;
    private final int compressionLevel;

    public ZstdCompressionDictionary(MemorySegment dict, int compressionLevel) {
        this(dict, dict.byteSize(), compressionLevel);
    }

    public ZstdCompressionDictionary(MemorySegment dict, long dictSize, int compressionLevel) {
        Objects.requireNonNull(dict, "dict");
        if (dictSize > dict.byteSize())
            throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
        this.cdict = ZSTD_h.ZSTD_createCDict(dict, dictSize, compressionLevel);
        this.dictSize = dictSize;
        this.compressionLevel = compressionLevel;
    }

    MemorySegment cdict() {
        return this.cdict;
    }

    public int compressionLevel() {
        return compressionLevel;
    }

    public long size() {
        return dictSize;
    }

    public long sizeOf() {
        return ZSTD_h.ZSTD_sizeof_CDict(this.cdict);
    }

    public int dictID() {
        return ZSTD_h.ZSTD_getDictID_fromCDict(this.cdict);
    }

    @Override
    public void close() throws ZstdException {
        ZstdResult.check(ZSTD_h.ZSTD_freeCDict(this.cdict));
    }
}
