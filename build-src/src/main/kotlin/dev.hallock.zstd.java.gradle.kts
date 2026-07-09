plugins {
    `java-library`
}

group = "dev.hallock.zstd"
version = providers.gradleProperty("version").orElse("dev").get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    modularity.inferModulePath.set(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Redundant with an exact Java 25 toolchain, but pins the compiled API/bytecode level
    // if the toolchain configuration ever floats.
    options.release.set(25)
}

tasks.withType<Javadoc>().configureEach {
    val options = options as StandardJavadocDocletOptions
    options.tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:")
}
