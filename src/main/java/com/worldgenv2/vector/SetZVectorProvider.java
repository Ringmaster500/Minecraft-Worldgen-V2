package com.worldgenv2.vector;

import com.worldgenv2.density.DensityFunction;
import java.util.Objects;

/**
 * Replaces the Z component of the input vector with the evaluation of a DensityFunction.
 */
public record SetZVectorProvider(VectorProvider vector, DensityFunction value) implements VectorProvider {

    public SetZVectorProvider {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(value, "value");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d v = vector.getVector(x, y, z);
        float newZ = value.compute((float) x, (float) y, (float) z);
        return v.withZ(newZ);
    }
}
