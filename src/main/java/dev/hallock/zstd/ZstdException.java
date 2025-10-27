package dev.hallock.zstd;

import java.io.IOException;

public final class ZstdException extends IOException {
    public ZstdException(ZstdResult.Error result) {
        super("ZSTD error %d|%d: %s".formatted(result.result(), result.code(), result.name()));
    }
}
