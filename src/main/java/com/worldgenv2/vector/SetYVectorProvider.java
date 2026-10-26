package com.worldgenv2.vector;

import com.worldgenv2.density.DensityFunction;
import java.util.Objects;

/**
 * Replaces the Y component of the input vector with the evaluation of a DensityFunction.
 */
public record SetYVectorProvider(VectorProvider vector, DensityFunction value) implements VectorProvider {

    public SetYVectorProvider {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(value, "value");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d v = vector.getVector(x, y, z);
        float newY = value.compute((float) x, (float) y, (float) z);
        return v.withY(newY);
    }
}
