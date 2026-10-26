package com.worldgenv2.density;

import java.util.Objects;

/**
 * 2D Voronoi Cellular noise density function varying on the XZ plane.
 * Column-constant: values do not vary with Y, enabling single-sample column optimizations.
 */
public final class CellNoise2dDensity implements DensityFunction {

    private final FastNoiseLite noise;
    private final float amplitude;

    public CellNoise2dDensity(FastNoiseLite noise) {
        this(noise, 1.0f);
    }

    public CellNoise2dDensity(FastNoiseLite noise, float amplitude) {
        this.noise = Objects.requireNonNull(noise, "noise");
        this.amplitude = amplitude;
    }

    @Override
    public float compute(float x, float y, float z) {
        return noise.getNoise(x, z) * amplitude;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) return;
        float val = noise.getNoise(x, z) * amplitude;
        for (int i = 0; i < height; i++) {
            out[i] = val;
        }
    }

    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
}
