package com.worldgenv2.density;

/**
 * High-performance vertical subsampling density node.
 * Evaluates the underlying 3D density function at vertical intervals of size `yInterval`
 * and applies cubic Hermite smoothstep interpolation between sampling points.
 */
public final class YSampledDensity implements DensityFunction {

    private final DensityFunction source;
    private final int yInterval;
    private final float invInterval;

    public YSampledDensity(DensityFunction source, int yInterval) {
        this.source = source;
        this.yInterval = Math.max(1, yInterval);
        this.invInterval = 1.0f / this.yInterval;
    }

    @Override
    public float compute(float x, float y, float z) {
        if (yInterval <= 1) {
            return source.compute(x, y, z);
        }

        int yFloor = Math.floorDiv((int) Math.floor(y), yInterval) * yInterval;
        int yCeil = yFloor + yInterval;

        float val0 = source.compute(x, yFloor, z);
        float val1 = source.compute(x, yCeil, z);

        float t = (y - yFloor) * invInterval;
        // Cubic Hermite smoothstep interpolation
        float smoothT = t * t * (3.0f - 2.0f * t);

        return val0 + (val1 - val0) * smoothT;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) {
            return;
        }
        if (yInterval <= 1) {
            source.computeColumn(x, z, minY, maxY, out);
            return;
        }

        int firstFloor = Math.floorDiv(minY, yInterval) * yInterval;
        int lastCeil = Math.floorDiv(maxY - 1, yInterval) * yInterval + yInterval;
        int numSegments = (lastCeil - firstFloor) / yInterval;

        // Evaluate lower knot once and carry upper knot forward across each vertical segment
        float val0 = source.compute(x, firstFloor, z);
        for (int s = 0; s < numSegments; s++) {
            int y0 = firstFloor + s * yInterval;
            int y1 = y0 + yInterval;
            float val1 = source.compute(x, y1, z);

            int startY = Math.max(minY, y0);
            int endY = Math.min(maxY, y1);
            float delta = val1 - val0;

            for (int y = startY; y < endY; y++) {
                float t = (y - y0) * invInterval;
                float smoothT = t * t * (3.0f - 2.0f * t);
                out[y - minY] = val0 + delta * smoothT;
            }

            val0 = val1;
        }
    }

    public int getYInterval() {
        return yInterval;
    }

    public DensityFunction getSource() {
        return source;
    }
}
