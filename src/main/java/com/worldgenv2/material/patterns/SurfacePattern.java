package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Matches top-layer surface voxels exposed to air along +Y (depth == 0 or air at y + 1).
 */
public final class SurfacePattern<T> implements Pattern<T> {

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        if (depth == 0) {
            return true;
        }
        if (voxelSpace != null && y + 1 < voxelSpace.getMaxY() && y >= voxelSpace.getMinY()) {
            return !voxelSpace.isAir(x, y, z) && voxelSpace.isAir(x, y + 1, z);
        }
        return false;
    }
}
