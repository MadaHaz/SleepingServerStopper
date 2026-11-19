package net.madahaz.sleepingserverstopper;

import net.madahaz.sleepingserverstopper.core.SSSConfig;
import net.madahaz.sleepingserverstopper.core.Scheduler;
import net.madahaz.sleepingserverstopper.core.ServerHandle;
import net.madahaz.sleepingserverstopper.core.ShutdownController;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.function.Consumer;

// acceptableRemoteVersions = "*" lets clients without the mod join - it only runs on the server
@Mod(modid = ShutdownController.MOD_ID, name = "Sleeping Server Stopper", version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
public class SleepingServerStopperForge {
    private static final Logger LOGGER = LogManager.getLogger(ShutdownController.MOD_ID);
    private static final Consumer<String> LOG = LOGGER::info;

    private File configDir;
    private ShutdownController controller;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        configDir = event.getModConfigurationDirectory();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
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
                server.addScheduledTask(task);
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
        for (EntityPlayerMP player : FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayers()) {
            if (player != event.player) {
                remaining++;
            }
        }
        controller.onPlayerLeave(remaining);
    }
}
