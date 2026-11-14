package com.worldgenv2.assignments;

import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;
import java.util.List;
import java.util.Objects;

/**
 * Assigns Props based on vertical world elevation (Y coordinate) bands.
 * Enables stratified flora/structures (e.g. foothill shrubs, alpine pines, summit cairns).
 */
public final class SandwichAssignment<T> implements Assignments<T> {

    public record HeightDelimiter<T>(double minY, double maxY, Assignments<T> assignments) {
        public boolean matches(double y) {
            return y >= minY && y < maxY;
        }
    }

    private final List<HeightDelimiter<T>> delimiters;

    public SandwichAssignment(List<HeightDelimiter<T>> delimiters) {
        Objects.requireNonNull(delimiters, "delimiters");
        this.delimiters = List.copyOf(delimiters);
    }

    @Override
    public Prop<T> assign(Vec3d position, long seed) {
        double y = position.y();
        for (HeightDelimiter<T> d : delimiters) {
            if (d.matches(y)) {
                return d.assignments().assign(position, seed);
            }
        }
        return null;
    }

    public List<HeightDelimiter<T>> getDelimiters() {
        return delimiters;
    }

    @Override
    public int horizontalReach() {
        int r = 0;
        for (HeightDelimiter<T> d : delimiters) r = Math.max(r, d.assignments().horizontalReach());
        return r;
    }
}
