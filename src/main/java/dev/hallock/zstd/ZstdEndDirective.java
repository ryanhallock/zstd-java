package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.util.Optional;

public enum ZstdEndDirective {
    CONTINUE(ZSTD_h.ZSTD_e_continue()),
    FLUSH(ZSTD_h.ZSTD_e_flush()),
    END(ZSTD_h.ZSTD_e_end());

    private final int value;

    ZstdEndDirective(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }

    public static Optional<ZstdEndDirective> fromValue(int value) {
        for (ZstdEndDirective strategy : ZstdEndDirective.values()) {
            if (strategy.value() == value) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}
