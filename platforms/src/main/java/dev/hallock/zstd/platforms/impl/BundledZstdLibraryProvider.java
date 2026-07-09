package dev.hallock.zstd.platforms.impl;

import dev.hallock.zstd.bindings.ZstdLibraryProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the bundled native zstd library, extracting it into a per-user content-addressed cache
 * ({@code $XDG_CACHE_HOME|~/.cache}{@code /zstd-java/<sha256>/<library>}) so repeated runs reuse
 * the same file instead of leaking one temp copy per process (Windows cannot delete a loaded DLL on
 * exit). Falls back to a delete-on-exit temp file when the cache root is unusable or extraction
 * into it fails.
 */
public final class BundledZstdLibraryProvider implements ZstdLibraryProvider {
  private static final Set<PosixFilePermission> OWNER_ONLY =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  private static boolean loaded;

  /** Creates the provider; invoked by {@link java.util.ServiceLoader}. */
  public BundledZstdLibraryProvider() {}

  @Override
  @SuppressWarnings("restricted")
  public void loadLibrary() {
    synchronized (BundledZstdLibraryProvider.class) {
      if (loaded) return;

      Platform platform =
          Platform.detect(
              System.getProperty("os.name"), System.getProperty("os.arch"), Platform.muslLibc());
      String resource = "natives/" + platform.resourcePath();
      try {
        String checksum = readChecksum(resource + ".sha256");
        Path library = provision(resource, platform.libraryFileName(), checksum);
        System.load(library.toString());
        loaded = true;
      } catch (IOException exception) {
        throw new UncheckedIOException("Unable to load bundled zstd library", exception);
      }
    }
  }

  private static Path provision(String resource, String fileName, String expectedChecksum)
      throws IOException {
    return provision(resource, fileName, expectedChecksum, prepareCacheDirectory(expectedChecksum));
  }

  /**
   * Returns the provisioned library, preferring the cache and falling back to a delete-on-exit temp
   * file when {@code cacheDirectory} is {@code null} (unusable cache root), is not writable, or
   * cache extraction fails.
   */
  static Path provision(
      String resource, String fileName, String expectedChecksum, Path cacheDirectory)
      throws IOException {
    if (cacheDirectory != null) {
      // A read-only cache directory (e.g. pre-warmed in a container image) can still serve hits.
      Path cached = cacheDirectory.resolve(fileName);
      if (isValidCached(cached, expectedChecksum)) return cached;
      if (Files.isWritable(cacheDirectory)) {
        try {
          return extractToCache(resource, fileName, expectedChecksum, cacheDirectory);
        } catch (IOException cacheFailure) {
          // A cache broken mid-extraction must not sink bundled loading; a temp copy still works.
          try {
            return extractToTemp(resource, fileName, expectedChecksum);
          } catch (IOException tempFailure) {
            tempFailure.addSuppressed(cacheFailure);
            throw tempFailure;
          }
        }
      }
    }
    return extractToTemp(resource, fileName, expectedChecksum);
  }

  /**
   * Returns the checksum-keyed cache directory, creating it if needed, or {@code null} when the
   * cache root cannot be used (falling back to temp extraction).
   */
  private static Path prepareCacheDirectory(String expectedChecksum) {
    try {
      Path directory =
          cacheRoot(System.getenv("XDG_CACHE_HOME"), System.getProperty("user.home"))
              .resolve("zstd-java")
              .resolve(expectedChecksum);
      createOwnerOnlyDirectories(directory);
      return directory;
    } catch (IOException | InvalidPathException unusableCacheRoot) {
      return null;
    }
  }

  static Path cacheRoot(String xdgCacheHome, String userHome) {
    if (xdgCacheHome != null && !xdgCacheHome.isBlank()) {
      Path xdg = Path.of(xdgCacheHome);
      // The XDG base directory spec requires ignoring relative values as invalid; a relative
      // cache root would also make System.load fail, which expects an absolute path.
      if (xdg.isAbsolute()) return xdg;
    }
    if (userHome == null || userHome.isBlank()) {
      // Surfaces like any other unusable cache root: prepareCacheDirectory catches this and
      // falls back to temp extraction.
      throw new InvalidPathException(String.valueOf(userHome), "user.home is not usable");
    }
    return Path.of(userHome, ".cache");
  }

  /**
   * Returns the verified cached library, extracting it first when absent or corrupt. Extraction
   * streams to a unique temp file in the cache directory and publishes it with an atomic move where
   * the file system supports one (falling back to a plain replace otherwise), so concurrent
   * processes practically never observe a partially written or unverified library.
   */
  static Path extractToCache(
      String resource, String fileName, String expectedChecksum, Path cacheDirectory)
      throws IOException {
    Path cached = cacheDirectory.resolve(fileName);
    if (isValidCached(cached, expectedChecksum)) return cached;

    Path temp = Files.createTempFile(cacheDirectory, fileName, ".tmp");
    boolean published = false;
    try {
      copyVerified(resource, temp, expectedChecksum);
      makeExecutable(temp);
      try {
        Files.move(temp, cached, StandardCopyOption.ATOMIC_MOVE);
        published = true;
      } catch (AtomicMoveNotSupportedException atomicUnsupported) {
        try {
          Files.move(temp, cached, StandardCopyOption.REPLACE_EXISTING);
          published = true;
        } catch (IOException moveFailure) {
          return cachedIfRaceWinnerIsValid(cached, expectedChecksum, moveFailure);
        }
      } catch (IOException moveFailure) {
        return cachedIfRaceWinnerIsValid(cached, expectedChecksum, moveFailure);
      }
      return cached;
    } finally {
      if (!published) Files.deleteIfExists(temp);
    }
  }

  /**
   * A failed move usually means a concurrent process published the library first (Windows refuses
   * to replace a loaded DLL); accept the winner's file if its checksum verifies.
   */
  private static Path cachedIfRaceWinnerIsValid(
      Path cached, String expectedChecksum, IOException moveFailure) throws IOException {
    if (isValidCached(cached, expectedChecksum)) return cached;
    throw moveFailure;
  }

  private static boolean isValidCached(Path cached, String expectedChecksum) {
    try {
      return Files.isRegularFile(cached) && checksumMatches(expectedChecksum, checksumOf(cached));
    } catch (IOException unreadable) {
      return false;
    }
  }

  private static String checksumOf(Path file) throws IOException {
    MessageDigest digest = sha256();
    try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
      input.transferTo(OutputStream.nullOutputStream());
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  /** Fallback used when the cache root is unusable; leaks one file per run on Windows. */
  static Path extractToTemp(String resource, String fileName, String expectedChecksum)
      throws IOException {
    int extensionIndex = fileName.indexOf('.');
    String suffix = extensionIndex < 0 ? null : fileName.substring(extensionIndex);
    Path extracted = Files.createTempFile("zstd-java-", suffix).toAbsolutePath();
    boolean complete = false;
    try {
      copyVerified(resource, extracted, expectedChecksum);
      makeExecutable(extracted);
      extracted.toFile().deleteOnExit();
      complete = true;
      return extracted;
    } finally {
      if (!complete) Files.deleteIfExists(extracted);
    }
  }

  private static void copyVerified(String resource, Path target, String expectedChecksum)
      throws IOException {
    try (InputStream resourceStream =
        BundledZstdLibraryProvider.class.getResourceAsStream(resource)) {
      if (resourceStream == null) {
        throw new UnsupportedOperationException("Bundled zstd library is missing: " + resource);
      }
      MessageDigest digest = sha256();
      try (DigestInputStream input = new DigestInputStream(resourceStream, digest)) {
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      }
      String actualChecksum = HexFormat.of().formatHex(digest.digest());
      if (!checksumMatches(expectedChecksum, actualChecksum)) {
        throw new IOException("Bundled zstd library checksum does not match: " + resource);
      }
    }
  }

  private static boolean checksumMatches(String expected, String actual) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
  }

  private static String readChecksum(String resource) throws IOException {
    try (InputStream input = BundledZstdLibraryProvider.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new UnsupportedOperationException("Bundled zstd checksum is missing: " + resource);
      }
      return new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static void createOwnerOnlyDirectories(Path directory) throws IOException {
    FileAttribute<Set<PosixFilePermission>> ownerOnly =
        PosixFilePermissions.asFileAttribute(OWNER_ONLY);
    try {
      Files.createDirectories(directory, ownerOnly);
    } catch (UnsupportedOperationException nonPosixFileSystem) {
      // Windows does not expose POSIX permissions.
      Files.createDirectories(directory);
    }
  }

  private static void makeExecutable(Path library) throws IOException {
    try {
      Files.setPosixFilePermissions(library, OWNER_ONLY);
    } catch (UnsupportedOperationException ignored) {
      // Windows does not expose POSIX permissions.
    }
  }

  record Platform(String operatingSystem, String architecture, String libraryFileName) {
    static Platform detect(String osName, String osArch, boolean muslLibc) {
      String operatingSystem = normalizeOperatingSystem(osName, muslLibc);
      String architecture = normalizeArchitecture(osArch);
      String libraryFileName =
          switch (operatingSystem) {
            case "linux", "linux-musl" -> "libzstd.so";
            case "macos" -> "libzstd.dylib";
            case "windows" -> "zstd.dll";
            default -> throw new AssertionError(operatingSystem);
          };
      return new Platform(operatingSystem, architecture, libraryFileName);
    }

    String resourcePath() {
      return operatingSystem + "/" + architecture + "/" + libraryFileName;
    }

    /** Detects musl by the presence of its dynamic loader, {@code /lib/ld-musl-<arch>.so.1}. */
    static boolean muslLibc() {
      try (var loaders = Files.newDirectoryStream(Path.of("/lib"), "ld-musl-*.so.1")) {
        return loaders.iterator().hasNext();
      } catch (IOException | DirectoryIteratorException _) {
        return false;
      }
    }

    private static String normalizeOperatingSystem(String value, boolean muslLibc) {
      String normalized = value.toLowerCase(Locale.ROOT);
      if (normalized.startsWith("linux")) return muslLibc ? "linux-musl" : "linux";
      if (normalized.startsWith("mac") || normalized.startsWith("darwin")) return "macos";
      if (normalized.startsWith("windows")) return "windows";
      throw new UnsupportedOperationException("Unsupported operating system: " + value);
    }

    private static String normalizeArchitecture(String value) {
      return switch (value.toLowerCase(Locale.ROOT)) {
        case "amd64", "x86_64" -> "x86_64";
        case "aarch64", "arm64" -> "aarch64";
        default -> throw new UnsupportedOperationException("Unsupported architecture: " + value);
      };
    }
  }
}
