package com.worldgenv2.assignments;

import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;

/**
 * Decouples prop selection from spatial positions.
 * Assigns a {@link Prop} to each generated 3D position based on vertical height,
 * continuous density evaluations, or statistical weights.
 */
@FunctionalInterface
public interface Assignments<T> {

    /**
     * Resolves the {@link Prop} to stamp at the specified 3D world position.
     *
     * @param position world coordinate position
     * @param seed deterministic world / stage seed
     * @return selected prop, or null if no prop should be placed
     */
    Prop<T> assign(Vec3d position, long seed);
}
