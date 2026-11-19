plugins {
    id("dev.architectury.loom") version "1.17.493"
}

// Single source of truth for the mod version: the root stonecutter.properties.toml
val modVersion = Regex("""(?m)^mod\.version\s*=\s*"([^"]+)"""")
    .find(rootDir.resolve("../../stonecutter.properties.toml").readText())!!.groupValues[1]

group = "net.madahaz"
version = "$modVersion+1.16.5"
base.archivesName = "sleepingserverstoppermod-forge"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
        vendor = JvmVendorSpec.AZUL
    }
}

sourceSets.main {
    java.srcDir(rootDir.resolve("../../core/src/main/java"))
}

dependencies {
    minecraft("com.mojang:minecraft:1.16.5")
    mappings(loom.officialMojangMappings())
    forge("net.minecraftforge:forge:1.16.5-36.2.42")
}

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("META-INF/mods.toml") {
        expand(mapOf("version" to modVersion))
    }
}
