package com.worldgenv2.density;

import java.util.Arrays;

public final class ConstantDensity implements DensityFunction {
    private final float value;

    public ConstantDensity(float value) {
        this.value = value;
    }

    @Override
    public float compute(float x, float y, float z) {
        return value;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height > 0) {
            Arrays.fill(out, 0, height, value);
        }
    }

    public float getValue() {
        return value;
    }
    @Override public boolean yIndependent() { return true; }
    @Override public boolean xzIndependent() { return true; }
}
