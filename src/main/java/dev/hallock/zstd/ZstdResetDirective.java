package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.util.Optional;

public enum ZstdResetDirective {
    NONE(0),
    SESSION_ONLY(ZSTD_h.ZSTD_reset_session_only()),
    PARAMETERS(ZSTD_h.ZSTD_reset_parameters()),
    SESSION_AND_PARAMETERS(ZSTD_h.ZSTD_reset_session_and_parameters());

    private final int value;

    ZstdResetDirective(int value) {
        this.value = value;
    }

    public int value() {
        return this.value;
    }

    public static Optional<ZstdResetDirective> fromValue(int value) {
        for (ZstdResetDirective strategy : ZstdResetDirective.values()) {
            if (strategy.value() == value) {
                return Optional.of(strategy);
            }
        }
        return Optional.empty();
    }
}
