package com.worldgenv2.density;

import java.util.Objects;

/**
 * High-performance 3D gradient domain warping using central differences.
 */
public final class FastGradientWarpDensity implements DensityFunction {

    private final DensityFunction source;
    private final DensityFunction warpField;
    private final float factor;

    public FastGradientWarpDensity(DensityFunction source, DensityFunction warpField, float factor) {
        this.source = Objects.requireNonNull(source, "source");
        this.warpField = Objects.requireNonNull(warpField, "warpField");
        this.factor = factor;
    }

    public DensityFunction source() {
        return source;
    }

    public DensityFunction warpField() {
        return warpField;
    }

    public float factor() {
        return factor;
    }

    @Override
    public float compute(float x, float y, float z) {
        float eps = 1.0f;
        float gx = (warpField.compute(x + eps, y, z) - warpField.compute(x - eps, y, z)) * 0.5f;
        float gy = (warpField.compute(x, y + eps, z) - warpField.compute(x, y - eps, z)) * 0.5f;
        float gz = (warpField.compute(x, y, z + eps) - warpField.compute(x, y, z - eps)) * 0.5f;

        return source.compute(x + gx * factor, y + gy * factor, z + gz * factor);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = compute(x, minY + i, z);
        }
    }
}
