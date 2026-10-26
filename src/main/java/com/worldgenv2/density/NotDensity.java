package com.worldgenv2.density;

import java.util.Objects;

/**
 * Logical NOT density node.
 * Returns 1.0 if input == 0.0, otherwise 0.0.
 */
public record NotDensity(DensityFunction input) implements DensityFunction {

    public NotDensity {
        Objects.requireNonNull(input, "input");
    }

    @Override
    public float compute(float x, float y, float z) {
        return input.compute(x, y, z) == 0.0f ? 1.0f : 0.0f;
    }
}
