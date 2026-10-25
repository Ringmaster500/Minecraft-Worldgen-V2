package com.hypixel.hytale.builtin.hytalegenerator.density;

/**
 * High-performance vertical subsampling density node.
 * Evaluates the underlying 3D density function at vertical intervals of size `yInterval`
 * and applies cubic smoothstep interpolation between sampling points.
 */
public final class YSampledDensity implements DensityFunction {

    private final DensityFunction source;
    private final int yInterval;

    public YSampledDensity(DensityFunction source, int yInterval) {
        this.source = source;
        this.yInterval = Math.max(1, yInterval);
    }

    @Override
    public float compute(float x, float y, float z) {
        if (yInterval <= 1) {
            return source.compute(x, y, z);
        }

        int yFloor = (int) Math.floor(y / yInterval) * yInterval;
        int yCeil = yFloor + yInterval;

        float val0 = source.compute(x, yFloor, z);
        float val1 = source.compute(x, yCeil, z);

        float t = (y - yFloor) / (float) yInterval;
        // Cubic Hermite smoothstep interpolation
        float smoothT = t * t * (3.0f - 2.0f * t);

        return val0 + (val1 - val0) * smoothT;
    }

    public int getYInterval() {
        return yInterval;
    }

    public DensityFunction getSource() {
        return source;
    }
}
