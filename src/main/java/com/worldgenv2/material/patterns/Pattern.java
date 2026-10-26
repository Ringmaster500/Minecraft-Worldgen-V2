package com.worldgenv2.material.patterns;

import com.worldgenv2.api.VoxelSpace;
import java.util.List;

/**
 * Functional geometric and geological pattern matcher.
 * Evaluates whether a voxel coordinate satisfies structural conditions (surface orientation,
 * slope angle, depth, vertical clearance, bounding volume, or neighbor offsets).
 */
@FunctionalInterface
public interface Pattern<T> {

    /**
     * Evaluates whether the specified voxel context matches this pattern.
     *
     * @param x          World X coordinate
     * @param y          World Y coordinate
     * @param z          World Z coordinate
     * @param depth      Depth in blocks beneath the nearest upward-exposed surface
     * @param space      Unobstructed vertical clearance above the current surface
     * @param slopeAngle Surface normal angle in degrees from +Y (0° = flat floor, 90° = vertical wall, 180° = flat ceiling)
     * @param voxelSpace Target voxel space for neighborhood queries
     * @return true if the pattern matches
     */
    boolean matches(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> voxelSpace);

    default Pattern<T> and(Pattern<T> other) {
        return new AndPattern<>(List.of(this, other));
    }

    default Pattern<T> or(Pattern<T> other) {
        return new OrPattern<>(List.of(this, other));
    }

    default Pattern<T> negate() {
        return new NotPattern<>(this);
    }

    default Pattern<T> offset(int dx, int dy, int dz) {
        return new OffsetPattern<>(this, dx, dy, dz);
    }
}
