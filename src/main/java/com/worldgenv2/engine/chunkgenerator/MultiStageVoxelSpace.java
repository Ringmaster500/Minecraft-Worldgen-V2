package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;

import java.util.Objects;

/**
 * Stage-aware {@link VoxelSpace} decorator that seamlessly routes cross-chunk neighbor read queries
 * to immutable stage snapshots from {@link StageContextCache}, preventing circular locks and infinite cascading chunk generation.
 */
public final class MultiStageVoxelSpace<T> implements VoxelSpace<T> {

    private final int chunkX;
    private final int chunkZ;
    private final int currentStage;
    private final VoxelSpace<T> primaryChunkSpace;
    private final StageContextCache<T> contextCache;
    private final T airBlock;

    public MultiStageVoxelSpace(
        int chunkX,
        int chunkZ,
        int currentStage,
        VoxelSpace<T> primaryChunkSpace,
        StageContextCache<T> contextCache,
        T airBlock
    ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.currentStage = currentStage;
        this.primaryChunkSpace = Objects.requireNonNull(primaryChunkSpace, "primaryChunkSpace");
        this.contextCache = contextCache;
        this.airBlock = airBlock;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    private boolean isInsidePrimaryChunk(int x, int z) {
        return (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }

    @Override
    public T getBlock(int x, int y, int z) {
        if (isInsidePrimaryChunk(x, z)) {
            return primaryChunkSpace.getBlock(x, y, z);
        }

        // Query neighbor chunk's snapshot from the prior stage
        int neighborCx = x >> 4;
        int neighborCz = z >> 4;
        if (contextCache != null && currentStage > 0) {
            ImmutableChunkSnapshot<T> snapshot = contextCache.getSnapshot(neighborCx, neighborCz, currentStage - 1);
            if (snapshot != null) {
                return snapshot.getBlock(x, y, z);
            }
        }

        return airBlock;
    }

    @Override
    public void setBlock(int x, int y, int z, T block) {
        if (isInsidePrimaryChunk(x, z)) {
            primaryChunkSpace.setBlock(x, y, z, block);
        }
        // Writes outside the primary chunk are strictly prevented from causing uncoordinated mutations
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        if (isInsidePrimaryChunk(x, z)) {
            return primaryChunkSpace.isAir(x, y, z);
        }

        int neighborCx = x >> 4;
        int neighborCz = z >> 4;
        if (contextCache != null && currentStage > 0) {
            ImmutableChunkSnapshot<T> snapshot = contextCache.getSnapshot(neighborCx, neighborCz, currentStage - 1);
            if (snapshot != null) {
                return snapshot.isAir(x, y, z);
            }
        }

        return true;
    }

    @Override
    public int getMinY() {
        return primaryChunkSpace.getMinY();
    }

    @Override
    public int getMaxY() {
        return primaryChunkSpace.getMaxY();
    }
}
