package com.worldgenv2.density;

import java.util.Arrays;
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

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) return;

        if (inputs.length == 0) {
            Arrays.fill(out, 0, height, 1.0f);
            return;
        }

        inputs[0].computeColumn(x, z, minY, maxY, out);
        if (inputs.length == 1) {
            return;
        }

        float[] scratch = new float[height];
        for (int k = 1; k < inputs.length; k++) {
            inputs[k].computeColumn(x, z, minY, maxY, scratch);
            for (int i = 0; i < height; i++) {
                out[i] *= scratch[i];
            }
        }
    }

    public DensityFunction[] getInputs() {
        return inputs;
    }
}
