package com.y271727uy.rtg.world;

import com.y271727uy.rtg.AequoraMod;
import com.y271727uy.rtg.world.gen.AequoraChunkGenerator;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import java.util.Map;

/**
 * Ported from RTG's WorldTypeRTG.
 * Registers the Aequora world preset for the world creation screen.
 */
public class WorldTypeAequora {

    public static final ResourceKey<WorldPreset> AEQUORA_PRESET = ResourceKey.create(
            Registries.WORLD_PRESET,
                    new ResourceLocation(AequoraMod.MODID, "rtg")
    );

    /**
     * Bootstrap method called during datagen to register the world preset.
     */
    public static void bootstrap(BootstapContext<WorldPreset> context) {
        HolderGetter<DimensionType> dimTypeLookup = context.lookup(Registries.DIMENSION_TYPE);
        HolderGetter<NoiseGeneratorSettings> noiseSettingsLookup = context.lookup(Registries.NOISE_SETTINGS);
        HolderGetter<Biome> biomeLookup = context.lookup(Registries.BIOME);

        AequoraChunkGenerator chunkGen = new AequoraChunkGenerator(
                new FixedBiomeSource(biomeLookup.getOrThrow(Biomes.PLAINS)),
                noiseSettingsLookup.getOrThrow(NoiseGeneratorSettings.OVERWORLD)
        );

        context.register(AEQUORA_PRESET, new WorldPreset(
                Map.of(
                        LevelStem.OVERWORLD,
                        new LevelStem(
                                dimTypeLookup.getOrThrow(BuiltinDimensionTypes.OVERWORLD),
                                chunkGen
                        )
                )
        ));
    }

    private WorldTypeAequora() {}
}
