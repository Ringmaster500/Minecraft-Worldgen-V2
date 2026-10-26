package com.worldgenv2.assignments;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;
import java.util.List;
import java.util.Objects;

/**
 * Assigns Props based on the evaluation of a continuous {@link DensityFunction} field at each position.
 */
public final class FieldFunctionAssignment<T> implements Assignments<T> {

    public record Delimiter<T>(double min, double max, Assignments<T> assignments) {
        public boolean matches(double value) {
            return value >= min && value < max;
        }
    }

    private final DensityFunction fieldFunction;
    private final List<Delimiter<T>> delimiters;

    public FieldFunctionAssignment(DensityFunction fieldFunction, List<Delimiter<T>> delimiters) {
        this.fieldFunction = Objects.requireNonNull(fieldFunction, "fieldFunction");
        this.delimiters = List.copyOf(Objects.requireNonNull(delimiters, "delimiters"));
    }

    @Override
    public Prop<T> assign(Vec3d position, long seed) {
        float val = fieldFunction.compute((float) position.x(), (float) position.y(), (float) position.z());
        for (Delimiter<T> d : delimiters) {
            if (d.matches(val)) {
                return d.assignments().assign(position, seed);
            }
        }
        return null;
    }

    public DensityFunction getFieldFunction() {
        return fieldFunction;
    }

    public List<Delimiter<T>> getDelimiters() {
        return delimiters;
    }
}
