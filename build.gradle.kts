plugins {
    `java-library`
    alias(libs.plugins.graalvm.buildtools)
    alias(libs.plugins.maven.publish)
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

tasks.javadoc {
    val options = options as StandardJavadocDocletOptions
    options.tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:")
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

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)
    signAllPublications()

    pom {
        name.set("zstd-java")
        description.set("Java (FFM) API for Zstandard (zstd)")
        inceptionYear.set("2026")
        url.set("https://github.com/ryanhallock/zstd-java/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("ryanhallock")
                name.set("Ryan Hallock")
                url.set("https://github.com/ryanhallock/")
            }
        }
        scm {
            url.set("https://github.com/ryanhallock/zstd-java/")
            connection.set("scm:git:git://github.com/ryanhallock/zstd-java.git")
            developerConnection.set("scm:git:ssh://git@github.com/ryanhallock/zstd-java.git")
        }
    }
}