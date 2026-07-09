/**
 * High-level Java bindings for the Zstandard (zstd) compression library, built on the Foreign
 * Function &amp; Memory API. The exported {@link dev.hallock.zstd} package provides simple and
 * streaming compression and decompression, contexts, dictionaries, and {@code java.io} stream
 * adapters over the low-level {@code dev.hallock.zstd.bindings} module.
 */
module dev.hallock.zstd {
  requires static org.jspecify;
  requires dev.hallock.zstd.bindings;

  exports dev.hallock.zstd;
}
