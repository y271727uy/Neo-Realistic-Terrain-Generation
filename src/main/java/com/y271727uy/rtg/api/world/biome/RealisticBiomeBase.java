package com.y271727uy.rtg.api.world.biome;

import com.y271727uy.rtg.api.util.noise.ISimplexData2D;
import com.y271727uy.rtg.api.util.noise.SimplexData2D;
import com.y271727uy.rtg.api.util.noise.VoronoiResult;
import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.api.world.terrain.TerrainBase;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public abstract class RealisticBiomeBase implements IRealisticBiome {
    private static final double INV_240 = 1.0d / 240.0d;
    private static final double INV_80 = 1.0d / 80.0d;
    private static final double INV_30 = 1.0d / 30.0d;

    private final ResourceKey<Biome> baseBiomeKey;
    private final TerrainBase terrain;

    protected RealisticBiomeBase(ResourceKey<Biome> baseBiomeKey) {
        this.baseBiomeKey = baseBiomeKey;
        this.terrain = initTerrain();
    }

    @Override
    public ResourceKey<Biome> baseBiomeKey() {
        return this.baseBiomeKey;
    }

    @Override
    public TerrainBase terrain() {
        return this.terrain;
    }

    @Override
    public float generateTerrainNoise(RTGWorld world, int x, int z, float border, float river) {
        if (!allowRivers()) {
            float borderForRiver = Math.min(border * 2.0f, 1.0f);
            river = 1.0f - (1.0f - borderForRiver) * (1.0f - river);
            return this.terrain.generateNoise(world, x, z, border, river);
        }

        float lakeStrength = lakePressure(
                world,
                x,
                z,
                border,
                world.getLakeFrequency(),
                world.getLakeBendSizeLarge(),
                world.getLakeBendSizeMedium(),
                world.getLakeBendSizeSmall()
        );
        float adjustedLake = lakeToRiverProportions(lakeStrength, world.getLakeShoreLevel(), world.getLakeDepressionLevel());
        river = Math.max(0.0f, RTGWorld.riverAdjustedForDepthDifference(river));

        if (adjustedLake < RTGWorld.ACTUAL_RIVER_PROPORTION) {
            adjustedLake = Math.max(0.0f, (adjustedLake - RTGWorld.ACTUAL_RIVER_PROPORTION) * 2.0f + RTGWorld.ACTUAL_RIVER_PROPORTION);
        }

        float combinedRiver;
        if (river < 1.0f && adjustedLake < 1.0f) {
            float leastLowering = Math.min(adjustedLake, river);
            float denominator = (1.0f - river) / river + (1.0f - adjustedLake) / adjustedLake;
            combinedRiver = 1.0f / (denominator + 1.0f);
            combinedRiver = (combinedRiver + leastLowering) * 0.5f;
        } else {
            combinedRiver = Math.min(adjustedLake, river);
        }

        float invertedRiver = 1.0f - combinedRiver;
        invertedRiver = invertedRiver * (invertedRiver / (invertedRiver + 0.05f) * 1.05f);
        combinedRiver = 1.0f - invertedRiver;

        float riverFlattening = Math.max(0.0f, combinedRiver * (1.0f + RTGWorld.RIVER_FLATTENING_ADDEND) - RTGWorld.RIVER_FLATTENING_ADDEND);
        float terrainNoise = this.terrain.generateNoise(world, x, z, border, riverFlattening);
        return erodedNoise(world, x, z, combinedRiver, terrainNoise);
    }

    @Override
    public float lakePressure(RTGWorld world, int x, int z, float border, float lakeInterval, float largeBendSize, float mediumBendSize, float smallBendSize) {
        if (!allowScenicLakes()) {
            return 1.0f;
        }

        double pX = x;
        double pZ = z;
        ISimplexData2D jitterData = SimplexData2D.newDisk();

        world.simplexInstance(1).multiEval2D(x * INV_240, z * INV_240, jitterData);
        pX += jitterData.getDeltaX() * largeBendSize;
        pZ += jitterData.getDeltaY() * largeBendSize;

        world.simplexInstance(0).multiEval2D(x * INV_80, z * INV_80, jitterData);
        pX += jitterData.getDeltaX() * mediumBendSize;
        pZ += jitterData.getDeltaY() * mediumBendSize;

        world.simplexInstance(4).multiEval2D(x * INV_30, z * INV_30, jitterData);
        pX += jitterData.getDeltaX() * smallBendSize;
        pZ += jitterData.getDeltaY() * smallBendSize;

        VoronoiResult lakeResults = world.cellularInstance(0).eval2D(pX / lakeInterval, pZ / lakeInterval);
        return (float) (1.0d - lakeResults.interiorValue());
    }

    protected float erodedNoise(RTGWorld world, int x, int z, float river, float biomeHeight) {
        float riverFlattening = 1.0f - river;
        riverFlattening -= 1.0f - RTGWorld.ACTUAL_RIVER_PROPORTION;

        if (riverFlattening < 0.0f || biomeHeight <= RTGWorld.LAKE_BOTTOM) {
            return biomeHeight;
        }

        riverFlattening /= RTGWorld.ACTUAL_RIVER_PROPORTION;
        float r = 1.0f - riverFlattening;

        if (r < 1.0f) {
            float irregularity = world.simplexInstance(0).noise2f(x / 12.0f, z / 12.0f) * 2.0f
                    + world.simplexInstance(0).noise2f(x / 8.0f, z / 8.0f);
            irregularity *= 1.0f + r;
            float lakeBottomWithIrregularity = RTGWorld.LAKE_BOTTOM + irregularity;
            return biomeHeight * r + lakeBottomWithIrregularity * (1.0f - r);
        }

        return biomeHeight;
    }

    protected float lakeToRiverProportions(float pressure, float shoreLevel, float topLevel) {
        if (pressure > topLevel) {
            return 1.0f;
        }
        if (pressure < shoreLevel) {
            return (pressure / shoreLevel) * RTGWorld.ACTUAL_RIVER_PROPORTION;
        }
        float proportion = (pressure - shoreLevel) / (topLevel - shoreLevel);
        return RTGWorld.ACTUAL_RIVER_PROPORTION + proportion * (1.0f - RTGWorld.ACTUAL_RIVER_PROPORTION);
    }

    protected abstract TerrainBase initTerrain();
}
