package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

/**
 * Evaluates the void/air gap distance between opposite solid surfaces (either vertically via clearance space
 * or horizontally across narrow canyons/crevasses in VoxelSpace).
 */
public final class GapPattern<T> implements Pattern<T> {

    private final int minGap;
    private final int maxGap;
    private final boolean checkHorizontal;

    public GapPattern(int minGap, int maxGap) {
        this(minGap, maxGap, false);
    }

    public GapPattern(int minGap, int maxGap, boolean checkHorizontal) {
        if (minGap > maxGap) {
            throw new IllegalArgumentException("minGap (" + minGap + ") must be <= maxGap (" + maxGap + ")");
        }
        this.minGap = minGap;
        this.maxGap = maxGap;
        this.checkHorizontal = checkHorizontal;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        if (!checkHorizontal || voxelSpace == null) {
            return space >= minGap && space <= maxGap;
        }
        int gapX = measureAxisGap(voxelSpace, x, y, z, 1, 0);
        int gapZ = measureAxisGap(voxelSpace, x, y, z, 0, 1);
        int smallestGap = Math.min(gapX, gapZ);
        return smallestGap >= minGap && smallestGap <= maxGap;
    }

    private int measureAxisGap(VoxelSpace<T> voxelSpace, int x, int y, int z, int stepX, int stepZ) {
        int count = 0;
        for (int i = 1; i <= maxGap + 1; i++) {
            if (voxelSpace.isAir(x + stepX * i, y, z + stepZ * i)) {
                count++;
            } else {
                break;
            }
        }
        for (int i = 1; i <= maxGap + 1; i++) {
            if (voxelSpace.isAir(x - stepX * i, y, z - stepZ * i)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
