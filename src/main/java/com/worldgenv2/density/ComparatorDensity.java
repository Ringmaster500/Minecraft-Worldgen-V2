package com.worldgenv2.density;

import java.util.Objects;

/**
 * Compares two input density functions:
 * Outputs -1.0 if A &lt; B, 0.0 if A == B, and 1.0 if A &gt; B.
 */
public record ComparatorDensity(DensityFunction inputA, DensityFunction inputB) implements DensityFunction {

    public ComparatorDensity {
        Objects.requireNonNull(inputA, "inputA");
        Objects.requireNonNull(inputB, "inputB");
    }

    @Override
    public float compute(float x, float y, float z) {
        float a = inputA.compute(x, y, z);
        float b = inputB.compute(x, y, z);
        if (a < b) return -1.0f;
        if (a > b) return 1.0f;
        return 0.0f;
    }
}
