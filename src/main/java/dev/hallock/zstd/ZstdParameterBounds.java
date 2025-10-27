package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_bounds;

import java.lang.foreign.MemorySegment;

public final class ZstdParameterBounds {
    private final MemorySegment input;

    public ZstdParameterBounds(MemorySegment input) {
        this.input = input;
    }

    /// Equivalent to error
    public ZstdResult result() {
        return ZstdResult.from(ZSTD_bounds.error(this.input));
    }

    public int lowerBound() {
        return ZSTD_bounds.lowerBound(this.input);
    }

    public int upperBound() {
        return ZSTD_bounds.upperBound(this.input);
    }
}
