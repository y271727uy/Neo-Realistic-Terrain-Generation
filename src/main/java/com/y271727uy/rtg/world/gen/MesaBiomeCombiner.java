package com.y271727uy.rtg.world.gen;

import java.util.List;

/**
 * Ported from RTG's MesaBiomeCombiner.
 * Handles the blending of Mesa/Badlands biome variants based on noise weights.
 */
public class MesaBiomeCombiner {

    public void adjust(final List<Float> result) {
        // Index mapping: 0=badlands, 1=eroded_badlands, 2=wooded_badlands
        float badlandsBorder = result.get(0);
        float erodedBorder = result.get(1);
        float woodedBorder = result.get(2);

        if (erodedBorder > woodedBorder) {
            result.set(0, 0f);
            result.set(2, 0f);
            result.set(1, woodedBorder + erodedBorder);
        } else {
            result.set(0, 0f);
            result.set(1, 0f);
            result.set(2, woodedBorder + erodedBorder);
        }
    }
}
