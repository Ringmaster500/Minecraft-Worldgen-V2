package com.hypixel.hytale.builtin.hytalegenerator.density;

import java.util.List;

public final class MinDensity implements DensityFunction {
    private final DensityFunction[] inputs;

    public MinDensity(DensityFunction... inputs) {
        this.inputs = inputs;
    }

    public MinDensity(List<DensityFunction> inputs) {
        this.inputs = inputs.toArray(new DensityFunction[0]);
    }

    @Override
    public float compute(float x, float y, float z) {
        if (inputs.length == 0) return 0.0f;
        float min = inputs[0].compute(x, y, z);
        for (int i = 1; i < inputs.length; i++) {
            min = Math.min(min, inputs[i].compute(x, y, z));
        }
        return min;
    }
}
