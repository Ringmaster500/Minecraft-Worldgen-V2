package com.worldgenv2.api;

/**
 * High-performance 3D voxel read/write abstraction.
 * Decouples Hytale WorldGen V2 from Minecraft block structures.
 * 
 * @param <T> Block state representation (e.g., Minecraft BlockState or raw short ID)
 */
public interface VoxelSpace<T> {

    /**
     * Retrieves the material at the specified coordinate.
     */
    T getBlock(int x, int y, int z);

    /**
     * Mutates the material at the specified coordinate.
     */
    void setBlock(int x, int y, int z, T block);

    /**
     * Checks if the coordinate represents empty space/air.
     */
    boolean isAir(int x, int y, int z);

    /**
     * Minimum valid Y level in this space.
     */
    int getMinY();

    /**
     * Maximum valid Y level in this space.
     */
    int getMaxY();

    /**
     * Optional hook to attach a loot table to a container block entity at (x, y, z).
     * Pure-Java test spaces ignore this by default.
     */
    /** Sets heights [yFrom, yTo) of one column to the same block. Implementations may do better than one call per block. */
    default void setBlockRun(int x, int z, int yFrom, int yTo, T block) { for (int y = yFrom; y < yTo; y++) setBlock(x, y, z, block); }

    /**
     * A bulk write is about to happen (many {@link #setBlockRun} calls into an empty space); implementations may defer bookkeeping until
     * {@link #endBulk}. Reads in between are not allowed.
     */
    default void beginBulk() {}

    /** Finishes a bulk write: the space is consistent again. */
    default void endBulk() {}

    default void setBlockLoot(int x, int y, int z, String lootTableId, long seed) {}
}
