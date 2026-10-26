package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Matches voxels whose world coordinates fall within an axis-aligned bounding box
 * [minX, minY, minZ] to [maxX, maxY, maxZ] (inclusive).
 * Also provides a factory method for elevation-only strata bands.
 */
public final class CuboidPattern<T> implements Pattern<T> {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public CuboidPattern(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * Creates a horizontal elevation band matcher over [minY, maxY] across all X and Z coordinates.
     */
    public static <T> CuboidPattern<T> elevationBand(int minY, int maxY) {
        return new CuboidPattern<>(
            Integer.MIN_VALUE, minY, Integer.MIN_VALUE,
            Integer.MAX_VALUE, maxY, Integer.MAX_VALUE
        );
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }
}
