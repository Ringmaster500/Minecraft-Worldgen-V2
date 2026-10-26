package com.worldgenv2.vector;

import java.util.Objects;

/**
 * Computes the cross product of two vector providers: VectorA x VectorB.
 * Produces a vector perpendicular to both inputs, with magnitude |A| * |B| * sin(theta).
 */
public record CrossVectorProvider(VectorProvider vectorA, VectorProvider vectorB) implements VectorProvider {

    public CrossVectorProvider {
        Objects.requireNonNull(vectorA, "vectorA");
        Objects.requireNonNull(vectorB, "vectorB");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d a = vectorA.getVector(x, y, z);
        Vec3d b = vectorB.getVector(x, y, z);
        return a.cross(b);
    }
}
