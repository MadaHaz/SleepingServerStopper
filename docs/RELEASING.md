# Releasing

Releases are uploaded by hand. One release is 12 jars, each covering a range of Minecraft versions.

## Checklist

1. Bump `mod.version` in `stonecutter.properties.toml`. Every build, the legacy ones included, reads it from there. Add a line to "Versions" in `README.md`.
2. Build everything:
   ```sh
   ./gradlew :core:test buildAll
   ./gradlew -p legacy/forge-1.21.6 build
   ./gradlew -p legacy/forge-1.21.1 build
   ./gradlew -p legacy/forge-1.20.4 build
   ./gradlew -p legacy/neoforge-1.20.4 build
   ./gradlew -p legacy/forge-1.16.5 build
   ./gradlew -p legacy/forge-1.12.2 build
   ./gradlew -p legacy/forge-1.7.10 build
   ```
   `node test/smoke/run.mjs --build --no-manual` does the same builds and then step 3.
3. Run the full smoke test (`node test/smoke/run.mjs --no-manual`; see `docs/TESTING.md`). Every target must pass, and join/leave must be checked by hand on any target reported as `MANUAL`.
4. Upload each jar with the loader and game versions below. On Modrinth and CurseForge, tick every release version in the range, not only the ends.

## Jars

| Jar | Loader | Game versions to tick |
| :-- | :----- | :-------------------- |
| `build/libs/<v>/sleepingserverstoppermod-fabric-<v>+1.16.4-1.21.11.jar` | Fabric | 1.16.4 – 1.21.11 |
| `build/libs/<v>/sleepingserverstoppermod-fabric-<v>+26.x.jar` | Fabric | 26.1 and later 26.x |
| `build/libs/<v>/sleepingserverstoppermod-neoforge-<v>+1.20.6-26.x.jar` | NeoForge | 1.20.6 – 26.x |
| `legacy/neoforge-1.20.4/build/libs/sleepingserverstoppermod-neoforge-<v>+1.20.2-1.20.4.jar` | NeoForge | 1.20.2 – 1.20.4 |
| `build/libs/<v>/sleepingserverstoppermod-forge-<v>+1.18.2-1.20.1.jar` | Forge, and NeoForge (1.20.1 only) | 1.18.2 – 1.20.1 (NeoForge: 1.20.1) |
| `legacy/forge-1.20.4/build/libs/sleepingserverstoppermod-forge-<v>+1.20.2-1.20.4.jar` | Forge | 1.20.2 – 1.20.4 |
| `build/libs/<v>/sleepingserverstoppermod-forge-<v>+1.17.1.jar` | Forge | 1.17.1 |
| `legacy/forge-1.21.6/build/libs/sleepingserverstoppermod-forge-<v>+1.21.6-1.21.11.jar` | Forge | 1.21.6 – 1.21.11 |
| `legacy/forge-1.21.1/build/libs/sleepingserverstoppermod-forge-<v>+1.20.6-1.21.5.jar` | Forge | 1.20.6 – 1.21.5 |
| `legacy/forge-1.16.5/build/libs/sleepingserverstoppermod-forge-<v>+1.16.5.jar` | Forge | 1.16.5 |
| `legacy/forge-1.12.2/build/libs/sleepingserverstoppermod-forge-<v>+1.12.2.jar` | Forge | 1.12.2 |
| `legacy/forge-1.7.10/build/libs/sleepingserverstoppermod-forge-<v>+1.7.10.jar` | Forge | 1.7.10 |

The ranges here must match what the jars declare: `mod.mc_compat` in `stonecutter.properties.toml`, and `versionRange` in each legacy `mods.toml`. If you widen a range, update it in four places: the metadata, the smoke test list (`mod.smoke_versions` or `smoke.properties`), this table, and the README.

## When a new Minecraft version comes out

1. Add the version to the matching jar's `mod.smoke_versions` (or `smoke.properties`) and widen `mod.mc_compat` (or `versionRange`). Rebuild.
2. Run `node test/smoke/run.mjs --no-manual --only <version>`.
3. If it passes, the jar can be marked as supporting that version. No new jar is needed.
4. If the mod crashes or misbehaves there, the game or loader changed something the mod uses. Revert the range and add a new Stonecutter node (or legacy build) starting at that version.
