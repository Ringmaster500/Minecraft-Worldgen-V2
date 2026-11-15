package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;

/**
 * Strategy interface dictating block material selection for solid voxels.
 */
@FunctionalInterface
public interface MaterialProvider<T> {

    /**
     * Resolves the material at the specified voxel coordinate.
     * 
     * @param x World X
     * @param y World Y
     * @param z World Z
     * @param depth Depth beneath the nearest upward-exposed surface
     * @param space Clearance air height above the surface
     * @param slopeAngle Angle in degrees from horizontal (0° = flat, 90° = vertical cliff)
     * @param spaceRef Reference to the voxel reading context
     */
    T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef);

    /**
     * Resolves the material at the specified voxel coordinate with both floor and ceiling contexts.
     *
     * @param depthIntoFloor Depth beneath the nearest upward-exposed floor surface
     * @param spaceAboveFloor Clearance air height above the floor surface
     * @param depthIntoCeiling Depth above the nearest downward-exposed ceiling surface (-1 if none)
     * @param spaceBelowCeiling Clearance air height below the ceiling surface
     */
    default T getMaterial(int x, int y, int z, int depthIntoFloor, int spaceAboveFloor,
                          int depthIntoCeiling, int spaceBelowCeiling,
                          float slopeAngle, VoxelSpace<T> spaceRef) {
        return getMaterial(x, y, z, depthIntoFloor, spaceAboveFloor, slopeAngle, spaceRef);
    }

    /**
     * How many blocks, going straight down from this one through solid ground (one block deeper each step), give exactly the same
     * answer as this one, so the caller can resolve them all at once. 1 means no promise and is always safe. Only providers that
     * know their answer cannot change (not a function of the block position, nor of the ceiling above) say more.
     */
    default int runLength(int x, int y, int z, int depthIntoFloor, int spaceAboveFloor,
                          int depthIntoCeiling, int spaceBelowCeiling, float slopeAngle, VoxelSpace<T> spaceRef) {
        return 1;
    }

    /** Like {@link #runLength} for the empty side: how many blocks straight down give the same {@link #getEmptyMaterial}. */
    default int emptyRunLength(int x, int y, int z, VoxelSpace<T> spaceRef) {
        return 1;
    }

    /**
     * Hytale's Solidity splits a provider in two: one for solid blocks, one for empty ones (where fluids come from). A provider that
     * has an empty side says so here, and the terrain stage asks it about every empty block.
     */
    default boolean providesEmpty() {
        return false;
    }

    /** The material for an empty (air) position, or null to leave it as it is. Only asked when {@link #providesEmpty()}. */
    default T getEmptyMaterial(int x, int y, int z, VoxelSpace<T> spaceRef) {
        return null;
    }
}
