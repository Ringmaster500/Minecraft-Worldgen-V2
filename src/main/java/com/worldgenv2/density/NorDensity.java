package com.worldgenv2.density;

import java.util.List;
import java.util.Objects;

/**
 * Logical NOR density node.
 * Returns 1.0 if every input is 0.0, otherwise 0.0.
 */
public final class NorDensity implements DensityFunction {

    private final List<DensityFunction> inputs;

    public NorDensity(List<DensityFunction> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        this.inputs = List.copyOf(inputs);
    }

    public NorDensity(DensityFunction... inputs) {
        this(List.of(inputs));
    }

    @Override
    public float compute(float x, float y, float z) {
        for (DensityFunction input : inputs) {
            if (input.compute(x, y, z) != 0.0f) {
                return 0.0f;
            }
        }
        return 1.0f;
    }

    public List<DensityFunction> getInputs() {
        return inputs;
    }
}
