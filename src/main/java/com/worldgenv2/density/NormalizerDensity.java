package com.worldgenv2.density;

import java.util.Objects;

/**
 * Linearly remaps an arbitrary input range [fromMin, fromMax] to a target range [toMin, toMax].
 * By default, maps [min, max] back to canonical [-1.0, 1.0]:
 * f(x, y, z) = ((g(x, y, z) - min) / (max - min)) * 2 - 1.
 */
public final class NormalizerDensity implements DensityFunction {

    private final DensityFunction input;
    private final float fromMin;
    private final float fromMax;
    private final float toMin;
    private final float toMax;
    private final float scale;
    private final float offset;

    public NormalizerDensity(DensityFunction input, float fromMin, float fromMax) {
        this(input, fromMin, fromMax, -1.0f, 1.0f);
    }

    public NormalizerDensity(DensityFunction input, float fromMin, float fromMax, float toMin, float toMax) {
        this.input = Objects.requireNonNull(input, "input");
        this.fromMin = fromMin;
        this.fromMax = fromMax;
        this.toMin = toMin;
        this.toMax = toMax;
        float span = fromMax - fromMin;
        if (Math.abs(span) < 1e-6f) {
            this.scale = 0.0f;
            this.offset = toMin;
        } else {
            this.scale = (toMax - toMin) / span;
            this.offset = toMin - fromMin * this.scale;
        }
    }

    public DensityFunction input() {
        return input;
    }

    public float fromMin() {
        return fromMin;
    }

    public float fromMax() {
        return fromMax;
    }

    public float toMin() {
        return toMin;
    }

    public float toMax() {
        return toMax;
    }

    @Override
    public float compute(float x, float y, float z) {
        return input.compute(x, y, z) * scale + offset;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        input.computeColumn(x, z, minY, maxY, out);
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = out[i] * scale + offset;
        }
    }
}
