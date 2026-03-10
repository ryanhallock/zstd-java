package dev.hallock.zstd;

import java.io.IOException;
import java.io.Serial;

public final class ZstdException extends IOException {
    @Serial
    private static final long serialVersionUID = -6539177472170593415L;

    public ZstdException(ZstdResult.Error result) {
        super("ZSTD error %d|%d: %s".formatted(result.result(), result.code(), result.name()));
    }
}
