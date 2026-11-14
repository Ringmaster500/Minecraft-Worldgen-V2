package com.worldgenv2.minecraft;

import com.worldgenv2.api.VoxelSpace;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * High-performance VoxelSpace wrapper over Minecraft 26.1's {@link ChunkAccess} and {@link LevelChunkSection}s.
 * Writes directly into chunk sections and updates generation heightmaps without allocating {@code BlockPos} objects.
 */
public final class MinecraftVoxelSpace implements VoxelSpace<BlockState> {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState VOID_AIR = Blocks.VOID_AIR.defaultBlockState();

    private final ChunkAccess chunk;
    private final LevelChunkSection[] sections;
    private final Heightmap oceanFloorHeightmap;
    private final Heightmap worldSurfaceHeightmap;
    private final int minBlockX;
    private final int minBlockZ;
    private final int minY;
    private final int maxY;

    public MinecraftVoxelSpace(ChunkAccess chunk) {
        this.chunk = chunk;
        this.sections = chunk.getSections();
        this.oceanFloorHeightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        this.worldSurfaceHeightmap = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        this.minBlockX = chunk.getPos().getMinBlockX();
        this.minBlockZ = chunk.getPos().getMinBlockZ();

        LevelHeightAccessor heightAccessor = chunk.getHeightAccessorForGeneration();
        this.minY = heightAccessor.getMinY();
        this.maxY = this.minY + heightAccessor.getHeight();
    }

    @Override
    public BlockState getBlock(int x, int y, int z) {
        if (y < minY || y >= maxY) {
            return VOID_AIR;
        }
        int lx = x - minBlockX;
        int lz = z - minBlockZ;
        if ((lx | lz) < 0 || lx >= 16 || lz >= 16) {
            return AIR;
        }
        int sectionIdx = chunk.getSectionIndex(y);
        if (sectionIdx < 0 || sectionIdx >= sections.length) {
            return VOID_AIR;
        }
        LevelChunkSection section = sections[sectionIdx];
        return section.hasOnlyAir() ? AIR : section.getBlockState(lx, y & 15, lz);
    }

    @Override
    public void setBlock(int x, int y, int z, BlockState block) {
        if (block == null || y < minY || y >= maxY) {
            return;
        }
        int lx = x - minBlockX;
        int lz = z - minBlockZ;
        if ((lx | lz) < 0 || lx >= 16 || lz >= 16) {
            return;
        }
        int sectionIdx = chunk.getSectionIndex(y);
        if (sectionIdx < 0 || sectionIdx >= sections.length) {
            return;
        }
        LevelChunkSection section = sections[sectionIdx];
        if (section.hasOnlyAir() && block.isAir()) {
            return;
        }
        section.setBlockState(lx, y & 15, lz, block, false);
        if (!block.isAir()) {
            oceanFloorHeightmap.update(lx, y, lz, block);
            worldSurfaceHeightmap.update(lx, y, lz, block);
        }
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        return getBlock(x, y, z).isAir();
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getMaxY() {
        return maxY;
    }

    @Override
    public void setBlockLoot(int x, int y, int z, String lootTableId, long seed) {
        if (lootTableId == null || lootTableId.isBlank() || y < minY || y >= maxY) {
            return;
        }
        int lx = x - minBlockX;
        int lz = z - minBlockZ;
        if ((lx | lz) < 0 || lx >= 16 || lz >= 16) {
            return;
        }
        net.minecraft.resources.Identifier loc = net.minecraft.resources.Identifier.tryParse(lootTableId);
        if (loc == null) return;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.block.state.BlockState state = chunk.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb)) {
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity be = chunk.getBlockEntity(pos);
        if (be == null) {
            be = eb.newBlockEntity(pos, state);
            if (be != null) {
                chunk.setBlockEntity(be);
            }
        }
        if (be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container) {
            container.setLootTable(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, loc),
                seed
            );
        }
    }

    public ChunkAccess getChunk() {
        return chunk;
    }

    /**
     * Clears all non-air blocks in all sections of this chunk to AIR prior to live in-place regeneration.
     */
    public void clearAllToAir() {
        for (LevelChunkSection section : sections) {
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        section.setBlockState(x, y, z, AIR, false);
                    }
                }
            }
        }
    }
}
