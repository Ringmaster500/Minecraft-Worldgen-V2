package com.worldgenv2.vector;

import java.util.Objects;

/**
 * Subtracts vector B (subtrahend) from vector A (minuend): A - B.
 * Produces the vector pointing from B to A.
 */
public record SubtracterVectorProvider(VectorProvider minuend, VectorProvider subtrahend) implements VectorProvider {

    public SubtracterVectorProvider {
        Objects.requireNonNull(minuend, "minuend");
        Objects.requireNonNull(subtrahend, "subtrahend");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d a = minuend.getVector(x, y, z);
        Vec3d b = subtrahend.getVector(x, y, z);
        return a.subtract(b);
    }
}
