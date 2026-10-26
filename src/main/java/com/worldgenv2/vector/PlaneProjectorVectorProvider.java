package com.worldgenv2.vector;

import java.util.Objects;

/**
 * Projects a vector onto the geometric plane spanned by PlaneA and PlaneB:
 * v_proj = v - (v . n) * n, where n = (PlaneA x PlaneB) / |PlaneA x PlaneB|.
 */
public record PlaneProjectorVectorProvider(
    VectorProvider vector,
    VectorProvider planeA,
    VectorProvider planeB
) implements VectorProvider {

    public PlaneProjectorVectorProvider {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(planeA, "planeA");
        Objects.requireNonNull(planeB, "planeB");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d v = vector.getVector(x, y, z);
        Vec3d a = planeA.getVector(x, y, z);
        Vec3d b = planeB.getVector(x, y, z);

        Vec3d normal = a.cross(b).normalize();
        if (normal.lengthSquared() <= 1e-12) {
            return v;
        }

        double dot = v.dot(normal);
        return v.subtract(normal.scale(dot));
    }
}
