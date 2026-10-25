package com.hypixel.hytale.builtin.hytalegenerator.density;

import java.util.List;

public final class ProductDensity implements DensityFunction {
    private final DensityFunction[] inputs;

    public ProductDensity(DensityFunction... inputs) {
        this.inputs = inputs;
    }

    public ProductDensity(List<DensityFunction> inputs) {
        this.inputs = inputs.toArray(new DensityFunction[0]);
    }

    @Override
    public float compute(float x, float y, float z) {
        float product = 1.0f;
        for (DensityFunction input : inputs) {
            product *= input.compute(x, y, z);
        }
        return product;
    }
}
