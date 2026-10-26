package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;
import java.util.List;

/**
 * Logical disjunction (OR) pattern combinator: evaluates true if at least one child pattern matches.
 */
public final class OrPattern<T> implements Pattern<T> {

    private final List<Pattern<T>> patterns;

    public OrPattern(List<Pattern<T>> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    public List<Pattern<T>> patterns() {
        return patterns;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        for (int i = 0, size = patterns.size(); i < size; i++) {
            if (patterns.get(i).matches(x, y, z, depth, space, slopeAngle, voxelSpace)) {
                return true;
            }
        }
        return false;
    }
}
