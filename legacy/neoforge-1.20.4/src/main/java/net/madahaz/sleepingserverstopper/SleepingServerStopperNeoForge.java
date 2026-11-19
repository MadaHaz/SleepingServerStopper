package net.madahaz.sleepingserverstopper;

import net.madahaz.sleepingserverstopper.core.SSSConfig;
import net.madahaz.sleepingserverstopper.core.Scheduler;
import net.madahaz.sleepingserverstopper.core.ServerHandle;
import net.madahaz.sleepingserverstopper.core.ShutdownController;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Mod(ShutdownController.MOD_ID)
public class SleepingServerStopperNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownController.MOD_ID);
    private static final Consumer<String> LOG = LOGGER::info;

    private MinecraftServer server;
    private ShutdownController controller;

    public SleepingServerStopperNeoForge() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
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

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (controller != null) {
            controller.onServerStopping();
            controller = null;
        }
        server = null;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (controller != null) {
            controller.onPlayerJoin();
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
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
