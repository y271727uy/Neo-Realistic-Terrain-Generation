package com.y271727uy.rtg.event;

import com.y271727uy.rtg.AequoraMod;
import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.compat.ModCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = AequoraMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EventHandlerCommon {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandlerCommon.class);

    private EventHandlerCommon() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LOGGER.debug("Server started, running biome compatibility check");
        ModCompat.doBiomeCheck();
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOGGER.debug("Loaded server level {}", level.dimension().location());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RTGWorld.clear(level);
            LOGGER.debug("Unloaded server level {}", level.dimension().location());
        }
    }

}
