# zstd-java

Java bindings for the Zstandard compression library. Its meant to replace the use of JNI with FFM.

### Usage
Adding to Gradle (kts):
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.hallock.zstd:zstd:0.1.0")
}
```

### Java
Requires Java 25 and will target LTS releases.

#### Resources
- [Zstandard](https://facebook.github.io/zstd/doc/api_manual_latest.html)
- [FFM](https://docs.oracle.com/en/java/javase/25/core/foreign-function-and-memory-api.html)
- [Jextract](https://github.com/openjdk/jextract/blob/master/doc/GUIDE.md)

<details>
<summary>AI Disclosure</summary>
AI was used to generate the Demo example, and most of the tests. My philosophy is that AI generated tests are slightly better coverage than no coverage. 
This may change in the future and is the only place(s) that is acceptable usage.
</details>



