package com.worldgenv2.density;

import java.util.Objects;

/**
 * Scales spatial coordinates independently across axes prior to evaluating the child density node:
 * f(x, y, z) = g(x * scaleX, y * scaleY, z * scaleZ).
 */
public final class ScaleDensity implements DensityFunction {

    private final DensityFunction input;
    private final float scaleX;
    private final float scaleY;
    private final float scaleZ;
    private final boolean unitYScale;

    public ScaleDensity(DensityFunction input, float scaleX, float scaleY, float scaleZ) {
        this.input = Objects.requireNonNull(input, "input");
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        this.unitYScale = Math.abs(scaleY - 1.0f) < 1e-6f;
    }

    public DensityFunction input() {
        return input;
    }

    public float scaleX() {
        return scaleX;
    }

    public float scaleY() {
        return scaleY;
    }

    public float scaleZ() {
        return scaleZ;
    }

    @Override
    public float compute(float x, float y, float z) {
        return input.compute(x * scaleX, y * scaleY, z * scaleZ);
    }

    @Override public boolean yIndependent() { return input.yIndependent(); }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (unitYScale) {
            input.computeColumn(x * scaleX, z * scaleZ, minY, maxY, out);
        } else {
            int height = maxY - minY;
            float sx = x * scaleX;
            float sz = z * scaleZ;
            for (int i = 0; i < height; i++) {
                out[i] = input.compute(sx, (minY + i) * scaleY, sz);
            }
        }
    }
}
