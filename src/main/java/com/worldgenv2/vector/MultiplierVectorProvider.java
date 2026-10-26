package com.worldgenv2.vector;

import java.util.List;
import java.util.Objects;

/**
 * Performs component-wise multiplication across a list of VectorProviders:
 * (x1 * x2 * ..., y1 * y2 * ..., z1 * z2 * ...).
 */
public final class MultiplierVectorProvider implements VectorProvider {

    private final List<VectorProvider> vectors;

    public MultiplierVectorProvider(List<VectorProvider> vectors) {
        Objects.requireNonNull(vectors, "vectors");
        this.vectors = List.copyOf(vectors);
    }

    public MultiplierVectorProvider(VectorProvider... vectors) {
        this(List.of(vectors));
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        double prodX = 1.0;
        double prodY = 1.0;
        double prodZ = 1.0;
        for (VectorProvider vp : vectors) {
            Vec3d v = vp.getVector(x, y, z);
            prodX *= v.x();
            prodY *= v.y();
            prodZ *= v.z();
        }
        return new Vec3d(prodX, prodY, prodZ);
    }

    public List<VectorProvider> getVectors() {
        return vectors;
    }
}
