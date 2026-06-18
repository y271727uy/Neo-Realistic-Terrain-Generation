package com.y271727uy.rtg.world.gen;

import com.y271727uy.rtg.api.world.biome.IRealisticBiome;

/**
 * Data container for a chunk's terrain generation output.
 * Stores height noise, biome assignments, and river strength per column.
 */
public class ChunkLandscape {

    public final float[] noise = new float[256];
    public final IRealisticBiome[] biome = new IRealisticBiome[256];
    public final float[] river = new float[256];
}
