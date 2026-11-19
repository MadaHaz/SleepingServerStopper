package net.madahaz.sleepingserverstopper;

import net.madahaz.sleepingserverstopper.core.SSSConfig;
import net.madahaz.sleepingserverstopper.core.Scheduler;
import net.madahaz.sleepingserverstopper.core.ServerHandle;
import net.madahaz.sleepingserverstopper.core.ShutdownController;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Mod(ShutdownController.MOD_ID)
public class SleepingServerStopperForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownController.MOD_ID);
    private static final Consumer<String> LOG = LOGGER::info;

    private MinecraftServer server;
    private ShutdownController controller;

    public SleepingServerStopperForge() {
        // Forge 1.21.6+ (EventBus 7): every event has its own static bus
        ServerStartedEvent.BUS.addListener(this::onServerStarted);
        ServerStoppingEvent.BUS.addListener(this::onServerStopping);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerJoin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(this::onPlayerLeave);
    }

    private void onServerStarted(ServerStartedEvent event) {
        final MinecraftServer started = event.getServer();
        if (!started.isDedicatedServer()) {
            return; // never shut down a singleplayer / LAN world
        }
        server = started;
        controller = new ShutdownController(SSSConfig.load(FMLPaths.CONFIGDIR.get().toFile(), LOG), Scheduler.daemon(), LOG);
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

    private void onServerStopping(ServerStoppingEvent event) {
        if (controller != null) {
            controller.onServerStopping();
            controller = null;
        }
        server = null;
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (controller != null) {
            controller.onPlayerJoin();
        }
    }

    private void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (controller == null) {
            return;
        }
        int remaining = 0;
        for (Object player : server.getPlayerList().getPlayers()) {
            if (player != event.getEntity()) {
                remaining++;
            }
        }
        controller.onPlayerLeave(remaining);
    }
}
