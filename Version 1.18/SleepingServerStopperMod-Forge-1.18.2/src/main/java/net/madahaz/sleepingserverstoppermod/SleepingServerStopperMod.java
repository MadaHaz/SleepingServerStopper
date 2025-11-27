package net.madahaz.sleepingserverstoppermod;

import com.mojang.logging.LogUtils;
import net.madahaz.sleepingserverstoppermod.config.SleepingServerStopperModCommonConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SleepingServerStopperMod.MODID)
public class SleepingServerStopperMod
{
    public static final String MODID = "sleepingserverstoppermod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private int shutdownTime;
    private boolean shutdownOnLaunch;
    private static MinecraftServer server;
    private static Timer timer;

    public SleepingServerStopperMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SleepingServerStopperModCommonConfig.SPEC, "sleepingserverstoppermod-common.toml");

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    // Server started Event.
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        shutdownTime = SleepingServerStopperModCommonConfig.SHUTDOWN_TIME_IN_MINUTES.get();
        shutdownOnLaunch = SleepingServerStopperModCommonConfig.SHUTDOWN_SERVER_ON_LAUNCH.get();
        LOGGER.info("[SSS] TIME = " + shutdownTime);
        LOGGER.info("[SSS] BOOL = " + shutdownOnLaunch);
        onServerStart(event.getServer());
    }


    // Server stopping Event.
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
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

    public void onServerStart(MinecraftServer server) {
        SleepingServerStopperMod.server = server;

        if (shutdownOnLaunch) {
            countPlayers();
        }
    }

    public void countPlayers() {
        if (server.getPlayerCount() <= 1) {
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
        if (server.getPlayerCount() <= 1 & timer != null) {
            LOGGER.info("[SSS] Player joined - Server shutdown cancelled.");
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public static void stopper() {
        int playerCount = server.getPlayerCount();
        if (playerCount <= 0) {
            LOGGER.info("[SSS] Server empty - Server shutting down.");
            server.halt(true);
        } else {
            LOGGER.info(String.format("[SSS} Abort shutdown - %d connected player(s).", playerCount));
        }
    }
}
