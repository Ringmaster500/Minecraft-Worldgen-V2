package com.worldgenv2.assignments;

import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;
import java.util.Objects;

/**
 * Assigns an invariant Prop to all positions.
 */
public record ConstantAssignment<T>(Prop<T> prop) implements Assignments<T> {

    public ConstantAssignment {
        Objects.requireNonNull(prop, "prop");
    }

    @Override
    public Prop<T> assign(Vec3d position, long seed) {
        return prop;
    }

    @Override
    public int horizontalReach() {
        return prop.horizontalReach();
    }
}
