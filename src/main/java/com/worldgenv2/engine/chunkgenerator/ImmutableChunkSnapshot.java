package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;

import java.util.Objects;

/**
 * Thread-safe, completely immutable block snapshot of a single chunk volume at a specific pipeline stage.
 * Can be safely read by subsequent stages and neighboring chunks concurrently without locks.
 */
public final class ImmutableChunkSnapshot<T> implements VoxelSpace<T> {

    public static final int CHUNK_SIZE_X = 16;
    public static final int CHUNK_SIZE_Z = 16;

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int maxY;
    private final int height;
    private final Object[] blocks;
    private final T airBlock;

    @SuppressWarnings("unchecked")
    public ImmutableChunkSnapshot(int chunkX, int chunkZ, int minY, int maxY, Object[] blocks, T airBlock) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.maxY = maxY;
        this.height = Math.max(1, maxY - minY);
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.airBlock = airBlock;
    }

    public static <T> ImmutableChunkSnapshot<T> capture(int chunkX, int chunkZ, VoxelSpace<T> source, T airBlock) {
        int minY = source.getMinY();
        int maxY = source.getMaxY();
        int height = maxY - minY;
        Object[] blocks = new Object[CHUNK_SIZE_X * CHUNK_SIZE_Z * height];

        int originX = chunkX * CHUNK_SIZE_X;
        int originZ = chunkZ * CHUNK_SIZE_Z;

        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            int wx = originX + lx;
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int wz = originZ + lz;
                int columnOffset = ((lx << 4) | lz) * height;
                for (int y = minY; y < maxY; y++) {
                    T state = source.getBlock(wx, y, wz);
                    blocks[columnOffset + (y - minY)] = state;
                }
            }
        }
        return new ImmutableChunkSnapshot<>(chunkX, chunkZ, minY, maxY, blocks, airBlock);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getBlock(int x, int y, int z) {
        if (y < minY || y >= maxY) {
            return airBlock;
        }
        int lx = x & 15;
        int lz = z & 15;
        int yIdx = y - minY;
        int idx = ((lx << 4) | lz) * height + yIdx;
        T state = (T) blocks[idx];
        return state != null ? state : airBlock;
    }

    @Override
    public void setBlock(int x, int y, int z, T block) {
        throw new UnsupportedOperationException("Cannot mutate ImmutableChunkSnapshot at (" + x + "," + y + "," + z + ")");
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        T b = getBlock(x, y, z);
        return b == null || Objects.equals(b, airBlock);
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getMaxY() {
        return maxY;
    }
}
