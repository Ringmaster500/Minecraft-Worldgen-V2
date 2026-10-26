package com.worldgenv2.density;

/**
 * Foundational vertical gravity gradient: (baseHeight - y) / heightScale.
 * Positive below baseHeight (solid ground), negative above baseHeight (open sky).
 */
public final class VerticalGradientDensity implements DensityFunction {

    private final float baseHeight;
    private final float invScale;

    public VerticalGradientDensity(float baseHeight, float heightScale) {
        this.baseHeight = baseHeight;
        this.invScale = 1.0f / Math.max(1e-4f, heightScale);
    }

    @Override
    public float compute(float x, float y, float z) {
        return (baseHeight - y) * invScale;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = (baseHeight - (minY + i)) * invScale;
        }
    }

    public float getBaseHeight() {
        return baseHeight;
    }

    public float getHeightScale() {
        return 1.0f / invScale;
    }
}
