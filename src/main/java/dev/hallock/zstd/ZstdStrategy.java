package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.util.Optional;

public enum ZstdStrategy {
    FAST(ZSTD_h.ZSTD_fast()),
    DFAST(ZSTD_h.ZSTD_dfast()),
    GREEDY(ZSTD_h.ZSTD_greedy()),
    LAZY(ZSTD_h.ZSTD_lazy()),
    LAZY2(ZSTD_h.ZSTD_lazy2()),
    BTLAZY2(ZSTD_h.ZSTD_btlazy2()),
    BTOPT(ZSTD_h.ZSTD_btopt()),
    BTULTRA(ZSTD_h.ZSTD_btultra()),
    BTULTRA2(ZSTD_h.ZSTD_btultra2());

    private final int value;

    ZstdStrategy(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }

    public static Optional<ZstdStrategy> fromValue(int value) {
        for (ZstdStrategy strategy : ZstdStrategy.values()) {
            if (strategy.value() == value) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}
