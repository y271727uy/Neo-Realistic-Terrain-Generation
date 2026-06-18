package com.y271727uy.rtg;

import com.mojang.logging.LogUtils;
import com.y271727uy.rtg.config.Config;
import com.y271727uy.rtg.config.WorldGenConfig;
import com.y271727uy.rtg.compat.ModCompat;
import com.y271727uy.rtg.data.AequoraRegistrate;
import com.y271727uy.rtg.world.biome.BiomeProviderAequora;
import com.y271727uy.rtg.world.gen.AequoraChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(AequoraMod.MODID)
public class AequoraMod {
    public static final String MODID = "rtg";
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final AequoraRegistrate REGISTRATE = AequoraRegistrate.create(MODID);

    public AequoraMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::registerCodecs);
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WorldGenConfig.SPEC, "rtg-worldgen.toml");
    }

    private void registerCodecs(final RegisterEvent event) {
        event.register(Registries.CHUNK_GENERATOR,
                new ResourceLocation(MODID, "rtg"),
                () -> AequoraChunkGenerator.CODEC);
        event.register(Registries.BIOME_SOURCE,
                new ResourceLocation(MODID, "rtg"),
                () -> BiomeProviderAequora.CODEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Neo Realistic Terrain Generation common setup complete");
        ModCompat.init();
        AequoraWorldGen.init();
    }

    /**
     * Builds the Aequora biome pool from the server's full biome registry, which (unlike
     * common setup) includes every data pack and third-party mod overworld biome.
     */
    @SubscribeEvent
    public void onServerAboutToStart(final ServerAboutToStartEvent event) {
        net.minecraft.core.Registry<Biome> biomeRegistry =
                event.getServer().registryAccess().registryOrThrow(Registries.BIOME);
        BiomeProviderAequora.refreshBiomePool(biomeRegistry);
        LOGGER.info("Neo Realistic Terrain Generation biome pool refreshed from server biome registry");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
    }
}
