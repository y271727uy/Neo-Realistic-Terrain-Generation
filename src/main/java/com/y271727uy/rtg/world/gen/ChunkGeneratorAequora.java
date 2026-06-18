package com.y271727uy.rtg.world.gen;

import com.y271727uy.rtg.AequoraWorldGen;
import com.y271727uy.rtg.api.RTGAPI;
import com.y271727uy.rtg.api.util.noise.ISimplexData2D;
import com.y271727uy.rtg.api.util.noise.SimplexData2D;
import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.api.world.biome.IRealisticBiome;
import com.y271727uy.rtg.api.world.terrain.TerrainBase;
import com.y271727uy.rtg.world.biome.BiomeAnalyzer;
import com.y271727uy.rtg.world.biome.BiomeProviderAequora;
import com.y271727uy.rtg.world.biome.realistic.AequoraRealisticBiome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ported from RTG's ChunkGeneratorRTG.
 * Aequora's terrain generator utility.
 * Generates {@link ChunkLandscape} data (height noise, biomes, river strength).
 *
 * <p>Usage: call {@link #generateLandscape(ChunkPos, RTGWorld, BiomeSource)}
 * to produce landscape data, then {@link #buildTerrain(ChunkAccess, float[])}
 * to fill a chunk with blocks.</p>
 */
public class ChunkGeneratorAequora {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkGeneratorAequora.class);

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState SAND = Blocks.SAND.defaultBlockState();
    private static final BlockState RED_SAND = Blocks.RED_SAND.defaultBlockState();
    private static final BlockState SNOW = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState MYCELIUM = Blocks.MYCELIUM.defaultBlockState();
    private static final int BIOME_BLEND_RADIUS = 2;
    private static final int BIOME_BLEND_STEP = 8;
    private static final int LANDSCAPE_CACHE_SIZE = 512;
    private static final int CAVE_SURFACE_BUFFER = 6;

    public static final int SEA_LEVEL = 63;
    public static final int MIN_Y = -64;
    public static final int GEN_DEPTH = 384;

    private static final float RIVER_BED_LEVEL = SEA_LEVEL - 6.0f;

    // Macro relief: a very low-frequency field that scales land height relative to sea level,
    // producing large basins (cherry-grove valleys, plains lowlands) and broad highland/
    // plateau belts where mountains rise much higher. Operates only above sea level so it
    // never floods terrain.
    private static final float MACRO_RELIEF_SCALE = 0.0009f;
    private static final float MACRO_RELIEF_AMP = 0.55f;
    private static final int MACRO_RELIEF_INSTANCE = 3;

    // Surface rock & snow line: steep columns expose bare stone (cliffs), while gentle slopes
    // above the snow line are capped with snow so high peaks read as snow-clad summits.
    private static final float SLOPE_STONE_THRESHOLD = 4.25f;
    private static final int SNOW_LINE_LEVEL = SEA_LEVEL + 75;

    // Ocean islands: only raise where the seabed is at least this far below sea level (open
    // ocean, not coast). Placement is driven by vanilla continentalness (see raiseOceanIsland):
    // high values give normal islands, the mushroom-fields extreme gives rarer mushroom islands.
    // Thresholds/spans are tunable to trade island frequency against rarity.
    private static final float ISLAND_MIN_DEPTH = 8.0f;
    // Lowered threshold roughly doubles the ocean area that qualifies as a normal island.
    private static final float ISLAND_CONT_THRESHOLD = 0.50f;
    private static final float ISLAND_CONT_SPAN = 0.30f;
    private static final float ISLAND_PEAK_LEVEL = SEA_LEVEL + 9.0f;
    private static final float MUSHROOM_CONT_THRESHOLD = -1.0f;
    private static final float MUSHROOM_CONT_SPAN = 0.18f;
    private static final float MUSHROOM_PEAK_LEVEL = SEA_LEVEL + 7.0f;

    // Small tributary streams: a high-frequency ridged-noise network that threads through
    // the lowlands and visually joins the large rivers and lakes. Narrow and shallow.
    private static final float SMALL_RIVER_FREQUENCY = 1.0f / 230.0f;
    // Lower threshold widens the streams (~+4 blocks); deeper bed + larger cut deepens them (~+4).
    private static final float SMALL_RIVER_THRESHOLD = 0.94f;
    private static final float SMALL_RIVER_BED_LEVEL = SEA_LEVEL - 6.0f;
    private static final float SMALL_RIVER_MAX_DEPTH = 8.0f;
    private static final float SMALL_RIVER_MAX_LAND_HEIGHT = SEA_LEVEL + 9.0f;
    private static final int SMALL_RIVER_NOISE_INSTANCE = 8;
    // Domain warp applied to the stream noise so the channels meander like vanilla rivers
    // instead of forming a regular ridged grid.
    private static final float SMALL_RIVER_WARP_FREQUENCY = 1.0f / 140.0f;
    private static final float SMALL_RIVER_WARP_AMP = 60.0f;
    private static final int SMALL_RIVER_WARP_INSTANCE_X = 5;
    private static final int SMALL_RIVER_WARP_INSTANCE_Z = 6;

    private final Map<ChunkPos, ChunkLandscape> landscapeCache = Collections.synchronizedMap(
            new LinkedHashMap<>(LANDSCAPE_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<ChunkPos, ChunkLandscape> eldest) {
                    return size() > LANDSCAPE_CACHE_SIZE;
                }
            }
    );

    public ChunkGeneratorAequora() {
    }

    // ---- Terrain building ----

    /**
     * Fills every column deterministically: stone up to the terrain height, then water up to
     * sea level for water-surface columns, then air to the top of the build height.
     *
     * <p>This runs <em>after</em> {@code super.fillFromNoise}, which has already filled the whole
     * column with vanilla overworld noise (stone, and sea/aquifer water up to y=63). We must
     * therefore overwrite the entire column - including everything <em>above</em> our terrain
     * height - rather than only stacking stone up to {@code stoneTop}. Leaving the region above
     * our surface untouched preserves vanilla's leftover stone and water there, which is exactly
     * what made terrain appear to float over a stray water sheet.</p>
     *
     * @param chunk  the chunk to fill
     * @param landscape  height and biome data (256 elements, x*16+z indexing)
     */
    public void buildTerrain(ChunkAccess chunk, ChunkLandscape landscape) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int seaTop = Math.min(SEA_LEVEL - 1, maxY - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int idx = x * 16 + z;
                int height = Mth.clamp((int) landscape.noise[idx], minY + 1, maxY - 1);
                boolean waterSurface = shouldFillSurfaceWater(landscape.biome[idx]);

                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state;
                    if (y <= height) {
                        state = STONE;
                    } else if (waterSurface && y <= seaTop) {
                        state = WATER;
                    } else {
                        // Explicitly clear above the surface so vanilla's leftover stone/water
                        // from super.fillFromNoise cannot remain floating above our terrain.
                        state = AIR;
                    }
                    chunk.setBlockState(pos, state, false);
                }
            }
        }
    }

    /**
     * Builds the Aequora main terrain, then restores inherited cave spaces (air only).
     *
     * <p>Only inherited <em>air</em> (genuine carved caves) is preserved - never the vanilla
     * pass's leftover sea/aquifer water. Restoring inherited fluid lets vanilla's ~sea-level
     * water sheet reappear partway up Aequora's taller terrain (notably the vanilla-noise
     * mountains/peaks, whose surface sits far above vanilla's), which read as land "floating"
     * over water. Restoration is also clamped to below sea level so that even inherited air
     * can never punch through the solid body of high terrain.</p>
     */
    public void buildTerrainPreservingInheritedCaves(ChunkAccess chunk, ChunkLandscape landscape) {
        BlockState[][][] inheritedCaves = captureInheritedCaves(chunk);
        buildTerrain(chunk, landscape);
        restoreInheritedCaves(chunk, landscape, inheritedCaves);
    }

    public static boolean shouldFillSurfaceWater(@Nullable IRealisticBiome biome) {
        if (biome instanceof AequoraRealisticBiome aequoraBiome) {
            return switch (aequoraBiome.terrainProfile()) {
                case OCEAN, RIVER, SWAMP -> true;
                default -> false;
            };
        }
        return false;
    }

    private BlockState[][][] captureInheritedCaves(ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        BlockState[][][] caves = new BlockState[16][maxY - minY][16];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int inheritedSurface = findInheritedSurface(chunk, pos, x, z, minY, maxY);
                // Stay below both the vanilla surface and sea level: open water near the
                // vanilla water plane is not a cave and must not be captured for restoration.
                int caveCeiling = Math.min(inheritedSurface, SEA_LEVEL) - CAVE_SURFACE_BUFFER;
                for (int y = minY; y <= caveCeiling; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    if (isCavePreservingState(state)) {
                        caves[x][y - minY][z] = state;
                    }
                }
            }
        }
        return caves;
    }

    private void restoreInheritedCaves(ChunkAccess chunk, ChunkLandscape landscape, BlockState[][][] inheritedCaves) {
        float[] noise = landscape.noise;
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int seaTop = Math.min(SEA_LEVEL - 1, maxY - 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int idx = x * 16 + z;
                int terrainTop = Mth.clamp((int) noise[idx], minY + 1, maxY - 1);
                // Water-surface columns (oceans/rivers/lakes) carry a water body up to sea level,
                // so any cave restored beneath them at/below sea level must be flooded rather than
                // left as a dry air pocket - those un-flooded pockets are the "holes" punched
                // through the water. Dry land columns keep genuine air caves.
                boolean waterColumn = shouldFillSurfaceWater(landscape.biome[idx]);
                // Clamp to sea level so inherited caves are only restored into the underground
                // body of the column; high terrain above sea level (e.g. vanilla-noise
                // mountains) is left solid instead of being hollowed by stale vanilla air.
                int caveCeiling = Math.min(terrainTop, SEA_LEVEL) - CAVE_SURFACE_BUFFER;
                for (int y = minY; y <= caveCeiling; y++) {
                    BlockState inherited = inheritedCaves[x][y - minY][z];
                    if (inherited == null) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState current = chunk.getBlockState(pos);
                    if (!isTerrainMaterial(current)) {
                        continue;
                    }
                    BlockState restored = (waterColumn && y <= seaTop && inherited.is(Blocks.AIR))
                            ? WATER
                            : inherited;
                    chunk.setBlockState(pos, restored, false);
                }
            }
        }
    }

    private static int findInheritedSurface(ChunkAccess chunk, BlockPos.MutableBlockPos pos,
                                            int x, int z, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            pos.set(x, y, z);
            if (!chunk.getBlockState(pos).is(Blocks.AIR)) {
                return y;
            }
        }
        return minY;
    }

    private static boolean isTerrainMaterial(BlockState state) {
        return isStoneLikeTerrain(state)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE);
    }

    /**
     * Only genuine carved air is preserved across the rebuild. Inherited fluid is deliberately
     * excluded: restoring vanilla's leftover sea/aquifer water is what made high terrain appear
     * to float over a stray water plane.
     */
    private static boolean isCavePreservingState(BlockState state) {
        return state.is(Blocks.AIR);
    }

    private static boolean isStoneLikeTerrain(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE);
    }

    /**
     * Places bedrock at the bottom of each column.
     * @param chunk  the chunk to finish
     * @param landscape  the landscape data
     */
    public void finishSurface(ChunkAccess chunk, ChunkLandscape landscape) {
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                IRealisticBiome biome = landscape.biome[x * 16 + z];
                if (biome == null) continue;
                int terrainTop = Mth.clamp((int) landscape.noise[x * 16 + z], MIN_Y + 1, GEN_DEPTH - 1);
                AequoraRealisticBiome.TerrainProfile profile = biome instanceof AequoraRealisticBiome aequoraBiome
                        ? aequoraBiome.terrainProfile()
                        : null;

                mpos.set(x, MIN_Y, z);
                chunk.setBlockState(mpos, BEDROCK, false);
                float slope = columnSlope(landscape.noise, x, z);
                applySurfaceColumn(chunk, mpos, x, z, terrainTop, profile, slope);
            }
        }
    }

    /** Largest height difference to the four orthogonal neighbors, i.e. local steepness. */
    private float columnSlope(float[] noise, int x, int z) {
        float center = noise[x * 16 + z];
        float max = 0.0f;
        if (x > 0) max = Math.max(max, Math.abs(center - noise[(x - 1) * 16 + z]));
        if (x < 15) max = Math.max(max, Math.abs(center - noise[(x + 1) * 16 + z]));
        if (z > 0) max = Math.max(max, Math.abs(center - noise[x * 16 + z - 1]));
        if (z < 15) max = Math.max(max, Math.abs(center - noise[x * 16 + z + 1]));
        return max;
    }

    private void applySurfaceColumn(ChunkAccess chunk, BlockPos.MutableBlockPos mpos, int x, int z,
                                    int terrainTop, @Nullable AequoraRealisticBiome.TerrainProfile profile,
                                    float slope) {
        BlockState top = GRASS;
        BlockState filler = DIRT;
        boolean sandy = false;
        boolean watery = false;

        if (profile != null) {
            switch (profile) {
                case BEACH, DESERT -> {
                    top = SAND;
                    filler = SAND;
                    sandy = true;
                }
                case OCEAN -> {
                    top = terrainTop < SEA_LEVEL - 10 ? DIRT : SAND;
                    filler = terrainTop < SEA_LEVEL - 10 ? DIRT : SAND;
                    watery = true;
                }
                case RIVER -> {
                    top = terrainTop < SEA_LEVEL - 2 ? DIRT : SAND;
                    filler = terrainTop < SEA_LEVEL - 2 ? DIRT : SAND;
                    watery = true;
                }
                case BADLANDS -> {
                    top = RED_SAND;
                    filler = RED_SAND;
                    sandy = true;
                }
                case SNOW -> {
                    top = SNOW;
                    filler = DIRT;
                }
                case MUSHROOM -> {
                    top = MYCELIUM;
                    filler = DIRT;
                }
                default -> {
                    top = GRASS;
                    filler = DIRT;
                }
            }
        } else if (terrainTop <= SEA_LEVEL + 1) {
            top = SAND;
            filler = SAND;
        }

        // Slope- and altitude-driven surface: steep faces expose bare rock (continuous cliffs
        // rather than the old speckled stone/grass patchwork), while gentle high-altitude
        // ground above the snow line is capped with snow for snow-clad summits.
        if (!sandy && !watery && terrainTop > SEA_LEVEL) {
            if (slope >= SLOPE_STONE_THRESHOLD) {
                top = STONE;
                filler = STONE;
            } else if (terrainTop > SNOW_LINE_LEVEL) {
                top = SNOW;
            }
        }

        for (int depth = 0; depth <= 3; depth++) {
            int y = terrainTop - depth;
            if (y < MIN_Y + 1) {
                break;
            }
            mpos.set(x, y, z);
            chunk.setBlockState(mpos, depth == 0 ? top : filler, false);
        }
    }

    // ---- Landscape generation ----

    /**
     * Gets (from cache) or generates the landscape for a chunk position.
     */
    public ChunkLandscape getOrCreateLandscape(ChunkPos chunkPos, @Nullable RTGWorld world,
                                               BiomeSource biomeSource, Climate.Sampler sampler) {
        return getOrCreateLandscape(chunkPos, world, biomeSource, sampler, null);
    }

    public ChunkLandscape getOrCreateLandscape(ChunkAccess chunk, @Nullable RTGWorld world) {
        return generateLandscape(chunk.getPos(), world, null, null, chunk);
    }

    public ChunkLandscape getOrCreateLandscape(ChunkAccess chunk, @Nullable RTGWorld world,
                                               BiomeSource biomeSource, Climate.Sampler sampler) {
        return getOrCreateLandscape(chunk.getPos(), world, biomeSource, sampler, chunk);
    }

    public void clearLandscapeCache() {
        this.landscapeCache.clear();
    }

    /**
     * Generates the full landscape for a chunk: river strengths, biome assignments, height noise.
     */
    public ChunkLandscape generateLandscape(ChunkPos chunkPos, @Nullable RTGWorld world,
                                            BiomeSource biomeSource, Climate.Sampler sampler) {
        return generateLandscape(chunkPos, world, biomeSource, sampler, null);
    }

    private ChunkLandscape getOrCreateLandscape(ChunkPos chunkPos, @Nullable RTGWorld world,
                                                @Nullable BiomeSource biomeSource, @Nullable Climate.Sampler sampler,
                                                @Nullable ChunkAccess chunk) {
        if (world == null || biomeSource == null || sampler == null) {
            return generateLandscape(chunkPos, world, biomeSource, sampler, chunk);
        }

        synchronized (this.landscapeCache) {
            ChunkLandscape cached = this.landscapeCache.get(chunkPos);
            if (cached != null) {
                return cached;
            }
        }

        ChunkLandscape generated = generateLandscape(chunkPos, world, biomeSource, sampler, chunk);
        synchronized (this.landscapeCache) {
            ChunkLandscape cached = this.landscapeCache.get(chunkPos);
            if (cached != null) {
                return cached;
            }
            this.landscapeCache.put(chunkPos, generated);
        }
        return generated;
    }

    private ChunkLandscape generateLandscape(ChunkPos chunkPos, @Nullable RTGWorld world,
                                            @Nullable BiomeSource biomeSource, @Nullable Climate.Sampler sampler,
                                            @Nullable ChunkAccess chunk) {
        ChunkLandscape landscape = new ChunkLandscape();
        int chunkWorldX = chunkPos.x * 16;
        int chunkWorldZ = chunkPos.z * 16;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Per-call jitter data: chunks are generated concurrently, so sharing a single
        // mutable instance across threads would corrupt river strengths.
        ISimplexData2D jitter = SimplexData2D.newDisk();

        // River strength + biome assignment per column (consumed by the surface pass
        // and BiomeAnalyzer).
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int idx = i * 16 + j;
                int wx = chunkWorldX + i;
                int wz = chunkWorldZ + j;

                if (world != null) {
                    pos.set(wx, 0, wz);
                    // Stored as positive river strength: 0 = no river, 1 = river centerline.
                    landscape.river[idx] = -TerrainBase.getRiverStrength(pos, world, jitter);
                } else {
                    landscape.river[idx] = 0.0f;
                }

                Holder<Biome> holder = getBiomeHolder(wx, wz, biomeSource, sampler, chunk);
                ResourceKey<Biome> biomeKey = holder.unwrapKey().orElse(null);
                landscape.biome[idx] = (biomeKey != null)
                        ? RTGAPI.getRealisticBiome(biomeKey).orElse(null)
                        : null;
            }
        }

        // Height is a pure function of world coordinates, so the raw height field is
        // already seamless across chunk borders. We sample one column beyond the chunk
        // on every side and smooth over that padded grid. The previous implementation
        // smoothed only the interior 14x14 of each chunk in isolation, leaving the
        // border columns at their raw values; on sloped terrain that offset between a
        // chunk's smoothed interior and its raw borders produced a visible 16x16 grid
        // of steps ("squares"). Padded smoothing eliminates those chunk seams.
        final int pad = 1;
        final int width = 16 + 2 * pad;
        float[] raw = new float[width * width];
        for (int i = -pad; i < 16 + pad; i++) {
            for (int j = -pad; j < 16 + pad; j++) {
                raw[(i + pad) * width + (j + pad)] = computeColumnHeight(
                        world, chunkWorldX + i, chunkWorldZ + j, biomeSource, sampler, chunk, jitter, pos);
            }
        }

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int ci = (i + pad) * width + (j + pad);
                float center = raw[ci];
                float neighbors = raw[ci - width] + raw[ci + width] + raw[ci - 1] + raw[ci + 1];
                landscape.noise[i * 16 + j] = center * 0.65f + neighbors * 0.0875f;
            }
        }
        erodeRuggedSlopes(landscape);

        // Columns that rose above sea level but still carry an ocean biome are ocean islands:
        // give them a land (or mushroom) biome before the analyzer runs.
        assignOceanIslandBiomes(chunkWorldX, chunkWorldZ, biomeSource, sampler, landscape);

        // Reconcile biome assignment with the generated terrain height (rivers, beaches,
        // land/ocean transitions and scenic lakes) using RTG's analyzer pass.
        repairBiomes(chunkWorldX, chunkWorldZ, biomeSource, landscape);

        // Water-surface clamp ("water wins"): on ocean/river/lake/swamp columns the solid
        // terrain must never reach the water surface, or it pokes through as a one-block
        // step above the water plane.
        //
        // Geometry: water blocks fill y<=seaTop where seaTop=SEA_LEVEL-1 (=62), so the water
        // top face sits at SEA_LEVEL (=63). buildTerrain fills stone for y<=height, putting
        // the highest solid block at exactly `height`. For the column to keep a continuous
        // sheet of water (at least the y=62 block) covering the seabed, the highest solid
        // block must stay strictly below seaTop, i.e. height<=SEA_LEVEL-2 (=61). Since the
        // height is truncated with (int) downstream, clamp the float to SEA_LEVEL-2 so
        // (int) can never reach seaTop. This single source feeds the build, surface and
        // heightmap passes alike, so they stay consistent automatically.
        final float waterSurfaceCap = SEA_LEVEL - 2.0f; // (int) -> highest solid at SEA_LEVEL-2, water covers SEA_LEVEL-1
        for (int idx = 0; idx < 256; idx++) {
            if (shouldFillSurfaceWater(landscape.biome[idx]) && landscape.noise[idx] > waterSurfaceCap) {
                landscape.noise[idx] = waterSurfaceCap;
            }
        }

        return landscape;
    }

    private void erodeRuggedSlopes(ChunkLandscape landscape) {
        for (int pass = 0; pass < 3; pass++) {
            float[] source = landscape.noise.clone();
            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    int idx = x * 16 + z;
                    if (!touchesRuggedProfile(landscape, x, z)) {
                        continue;
                    }

                    float center = source[idx];
                    float n = source[(x - 1) * 16 + z];
                    float s = source[(x + 1) * 16 + z];
                    float w = source[x * 16 + z - 1];
                    float e = source[x * 16 + z + 1];
                    float average = (n + s + w + e) * 0.25f;
                    float minNeighbor = Math.min(Math.min(n, s), Math.min(w, e));
                    float maxDrop = isRuggedProfile(landscape.biome[idx]) ? 5.5f : 4.0f;

                    float softened = center * 0.70f + average * 0.30f;
                    if (softened - minNeighbor > maxDrop) {
                        softened = minNeighbor + maxDrop;
                    }
                    landscape.noise[idx] = softened;
                }
            }
        }
    }

    private static boolean touchesRuggedProfile(ChunkLandscape landscape, int x, int z) {
        return isRuggedProfile(landscape.biome[x * 16 + z])
                || isRuggedProfile(landscape.biome[(x - 1) * 16 + z])
                || isRuggedProfile(landscape.biome[(x + 1) * 16 + z])
                || isRuggedProfile(landscape.biome[x * 16 + z - 1])
                || isRuggedProfile(landscape.biome[x * 16 + z + 1]);
    }

    private static boolean isRuggedProfile(@Nullable IRealisticBiome biome) {
        if (biome instanceof AequoraRealisticBiome aequoraBiome) {
            return switch (aequoraBiome.terrainProfile()) {
                case MOUNTAINS, PEAKS -> true;
                default -> false;
            };
        }
        return false;
    }

    private void assignOceanIslandBiomes(int chunkWorldX, int chunkWorldZ, @Nullable BiomeSource biomeSource,
                                         @Nullable Climate.Sampler sampler, ChunkLandscape landscape) {
        if (sampler == null || !(biomeSource instanceof BiomeProviderAequora aequoraSource)) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int idx = i * 16 + j;
                if (landscape.noise[idx] <= SEA_LEVEL) {
                    continue;
                }
                IRealisticBiome current = landscape.biome[idx];
                boolean ocean = current instanceof AequoraRealisticBiome aequoraBiome
                        && aequoraBiome.terrainProfile() == AequoraRealisticBiome.TerrainProfile.OCEAN;
                if (!ocean) {
                    continue;
                }

                int wx = chunkWorldX + i;
                int wz = chunkWorldZ + j;
                float vc = vanillaContinentalness(sampler, wx, wz);
                ResourceKey<Biome> key = vc <= MUSHROOM_CONT_THRESHOLD
                        ? aequoraSource.mushroomKey()
                        : aequoraSource.islandBiomeKey(wx, wz);
                if (key == null) {
                    continue;
                }
                IRealisticBiome island = RTGAPI.getRealisticBiome(key).orElse(null);
                if (island != null) {
                    landscape.biome[idx] = island;
                }
            }
        }
    }

    private void repairBiomes(int chunkWorldX, int chunkWorldZ, @Nullable BiomeSource biomeSource,
                              ChunkLandscape landscape) {
        if (!(biomeSource instanceof BiomeProviderAequora aequoraSource)) {
            return;
        }
        BiomeAnalyzer analyzer = AequoraWorldGen.getBiomeAnalyzer();
        if (analyzer == null || !analyzer.isReady()) {
            return;
        }

        int width = analyzer.neighborhoodWidth();
        int[] neighborhood = new int[width * width];
        for (int r = 0; r < width; r++) {
            int sampleX = chunkWorldX - 16 + (r - BiomeAnalyzer.SAMPLE_SIZE) * 8;
            for (int c = 0; c < width; c++) {
                int sampleZ = chunkWorldZ - 16 + (c - BiomeAnalyzer.SAMPLE_SIZE) * 8;
                ResourceKey<Biome> key = aequoraSource.getTerrainBiomeKey(sampleX, sampleZ);
                neighborhood[r * width + c] = analyzer.idForKey(key);
            }
        }

        int[] columnIds = new int[256];
        for (int i = 0; i < 256; i++) {
            IRealisticBiome biome = landscape.biome[i];
            columnIds[i] = biome != null ? analyzer.idForKey(biome.baseBiomeKey()) : -1;
        }

        analyzer.newRepair(columnIds, neighborhood, landscape);
    }

    private float computeColumnHeight(@Nullable RTGWorld world, int worldX, int worldZ,
                                      @Nullable BiomeSource biomeSource, @Nullable Climate.Sampler sampler,
                                      @Nullable ChunkAccess chunk, ISimplexData2D jitter,
                                      BlockPos.MutableBlockPos pos) {
        Holder<Biome> holder = getBiomeHolder(worldX, worldZ, biomeSource, sampler, chunk);
        ResourceKey<Biome> biomeKey = holder.unwrapKey().orElse(null);
        IRealisticBiome realistic = (biomeKey != null)
                ? RTGAPI.getRealisticBiome(biomeKey).orElse(null)
                : null;

        if (realistic == null || world == null) {
            return SEA_LEVEL + 2.0f;
        }

        pos.set(worldX, 0, worldZ);
        float river = TerrainBase.getRiverStrength(pos, world, jitter) + 1.0f;
        float height = blendedTerrainNoise(world, worldX, worldZ, river, realistic, biomeSource, sampler, chunk);
        height = applyContinentalnessLimits(world, biomeSource, height, worldX, worldZ);
        height = applyMacroRelief(world, height, worldX, worldZ);
        height = carveRiverChannel(height, river, realistic);
        height = carveSmallRivers(world, height, worldX, worldZ, realistic);
        height = raiseOceanIsland(world, height, worldX, worldZ, sampler);
        return Math.max(height, MIN_Y + 1);
    }

    /**
     * Raises a rare island out of deep ocean, driven by vanilla's continentalness noise.
     *
     * <p>Only acts well below sea level (open ocean, not coast). It reads the continentalness
     * from the vanilla overworld {@link Climate.Sampler} that already feeds this generator, so
     * island placement "inherits" vanilla's noise rather than inventing a new one:</p>
     * <ul>
     *   <li>where vanilla continentalness pokes high within our ocean -> a normal land island;</li>
     *   <li>where it hits vanilla's mushroom-fields extreme (very negative) -> a (rarer) island
     *       that the biome pass turns into mushroom fields.</li>
     * </ul>
     * The bed eases up with a smoothstep so shores are gentle. Biome assignment for the raised
     * columns happens in {@link #assignOceanIslandBiomes}.
     */
    private float raiseOceanIsland(@Nullable RTGWorld world, float height, int worldX, int worldZ,
                                   @Nullable Climate.Sampler sampler) {
        if (world == null || sampler == null || height > SEA_LEVEL - ISLAND_MIN_DEPTH) {
            return height;
        }
        float vc = vanillaContinentalness(sampler, worldX, worldZ);
        float strength;
        float peak;
        if (vc >= ISLAND_CONT_THRESHOLD) {
            strength = Mth.clamp((vc - ISLAND_CONT_THRESHOLD) / ISLAND_CONT_SPAN, 0.0f, 1.0f);
            peak = ISLAND_PEAK_LEVEL;
        } else if (vc <= MUSHROOM_CONT_THRESHOLD) {
            strength = Mth.clamp((MUSHROOM_CONT_THRESHOLD - vc) / MUSHROOM_CONT_SPAN, 0.0f, 1.0f);
            peak = MUSHROOM_PEAK_LEVEL;
        } else {
            return height;
        }
        if (strength <= 0.0f) {
            return height;
        }
        float t = strength * strength * (3.0f - 2.0f * strength);
        float relief = world.simplexInstance(0).noise2f(worldX / 40.0f, worldZ / 40.0f) * 2.5f;
        float target = Mth.lerp(t, SEA_LEVEL - ISLAND_MIN_DEPTH, peak) + relief * t;
        return Math.max(height, target);
    }

    /** Vanilla overworld continentalness at a block column, from the noise router's sampler. */
    private static float vanillaContinentalness(Climate.Sampler sampler, int worldX, int worldZ) {
        Climate.TargetPoint target = sampler.sample(
                QuartPos.fromBlock(worldX), QuartPos.fromBlock(SEA_LEVEL), QuartPos.fromBlock(worldZ));
        return Climate.unquantizeCoord(target.continentalness());
    }

    /**
     * Scales land height relative to sea level by a very low-frequency noise, carving out
     * broad basins and raising sweeping highland belts. Only affects columns above sea
     * level so it cannot turn land into water.
     */
    private float applyMacroRelief(@Nullable RTGWorld world, float height, int worldX, int worldZ) {
        if (world == null || height <= SEA_LEVEL) {
            return height;
        }
        float macro = world.simplexInstance(MACRO_RELIEF_INSTANCE)
                .noise2f(worldX * MACRO_RELIEF_SCALE, worldZ * MACRO_RELIEF_SCALE);
        float scale = 1.0f + macro * MACRO_RELIEF_AMP;
        return SEA_LEVEL + (height - SEA_LEVEL) * scale;
    }

    /**
     * Carves a continuous river channel below sea level.
     *
     * <p>RTG's erosion only reaches water depth along a sub-block-wide centerline, so on
     * the chunk grid the river aliases into disconnected puddles. River strength is a
     * smooth, continuous field that peaks at the river centerline, so we use it directly
     * to open a channel that never breaks up.</p>
     *
     * <p>The depth follows a smoothstep profile of the strength: shallow and gentle near
     * the banks, deepening continuously toward the center. This avoids the flat-bottomed
     * trough that a clamped linear mapping produces, giving a natural U-shaped bed.</p>
     *
     * @param height the terrain height so far
     * @param river  river parameter in [0,1]; 0 at the river centerline, 1 away from rivers
     */
    private float carveRiverChannel(float height, float river, @Nullable IRealisticBiome biome) {
        if (biome != null && !biome.allowRivers()) {
            return height;
        }
        float strength = 1.0f - river;
        if (strength <= 0.0f) {
            return height;
        }
        // Smoothstep: zero slope at both banks (strength->0) and center (strength->1),
        // so the bed eases in from the shore and flattens only at the very deepest point.
        float t = strength * strength * (3.0f - 2.0f * strength);
        float carved = height - (height - RIVER_BED_LEVEL) * t;
        return Math.min(height, carved);
    }

    /**
     * Carves a network of narrow, shallow tributary streams.
     *
     * <p>Uses a ridged-noise field (peaks along the noise zero-crossings) to lay down
     * continuous, branching channels - the same trick the large rivers use, but at a
     * higher frequency so the streams are only a handful of blocks wide. The depth is a
     * small relative cut (clamped to a bed level) so the streams stay vanilla-shallow,
     * and they are limited to the lowlands so they appear to feed the large rivers and
     * lakes rather than gouging trenches through hills.</p>
     */
    private float carveSmallRivers(@Nullable RTGWorld world, float height, int worldX, int worldZ,
                                   @Nullable IRealisticBiome biome) {
        if (world == null || (biome != null && !biome.allowRivers())) {
            return height;
        }
        if (height > SMALL_RIVER_MAX_LAND_HEIGHT) {
            return height;
        }
        // Domain-warp the sample coordinates so the ridge lines meander.
        float warpX = worldX + world.simplexInstance(SMALL_RIVER_WARP_INSTANCE_X)
                .noise2f(worldX * SMALL_RIVER_WARP_FREQUENCY, worldZ * SMALL_RIVER_WARP_FREQUENCY) * SMALL_RIVER_WARP_AMP;
        float warpZ = worldZ + world.simplexInstance(SMALL_RIVER_WARP_INSTANCE_Z)
                .noise2f(worldX * SMALL_RIVER_WARP_FREQUENCY, worldZ * SMALL_RIVER_WARP_FREQUENCY) * SMALL_RIVER_WARP_AMP;
        float n = world.simplexInstance(SMALL_RIVER_NOISE_INSTANCE)
                .noise2f(warpX * SMALL_RIVER_FREQUENCY, warpZ * SMALL_RIVER_FREQUENCY);
        float ridge = 1.0f - Math.abs(n);
        float strength = (ridge - SMALL_RIVER_THRESHOLD) / (1.0f - SMALL_RIVER_THRESHOLD);
        strength = Mth.clamp(strength, 0.0f, 1.0f);
        if (strength <= 0.0f) {
            return height;
        }
        float t = strength * strength * (3.0f - 2.0f * strength);
        float target = Math.max(SMALL_RIVER_BED_LEVEL, height - SMALL_RIVER_MAX_DEPTH);
        float carved = Mth.lerp(t, height, target);
        return Math.min(height, carved);
    }

    private Holder<Biome> getBiomeHolder(int worldX, int worldZ, @Nullable BiomeSource biomeSource,
                                         @Nullable Climate.Sampler sampler, @Nullable ChunkAccess chunk) {
        if (biomeSource instanceof BiomeProviderAequora aequoraBiomeSource) {
            return aequoraBiomeSource.getTerrainBiome(worldX, worldZ);
        }
        if (biomeSource != null && sampler != null) {
            return biomeSource.getNoiseBiome(QuartPos.fromBlock(worldX), QuartPos.fromBlock(SEA_LEVEL), QuartPos.fromBlock(worldZ), sampler);
        }
        if (chunk != null) {
            int localQuartX = QuartPos.fromBlock(Math.floorMod(worldX, 16));
            int localQuartZ = QuartPos.fromBlock(Math.floorMod(worldZ, 16));
            return chunk.getNoiseBiome(localQuartX, QuartPos.fromBlock(SEA_LEVEL), localQuartZ);
        }
        throw new IllegalStateException("Cannot sample biome without a biome source or chunk biome data");
    }

    private float blendedTerrainNoise(RTGWorld world, int worldX, int worldZ, float river,
                                      IRealisticBiome fallback, @Nullable BiomeSource biomeSource,
                                      @Nullable Climate.Sampler sampler, @Nullable ChunkAccess chunk) {
        if (biomeSource == null || sampler == null) {
            return fallback.generateTerrainNoise(world, worldX, worldZ, 1.0f, river);
        }

        if (isSingleBiomeNeighborhood(fallback.baseBiomeKey(), worldX, worldZ, biomeSource, sampler, chunk)) {
            return fallback.generateTerrainNoise(world, worldX, worldZ, 1.0f, river);
        }

        float totalWeight = 0.0f;
        float totalHeight = 0.0f;
        for (int dx = -BIOME_BLEND_RADIUS; dx <= BIOME_BLEND_RADIUS; dx++) {
            for (int dz = -BIOME_BLEND_RADIUS; dz <= BIOME_BLEND_RADIUS; dz++) {
                int sampleX = worldX + dx * BIOME_BLEND_STEP;
                int sampleZ = worldZ + dz * BIOME_BLEND_STEP;
                Holder<Biome> sampleHolder = getBiomeHolder(sampleX, sampleZ, biomeSource, sampler, chunk);
                ResourceKey<Biome> key = sampleHolder.unwrapKey().orElse(null);
                IRealisticBiome biome = key != null ? RTGAPI.getRealisticBiome(key).orElse(null) : null;
                if (biome == null) {
                    continue;
                }

                float distanceSq = dx * dx + dz * dz;
                float weight = 1.0f / (distanceSq + 1.0f);
                totalHeight += biome.generateTerrainNoise(world, worldX, worldZ, weight, river) * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight <= 0.0f) {
            return fallback.generateTerrainNoise(world, worldX, worldZ, 1.0f, river);
        }
        return totalHeight / totalWeight;
    }

    private boolean isSingleBiomeNeighborhood(ResourceKey<Biome> fallbackKey, int worldX, int worldZ,
                                              BiomeSource biomeSource, Climate.Sampler sampler,
                                              @Nullable ChunkAccess chunk) {
        for (int dx = -BIOME_BLEND_RADIUS; dx <= BIOME_BLEND_RADIUS; dx++) {
            for (int dz = -BIOME_BLEND_RADIUS; dz <= BIOME_BLEND_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                int sampleX = worldX + dx * BIOME_BLEND_STEP;
                int sampleZ = worldZ + dz * BIOME_BLEND_STEP;
                Holder<Biome> sampleHolder = getBiomeHolder(sampleX, sampleZ, biomeSource, sampler, chunk);
                ResourceKey<Biome> key = sampleHolder.unwrapKey().orElse(null);
                if (!fallbackKey.equals(key)) {
                    return false;
                }
            }
        }
        return true;
    }

    private float applyContinentalnessLimits(@Nullable RTGWorld world, @Nullable BiomeSource biomeSource,
                                             float height, int worldX, int worldZ) {
        if (!(biomeSource instanceof BiomeProviderAequora aequoraSource)) {
            return height;
        }
        float continentalness = (float) aequoraSource.sampleContinentalness(worldX, worldZ);
        if (world != null) {
            continentalness += world.simplexInstance(5).noise2f(worldX * 0.015625f, worldZ * 0.015625f) * 0.02f;
        }

        if (continentalness < -0.26f) {
            float ocean = Mth.clampedMap(continentalness, -0.455f, -0.26f, 40.0f, 58.0f);
            return Math.min(height, ocean);
        }
        if (continentalness < -0.07f) {
            float coastCap = Mth.clampedMap(continentalness, -0.26f, -0.07f, 58.5f, 64.5f);
            return Math.min(height, coastCap);
        }
        if (continentalness < 0.05f && height < SEA_LEVEL + 3.0f) {
            float inland = Mth.clampedMap(continentalness, -0.07f, 0.05f, 0.0f, 1.0f);
            return Mth.lerp(inland, Math.max(height, 61.5f), SEA_LEVEL + 2.0f);
        }
        return height;
    }

    public int getHeightAt(int worldX, int worldZ, @Nullable RTGWorld world,
                           BiomeSource biomeSource, Climate.Sampler sampler) {
        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16));
        ChunkLandscape landscape = getOrCreateLandscape(chunkPos, world, biomeSource, sampler, null);
        int localX = Math.floorMod(worldX, 16);
        int localZ = Math.floorMod(worldZ, 16);
        return Mth.clamp((int) landscape.noise[localX * 16 + localZ], MIN_Y + 1, GEN_DEPTH - 1);
    }
}
