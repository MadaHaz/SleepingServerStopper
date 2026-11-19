plugins {
    id("dev.architectury.loom") version "1.17.493"
}

// Single source of truth for the mod version: the root stonecutter.properties.toml
val modVersion = Regex("""(?m)^mod\.version\s*=\s*"([^"]+)"""")
    .find(rootDir.resolve("../../stonecutter.properties.toml").readText())!!.groupValues[1]

group = "net.madahaz"
version = "$modVersion+1.20.2-1.20.4"
base.archivesName = "sleepingserverstoppermod-neoforge"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

sourceSets.main {
    java.srcDir(rootDir.resolve("../../core/src/main/java"))
}

repositories {
    maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
}

dependencies {
    minecraft("com.mojang:minecraft:1.20.4")
    mappings(loom.officialMojangMappings())
    neoForge("net.neoforged:neoforge:20.4.253")
}

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to modVersion))
    }
}
