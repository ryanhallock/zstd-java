@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-src")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "zstd-java"
include("bindings")
include("platforms")
