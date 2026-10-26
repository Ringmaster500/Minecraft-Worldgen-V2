package com.worldgenv2.vector;

import com.worldgenv2.density.ConstantDensity;
import com.worldgenv2.density.DensityFunction;
import java.util.Objects;

/**
 * Normalizes an input vector and scales it to the specified magnitude:
 * m * (v / |v|).
 */
public final class NormalizerVectorProvider implements VectorProvider {

    private final VectorProvider vector;
    private final DensityFunction magnitude;

    public NormalizerVectorProvider(VectorProvider vector) {
        this(vector, new ConstantDensity(1.0f));
    }

    public NormalizerVectorProvider(VectorProvider vector, DensityFunction magnitude) {
        this.vector = Objects.requireNonNull(vector, "vector");
        this.magnitude = magnitude != null ? magnitude : new ConstantDensity(1.0f);
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d v = vector.getVector(x, y, z);
        double len = v.length();
        if (len <= 1e-12) {
            return Vec3d.ZERO;
        }
        float m = magnitude.compute((float) x, (float) y, (float) z);
        return v.scale(m / len);
    }

    public VectorProvider getVectorProvider() {
        return vector;
    }

    public DensityFunction getMagnitude() {
        return magnitude;
    }
}
