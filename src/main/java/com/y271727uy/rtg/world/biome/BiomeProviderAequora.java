package com.y271727uy.rtg.world.biome;

import com.mojang.serialization.Codec;
import com.y271727uy.rtg.api.RTGAPI;
import com.y271727uy.rtg.api.world.biome.IRealisticBiome;
import com.y271727uy.rtg.init.AequoraBiomeInit;
import com.y271727uy.rtg.world.biome.realistic.AequoraRealisticBiome;
import com.y271727uy.rtg.world.biome.realistic.AequoraRealisticBiome.TerrainProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Ported from RTG's BiomeProviderRTG.
 *
 * <p>Unlike the original, the biome pool is not a hand-written list: it is built at server
 * start from the world's full biome registry, so every overworld biome - including ones
 * added by data packs and third-party mods - is eligible. Biomes are bucketed into
 * climate-coherent groups by their {@link TerrainProfile} (and temperature for oceans,
 * beaches and mountains), and the climate sampler picks a group; a low-frequency noise then
 * selects one variant from the group so variants form large patches.</p>
 */
public class BiomeProviderAequora extends BiomeSource {

    public static final Codec<BiomeProviderAequora> CODEC = Codec.unit(BiomeProviderAequora::new);

    // Shared, immutable snapshot of the resolved biome pool. Rebuilt on server start, when the
    // full (mod + datapack) biome registry is available. Volatile for safe publication to the
    // worker threads that drive chunk generation.
    private static volatile BiomePool pool;

    // World seed, folded into the climate noise so every world has a unique biome layout.
    // Set by the chunk generator once the seed is known (see AequoraChunkGenerator).
    private volatile long seed;

    public BiomeProviderAequora() {
        super();
    }

    // ---- Biome pool construction (server start) ----

    /** Rebuilds the biome pool from a world's complete biome registry. */
    public static void refreshBiomePool(Registry<Biome> registry) {
        List<Holder<Biome>> all = new ArrayList<>();
        registry.holders().forEach(all::add);
        buildPool(all);
    }

    private static BiomePool ensurePool() {
        BiomePool current = pool;
        if (current != null) {
            return current;
        }
        synchronized (BiomeProviderAequora.class) {
            if (pool != null) {
                return pool;
            }
            // Defensive fallback: the server-start event should have populated the pool, but if
            // something queried us first, build from the Forge biome registry.
            List<Holder<Biome>> all = new ArrayList<>();
            for (Map.Entry<ResourceKey<Biome>, Biome> entry : ForgeRegistries.BIOMES.getEntries()) {
                ForgeRegistries.BIOMES.getHolder(entry.getKey()).ifPresent(all::add);
            }
            buildPool(all);
            return pool;
        }
    }

    private static void buildPool(List<Holder<Biome>> allHolders) {
        List<Holder<Biome>> biomes = new ArrayList<>();
        Map<ResourceKey<Biome>, Holder<Biome>> holderByKey = new HashMap<>();
        List<TerrainProfile> profiles = new ArrayList<>();
        List<Boolean> islandOnly = new ArrayList<>();

        for (Holder<Biome> holder : allHolders) {
            ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
            if (key == null || holderByKey.containsKey(key)) {
                continue;
            }
            if (!AequoraBiomeInit.isOverworldBiome(holder, key)) {
                continue;
            }
            String path = key.location().getPath();
            if (isUndergroundExcluded(path)) {
                continue;
            }

            // Mushroom fields stay in the biome pool (so they can be placed and resolved) but are
            // never selected by the normal climate logic - only the rare ocean-island pass uses
            // them, mirroring vanilla where they only appear on isolated islands.
            TerrainProfile profile = resolveProfile(holder, key);
            biomes.add(holder);
            holderByKey.put(key, holder);
            profiles.add(profile);
            islandOnly.add(path.contains("mushroom"));
        }

        Map<String, List<Integer>> grouped = new HashMap<>();
        boolean[] prone = new boolean[biomes.size()];
        int mushroomIndex = -1;
        for (int i = 0; i < biomes.size(); i++) {
            Holder<Biome> holder = biomes.get(i);
            ResourceKey<Biome> key = holder.unwrapKey().orElseThrow();
            String path = key.location().getPath();
            if (islandOnly.get(i)) {
                if (mushroomIndex < 0) {
                    mushroomIndex = i;
                }
                continue;
            }
            String category = categoryOf(holder, key, profiles.get(i));
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(i);
            prone[i] = path.contains("peak") || path.contains("slope")
                    || path.contains("badlands") || path.contains("windswept");
        }

        Map<String, int[]> groups = new HashMap<>();
        grouped.forEach((category, list) -> {
            int[] arr = new int[list.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = list.get(i);
            }
            groups.put(category, arr);
        });

        pool = new BiomePool(List.copyOf(biomes), Map.copyOf(holderByKey), Map.copyOf(groups), prone, mushroomIndex);
    }

    /**
     * Excludes biomes that are tagged overworld but are meant for the underground (cave biomes,
     * deep dark). Without this they would be classified as plains and surface across the world,
     * e.g. sculk patches inland. Mushroom fields are intentionally NOT excluded here - they are
     * kept in the pool and placed only by the ocean-island pass.
     */
    private static boolean isUndergroundExcluded(String path) {
        return path.contains("cave")
                || path.contains("cavern")
                || path.contains("underground")
                || path.contains("deep_dark")
                || path.contains("dripstone");
    }

    /**
     * Resolves a biome's terrain profile, registering an adapter for modded/datapack biomes
     * that were not present when {@link AequoraBiomeInit} ran during common setup.
     */
    private static TerrainProfile resolveProfile(Holder<Biome> holder, ResourceKey<Biome> key) {
        if (RTGAPI.hasRealisticBiome(key)) {
            IRealisticBiome biome = RTGAPI.getRealisticBiome(key).orElse(null);
            if (biome instanceof AequoraRealisticBiome aequoraBiome) {
                return aequoraBiome.terrainProfile();
            }
        }
        TerrainProfile profile = AequoraBiomeInit.classifyBiome(holder, key);
        RTGAPI.registerIfAbsent(new AequoraRealisticBiome(key, profile));
        return profile;
    }

    /** Maps a biome to a climate-coherent selection group based on profile and temperature. */
    private static String categoryOf(Holder<Biome> holder, ResourceKey<Biome> key, TerrainProfile profile) {
        float temperature = holder.value().getBaseTemperature();
        String path = key.location().getPath();
        return switch (profile) {
            case OCEAN -> {
                if (path.contains("frozen") || temperature <= 0.05f) yield "ocean_frozen";
                if (path.contains("cold")) yield "ocean_cold";
                if (path.contains("warm") || path.contains("lukewarm")) yield "ocean_warm";
                yield "ocean";
            }
            case BEACH -> {
                if (path.contains("stony") || path.contains("gravel")) yield "stony_shore";
                if (path.contains("snow") || temperature < 0.2f) yield "beach_snowy";
                yield "beach";
            }
            case MOUNTAINS, PEAKS -> {
                boolean cold = temperature < 0.2f || path.contains("snow") || path.contains("frozen")
                        || path.contains("ice") || path.contains("jagged") || path.contains("slope");
                yield cold ? "mountains_cold" : "mountains_warm";
            }
            case RIVER -> "river";
            case PLAINS -> "plains";
            case FOREST -> "forest";
            case TAIGA -> "taiga";
            case SWAMP -> "swamp";
            case JUNGLE -> "jungle";
            case SAVANNA -> "savanna";
            case DESERT -> "desert";
            case BADLANDS -> "badlands";
            case HILLS -> "hills";
            case SNOW -> "snow";
            // Mushroom is island-only and never grouped, but the switch must be exhaustive.
            case MUSHROOM -> "plains";
        };
    }

    // ---- BiomeSource overrides ----

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return ensurePool().biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return selectBiome((x << 2) + 2, (z << 2) + 2);
    }

    /**
     * Terrain generator hook: samples the same biome logic in block space so the
     * terrain blending pass does not collapse into obvious 4x4 quart squares.
     */
    public Holder<Biome> getTerrainBiome(int blockX, int blockZ) {
        return selectBiome(blockX, blockZ);
    }

    /** Resolved biome key at a block position, used to build the analyzer neighborhood. */
    @Nullable
    public ResourceKey<Biome> getTerrainBiomeKey(int blockX, int blockZ) {
        return selectBiome(blockX, blockZ).unwrapKey().orElse(null);
    }

    /** Returns the registered holder for a biome key, or {@code null} if it is not in this source. */
    @Nullable
    public Holder<Biome> holderForKey(@Nullable ResourceKey<Biome> key) {
        return key == null ? null : ensurePool().holderByKey.get(key);
    }

    /** Mushroom-fields biome key, or {@code null} if the pool has no mushroom biome. */
    @Nullable
    public ResourceKey<Biome> mushroomKey() {
        BiomePool p = ensurePool();
        return p.mushroomIndex >= 0 ? p.biomes.get(p.mushroomIndex).unwrapKey().orElse(null) : null;
    }

    /**
     * Biome key for a normal ocean island, chosen purely from temperature/humidity so islands
     * carry pleasant lowland biomes (never oceans or towering mountains).
     */
    @Nullable
    public ResourceKey<Biome> islandBiomeKey(int blockX, int blockZ) {
        BiomePool p = ensurePool();
        int idx = classifyLandIndex(p, blockX, blockZ);
        return idx >= 0 ? p.biomes.get(idx).unwrapKey().orElse(null) : null;
    }

    /** Folds the world seed into the climate noise so each world gets a unique layout. */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    public double sampleContinentalness(int blockX, int blockZ) {
        int wx = warpedX(blockX, blockZ);
        int wz = warpedZ(blockX, blockZ);
        return fbm(wx, wz, CONT_SCALE, 13, 4);
    }

    public double sampleMountainRange(int blockX, int blockZ) {
        int wx = warpedX(blockX, blockZ);
        int wz = warpedZ(blockX, blockZ);
        return fbm(wx, wz, MOUNTAIN_RANGE_SCALE, 173, 4);
    }

    // ---- Biome selection ----

    private Holder<Biome> selectBiome(int x, int z) {
        BiomePool p = ensurePool();
        if (p.biomes.isEmpty()) {
            throw new IllegalStateException("No overworld biomes available for Aequora biome source");
        }
        int idx = classifyIndex(p, x, z);
        if (idx >= 0 && idx < p.speckleProne.length && p.speckleProne[idx]) {
            idx = deSpeckle(p, x, z, idx);
        }
        return p.biomes.get(idx);
    }

    /**
     * Replaces an isolated speckle-prone biome with the dominant biome of its surroundings.
     */
    private int deSpeckle(BiomePool p, int x, int z, int center) {
        int[] counts = new int[p.biomes.size()];
        int best = center;
        int bestCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int idx = (dx == 0 && dz == 0)
                        ? center
                        : classifyIndex(p, x + dx * DESPECKLE_RADIUS, z + dz * DESPECKLE_RADIUS);
                if (idx < 0 || idx >= counts.length) {
                    continue;
                }
                counts[idx]++;
                if (counts[idx] > bestCount) {
                    bestCount = counts[idx];
                    best = idx;
                }
            }
        }
        return counts[center] <= 3 ? best : center;
    }

    /**
     * Climate-driven biome classification, returning an index into the biome pool.
     *
     * <p>Regions are sampled from low-frequency, domain-warped fractal noise so each
     * climate zone spans hundreds of blocks with organic borders. Rivers are carved by
     * the terrain system, not produced here.</p>
     */
    private int classifyIndex(BiomePool p, int x, int z) {
        int wx = warpedX(x, z);
        int wz = warpedZ(x, z);

        double continental = fbm(wx, wz, CONT_SCALE, 13, 4);
        double temperature = fbm(wx, wz, TEMP_SCALE, 47, 3);
        double humidity = fbm(wx, wz, HUMID_SCALE, 91, 3);
        double ruggedness = fbm(wx, wz, RUGGED_SCALE, 131, 3);
        double mountainRange = fbm(wx, wz, MOUNTAIN_RANGE_SCALE, 173, 4);

        if (continental < -0.38) {
            return oceanGroup(p, temperature, x, z);
        }
        if (continental < -0.16) {
            return coastalIndex(p, ruggedness, temperature, x, z);
        }

        if (continental > -0.02 && mountainRange > 0.36) {
            return temperature < -0.20
                    ? pick(p, "mountains_cold", x, z, 211)
                    : pick(p, "mountains_warm", x, z, 223);
        }

        if (temperature < -0.42) return pick(p, "snow", x, z, 17);
        if (temperature < -0.12) return pick(p, "taiga", x, z, 29);

        if (temperature > 0.40) {
            if (humidity < -0.28) return pick(p, "desert", x, z, 37);
            if (humidity < -0.05) {
                return ruggedness > 0.18
                        ? pick(p, "badlands", x, z, 41)
                        : pick(p, "savanna", x, z, 43);
            }
            if (humidity < 0.22) return pick(p, "savanna", x, z, 43);
            return pick(p, "jungle", x, z, 53);
        }

        if (humidity > 0.45) return pick(p, "swamp", x, z, 59);
        if (ruggedness > 0.34) return pick(p, "hills", x, z, 61);
        if (humidity > 0.05) return pick(p, "forest", x, z, 67);
        return pick(p, "plains", x, z, 71);
    }

    /**
     * Land-only climate classification for ocean islands: skips the ocean/coast branches and
     * the towering mountain branch so islands always read as gentle lowland biomes.
     */
    private int classifyLandIndex(BiomePool p, int x, int z) {
        int wx = warpedX(x, z);
        int wz = warpedZ(x, z);

        double temperature = fbm(wx, wz, TEMP_SCALE, 47, 3);
        double humidity = fbm(wx, wz, HUMID_SCALE, 91, 3);
        double ruggedness = fbm(wx, wz, RUGGED_SCALE, 131, 3);

        if (temperature < -0.42) return pick(p, "snow", x, z, 17);
        if (temperature < -0.12) return pick(p, "taiga", x, z, 29);

        if (temperature > 0.40) {
            if (humidity < -0.28) return pick(p, "desert", x, z, 37);
            if (humidity < -0.05) {
                return ruggedness > 0.18
                        ? pick(p, "badlands", x, z, 41)
                        : pick(p, "savanna", x, z, 43);
            }
            if (humidity < 0.22) return pick(p, "savanna", x, z, 43);
            return pick(p, "jungle", x, z, 53);
        }

        if (humidity > 0.45) return pick(p, "swamp", x, z, 59);
        if (humidity > 0.05) return pick(p, "forest", x, z, 67);
        return pick(p, "plains", x, z, 71);
    }

    private int oceanGroup(BiomePool p, double temperature, int x, int z) {
        String group;
        if (temperature < -0.42) {
            group = "ocean_frozen";
        } else if (temperature < -0.12) {
            group = "ocean_cold";
        } else if (temperature > 0.40) {
            group = "ocean_warm";
        } else {
            group = "ocean";
        }
        return pick(p, group, x, z, 101);
    }

    private int coastalIndex(BiomePool p, double ruggedness, double temperature, int x, int z) {
        if (ruggedness > 0.42 && hasGroup(p, "stony_shore")) {
            return pick(p, "stony_shore", x, z, 113);
        }
        if (temperature < -0.30 && hasGroup(p, "beach_snowy")) {
            return pick(p, "beach_snowy", x, z, 127);
        }
        if (hasGroup(p, "beach")) {
            return pick(p, "beach", x, z, 131);
        }
        return plainsIndex(p);
    }

    private boolean hasGroup(BiomePool p, String group) {
        int[] candidates = p.groups.get(group);
        return candidates != null && candidates.length > 0;
    }

    /**
     * Picks one biome from a climate-coherent variant group using a low-frequency noise,
     * so variants form large patches. Falls back to plains if the group is empty.
     */
    private int pick(BiomePool p, String group, int x, int z, int salt) {
        int[] candidates = p.groups.get(group);
        if (candidates == null || candidates.length == 0) {
            return plainsIndex(p);
        }
        if (candidates.length == 1) {
            return candidates[0];
        }
        double v = (fbm(x, z, VARIANT_SCALE, salt, 2) + 1.0) * 0.5;
        int index = (int) (v * candidates.length);
        if (index >= candidates.length) {
            index = candidates.length - 1;
        } else if (index < 0) {
            index = 0;
        }
        return candidates[index];
    }

    private int plainsIndex(BiomePool p) {
        int[] plains = p.groups.get("plains");
        if (plains != null && plains.length > 0) {
            return plains[0];
        }
        return 0;
    }

    // ---- Climate noise ----

    private static final double CONT_SCALE = 0.0021;
    private static final double TEMP_SCALE = 0.0037;
    private static final double HUMID_SCALE = 0.0041;
    private static final double RUGGED_SCALE = 0.0052;
    private static final double MOUNTAIN_RANGE_SCALE = 0.00135;
    private static final double WARP_SCALE = 0.006;
    private static final double WARP_AMP = 75.0;
    // Neighbor sampling distance (blocks) for dissolving isolated micro-biomes.
    private static final int DESPECKLE_RADIUS = 80;
    // Frequency of the variant-selection noise: lower = larger patches of each variant.
    private static final double VARIANT_SCALE = 0.006;

    private int warpedX(int x, int z) {
        return x + (int) Math.round(fbm(x, z, WARP_SCALE, 700, 2) * WARP_AMP);
    }

    private int warpedZ(int x, int z) {
        return z + (int) Math.round(fbm(x, z, WARP_SCALE, 701, 2) * WARP_AMP);
    }

    /** Fractal (summed-octave) value noise in the range [-1, 1]. */
    private double fbm(int x, int z, double baseScale, int salt, int octaves) {
        double sum = 0.0;
        double amplitude = 1.0;
        double frequency = baseScale;
        double normalization = 0.0;
        for (int octave = 0; octave < octaves; octave++) {
            sum += valueNoise(x, z, frequency, salt + octave * 31) * amplitude;
            normalization += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / normalization;
    }

    private double valueNoise(int x, int z, double scale, int salt) {
        int cellX = (int) Math.floor(x * scale);
        int cellZ = (int) Math.floor(z * scale);
        double localX = x * scale - cellX;
        double localZ = z * scale - cellZ;

        double a = random(cellX, cellZ, salt);
        double b = random(cellX + 1, cellZ, salt);
        double c = random(cellX, cellZ + 1, salt);
        double d = random(cellX + 1, cellZ + 1, salt);
        double sx = smooth(localX);
        double sz = smooth(localZ);
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sz) * 2.0 - 1.0;
    }

    private double random(int x, int z, int salt) {
        long value = x * 341873128712L + z * 132897987541L + salt * 42317861L + seed * 6364136223846793005L;
        value ^= seed;
        value = (value ^ (value >>> 13)) * 1274126177L;
        return ((value ^ (value >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Immutable snapshot of the resolved overworld biome pool and its selection groups. */
    private record BiomePool(List<Holder<Biome>> biomes,
                             Map<ResourceKey<Biome>, Holder<Biome>> holderByKey,
                             Map<String, int[]> groups,
                             boolean[] speckleProne,
                             int mushroomIndex) {
    }
}
