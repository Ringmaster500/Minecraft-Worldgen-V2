package com.worldgenv2.vector;

import com.worldgenv2.density.DensityFunction;
import java.util.Objects;

/**
 * Computes the 3D continuous gradient vector grad(D) = (dD/dx, dD/dy, dD/dz) of a DensityFunction
 * using central difference numerical differentiation:
 * dD/dx = (D(x + delta, y, z) - D(x - delta, y, z)) / (2 * delta).
 */
public final class DensityGradientVectorProvider implements VectorProvider {

    private final DensityFunction density;
    private final double sampleDistance;

    public DensityGradientVectorProvider(DensityFunction density) {
        this(density, 1.0);
    }

    public DensityGradientVectorProvider(DensityFunction density, double sampleDistance) {
        this.density = Objects.requireNonNull(density, "density");
        this.sampleDistance = sampleDistance > 0.0 ? sampleDistance : 1.0;
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;
        float delta = (float) sampleDistance;
        float invTwoDelta = 1.0f / (2.0f * delta);

        float dx = (density.compute(fx + delta, fy, fz) - density.compute(fx - delta, fy, fz)) * invTwoDelta;
        float dy = (density.compute(fx, fy + delta, fz) - density.compute(fx, fy - delta, fz)) * invTwoDelta;
        float dz = (density.compute(fx, fy, fz + delta) - density.compute(fx, fy, fz - delta)) * invTwoDelta;

        return new Vec3d(dx, dy, dz);
    }

    public DensityFunction getDensity() {
        return density;
    }

    public double getSampleDistance() {
        return sampleDistance;
    }
}
