package dev.hallock.zstd.bindings;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Loads the native {@code zstd} library on behalf of the generated bindings. */
final class ZstdLibrary {

  /**
   * System property selecting the load mechanism. {@code "system"} bypasses providers and calls
   * {@code System.loadLibrary("zstd")} directly; unset or any other value uses the provider flow
   * with the system library as the final fallback.
   */
  static final String LOADER_PROPERTY = "dev.hallock.zstd.loader";

  private ZstdLibrary() {}

  /**
   * Loads the native {@code zstd} library.
   *
   * <p>If the {@code dev.hallock.zstd.loader} system property is {@code "system"}, providers are
   * skipped entirely in favor of {@link System#loadLibrary(String) System.loadLibrary("zstd")}.
   * Otherwise every registered {@link ZstdLibraryProvider} is tried in service-loader order and the
   * first success wins, with {@code System.loadLibrary("zstd")} as the final fallback.
   *
   * @throws UnsatisfiedLinkError if every mechanism fails; the message names each attempted
   *     mechanism and each individual failure is attached as a suppressed exception. In the {@code
   *     "system"} fast path there is nothing to aggregate, so the {@code System.loadLibrary}
   *     failure propagates as-is.
   */
  static void load() {
    // Called once from the ZSTD_h <clinit>, which serializes it; no synchronization needed.
    if ("system".equals(System.getProperty(LOADER_PROPERTY))) {
      System.loadLibrary("zstd");
      return;
    }
    List<String> attempted = new ArrayList<>();
    List<Throwable> failures = new ArrayList<>();
    // Anchored to this class's loader so discovery does not depend on the first-touch thread.
    ServiceLoader<ZstdLibraryProvider> providers =
        ServiceLoader.load(ZstdLibraryProvider.class, ZstdLibrary.class.getClassLoader());
    for (ServiceLoader.Provider<ZstdLibraryProvider> provider : providers.stream().toList()) {
      try {
        attempted.add("provider " + provider.type().getName());
        provider.get().loadLibrary();
        return;
      } catch (Throwable t) {
        failures.add(t);
      }
    }
    try {
      System.loadLibrary("zstd");
    } catch (UnsatisfiedLinkError | RuntimeException e) {
      // RuntimeException too: loadLibrary is restricted and can throw IllegalCallerException
      // under --illegal-native-access=deny.
      attempted.add("System.loadLibrary(\"zstd\")");
      failures.add(e);
      // An Error propagates out of the ZSTD_h <clinit> unwrapped; a RuntimeException would be
      // wrapped in ExceptionInInitializerError.
      UnsatisfiedLinkError error =
          new UnsatisfiedLinkError(
              "Failed to load the native zstd library; attempted: " + String.join(", ", attempted));
      failures.forEach(error::addSuppressed);
      throw error;
    }
  }
}
