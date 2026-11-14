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
    default void setBlockLoot(int x, int y, int z, String lootTableId, long seed) {}
}
