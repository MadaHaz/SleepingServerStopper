package net.madahaz.sleepingserverstopper.neoforge;

//? if neoforge {
/*import net.madahaz.sleepingserverstopper.SleepingServerStopper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(SleepingServerStopper.MOD_ID)
public class SleepingServerStopperNeoForge {
    public SleepingServerStopperNeoForge() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        SleepingServerStopper.onServerStarted(event.getServer(), FMLPaths.CONFIGDIR.get().toFile());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SleepingServerStopper.onServerStopping();
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        SleepingServerStopper.onPlayerJoin();
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        SleepingServerStopper.onPlayerLeave(event.getEntity());
    }
}
*///?}
