package net.madahaz.sleepingserverstopper.forge;

//? if forge {
/*import net.madahaz.sleepingserverstopper.SleepingServerStopper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(SleepingServerStopper.MOD_ID)
public class SleepingServerStopperForge {
    public SleepingServerStopperForge() {
        MinecraftForge.EVENT_BUS.register(this);
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
        //? if <1.19 {
        /^SleepingServerStopper.onPlayerLeave(event.getPlayer());
        ^///?} else
        SleepingServerStopper.onPlayerLeave(event.getEntity());
    }
}
*///?}
