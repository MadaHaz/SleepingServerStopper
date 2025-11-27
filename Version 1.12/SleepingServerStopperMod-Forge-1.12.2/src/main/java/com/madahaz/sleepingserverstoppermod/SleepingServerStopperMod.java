package com.madahaz.sleepingserverstoppermod;

import com.madahaz.sleepingserverstoppermod.config.SleepingServerStopperModConfig;
import com.madahaz.sleepingserverstoppermod.proxy.CommonProxy;
import com.madahaz.sleepingserverstoppermod.util.Reference;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

@Mod(modid = Reference.MODID, name = Reference.NAME, version = Reference.VERSION)
public class SleepingServerStopperMod
{
    @Mod.Instance
    public static SleepingServerStopperMod instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    private static Logger logger;

    private static int shutdownTime;
    private static boolean shutdownOnLaunch;
    private static MinecraftServer server;
    private static Timer timer;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        
        // Initialize configuration
        SleepingServerStopperModConfig.init(event.getSuggestedConfigurationFile());
        
        // Register event handlers
        MinecraftForge.EVENT_BUS.register(this);
    }

    // Server started Event.
    @EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        shutdownTime = SleepingServerStopperModConfig.SHUTDOWN_TIME_IN_MINUTES;
        shutdownOnLaunch = SleepingServerStopperModConfig.SHUTDOWN_SERVER_ON_LAUNCH;
        logger.info("[SSS] TIME = " + shutdownTime);
        logger.info("[SSS] BOOL = " + shutdownOnLaunch);
        onServerStart();
    }

    // Server stopping Event.
    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (timer != null) {
            timer.cancel();
        }
    }

    // Player joining Event.
    @SubscribeEvent
    public void onPlayerConnect(PlayerEvent.PlayerLoggedInEvent event) {
        onPlayerJoin();
    }

    // Player leaving Event.
    @SubscribeEvent
    public void onPlayerDisconnect(PlayerEvent.PlayerLoggedOutEvent event) {
        countPlayers();
    }

    // METHODS

    public static void onServerStart() {
        SleepingServerStopperMod.server = FMLCommonHandler.instance().getMinecraftServerInstance();

        if (shutdownOnLaunch) {
            countPlayers();
        }
    }

    public static void countPlayers() {
        if (server.getCurrentPlayerCount() <= 1) {
            logger.info(String.format("[SSS] Server Empty - Server will shutdown in %d minute(s)!", shutdownTime));
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
            logger.info("[SSS] Player joined - Server shutdown cancelled.");
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public static void stopper() {
        int playerCount = server.getCurrentPlayerCount();
        if (playerCount <= 0) {
            logger.info("[SSS] Server empty - Server shutting down.");
            server.initiateShutdown();
        } else {
            logger.info(String.format("[SSS] Abort shutdown - %d connected player(s).", playerCount));
        }
    }
}
