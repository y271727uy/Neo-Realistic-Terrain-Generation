package com.y271727uy.rtg.world.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.y271727uy.rtg.api.world.RTGWorld;
import com.y271727uy.rtg.api.world.biome.IRealisticBiome;
import com.y271727uy.rtg.api.world.terrain.VanillaMountainNoise;
import com.y271727uy.rtg.world.biome.BiomeProviderAequora;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class AequoraChunkGenerator extends NoiseBasedChunkGenerator {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    public static final Codec<AequoraChunkGenerator> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(AequoraChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
            ).apply(instance, instance.stable(AequoraChunkGenerator::new))
    );

    private final Holder<NoiseGeneratorSettings> settings;
    private volatile long worldSeed;
    private final ChunkGeneratorAequora terrain = new ChunkGeneratorAequora();
    // Built once from the world's RandomState; shapes mountains/peaks with vanilla noise.
    private volatile VanillaMountainNoise vanillaMountainNoise;

    public AequoraChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.settings = settings;
    }

    @Override
    protected Codec<? extends NoiseBasedChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(Executor executor, RandomState randomState, Blender blender,
                                                       StructureManager structureManager, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            applyBiomeSeed(this.worldSeed);
            int surfaceQuartY = QuartPos.fromBlock(ChunkGeneratorAequora.SEA_LEVEL);
            BiomeSource source = this.getBiomeSource();
            if (source instanceof BiomeProviderAequora aequoraSource) {
                RTGWorld world = this.worldSeed == 0L ? RTGWorld.fallback() : RTGWorld.fromSeed(this.worldSeed);
                this.applyMountainNoise(randomState, world);
                ChunkLandscape landscape = this.terrain.getOrCreateLandscape(chunk, world, source, randomState.sampler());
                chunk.fillBiomesFromNoise((x, y, z, sampler) -> {
                    int localX = QuartPos.toBlock(x) & 15;
                    int localZ = QuartPos.toBlock(z) & 15;
                    IRealisticBiome biome = landscape.biome[localX * 16 + localZ];
                    if (biome != null) {
                        ResourceKey<Biome> key = biome.baseBiomeKey();
                        Holder<Biome> holder = aequoraSource.holderForKey(key);
                        if (holder != null) {
                            return holder;
                        }
                    }
                    return source.getNoiseBiome(x, surfaceQuartY, z, sampler);
                }, randomState.sampler());
                return chunk;
            }
            chunk.fillBiomesFromNoise((x, y, z, sampler) ->
                    source.getNoiseBiome(x, surfaceQuartY, z, sampler), randomState.sampler());
            return chunk;
        }, executor);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structureManager, ChunkAccess chunk) {
        return super.fillFromNoise(executor, blender, random, structureManager, chunk).thenApply(generatedChunk -> {
            applyBiomeSeed(this.worldSeed);
            RTGWorld world = this.worldSeed == 0L ? RTGWorld.fallback() : RTGWorld.fromSeed(this.worldSeed);
            this.applyMountainNoise(random, world);
            ChunkLandscape landscape = this.terrain.getOrCreateLandscape(generatedChunk, world, this.getBiomeSource(), random.sampler());
            this.terrain.buildTerrainPreservingInheritedCaves(generatedChunk, landscape);
            this.terrain.finishSurface(generatedChunk, landscape);
            return generatedChunk;
        });
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int terrainHeight = this.getAequoraHeight(x, z, random);
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int seaTop = Math.min(ChunkGeneratorAequora.SEA_LEVEL - 1, maxY - 1);
        boolean waterSurface = this.shouldUseWaterSurface(x, z, random);

        int top = Math.min(maxY - 1, waterSurface ? Math.max(terrainHeight, seaTop) : terrainHeight);
        for (int y = top; y >= minY; y--) {
            BlockState state = stateForY(y, terrainHeight, seaTop, waterSurface);
            if (type.isOpaque().test(state)) {
                return y + 1;
            }
        }
        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        int minY = height.getMinBuildHeight();
        int maxY = height.getMaxBuildHeight();
        int terrainHeight = this.getAequoraHeight(x, z, random);
        int seaTop = Math.min(ChunkGeneratorAequora.SEA_LEVEL - 1, maxY - 1);
        boolean waterSurface = this.shouldUseWaterSurface(x, z, random);
        BlockState[] states = new BlockState[maxY - minY];

        for (int y = minY; y < maxY; y++) {
            states[y - minY] = stateForY(y, terrainHeight, seaTop, waterSurface);
        }

        return new NoiseColumn(minY, states);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
        super.applyCarvers(level, seed, random, biomeManager, structureManager, chunk, step);
        // Vanilla carvers gouge ravines/caves through terrain we built ourselves, but they don't
        // re-flood the parts that lie under our manually-placed water bodies (oceans/rivers/lakes),
        // leaving open air "holes" in the water - and where a carver cuts right through the water
        // surface it punches a dry shaft straight down to the seabed. Re-flood any carved air that
        // sits below the water surface in a column that our landscape marks as a water body.
        floodCarvedAirUnderWater(chunk);
    }

    /**
     * Re-floods carver-created air beneath our manually-placed water bodies.
     *
     * <p>A column is treated as water using the landscape's biome profile rather than by probing
     * the block at sea level. The old probe required the {@code seaTop} block itself to still be
     * water, so a carver that sliced through the surface (turning that very block to air) made the
     * whole column be skipped - leaving the dry shafts and surface pits seen over carved oceans.</p>
     *
     * <p>For each water column, water is poured down from {@code seaTop} through every air block
     * that is vertically connected to the surface, stopping at the first solid seabed block. This
     * seals the dry shafts and surface pits a carver opens into the water body while leaving the
     * genuine dry caves that sit <em>below</em> the seabed (lower than the local water table)
     * untouched, so underwater ravines and cave mouths stay explorable instead of being drowned
     * wholesale. Water already present is passed through; only carved air is converted.</p>
     */
    private void floodCarvedAirUnderWater(ChunkAccess chunk) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        int seaTop = Math.min(ChunkGeneratorAequora.SEA_LEVEL - 1, maxY - 1);
        if (seaTop < minY) {
            return;
        }

        RTGWorld world = this.worldSeed == 0L ? RTGWorld.fallback() : RTGWorld.fromSeed(this.worldSeed);
        ChunkLandscape landscape = this.terrain.getOrCreateLandscape(chunk, world);

        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (!ChunkGeneratorAequora.shouldFillSurfaceWater(landscape.biome[x * 16 + z])) {
                    continue;
                }
                // Pour from the surface downward through the connected open column. The column
                // is identified from the landscape (not by probing the sea-level block), so a
                // carver that cut through the surface block no longer makes us skip the column.
                for (int y = seaTop; y >= minY; y--) {
                    pos.set(baseX + x, y, baseZ + z);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.is(Blocks.AIR)) {
                        chunk.setBlockState(pos, water, false);
                    } else if (!state.is(Blocks.WATER)) {
                        // First solid seabed/lake floor: stop so dry caves carved below the
                        // bed (genuine, below the water table) are preserved.
                        break;
                    }
                }
            }
        }
    }

    @Override
    public ChunkGeneratorStructureState createState(net.minecraft.core.HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        if (this.worldSeed != seed) {
            this.terrain.clearLandscapeCache();
            // Force the vanilla mountain noise to be rebuilt from the new seed's RandomState;
            // otherwise a previous world's mountain noise would leak into this one.
            this.vanillaMountainNoise = null;
        }
        this.worldSeed = seed;
        applyBiomeSeed(seed);
        // Build and inject the vanilla mountain noise here, before ANY terrain height is
        // computed. createState runs first in the generation pipeline, so doing it now
        // guarantees getBaseHeight / getBaseColumn (called during structure placement) and
        // fillFromNoise all see the same non-null noise. If this were deferred to
        // createBiomes/fillFromNoise, early structure-phase height queries would compute and
        // cache simplex-fallback heights for the vanilla-noise mountains, which then mismatch
        // the vanilla water plane and make that terrain float over water.
        if (seed != 0L) {
            this.applyMountainNoise(randomState, RTGWorld.fromSeed(seed));
        }
        return super.createState(structureSetLookup, randomState, seed);
    }

    private void applyBiomeSeed(long seed) {
        if (this.getBiomeSource() instanceof BiomeProviderAequora aequoraSource) {
            aequoraSource.setSeed(seed);
        }
    }

    /** Builds the vanilla mountain noise once and makes it available to terrain generation. */
    private void applyMountainNoise(RandomState randomState, RTGWorld world) {
        VanillaMountainNoise noise = this.vanillaMountainNoise;
        if (noise == null) {
            synchronized (this) {
                noise = this.vanillaMountainNoise;
                if (noise == null) {
                    noise = new VanillaMountainNoise(randomState);
                    this.vanillaMountainNoise = noise;
                }
            }
        }
        world.setMountainNoise(noise);
    }

    private int getAequoraHeight(int x, int z, RandomState random) {
        RTGWorld world = this.worldSeed == 0L ? RTGWorld.fallback() : RTGWorld.fromSeed(this.worldSeed);
        return this.terrain.getHeightAt(x, z, world, this.getBiomeSource(), random.sampler());
    }

    private boolean shouldUseWaterSurface(int x, int z, RandomState random) {
        RTGWorld world = this.worldSeed == 0L ? RTGWorld.fallback() : RTGWorld.fromSeed(this.worldSeed);
        ChunkPos chunkPos = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        ChunkLandscape landscape = this.terrain.getOrCreateLandscape(chunkPos, world, this.getBiomeSource(), random.sampler());
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        return ChunkGeneratorAequora.shouldFillSurfaceWater(landscape.biome[localX * 16 + localZ]);
    }

    private static BlockState stateForY(int y, int terrainHeight, int seaTop, boolean waterSurface) {
        if (y <= terrainHeight) {
            return STONE;
        }
        if (waterSurface && y <= seaTop) {
            return WATER;
        }
        return AIR;
    }
}
