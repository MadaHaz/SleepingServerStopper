# Agent guide

Instructions for AI coding agents working in this repository. Humans: see `README.md` and `docs/TESTING.md`.

## Project

A server-side Minecraft mod that stops a dedicated server after it has been empty for a set time. One codebase builds 16 jars.

| Path | What it is |
| --- | --- |
| `core/` | Plain Java 8 logic (countdown, config) with unit tests. Compiled into every jar. |
| `src/` | Loader entrypoints for the modern builds, versioned with Stonecutter comments (`//? if fabric {`). |
| `stonecutter.properties.toml` | Mod version, and the per-target dependency versions. The list of modern targets. |
| `build.<loader>.gradle.kts` | Build script shared by every modern target of that loader. |
| `legacy/forge-<mc>/` | Standalone Gradle builds for Forge 1.16.5, 1.12.2 and 1.7.10. |
| `test/smoke/run.mjs` | Smoke test harness. `docs/TESTING.md` explains it in full. |

```sh
./gradlew :core:test                    # unit tests
./gradlew buildAll                      # modern jars -> build/libs/<mod version>/
./gradlew -p legacy/forge-1.12.2 build  # one legacy jar -> legacy/forge-1.12.2/build/libs/
```

On Windows use `.\gradlew.bat`. A cold build of everything takes 10 – 20 minutes.

## Running the tests

When asked to test the mod, do this in order and stop at the first step that fails.

1. **Preflight.**
   - `docker info` must succeed. If not, ask the user to start Docker Desktop. Don't work around it with `--dev`.
   - `test/smoke/node_modules` must exist. If not, run `npm ci` in `test/smoke`.
   - `docker ps --filter name=sss-smoke-` must be empty. If not, another run is in progress or was interrupted. Wait a couple of minutes (the servers stop themselves), then stop any that remain.
   - Check free memory. Below about 8 GB, use `--parallel 2`.
2. **Unit tests:** `./gradlew :core:test`.
3. **Build** if sources changed since the jars were built: `node test/smoke/run.mjs --build --no-manual` builds and then runs step 4. Otherwise go to step 4.
4. **Smoke test:** `node test/smoke/run.mjs --no-manual`. Allow 15 minutes, or 40 on a machine that has never run it. Run it in the background if your tool calls time out sooner.
5. **Read** `test/smoke/.work/results.json`. Each entry has `id`, `smoke`, `joinLeave`, `detail`, `note`, `log`, `bootSeconds`.

Rules while testing:

- **One run at a time.** Never run two smoke tests together, and never run `--dev` next to Docker mode. Free Gradle memory first with `./gradlew --stop` if a build has just finished.
- **Always pass `--no-manual`.** Without it the script starts servers and waits for a person. Only use `--manual-only` when the user asks for a server to join.
- To re-run a failure, use `--only <target>`. A cached target takes about 90 seconds.
- Use `--dev --only <target>` to check a source change quickly without rebuilding jars. It is not a substitute for the Docker run before reporting success.

## Diagnosing a failure

Start from the `detail` text, then open the log named in `log`. The mod's own lines all contain `[SSS]`:

```
[SSS] Shutdown time: 1 minute(s), shutdown on launch: true      mod loaded, config read
[SSS] Server empty - Server will shutdown in 1 minute(s)!       countdown started
[SSS] Player joined - Server shutdown cancelled.                join seen
[SSS] Server empty - Server shutting down.                      countdown finished
```

| `detail` says | Likely cause | Where to look |
| --- | --- | --- |
| `jar not found` | Not built, or the mod version changed | Build it. Not a bug. |
| `could not prepare the server volume` | Docker problem | The stderr printed with it. |
| `server exited with code N while waiting for "[SSS] Server empty - Server will shutdown` | The server crashed on startup | Search the log for `Exception`, `Caused by`, `Missing`, `requires`. A crash naming `sleepingserverstopper` or a mixin/class from this mod is a mod bug. A failed download or installer error is infrastructure. |
| `timed out ... waiting for "[SSS] Server empty - Server will shutdown` | Server is up but the mod did nothing | Is there a `[SSS] Shutdown time` line? If not, the mod did not load: check the mods list in the log and the metadata version ranges (`mod.mc_compat`, `mod.loader_compat` in `stonecutter.properties.toml`). If the server itself is still starting, it is slow, not broken. |
| `bot joined but the countdown was not cancelled` | Join event not handled on this target | The loader entrypoint for that target in `src/` or `legacy/`. Mod bug. |
| `bot left but the countdown did not start again` | Leave event not handled, or the player count is off by one | `SleepingServerStopper.onPlayerLeave` and the entrypoint. Mod bug. |
| `shutdown came Ns after the countdown started` | Timer wrong, or the shutdown task ran late | `core/` scheduler, and `execute` in the `ServerHandle`. Mod bug. |
| `the server logged the shutdown but its process never exited` | Server hung while stopping | The end of the log. Usually a mod bug (non-daemon thread, deadlock). |
| `server process exited with code N` | Crash during shutdown | The end of the log. |

Classify every failure as one of:

- **Mod bug:** the log shows the mod misbehaving. Fix it in `core/`, `src/` or `legacy/`, add a unit test in `core/` when the logic lives there, rebuild that target and re-run it.
- **Harness bug:** the server and mod behaved, and `run.mjs` misjudged it or the bot failed. Fix `run.mjs`.
- **Infrastructure:** Docker not running, a download from Mojang, Modrinth or a loader's Maven failed, a port in 25600 – 25615 is taken, or the machine ran out of memory. Re-run once. If it repeats, report it.

`joinLeave: "MANUAL"` is not a failure. Check `note`:

- `unsupported protocol version` means the bot library does not know that Minecraft version yet. Try `npm update minecraft-protocol` in `test/smoke`. If it is still unsupported, report it as needing a manual test.
- Anything else on a version that used to pass is a harness regression. Start a server with `--manual-only --only <target>` in the background and trace a bot against it by logging the packets of a `minecraft-protocol` client.

Known quirks, already handled in `run.mjs`:

- Forge 1.7.10 never admits a vanilla connection. The bot answers the FML handshake as a Forge client without this mod (`answerForgeHandshake`).
- Fabric API and NeoForge on 1.20.2+ send a `ping` during the configuration phase and wait for a `pong`. The library does not reply by itself.
- The library may fail to parse a play packet on modded servers. Errors after login are ignored on purpose.

## CI

`.github/workflows/build.yml` builds everything and runs the Docker smoke test, one job per target, on every push.

```sh
gh run list --branch <branch> --limit 5
gh run view <run id> --log-failed
gh run download <run id> --name smoke-log-<target>
```

Diagnose a red `Smoke <target>` job the same way as a local failure. Reproduce locally with `--only <target>` before changing anything.

## Boundaries

- Don't commit or push unless asked.
- Don't change what the shipped mod does (defaults, config keys, log messages, the 1 minute minimum) to make a test pass. Report it and ask. The harness matches the `[SSS]` log messages, so changing one means changing `run.mjs` too.
- Don't delete the `sss-smoke-*` Docker volumes unless a cached install is the suspected cause. They save several minutes per target.
- Don't edit files under `versions/`. Stonecutter generates them.

## Reporting back

Finish with a short report:

1. The result table (target, shutdown, join/leave).
2. For each failure: the classification, the root cause in one or two sentences, the log line that shows it, and what you changed.
3. What was re-run after fixes, and its result.
4. **Needs you:** only the things a person must do. For example: join the listed `MANUAL` targets by hand (`node test/smoke/run.mjs --manual-only --only <target>`), start Docker, or decide on a behaviour change.

State plainly anything that was not run.
