package net.madahaz.sleepingserverstopper.fabric;

//? if fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.madahaz.sleepingserverstopper.SleepingServerStopper;

public class SleepingServerStopperFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                SleepingServerStopper.onServerStarted(server, FabricLoader.getInstance().getConfigDir().toFile()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SleepingServerStopper.onServerStopping());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> SleepingServerStopper.onPlayerJoin());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SleepingServerStopper.onPlayerLeave(handler.player));
    }
}
//?}
