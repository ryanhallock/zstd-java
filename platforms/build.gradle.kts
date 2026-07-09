plugins {
    id("dev.hallock.zstd.java")
    id("dev.hallock.zstd.quality")
    id("dev.hallock.zstd.publish")
}

dependencies {
    implementation(project(":bindings"))
    testImplementation(project(":"))
}

providers.gradleProperty("platformTestResources").orNull?.let {
    sourceSets.test {
        resources.srcDir(it)
    }
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

val expectedPlatformResources = listOf(
    "linux/x86_64/libzstd.so",
    "linux/aarch64/libzstd.so",
    "linux-musl/x86_64/libzstd.so",
    "linux-musl/aarch64/libzstd.so",
    "macos/x86_64/libzstd.dylib",
    "macos/aarch64/libzstd.dylib",
    "windows/x86_64/zstd.dll",
    "windows/aarch64/zstd.dll",
)

val verifyPlatformResources = tasks.register("verifyPlatformResources") {
    group = "verification"
    description = "Verifies that every supported native library and checksum is staged."
    // Hoisted so the action captures only configuration-cache-serializable values,
    // never the script object or Project.
    val resourceRoot =
        layout.projectDirectory.dir("src/main/resources/dev/hallock/zstd/platforms/impl/natives")
    val expectedResources = expectedPlatformResources.flatMap { listOf(it, "$it.sha256") }
    doLast {
        val missing = expectedResources.filterNot { resourceRoot.file(it).asFile.isFile }
        check(missing.isEmpty()) { "Missing platform resources: ${missing.joinToString()}" }
    }
}

tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(verifyPlatformResources)
}

mavenPublishing {
    pom {
        name.set("zstd-java platforms")
        description.set("Bundled native Zstandard libraries for zstd-java")
    }
}
