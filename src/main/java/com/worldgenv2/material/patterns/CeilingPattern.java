package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Matches cave ceilings and overhang undersides where the voxel faces open air along -Y.
 * Detects downward-pointing 3D density normals (slopeAngle > minCeilingAngle) or air at (x, y - 1, z).
 */
public final class CeilingPattern<T> implements Pattern<T> {

    private final float minCeilingAngle;

    public CeilingPattern() {
        this(115.0f);
    }

    public CeilingPattern(float minCeilingAngle) {
        this.minCeilingAngle = minCeilingAngle;
    }

    public float minCeilingAngle() {
        return minCeilingAngle;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        if (slopeAngle >= minCeilingAngle) {
            return true;
        }
        // Only query voxel neighbor at y - 1 if (x, y, z) is already populated as solid in voxelSpace
        if (voxelSpace != null && y - 1 >= voxelSpace.getMinY() && y < voxelSpace.getMaxY() && !voxelSpace.isAir(x, y, z)) {
            return voxelSpace.isAir(x, y - 1, z);
        }
        return false;
    }
}
