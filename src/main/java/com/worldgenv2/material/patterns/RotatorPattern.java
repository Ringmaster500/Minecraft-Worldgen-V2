package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;

import java.util.Objects;

/**
 * Rotates neighborhood evaluation coordinates around the Y axis by 90-degree increments.
 */
public final class RotatorPattern<T> implements Pattern<T> {

    private final Pattern<T> child;
    private final int quarterTurns;

    public RotatorPattern(Pattern<T> child, int quarterTurns) {
        this.child = Objects.requireNonNull(child, "child");
        this.quarterTurns = Math.floorMod(quarterTurns, 4);
    }

    public Pattern<T> getChild() {
        return child;
    }

    public int getQuarterTurns() {
        return quarterTurns;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        int rx = x;
        int rz = z;
        switch (quarterTurns) {
            case 1 -> { // 90 deg clockwise
                rx = -z;
                rz = x;
            }
            case 2 -> { // 180 deg
                rx = -x;
                rz = -z;
            }
            case 3 -> { // 270 deg
                rx = z;
                rz = -x;
            }
        }
        return child.matches(rx, y, rz, depth, space, slopeAngle, voxelSpace);
    }
}
