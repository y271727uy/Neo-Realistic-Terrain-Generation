package com.y271727uy.rtg.api.world.terrain.heighteffect;

import com.y271727uy.rtg.api.world.RTGWorld;

public final class SummedHeightEffect extends HeightEffect {
    private final HeightEffect first;
    private final HeightEffect second;

    public SummedHeightEffect(HeightEffect first, HeightEffect second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public float added(RTGWorld world, float x, float z) {
        return this.first.added(world, x, z) + this.second.added(world, x, z);
    }
}
