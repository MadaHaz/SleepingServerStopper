package net.madahaz.sleepingserverstopper;

import net.madahaz.sleepingserverstopper.core.SSSConfig;
import net.madahaz.sleepingserverstopper.core.Scheduler;
import net.madahaz.sleepingserverstopper.core.ServerHandle;
import net.madahaz.sleepingserverstopper.core.ShutdownController;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;

@Mod(ShutdownController.MOD_ID)
public class SleepingServerStopperForge {
    private static final Logger LOGGER = LogManager.getLogger(ShutdownController.MOD_ID);
    private static final Consumer<String> LOG = LOGGER::info;

    private MinecraftServer server;
    private ShutdownController controller;

    public SleepingServerStopperForge() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(FMLServerStartedEvent event) {
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
    public void onServerStopping(FMLServerStoppingEvent event) {
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
            if (player != event.getPlayer()) {
                remaining++;
            }
        }
        controller.onPlayerLeave(remaining);
    }
}
