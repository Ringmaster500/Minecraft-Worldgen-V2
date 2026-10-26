package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Matches voxels whose surface normal angle theta = arccos(n_y) in degrees falls within [minAngleDeg, maxAngleDeg].
 * - [0°, 35°]: Gentle meadow slope
 * - [35°, 60°]: Steep scree/talus grade
 * - [60°, 90°+]: Sheer cliff bluff
 */
public final class SlopePattern<T> implements Pattern<T> {

    private final float minAngleDeg;
    private final float maxAngleDeg;

    public SlopePattern(float minAngleDeg, float maxAngleDeg) {
        if (minAngleDeg > maxAngleDeg) {
            throw new IllegalArgumentException("minAngleDeg (" + minAngleDeg + ") must be <= maxAngleDeg (" + maxAngleDeg + ")");
        }
        this.minAngleDeg = minAngleDeg;
        this.maxAngleDeg = maxAngleDeg;
    }

    public float minAngleDeg() {
        return minAngleDeg;
    }

    public float maxAngleDeg() {
        return maxAngleDeg;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        return slopeAngle >= minAngleDeg && slopeAngle <= maxAngleDeg;
    }
}
