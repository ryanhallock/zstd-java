import dev.hallock.zstd.bindings.ZstdLibraryProvider;
import dev.hallock.zstd.platforms.impl.BundledZstdLibraryProvider;

/**
 * Bundled native Zstandard libraries for zstd-java.
 *
 * <p>This module packages prebuilt {@code zstd} shared libraries (with SHA-256 checksums) for every
 * supported operating system and architecture, and provides a {@link ZstdLibraryProvider}
 * implementation that extracts and loads the library matching the running platform. Simply adding
 * this module to the module path (or its jar to the class path) activates bundled loading: the
 * bindings module discovers the provider via {@link java.util.ServiceLoader} and prefers it over
 * the {@code System.loadLibrary("zstd")} fallback.
 *
 * <p>Loading the extracted library calls the restricted {@code System.load} method, so this module
 * requires native access: run with {@code
 * --enable-native-access=dev.hallock.zstd.platforms,dev.hallock.zstd.bindings} on the module path,
 * or {@code --enable-native-access=ALL-UNNAMED} on the class path.
 */
module dev.hallock.zstd.platforms {
  requires dev.hallock.zstd.bindings;

  provides ZstdLibraryProvider with
      BundledZstdLibraryProvider;
}
