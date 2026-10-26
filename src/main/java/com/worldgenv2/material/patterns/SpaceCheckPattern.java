package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Matches voxels where the unobstructed vertical clearance space above the surface lies within [minSpace, maxSpace].
 */
public final class SpaceCheckPattern<T> implements Pattern<T> {

    private final int minSpace;
    private final int maxSpace;

    public SpaceCheckPattern(int minSpace) {
        this(minSpace, Integer.MAX_VALUE);
    }

    public SpaceCheckPattern(int minSpace, int maxSpace) {
        if (minSpace > maxSpace) {
            throw new IllegalArgumentException("minSpace (" + minSpace + ") must be <= maxSpace (" + maxSpace + ")");
        }
        this.minSpace = minSpace;
        this.maxSpace = maxSpace;
    }

    public int minSpace() {
        return minSpace;
    }

    public int maxSpace() {
        return maxSpace;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        return space >= minSpace && space <= maxSpace;
    }
}
