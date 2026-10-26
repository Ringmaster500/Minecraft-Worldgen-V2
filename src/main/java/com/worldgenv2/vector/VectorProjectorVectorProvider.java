package com.worldgenv2.vector;

import java.util.Objects;

/**
 * Projects a source vector onto a target vector:
 * proj_t(s) = ((s . t) / (t . t)) * t.
 */
public record VectorProjectorVectorProvider(VectorProvider source, VectorProvider target) implements VectorProvider {

    public VectorProjectorVectorProvider {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d s = source.getVector(x, y, z);
        Vec3d t = target.getVector(x, y, z);

        double tLenSq = t.lengthSquared();
        if (tLenSq <= 1e-12) {
            return Vec3d.ZERO;
        }

        double factor = s.dot(t) / tLenSq;
        return t.scale(factor);
    }
}
