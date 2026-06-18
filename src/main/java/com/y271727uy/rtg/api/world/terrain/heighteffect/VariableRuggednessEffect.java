package com.y271727uy.rtg.api.world.terrain.heighteffect;

import com.y271727uy.rtg.api.world.RTGWorld;

public final class VariableRuggednessEffect extends HeightEffect {
    public static final float STANDARD_RUGGEDNESS_WAVELENGTH = 300.0f;
    public static final float INV_STANDARD_RUGGEDNESS_WAVELENGTH = 1.0f / STANDARD_RUGGEDNESS_WAVELENGTH;

    private final HeightEffect smooth;
    private final HeightEffect rugged;
    private final float threshold;

    public VariableRuggednessEffect(HeightEffect smooth, HeightEffect rugged, float threshold) {
        this.smooth = smooth;
        this.rugged = rugged;
        this.threshold = threshold;
    }

    @Override
    public float added(RTGWorld world, float x, float z) {
        float selector = world.simplexInstance(1).noise2f(
                x * INV_STANDARD_RUGGEDNESS_WAVELENGTH,
                z * INV_STANDARD_RUGGEDNESS_WAVELENGTH
        );
        return selector > this.threshold ? this.rugged.added(world, x, z) : this.smooth.added(world, x, z);
    }
}
