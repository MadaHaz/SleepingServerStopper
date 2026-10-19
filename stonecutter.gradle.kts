plugins {
    id("dev.kikugie.stonecutter")
    // Declared once here so the NeoForge and Forge nodes share one copy of ModDevGradle
    id("net.neoforged.moddev") version "2.0.148" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.148" apply false
}

stonecutter active "26.3-fabric"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    val loader = current.project.substringAfterLast('-')

    properties {
        tags(current.version, loader)
    }

    // Enables `//? if fabric {`, `//? if forge {` and `//? if neoforge {` in sources
    constants {
        match(loader, "fabric", "forge", "neoforge")
    }
}

// Builds every node and copies the jars to build/libs/<mod version>/
tasks.register("buildAll") {
    group = "build"
    description = "Builds all versions and loaders"
    dependsOn(stonecutter.tasks.named("buildAndCollect"))
}
