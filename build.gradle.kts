plugins {
    id("dev.hallock.zstd.java")
    id("dev.hallock.zstd.quality")
    id("dev.hallock.zstd.publish")
    alias(libs.plugins.graalvm.buildtools)
}

// CI opt-in: -PtestBundledNatives tests against the bundled natives from :platforms; values
// other than ""/true/false fail fast so a workflow typo cannot test the wrong library.
val testBundledNatives = providers.gradleProperty("testBundledNatives")
    .map {
        when (it.lowercase()) {
            "", "true" -> true
            "false" -> false
            else -> throw GradleException(
                "Unrecognized -PtestBundledNatives value '$it'; use true or false"
            )
        }
    }
    .orElse(false)
    .get()

dependencies {
    api(project(":bindings"))
    // The core public API carries the JSpecify nullness annotations.
    api(libs.jspecify)
    if (testBundledNatives) {
        testRuntimeOnly(project(":platforms"))
    }
}

tasks.test {
    val nativeAccessModules = buildList {
        add("dev.hallock.zstd.bindings")
        if (testBundledNatives) {
            add("dev.hallock.zstd.platforms")
        }
    }
    jvmArgs("--enable-native-access=${nativeAccessModules.joinToString(",")}")
}

graalvmNative {
    agent {
        enabled.set(providers.gradleProperty("nativeAgent").map(String::toBoolean).orElse(false))
        metadataCopy {
            inputTaskNames.add("test")
            outputDirectories.add("src/test/resources/META-INF/native-image/dev.hallock.zstd/zstd-java-test")
            mergeWithExisting.set(true)
        }

        modes {
            defaultMode = "standard"
            standard {
                accessFilterFiles.from("src/test/resources/native-image/access-filter.json")
            }
        }
    }

    binaries {
        named("test") {
            buildArgs.add("-O0")
            // native-build-tools builds the test image from the class path, so everything in the
            // image runs in the unnamed module; module-targeted grants cannot apply here.
            jvmArgs.add("--enable-native-access=ALL-UNNAMED")
        }
    }
}

mavenPublishing {
    pom {
        name.set("zstd-java")
        description.set("Java (FFM) API for Zstandard (zstd)")
    }
}
