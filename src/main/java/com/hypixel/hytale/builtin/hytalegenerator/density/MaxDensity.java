package com.hypixel.hytale.builtin.hytalegenerator.density;

import java.util.List;

public final class MaxDensity implements DensityFunction {
    private final DensityFunction[] inputs;

    public MaxDensity(DensityFunction... inputs) {
        this.inputs = inputs;
    }

    public MaxDensity(List<DensityFunction> inputs) {
        this.inputs = inputs.toArray(new DensityFunction[0]);
    }

    @Override
    public float compute(float x, float y, float z) {
        if (inputs.length == 0) return 0.0f;
        float max = inputs[0].compute(x, y, z);
        for (int i = 1; i < inputs.length; i++) {
            max = Math.max(max, inputs[i].compute(x, y, z));
        }
        return max;
    }
}
