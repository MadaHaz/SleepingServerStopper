// Standalone build: ModDevGradle only supports Forge up to 1.20.1, and Architectury Loom
// can't share a Gradle build with Fabric Loom. Build from the repo root with:
//   ./gradlew -p legacy/forge-1.21.1 build
pluginManagement {
    repositories {
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.minecraftforge.net/") { name = "Forge" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sleepingserverstoppermod-forge-1.21.1"
