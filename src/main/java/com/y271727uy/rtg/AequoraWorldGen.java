package com.y271727uy.rtg;

import com.y271727uy.rtg.config.WorldGenConfig;
import com.y271727uy.rtg.init.AequoraBiomeInit;
import com.y271727uy.rtg.world.biome.BiomeAnalyzer;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ported from RTG's RTG.java main class.
 * Manages Aequora world generation lifecycle: initialization, biome analyzer setup.
 */
@Mod.EventBusSubscriber(modid = AequoraMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AequoraWorldGen {
    private static final Logger LOGGER = LoggerFactory.getLogger(AequoraWorldGen.class);

    private static boolean initialized = false;
    private static BiomeAnalyzer biomeAnalyzer;

    /**
     * Initialize world generation. Called during FMLCommonSetup.
     * Sets up the BiomeAnalyzer (including lake biomes) after realistic biomes are registered.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        LOGGER.info("Initializing Aequora world generation");
        LOGGER.debug("Biome size: {}, River size: {}, Land scheme: {}",
                WorldGenConfig.biomeSize, WorldGenConfig.riverSize, WorldGenConfig.landScheme);

        AequoraBiomeInit.init();
        biomeAnalyzer = new BiomeAnalyzer();
        biomeAnalyzer.initLakeBiomes();

        LOGGER.info("Aequora world generation initialized successfully");
    }

    /**
     * Returns the global BiomeAnalyzer instance.
     */
    public static BiomeAnalyzer getBiomeAnalyzer() {
        if (biomeAnalyzer == null) {
            init();
        }
        return biomeAnalyzer;
    }

    /**
     * Whether debug logging is enabled for world generation.
     */
    public static boolean isDebugging() {
        return WorldGenConfig.enableDebugging;
    }

    private AequoraWorldGen() {}
}
