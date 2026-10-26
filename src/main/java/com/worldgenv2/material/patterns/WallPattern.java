package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Matches steep cliff faces and vertical walls.
 * Evaluates true when the 3D density surface normal indicates a sheer wall (slopeAngle within [minWallAngle, maxWallAngle])
 * or when a horizontal neighbor (±X, ±Z) in the VoxelSpace is air.
 */
public final class WallPattern<T> implements Pattern<T> {

    private final float minWallAngle;
    private final float maxWallAngle;

    public WallPattern() {
        this(60.0f, 120.0f);
    }

    public WallPattern(float minWallAngle, float maxWallAngle) {
        this.minWallAngle = minWallAngle;
        this.maxWallAngle = maxWallAngle;
    }

    public float minWallAngle() {
        return minWallAngle;
    }

    public float maxWallAngle() {
        return maxWallAngle;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        if (slopeAngle >= minWallAngle && slopeAngle <= maxWallAngle) {
            return true;
        }
        // Only query horizontal voxel neighbors if (x, y, z) is already populated as solid in voxelSpace
        if (voxelSpace != null && y >= voxelSpace.getMinY() && y < voxelSpace.getMaxY() && !voxelSpace.isAir(x, y, z)) {
            return voxelSpace.isAir(x + 1, y, z)
                || voxelSpace.isAir(x - 1, y, z)
                || voxelSpace.isAir(x, y, z + 1)
                || voxelSpace.isAir(x, y, z - 1);
        }
        return false;
    }
}
