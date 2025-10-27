package dev.hallock.zstd;

public sealed interface ZstdResult {
    static ZstdResult from(long result) {
        if (Zstd.isError(result)) {
            return new Error(result);
        } else {
            return new Ok(result);
        }
    }

    static void check(long result) throws ZstdException {
        if (Zstd.isError(result)) {
            throw new ZstdException(new Error(result));
        }
    }

    default Ok orElseThrow() throws ZstdException {
        return switch (this) {
            case Ok ok -> ok;
            case Error error -> throw new ZstdException(error);
        };
    }

    record Ok(long result) implements ZstdResult {
    }

    record Error(long result) implements ZstdResult {
        public int code() {
            return Zstd.getErrorCode(result);
        }

        public String name() {
            return Zstd.getErrorName(result);
        }
    }
}
