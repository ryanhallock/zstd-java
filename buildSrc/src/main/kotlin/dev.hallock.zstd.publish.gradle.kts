plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        inceptionYear.set("2025")
        url.set("https://github.com/ryanhallock/zstd-java/")
        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://opensource.org/licenses/MIT")
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
