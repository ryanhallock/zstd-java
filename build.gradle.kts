plugins {
    `java-library`
    alias(libs.plugins.graalvm.buildtools)
}

group = "dev.hallock.zstd"
version = findProperty("version") ?: "dev"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jspecify)
    api(project(":bindings"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=dev.hallock.zstd.bindings")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    modularity.inferModulePath = true
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:all")
    options.compilerArgs.add("-Werror")
    options.encoding = "UTF-8"
}

graalvmNative {
    agent {
        enabled.set(true)
        metadataCopy {
            inputTaskNames.add("test")
            outputDirectories.add("src/main/resources/META-INF/native-image/dev.hallock.zstd")
            mergeWithExisting.set(true)
        }

        modes {
            defaultMode = "standard"
            standard {
                //TODO suppress globs
                accessFilterFiles.from("src/test/resources/native-image/access-filter.json")
            }
        }

        binaries {
            named("test") {
                buildArgs.add("-O0")
                jvmArgs.add("--enable-native-access=ALL-UNNAMED") //TODO figure out why this isn't using modules
            }
        }
    }
}

