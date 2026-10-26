package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;
import java.util.List;

/**
 * Logical conjunction (AND) pattern combinator: evaluates true only if all child patterns match.
 */
public final class AndPattern<T> implements Pattern<T> {

    private final List<Pattern<T>> patterns;

    public AndPattern(List<Pattern<T>> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    public List<Pattern<T>> patterns() {
        return patterns;
    }

    @Override
    public boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace) {
        for (int i = 0, size = patterns.size(); i < size; i++) {
            if (!patterns.get(i).matches(x, y, z, depth, space, slopeAngle, voxelSpace)) {
                return false;
            }
        }
        return true;
    }
}
