package com.worldgenv2.vector;

import java.util.Objects;

/**
 * Returns an invariant constant 3D vector.
 */
public record ConstantVectorProvider(Vec3d vector) implements VectorProvider {

    public ConstantVectorProvider {
        Objects.requireNonNull(vector, "vector");
    }

    public ConstantVectorProvider(double x, double y, double z) {
        this(new Vec3d(x, y, z));
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        return vector;
    }
}
