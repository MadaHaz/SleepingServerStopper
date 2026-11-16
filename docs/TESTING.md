# Testing

There are three layers, from fastest to most thorough.

| Layer | Command | What it proves | Time |
| --- | --- | --- | --- |
| Unit tests | `./gradlew :core:test` | The countdown and config logic in `core/`, with no Minecraft involved | seconds |
| Dev smoke test | `node test/smoke/run.mjs --dev --only <target>` | The mod works in the development environment for one target | 2 – 3 min per target |
| Docker smoke test | `node test/smoke/run.mjs` | The release jars work on real server installs, for every version and loader | about 10 min for all 16 once cached |

GitHub Actions runs the unit tests, the builds and the Docker smoke test on every push.

## What the smoke test checks

For each target (one Minecraft version on one loader) it boots a dedicated server with the mod installed and a 1 minute countdown, then reads the server log:

1. `[SSS] Server empty - Server will shutdown in 1 minute(s)!` appears once the server has started. The mod loaded and saw the empty server.
2. A bot joins. `[SSS] Player joined - Server shutdown cancelled.` must appear.
3. The bot leaves. The `Server empty` line must appear again.
4. `[SSS] Server empty - Server shutting down.` must appear 50 – 100 seconds after step 3, and the server process must then exit with code 0.

The result table has two columns per target:

- **SHUTDOWN** is `PASS` when steps 1 and 4 held.
- **JOIN/LEAVE** is `PASS` when steps 2 and 3 held, `FAIL` when the bot got in but the mod did not react, and `MANUAL` when the bot could not join at all. `MANUAL` is not a failure, it means a person has to check that part.

The script exits with code 1 if any target has a `FAIL`.

## Setup

- [Node.js](https://nodejs.org/) 22 or newer.
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine on Linux), running. Not needed for `--dev`.
- Once: `cd test/smoke && npm ci`. This installs the bot ([node-minecraft-protocol](https://github.com/PrismarineJS/node-minecraft-protocol)).

## Running it

```sh
node test/smoke/run.mjs --build                # build every jar, then test all targets
node test/smoke/run.mjs                        # test the jars that are already built
node test/smoke/run.mjs --only 1.21.1          # both loaders of one Minecraft version
node test/smoke/run.mjs --only neoforge        # every target of one loader
node test/smoke/run.mjs --only 1.20.1-forge    # one target
node test/smoke/run.mjs --list                 # show the targets, images and ports
```

| Flag | Effect |
| --- | --- |
| `--only <a,b>` | Only these targets. Each entry is a target id, a Minecraft version or a loader. |
| `--parallel <n>` | Servers to run at once. Default 4. Each one needs roughly 1.5 – 2 GB of memory. |
| `--build` | Run `gradlew buildAll` and the three legacy builds first. |
| `--dev` | Use Gradle's `runServer` instead of Docker. See below. |
| `--no-bot` | Skip the join/leave check. |
| `--no-manual` | Don't start servers for the `MANUAL` targets after the run. |
| `--manual-only` | Skip the automated run and start the selected targets for manual testing. |
| `--list`, `--list-json` | Print the targets and exit. The JSON form feeds the CI matrix. |

Output:

- A line per event while it runs, and the result table at the end.
- `test/smoke/.work/results.json`: the same results in machine-readable form.
- `test/smoke/.work/logs/<target>.log`: each server's full log. Dev runs use `<target>.dev.log`, manual servers `<target>.manual.log`.

### Docker mode

Each server runs in an [itzg/minecraft-server](https://github.com/itzg/docker-minecraft-server) container, which installs the right loader for the version. The Java version comes from the image tag (`java8` up to 1.16.5, `java17`, `java21`, `java25`). Fabric targets also get Fabric API from Modrinth.

The server install is kept in a Docker volume per target, named `sss-smoke-<target>-data`. The first run downloads everything and takes a few minutes per server. Later runs boot in 10 – 40 seconds. To start from scratch:

```sh
docker volume ls -q --filter name=sss-smoke- | xargs docker volume rm
```

The mod jar and config are copied into the volume before every start, so the cache never holds a stale jar.

Servers run in offline mode and listen on `127.0.0.1` only, on ports 25600 – 25615.

### Dev mode

`--dev` runs the same checks against a target's Gradle `runServer` task, so it tests the code as the IDE runs it, without building a jar. It runs one target at a time and uses ports 25700 – 25715.

It writes `eula.txt`, `config/sleepingserverstoppermod.properties` and the port and `online-mode=false` lines of `server.properties` into that target's run directory (`run/<target>/`, or `legacy/forge-<mc>/run/`).

### Memory

Run one smoke test at a time, and don't run Docker mode and dev mode together. Gradle and four servers at once is enough to exhaust a 32 GB machine. The script refuses to start in Docker mode while `sss-smoke-*` containers from another run are still up.

If memory is tight, use `--parallel 2` and run `./gradlew --stop` first to free idle Gradle daemons.

## Manual checks

The bot can only join versions its protocol library knows. When a new Minecraft version comes out, the library usually lags behind for a while, and those targets come back as `MANUAL`.

After the automated run, the script lists them and starts a server for each, with no launch countdown so it waits for you:

```
18:11:19 [26.3-fabric] READY  ->  localhost:25600   (Minecraft 26.3, fabric)
```

For each one:

1. Join that address with a matching client (offline mode, any username), then leave.
2. Rejoin within a minute to see the countdown cancel, then leave again.
3. The server should stop by itself a minute later.

The script reports what it sees in the log as you go, and prints a table of leave seen, cancel seen and clean shutdown at the end. Ctrl+C stops all of them. Manual servers are skipped when `--no-manual` is given or the `CI` environment variable is set.

To start a manual server for any target: `node test/smoke/run.mjs --manual-only --only <target>`.

## GitHub Actions

`.github/workflows/build.yml` runs on every push and pull request:

1. `modern` and `legacy` build every jar and upload them as artifacts.
2. `smoke-targets` runs `run.mjs --list-json` to get the target list.
3. `smoke` starts one job per target. Each downloads the jar it needs and runs `node test/smoke/run.mjs --only <target>`.

A red `Smoke <target>` job means that target failed. The job's summary page shows the result table, the job log shows the last lines of the server log, and the full log is in the `smoke-log-<target>` artifact. `MANUAL` targets show up as a warning annotation on the run, not as a failure.

With the [GitHub CLI](https://cli.github.com/):

```sh
gh run list --branch dev --limit 5
gh run view <run id> --log-failed
gh run download <run id> --name smoke-log-1.20.1-forge
```

## How targets are found

There is no separate list to maintain.

- Modern targets are the `[loader."mc"]` tables in `stonecutter.properties.toml`. The loader versions to install come from the same tables.
- Legacy targets are the `legacy/forge-<mc>/` folders.

Adding a version to the build adds it to the smoke test and to CI.

## Notes

- Running the smoke test accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for the test servers.
- The countdown cannot be shorter than 1 minute (`SSSConfig` enforces it), so a target takes at least a minute after its server is up.
- On Forge 1.7.10 the bot joins as a Forge client that does not have the mod. That is the only way in on that version, and it also checks that clients without the mod are accepted.
