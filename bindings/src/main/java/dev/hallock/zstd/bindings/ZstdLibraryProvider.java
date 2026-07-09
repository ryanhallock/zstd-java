package dev.hallock.zstd.bindings;

/**
 * Service provider interface for loading the native {@code zstd} library.
 *
 * <p>Implementations are discovered with {@link java.util.ServiceLoader} through this module's
 * class loader when the bindings are first used. Providers are tried in service-loader order: a
 * provider that throws is skipped in favor of the next provider, and if every provider fails (or
 * none is registered) the bindings fall back to {@link System#loadLibrary(String)
 * System.loadLibrary("zstd")}. A {@link java.util.ServiceConfigurationError} from malformed
 * provider metadata is deliberately not treated as a provider failure: it propagates immediately,
 * without the system fallback, so broken packaging fails loudly. Setting the {@code
 * dev.hallock.zstd.loader} system property to {@code "system"} skips providers entirely and loads
 * via {@code System.loadLibrary("zstd")} directly.
 *
 * <p>Implementations typically call {@link System#load(String)} or {@link
 * System#loadLibrary(String)} from their own module (for example after extracting a bundled library
 * from resources), so that module must have native access enabled (for example via {@code
 * --enable-native-access}).
 */
public interface ZstdLibraryProvider {

  /**
   * Loads the native {@code zstd} library.
   *
   * <p>Implementations must ensure that the native library is successfully loaded into the JVM
   * (typically via {@link System#loadLibrary(String)} or {@link System#load(String)}) so that
   * subsequent native binding calls can resolve its symbols. A thrown exception is treated as
   * "unavailable": loading continues with the next registered provider and finally the system
   * fallback.
   */
  void loadLibrary();
}
