package net.madahaz.sleepingserverstopper.core;

import java.util.function.Consumer;

/**
 * All of the mod's behaviour, independent of Minecraft version and mod loader.
 * <p>
 * Loader adapters forward server lifecycle and player connection events here. Every method except the
 * scheduler callback is expected to be called on the server thread.
 */
public final class ShutdownController {
    public static final String MOD_ID = "sleepingserverstoppermod";

    private final SSSConfig config;
    private final Scheduler scheduler;
    private final Consumer<String> log;

    private ServerHandle server;
    private Runnable cancelCountdown;
    /** Incremented whenever the countdown is started or cancelled, so stale timer callbacks are ignored. */
    private int generation;

    public ShutdownController(SSSConfig config, Scheduler scheduler, Consumer<String> log) {
        this.config = config;
        this.scheduler = scheduler;
        this.log = message -> log.accept("[SSS] " + message);
    }

    public synchronized void onServerStarted(ServerHandle server) {
        this.server = server;
        log.accept("Shutdown time: " + config.shutdownTimeInMinutes + " minute(s), shutdown on launch: " + config.shutdownOnLaunch);
        if (config.shutdownOnLaunch) {
            checkEmpty(server.playerCount());
        }
    }

    public synchronized void onPlayerJoin() {
        if (cancelCountdown != null) {
            log.accept("Player joined - Server shutdown cancelled.");
            cancel();
        }
    }

    /**
     * @param remainingPlayers players still online, not counting the one leaving. Loaders differ on whether the
     *                         leaving player has already been removed from the player list, so adapters compute this.
     */
    public synchronized void onPlayerLeave(int remainingPlayers) {
        checkEmpty(remainingPlayers);
    }

    public synchronized void onServerStopping() {
        cancel();
        server = null;
        scheduler.shutdown();
    }

    public synchronized boolean isCountingDown() {
        return cancelCountdown != null;
    }

    private void checkEmpty(int players) {
        if (server == null || players > 0 || cancelCountdown != null) {
            return;
        }
        log.accept(String.format("Server empty - Server will shutdown in %d minute(s)!", config.shutdownTimeInMinutes));
        final int expected = ++generation;
        final ServerHandle target = server;
        cancelCountdown = scheduler.schedule(() -> target.execute(() -> onCountdownFinished(expected)),
                config.shutdownTimeInMinutes * 60_000L);
    }

    private synchronized void onCountdownFinished(int expected) {
        if (expected != generation || server == null) {
            return;
        }
        cancelCountdown = null;
        int players = server.playerCount();
        if (players <= 0) {
            log.accept("Server empty - Server shutting down.");
            server.stop();
        } else {
            log.accept(String.format("Abort shutdown - %d connected player(s).", players));
        }
    }

    private void cancel() {
        generation++;
        if (cancelCountdown != null) {
            cancelCountdown.run();
            cancelCountdown = null;
        }
    }
}
