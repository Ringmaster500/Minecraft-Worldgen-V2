package com.worldgenv2.minecraft;

import com.worldgenv2.worldstructure.MacroFeatureGraph;
import com.worldgenv2.worldstructure.WorldStructure;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Standalone NeoForge 26.1 mod entrypoint for Minecraft Worldgen V2.
 * Registers the {@link WorldGenV2ChunkGenerator} and {@link WorldGenV2BiomeSource} codecs
 * and provides in-game world designer commands ({@code /worldgenv2 info}, {@code /worldgenv2 reload},
 * {@code /worldgenv2 viewport}, {@code /worldgenv2 test-prefab}).
 */
@Mod(WorldGenV2Mod.MOD_ID)
public final class WorldGenV2Mod {

    public static final String MOD_ID = "worldgenv2";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
        DeferredRegister.create(Registries.BIOME_SOURCE, MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<WorldGenV2ChunkGenerator>> VOLUMETRIC_CHUNK_GENERATOR =
        CHUNK_GENERATORS.register("volumetric_v2", () -> WorldGenV2ChunkGenerator.CODEC);

    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<WorldGenV2BiomeSource>> BICARTA_BIOME_SOURCE =
        BIOME_SOURCES.register("bicarta_biomes", () -> WorldGenV2BiomeSource.CODEC);

    public WorldGenV2Mod(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            com.worldgenv2.studio.WorldGenStudioClient.init(modEventBus);
        }

        LOGGER.info("Initialized Minecraft Worldgen V2 for Minecraft 26.1 (NeoForge)");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("worldgenv2")
                .then(Commands.literal("info")
                    .executes(ctx -> executeInfo(ctx.getSource()))
                )
                .then(Commands.literal("reload")
                    .executes(ctx -> executeReload(ctx.getSource()))
                )
                .then(Commands.literal("viewport")
                    .executes(ctx -> executeViewport(ctx.getSource(), 1))
                    .then(Commands.argument("radius_chunks", IntegerArgumentType.integer(1, 4))
                        .executes(ctx -> executeViewport(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "radius_chunks")
                        ))
                    )
                )
                .then(Commands.literal("test-prefab")
                    .executes(ctx -> executeTestPrefab(ctx.getSource()))
                )
                .then(Commands.literal("landmarks")
                    .executes(ctx -> executeLandmarks(ctx.getSource()))
                )
                .then(Commands.literal("studio")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("[WorldGen V2] Press 'G' or run /studio to open the creator companion studio."), false);
                        return 1;
                    })
                )
        );
    }

    private static int executeLandmarks(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof WorldGenV2ChunkGenerator v2Gen)) {
            source.sendFailure(Component.literal("[Worldgen V2] Active dimension is not running WorldGenV2ChunkGenerator."));
            return 0;
        }
        MacroFeatureGraph graph = v2Gen.getActiveWorldStructure().getMacroFeatureGraph();
        if (graph == null || graph.getPlacedLandmarks().isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Worldgen V2] No macro landmarks registered."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[Worldgen V2] Macro Landmarks & Road Endpoints:");
        for (MacroFeatureGraph.PlacedLandmark lm : graph.getPlacedLandmarks().values()) {
            String rel = lm.parentLandmarkId() != null ? " (connected to " + lm.parentLandmarkId() + ")" : " (primary hub)";
            sb.append(String.format("\n - %s at (%d, %d, %d) in %s%s", lm.id(), lm.x(), lm.y(), lm.z(), lm.biomeName(), rel));
        }
        String msg = sb.toString();
        source.sendSuccess(() -> Component.literal(msg), false);
        return graph.getPlacedLandmarks().size();
    }

    private static int executeInfo(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        ChunkGenerator generator = level.getChunkSource().getGenerator();

        if (generator instanceof WorldGenV2ChunkGenerator v2Gen) {
            WorldStructure.BiomeQueryResult query = v2Gen.getActiveWorldStructure().queryBiome(pos.getX(), pos.getZ());
            float density = query.computeBlendedPoint(pos.getX(), pos.getY(), pos.getZ());
            String customDesc = query.customBiome() != null
                ? String.format(", CustomOverlay=%s (%.2f)", query.customBiome().name(), query.customBlendWeight())
                : "";

            WorldHeightConfig hConfig = v2Gen.getHeightConfig();
            source.sendSuccess(
                () -> Component.literal(String.format(
                    "[Worldgen V2] Pos=(%d, %d, %d) | Primary=%s | Secondary=%s | EdgeDist=%.1f | Blend=%.2f%s | YRange=[%d..%d] (Depth=%d, Sea=%d, Preset=%s) | Density=%.3f",
                    pos.getX(), pos.getY(), pos.getZ(),
                    query.primaryTileBiome().name(),
                    query.secondaryTileBiome().name(),
                    query.distanceToEdge(),
                    query.tileBlendFactor(),
                    customDesc,
                    hConfig.minY(), hConfig.maxY(), hConfig.genDepth(), hConfig.seaLevel(), hConfig.preset().getId(),
                    density
                )),
                false
            );
            return 1;
        } else {
            source.sendSuccess(
                () -> Component.literal("[Worldgen V2] Current dimension is not using WorldGenV2ChunkGenerator. Create a world with the 'Minecraft Worldgen V2' World Type!"),
                false
            );
            return 0;
        }
    }

    private static int executeReload(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof WorldGenV2ChunkGenerator v2Gen) {
            v2Gen.reload();
            source.sendSuccess(
                () -> Component.literal("[Worldgen V2] Reloaded WorldStructure, BiCarta Voronoi grid, and StagedChunkGenerator pipeline."),
                true
            );
            return 1;
        }
        source.sendFailure(Component.literal("[Worldgen V2] Active dimension is not running WorldGenV2ChunkGenerator."));
        return 0;
    }

    private static int executeViewport(CommandSourceStack source, int radiusChunks) {
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof WorldGenV2ChunkGenerator v2Gen)) {
            source.sendFailure(Component.literal("[Worldgen V2] Active dimension is not running WorldGenV2ChunkGenerator."));
            return 0;
        }

        v2Gen.reload();
        BlockPos center = BlockPos.containing(source.getPosition());
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        int count = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                v2Gen.regenerateLiveChunk(level, new net.minecraft.world.level.ChunkPos(centerChunkX + dx, centerChunkZ + dz));
                count++;
            }
        }
        final int total = count;

        source.sendSuccess(
            () -> Component.literal(String.format(
                "[Worldgen V2] Live-regenerated %d chunks around [%d, %d].",
                total, centerChunkX, centerChunkZ
            )),
            true
        );
        return count;
    }

    private static int executeTestPrefab(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition()).offset(3, 0, 3);

        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossyBricks = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState oakPlanks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState();

        // 1. Adaptive foundation plinth: ray-march downward under the 5x5 footprint until hitting solid terrain
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int wx = origin.getX() + dx;
                int wz = origin.getZ() + dz;
                int y = origin.getY() - 1;
                int depth = 0;
                while (y > level.getMinY() && level.getBlockState(new BlockPos(wx, y, wz)).isAir() && depth < 24) {
                    level.setBlock(new BlockPos(wx, y, wz), cobble, 3);
                    y--;
                    depth++;
                }
                level.setBlock(new BlockPos(wx, origin.getY(), wz), stoneBricks, 3);
            }
        }

        // 2. Watchtower walls & timber deck
        for (int dy = 1; dy <= 6; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean isWall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    BlockPos target = origin.offset(dx, dy, dz);
                    if (dy == 5 && !isWall) {
                        level.setBlock(target, oakPlanks, 3);
                    } else if (isWall) {
                        if (dy == 6 && (dx + dz) % 2 != 0) {
                            continue;
                        }
                        level.setBlock(target, ((dx * 31 + dy * 17 + dz) & 3) == 0 ? mossyBricks : stoneBricks, 3);
                    } else {
                        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        level.setBlock(origin.offset(0, 6, 0), lantern, 3);

        source.sendSuccess(
            () -> Component.literal(String.format(
                "[Worldgen V2] Stamped test watchtower prefab with adaptive foundation plinth at (%d, %d, %d).",
                origin.getX(), origin.getY(), origin.getZ()
            )),
            true
        );
        return 1;
    }
}
