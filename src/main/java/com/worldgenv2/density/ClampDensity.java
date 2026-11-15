package com.worldgenv2.density;

import java.util.Objects;

/**
 * Constrains density values to a specified [min, max] range: f(x, y, z) = clamp(g(x, y, z), min, max).
 * Used to enforce canonical [-1, 1] bounds or sculpt flat plateau shelves.
 */
public final class ClampDensity implements DensityFunction {

    private final DensityFunction input;
    private final float min;
    private final float max;

    public ClampDensity(DensityFunction input, float min, float max) {
        if (min > max) {
            throw new IllegalArgumentException("min (" + min + ") must be <= max (" + max + ")");
        }
        this.input = Objects.requireNonNull(input, "input");
        this.min = min;
        this.max = max;
    }

    public DensityFunction input() {
        return input;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    @Override
    public float compute(float x, float y, float z) {
        return Math.clamp(input.compute(x, y, z), min, max);
    }

    @Override public boolean yIndependent() { return input.yIndependent(); }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        input.computeColumn(x, z, minY, maxY, out);
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = Math.clamp(out[i], min, max);
        }
    }
}
