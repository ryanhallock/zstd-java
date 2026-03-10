# zstd-java

Java bindings for the Zstandard compression library.

### Provides
- `dev.hallock.zstd` is higher level bindings (Module)
- `dev.hallock.zstd.bindings` the underlying native bindings (Module)

### Dependencies
- Java 25 or higher
- Zstandard library (zstd)

### Usage
Adding to Gradle (kts):
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.hallock.zstd:zstd:<version>")
}
```
where `<version>` is the latest tag.

### Runtime
Ideally you want your project to be using JPMS as running on the module path allows nicer syntax to open native access. 
`--enable-native-access=dev.hallock.zstd.bindings` should be used to allow native access to the native Zstandard library.

Running in the unnamed module should be supported but is not recommended.

#### Resources
- [Zstandard](https://facebook.github.io/zstd/doc/api_manual_latest.html)
- [FFM](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html)
- [Jextract](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md)

<details>
<summary>AI Disclosure</summary>
AI was used to generate most of the tests. My philosophy is that AI generated tests are slightly better coverage than no coverage. 
This should change in the future and is the only place that is acceptable usage.
</details>



