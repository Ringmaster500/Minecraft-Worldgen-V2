package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;
import java.util.Objects;

/**
 * Logical negation (NOT) pattern combinator: inverts the result of the child pattern.
 */
public final class NotPattern<T> implements Pattern<T> {

    private final Pattern<T> child;

    public NotPattern(Pattern<T> child) {
        this.child = Objects.requireNonNull(child, "child");
    }

    public Pattern<T> child() {
        return child;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        return !child.matches(x, y, z, depth, space, slopeAngle, voxelSpace);
    }
}
