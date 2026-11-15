package com.worldgenv2.density;

/**
 * Returns the raw continuous world Z coordinate value as density.
 */
public final class ZValueDensity implements DensityFunction {

    public static final ZValueDensity INSTANCE = new ZValueDensity();

    @Override
    public float compute(float x, float y, float z) {
        return z;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = z;
        }
    }
    @Override public boolean yIndependent() { return true; }
}
