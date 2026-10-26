package com.worldgenv2.density;

import java.util.Arrays;
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

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) return;

        if (inputs.length == 0) {
            Arrays.fill(out, 0, height, 0.0f);
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
                out[i] = Math.max(out[i], scratch[i]);
            }
        }
    }

    public DensityFunction[] getInputs() {
        return inputs;
    }
}
