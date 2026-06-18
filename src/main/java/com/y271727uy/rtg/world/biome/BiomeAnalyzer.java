package com.y271727uy.rtg.world.biome;

import com.y271727uy.rtg.api.RTGAPI;
import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.api.world.biome.IRealisticBiome;
import com.y271727uy.rtg.world.gen.ChunkLandscape;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ported from RTG's BiomeAnalyzer.
 *
 * <p>Reconciles the per-column biome assignment with the actual generated terrain
 * height: below-sea columns become ocean/river biomes, shore-level columns become
 * beaches, and stray land/ocean biomes are replaced with the nearest biome of the
 * correct type. A weighted quadrant smoothing pass keeps those transitions smooth.</p>
 *
 * <p>Biomes are addressed through a dense integer id assigned from the biome registry,
 * shared consistently by the flag tables, the neighborhood array and the search
 * results.</p>
 */
public final class BiomeAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BiomeAnalyzer.class);

    private static final int NO_BIOME = -1;
    private static final int RIVER_FLAG = 1;
    private static final int OCEAN_FLAG = 2;
    private static final int SWAMP_FLAG = 4;
    private static final int BEACH_FLAG = 8;
    private static final int LAND_FLAG = 16;

    /** Half-width (in 8-block steps) of the biome neighborhood sampled around a chunk. */
    public static final int SAMPLE_SIZE = 8;
    /** Side length of the (square) neighborhood id array expected by {@link #newRepair}. */
    public static final int NEIGHBORHOOD_WIDTH = SAMPLE_SIZE * 2 + 5;

    // Land/water divide for biome repair. Must agree with the real water plane: water blocks
    // fill y<=SEA_LEVEL-1 (=62) with the surface at SEA_LEVEL (=63), and the water-surface
    // clamp in ChunkGeneratorAequora holds water columns' solid top at SEA_LEVEL-2 (=61). A
    // column only stands clear of the water when its (int) height reaches SEA_LEVEL (=63), so
    // anything truncating to 62 or below is below/at the water surface and must be treated as
    // water here. Using 62.5 makes columns with height in (61.5,62.5] (i.e. (int)==62) fall on
    // the water side, so they become river/ocean/lake and get flooded, instead of staying as
    // drowned grass shelves that sprout features (e.g. pumpkins) one block above the water.
    private static final float SEA = 62.5f;
    private static final float BEACH_TOP = 64.5f;

    private final Map<ResourceKey<Biome>, Integer> idByKey = new HashMap<>();
    private final List<ResourceKey<Biome>> keyById = new ArrayList<>();
    private int[] flagsById = new int[0];
    private boolean[] beachDesired = new boolean[0];
    private boolean[] landDesired = new boolean[0];
    private boolean[] oceanDesired = new boolean[0];

    private volatile IRealisticBiome[] realisticById;
    private IRealisticBiome scenicLakeBiome;
    private IRealisticBiome scenicFrozenLakeBiome;

    public BiomeAnalyzer() {
        buildIdsAndFlags();
    }

    private void buildIdsAndFlags() {
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : ForgeRegistries.BIOMES.getEntries()) {
            ResourceKey<Biome> key = entry.getKey();
            if (!idByKey.containsKey(key)) {
                idByKey.put(key, keyById.size());
                keyById.add(key);
            }
        }
        int count = keyById.size();
        flagsById = new int[count];
        for (int id = 0; id < count; id++) {
            ResourceKey<Biome> key = keyById.get(id);
            Holder<Biome> holder = ForgeRegistries.BIOMES.getHolder(key).orElse(null);
            flagsById[id] = computeFlags(holder, key);
        }
        beachDesired = filterForFlag(BEACH_FLAG);
        landDesired = filterForFlag(LAND_FLAG);
        oceanDesired = filterForFlag(OCEAN_FLAG);
    }

    private int computeFlags(Holder<Biome> holder, ResourceKey<Biome> key) {
        if (holder == null) {
            return LAND_FLAG;
        }
        if (holder.is(BiomeTags.IS_RIVER)) return RIVER_FLAG;
        if (holder.is(BiomeTags.IS_OCEAN)) return OCEAN_FLAG;
        if (holder.is(BiomeTags.IS_BEACH)) return BEACH_FLAG;
        String path = key.location().getPath();
        if (path.contains("swamp") || path.contains("mangrove")) return SWAMP_FLAG;
        return LAND_FLAG;
    }

    private boolean[] filterForFlag(int flag) {
        boolean[] result = new boolean[flagsById.length];
        for (int id = 0; id < flagsById.length; id++) {
            result[id] = (flagsById[id] & flag) != 0;
        }
        return result;
    }

    /** Must be called once realistic biomes have been registered and locked. */
    public void initLakeBiomes() {
        int count = keyById.size();
        IRealisticBiome[] resolved = new IRealisticBiome[count];
        for (int id = 0; id < count; id++) {
            resolved[id] = RTGAPI.getRealisticBiome(keyById.get(id)).orElse(null);
        }
        scenicLakeBiome = RTGAPI.getRealisticBiome(Biomes.RIVER).orElse(null);
        scenicFrozenLakeBiome = RTGAPI.getRealisticBiome(Biomes.FROZEN_RIVER).orElse(scenicLakeBiome);
        this.realisticById = resolved;
        LOGGER.info("BiomeAnalyzer ready: {} biomes indexed", count);
    }

    public boolean isReady() {
        return realisticById != null;
    }

    public int neighborhoodWidth() {
        return NEIGHBORHOOD_WIDTH;
    }

    public int idForKey(ResourceKey<Biome> key) {
        Integer id = idByKey.get(key);
        return id == null ? NO_BIOME : id;
    }

    private IRealisticBiome realistic(int id) {
        IRealisticBiome[] table = realisticById;
        return (table != null && id >= 0 && id < table.length) ? table[id] : null;
    }

    private int flags(int id) {
        return (id >= 0 && id < flagsById.length) ? flagsById[id] : LAND_FLAG;
    }

    private int flagsOf(IRealisticBiome biome) {
        if (biome == null) {
            return LAND_FLAG;
        }
        return flags(idForKey(biome.baseBiomeKey()));
    }

    /**
     * Repairs {@link ChunkLandscape#biome} in place so that biome assignment follows
     * the generated terrain height.
     *
     * @param columnIds      base biome id per chunk column (length 256, index x*16+z)
     * @param neighborhood   biome ids sampled every 8 blocks around the chunk,
     *                       {@link #NEIGHBORHOOD_WIDTH}^2 entries
     * @param landscape      landscape with noise and river already populated
     */
    public void newRepair(int[] columnIds, int[] neighborhood, ChunkLandscape landscape) {
        if (realisticById == null) {
            return;
        }
        final IRealisticBiome[] biome = landscape.biome;
        final float[] noise = landscape.noise;
        final float[] river = landscape.river;

        // 1) Rivers: carve river biomes into below-sea columns with high river strength.
        for (int i = 0; i < 256; i++) {
            int id = columnIds[i];
            IRealisticBiome base = realistic(id);
            if (base == null) {
                continue;
            }
            if (noise[i] > SEA) {
                biome[i] = base;
            } else {
                int f = flags(id);
                if (river[i] > 0.7f && (f & OCEAN_FLAG) == 0 && (f & SWAMP_FLAG) == 0) {
                    biome[i] = riverBiomeFor(id);
                } else {
                    biome[i] = base;
                }
            }
        }

        // 2) Beaches: shore-level land columns blend in the nearest beach biome.
        SmoothingSearchStatus beachSearch = new SmoothingSearchStatus(beachDesired);
        for (int i = 0; i < 256; i++) {
            float adjustedTop = riverAdjusted(BEACH_TOP, river[i]);
            boolean beachLevel = noise[i] >= SEA && noise[i] <= adjustedTop;
            if (!beachLevel || (flagsOf(biome[i]) & SWAMP_FLAG) != 0) {
                continue;
            }
            if (beachSearch.notHunted()) {
                beachSearch.hunt(neighborhood);
            }
            if (beachSearch.absent()) {
                break;
            }
            IRealisticBiome found = realistic(beachSearch.resultAt(i));
            if (found != null) {
                biome[i] = found;
            }
        }

        // 3) Land transition: above shore height, replace stray ocean/beach with land.
        SmoothingSearchStatus landSearch = new SmoothingSearchStatus(landDesired);
        for (int i = 0; i < 256; i++) {
            float adjustedTop = riverAdjusted(BEACH_TOP, river[i]);
            if (noise[i] < adjustedTop) {
                continue;
            }
            int f = flagsOf(biome[i]);
            if ((f & LAND_FLAG) != 0 || (f & SWAMP_FLAG) != 0) {
                continue;
            }
            if (landSearch.notHunted()) {
                landSearch.hunt(neighborhood);
            }
            if (landSearch.absent()) {
                break;
            }
            IRealisticBiome found = realistic(landSearch.resultAt(i));
            if (found != null) {
                biome[i] = found;
            }
        }

        // 4) Ocean transition: below sea, replace stray land with the nearest ocean.
        SmoothingSearchStatus oceanSearch = new SmoothingSearchStatus(oceanDesired);
        for (int i = 0; i < 256; i++) {
            if (noise[i] > SEA) {
                continue;
            }
            int f = flagsOf(biome[i]);
            if ((f & OCEAN_FLAG) != 0 || (f & SWAMP_FLAG) != 0 || (f & RIVER_FLAG) != 0) {
                continue;
            }
            if (oceanSearch.notHunted()) {
                oceanSearch.hunt(neighborhood);
            }
            if (oceanSearch.absent()) {
                break;
            }
            IRealisticBiome found = realistic(oceanSearch.resultAt(i));
            if (found != null) {
                biome[i] = found;
            }
        }

        // 5) Scenic lakes: any remaining below-sea land becomes a lake (river) biome.
        if (scenicLakeBiome != null) {
            for (int i = 0; i < 256; i++) {
                int f = flagsOf(biome[i]);
                if (noise[i] <= SEA
                        && (f & RIVER_FLAG) == 0
                        && (f & OCEAN_FLAG) == 0
                        && (f & SWAMP_FLAG) == 0
                        && (f & BEACH_FLAG) == 0) {
                    biome[i] = scenicLakeBiome;
                }
            }
        }
    }

    private IRealisticBiome riverBiomeFor(int id) {
        ResourceKey<Biome> key = keyById.get(id);
        String path = key.location().getPath();
        boolean cold = path.contains("snow") || path.contains("frozen") || path.contains("ice")
                || path.contains("cold") || path.contains("taiga") || path.contains("grove")
                || path.contains("peak") || path.contains("slope");
        IRealisticBiome river = cold ? scenicFrozenLakeBiome : scenicLakeBiome;
        return river != null ? river : realistic(id);
    }

    private float riverAdjusted(float top, float riverStrength) {
        if (riverStrength >= 1.0f) {
            return top;
        }
        float adjusted = Math.min(riverStrength, RTGWorld.ACTUAL_RIVER_PROPORTION);
        return top * (1.0f - adjusted) + 62.0f * adjusted;
    }

    // ---- Smoothing search: nearest biome of a type with weighted quadrant blending ----

    private static final class SmoothingSearchStatus {
        // Distance-sorted ring of neighborhood indices, computed once for the fixed width.
        private static final int[] PATTERN = createCircularPattern(NEIGHBORHOOD_WIDTH / 2f - 1, NEIGHBORHOOD_WIDTH);

        private final boolean[] desired;
        private final int[] findings = new int[9];
        private final float[] weightings = new float[9];
        private final int[] smoothed = new int[256];
        private final int[] quadrantBiome = new int[4];
        private final float[] quadrantWeight = new float[4];
        private int biomeCount;
        private boolean hunted;
        private boolean absent;

        private SmoothingSearchStatus(boolean[] desired) {
            this.desired = desired;
        }

        private boolean notHunted() {
            return !hunted;
        }

        private boolean absent() {
            return absent;
        }

        private int resultAt(int index) {
            return smoothed[index];
        }

        private void hunt(int[] neighborhood) {
            hunted = true;
            int arraySize = (int) Math.round(Math.sqrt(neighborhood.length));
            if (arraySize * arraySize != neighborhood.length) {
                throw new IllegalArgumentException("Non-square neighborhood: " + neighborhood.length);
            }
            Arrays.fill(findings, NO_BIOME);

            boolean anyFound = false;
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    int location = (xOffset + 1) * 3 + (zOffset + 1);
                    int offset = xOffset * arraySize + zOffset;
                    findings[location] = NO_BIOME;
                    weightings[location] = 2.0f;
                    for (int i = 0; i < PATTERN.length; i++) {
                        int idx = PATTERN[i] + offset;
                        if (idx < 0 || idx >= neighborhood.length) {
                            continue;
                        }
                        int b = neighborhood[idx];
                        if (b >= 0 && b < desired.length && desired[b]) {
                            findings[location] = b;
                            weightings[location] = (float) Math.sqrt(PATTERN.length) - (float) Math.sqrt(i) + 2.0f;
                            anyFound = true;
                            break;
                        }
                    }
                }
            }

            absent = !anyFound;
            if (!absent) {
                smoothQuadrant(0, 0);
                smoothQuadrant(8, 3);
                smoothQuadrant(128, 1);
                smoothQuadrant(136, 4);
            }
        }

        private void smoothQuadrant(int biomesOffset, int findingsOffset) {
            int ul = findings[findingsOffset];
            int ur = findings[findingsOffset + 3];
            int ll = findings[findingsOffset + 1];
            int lr = findings[findingsOffset + 4];

            if (ul == ur && ul == ll && ul == lr) {
                for (int x = 0; x < 8; x++) {
                    for (int z = 0; z < 8; z++) {
                        smoothed[x * 16 + z + biomesOffset] = ul;
                    }
                }
                return;
            }

            float wUL = weightings[findingsOffset];
            float wUR = weightings[findingsOffset + 3];
            float wLL = weightings[findingsOffset + 1];
            float wLR = weightings[findingsOffset + 4];
            biomeCount = 0;
            addBiome(ul);
            addBiome(ur);
            addBiome(ll);
            addBiome(lr);

            for (int x = 0; x < 8; x++) {
                float t1 = 7.0f - x;
                for (int z = 0; z < 8; z++) {
                    float t2 = 7.0f - z;
                    for (int i = 0; i < 4; i++) {
                        quadrantWeight[i] = 0.0f;
                    }
                    addWeight(ul, wUL * t1 * t2);
                    addWeight(ur, wUR * x * t2);
                    addWeight(ll, wLL * t1 * z);
                    addWeight(lr, wLR * x * z);
                    smoothed[x * 16 + z + biomesOffset] = preferred();
                }
            }
        }

        private void addBiome(int biome) {
            if (biome == NO_BIOME) {
                return;
            }
            for (int i = 0; i < biomeCount; i++) {
                if (quadrantBiome[i] == biome) {
                    return;
                }
            }
            if (biomeCount < 4) {
                quadrantBiome[biomeCount++] = biome;
            }
        }

        private void addWeight(int biome, float weight) {
            if (biome == NO_BIOME || weight <= 0.0f) {
                return;
            }
            for (int i = 0; i < biomeCount; i++) {
                if (quadrantBiome[i] == biome) {
                    quadrantWeight[i] += weight;
                    return;
                }
            }
        }

        private int preferred() {
            float best = -1.0f;
            int bestBiome = NO_BIOME;
            for (int i = 0; i < biomeCount; i++) {
                if (quadrantWeight[i] > best) {
                    best = quadrantWeight[i];
                    bestBiome = quadrantBiome[i];
                }
            }
            return bestBiome;
        }

        private static int[] createCircularPattern(float radius, int arraySize) {
            List<Integer> list = new ArrayList<>();
            int center = arraySize / 2;
            float radiusSq = radius * radius;
            for (int i = 0; i < arraySize; i++) {
                for (int j = 0; j < arraySize; j++) {
                    int dx = i - center;
                    int dz = j - center;
                    if (dx * dx + dz * dz <= radiusSq) {
                        list.add(i * arraySize + j);
                    }
                }
            }
            // Nearest-first so the search returns the closest desired biome.
            list.sort((a, b) -> {
                int ax = a / arraySize - center;
                int az = a % arraySize - center;
                int bx = b / arraySize - center;
                int bz = b % arraySize - center;
                return Integer.compare(ax * ax + az * az, bx * bx + bz * bz);
            });
            int[] pattern = new int[list.size()];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = list.get(i);
            }
            return pattern;
        }
    }
}
