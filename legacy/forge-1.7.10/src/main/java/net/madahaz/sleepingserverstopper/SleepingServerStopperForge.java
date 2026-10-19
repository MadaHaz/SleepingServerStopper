package net.madahaz.sleepingserverstopper;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.madahaz.sleepingserverstopper.core.SSSConfig;
import net.madahaz.sleepingserverstopper.core.Scheduler;
import net.madahaz.sleepingserverstopper.core.ServerHandle;
import net.madahaz.sleepingserverstopper.core.ShutdownController;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.function.Consumer;

// acceptableRemoteVersions = "*" lets clients without the mod join - it only runs on the server
@Mod(modid = ShutdownController.MOD_ID, name = "Sleeping Server Stopper", version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public class SleepingServerStopperForge {
    private static final Logger LOGGER = LogManager.getLogger(ShutdownController.MOD_ID);
    private static final Consumer<String> LOG = LOGGER::info;

    private File configDir;
    private ShutdownController controller;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configDir = event.getModConfigurationDirectory();
        // Player connection events are on the FML bus in 1.7.10
        FMLCommonHandler.instance().bus().register(this);
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        final MinecraftServer server = MinecraftServer.getServer();
        if (!server.isDedicatedServer()) {
            return; // never shut down a singleplayer / LAN world
        }
        controller = new ShutdownController(SSSConfig.load(configDir, LOG), Scheduler.daemon(), LOG);
        controller.onServerStarted(new ServerHandle() {
            @Override
            public int playerCount() {
                return server.getCurrentPlayerCount();
            }

            @Override
            public void stop() {
                server.initiateShutdown();
            }

            @Override
            public void execute(Runnable task) {
                // 1.7.10 has no server task queue. The task only reads the player count and sets the
                // server's "running" flag, both of which are safe to do from the timer thread.
                task.run();
            }
        });
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (controller != null) {
            controller.onServerStopping();
            controller = null;
        }
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
        for (Object player : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            if (player != event.player) {
                remaining++;
            }
        }
        controller.onPlayerLeave(remaining);
    }
}
