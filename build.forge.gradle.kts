// MinecraftForge 1.17 - 1.20.1 via ModDevGradle's legacyforge plugin.
// Forge 1.16.5 and older are separate builds under legacy/.
plugins {
    id("net.neoforged.moddev.legacyforge")
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "${property("mod.id")}-forge"

val requiredJava = JavaVersion.VERSION_17

sourceSets.main {
    java.srcDir(rootProject.file("core/src/main/java"))
}

legacyForge {
    version = sc.properties.get<String>("deps.forge")

    mods {
        register(sc.properties.get<String>("mod.id")) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("server") {
            gameDirectory = rootProject.file("run/${sc.current.project}")
            server()
        }
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        val props = mapOf(
            "id" to sc.properties.get<String>("mod.id"),
            "name" to sc.properties.get<String>("mod.name"),
            "version" to sc.properties.get<String>("mod.version"),
            "authors" to sc.properties.get<String>("mod.authors"),
            "description" to sc.properties.get<String>("mod.description"),
            "minecraft" to sc.properties.get<String>("mod.mc_compat"),
            "loader" to sc.properties.get<String>("mod.loader_compat"),
        )
        inputs.properties(props)
        filesMatching("META-INF/mods.toml") { expand(props) }
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the reobfuscated mod jar and copies it to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        // legacyforge moves the dev jar to build/devlibs and makes `reobfJar` the release artifact
        from(named<Jar>("reobfJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}
