# Testing

There are three layers, from fastest to most thorough.

| Layer | Command | What it proves | Time |
| --- | --- | --- | --- |
| Unit tests | `./gradlew :core:test` | The countdown and config logic in `core/`, with no Minecraft involved | seconds |
| Dev smoke test | `node test/smoke/run.mjs --dev --only <target>` | The mod works in the development environment for one target | 2 – 3 min per target |
| Docker smoke test | `node test/smoke/run.mjs` | The release jars work on real server installs, on every Minecraft version each jar claims | about 20 min for all 40 targets once cached |

GitHub Actions runs the unit tests and the builds on pushes to `dev` and `master` and on pull requests. The Docker smoke test is run locally, and can also be started by hand on GitHub Actions.

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

Servers run in offline mode and listen on `127.0.0.1` only, on ports from 25600 up, one per target (25600 – 25639 today). Add `--lan` to let other machines on the network join (for manual checks from another PC).

### Dev mode

`--dev` runs the same checks against a target's Gradle `runServer` task, so it tests the code as the IDE runs it, without building a jar. It runs one target at a time and uses ports from 25700 up. Only the version each jar is built against has a `runServer`, so `--dev` skips the other targets.

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

`.github/workflows/build.yml` runs on pushes to `dev` and `master` and on every pull request. Those runs only build:

1. `modern` runs the unit tests and builds the modern jars. `legacy` builds the three legacy jars. Both upload the jars as artifacts on the run.

The smoke test is not part of those runs. To run it on GitHub's machines, start the workflow by hand: Actions tab > Build > Run workflow, or `gh workflow run build.yml --ref dev`. A run started this way also does:

2. `smoke-targets` runs `run.mjs --list-json` to get the target list.
3. `smoke` starts one job per target. Each downloads the jar it needs and runs `node test/smoke/run.mjs --only <target>`.

Nobody can join a server on GitHub's machines, so a `MANUAL` target stays unchecked there. The job still passes on its shutdown check, and the run finishes with a warning naming the targets to test by hand locally. GitHub does not track whether you did.

A red `Smoke <target>` job means that target failed. The job's summary page shows the result table, the job log shows the last lines of the server log, and the full log is in the `smoke-log-<target>` artifact. `MANUAL` targets show up as a warning annotation on the run, not as a failure.

With the [GitHub CLI](https://cli.github.com/):

```sh
gh run list --branch dev --limit 5
gh run view <run id> --log-failed
gh run download <run id> --name smoke-log-1.20.1-forge
```

## How targets are found

There is no separate list to maintain.

A target is one Minecraft version that one jar is tested on, with the id `<mc>-<loader>`.

- Modern jars are the `[loader."mc"]` tables in `stonecutter.properties.toml`. Each jar is tested on every version in its `mod.smoke_versions` (or just its own version if the key is missing). On the version the jar is built against, the loader build is pinned from `deps.forge` / `deps.neoforge`. On other versions the image installs the recommended or latest loader build for that version.
- Legacy jars are the `legacy/forge-<mc>/` and `legacy/neoforge-<mc>/` folders. An optional `smoke.properties` there sets `label` (the range part of the jar name) and `versions` (the versions to test on).

An entry `<mc>@<loader>` tests the jar on another loader's server, for example `1.20.1@neoforge` runs the Forge jar on NeoForge 1.20.1 (a Forge fork).

Keep each list to both ends of the jar's range plus one version per minor release in between. Adding a version to a list adds it to the smoke test and to CI.

## Notes

- Running the smoke test accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for the test servers.
- The countdown cannot be shorter than 1 minute (`SSSConfig` enforces it), so a target takes at least a minute after its server is up.
- On Forge 1.7.10 the bot joins as a Forge client that does not have the mod. That is the only way in on that version, and it also checks that clients without the mod are accepted.
