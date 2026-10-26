package com.worldgenv2.density;

import java.util.Objects;

/**
 * Returns 1.0 if |A - B| &lt;= epsilon, otherwise 0.0.
 */
public final class EqualDensity implements DensityFunction {

    private final DensityFunction inputA;
    private final DensityFunction inputB;
    private final float epsilon;

    public EqualDensity(DensityFunction inputA, DensityFunction inputB) {
        this(inputA, inputB, 0.0f);
    }

    public EqualDensity(DensityFunction inputA, DensityFunction inputB, float epsilon) {
        this.inputA = Objects.requireNonNull(inputA, "inputA");
        this.inputB = Objects.requireNonNull(inputB, "inputB");
        this.epsilon = Math.max(0.0f, epsilon);
    }

    @Override
    public float compute(float x, float y, float z) {
        float a = inputA.compute(x, y, z);
        float b = inputB.compute(x, y, z);
        return Math.abs(a - b) <= epsilon ? 1.0f : 0.0f;
    }

    public DensityFunction getInputA() {
        return inputA;
    }

    public DensityFunction getInputB() {
        return inputB;
    }

    public float getEpsilon() {
        return epsilon;
    }
}
