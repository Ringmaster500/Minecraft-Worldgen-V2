package com.worldgenv2.vector;

import com.worldgenv2.density.DensityFunction;
import java.util.Objects;

/**
 * Multiplies an input vector by a scalar evaluated from a DensityFunction:
 * k * (x, y, z) = (kx, ky, kz).
 */
public record ScalarMultiplierVectorProvider(VectorProvider vector, DensityFunction scalar) implements VectorProvider {

    public ScalarMultiplierVectorProvider {
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(scalar, "scalar");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        Vec3d v = vector.getVector(x, y, z);
        float s = scalar.compute((float) x, (float) y, (float) z);
        return v.scale(s);
    }
}
