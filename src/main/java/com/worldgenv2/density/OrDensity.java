package com.worldgenv2.density;

import java.util.List;
import java.util.Objects;

/**
 * Returns 1.0 if at least one input is non-zero, otherwise 0.0.
 */
public final class OrDensity implements DensityFunction {

    private final List<DensityFunction> inputs;

    public OrDensity(List<DensityFunction> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        this.inputs = List.copyOf(inputs);
    }

    public OrDensity(DensityFunction... inputs) {
        this(List.of(inputs));
    }

    @Override
    public float compute(float x, float y, float z) {
        for (DensityFunction input : inputs) {
            if (input.compute(x, y, z) != 0.0f) {
                return 1.0f;
            }
        }
        return 0.0f;
    }

    public List<DensityFunction> getInputs() {
        return inputs;
    }
}
