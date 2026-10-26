package com.worldgenv2.density;

import java.util.Objects;

/**
 * Replaces the X sample coordinate with the evaluation of an override density function
 * before evaluating the source density function: source(override(x, y, z), y, z).
 */
public record XOverrideDensity(DensityFunction source, DensityFunction override) implements DensityFunction {

    public XOverrideDensity {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(override, "override");
    }

    @Override
    public float compute(float x, float y, float z) {
        float newX = override.compute(x, y, z);
        return source.compute(newX, y, z);
    }
}
