package dev.hallock.zstd.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.hallock.zstd.Zstd;
import dev.hallock.zstd.bindings.ZstdLibraryProvider;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class ZstdLibraryProviderTest {
  private static final String BUNDLED_PROVIDER =
      "dev.hallock.zstd.platforms.impl.BundledZstdLibraryProvider";

  @Test
  void bundledProviderCanLoadTheNativeLibrary() {
    boolean bundledNatives = Boolean.getBoolean("dev.hallock.zstd.test.bundledNatives");
    assumeTrue(bundledNatives, "bundled native testing is not enabled");

    Optional<ServiceLoader.Provider<ZstdLibraryProvider>> bundledProvider =
        ServiceLoader.load(ZstdLibraryProvider.class, ZstdLibraryProvider.class.getClassLoader())
            .stream()
            .filter(provider -> provider.type().getName().equals(BUNDLED_PROVIDER))
            .findFirst();

    assertTrue(bundledProvider.isPresent(), "bundled native provider is not registered");

    ServiceLoader.Provider<ZstdLibraryProvider> provider = bundledProvider.orElseThrow();
    assertDoesNotThrow(
        () -> provider.get().loadLibrary(),
        () -> "Unable to load zstd through " + BUNDLED_PROVIDER);
    assertTrue(Zstd.zstd().versionNumber() >= 10506);
  }
}
