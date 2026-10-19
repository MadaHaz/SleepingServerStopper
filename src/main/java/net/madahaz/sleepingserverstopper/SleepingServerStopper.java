package net.madahaz.sleepingserverstopper;

import net.madahaz.sleepingserverstopper.core.SSSConfig;
import net.madahaz.sleepingserverstopper.core.Scheduler;
import net.madahaz.sleepingserverstopper.core.ServerHandle;
import net.madahaz.sleepingserverstopper.core.ShutdownController;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.function.Consumer;

/**
 * Shared glue between the loader entrypoints and {@link ShutdownController}. Uses only Mojang-mapped
 * {@link MinecraftServer} methods that are the same on every supported version and loader.
 */
public final class SleepingServerStopper {
    public static final String MOD_ID = ShutdownController.MOD_ID;

    //? if <1.18 {
    /*private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger(MOD_ID);
     *///?} else {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);
    //?}
    private static final Consumer<String> LOG = LOGGER::info;

    private static MinecraftServer server;
    private static ShutdownController controller;

    private SleepingServerStopper() {
    }

    public static void onServerStarted(MinecraftServer started, File configDir) {
        if (!started.isDedicatedServer()) {
            return; // never shut down a singleplayer / LAN world
        }
        server = started;
        controller = new ShutdownController(SSSConfig.load(configDir, LOG), Scheduler.daemon(), LOG);
        controller.onServerStarted(new ServerHandle() {
            @Override
            public int playerCount() {
                return started.getPlayerCount();
            }

            @Override
            public void stop() {
                started.halt(false);
            }

            @Override
            public void execute(Runnable task) {
                started.execute(task);
            }
        });
    }

    public static void onServerStopping() {
        if (controller != null) {
            controller.onServerStopping();
            controller = null;
        }
        server = null;
    }

    public static void onPlayerJoin() {
        if (controller != null) {
            controller.onPlayerJoin();
        }
    }

    /**
     * @param leaving the player who is disconnecting; counted out whether or not the loader has already
     *                removed them from the player list.
     */
    public static void onPlayerLeave(Object leaving) {
        if (controller == null) {
            return;
        }
        int remaining = 0;
        for (Object player : server.getPlayerList().getPlayers()) {
            if (player != leaving) {
                remaining++;
            }
        }
        controller.onPlayerLeave(remaining);
    }
}
