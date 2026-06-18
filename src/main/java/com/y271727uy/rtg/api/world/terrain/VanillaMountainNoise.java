package com.y271727uy.rtg.api.world.terrain;

import com.y271727uy.rtg.AequoraMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Vanilla {@link NormalNoise} sources used to shape Aequora's mountains and peaks.
 *
 * <p>Instead of the hand-rolled OpenSimplex peak terrain, the mountain body and crests are
 * driven by vanilla's own noise generator - a broad low-frequency {@code NormalNoise} for the
 * massif plus vanilla's authentic {@link Noises#JAGGED} jaggedness for spiky ridgelines - so
 * the result inherits vanilla's mountain feel while still flowing through Aequora's terrain
 * framework (river carving, base height, {@code mountainCap}).</p>
 */
public final class VanillaMountainNoise {
    private static final ResourceLocation MASS_RANDOM = new ResourceLocation(AequoraMod.MODID, "mountain_mass");
    // Vanilla JAGGED is very high frequency; stretch the sample coords so crests span tens of
    // blocks rather than single-block static.
    private static final double JAGGED_INPUT_SCALE = 0.5;

    private final NormalNoise mass;
    private final NormalNoise jagged;

    public VanillaMountainNoise(RandomState randomState) {
        RandomSource random = randomState.getOrCreateRandomFactory(MASS_RANDOM).fromHashOf("mass");
        // Low-frequency octaves: ~512 down to ~32 block features for the broad mountain body.
        this.mass = NormalNoise.create(random, -9, 1.0, 1.0, 1.0, 0.5);
        this.jagged = randomState.getOrCreateNoise(Noises.JAGGED);
    }

    /** Broad mountain mass, roughly in [-1, 1]. */
    public double mass(int x, int z) {
        return this.mass.getValue(x, 0.0, z);
    }

    /** Vanilla jaggedness at a tamed frequency, roughly in [-1, 1]. */
    public double jagged(int x, int z) {
        return this.jagged.getValue(x * JAGGED_INPUT_SCALE, 0.0, z * JAGGED_INPUT_SCALE);
    }
}
