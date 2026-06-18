package com.y271727uy.rtg.api;

import com.y271727uy.rtg.api.world.biome.IRealisticBiome;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Public registry for Aequora's RTG-style biome adapters.
 */
public final class RTGAPI {
    private static final Map<ResourceKey<Biome>, IRealisticBiome> REALISTIC_BIOMES = new LinkedHashMap<>();
    private static boolean realisticBiomesLocked;
    private static IRealisticBiome patchBiome;

    private RTGAPI() {
    }

    public static void addRealisticBiomes(IRealisticBiome... biomes) {
        if (realisticBiomesLocked) {
            throw new IllegalStateException("Realistic biomes are already locked.");
        }
        for (IRealisticBiome biome : biomes) {
            REALISTIC_BIOMES.put(biome.baseBiomeKey(), biome);
        }
    }

    /**
     * Registers a realistic biome adapter only if one is not already present, even after the
     * registry has been locked. Used to back-fill modded/datapack overworld biomes that are
     * not available during {@code FMLCommonSetup} but appear once a world's registries load.
     *
     * @return the existing adapter if present, otherwise the newly registered one
     */
    public static IRealisticBiome registerIfAbsent(IRealisticBiome biome) {
        IRealisticBiome existing = REALISTIC_BIOMES.get(biome.baseBiomeKey());
        if (existing != null) {
            return existing;
        }
        REALISTIC_BIOMES.put(biome.baseBiomeKey(), biome);
        return biome;
    }

    public static Optional<IRealisticBiome> getRealisticBiome(ResourceKey<Biome> biomeKey) {
        IRealisticBiome biome = REALISTIC_BIOMES.get(biomeKey);
        return Optional.ofNullable(biome != null ? biome : patchBiome);
    }

    public static Optional<IRealisticBiome> getRealisticBiome(ResourceLocation biomeId) {
        return getRealisticBiome(ResourceKey.create(Registries.BIOME, biomeId));
    }

    public static boolean hasRealisticBiome(ResourceKey<Biome> biomeKey) {
        return REALISTIC_BIOMES.containsKey(biomeKey);
    }

    public static int realisticBiomeCount() {
        return REALISTIC_BIOMES.size();
    }

    public static Collection<IRealisticBiome> realisticBiomes() {
        return Collections.unmodifiableCollection(REALISTIC_BIOMES.values());
    }

    public static void lockRealisticBiomes() {
        realisticBiomesLocked = true;
    }

    public static boolean realisticBiomesLocked() {
        return realisticBiomesLocked;
    }

    public static void setPatchBiome(IRealisticBiome biome) {
        patchBiome = biome;
    }

    public static Optional<IRealisticBiome> patchBiome() {
        return Optional.ofNullable(patchBiome);
    }
}
