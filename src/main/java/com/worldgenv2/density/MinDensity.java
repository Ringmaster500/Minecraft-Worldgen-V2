package com.worldgenv2.density;

import java.util.Arrays;
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

    @Override public boolean yIndependent() { return allFlat(inputs); }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
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
                out[i] = Math.min(out[i], scratch[i]);
            }
        }
    }

    public DensityFunction[] getInputs() {
        return inputs;
    }
    private static boolean allFlat(DensityFunction[] in) { for (DensityFunction f : in) if (!f.yIndependent()) return false; return true; }
}
