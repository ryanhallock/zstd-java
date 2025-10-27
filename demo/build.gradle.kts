plugins {
    id("java")
    //id("org.graalvm.buildtools.native") version "0.11.1"
    id("application")
}

group = "dev.hallock.zstd"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass = "dev.hallock.zstd.demo.Demo"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    modularity.inferModulePath.set(true)
}

dependencies {
    implementation(project(":"))
}

