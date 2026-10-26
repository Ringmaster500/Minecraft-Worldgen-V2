package com.worldgenv2.vector;

import java.util.List;
import java.util.Objects;

/**
 * Performs component-wise addition across a list of VectorProviders.
 */
public final class AdderVectorProvider implements VectorProvider {

    private final List<VectorProvider> vectors;

    public AdderVectorProvider(List<VectorProvider> vectors) {
        Objects.requireNonNull(vectors, "vectors");
        this.vectors = List.copyOf(vectors);
    }

    public AdderVectorProvider(VectorProvider... vectors) {
        this(List.of(vectors));
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        for (VectorProvider vp : vectors) {
            Vec3d v = vp.getVector(x, y, z);
            sumX += v.x();
            sumY += v.y();
            sumZ += v.z();
        }
        return new Vec3d(sumX, sumY, sumZ);
    }

    public List<VectorProvider> getVectors() {
        return vectors;
    }
}
