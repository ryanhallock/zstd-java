package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.ZstdCompressionParameter;
import dev.hallock.zstd.ZstdDecompressionParameter;
import dev.hallock.zstd.ZstdEndDirective;
import dev.hallock.zstd.ZstdResetDirective;
import dev.hallock.zstd.ZstdStrategy;
import dev.hallock.zstd.bindings.ZSTD_h;
import org.junit.jupiter.api.Test;

/**
 * The public enums hardcode their native values (so class-loading them does not force the native
 * library to load). These tests pin every hardcoded value against the jextract-generated constants
 * from zstd.h, so any drift surfaces the next time the bindings are regenerated. The
 * values().length assertions force a new enum constant to gain drift coverage here.
 */
class ZstdAbiDriftTest {

  @Test
  void loadedNativeVersionMatchesCiExpectation() {
    String expected = System.getProperty("dev.hallock.zstd.test.expectedVersion");
    if (expected != null) {
      assertEquals(Integer.parseInt(expected), Zstd.zstd().versionNumber());
    }
  }

  @Test
  void compressionParameterValuesMatchHeader() {
    assertEquals(
        ZSTD_h.ZSTD_c_compressionLevel(), ZstdCompressionParameter.COMPRESSION_LEVEL.value());
    assertEquals(ZSTD_h.ZSTD_c_windowLog(), ZstdCompressionParameter.WINDOW_LOG.value());
    assertEquals(ZSTD_h.ZSTD_c_hashLog(), ZstdCompressionParameter.HASH_LOG.value());
    assertEquals(ZSTD_h.ZSTD_c_chainLog(), ZstdCompressionParameter.CHAIN_LOG.value());
    assertEquals(ZSTD_h.ZSTD_c_searchLog(), ZstdCompressionParameter.SEARCH_LOG.value());
    assertEquals(ZSTD_h.ZSTD_c_minMatch(), ZstdCompressionParameter.MIN_MATCH.value());
    assertEquals(ZSTD_h.ZSTD_c_targetLength(), ZstdCompressionParameter.TARGET_LENGTH.value());
    assertEquals(ZSTD_h.ZSTD_c_strategy(), ZstdCompressionParameter.STRATEGY.value());
    assertEquals(
        ZSTD_h.ZSTD_c_targetCBlockSize(), ZstdCompressionParameter.TARGET_BLOCK_SIZE.value());
    assertEquals(
        ZSTD_h.ZSTD_c_enableLongDistanceMatching(),
        ZstdCompressionParameter.ENABLE_LONG_DISTANCE_MATCH.value());
    assertEquals(ZSTD_h.ZSTD_c_ldmHashLog(), ZstdCompressionParameter.LDM_HASH_LOG.value());
    assertEquals(ZSTD_h.ZSTD_c_ldmMinMatch(), ZstdCompressionParameter.LDM_MIN_MATCH.value());
    assertEquals(
        ZSTD_h.ZSTD_c_ldmBucketSizeLog(), ZstdCompressionParameter.LDM_BUCKET_SIZE_LOG.value());
    assertEquals(
        ZSTD_h.ZSTD_c_ldmHashRateLog(), ZstdCompressionParameter.LDM_HASH_RATE_LOG.value());
    assertEquals(
        ZSTD_h.ZSTD_c_contentSizeFlag(), ZstdCompressionParameter.CONTENT_SIZE_FLAG.value());
    assertEquals(ZSTD_h.ZSTD_c_checksumFlag(), ZstdCompressionParameter.CHECKSUM_FLAG.value());
    assertEquals(ZSTD_h.ZSTD_c_dictIDFlag(), ZstdCompressionParameter.DICTIONARY_ID_FLAG.value());
    assertEquals(ZSTD_h.ZSTD_c_nbWorkers(), ZstdCompressionParameter.NB_WORKERS.value());
    assertEquals(ZSTD_h.ZSTD_c_jobSize(), ZstdCompressionParameter.JOB_SIZE.value());
    assertEquals(ZSTD_h.ZSTD_c_overlapLog(), ZstdCompressionParameter.OVERLAP_LOG.value());

    assertEquals(
        20,
        ZstdCompressionParameter.values().length,
        "new ZstdCompressionParameter constant lacks drift coverage");
  }

  @Test
  void decompressionParameterValuesMatchHeader() {
    assertEquals(ZSTD_h.ZSTD_d_windowLogMax(), ZstdDecompressionParameter.WINDOW_LOG_MAX.value());

    assertEquals(
        1,
        ZstdDecompressionParameter.values().length,
        "new ZstdDecompressionParameter constant lacks drift coverage");
  }

  @Test
  void strategyValuesMatchHeader() {
    assertEquals(ZSTD_h.ZSTD_fast(), ZstdStrategy.FAST.value());
    assertEquals(ZSTD_h.ZSTD_dfast(), ZstdStrategy.DFAST.value());
    assertEquals(ZSTD_h.ZSTD_greedy(), ZstdStrategy.GREEDY.value());
    assertEquals(ZSTD_h.ZSTD_lazy(), ZstdStrategy.LAZY.value());
    assertEquals(ZSTD_h.ZSTD_lazy2(), ZstdStrategy.LAZY2.value());
    assertEquals(ZSTD_h.ZSTD_btlazy2(), ZstdStrategy.BTLAZY2.value());
    assertEquals(ZSTD_h.ZSTD_btopt(), ZstdStrategy.BTOPT.value());
    assertEquals(ZSTD_h.ZSTD_btultra(), ZstdStrategy.BTULTRA.value());
    assertEquals(ZSTD_h.ZSTD_btultra2(), ZstdStrategy.BTULTRA2.value());

    assertEquals(9, ZstdStrategy.values().length, "new ZstdStrategy constant lacks drift coverage");
  }

  @Test
  void endDirectiveValuesMatchHeader() {
    assertEquals(ZSTD_h.ZSTD_e_continue(), ZstdEndDirective.CONTINUE.value());
    assertEquals(ZSTD_h.ZSTD_e_flush(), ZstdEndDirective.FLUSH.value());
    assertEquals(ZSTD_h.ZSTD_e_end(), ZstdEndDirective.END.value());

    assertEquals(
        3, ZstdEndDirective.values().length, "new ZstdEndDirective constant lacks drift coverage");
  }

  @Test
  void resetDirectiveValuesMatchHeader() {
    assertEquals(ZSTD_h.ZSTD_reset_session_only(), ZstdResetDirective.SESSION_ONLY.value());
    assertEquals(ZSTD_h.ZSTD_reset_parameters(), ZstdResetDirective.PARAMETERS.value());
    assertEquals(
        ZSTD_h.ZSTD_reset_session_and_parameters(),
        ZstdResetDirective.SESSION_AND_PARAMETERS.value());

    assertEquals(
        3,
        ZstdResetDirective.values().length,
        "new ZstdResetDirective constant lacks drift coverage");
  }

  @Test
  void contentSizeSentinelMatchesHeader() {
    assertEquals(ZSTD_h.ZSTD_CONTENTSIZE_UNKNOWN(), Zstd.CONTENT_SIZE_UNKNOWN);
  }
}
