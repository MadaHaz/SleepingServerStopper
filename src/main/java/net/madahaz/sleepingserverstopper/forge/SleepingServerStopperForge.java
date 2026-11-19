package net.madahaz.sleepingserverstopper.forge;

//? if forge {
/*import net.madahaz.sleepingserverstopper.SleepingServerStopper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
//? if <1.18 {
/^import net.minecraftforge.fmlserverevents.FMLServerStartedEvent;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;
^///?} else {
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
//?}
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(SleepingServerStopper.MOD_ID)
public class SleepingServerStopperForge {
    public SleepingServerStopperForge() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    //? if <1.18 {
    /^public void onServerStarted(FMLServerStartedEvent event) {
    ^///?} else
    public void onServerStarted(ServerStartedEvent event) {
        SleepingServerStopper.onServerStarted(event.getServer(), FMLPaths.CONFIGDIR.get().toFile());
    }

    @SubscribeEvent
    //? if <1.18 {
    /^public void onServerStopping(FMLServerStoppingEvent event) {
    ^///?} else
    public void onServerStopping(ServerStoppingEvent event) {
        SleepingServerStopper.onServerStopping();
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        SleepingServerStopper.onPlayerJoin();
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        // EntityEvent.getEntity() exists on every version this jar covers (1.19+ adds a bridge for it),
        // unlike PlayerEvent.getPlayer(), which 1.19 removed.
        SleepingServerStopper.onPlayerLeave(event.getEntity());
    }
}
*///?}
