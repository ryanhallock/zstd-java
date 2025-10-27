plugins {
    id("java-library")
    id("org.graalvm.buildtools.native") version "0.11.2"
}

group = "dev.hallock.zstd"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("org.jspecify:jspecify:1.0.0")
    api(project(":bindings"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=dev.hallock.zstd.bindings");
}


java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    modularity.inferModulePath = true
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
                jvmArgs.add("--enable-native-access=dev.hallock.zstd.bindings")
            }
        }
    }
}

