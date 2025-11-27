package com.madahaz.sleepingserverstoppermod;

import com.madahaz.sleepingserverstoppermod.config.SleepingServerStopperModConfig;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

@Mod(modid = SleepingServerStopperMod.MODID, version = SleepingServerStopperMod.VERSION, name = "Sleeping Server Stopper Mod")
public class SleepingServerStopperMod
{
    public static final String MODID = "sleepingserverstoppermod";
    public static final String VERSION = "3.0.1-1.7.10";
    
    private static final Logger LOGGER = LogManager.getLogger(MODID);

    private static int shutdownTime;
    private static boolean shutdownOnLaunch;
    private static MinecraftServer server;
    private static Timer timer;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        // Load configuration
        SleepingServerStopperModConfig.init(new File(event.getModConfigurationDirectory(), "sleepingserverstoppermod.cfg"));
        
        // Register event handlers
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        shutdownTime = SleepingServerStopperModConfig.SHUTDOWN_TIME_IN_MINUTES;
        shutdownOnLaunch = SleepingServerStopperModConfig.SHUTDOWN_SERVER_ON_LAUNCH;
        LOGGER.info("[SSS] TIME = " + shutdownTime);
        LOGGER.info("[SSS] BOOL = " + shutdownOnLaunch);
        onServerStart(MinecraftServer.getServer());
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (timer != null) {
            timer.cancel();
        }
    }

    @SubscribeEvent
    public void onPlayerConnect(PlayerEvent.PlayerLoggedInEvent event) {
        onPlayerJoin();
    }

    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        countPlayers();
    }

    // METHODS

    public static void onServerStart(MinecraftServer server) {
        SleepingServerStopperMod.server = server;

        if (shutdownOnLaunch) {
            countPlayers();
        }
    }

    public static void countPlayers() {
        if (server.getCurrentPlayerCount() <= 0) {
            LOGGER.info(String.format("[SSS] Server Empty - Server will shutdown in %d minute(s)!", shutdownTime));
            TimerTask task = new TimerTask() {
                public void run() {
                    stopper();
                }
            };
            timer = new Timer();
            timer.schedule(task, (60000L * shutdownTime));
        }
    }

    public static void onPlayerJoin() {
        if (server.getCurrentPlayerCount() <= 1 & timer != null) {
            LOGGER.info("[SSS] Player joined - Server shutdown cancelled.");
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public static void stopper() {
        int playerCount = server.getCurrentPlayerCount();
        if (playerCount <= 0) {
            LOGGER.info("[SSS] Server empty - Server shutting down.");
            server.initiateShutdown();
        } else {
            LOGGER.info(String.format("[SSS] Abort shutdown - %d connected player(s).", playerCount));
        }
    }
}