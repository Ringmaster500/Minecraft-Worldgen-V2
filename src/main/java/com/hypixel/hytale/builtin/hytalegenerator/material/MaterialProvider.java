package com.hypixel.hytale.builtin.hytalegenerator.material;

import com.hypixel.hytale.builtin.hytalegenerator.api.VoxelSpace;

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
}
