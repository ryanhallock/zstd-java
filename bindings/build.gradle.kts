plugins {
    id("java-library")
    id("de.infolektuell.jextract") version "1.3.0"
}

group = "dev.hallock.zstd"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    modularity.inferModulePath.set(true)
}

dependencies {
    api("org.jspecify:jspecify:1.0.0")
}

jextract.libraries {
    val zstd by registering {
        header = project.file("src/c/header.h")
        useSystemLoadLibrary = true
        //libraries.add("zstd")

        targetPackage = "dev.hallock.zstd.bindings"
        headerClassName = "ZSTD_h"
        output = project.file("src/main/java")
    }
    // Don't use a lazy addLater, we don't want to declare it in a SourceSet
    jextract.libraries.add(zstd.get())
}