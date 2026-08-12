# zstd-java

Java bindings for the [Zstandard](https://facebook.github.io/zstd/) (zstd) compression library,
built on the Foreign Function & Memory API.

## Requirements

- Java 25 or later.
- A zstd native library, one of:
  - the bundled zstd v1.5.7 shipped in the optional `platforms` artifact, or
  - a system installed `libzstd`, version 1.5.6 or later (the minimum is enforced at load).

## Installation

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("dev.hallock.zstd:zstd-java:<version>")
    // Optional: bundled native libraries (see Supported platforms below).
    runtimeOnly("dev.hallock.zstd:platforms:<version>")
}
```

Maven:

```xml
<dependency>
  <groupId>dev.hallock.zstd</groupId>
  <artifactId>zstd-java</artifactId>
  <version><!-- version --></version>
</dependency>
<!-- Optional: bundled native libraries (see Supported platforms below). -->
<dependency>
  <groupId>dev.hallock.zstd</groupId>
  <artifactId>platforms</artifactId>
  <version><!-- version --></version>
  <scope>runtime</scope>
</dependency>
```

Without `platforms`, a system installed `libzstd` is used instead (see Library resolution below).

## Quick start

Compress through native copy with `byte[]` convenience methods:

```java
import dev.hallock.zstd.Zstd;

Zstd zstd = Zstd.zstd();
byte[] compressed = zstd.compress(data); // or compress(data, level)
byte[] restored = zstd.decompress(compressed);
```

Streaming through `java.io`:

```java
Zstd zstd = Zstd.zstd();
try (var out = zstd.createCompressorOutputStream(Files.newOutputStream(compressedFile))) {
  out.write(data);
} // close() finishes the frame and closes the underlying stream

try (var in = zstd.createDecompressorInputStream(Files.newInputStream(compressedFile))) {
  byte[] restored = in.readAllBytes();
}
```

`ZstdCompressorOutputStream.finish()` completes the frame without closing the underlying stream,
so a zstd frame can be embedded in the middle of a larger stream.

Usage over `MemorySegment`:

```java
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

Zstd zstd = Zstd.zstd();
try (Arena arena = Arena.ofConfined()) {
  MemorySegment src = arena.allocate(data.length);
  MemorySegment.copy(data, 0, src, ValueLayout.JAVA_BYTE, 0, data.length);

  long bound = zstd.compressBound(data.length);
  MemorySegment dst = arena.allocate(bound);
  long compressedSize = zstd.compress(dst, bound, src, data.length, 3);

  MemorySegment roundTrip = arena.allocate(data.length);
  long restoredSize = zstd.decompress(roundTrip, data.length, dst, compressedSize);
}
```

Reusable contexts, digested dictionaries, and the raw streaming buffer API are also available from
the `Zstd` entry point; see the javadoc.

## Runtime

### Native access

On the module path, grant native access to the bindings module:

```
--enable-native-access=dev.hallock.zstd.bindings
```

When `platforms` is on the module path, grant it to both modules:

```
--enable-native-access=dev.hallock.zstd.platforms,dev.hallock.zstd.bindings
```

Running from the class path (unnamed module) is supported with
`--enable-native-access=ALL-UNNAMED`, but the module path is recommended for the narrower grant.

### Library resolution

The native library is loaded once, on first use, in this order:

1. Every registered `dev.hallock.zstd.bindings.ZstdLibraryProvider` service, in service loader
   order. Adding `platforms` registers the bundled library provider; you can also register your own.
2. `System.loadLibrary("zstd")` as the final fallback (a system installed `libzstd`).

Setting the system property `dev.hallock.zstd.loader=system` skips providers entirely and uses
only `System.loadLibrary("zstd")`.

If loading fails, every call to `Zstd.zstd()` throws `IllegalStateException` carrying the original
failure as its cause.

## Supported platforms

Bundled natives in `platforms` (zstd v1.5.7):

| OS            | x86_64 | aarch64 |
|---------------|--------|---------|
| Linux (glibc) | yes    | yes     |
| Linux (musl)  | yes    | yes     |
| macOS         | yes    | yes     |
| Windows       | yes    | yes     |

The matching Linux native is chosen at runtime by probing for the musl dynamic loader
(`/lib/ld-musl-*.so.1`), so glibc distributions and musl distributions such as Alpine both work
without configuration, though it should always work through an system installed `libzstd`.

The bundled library is extracted into a per user, cache at
`$XDG_CACHE_HOME/zstd-java/<sha256>/` (default `~/.cache/zstd-java/<sha256>/`), and reused across runs.
If the cache root is unusable, extraction falls back to a temporary file that is deleted on exit.

## GraalVM native image

The published jars ship reachability metadata under `META-INF/native-image/`. The
`platforms` jar registers its bundled libraries as resources, so bundled loading and
extraction work inside a native image.

## Compatibility

- Semantic versioning from 1.0.
- `dev.hallock.zstd` (the `zstd-java` artifact) is the stable API.
- `dev.hallock.zstd.bindings` tracks the native zstd ABI and carries no independent compatibility promise.
- The zstd frame format itself is stable and interoperable, per
  [RFC 8878](https://www.rfc-editor.org/rfc/rfc8878).

<details>
<summary>AI Disclosure</summary>
AI was used to generate most of the tests. My philosophy is that AI generated tests are slightly better coverage than no coverage. 
This should change in the future and is the only place that is acceptable usage.
</details>

## Resources

- [Zstandard manual](https://facebook.github.io/zstd/doc/api_manual_latest.html)
- [FFM](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html)
- [Jextract](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md)
