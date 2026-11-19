// Standalone build: NeoForge 1.20.2 - 1.20.4 needs the old mods.toml and Java 17, which ModDevGradle does not support, and Architectury Loom
// can't share a Gradle build with Fabric Loom. Build from the repo root with:
//   ./gradlew -p legacy/neoforge-1.20.4 build
pluginManagement {
    repositories {
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sleepingserverstoppermod-neoforge-1.20.4"
