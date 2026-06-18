package com.y271727uy.rtg.config;

import com.y271727uy.rtg.AequoraMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Ported from RTG's RTGConfig.
 * World generation-specific configuration for Aequora terrain.
 */
@Mod.EventBusSubscriber(modid = AequoraMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WorldGenConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ---- Geography ----

    private static final ForgeConfigSpec.IntValue BIOME_SIZE = BUILDER
            .comment("Number of times biomes are scaled and refined.",
                    "Smaller values = smaller, more fragmented biomes with more frequent terrain transitions.")
            .defineInRange("biomeSize", 5, 1, 10);

    private static final ForgeConfigSpec.IntValue RIVER_SIZE = BUILDER
            .comment("Density and complexity of river distribution.",
                    "Larger values lead to denser and more intricate river systems.")
            .defineInRange("riverSize", 4, 1, 10);

    private static final ForgeConfigSpec.IntValue LAND_SCHEME = BUILDER
            .comment("Global land and sea distribution.",
                    "1: Vanilla distribution (no clear preference)",
                    "2: Continents",
                    "3: Archipelagos")
            .defineInRange("landScheme", 1, 1, 3);

    private static final ForgeConfigSpec.IntValue ISLAND_SCHEME = BUILDER
            .comment("Global island and ocean distribution.",
                    "1: Vanilla distribution (no clear preference)",
                    "2: Continents",
                    "3: Archipelagos")
            .defineInRange("islandScheme", 1, 1, 3);

    private static final ForgeConfigSpec.IntValue TEMP_SCHEME = BUILDER
            .comment("Temperature distribution patterns.",
                    "1: Latitude-based  2: Small areas  3: Medium areas  4: Large areas  5: Random")
            .defineInRange("tempScheme", 3, 1, 5);

    private static final ForgeConfigSpec.IntValue RAIN_SCHEME = BUILDER
            .comment("Precipitation distribution patterns.",
                    "1: Small areas  2: Medium areas  3: Large areas  4: Random")
            .defineInRange("rainScheme", 3, 1, 4);

    // ---- Surface ----

    private static final ForgeConfigSpec.IntValue SURFACE_BLEND_RADIUS = BUILDER
            .comment("The maximum distance surfaces will blend into each other if enabled for two adjacent biomes.")
            .defineInRange("surfaceBlendRadius", 32, 8, 32);

    private static final ForgeConfigSpec.DoubleValue RIVER_DEPTH = BUILDER
            .comment("Average depth of rivers.")
            .defineInRange("riverDepth", 57.0, 53.0, 60.0);

    private static final ForgeConfigSpec.DoubleValue WATER_FEATURE_WIDTH_MULTIPLIER = BUILDER
            .comment("Multiplier to average width of rivers and lakes.")
            .defineInRange("waterFeatureWidthMultiplier", 1.0, 0.1, 10.0);

    // ---- Debug ----

    private static final ForgeConfigSpec.BooleanValue ENABLE_DEBUGGING = BUILDER
            .comment("Enable extra debug logging. This has a severe performance penalty.")
            .define("enableDebugging", false);

    private static final ForgeConfigSpec.ConfigValue<String> PATCH_BIOME = BUILDER
            .comment("If Aequora encounters an unsupported biome it will generate this biome instead.",
                    "Uses standard ResourceLocation format: mod_id:biome_registry_name")
            .define("patchBiome", "minecraft:plains");

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // ---- Cached values ----

    public static int biomeSize = 5;
    public static int riverSize = 4;
    public static int landScheme = 1;
    public static int islandScheme = 1;
    public static int tempScheme = 3;
    public static int rainScheme = 3;
    public static int surfaceBlendRadius = 32;
    public static double riverDepth = 57.0;
    public static double waterFeatureWidthMultiplier = 1.0;
    public static boolean enableDebugging = false;
    public static String patchBiome = "minecraft:plains";

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        biomeSize = BIOME_SIZE.get();
        riverSize = RIVER_SIZE.get();
        landScheme = LAND_SCHEME.get();
        islandScheme = ISLAND_SCHEME.get();
        tempScheme = TEMP_SCHEME.get();
        rainScheme = RAIN_SCHEME.get();
        surfaceBlendRadius = SURFACE_BLEND_RADIUS.get();
        riverDepth = RIVER_DEPTH.get();
        waterFeatureWidthMultiplier = WATER_FEATURE_WIDTH_MULTIPLIER.get();
        enableDebugging = ENABLE_DEBUGGING.get();
        patchBiome = PATCH_BIOME.get();
    }

    private WorldGenConfig() {}
}
