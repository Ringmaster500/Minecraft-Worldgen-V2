package com.worldgenv2.density;

/**
 * Returns the raw continuous world X coordinate value as density.
 */
public final class XValueDensity implements DensityFunction {

    public static final XValueDensity INSTANCE = new XValueDensity();

    @Override
    public float compute(float x, float y, float z) {
        return x;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = x;
        }
    }
}
