package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Matches voxels whose depth beneath the nearest surface lies within [minDepth, maxDepth] (inclusive).
 */
public final class DepthPattern<T> implements Pattern<T> {

    private final int minDepth;
    private final int maxDepth;

    public DepthPattern(int minDepth, int maxDepth) {
        if (minDepth > maxDepth) {
            throw new IllegalArgumentException("minDepth (" + minDepth + ") must be <= maxDepth (" + maxDepth + ")");
        }
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
    }

    public int minDepth() {
        return minDepth;
    }

    public int maxDepth() {
        return maxDepth;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        return depth >= minDepth && depth <= maxDepth;
    }
}
