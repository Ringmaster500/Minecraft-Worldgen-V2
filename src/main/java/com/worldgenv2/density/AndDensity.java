package com.worldgenv2.density;

import java.util.List;
import java.util.Objects;

/**
 * Returns 1.0 if all inputs are non-zero (&gt; 0 or != 0), otherwise 0.0.
 */
public final class AndDensity implements DensityFunction {

    private final List<DensityFunction> inputs;

    public AndDensity(List<DensityFunction> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        this.inputs = List.copyOf(inputs);
    }

    public AndDensity(DensityFunction... inputs) {
        this(List.of(inputs));
    }

    @Override
    public float compute(float x, float y, float z) {
        if (inputs.isEmpty()) return 0.0f;
        for (DensityFunction input : inputs) {
            if (input.compute(x, y, z) == 0.0f) {
                return 0.0f;
            }
        }
        return 1.0f;
    }

    public List<DensityFunction> getInputs() {
        return inputs;
    }
}
