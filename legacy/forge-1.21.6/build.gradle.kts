plugins {
    id("dev.architectury.loom") version "1.17.493"
}

// Single source of truth for the mod version: the root stonecutter.properties.toml
val modVersion = Regex("""(?m)^mod\.version\s*=\s*"([^"]+)"""")
    .find(rootDir.resolve("../../stonecutter.properties.toml").readText())!!.groupValues[1]

group = "net.madahaz"
version = "$modVersion+1.21.6-1.21.11"
base.archivesName = "sleepingserverstoppermod-forge"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

sourceSets.main {
    java.srcDir(rootDir.resolve("../../core/src/main/java"))
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.6")
    mappings(loom.officialMojangMappings())
    forge("net.minecraftforge:forge:1.21.6-56.0.9")
}

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to modVersion))
    }
}
