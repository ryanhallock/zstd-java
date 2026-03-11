plugins {
    `java-library`
    alias(libs.plugins.jextract)
}

group = "dev.hallock.zstd"
version = findProperty("version") ?: "dev"

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    modularity.inferModulePath.set(true)
}

dependencies {
    api(libs.jspecify)
}

jextract.libraries {
    val zstd by registering {
        header = project.file("src/c/header.h")
        useSystemLoadLibrary = true

        targetPackage = "dev.hallock.zstd.bindings"
        headerClassName = "ZSTD_h"
        output = project.file("src/main/java")
    }
    // Don't use a lazy addLater, we don't want to declare it in a SourceSet
    jextract.libraries.add(zstd.get())
}