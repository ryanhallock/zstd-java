@file:Suppress("UnstableApiUsage")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "zstd-java"
include("bindings")
include("platforms")
