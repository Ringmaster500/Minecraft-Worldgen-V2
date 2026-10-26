package com.worldgenv2.density;

/**
 * Returns the raw continuous world Y coordinate value as density.
 */
public final class YValueDensity implements DensityFunction {

    public static final YValueDensity INSTANCE = new YValueDensity();

    @Override
    public float compute(float x, float y, float z) {
        return y;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = minY + i;
        }
    }
}
