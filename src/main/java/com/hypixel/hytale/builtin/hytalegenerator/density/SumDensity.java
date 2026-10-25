package com.hypixel.hytale.builtin.hytalegenerator.density;

import java.util.List;

public final class SumDensity implements DensityFunction {
    private final DensityFunction[] inputs;

    public SumDensity(DensityFunction... inputs) {
        this.inputs = inputs;
    }

    public SumDensity(List<DensityFunction> inputs) {
        this.inputs = inputs.toArray(new DensityFunction[0]);
    }

    @Override
    public float compute(float x, float y, float z) {
        float sum = 0.0f;
        for (DensityFunction input : inputs) {
            sum += input.compute(x, y, z);
        }
        return sum;
    }
}
