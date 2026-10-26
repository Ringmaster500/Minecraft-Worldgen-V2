package com.worldgenv2.density;

import java.util.List;
import java.util.Objects;

/**
 * Logical XOR density node.
 * Returns 1.0 if an odd number of inputs are non-zero, otherwise 0.0.
 */
public final class XorDensity implements DensityFunction {

    private final List<DensityFunction> inputs;

    public XorDensity(List<DensityFunction> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        this.inputs = List.copyOf(inputs);
    }

    public XorDensity(DensityFunction... inputs) {
        this(List.of(inputs));
    }

    @Override
    public float compute(float x, float y, float z) {
        int nonZeroCount = 0;
        for (DensityFunction input : inputs) {
            if (input.compute(x, y, z) != 0.0f) {
                nonZeroCount++;
            }
        }
        return (nonZeroCount & 1) == 1 ? 1.0f : 0.0f;
    }

    public List<DensityFunction> getInputs() {
        return inputs;
    }
}
