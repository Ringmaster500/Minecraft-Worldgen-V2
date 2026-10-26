package com.worldgenv2.density;

import java.util.Objects;

/**
 * Replaces the Y sample coordinate with the evaluation of an override density function
 * before evaluating the source density function: source(x, override(x, y, z), z).
 */
public record YOverrideDensity(DensityFunction source, DensityFunction override) implements DensityFunction {

    public YOverrideDensity {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(override, "override");
    }

    @Override
    public float compute(float x, float y, float z) {
        float newY = override.compute(x, y, z);
        return source.compute(x, newY, z);
    }
}
