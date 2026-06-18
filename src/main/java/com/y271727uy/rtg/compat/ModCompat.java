package com.y271727uy.rtg.compat;

import com.y271727uy.rtg.api.RTGAPI;
import com.y271727uy.rtg.api.world.biome.IRealisticBiome;
import com.y271727uy.rtg.config.WorldGenConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ModCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModCompat.class);

    private static final int ID_LENGTH = 3;
    private static final int NAME_LENGTH = 32;
    private static final int RESLOC_LENGTH = 50;
    private static final int BIOME_NAME_LENGTH = 24;
    private static final int BIOME_CLASS_LENGTH = 32;
    private static final int BIOME_RESLOC_LENGTH = 44;
    private static final int BEACH_NAME_LENGTH = 24;

    private ModCompat() {
    }

    public static void init() {
        Mods.init();
        if (WorldGenConfig.enableDebugging) {
            doBiomeCheck();
        }
    }

    public static void doBiomeCheck() {
        List<ResourceLocation> supported = RTGAPI.realisticBiomes().stream()
                .map(IRealisticBiome::baseBiomeId)
                .toList();

        List<Biome> unsupportedBiomes = ForgeRegistries.BIOMES.getValues().stream()
                .filter(biome -> {
                    ResourceLocation key = ForgeRegistries.BIOMES.getKey(biome);
                    return key != null && !supported.contains(key);
                })
                .sorted(Comparator.comparing(biome -> Objects.requireNonNullElse(ForgeRegistries.BIOMES.getKey(biome), new ResourceLocation("minecraft", "unknown")).toString()))
                .toList();

        if (unsupportedBiomes.isEmpty()) {
            LOGGER.debug("Aequora biome check: no unsupported biomes found");
            return;
        }

        LOGGER.warn("Aequora biome check: {} unsupported biomes detected", unsupportedBiomes.size());
        for (Biome biome : unsupportedBiomes) {
            ResourceLocation key = ForgeRegistries.BIOMES.getKey(biome);
            LOGGER.warn("  {} | {}", biome.getClass().getSimpleName(), key != null ? key : "unknown");
        }
    }

    public enum Mods {
        abyssalcraft,
        biomesoplenty("biomesoplenty"),
        byg("biomesyougo"),
        explorercraft,
        floricraft,
        geographicraft,
        minecraft,
        mistbiomes,
        novamterram("novamterram"),
        plants2,
        terralith,
        traverse,
        twilightforest,
        valoegheses_be("zoesteria");

        private final String prettyName;
        private boolean loaded;

        Mods() {
            this("");
        }

        Mods(String prettyName) {
            this.prettyName = prettyName.isEmpty() ? name() : prettyName;
        }

        private static void init() {
            Arrays.stream(values()).forEach(mod -> mod.loaded = ModList.get().isLoaded(mod.name()));
        }

        public boolean isLoaded() {
            return this.loaded;
        }

        public ResourceLocation getResourceLocation(String biomePath) {
            return new ResourceLocation(name().toLowerCase(Locale.ROOT), biomePath);
        }

        public String getPrettyName() {
            return this.prettyName;
        }
    }
}
