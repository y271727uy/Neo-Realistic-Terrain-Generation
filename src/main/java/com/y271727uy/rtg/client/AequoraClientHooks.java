package com.y271727uy.rtg.client;

import com.y271727uy.rtg.AequoraMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = AequoraMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AequoraClientHooks {
    private AequoraClientHooks() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = AequoraMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {
        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            Screen screen = event.getScreen();
            if (screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen) {
                // Placeholder hook for a future integration point.
            }
        }
    }
}
