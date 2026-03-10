### Roadmap
Javadocs and better fail fast checking

Create bundled bindings for popular platforms (Windows, Linux, MacOS) to avoid the need for users to install the Zstandard library separately.

Create validation abstraction for streaming (Currently streaming works but allows out of bound accesses)

Support byte[] and ByteBuffer APIs for easier integration with existing code, (Likely copying data to native memory)

Validate native-image reachability metadata on push and create a workflow for it.

Validate jextract on push and create a workflow for it.

Support bindings separately to allow implementations that just consume the jextract module. (native image reachability)

1.0 Will not be tagged until all TODOS are addressed, and Streaming no longer can do out of bound accesses

Consider splitting API into dev.hallock.zstd.api to use services pro