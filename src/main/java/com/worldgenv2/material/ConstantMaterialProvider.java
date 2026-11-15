package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;

/**
 * Invariant material provider that returns a constant block state for all queried coordinates.
 */
public final class ConstantMaterialProvider<T> implements MaterialProvider<T> {

    private final T material;

    public ConstantMaterialProvider(T material) {
        this.material = material;
    }

    public T material() {
        return material;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        return material;
    }

    @Override
    public int runLength(int x, int y, int z, int depthIntoFloor, int spaceAboveFloor, int depthIntoCeiling, int spaceBelowCeiling,
                         float slopeAngle, VoxelSpace<T> spaceRef) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int emptyRunLength(int x, int y, int z, VoxelSpace<T> spaceRef) {
        return Integer.MAX_VALUE;
    }
}
