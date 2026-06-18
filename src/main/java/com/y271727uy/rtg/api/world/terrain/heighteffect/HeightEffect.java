package com.y271727uy.rtg.api.world.terrain.heighteffect;

import com.y271727uy.rtg.api.world.RTGWorld;

public abstract class HeightEffect {
    public abstract float added(RTGWorld world, float x, float z);

    public HeightEffect plus(HeightEffect added) {
        return new SummedHeightEffect(this, added);
    }
}
