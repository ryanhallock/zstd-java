package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.SegmentAllocator;

public enum ZstdDecompressionParameter {
    WINDOW_LOG_MAX(ZSTD_h.ZSTD_d_windowLogMax()),
    //TODO experimental
    ;

    private final int value;

    ZstdDecompressionParameter(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }

    public ZstdParameterBounds bounds(SegmentAllocator allocator) {
        return new ZstdParameterBounds(ZSTD_h.ZSTD_dParam_getBounds(allocator, this.value));
    }
}
