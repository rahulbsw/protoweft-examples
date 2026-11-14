plugins {
    // Keep in step with springBootVersion in gradle.properties (plugin versions can't read properties).
    id("org.springframework.boot") version "4.1.2" apply false
}

subprojects {
    apply(plugin = "java")

    group = "io.github.rahulbsw.protoweft.examples"
    version = "0.1.0-SNAPSHOT"

    repositories {
        // Protoweft snapshots (published from protoweft's main branch) until a release is on Maven Central.
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content { includeGroup("io.github.rahulbsw") }
            mavenContent { snapshotsOnly() }
        }
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    // Security floor: ARGI pulls jsoup 1.15.4 transitively (GHSA-65r4-943x-97jj, GHSA-pmhh-3w7g-xqp8).
    val jsoupVersion = property("jsoupVersion")
    configurations.configureEach {
        // Always resolve the latest Protoweft snapshot (snapshots are the only changing modules here).
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.jsoup:jsoup")).using(module("org.jsoup:jsoup:$jsoupVersion"))
        }
    }
}
