package com.y271727uy.rtg.api.world;

import com.y271727uy.rtg.api.util.noise.CellularNoise;
import com.y271727uy.rtg.api.util.noise.OpenSimplexNoise;
import com.y271727uy.rtg.api.util.noise.SimplexNoise;
import com.y271727uy.rtg.api.util.noise.SpacedCellularNoise;
import com.y271727uy.rtg.api.world.terrain.VanillaMountainNoise;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-scoped terrain generation context ported from RTGWorld.
 */
public final class RTGWorld {
    public static final float SEA_LEVEL = 63.0f;
    public static final float ACTUAL_RIVER_PROPORTION = 150.0f / 1600.0f;
    public static final float RIVER_FLATTENING_ADDEND = ACTUAL_RIVER_PROPORTION / (1.0f - ACTUAL_RIVER_PROPORTION);
    public static final float RIVER_BOTTOM = 53.0f;
    public static final float LAKE_BOTTOM = 53.0f;

    private static final double RIVER_LARGE_BEND_SIZE_BASE = 140.0d;
    private static final double RIVER_SMALL_BEND_SIZE_BASE = 30.0d;
    private static final double RIVER_SEPARATION_BASE = 975.0d;
    private static final double RIVER_VALLEY_LEVEL_BASE = 140.0d / 450.0d;
    private static final float LAKE_FREQUENCY_BASE = 649.0f;
    private static final float LAKE_SHORE_LEVEL_BASE = 0.035f;
    private static final float LAKE_DEPRESSION_LEVEL = 0.15f;
    private static final float LAKE_BEND_SIZE_LARGE = 80.0f;
    private static final float LAKE_BEND_SIZE_MEDIUM = 30.0f;
    private static final float LAKE_BEND_SIZE_SMALL = 12.0f;
    private static final int SIMPLEX_INSTANCE_COUNT = 10;
    private static final int CELLULAR_INSTANCE_COUNT = 5;
    private static final float TREE_FREQUENCY_DIVISOR = 837.0f;
    private static final float TREE_MATERIALS_DIVISOR = 631.0f;
    private static final RTGWorld FALLBACK_WORLD = new RTGWorld(null, 0L);

    private static final Map<ServerLevel, RTGWorld> INSTANCE_CACHE = new WeakHashMap<>();
    private static final Map<Long, RTGWorld> SEED_CACHE = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final long seed;
    private final AequoraWorldSettings settings;
    private final SimplexNoise[] simplexNoiseInstances = new SimplexNoise[SIMPLEX_INSTANCE_COUNT];
    private final CellularNoise[] cellularNoiseInstances = new CellularNoise[CELLULAR_INSTANCE_COUNT];
    private final long chunkSeedX;
    private final long chunkSeedZ;
    private Random generatorRandom;
    // Vanilla noise used to shape mountains/peaks; set lazily once the chunk generator has the
    // world's RandomState. Null until then, in which case terrain falls back to simplex.
    private volatile VanillaMountainNoise mountainNoise;

    private RTGWorld(ServerLevel level) {
        this(level, level.getSeed());
    }

    private RTGWorld(ServerLevel level, long seed) {
        this.level = level;
        this.seed = seed;
        this.settings = AequoraWorldSettings.defaults();

        Random chunkSeedRand = new Random(this.seed());
        this.chunkSeedX = chunkSeedRand.nextLong() / 2L * 2L + 1L;
        this.chunkSeedZ = chunkSeedRand.nextLong() / 2L * 2L + 1L;

        for (int i = 0; i < SIMPLEX_INSTANCE_COUNT; i++) {
            this.simplexNoiseInstances[i] = new OpenSimplexNoise(this.seed() + i);
        }
        for (int i = 0; i < CELLULAR_INSTANCE_COUNT; i++) {
            this.cellularNoiseInstances[i] = new SpacedCellularNoise(this.seed() + i);
        }
    }

    public static RTGWorld fallback() {
        return FALLBACK_WORLD;
    }

    public static RTGWorld fromSeed(long seed) {
        return SEED_CACHE.computeIfAbsent(seed, value -> new RTGWorld(null, value));
    }

    public static RTGWorld getInstance(ServerLevel level) {
        synchronized (INSTANCE_CACHE) {
            return INSTANCE_CACHE.computeIfAbsent(level, RTGWorld::new);
        }
    }

    public static void clear(ServerLevel level) {
        synchronized (INSTANCE_CACHE) {
            INSTANCE_CACHE.remove(level);
        }
    }

    public ServerLevel level() {
        return this.level;
    }

    public LevelAccessor levelAccessor() {
        return this.level;
    }

    public AequoraWorldSettings settings() {
        return this.settings;
    }

    public long seed() {
        return this.seed;
    }

    public Random rand() {
        return this.generatorRandom;
    }

    public VanillaMountainNoise mountainNoise() {
        return this.mountainNoise;
    }

    public void setMountainNoise(VanillaMountainNoise noise) {
        this.mountainNoise = noise;
    }

    public void setRandom(Random random) {
        if (this.generatorRandom == null) {
            this.generatorRandom = random;
        }
    }

    public long getChunkSeed(int chunkX, int chunkZ) {
        return (chunkX * this.chunkSeedX) + (chunkZ * this.chunkSeedZ) ^ this.seed();
    }

    public SimplexNoise simplexInstance(int index) {
        if (index < 0 || index >= this.simplexNoiseInstances.length) {
            index = 0;
        }
        return this.simplexNoiseInstances[index];
    }

    public CellularNoise cellularInstance(int index) {
        if (index < 0 || index >= this.cellularNoiseInstances.length) {
            index = 0;
        }
        return this.cellularNoiseInstances[index];
    }

    public double getRiverLargeBendSize() {
        return RIVER_LARGE_BEND_SIZE_BASE * this.settings.riverBendMultiplier();
    }

    public double getRiverSmallBendSize() {
        return RIVER_SMALL_BEND_SIZE_BASE * this.settings.riverBendMultiplier();
    }

    public double getRiverSeparation() {
        return RIVER_SEPARATION_BASE / this.settings.riverFrequency();
    }

    public double getRiverValleyLevel() {
        return RIVER_VALLEY_LEVEL_BASE * this.settings.riverSizeMultiplier() * this.settings.riverFrequency();
    }

    public float getLakeFrequency() {
        return LAKE_FREQUENCY_BASE * this.settings.lakeFrequencyMultiplier();
    }

    public float getLakeShoreLevel() {
        return LAKE_SHORE_LEVEL_BASE * this.settings.lakeFrequencyMultiplier() * this.settings.lakeSizeMultiplier();
    }

    public float getLakeDepressionLevel() {
        return LAKE_DEPRESSION_LEVEL * this.settings.lakeFrequencyMultiplier() * this.settings.lakeSizeMultiplier();
    }

    public float getLakeBendSizeLarge() {
        return LAKE_BEND_SIZE_LARGE * this.settings.lakeShoreBend();
    }

    public float getLakeBendSizeMedium() {
        return LAKE_BEND_SIZE_MEDIUM * this.settings.lakeShoreBend();
    }

    public float getLakeBendSizeSmall() {
        return LAKE_BEND_SIZE_SMALL * this.settings.lakeShoreBend();
    }

    public SimplexNoise treeDistributionNoise() {
        return this.simplexNoiseInstances[9];
    }

    public SimplexNoise treeMaterialsNoise() {
        return this.simplexNoiseInstances[7];
    }

    public static float riverAdjustedForDepthDifference(float river) {
        if (river > ACTUAL_RIVER_PROPORTION) {
            return river;
        }
        river -= ACTUAL_RIVER_PROPORTION;
        river = river * (SEA_LEVEL - RIVER_BOTTOM) / (SEA_LEVEL - LAKE_BOTTOM);
        river += ACTUAL_RIVER_PROPORTION;
        return river;
    }

    public static float getTreeFrequencyNoiseDivisor() {
        return TREE_FREQUENCY_DIVISOR;
    }

    public static float getTreeMaterialsNoiseDivisor() {
        return TREE_MATERIALS_DIVISOR;
    }
}
