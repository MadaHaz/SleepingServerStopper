pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // https://stonecutter.kikugie.dev/
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks the right Fabric Loom variant for obfuscated (<26.1) and unobfuscated (26.1+) versions
    id("dev.kikugie.loom-back-compat") version "0.4.3"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * Creates `versions/{mc}-{loader}` nodes, each built by `build.{loader}.gradle.kts`.
         * Per-node dependency versions and compatible MC ranges live in `stonecutter.properties.toml`.
         */
        fun match(mc: String, vararg loaders: String) {
            for (loader in loaders) version("$mc-$loader", mc).buildscript("build.$loader.gradle.kts")
        }

        // Each node builds one jar that covers a range of Minecraft versions (`mod.mc_compat`).
        // Build against the lowest version of the range; runtime names must stay stable across it.
        match("1.16.5", "fabric")   // 1.16.4 - 1.21.11 (intermediary names)
        match("26.1", "fabric")     // 26.1+ (unobfuscated)
        match("1.17.1", "forge")    // 1.17.1 (server events live in another package)
        match("1.18.2", "forge")    // 1.18.2 - 1.20.1 (SRG names)
        match("1.21.1", "neoforge") // 1.21 - 26.x (Mojang names)
        vcsVersion = "26.1-fabric"
    }
}

// Loader-independent logic, unit tested on its own and compiled into every jar (including legacy/).
include("core")

rootProject.name = "SleepingServerStopper"
