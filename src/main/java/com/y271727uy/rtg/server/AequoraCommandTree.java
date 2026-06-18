package com.y271727uy.rtg.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.y271727uy.rtg.AequoraMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ported from RTG's RTGCommandTree.
 * Provides the /aequora whereami command to display biome information at the player's location.
 */
@Mod.EventBusSubscriber(modid = AequoraMod.MODID)
public final class AequoraCommandTree {
    private static final Logger LOGGER = LoggerFactory.getLogger(AequoraCommandTree.class);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("aequora")
                        .then(Commands.literal("whereami")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        BlockPos pos = player.blockPosition();
                                        ChunkAccess chunk = player.level().getChunk(pos);

                                        int biomeX = QuartPos.fromBlock(pos.getX()) & 3;
                                        int biomeY = QuartPos.fromBlock(pos.getY()) & 3;
                                        int biomeZ = QuartPos.fromBlock(pos.getZ()) & 3;

                                        Holder<Biome> chunkBiome = chunk.getNoiseBiome(biomeX, biomeY, biomeZ);
                                        Holder<Biome> levelBiome = player.level().getBiome(pos);
                                        ChunkGenerator generator = player.serverLevel().getChunkSource().getGenerator();
                                        RandomState randomState = player.serverLevel().getChunkSource().randomState();
                                        Holder<Biome> sourceBiome = generator.getBiomeSource()
                                                .getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), randomState.sampler());

                                        player.sendSystemMessage(Component.literal(
                                                String.format("Biome @ %s,%s | Level: %s | Chunk: %s | Source: %s",
                                                        pos.getX(), pos.getZ(),
                                                        biomeId(levelBiome),
                                                        biomeId(chunkBiome),
                                                        biomeId(sourceBiome))));
                                        int surfaceY = player.level().getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
                                        player.sendSystemMessage(Component.literal(
                                                String.format("Surface @ column: y=%s block=%s",
                                                        surfaceY - 1,
                                                        player.level().getBlockState(new BlockPos(pos.getX(), surfaceY - 1, pos.getZ())).getBlock().builtInRegistryHolder().key().location())));
                                        List<String> debug = new ArrayList<>();
                                        generator.getBiomeSource().addDebugInfo(debug, pos, randomState.sampler());
                                        generator.addDebugScreenInfo(debug, randomState, pos);
                                        debug.forEach(line -> player.sendSystemMessage(Component.literal(line)));

                                        return Command.SINGLE_SUCCESS;
                                    }
                                    source.sendFailure(Component.literal("This command can only be used by a player."));
                                    return 0;
                                })
                        )
                        .then(Commands.literal("biomes")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> reportBiomes(ctx.getSource(), 512))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(128, 4096))
                                        .executes(ctx -> reportBiomes(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))
                        )
        );

        LOGGER.debug("Registered /aequora command tree");
    }

    private static int reportBiomes(CommandSourceStack source, int radius) {
        if (source.getEntity() instanceof ServerPlayer player) {
            BlockPos center = player.blockPosition();
            Map<ResourceLocation, Integer> levelCounts = new LinkedHashMap<>();
            Map<ResourceLocation, Integer> sourceCounts = new LinkedHashMap<>();
            ChunkGenerator generator = player.serverLevel().getChunkSource().getGenerator();
            RandomState randomState = player.serverLevel().getChunkSource().randomState();

            int step = Math.max(16, Math.min(128, radius / 8));
            for (int x = center.getX() - radius; x <= center.getX() + radius; x += step) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z += step) {
                    BlockPos pos = new BlockPos(x, center.getY(), z);
                    ResourceLocation levelKey = biomeId(player.level().getBiome(pos));
                    Holder<Biome> sourceBiome = generator.getBiomeSource()
                            .getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(center.getY()), QuartPos.fromBlock(z), randomState.sampler());
                    ResourceLocation sourceKey = biomeId(sourceBiome);
                    levelCounts.merge(levelKey, 1, Integer::sum);
                    sourceCounts.merge(sourceKey, 1, Integer::sum);
                }
            }

            player.sendSystemMessage(Component.literal("Level biome samples within " + radius + " blocks:"));
            sendCounts(player, levelCounts);
            player.sendSystemMessage(Component.literal("Source biome samples within " + radius + " blocks:"));
            sendCounts(player, sourceCounts);
            return Command.SINGLE_SUCCESS;
        }
        source.sendFailure(Component.literal("This command can only be used by a player."));
        return 0;
    }

    private static void sendCounts(ServerPlayer player, Map<ResourceLocation, Integer> counts) {
        counts.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(12)
                    .forEach(entry -> player.sendSystemMessage(Component.literal(
                            entry.getKey() + ": " + entry.getValue())));
    }

    private static ResourceLocation biomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> key.location())
                .orElseGet(() -> {
                    ResourceLocation fallback = ForgeRegistries.BIOMES.getKey(biome.value());
                    return fallback != null ? fallback : new ResourceLocation(AequoraMod.MODID, "unknown");
                });
    }

    private AequoraCommandTree() {}
}
