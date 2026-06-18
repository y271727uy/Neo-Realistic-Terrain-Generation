package com.y271727uy.rtg.event;

import com.y271727uy.rtg.AequoraMod;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = AequoraMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EventHandlerClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandlerClient.class);

    private EventHandlerClient() {
    }

    @SubscribeEvent
    public static void onCreateWorldScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CreateWorldScreen) {
            LOGGER.debug("CreateWorldScreen opened");
        }
    }
}
