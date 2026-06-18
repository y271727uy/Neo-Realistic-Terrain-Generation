package com.y271727uy.rtg.world.gen;

/**
 * Ported from RTG's MapGenCavesRTG.
 * Controls cave generation density and chance parameters for Aequora terrain.
 */
public class MapGenCavesAequora {

    private final int caveChance;
    private final int caveDensity;

    public MapGenCavesAequora(int caveChance, int caveDensity) {
        // Vanilla chance = 7. HIGHER values = FEWER caves.
        this.caveChance = Math.max(caveChance, 1);
        // Vanilla density = 15. Controls the number of cave tunnels.
        this.caveDensity = Math.max(caveDensity, 1);
    }

    public int caveChance() {
        return caveChance;
    }

    public int caveDensity() {
        return caveDensity;
    }
}
