import dev.hallock.zstd.bindings.ZstdLibraryProvider;

/**
 * Generated Java FFM bindings for the native Zstandard (zstd) library, together with the
 * hand-written helpers that load it and classify its error codes.
 *
 * <p>This module exposes generated raw bindings that track the native zstd ABI and carries no
 * independent API-compatibility promise beyond it; use the {@code dev.hallock.zstd} module for the
 * stable Java API.
 *
 * <p>Native library loading can be customized by registering a {@link
 * dev.hallock.zstd.bindings.ZstdLibraryProvider} service, or forced to {@code
 * System.loadLibrary("zstd")} by setting the {@code dev.hallock.zstd.loader} system property to
 * {@code "system"}.
 */
module dev.hallock.zstd.bindings {
  exports dev.hallock.zstd.bindings;

  uses ZstdLibraryProvider;
}
