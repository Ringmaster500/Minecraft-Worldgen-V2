package com.worldgenv2.density;

import java.util.Objects;

/**
 * Calculates exponential power of input density: {@code f(x, y, z) = sign(input) * |input|^exponent}.
 * Preserves the sign for continuous surface contours.
 */
public final class PowDensity implements DensityFunction {

    private final DensityFunction input;
    private final float exponent;

    public PowDensity(DensityFunction input, float exponent) {
        this.input = Objects.requireNonNull(input, "input");
        this.exponent = exponent;
    }

    public DensityFunction input() {
        return input;
    }

    public float exponent() {
        return exponent;
    }

    @Override
    public float compute(float x, float y, float z) {
        float v = input.compute(x, y, z);
        if (v == 0.0f) return 0.0f;
        float sign = Math.signum(v);
        return sign * (float) Math.pow(Math.abs(v), exponent);
    }

    @Override public boolean yIndependent() { return input.yIndependent(); }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        input.computeColumn(x, z, minY, maxY, out);
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            float v = out[i];
            if (v == 0.0f) {
                out[i] = 0.0f;
            } else {
                float sign = Math.signum(v);
                out[i] = sign * (float) Math.pow(Math.abs(v), exponent);
            }
        }
    }
}
