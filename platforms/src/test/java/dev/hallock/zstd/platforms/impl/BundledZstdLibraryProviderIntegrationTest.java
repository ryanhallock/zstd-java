package dev.hallock.zstd.platforms.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.hallock.zstd.Zstd;
import org.junit.jupiter.api.Test;

class BundledZstdLibraryProviderIntegrationTest {
  @Test
  void loadsBundledLibrary() throws Exception {
    BundledZstdLibraryProvider.Platform platform =
        BundledZstdLibraryProvider.Platform.detect(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            BundledZstdLibraryProvider.Platform.muslLibc());
    assumeTrue(
        BundledZstdLibraryProvider.class.getResource("natives/" + platform.resourcePath()) != null,
        "No bundled native was staged for this development build");

    assertTrue(Zstd.zstd().versionNumber() > 0);
  }
}
