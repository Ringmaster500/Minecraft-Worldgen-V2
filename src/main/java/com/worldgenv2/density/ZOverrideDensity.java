package com.worldgenv2.density;

import java.util.Objects;

/**
 * Replaces the Z sample coordinate with the evaluation of an override density function
 * before evaluating the source density function: source(x, y, override(x, y, z)).
 */
public record ZOverrideDensity(DensityFunction source, DensityFunction override) implements DensityFunction {

    public ZOverrideDensity {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(override, "override");
    }

    @Override
    public float compute(float x, float y, float z) {
        float newZ = override.compute(x, y, z);
        return source.compute(x, y, newZ);
    }
}
