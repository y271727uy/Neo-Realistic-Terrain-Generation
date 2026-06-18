package com.y271727uy.rtg.api.world.biome;

import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.api.world.terrain.TerrainBase;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public interface IRealisticBiome {
    ResourceKey<Biome> baseBiomeKey();

    default ResourceLocation baseBiomeId() {
        return baseBiomeKey().location();
    }

    TerrainBase terrain();

    float generateTerrainNoise(RTGWorld world, int x, int z, float border, float river);

    float lakePressure(RTGWorld world, int x, int z, float border, float lakeInterval, float largeBendSize, float mediumBendSize, float smallBendSize);

    default float riverStrength(RTGWorld world, BlockPos blockPos) {
        return TerrainBase.getRiverStrength(blockPos, world);
    }

    default double waterLakeMultiplier() {
        return 1.0d;
    }

    default double lavaLakeMultiplier() {
        return 0.0d;
    }

    default boolean allowRivers() {
        return true;
    }

    default boolean allowScenicLakes() {
        return true;
    }
}
