#!/usr/bin/env node
// Smoke test: boots a real dedicated server for every Minecraft version + loader each jar claims to support,
// then checks the mod's behaviour from the server log.
//
//   empty on launch -> countdown starts -> bot joins -> countdown cancelled -> bot leaves
//   -> countdown starts again -> server shuts itself down and the process exits cleanly
//
// Default mode runs the release jars in Docker (itzg/minecraft-server). `--dev` runs the Gradle
// `runServer` tasks instead. See README.md ("Testing") for usage.

import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import readline from 'node:readline';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../..');
const LOGS = path.join(HERE, '.work', 'logs');
const IS_WIN = process.platform === 'win32';
const GRADLEW = path.join(ROOT, IS_WIN ? 'gradlew.bat' : 'gradlew');

const IMAGE = 'itzg/minecraft-server';
const BASE_PORT = 25600;
const BOT_NAME = 'SSSBot';

const M_CONFIG = '[SSS] Shutdown time: ';
const M_EMPTY = '[SSS] Server empty - Server will shutdown in';
const M_CANCEL = '[SSS] Player joined - Server shutdown cancelled.';
const M_STOP = '[SSS] Server empty - Server shutting down.';

const SECOND = 1000;
const BOOT_TIMEOUT = 20 * 60 * SECOND; // includes first-time image pull and server install
const JOIN_TIMEOUT = 45 * SECOND;
const LEAVE_TIMEOUT = 30 * SECOND;
const STOP_TIMEOUT = 150 * SECOND;
const EXIT_TIMEOUT = 180 * SECOND;
// The countdown is 1 minute, the shortest the mod allows
const COUNTDOWN_MIN = 50 * SECOND;
const COUNTDOWN_MAX = 100 * SECOND;

// ---------------------------------------------------------------- arguments

const args = parseArgs(process.argv.slice(2));

function parseArgs(argv) {
    const out = { only: [], parallel: null, dev: false, build: false, bot: true, manual: !process.env.CI, manualOnly: false, lan: false, list: false, listJson: false };
    for (let i = 0; i < argv.length; i++) {
        const arg = argv[i];
        if (arg === '--only') out.only.push(...argv[++i].split(','));
        else if (arg === '--parallel') out.parallel = Number(argv[++i]);
        else if (arg === '--dev') out.dev = true;
        else if (arg === '--build') out.build = true;
        else if (arg === '--no-bot') out.bot = false;
        else if (arg === '--no-manual') out.manual = false;
        else if (arg === '--manual-only') out.manualOnly = true;
        else if (arg === '--lan') out.lan = true;
        else if (arg === '--list') out.list = true;
        else if (arg === '--list-json') out.listJson = true;
        else if (arg === '--help' || arg === '-h') usage(0);
        else {
            console.error(`Unknown argument: ${arg}\n`);
            usage(2);
        }
    }
    return out;
}

function usage(code) {
    console.log(`Usage: node test/smoke/run.mjs [options]

  --only <a,b>     Only these targets: an id, Minecraft version or loader (e.g. 1.20.1-forge, 1.21.1, neoforge)
  --parallel <n>   Servers to run at once (default 4, always 1 with --dev)
  --build          Build every jar first
  --dev            Use the Gradle runServer tasks instead of Docker + release jars
  --no-bot         Skip the join/leave check
  --no-manual      Don't start servers for the versions that need a manual join/leave check
  --manual-only    Skip the automated run and start the selected targets for manual testing
  --lan            Let other machines on the network join the servers (default: this machine only)
  --list           Print the targets and exit`);
    process.exit(code);
}

// ---------------------------------------------------------------- targets

function compareVersions(a, b) {
    const pa = a.split('.').map(Number);
    const pb = b.split('.').map(Number);
    for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
        const diff = (pa[i] || 0) - (pb[i] || 0);
        if (diff !== 0) return diff;
    }
    return 0;
}

function javaTag(mc) {
    if (compareVersions(mc, '26') >= 0) return 'java25';
    if (compareVersions(mc, '1.20.5') >= 0) return 'java21';
    if (compareVersions(mc, '1.17') >= 0) return 'java17';
    return 'java8';
}

/**
 * A target is one Minecraft version that one jar is tested on. Each jar covers a range of versions and is
 * tested on every version listed for it:
 * - modern jars: `mod.smoke_versions` of each node in stonecutter.properties.toml
 * - legacy jars: `versions` in legacy/<dir>/smoke.properties (default: the version in the folder name)
 * Targets on the version a jar was built against also get a Gradle `runServer` for `--dev`.
 */
function loadTargets() {
    const toml = fs.readFileSync(path.join(ROOT, 'stonecutter.properties.toml'), 'utf8');
    const top = {};
    const tables = [];
    let current = null;
    for (const line of toml.split(/\r?\n/)) {
        const table = /^\[(\w+)\."([^"]+)"\]/.exec(line);
        if (table) {
            current = { loader: table[1], mc: table[2], props: {} };
            tables.push(current);
            continue;
        }
        const pair = /^([\w.]+)\s*=\s*"([^"]*)"/.exec(line);
        if (pair) (current ? current.props : top)[pair[1]] = pair[2];
    }
    const modId = top['mod.id'];
    const modVersion = top['mod.version'];
    const versionList = text => (text || '').split(/[\s,]+/).filter(Boolean);

    const targets = [];
    for (const { loader, mc: built, props } of tables) {
        const node = `${built}-${loader}`;
        const label = props['mod.mc_label'] || built;
        const versions = versionList(props['mod.smoke_versions']);
        for (const entry of versions.length ? versions : [built]) {
            // `<mc>@<loader>` runs the jar on another loader's server (NeoForge 1.20.1 is a Forge fork)
            const [mc, server = loader] = entry.split('@');
            const own = mc === built && server === loader;
            const env = { VERSION: mc };
            if (server === 'fabric') {
                env.TYPE = 'FABRIC';
                env.FABRIC_LOADER_VERSION = top['deps.fabric_loader'];
                env.MODRINTH_PROJECTS = 'fabric-api';
            } else if (server === 'forge') {
                env.TYPE = 'FORGE';
                // On other versions the image picks the recommended (or latest) loader build
                if (own) env.FORGE_VERSION = props['deps.forge'].replace(`${built}-`, '');
            } else {
                env.TYPE = 'NEOFORGE';
                if (own) env.NEOFORGE_VERSION = props['deps.neoforge'];
            }
            targets.push({
                id: `${mc}-${server}`, mc, built, loader: server, env,
                jar: path.join(ROOT, 'build', 'libs', modVersion, `${modId}-${loader}-${modVersion}+${label}.jar`),
                artifact: { name: 'modern', path: 'build/libs' },
                gradleArgs: own ? [`:${node}:runServer`] : null,
                runDir: path.join(ROOT, 'run', node),
            });
        }
    }

    const legacyRoot = path.join(ROOT, 'legacy');
    for (const dir of fs.readdirSync(legacyRoot).sort()) {
        const match = /^(forge|neoforge)-(.+)$/.exec(dir);
        if (!match) continue;
        const [, loader, built] = match;
        const smokeFile = path.join(legacyRoot, dir, 'smoke.properties');
        const smoke = {};
        if (fs.existsSync(smokeFile)) {
            for (const line of fs.readFileSync(smokeFile, 'utf8').split(/\r?\n/)) {
                const pair = /^\s*([\w.]+)\s*=\s*(.*?)\s*$/.exec(line);
                if (pair && !line.trimStart().startsWith('#')) smoke[pair[1]] = pair[2];
            }
        }
        const label = smoke.label || built;
        const versions = versionList(smoke.versions);
        const script = fs.readFileSync(path.join(legacyRoot, dir, 'build.gradle.kts'), 'utf8');
        // The loader build the jar is built against: `forge:<mc>-<build>` or `neoforge:<build>`
        const pinned = loader === 'forge'
            ? new RegExp(`forge:${built.replaceAll('.', '\\.')}-([\\d.]+)`).exec(script)
            : /neoforge:([\d.]+)/.exec(script);
        const LOADER = loader.toUpperCase();
        for (const mc of versions.length ? versions : [built]) {
            const env = { TYPE: LOADER, VERSION: mc };
            if (pinned && mc === built) env[`${LOADER}_VERSION`] = pinned[1];
            targets.push({
                id: `${mc}-${loader}`, mc, built, loader, env,
                jar: path.join(legacyRoot, dir, 'build', 'libs', `${modId}-${loader}-${modVersion}+${label}.jar`),
                artifact: { name: dir, path: `legacy/${dir}/build/libs` },
                gradleArgs: mc === built ? ['-p', `legacy/${dir}`, 'runServer'] : null,
                runDir: path.join(legacyRoot, dir, 'run'),
                legacyDir: `legacy/${dir}`,
            });
        }
    }

    const ids = new Set();
    for (const target of targets) {
        if (ids.has(target.id)) throw new Error(`two jars are tested on ${target.id}; fix the smoke version lists`);
        ids.add(target.id);
    }
    targets.sort((a, b) => compareVersions(b.mc, a.mc) || a.loader.localeCompare(b.loader));
    targets.forEach((target, index) => {
        // Dev runs get their own range so they can run next to the Docker ones
        target.port = BASE_PORT + index + (args.dev ? 100 : 0);
        target.image = `${IMAGE}:${javaTag(target.mc)}`;
    });
    return { targets, modId, modVersion };
}

// ---------------------------------------------------------------- process helpers

function log(id, message) {
    console.log(`${new Date().toTimeString().slice(0, 8)} [${id}] ${message}`);
}

/** Collects a server's output and lets the scenario wait for a line containing some text. */
class Watcher {
    constructor(logFile) {
        this.lines = [];
        this.waiters = [];
        this.exitCode = null;
        this.listeners = [];
        fs.mkdirSync(path.dirname(logFile), { recursive: true });
        this.file = fs.createWriteStream(logFile);
        this.exited = new Promise(resolve => (this.resolveExit = resolve));
    }

    push(text) {
        const entry = { text, time: Date.now(), index: this.lines.length };
        this.lines.push(entry);
        this.file.write(text + '\n');
        this.waiters = this.waiters.filter(waiter => {
            if (!text.includes(waiter.needle)) return true;
            waiter.resolve(entry);
            return false;
        });
        for (const listener of this.listeners) listener(entry);
    }

    exit(code) {
        this.exitCode = code;
        this.file.end();
        for (const waiter of this.waiters) waiter.reject(new Error(`server exited with code ${code} while waiting for "${waiter.needle}"`));
        this.waiters = [];
        this.resolveExit(code);
    }

    waitFor(needle, timeoutMs, from = 0) {
        const hit = this.lines.slice(from).find(line => line.text.includes(needle));
        if (hit) return Promise.resolve(hit);
        if (this.exitCode !== null) return Promise.reject(new Error(`server exited with code ${this.exitCode} before logging "${needle}"`));
        return new Promise((resolve, reject) => {
            const waiter = {
                needle,
                resolve: entry => { clearTimeout(timer); resolve(entry); },
                reject: error => { clearTimeout(timer); reject(error); },
            };
            const timer = setTimeout(() => {
                this.waiters = this.waiters.filter(other => other !== waiter);
                reject(new Error(`timed out after ${Math.round(timeoutMs / SECOND)}s waiting for "${needle}"`));
            }, timeoutMs);
            this.waiters.push(waiter);
        });
    }

    tail(count = 15) {
        return this.lines.slice(-count).map(line => `    ${line.text}`).join('\n');
    }
}

function pipeLines(child, watcher) {
    for (const stream of [child.stdout, child.stderr]) {
        readline.createInterface({ input: stream }).on('line', line => watcher.push(line));
    }
    child.on('error', error => watcher.push(`[smoke] failed to start process: ${error.message}`));
    child.on('close', code => watcher.exit(code ?? -1));
}

function run(command, commandArgs, options) {
    if (!Array.isArray(commandArgs)) return spawnSync(command, { encoding: 'utf8', ...commandArgs });
    return spawnSync(command, commandArgs, { encoding: 'utf8', ...options });
}

function gradle(gradleArgs, options = {}) {
    // .bat files can only be started through a shell on Windows
    return IS_WIN
        ? spawn(`"${GRADLEW}" ${gradleArgs.join(' ')}`, { cwd: ROOT, shell: true, ...options })
        : spawn(GRADLEW, gradleArgs, { cwd: ROOT, ...options });
}

const active = new Set();

// ---------------------------------------------------------------- launchers

/** The address to give a person joining by hand: this machine's LAN address with `--lan`. */
function joinHost() {
    if (!args.lan) return 'localhost';
    const addresses = Object.values(os.networkInterfaces()).flat()
        .filter(address => address && address.family === 'IPv4' && !address.internal);
    const lan = addresses.find(address => /^(192\.168|10)\./.test(address.address)) || addresses[0];
    return lan ? lan.address : 'localhost';
}

function configText(config) {
    return `shutdownTimeInMinutes=${config.minutes}\nshutdownOnLaunch=${config.onLaunch}\n`;
}

function containerName(target) {
    return `sss-smoke-${target.id}`;
}

/** Release jar on a real server install. The install is cached in a named volume between runs. */
function startDocker(target, ctx, config, logFile) {
    const name = containerName(target);
    const volume = `${name}-data`;
    run('docker', ['rm', '-f', name]);

    // Put the jar and config into the volume with a throwaway container, so nothing depends on how the
    // image syncs mounted folders and no old jar is left behind after a version bump.
    const prepare = run('docker', [
        'run', '--rm', '--entrypoint', 'sh',
        '-v', `${volume}:/data`, '-v', `${path.dirname(target.jar)}:/in:ro`,
        '-e', `SSS_CFG=${configText(config)}`, '-e', `SSS_JAR=${path.basename(target.jar)}`,
        target.image, '-c',
        `mkdir -p /data/mods /data/config && rm -f /data/mods/${ctx.modId}-*.jar && cp "/in/$SSS_JAR" /data/mods/ `
        + `&& printf '%s' "$SSS_CFG" > /data/config/${ctx.modId}.properties && chown -R --reference=/data /data/mods /data/config`,
    ]);
    if (prepare.status !== 0) throw new Error(`could not prepare the server volume:\n${prepare.stderr || prepare.stdout}`);

    const env = { EULA: 'TRUE', MEMORY: '1G', ONLINE_MODE: 'FALSE', VIEW_DISTANCE: '4', ...target.env };
    const child = spawn('docker', [
        'run', '--rm', '--name', name,
        '-p', `${args.lan ? '0.0.0.0' : '127.0.0.1'}:${target.port}:25565`,
        '-v', `${volume}:/data`,
        ...Object.entries(env).flatMap(([key, value]) => ['-e', `${key}=${value}`]),
        target.image,
    ]);
    const watcher = new Watcher(logFile);
    pipeLines(child, watcher);
    return { watcher, kill: () => run('docker', ['stop', '-t', '20', name]) };
}

/** Dev environment: the node's Gradle runServer task, using its normal run directory. */
function startGradle(target, ctx, config, logFile) {
    fs.mkdirSync(path.join(target.runDir, 'config'), { recursive: true });
    fs.writeFileSync(path.join(target.runDir, 'eula.txt'), 'eula=true\n');
    fs.writeFileSync(path.join(target.runDir, 'config', `${ctx.modId}.properties`), configText(config));

    // Keep whatever is already in server.properties, only force the port and offline mode
    const propsFile = path.join(target.runDir, 'server.properties');
    const forced = { 'server-port': String(target.port), 'online-mode': 'false' };
    const kept = fs.existsSync(propsFile)
        ? fs.readFileSync(propsFile, 'utf8').split(/\r?\n/).filter(line => line && !(line.split('=')[0] in forced))
        : [];
    fs.writeFileSync(propsFile, [...kept, ...Object.entries(forced).map(([key, value]) => `${key}=${value}`)].join('\n') + '\n');

    const child = gradle([...target.gradleArgs, '--console=plain'], { stdio: ['pipe', 'pipe', 'pipe'] });
    const watcher = new Watcher(logFile);
    pipeLines(child, watcher);
    return {
        watcher,
        kill: () => {
            if (IS_WIN) run('taskkill', ['/pid', String(child.pid), '/T', '/F']);
            else child.kill('SIGKILL');
        },
    };
}

function start(target, ctx, config, logName) {
    const logFile = path.join(LOGS, `${logName}.log`);
    const server = (args.dev ? startGradle : startDocker)(target, ctx, config, logFile);
    server.logFile = logFile;
    active.add(server);
    server.watcher.exited.then(() => active.delete(server));
    return server;
}

// ---------------------------------------------------------------- bot

/** The client half of the Forge 1.7.10 mod handshake, which runs over the FML|HS plugin channel. */
function answerForgeHandshake(client, mods) {
    const HELLO = 0, CLIENT_HELLO = 1, MOD_LIST = 2, MOD_ID_DATA = 3, ACK = 0xff;
    const text = value => {
        const bytes = Buffer.from(value, 'utf8');
        return Buffer.concat([Buffer.from([bytes.length]), bytes]); // ids and versions are far below 128 bytes
    };
    const send = (channel, data) => client.write('custom_payload', { channel, data });
    const ack = phase => send('FML|HS', Buffer.from([ACK, phase]));

    client.on('custom_payload', packet => {
        if (packet.channel !== 'FML|HS') return;
        const type = packet.data[0];
        if (type === HELLO) {
            send('REGISTER', Buffer.from(['FML|HS', 'FML', 'FML|MP', 'FML', 'FORGE'].join('\0')));
            send('FML|HS', Buffer.from([CLIENT_HELLO, packet.data[1]]));
            send('FML|HS', Buffer.concat([Buffer.from([MOD_LIST, mods.length]), ...mods.flatMap(mod => [text(mod.modid), text(mod.version)])]));
        } else if (type === MOD_LIST) {
            ack(2); // waiting for server data
        } else if (type === MOD_ID_DATA) {
            ack(3); // waiting for the server to complete
        } else if (type === ACK) {
            ack(packet.data[1] === 2 ? 4 : 5); // pending complete, then complete
        }
    });
}

/**
 * Joins as an offline-mode player. Throws if the protocol library does not know the version.
 * `failed` resolves with a reason if the connection drops or is refused.
 */
async function startBot(target, ctx) {
    const require = createRequire(import.meta.url);
    const protocol = require('minecraft-protocol');
    const connection = { host: '127.0.0.1', port: target.port, version: target.mc };

    // Forge 1.7.10 never lets a vanilla connection into the world, so join the way a Forge client without
    // this mod would: answer its handshake with the server's own mod list, minus the mod under test.
    let forgeMods = null;
    if (compareVersions(target.mc, '1.8') < 0) {
        const status = await protocol.ping(connection);
        forgeMods = ((status.modinfo && status.modinfo.modList) || []).filter(mod => mod.modid !== ctx.modId);
    }

    const client = protocol.createClient({ ...connection, username: BOT_NAME, auth: 'offline', hideErrors: true });
    if (forgeMods) answerForgeHandshake(client, forgeMods);
    // Fabric API and NeoForge ping the client while it is being configured (1.20.2+) and wait for the reply
    client.on('ping', packet => client.write('pong', { id: packet.id }));
    const bot = { loggedIn: false, left: false };
    const reasonOf = packet => {
        const reason = packet && packet.reason;
        return typeof reason === 'string' ? reason : JSON.stringify(reason);
    };
    bot.failed = new Promise(resolve => {
        client.on('login', () => (bot.loggedIn = true));
        client.on('disconnect', packet => resolve(`refused: ${reasonOf(packet)}`));
        client.on('kick_disconnect', packet => resolve(`kicked: ${reasonOf(packet)}`));
        // Once in the world, a packet the library can't parse is not a failed join
        client.on('error', error => { if (!bot.loggedIn) resolve(`connection error: ${error.message}`); });
        client.on('end', reason => { if (!bot.left) resolve(`disconnected: ${reason}`); });
    });
    bot.leave = () => {
        bot.left = true;
        client.end();
    };
    return bot;
}

// ---------------------------------------------------------------- automated scenario

async function runTarget(target, ctx) {
    const result = { id: target.id, smoke: 'FAIL', joinLeave: args.bot ? 'MANUAL' : 'SKIPPED', detail: '', note: '' };
    let server;
    try {
        if (!args.dev && !fs.existsSync(target.jar)) {
            throw new Error(`jar not found: ${path.relative(ROOT, target.jar)} (run with --build)`);
        }
        log(target.id, `starting (${args.dev ? 'gradle runServer' : target.image}, port ${target.port})`);
        server = start(target, ctx, { minutes: 1, onLaunch: true }, args.dev ? `${target.id}.dev` : target.id);
        const { watcher } = server;
        const begun = Date.now();
        result.log = path.relative(ROOT, server.logFile).replaceAll('\\', '/');

        const firstEmpty = await watcher.waitFor(M_EMPTY, BOOT_TIMEOUT);
        await watcher.waitFor(M_CONFIG, 1, 0);
        result.bootSeconds = Math.round((firstEmpty.time - begun) / SECOND);
        log(target.id, `server up after ${result.bootSeconds}s, countdown started on launch`);
        let countdownStart = firstEmpty;

        if (args.bot) {
            const cancelled = watcher.waitFor(M_CANCEL, JOIN_TIMEOUT, firstEmpty.index + 1);
            let bot = null;
            let outcome;
            try {
                bot = await startBot(target, ctx);
                outcome = await Promise.race([
                    cancelled.then(entry => ({ entry }), error => ({ timeout: error.message })),
                    bot.failed.then(reason => ({ reason })),
                ]);
            } catch (error) {
                cancelled.catch(() => {});
                outcome = { reason: `bot could not connect (${error.message})` };
            }

            if (!outcome.entry && bot && bot.loggedIn) {
                // The bot got in and was dropped straight away - the server may still be about to log the join
                const late = await Promise.race([
                    cancelled.then(entry => ({ entry }), () => null),
                    new Promise(resolve => setTimeout(() => resolve(null), 5 * SECOND)),
                ]);
                if (late) outcome = late;
            }

            if (outcome.entry) {
                log(target.id, 'bot joined, countdown cancelled');
                bot.leave();
                try {
                    countdownStart = await watcher.waitFor(M_EMPTY, LEAVE_TIMEOUT, outcome.entry.index + 1);
                } catch (error) {
                    result.joinLeave = 'FAIL';
                    throw new Error(`bot left but the countdown did not start again: ${error.message}`);
                }
                log(target.id, 'bot left, countdown started again');
                result.joinLeave = 'PASS';
            } else if (bot && bot.loggedIn) {
                bot.leave();
                result.joinLeave = 'FAIL';
                throw new Error(`bot joined but the countdown was not cancelled (${outcome.reason || outcome.timeout})`);
            } else {
                if (bot) bot.leave();
                result.note = outcome.reason || 'bot could not join in time';
                log(target.id, `join/leave needs a manual check: ${result.note}`);
            }
        }

        const stopping = await watcher.waitFor(M_STOP, STOP_TIMEOUT, countdownStart.index + 1);
        const waited = stopping.time - countdownStart.time;
        if (waited < COUNTDOWN_MIN || waited > COUNTDOWN_MAX) {
            throw new Error(`shutdown came ${Math.round(waited / SECOND)}s after the countdown started, expected about 60s`);
        }
        log(target.id, `shutdown triggered after ${Math.round(waited / SECOND)}s`);

        const code = await Promise.race([
            watcher.exited,
            new Promise(resolve => setTimeout(() => resolve('timeout'), EXIT_TIMEOUT)),
        ]);
        if (code === 'timeout') throw new Error('the server logged the shutdown but its process never exited');
        if (code !== 0) throw new Error(`server process exited with code ${code}`);
        result.smoke = 'PASS';
        log(target.id, `PASS (join/leave: ${result.joinLeave})`);
    } catch (error) {
        result.detail = error.message;
        log(target.id, `FAIL: ${error.message}`);
        if (server) {
            console.log(server.watcher.tail());
            log(target.id, `full log: ${path.relative(ROOT, server.logFile)}`);
            if (server.watcher.exitCode === null) server.kill();
        }
    }
    return result;
}

// ---------------------------------------------------------------- manual servers

/** Leaves a server up with no launch countdown, and reports what it sees while a person joins and leaves. */
async function runManual(target, ctx) {
    const seen = { id: target.id, ready: false, cancelled: false, left: false, stopped: false };
    let server;
    try {
        server = start(target, ctx, { minutes: 1, onLaunch: false }, `${target.id}${args.dev ? '.dev' : ''}.manual`);
        const { watcher } = server;
        watcher.listeners.push(entry => {
            if (entry.text.includes(M_EMPTY)) {
                seen.left = true;
                log(target.id, 'saw the last player leave, 1 minute countdown started (rejoin now to test the cancel)');
            } else if (entry.text.includes(M_CANCEL)) {
                seen.cancelled = true;
                log(target.id, 'saw a join cancel the countdown');
            } else if (entry.text.includes(M_STOP)) {
                log(target.id, 'countdown finished, server shutting down');
            }
        });
        await watcher.waitFor(M_CONFIG, BOOT_TIMEOUT);
        seen.ready = true;
        log(target.id, `READY  ->  ${joinHost()}:${target.port}   (Minecraft ${target.mc}, ${target.loader})`);
        const code = await watcher.exited;
        seen.stopped = code === 0 && watcher.lines.some(line => line.text.includes(M_STOP));
        log(target.id, seen.stopped ? 'shut itself down cleanly' : `exited with code ${code}`);
    } catch (error) {
        log(target.id, `could not run: ${error.message}`);
        if (server && server.watcher.exitCode === null) server.kill();
    }
    return seen;
}

// ---------------------------------------------------------------- main

async function pool(items, size, worker) {
    const results = new Array(items.length);
    let next = 0;
    await Promise.all(Array.from({ length: Math.min(size, items.length) }, async () => {
        while (next < items.length) {
            const index = next++;
            results[index] = await worker(items[index]);
        }
    }));
    return results;
}

function table(rows) {
    const widths = rows[0].map((_, column) => Math.max(...rows.map(row => String(row[column]).length)));
    return rows.map(row => row.map((cell, column) => String(cell).padEnd(widths[column])).join('  ').trimEnd()).join('\n');
}

function buildAll(ctx) {
    const legacyDirs = [...new Set(ctx.targets.filter(target => target.legacyDir).map(target => target.legacyDir))];
    const builds = [['buildAll'], ...legacyDirs.map(dir => ['-p', dir, 'build'])];
    for (const gradleArgs of builds) {
        console.log(`> gradlew ${gradleArgs.join(' ')}`);
        const result = IS_WIN
            ? run(`"${GRADLEW}" ${gradleArgs.join(' ')}`, { cwd: ROOT, shell: true, stdio: 'inherit' })
            : run(GRADLEW, gradleArgs, { cwd: ROOT, stdio: 'inherit' });
        if (result.status !== 0) process.exit(result.status ?? 1);
    }
}

async function main() {
    const ctx = loadTargets();
    let selected = ctx.targets;
    if (args.only.length) {
        // An exact id, Minecraft version or loader wins, so `1.21.1` does not also pick 1.21.11
        const exact = (target, filter) => target.id === filter || target.mc === filter || target.loader === filter;
        selected = selected.filter(target => args.only.some(filter =>
            ctx.targets.some(other => exact(other, filter)) ? exact(target, filter) : target.id.includes(filter)));
        if (!selected.length) {
            console.error(`No target matches ${args.only.join(', ')}. Use --list.`);
            process.exit(2);
        }
    }
    if (args.dev) {
        // runServer only exists for the version each jar is built against
        const skipped = selected.filter(target => !target.gradleArgs);
        selected = selected.filter(target => target.gradleArgs);
        if (skipped.length) console.log(`--dev skips ${skipped.map(target => target.id).join(', ')} (no runServer for those versions)
`);
        if (!selected.length) {
            console.error('None of the selected targets has a dev server. Drop --dev to test them in Docker.');
            process.exit(2);
        }
    }

    if (args.listJson) {
        // Consumed by the GitHub Actions matrix
        console.log(JSON.stringify(selected.map(target => ({ target: target.id, artifact: target.artifact.name, path: target.artifact.path }))));
        return;
    }
    if (args.list) {
        console.log(table([['TARGET', 'JAR', 'IMAGE', 'PORT'],
            ...selected.map(target => [target.id, path.basename(target.jar), target.image, target.port])]));
        return;
    }

    if (args.bot && !args.manualOnly) {
        try {
            createRequire(import.meta.url).resolve('minecraft-protocol');
        } catch {
            console.error('The join/leave bot is not installed. Run `npm ci` in test/smoke, or pass --no-bot.');
            process.exit(2);
        }
    }
    if (!args.dev && run('docker', ['info']).status !== 0) {
        console.error('Docker is not running. Start Docker Desktop and try again, or use --dev.');
        process.exit(2);
    }
    if (!args.dev) {
        // A second run on top of a live one fights over container names and ports, and can exhaust memory
        const running = run('docker', ['ps', '--filter', 'name=sss-smoke-', '--format', '{{.Names}}']).stdout.trim();
        if (running) {
            console.error(`Smoke test servers are already running:\n${running}\n`
                + 'Wait for that run to finish, or stop them with: docker ps -q --filter name=sss-smoke- | xargs docker stop');
            process.exit(2);
        }
    }
    if (args.build) buildAll(ctx);

    const parallel = args.dev ? 1 : (args.parallel || 4);
    let interrupted = false;
    process.on('SIGINT', () => {
        if (interrupted) process.exit(130);
        interrupted = true;
        console.log('\nStopping servers...');
        for (const server of active) server.kill();
    });

    let results = [];
    let failed = false;
    if (!args.manualOnly) {
        console.log(`Testing ${selected.length} target(s), ${parallel} at a time. Logs: ${path.relative(ROOT, LOGS)}\n`);
        results = await pool(selected, parallel, target => (interrupted ? null : runTarget(target, ctx)));
        results = results.filter(Boolean);
        failed = results.some(result => result.smoke !== 'PASS' || result.joinLeave === 'FAIL');
        // Machine-readable copy of the table below
        fs.writeFileSync(path.join(HERE, '.work', 'results.json'),
            JSON.stringify({ mode: args.dev ? 'dev' : 'docker', finished: new Date().toISOString(), passed: !failed, results }, null, 2) + '\n');

        const rows = [['TARGET', 'SHUTDOWN', 'JOIN/LEAVE', 'DETAIL'],
            ...results.map(result => [result.id, result.smoke, result.joinLeave, result.detail || result.note])];
        console.log(`\n${table(rows)}\n`);
        if (process.env.GITHUB_STEP_SUMMARY) {
            const markdown = ['| Target | Shutdown | Join/leave | Detail |', '| --- | --- | --- | --- |',
                ...rows.slice(1).map(row => `| ${row.join(' | ')} |`)].join('\n');
            fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, markdown + '\n');
        }
    }

    const manualIds = args.manualOnly
        ? selected.map(target => target.id)
        : results.filter(result => result.joinLeave === 'MANUAL').map(result => result.id);
    if (manualIds.length && !args.manualOnly) {
        const message = `Join/leave was not checked automatically for: ${manualIds.join(', ')}. These need a manual test.`;
        console.log(process.env.GITHUB_ACTIONS ? `::warning title=Manual test needed::${message}` : `NOTE: ${message}`);
    }

    if (manualIds.length && !interrupted && (args.manual || args.manualOnly)) {
        const manualTargets = selected.filter(target => manualIds.includes(target.id));
        console.log(`
Starting ${manualTargets.length} server(s) for manual testing, ${parallel} at a time. For each one:
  1. Join the address printed on its READY line (offline mode, any username), then leave.
  2. Rejoin within a minute to see the countdown cancel, then leave again.
  3. The server should stop by itself a minute later. The next queued server then starts.
Press Ctrl+C to stop them all.
`);
        const seen = await pool(manualTargets, parallel, target => (interrupted ? null : runManual(target, ctx)));
        const yesNo = value => (value ? 'yes' : 'no');
        console.log(`\n${table([['TARGET', 'LEAVE SEEN', 'CANCEL SEEN', 'CLEAN SHUTDOWN'],
            ...seen.filter(Boolean).map(row => [row.id, yesNo(row.left), yesNo(row.cancelled), yesNo(row.stopped)])])}\n`);
    }

    process.exit(failed || (interrupted && !args.manualOnly) ? 1 : 0);
}

main().catch(error => {
    console.error(error);
    for (const server of active) server.kill();
    process.exit(1);
});
