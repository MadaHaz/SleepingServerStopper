// Standalone build: RetroFuturaGradle can't share a Gradle build with Loom/ModDevGradle.
// Build from the repo root with: ./gradlew -p legacy/forge-1.7.10 build
pluginManagement {
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            name = "GTNH Maven"
            mavenContent {
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
                includeGroup("com.gtnewhorizons")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sleepingserverstoppermod-forge-1.7.10"
