package com.worldgenv2.density;

import java.util.Objects;

/**
 * Returns 1.0 if A &gt; B, otherwise 0.0.
 */
public record GreaterThanDensity(DensityFunction inputA, DensityFunction inputB) implements DensityFunction {

    public GreaterThanDensity {
        Objects.requireNonNull(inputA, "inputA");
        Objects.requireNonNull(inputB, "inputB");
    }

    @Override
    public float compute(float x, float y, float z) {
        return inputA.compute(x, y, z) > inputB.compute(x, y, z) ? 1.0f : 0.0f;
    }
}
