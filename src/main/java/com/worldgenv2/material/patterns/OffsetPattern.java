package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;
import java.util.Objects;

/**
 * Evaluates a child pattern at a translated coordinate (x + dx, y + dy, z + dz).
 * Adjusts depth relative to -dy when evaluating vertical offsets.
 */
public final class OffsetPattern<T> implements Pattern<T> {

    private final Pattern<T> child;
    private final int dx;
    private final int dy;
    private final int dz;

    public OffsetPattern(Pattern<T> child, int dx, int dy, int dz) {
        this.child = Objects.requireNonNull(child, "child");
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public Pattern<T> child() {
        return child;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        int shiftedDepth = Math.max(0, depth - dy);
        return child.matches(x + dx, y + dy, z + dz, shiftedDepth, space, slopeAngle, voxelSpace);
    }
}
