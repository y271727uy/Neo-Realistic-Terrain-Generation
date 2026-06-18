package com.y271727uy.rtg.api.world.terrain;

import com.y271727uy.rtg.api.util.noise.CellularNoise;
import com.y271727uy.rtg.api.util.noise.ISimplexData2D;
import com.y271727uy.rtg.api.util.noise.SimplexData2D;
import com.y271727uy.rtg.api.util.noise.SimplexNoise;
import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.api.world.terrain.heighteffect.VariableRuggednessEffect;
import net.minecraft.core.BlockPos;

@SuppressWarnings("unused")
public abstract class TerrainBase {
    private static final float MINIMUM_OCEAN_FLOOR = 20.01f;
    private static final float INV_49 = 1.0f / 49.0f;
    private static final float INV_23 = 1.0f / 23.0f;
    private static final float INV_11 = 1.0f / 11.0f;
    private static final float INV_150 = 1.0f / 150.0f;
    private static final float INV_55 = 1.0f / 55.0f;
    private static final float INV_100 = 1.0f / 100.0f;
    private static final float INV_300 = 1.0f / 300.0f;
    private static final float INV_50 = 1.0f / 50.0f;
    private static final float INV_15 = 1.0f / 15.0f;
    private static final float INV_30 = 1.0f / 30.0f;
    private static final float INV_20 = 1.0f / 20.0f;
    private static final float INV_12 = 1.0f / 12.0f;
    private static final float INV_8 = 1.0f / 8.0f;
    private static final float INV_7 = 1.0f / 7.0f;
    private static final float INV_5 = 1.0f / 5.0f;
    private static final float INV_40 = 1.0f / 40.0f;
    private static final float INV_70 = 1.0f / 70.0f;
    private static final float INV_230 = 1.0f / 230.0f;
    private static final float INV_180 = 1.0f / 180.0f;
    private static final float INV_130 = 1.0f / 130.0f;
    private static final float INV_64 = 1.0f / 64.0f;
    private static final float INV_240 = 1.0f / 240.0f;
    private static final float INV_80 = 1.0f / 80.0f;
    private static final float BLENDED_HILL_NORMALIZATION = 1.0f / 0.45f;
    private static final float BLENDED_HILL_OFFSET = 4.5f;

    protected float base;

    protected TerrainBase() {
        this(68.0f);
    }

    protected TerrainBase(float base) {
        this.base = base;
    }

    public static float blendedHillHeight(float simplex) {
        float result = simplex + 1.0f;
        result = result * result * result + 10.0f;
        result = (float) Math.pow(result, 1.0d / 3.0d);
        result = result * BLENDED_HILL_NORMALIZATION;
        return result - BLENDED_HILL_OFFSET;
    }

    public static float blendedHillHeight(float simplex, float turnAt) {
        float oneMinusTurnAt = 1.0f - turnAt;
        float adjusted = 1.0f - (1.0f - simplex) / oneMinusTurnAt;
        return blendedHillHeight(adjusted);
    }

    public static float above(float limited, float limit) {
        return limited > limit ? limited - limit : 0.0f;
    }

    public static float unsignedPower(float number, float power) {
        if (number > 0.0f) {
            return (float) Math.pow(number, power);
        }
        return -1.0f * (float) Math.pow(-number, power);
    }

    public static float hills(float x, float y, float hillStrength, RTGWorld world) {
        SimplexNoise simplex0 = world.simplexInstance(0);
        SimplexNoise simplex2 = world.simplexInstance(2);

        float m = simplex0.noise2f(x * INV_150, y * INV_150);
        m = blendedHillHeight(m, 0.2f);

        float sm = simplex2.noise2f(x * INV_55, y * INV_55);
        sm = blendedHillHeight(sm, 0.2f);
        sm *= sm * m;
        m += sm * 0.33333333f;

        return m * hillStrength;
    }

    public static float groundNoise(int x, int z, float amplitude, RTGWorld world) {
        return groundNoise((float) x, (float) z, amplitude, world);
    }

    public static float groundNoise(float x, float z, float amplitude, RTGWorld world) {
        SimplexNoise simplex0 = world.simplexInstance(0);
        SimplexNoise simplex1 = world.simplexInstance(1);
        SimplexNoise simplex2 = world.simplexInstance(2);

        float h = blendedHillHeight(simplex0.noise2f(x * INV_49, z * INV_49), 0.2f) * amplitude;
        h += blendedHillHeight(simplex1.noise2f(x * INV_23, z * INV_23), 0.2f) * amplitude * 0.5f;
        h += blendedHillHeight(simplex2.noise2f(x * INV_11, z * INV_11), 0.2f) * amplitude * 0.25f;
        return h;
    }

    public static float getTerrainBase() {
        return 68.0f;
    }

    public static float getTerrainBase(float river) {
        return 62.0f + 6.0f * river;
    }

    public static float mountainCap(float m) {
        if (m > 180.0f) {
            m = 180.0f + (m - 180.0f) * 0.9f;
            if (m > 220.0f) {
                m = 220.0f + (m - 220.0f) * 0.75f;
            }
        }
        return m;
    }

    public static float riverized(float height, float river) {
        if (height < 62.45f) {
            return height;
        }
        float heightAdjust = (height - 62.45f) * 0.1f + 0.6f;
        river = bayesianAdjustment(river, heightAdjust);
        return 62.45f + (height - 62.45f) * river;
    }

    public static float terrainBeach(int x, int z, RTGWorld world, float river, float baseHeight) {
        return riverized(baseHeight + groundNoise(x, z, 4.0f, world), river);
    }

    public static float terrainFlatLakes(int x, int z, RTGWorld world, float river, float baseHeight) {
        float ruggedNoise = world.simplexInstance(1).noise2f(
                x * VariableRuggednessEffect.INV_STANDARD_RUGGEDNESS_WAVELENGTH,
                z * VariableRuggednessEffect.INV_STANDARD_RUGGEDNESS_WAVELENGTH
        );

        ruggedNoise = blendedHillHeight(ruggedNoise);
        float h = groundNoise(x, z, 2.0f * (ruggedNoise + 1.0f), world);
        return riverized(baseHeight + h, river);
    }

    public static float terrainForest(int x, int z, RTGWorld world, float river, float baseHeight) {
        SimplexNoise simplex = world.simplexInstance(0);

        double h = simplex.noise2d(x * INV_100, z * INV_100) * 8.0d;
        h += simplex.noise2d(x * INV_30, z * INV_30) * 4.0d;
        h += simplex.noise2d(x * INV_15, z * INV_15) * 2.0d;
        h += simplex.noise2d(x * INV_7, z * INV_7);

        return riverized(baseHeight + 20.0f + (float) h, river);
    }

    public static float terrainGrasslandFlats(int x, int z, RTGWorld world, float river, float mPitch, float baseHeight) {
        SimplexNoise simplex = world.simplexInstance(0);
        float h = simplex.noise2f(x * INV_100, z * INV_100) * 7.0f;
        h += simplex.noise2f(x * INV_20, z * INV_20) * 2.0f;

        float m = simplex.noise2f(x * INV_180, z * INV_180) * 35.0f * river;
        m *= m / mPitch;

        float sm = blendedHillHeight(simplex.noise2f(x * INV_30, z * INV_30)) * 8.0f;
        sm *= Math.min(m * 0.05f, 3.75f);
        m += sm;

        return riverized(baseHeight + h + m, river);
    }

    public static float terrainGrasslandHills(int x, int z, RTGWorld world, float river, float vWidth, float vHeight, float hWidth, float hHeight, float bHeight) {
        float invVWidth = 1.0f / vWidth;
        float invHWidth = 1.0f / hWidth;

        SimplexNoise simplex0 = world.simplexInstance(0);
        SimplexNoise simplex1 = world.simplexInstance(1);

        float h = simplex0.noise2f(x * invVWidth, z * invVWidth);
        h = blendedHillHeight(h, 0.3f);

        float m = simplex1.noise2f(x * invHWidth, z * invHWidth);
        m = blendedHillHeight(m, 0.3f) * h;
        m *= m;

        h *= vHeight * river;
        m *= hHeight * river;
        h += groundNoise(x, z, 4.0f, world);

        return riverized(bHeight + h, river) + m;
    }

    public static float terrainGrasslandMountains(int x, int z, RTGWorld world, float river, float hFactor, float mFactor, float baseHeight) {
        SimplexNoise simplex0 = world.simplexInstance(0);
        float h = simplex0.noise2f(x * INV_100, z * INV_100) * hFactor;
        h += simplex0.noise2f(x * INV_20, z * INV_20) * 2.0f;

        float m = simplex0.noise2f(x * INV_230, z * INV_230) * mFactor * river;
        m *= m * 0.028571429f;
        m = m > 90.0f ? 90.0f + (m - 90.0f) * 0.6f : m;

        float c = world.simplexInstance(4).noise3f(x * INV_30, z * INV_30, 1.0f) * (m * 0.30f);

        float sm = simplex0.noise2f(x * INV_30, z * INV_30) * 8.0f + simplex0.noise2f(x * INV_8, z * INV_8);
        sm *= Math.min(m * 0.05f, 2.5f);
        m += sm + c;

        return riverized(baseHeight + h + m, river);
    }

    public static float terrainHighland(float x, float z, RTGWorld world, float river, float start, float width, float height, float baseAdjust) {
        float h = world.simplexInstance(0).noise2f(x / width, z / width) * height * river;
        h = h < start ? start + ((h - start) * 0.22222222f) : h;

        if (h < 0.0f) {
            h = 0.0f;
        }
        if (h > 0.0f) {
            float st = Math.min(h * 1.5f, 15.0f);
            h += world.simplexInstance(4).noise3f(x * INV_70, z * INV_70, 1.0f) * st;
            h *= river;
        }

        h += blendedHillHeight(world.simplexInstance(0).noise2f(x * INV_20, z * INV_20), 0.0f) * 4.0f;
        h += blendedHillHeight(world.simplexInstance(0).noise2f(x * INV_12, z * INV_12), 0.0f) * 2.0f;
        h += blendedHillHeight(world.simplexInstance(0).noise2f(x * INV_5, z * INV_5), 0.0f);

        if (h < 0.0f) {
            h *= 0.5f;
        }
        if (h < -3.0f) {
            h = (h + 3.0f) * 0.5f - 3.0f;
        }

        return getTerrainBase(river) + (h + baseAdjust) * river;
    }

    public static float terrainMarsh(int x, int z, RTGWorld world, float baseHeight, float river) {
        SimplexNoise simplex = world.simplexInstance(0);
        float h = simplex.noise2f(x * INV_130, z * INV_130) * 20.0f;
        h += simplex.noise2f(x * INV_12, z * INV_12) * 2.0f;
        h += simplex.noise2f(x * INV_18(), z * INV_18()) * 4.0f;
        h = h < 8.0f ? 0.0f : h - 8.0f;

        if (h == 0.0f) {
            h += simplex.noise2f(x * INV_20, z * INV_20) + simplex.noise2f(x * INV_5, z * INV_5);
            h *= 2.0f;
        }

        return riverized(baseHeight + h, river);
    }

    public static float terrainOcean(int x, int z, RTGWorld world, float river, float averageFloor) {
        SimplexNoise simplex = world.simplexInstance(0);
        float h = simplex.noise2f(x * INV_300, z * INV_300) * 8.0f * river;
        h += simplex.noise2f(x * INV_50, z * INV_50) * 2.0f;
        h += simplex.noise2f(x * INV_15, z * INV_15);
        return Math.max(averageFloor + h, MINIMUM_OCEAN_FLOOR);
    }

    public static float terrainPlains(int x, int z, RTGWorld world, float river, float stPitch, float stFactor, float hPitch, float hDivisor, float baseHeight) {
        SimplexNoise simplex = world.simplexInstance(0);
        float st = (simplex.noise2f(x / stPitch, z / stPitch) + 0.38f) * stFactor * river;
        st = Math.max(st, 0.2f);

        float h = simplex.noise2f(x / hPitch, z / hPitch) * st * 2.0f;
        h = h > 0.0f ? -h : h;
        h += st;
        h *= h / hDivisor;
        h += st;

        return riverized(baseHeight + h, river);
    }

    public static float terrainRollingHills(int x, int z, RTGWorld world, float river, float hillStrength, float groundNoiseAmplitudeHills, float baseHeight) {
        float groundNoise = groundNoise(x, z, groundNoiseAmplitudeHills, world);
        float m = hills(x, z, hillStrength, world);
        return riverized(groundNoise + m + baseHeight, river);
    }

    public static float terrainVanillaStyleMountains(int x, int z, RTGWorld world, float river,
                                                     float baseHeight, float massifHeight, float ridgeHeight) {
        SimplexNoise simplex0 = world.simplexInstance(0);
        SimplexNoise simplex1 = world.simplexInstance(1);
        SimplexNoise simplex2 = world.simplexInstance(2);
        VanillaMountainNoise vanilla = world.mountainNoise();

        float broadMass = vanilla != null
                ? (float) vanilla.mass(x, z)
                : simplex0.noise2f(x * INV_300, z * INV_300);
        broadMass = smoothstep(broadMass, -0.50f, 0.82f);

        float erosion = 1.0f - smoothstep(simplex1.noise2f(x * INV_300, z * INV_300), -0.20f, 0.92f);
        float range = smoothstep(broadMass * 0.76f + erosion * 0.24f, 0.18f, 0.92f);

        float ridgeBase = vanilla != null
                ? 1.0f - Math.abs((float) vanilla.jagged(x, z))
                : 1.0f - Math.abs(simplex2.noise2f(x * INV_240, z * INV_240));
        ridgeBase = smoothstep(ridgeBase, 0.70f, 0.99f);
        float ridge = ridgeBase * ridgeBase * range * 0.28f;

        float shoulder = simplex1.noise2f(x * INV_150, z * INV_150);
        shoulder = Math.max(0.0f, blendedHillHeight(shoulder, 0.25f)) * 0.10f * range;

        float local = simplex0.noise2f(x * INV_100, z * INV_100) * 0.75f;
        float height = baseHeight
                + range * massifHeight * river
                + ridge * ridgeHeight * river
                + shoulder * massifHeight * river
                + local;
        return riverized(mountainCap(height), river);
    }

    public static float terrainVolcano(int x, int z, RTGWorld world, float border, float baseHeight) {
        SimplexNoise simplex = world.simplexInstance(0);
        CellularNoise cellularNoise = world.cellularInstance(0);

        float st = 15.0f - (float) (cellularNoise.eval2D(x * 0.002f, z * 0.002f).getShortestDistance() * 42.0d)
                + simplex.noise2f(x * INV_30, z * INV_30) * 2.0f;

        float h = Math.max(st, 0.0f);
        h += (h * 0.4f) * ((h * 0.4f) * 2.0f);

        if (h > 10.0f) {
            float d2 = Math.min((h - 10.0f) * 0.66666667f, 30.0f);
            h += (float) (cellularNoise.eval2D(x * 0.04f, z * 0.04f).getShortestDistance() * d2);
        }

        h += simplex.noise2f(x * INV_18(), z * INV_18()) * 3.0f;
        h += simplex.noise2f(x * INV_8, z * INV_8) * 2.0f;

        return baseHeight + h * border;
    }

    /**
     * Towering, jagged peaks (frozen/jagged peaks) that rise several hundred blocks.
     *
     * <p>Combines a broad mountain mass with a sharpened ridged component so crests are
     * spiky rather than rounded, then applies {@link #mountainCap} so the very top tapers
     * instead of clipping flat. {@code baseHeight} sets the foot elevation.</p>
     */
    public static float terrainPeaks(int x, int z, RTGWorld world, float river, float baseHeight) {
        SimplexNoise simplex0 = world.simplexInstance(0);
        SimplexNoise simplex2 = world.simplexInstance(2);

        float mass = simplex0.noise2f(x * INV_240, z * INV_240);
        mass = blendedHillHeight(mass, 0.3f);
        float m = mass * 70.0f * river;

        float ridge = 1.0f - Math.abs(simplex2.noise2f(x * INV_130, z * INV_130));
        ridge = ridge * ridge * ridge;
        m += ridge * 78.0f * river;

        m = mountainCap(m);

        float h = groundNoise(x, z, 6.0f, world);
        h += simplex0.noise2f(x * INV_30, z * INV_30) * 8.0f;

        return riverized(baseHeight + m + h, river);
    }

    /**
     * Towering jagged peaks shaped by vanilla noise instead of the simplex {@link #terrainPeaks}.
     *
     * <p>The broad massif comes from a low-frequency vanilla {@code NormalNoise}; the spiky
     * crests come from vanilla's {@link net.minecraft.world.level.levelgen.Noises#JAGGED}
     * jaggedness, gated by the massif so spikes only appear high on the mountain. Falls back to
     * the simplex peaks before the vanilla noise is available (e.g. the seed-0 fallback world).</p>
     */
    public static float terrainVanillaPeaks(RTGWorld world, int x, int z, float river, float baseHeight) {
        VanillaMountainNoise mn = world.mountainNoise();
        if (mn == null) {
            return terrainPeaks(x, z, world, river, baseHeight);
        }
        float mass = blendedHillHeight((float) mn.mass(x, z), 0.3f);
        float m = mass * 70.0f * river;

        float ridge = 1.0f - Math.abs((float) mn.jagged(x, z));
        ridge = ridge * ridge * ridge;
        float massFactor = clamp01(mass);
        m += ridge * 80.0f * massFactor * river;

        m = mountainCap(m);
        float h = groundNoise(x, z, 6.0f, world);
        return riverized(baseHeight + m + h, river);
    }

    /**
     * Rugged mountains shaped by vanilla noise instead of {@link #terrainGrasslandMountains}.
     * Gentler than {@link #terrainVanillaPeaks}: smaller massif and milder jaggedness.
     */
    public static float terrainVanillaMountains(RTGWorld world, int x, int z, float river,
                                                float hFactor, float mFactor, float baseHeight) {
        VanillaMountainNoise mn = world.mountainNoise();
        if (mn == null) {
            return terrainGrasslandMountains(x, z, world, river, hFactor, mFactor, baseHeight);
        }
        float mass = blendedHillHeight((float) mn.mass(x, z), 0.3f);
        float m = mass * mFactor * river;

        float ridge = 1.0f - Math.abs((float) mn.jagged(x, z));
        ridge = ridge * ridge;
        float massFactor = clamp01(mass);
        m += ridge * (mFactor * 0.33f) * massFactor * river;

        m = mountainCap(m);
        float h = groundNoise(x, z, hFactor, world);
        return riverized(baseHeight + m + h, river);
    }

    private static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        return Math.min(value, 1.0f);
    }

    private static float smoothstep(float value, float edge0, float edge1) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    public static float getRiverStrength(BlockPos blockPos, RTGWorld world) {
        return getRiverStrength(blockPos, world, SimplexData2D.newDisk());
    }

    public static float getRiverStrength(BlockPos blockPos, RTGWorld world, ISimplexData2D jitterData) {
        int worldX = blockPos.getX();
        int worldZ = blockPos.getZ();
        double pX = worldX;
        double pZ = worldZ;

        world.simplexInstance(1).multiEval2D(worldX * INV_240, worldZ * INV_240, jitterData);
        pX += jitterData.getDeltaX() * world.getRiverLargeBendSize();
        pZ += jitterData.getDeltaY() * world.getRiverLargeBendSize();

        world.simplexInstance(2).multiEval2D(worldX * INV_80, worldZ * INV_80, jitterData);
        pX += jitterData.getDeltaX() * world.getRiverSmallBendSize();
        pZ += jitterData.getDeltaY() * world.getRiverSmallBendSize();

        double riverSeparation = world.getRiverSeparation();
        pX /= riverSeparation;
        pZ /= riverSeparation;

        double riverFactor = world.cellularInstance(0).eval2D(pX, pZ).interiorValue();
        riverFactor = bayesianAdjustment((float) riverFactor, 0.5f);
        double riverValleyLevel = world.getRiverValleyLevel();
        if (riverFactor > riverValleyLevel) {
            return 0.0f;
        }
        return (float) (riverFactor / riverValleyLevel - 1.0d);
    }

    public static float calcCliff(int x, int z, float[] noise, float river) {
        float cliff = 0.0f;
        int index = x * 16 + z;
        float currentNoise = noise[index];
        if (currentNoise < 64.5f && currentNoise > 61.5f && river + RTGWorld.ACTUAL_RIVER_PROPORTION > 0.97f) {
            float xUp = x < 15 ? Math.abs(currentNoise - noise[(x + 1) * 16 + z]) : 0.0f;
            float xDown = x > 0 ? Math.abs(currentNoise - noise[(x - 1) * 16 + z]) : 0.0f;
            float zUp = z < 15 ? Math.abs(currentNoise - noise[x * 16 + z + 1]) : 0.0f;
            float zDown = z > 0 ? Math.abs(currentNoise - noise[x * 16 + z - 1]) : 0.0f;
            return Math.max(Math.min(xUp, xDown), Math.min(zDown, zUp));
        }
        if (x > 0) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[(x - 1) * 16 + z]));
        }
        if (z > 0) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[x * 16 + z - 1]));
        }
        if (x < 15) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[(x + 1) * 16 + z]));
        }
        if (z < 15) {
            cliff = Math.max(cliff, Math.abs(currentNoise - noise[x * 16 + z + 1]));
        }
        return cliff;
    }

    public static float bayesianAdjustment(float probability, float multiplier) {
        if (probability >= 1.0f || probability <= 0.0f) {
            return probability;
        }
        float oneMinusProbability = 1.0f - probability;
        float newConfidence = probability * multiplier / oneMinusProbability;
        return newConfidence / (1.0f + newConfidence);
    }

    private static float INV_18() {
        return 1.0f / 18.0f;
    }

    public abstract float generateNoise(RTGWorld world, int x, int z, float border, float river);
}
