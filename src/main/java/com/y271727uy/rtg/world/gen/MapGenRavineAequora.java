package com.y271727uy.rtg.world.gen;

/**
 * Ported from RTG's MapGenRavineRTG.
 * Controls ravine generation chance parameters for Aequora terrain.
 */
public class MapGenRavineAequora {

    private final int ravineChance;

    public MapGenRavineAequora(int ravineChance) {
        // Vanilla chance = 50. HIGHER values = FEWER ravines.
        this.ravineChance = Math.max(ravineChance, 1);
    }

    public int ravineChance() {
        return ravineChance;
    }
}
