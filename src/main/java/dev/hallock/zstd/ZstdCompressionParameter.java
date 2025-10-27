package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_h;

import java.lang.foreign.SegmentAllocator;

public enum ZstdCompressionParameter {
    COMPRESSION_LEVEL(ZSTD_h.ZSTD_c_compressionLevel()),
    WINDOW_LOG(ZSTD_h.ZSTD_c_windowLog()),
    HASH_LOG(ZSTD_h.ZSTD_c_hashLog()),
    CHAIN_LOG(ZSTD_h.ZSTD_c_chainLog()),
    SEARCH_LOG(ZSTD_h.ZSTD_c_searchLog()),
    MIN_MATCH(ZSTD_h.ZSTD_c_minMatch()),
    TARGET_LENGTH(ZSTD_h.ZSTD_c_targetLength()),
    STRATEGY(ZSTD_h.ZSTD_c_strategy()),
    TARGET_BLOCK_SIZE(ZSTD_h.ZSTD_c_targetCBlockSize()),
    ENABLE_LONG_DISTANCE_MATCH(ZSTD_h.ZSTD_c_enableLongDistanceMatching()),
    LDM_HASH_LOG(ZSTD_h.ZSTD_c_ldmHashLog()),
    LDM_MIN_MATCH(ZSTD_h.ZSTD_c_ldmMinMatch()),
    LDM_BUCKET_SIZE_LOG(ZSTD_h.ZSTD_c_ldmBucketSizeLog()),
    LDM_HASH_RATE_LOG(ZSTD_h.ZSTD_c_ldmHashRateLog()),
    CONTENT_SIZE_FLAG(ZSTD_h.ZSTD_c_contentSizeFlag()),
    CHECKSUM_FLAG(ZSTD_h.ZSTD_c_checksumFlag()),
    DICTIONARY_ID_FLAG(ZSTD_h.ZSTD_c_dictIDFlag()),
    NB_WORKERS(ZSTD_h.ZSTD_c_nbWorkers()),
    JOB_SIZE(ZSTD_h.ZSTD_c_jobSize()),
    OVERLAP_LOG(ZSTD_h.ZSTD_c_overlapLog())
    //TODO experimental?
    ;

    private final int value;

    ZstdCompressionParameter(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }

    public ZstdParameterBounds bounds(SegmentAllocator allocator) {
        return new ZstdParameterBounds(ZSTD_h.ZSTD_cParam_getBounds(allocator, this.value));
    }
}
