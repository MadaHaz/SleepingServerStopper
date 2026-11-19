plugins {
    id("java-library")
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.6"
}

// Single source of truth for the mod version: the root stonecutter.properties.toml
val modVersion = Regex("""(?m)^mod\.version\s*=\s*"([^"]+)"""")
    .find(rootDir.resolve("../../stonecutter.properties.toml").readText())!!.groupValues[1]

group = "net.madahaz"
version = "$modVersion+1.7.10"
base.archivesName = "sleepingserverstoppermod-forge"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
        vendor = JvmVendorSpec.AZUL
    }
}

sourceSets.main {
    java.srcDir(rootDir.resolve("../../core/src/main/java"))
}

minecraft {
    mcVersion = "1.7.10"
    injectedTags.put("VERSION", modVersion)
}

tasks.injectTags.configure {
    outputClassName = "net.madahaz.sleepingserverstopper.Tags"
}

tasks.processResources.configure {
    inputs.property("version", modVersion)
    filesMatching("mcmod.info") {
        expand(mapOf("version" to modVersion, "mcversion" to "1.7.10"))
    }
}
