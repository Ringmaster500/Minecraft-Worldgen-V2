package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;

/**
 * A scratch chunk used to compute a neighbour's terrain on demand. Reads outside the chunk are air, exactly as in the real
 * chunk space, so a tile and the real chunk it stands for come out identical.
 */
final class TerrainTileSpace<T> implements VoxelSpace<T> {
    private final int originX, originZ, minY, maxY, height;
    private final T air;
    private final Object[] blocks;

    TerrainTileSpace(int chunkX, int chunkZ, int minY, int maxY, T air) {
        this.originX = chunkX << 4;
        this.originZ = chunkZ << 4;
        this.minY = minY;
        this.maxY = maxY;
        this.height = maxY - minY;
        this.air = air;
        this.blocks = new Object[256 * height];
    }

    @Override @SuppressWarnings("unchecked")
    public T getBlock(int x, int y, int z) {
        int lx = x - originX, lz = z - originZ;
        if (y < minY || y >= maxY || lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return air;
        Object b = blocks[((lx << 4) | lz) * height + (y - minY)];
        return b == null ? air : (T) b;
    }

    @Override
    public void setBlock(int x, int y, int z, T block) {
        int lx = x - originX, lz = z - originZ;
        if (block == null || y < minY || y >= maxY || lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return;
        blocks[((lx << 4) | lz) * height + (y - minY)] = block;
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        T b = getBlock(x, y, z);
        return b == null || b.equals(air);
    }

    @Override public int getMinY() { return minY; }

    @Override public int getMaxY() { return maxY; }
}
