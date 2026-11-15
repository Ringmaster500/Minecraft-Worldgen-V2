package com.worldgenv2.density;

import java.util.Objects;

/**
 * Calculates square root of input density: {@code f(x, y, z) = sqrt(max(0, input))}.
 */
public final class SqrtDensity implements DensityFunction {

    private final DensityFunction input;

    public SqrtDensity(DensityFunction input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    public DensityFunction input() {
        return input;
    }

    @Override
    public float compute(float x, float y, float z) {
        float v = input.compute(x, y, z);
        return v <= 0.0f ? 0.0f : (float) Math.sqrt(v);
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (input.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (input.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        input.computeColumn(x, z, minY, maxY, out);
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            float v = out[i];
            out[i] = v <= 0.0f ? 0.0f : (float) Math.sqrt(v);
        }
    }
}
