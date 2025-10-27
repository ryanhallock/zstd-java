package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

public final class Zstd {
    public static ZstdResult decompress(MemorySegment dst, long dstCapacity, MemorySegment src, long compressedSize) {
        Objects.requireNonNull(dst, "dst");
        if (dstCapacity > dst.byteSize())
            throw new IllegalArgumentException("dstCapacity cannot be larger than dst.byteSize()");
        Objects.requireNonNull(src, "src");
        if (compressedSize > src.byteSize())
            throw new IllegalArgumentException("compressedSize cannot be larger than src.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_decompress(dst, dstCapacity, src, compressedSize));
    }

    public static ZstdResult compress(MemorySegment dst, long dstCapacity, MemorySegment src, long srcSize, int compressionLevel) {
        Objects.requireNonNull(dst, "dst");
        if (dstCapacity > dst.byteSize())
            throw new IllegalArgumentException("dstCapacity cannot be larger than dst.byteSize()");
        Objects.requireNonNull(src, "src");
        if (srcSize > src.byteSize())
            throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
        return ZstdResult.from(ZSTD_h.ZSTD_compress(dst, dstCapacity, src, srcSize, compressionLevel));
    }

    public static long compressBound(long srcSize) {
        return ZSTD_h.ZSTD_compressBound(srcSize);
    }

    public static long decompressBound(MemorySegment src, long srcSize) {
        Objects.requireNonNull(src, "src");
        if (srcSize > src.byteSize())
            throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
        return ZSTD_h.ZSTD_getFrameContentSize(src, srcSize);
    }

    public static boolean isError(long result) {
        return ZSTD_h.ZSTD_isError(result) != 0;
    }

    public static int getErrorCode(long result) {
        return ZSTD_h.ZSTD_getErrorCode(result);
    }

    public static String getErrorName(long result) {
        return ZSTD_h.ZSTD_getErrorName(result).getString(0);
    }

    public static int defaultCompressionLevel() {
        return ZSTD_h.ZSTD_defaultCLevel();
    }

    public static int minCompressionLevel() {
        return ZSTD_h.ZSTD_minCLevel();
    }

    public static int maxCompressionLevel() {
        return ZSTD_h.ZSTD_maxCLevel();
    }

    public static long findFrameCompressedSize(MemorySegment src, long srcSize) {
        Objects.requireNonNull(src, "src");
        if (srcSize > src.byteSize()) throw new IllegalArgumentException("srcSize cannot be larger than src.byteSize()");
        return ZSTD_h.ZSTD_findFrameCompressedSize(src, srcSize);
    }

    public static int dictIdFromDict(MemorySegment dict, long dictSize) {
        Objects.requireNonNull(dict, "dict");
        if (dictSize > dict.byteSize()) throw new IllegalArgumentException("dictSize cannot be larger than dict.byteSize()");
        return ZSTD_h.ZSTD_getDictID_fromDict(dict, dictSize);
    }

    public static int dictIdFromFrame(MemorySegment frame, long srcSize) {
        Objects.requireNonNull(frame, "frame");
        if (srcSize > frame.byteSize()) throw new IllegalArgumentException("srcSize cannot be larger than frame.byteSize()");
        return ZSTD_h.ZSTD_getDictID_fromFrame(frame, srcSize);
    }

    public static int versionNumber() {
        return ZSTD_h.ZSTD_versionNumber();
    }

    public static String versionString() {
        return ZSTD_h.ZSTD_versionString().getString(0);
    }

    public static int magicNumber() {
        return ZSTD_h.ZSTD_MAGICNUMBER();
    }

    private Zstd() {}
}
