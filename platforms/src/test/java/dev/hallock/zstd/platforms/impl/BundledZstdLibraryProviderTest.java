package dev.hallock.zstd.platforms.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledZstdLibraryProviderTest {
  private static final byte[] RESOURCE_CONTENT =
      "platform resource\n".getBytes(StandardCharsets.UTF_8);

  @Test
  void detectsSupportedPlatforms() {
    assertEquals(
        "linux/x86_64/libzstd.so",
        BundledZstdLibraryProvider.Platform.detect("Linux", "amd64", false).resourcePath());
    assertEquals(
        "linux/aarch64/libzstd.so",
        BundledZstdLibraryProvider.Platform.detect("linux", "arm64", false).resourcePath());
    assertEquals(
        "macos/x86_64/libzstd.dylib",
        BundledZstdLibraryProvider.Platform.detect("Mac OS X", "x86_64", false).resourcePath());
    assertEquals(
        "macos/aarch64/libzstd.dylib",
        BundledZstdLibraryProvider.Platform.detect("Darwin", "aarch64", false).resourcePath());
    assertEquals(
        "windows/x86_64/zstd.dll",
        BundledZstdLibraryProvider.Platform.detect("Windows 11", "amd64", false).resourcePath());
    assertEquals(
        "windows/aarch64/zstd.dll",
        BundledZstdLibraryProvider.Platform.detect("Windows 11", "arm64", false).resourcePath());
  }

  @Test
  void detectsMuslLinux() {
    assertEquals(
        "linux-musl/x86_64/libzstd.so",
        BundledZstdLibraryProvider.Platform.detect("Linux", "amd64", true).resourcePath());
    assertEquals(
        "linux-musl/aarch64/libzstd.so",
        BundledZstdLibraryProvider.Platform.detect("Linux", "aarch64", true).resourcePath());
  }

  @Test
  void muslFlagOnlyAffectsLinux() {
    assertEquals(
        "macos/aarch64/libzstd.dylib",
        BundledZstdLibraryProvider.Platform.detect("Darwin", "aarch64", true).resourcePath());
    assertEquals(
        "windows/x86_64/zstd.dll",
        BundledZstdLibraryProvider.Platform.detect("Windows 11", "amd64", true).resourcePath());
  }

  @Test
  void rejectsUnsupportedPlatforms() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> BundledZstdLibraryProvider.Platform.detect("FreeBSD", "amd64", false));
    assertThrows(
        UnsupportedOperationException.class,
        () -> BundledZstdLibraryProvider.Platform.detect("Linux", "riscv64", false));
  }

  @Test
  void resolvesCacheRootFromXdgCacheHome(@TempDir Path absoluteCache) {
    // @TempDir yields a path that is absolute on every OS (a bare "/x" is not absolute on
    // Windows).
    assertEquals(
        absoluteCache,
        BundledZstdLibraryProvider.cacheRoot(absoluteCache.toString(), "/home/user"));
    assertEquals(
        Path.of("/home/user", ".cache"), BundledZstdLibraryProvider.cacheRoot(null, "/home/user"));
    assertEquals(
        Path.of("/home/user", ".cache"), BundledZstdLibraryProvider.cacheRoot("", "/home/user"));
  }

  @Test
  void ignoresRelativeXdgCacheHome() {
    // The XDG base directory spec requires relative values to be treated as unset.
    assertEquals(
        Path.of("/home/user", ".cache"),
        BundledZstdLibraryProvider.cacheRoot("relative/cache", "/home/user"));
  }

  @Test
  void rejectsUnusableUserHome() {
    assertThrows(
        InvalidPathException.class, () -> BundledZstdLibraryProvider.cacheRoot(null, null));
    assertThrows(InvalidPathException.class, () -> BundledZstdLibraryProvider.cacheRoot(null, " "));
  }

  @Test
  void extractsToTempOnlyPackageRelativeResourcesAndChecksIntegrity() throws Exception {
    Path extracted =
        BundledZstdLibraryProvider.extractToTemp("test-native.bin", "test.so", checksum());
    assertEquals("platform resource\n", Files.readString(extracted));
    assertThrows(
        IOException.class,
        () ->
            BundledZstdLibraryProvider.extractToTemp("test-native.bin", "test.so", "0".repeat(64)));
    assertThrows(
        UnsupportedOperationException.class,
        () -> BundledZstdLibraryProvider.extractToTemp("missing.bin", "test.so", checksum()));
  }

  @Test
  void extractsIntoCacheDirectory(@TempDir Path cacheDirectory) throws Exception {
    Path cached =
        BundledZstdLibraryProvider.extractToCache(
            "test-native.bin", "test.so", checksum(), cacheDirectory);
    assertEquals(cacheDirectory.resolve("test.so"), cached);
    assertEquals("platform resource\n", Files.readString(cached));
    assertEquals(List.of(cached), entriesOf(cacheDirectory));
  }

  @Test
  void reusesValidCachedFileWithoutReExtracting(@TempDir Path cacheDirectory) throws Exception {
    Path cached = cacheDirectory.resolve("test.so");
    Files.write(cached, RESOURCE_CONTENT);

    // "missing.bin" does not exist, so any attempt to re-extract would throw
    // UnsupportedOperationException; returning the cached path proves reuse.
    assertEquals(
        cached,
        BundledZstdLibraryProvider.extractToCache(
            "missing.bin", "test.so", checksum(), cacheDirectory));
  }

  @Test
  void replacesCorruptCachedFile(@TempDir Path cacheDirectory) throws Exception {
    Path cached = cacheDirectory.resolve("test.so");
    Files.writeString(cached, "corrupted");

    assertEquals(
        cached,
        BundledZstdLibraryProvider.extractToCache(
            "test-native.bin", "test.so", checksum(), cacheDirectory));
    assertEquals("platform resource\n", Files.readString(cached));
  }

  @Test
  void rejectsCacheExtractionOnChecksumMismatch(@TempDir Path cacheDirectory) {
    assertThrows(
        IOException.class,
        () ->
            BundledZstdLibraryProvider.extractToCache(
                "test-native.bin", "test.so", "0".repeat(64), cacheDirectory));
    assertEquals(List.of(), entriesOf(cacheDirectory));
  }

  @Test
  void provisionsFromCacheDirectory(@TempDir Path cacheDirectory) throws Exception {
    Path provisioned =
        BundledZstdLibraryProvider.provision(
            "test-native.bin", "test.so", checksum(), cacheDirectory);
    assertEquals(cacheDirectory.resolve("test.so"), provisioned);
    assertEquals("platform resource\n", Files.readString(provisioned));
  }

  @Test
  void provisionFallsBackToTempWhenCacheExtractionFails(@TempDir Path cacheDirectory)
      throws Exception {
    // A populated directory squatting on the target path keeps the cache directory writable but
    // makes the publishing move inside extractToCache fail with an IOException.
    Path occupied = cacheDirectory.resolve("test.so");
    Files.createDirectory(occupied);
    Files.writeString(occupied.resolve("occupant"), "x");

    Path provisioned =
        BundledZstdLibraryProvider.provision(
            "test-native.bin", "test.so", checksum(), cacheDirectory);
    assertNotEquals(occupied, provisioned);
    assertEquals("platform resource\n", Files.readString(provisioned));
  }

  @Test
  void provisionFallsBackToTempWithoutCacheDirectory() throws Exception {
    Path provisioned =
        BundledZstdLibraryProvider.provision("test-native.bin", "test.so", checksum(), null);
    assertEquals("platform resource\n", Files.readString(provisioned));
  }

  @Test
  void rejectsCacheExtractionOfMissingResource(@TempDir Path cacheDirectory) {
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            BundledZstdLibraryProvider.extractToCache(
                "missing.bin", "test.so", checksum(), cacheDirectory));
    assertEquals(List.of(), entriesOf(cacheDirectory));
  }

  private static String checksum() throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(RESOURCE_CONTENT));
  }

  private static List<Path> entriesOf(Path directory) {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.toList();
    } catch (IOException exception) {
      throw new AssertionError(exception);
    }
  }
}
