package com.y271727uy.rtg.world.biome.realistic;

import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.api.world.biome.RealisticBiomeBase;
import com.y271727uy.rtg.api.world.terrain.TerrainBase;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class AequoraRealisticBiome extends RealisticBiomeBase {
    private final TerrainProfile profile;

    public AequoraRealisticBiome(ResourceKey<Biome> biomeKey, TerrainProfile profile) {
        super(biomeKey);
        this.profile = profile;
    }

    @Override
    public boolean allowRivers() {
        return this.profile != TerrainProfile.OCEAN;
    }

    @Override
    public boolean allowScenicLakes() {
        return this.profile != TerrainProfile.OCEAN && this.profile != TerrainProfile.RIVER;
    }

    public TerrainProfile terrainProfile() {
        return this.profile;
    }

    @Override
    protected TerrainBase initTerrain() {
        return new ProfileTerrain();
    }

    public enum TerrainProfile {
        PLAINS,
        FOREST,
        TAIGA,
        HILLS,
        MOUNTAINS,
        PEAKS,
        DESERT,
        SWAMP,
        JUNGLE,
        SAVANNA,
        BADLANDS,
        BEACH,
        RIVER,
        OCEAN,
        SNOW,
        MUSHROOM
    }

    private final class ProfileTerrain extends TerrainBase {
        @Override
        public float generateNoise(RTGWorld world, int x, int z, float border, float river) {
            return switch (profile) {
                case PLAINS -> TerrainBase.terrainPlains(x, z, world, river, 160.0f, 6.0f, 60.0f, 16.0f, 64.0f);
                case FOREST -> TerrainBase.terrainRollingHills(x, z, world, river, 9.0f, 3.0f, 68.0f);
                case TAIGA -> TerrainBase.terrainRollingHills(x, z, world, river, 14.0f, 4.0f, 70.0f);
                case HILLS -> TerrainBase.terrainGrasslandHills(x, z, world, river, 160.0f, 16.0f, 70.0f, 22.0f, 68.0f);
                case MOUNTAINS -> TerrainBase.terrainVanillaStyleMountains(x, z, world, river, 72.0f, 36.0f, 8.0f);
                case PEAKS -> TerrainBase.terrainVanillaStyleMountains(x, z, world, river, 78.0f, 52.0f, 16.0f);
                case DESERT -> TerrainBase.terrainPlains(x, z, world, river, 140.0f, 5.0f, 50.0f, 14.0f, 64.0f);
                case SWAMP -> TerrainBase.terrainMarsh(x, z, world, 62.5f, river);
                case JUNGLE -> TerrainBase.terrainForest(x, z, world, river, 65.0f);
                case SAVANNA -> TerrainBase.terrainGrasslandFlats(x, z, world, river, 38.0f, 66.0f);
                case BADLANDS -> TerrainBase.terrainGrasslandHills(x, z, world, river, 180.0f, 18.0f, 90.0f, 24.0f, 70.0f);
                case BEACH -> TerrainBase.terrainBeach(x, z, world, river, 62.5f);
                case RIVER -> TerrainBase.riverized(61.5f + TerrainBase.groundNoise(x, z, 1.0f, world), river);
                case OCEAN -> TerrainBase.terrainOcean(x, z, world, river, 38.0f);
                case SNOW -> TerrainBase.terrainRollingHills(x, z, world, river, 8.0f, 2.5f, 66.0f);
                case MUSHROOM -> TerrainBase.terrainPlains(x, z, world, river, 150.0f, 5.0f, 55.0f, 16.0f, 66.0f);
            };
        }
    }
}
