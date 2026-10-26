package com.worldgenv2.density;

import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;

/**
 * Gradient Warp density node.
 * Perturbs the evaluation coordinates along a gradient or displacement vector,
 * producing organic, flowing terrain contours and eliminating grid artifacts.
 */
public final class GradientWarpDensity implements DensityFunction {

    private final DensityFunction source;
    private final DensityFunction warpSource;
    private final float warpStrength;
    private final VectorProvider vectorProvider;

    public GradientWarpDensity(DensityFunction source, DensityFunction warpSource, float warpStrength) {
        this.source = source;
        this.warpSource = warpSource;
        this.warpStrength = warpStrength;
        this.vectorProvider = null;
    }

    public GradientWarpDensity(DensityFunction source, VectorProvider vectorProvider) {
        this.source = source;
        this.warpSource = null;
        this.warpStrength = 1.0f;
        this.vectorProvider = vectorProvider;
    }

    @Override
    public float compute(float x, float y, float z) {
        if (vectorProvider != null) {
            Vec3d v = vectorProvider.getVector(x, y, z);
            return source.compute((float) (x + v.x()), (float) (y + v.y()), (float) (z + v.z()));
        }

        // Sample gradient offsets along X and Z
        float delta = 1.0f;
        float warpX = (warpSource.compute(x + delta, y, z) - warpSource.compute(x - delta, y, z)) * 0.5f * warpStrength;
        float warpZ = (warpSource.compute(x, y, z + delta) - warpSource.compute(x, y, z - delta)) * 0.5f * warpStrength;

        return source.compute(x + warpX, y, z + warpZ);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) return;

        if (vectorProvider != null) {
            for (int i = 0; i < height; i++) {
                float y = minY + i;
                Vec3d v = vectorProvider.getVector(x, y, z);
                out[i] = source.compute((float) (x + v.x()), (float) (y + v.y()), (float) (z + v.z()));
            }
            return;
        }

        float delta = 1.0f;
        float scale = 0.5f * warpStrength;

        // Fast path when warpSource is 2D (Noise2dDensity / ConstantDensity)
        if (warpSource instanceof Noise2dDensity || warpSource instanceof ConstantDensity) {
            float warpX = (warpSource.compute(x + delta, 0.0f, z) - warpSource.compute(x - delta, 0.0f, z)) * scale;
            float warpZ = (warpSource.compute(x, 0.0f, z + delta) - warpSource.compute(x, 0.0f, z - delta)) * scale;
            source.computeColumn(x + warpX, z + warpZ, minY, maxY, out);
            return;
        }

        for (int i = 0; i < height; i++) {
            float y = minY + i;
            float warpX = (warpSource.compute(x + delta, y, z) - warpSource.compute(x - delta, y, z)) * scale;
            float warpZ = (warpSource.compute(x, y, z + delta) - warpSource.compute(x, y, z - delta)) * scale;
            out[i] = source.compute(x + warpX, y, z + warpZ);
        }
    }

    public DensityFunction getSource() {
        return source;
    }

    public DensityFunction getWarpSource() {
        return warpSource;
    }

    public float getWarpStrength() {
        return warpStrength;
    }

    public VectorProvider getVectorProvider() {
        return vectorProvider;
    }
}
