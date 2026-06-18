package com.y271727uy.rtg.init;

import com.y271727uy.rtg.api.RTGAPI;
import com.y271727uy.rtg.config.WorldGenConfig;
import com.y271727uy.rtg.world.biome.realistic.AequoraRealisticBiome;
import com.y271727uy.rtg.world.biome.realistic.AequoraRealisticBiome.TerrainProfile;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AequoraBiomeInit {
    private static final Logger LOGGER = LoggerFactory.getLogger(AequoraBiomeInit.class);
    private static boolean initialized;

    private AequoraBiomeInit() {
    }

    public static void init() {
        if (initialized || RTGAPI.realisticBiomesLocked()) {
            return;
        }
        initialized = true;

        registerVanillaOverworld();
        registerRemainingOverworldBiomes();
        initPatchBiome();
        RTGAPI.lockRealisticBiomes();

        LOGGER.info("Registered {} Aequora realistic biome adapters", RTGAPI.realisticBiomeCount());
    }

    private static void registerVanillaOverworld() {
        Map<ResourceKey<Biome>, TerrainProfile> biomes = new LinkedHashMap<>();

        addPlains(biomes);
        addForests(biomes);
        addTaiga(biomes);
        addWarmBiomes(biomes);
        addColdBiomes(biomes);
        addMountains(biomes);
        addWaterAndShores(biomes);

        biomes.forEach((key, profile) -> RTGAPI.addRealisticBiomes(new AequoraRealisticBiome(key, profile)));
    }

    private static void registerRemainingOverworldBiomes() {
        int registered = 0;
        int skipped = 0;

        for (Map.Entry<ResourceKey<Biome>, Biome> entry : ForgeRegistries.BIOMES.getEntries()) {
            ResourceKey<Biome> key = entry.getKey();
            if (RTGAPI.hasRealisticBiome(key)) {
                continue;
            }
            Holder<Biome> holder = ForgeRegistries.BIOMES.getHolder(key).orElse(null);
            if (holder == null || !isOverworldBiome(holder, key)) {
                skipped++;
                continue;
            }

            TerrainProfile profile = classifyBiome(holder, key);
            RTGAPI.addRealisticBiomes(new AequoraRealisticBiome(key, profile));
            registered++;
        }

        LOGGER.info("Registered {} dynamic overworld biome adapters, skipped {}", registered, skipped);
    }

    public static boolean isOverworldBiome(Holder<Biome> holder, ResourceKey<Biome> key) {
        ResourceLocation id = key.location();
        String path = id.getPath();
        if (path.contains("nether") || path.contains("end") || path.contains("void") || path.contains("hell") || path.contains("sky")) {
            return false;
        }
        return holder.is(BiomeTags.IS_OVERWORLD) || holder.is(BiomeTags.IS_RIVER) || holder.is(BiomeTags.IS_OCEAN) || holder.is(BiomeTags.IS_BEACH);
    }

    public static TerrainProfile classifyBiome(Holder<Biome> holder, ResourceKey<Biome> key) {
        if (holder.is(BiomeTags.IS_RIVER)) {
            return TerrainProfile.RIVER;
        }
        if (holder.is(BiomeTags.IS_OCEAN)) {
            return TerrainProfile.OCEAN;
        }
        if (holder.is(BiomeTags.IS_BEACH)) {
            return TerrainProfile.BEACH;
        }

        String path = key.location().getPath();
        if (matches(path, "mushroom")) return TerrainProfile.MUSHROOM;
        if (matches(path, "swamp", "mangrove")) return TerrainProfile.SWAMP;
        if (matches(path, "jungle", "bamboo")) return TerrainProfile.JUNGLE;
        if (matches(path, "desert")) return TerrainProfile.DESERT;
        if (matches(path, "savanna")) return TerrainProfile.SAVANNA;
        if (matches(path, "badlands", "mesa", "canyon")) return TerrainProfile.BADLANDS;
        if (matches(path, "taiga", "spruce", "pine", "conifer", "grove")) return TerrainProfile.TAIGA;
        if (matches(path, "snow", "frozen", "ice", "cold", "frost")) return TerrainProfile.SNOW;
        if (matches(path, "peak", "jagged", "spire")) return TerrainProfile.PEAKS;
        if (matches(path, "mountain", "slope", "alps", "highland", "cliff")) return TerrainProfile.MOUNTAINS;
        if (matches(path, "hill", "windswept")) return TerrainProfile.HILLS;
        if (matches(path, "forest", "woods", "woodland", "birch", "dark", "cherry")) return TerrainProfile.FOREST;
        if (matches(path, "beach", "shore")) return TerrainProfile.BEACH;
        if (matches(path, "river")) return TerrainProfile.RIVER;
        if (matches(path, "ocean", "reef", "kelp")) return TerrainProfile.OCEAN;
        if (matches(path, "plains", "meadow", "flat", "grass")) return TerrainProfile.PLAINS;
        return TerrainProfile.PLAINS;
    }

    private static boolean matches(String path, String... needles) {
        for (String needle : needles) {
            if (path.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void addPlains(Map<ResourceKey<Biome>, TerrainProfile> biomes) {
        biomes.put(Biomes.PLAINS, TerrainProfile.PLAINS);
        biomes.put(Biomes.SUNFLOWER_PLAINS, TerrainProfile.PLAINS);
        biomes.put(Biomes.MEADOW, TerrainProfile.PLAINS);
        biomes.put(Biomes.CHERRY_GROVE, TerrainProfile.FOREST);
    }

    private static void addForests(Map<ResourceKey<Biome>, TerrainProfile> biomes) {
        biomes.put(Biomes.FOREST, TerrainProfile.FOREST);
        biomes.put(Biomes.FLOWER_FOREST, TerrainProfile.FOREST);
        biomes.put(Biomes.BIRCH_FOREST, TerrainProfile.FOREST);
        biomes.put(Biomes.OLD_GROWTH_BIRCH_FOREST, TerrainProfile.FOREST);
        biomes.put(Biomes.DARK_FOREST, TerrainProfile.FOREST);
    }

    private static void addTaiga(Map<ResourceKey<Biome>, TerrainProfile> biomes) {
        biomes.put(Biomes.TAIGA, TerrainProfile.TAIGA);
        biomes.put(Biomes.OLD_GROWTH_PINE_TAIGA, TerrainProfile.TAIGA);
        biomes.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, TerrainProfile.TAIGA);
        biomes.put(Biomes.GROVE, TerrainProfile.TAIGA);
    }

    private static void addWarmBiomes(Map<ResourceKey<Biome>, TerrainProfile> biomes) {
        biomes.put(Biomes.DESERT, TerrainProfile.DESERT);
        biomes.put(Biomes.SAVANNA, TerrainProfile.SAVANNA);
        biomes.put(Biomes.SAVANNA_PLATEAU, TerrainProfile.SAVANNA);
        biomes.put(Biomes.WINDSWEPT_SAVANNA, TerrainProfile.HILLS);
        biomes.put(Biomes.JUNGLE, TerrainProfile.JUNGLE);
        biomes.put(Biomes.SPARSE_JUNGLE, TerrainProfile.JUNGLE);
        biomes.put(Biomes.BAMBOO_JUNGLE, TerrainProfile.JUNGLE);
        biomes.put(Biomes.SWAMP, TerrainProfile.SWAMP);
        biomes.put(Biomes.MANGROVE_SWAMP, TerrainProfile.SWAMP);
        biomes.put(Biomes.BADLANDS, TerrainProfile.BADLANDS);
        biomes.put(Biomes.ERODED_BADLANDS, TerrainProfile.BADLANDS);
        biomes.put(Biomes.WOODED_BADLANDS, TerrainProfile.BADLANDS);
    }

    private static void addColdBiomes(Map<ResourceKey<Biome>, TerrainProfile> biomes) {
        biomes.put(Biomes.SNOWY_PLAINS, TerrainProfile.SNOW);
        biomes.put(Biomes.ICE_SPIKES, TerrainProfile.SNOW);
        biomes.put(Biomes.SNOWY_TAIGA, TerrainProfile.TAIGA);
        biomes.put(Biomes.SNOWY_SLOPES, TerrainProfile.MOUNTAINS);
        biomes.put(Biomes.FROZEN_PEAKS, TerrainProfile.PEAKS);
        biomes.put(Biomes.JAGGED_PEAKS, TerrainProfile.PEAKS);
    }

    private static void addMountains(Map<ResourceKey<Biome>, TerrainProfile> biomes) {
        biomes.put(Biomes.WINDSWEPT_HILLS, TerrainProfile.HILLS);
        biomes.put(Biomes.WINDSWEPT_GRAVELLY_HILLS, TerrainProfile.HILLS);
        biomes.put(Biomes.WINDSWEPT_FOREST, TerrainProfile.HILLS);
        biomes.put(Biomes.STONY_PEAKS, TerrainProfile.PEAKS);
    }

    private static void addWaterAndShores(Map<ResourceKey<Biome>, TerrainProfile> biomes) {
        biomes.put(Biomes.RIVER, TerrainProfile.RIVER);
        biomes.put(Biomes.FROZEN_RIVER, TerrainProfile.RIVER);
        biomes.put(Biomes.BEACH, TerrainProfile.BEACH);
        biomes.put(Biomes.SNOWY_BEACH, TerrainProfile.BEACH);
        biomes.put(Biomes.STONY_SHORE, TerrainProfile.BEACH);
        biomes.put(Biomes.OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.DEEP_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.COLD_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.DEEP_COLD_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.FROZEN_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.DEEP_FROZEN_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.LUKEWARM_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.DEEP_LUKEWARM_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.WARM_OCEAN, TerrainProfile.OCEAN);
        biomes.put(Biomes.MUSHROOM_FIELDS, TerrainProfile.MUSHROOM);
    }

    private static void initPatchBiome() {
        ResourceLocation patchId = ResourceLocation.tryParse(WorldGenConfig.patchBiome);
        if (patchId == null) {
            patchId = Biomes.PLAINS.location();
        }

        ResourceKey<Biome> patchKey = ResourceKey.create(Registries.BIOME, patchId);
        RTGAPI.getRealisticBiome(patchKey)
                .or(() -> RTGAPI.getRealisticBiome(Biomes.PLAINS))
                .ifPresent(RTGAPI::setPatchBiome);
    }
}
