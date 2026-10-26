package com.worldgenv2.density;

import java.util.Objects;

/**
 * 3D Voronoi Cellular noise density function varying across XYZ space.
 */
public final class CellNoise3dDensity implements DensityFunction {

    private final FastNoiseLite noise;
    private final float amplitude;

    public CellNoise3dDensity(FastNoiseLite noise) {
        this(noise, 1.0f);
    }

    public CellNoise3dDensity(FastNoiseLite noise, float amplitude) {
        this.noise = Objects.requireNonNull(noise, "noise");
        this.amplitude = amplitude;
    }

    @Override
    public float compute(float x, float y, float z) {
        return noise.getNoise(x, y, z) * amplitude;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = noise.getNoise(x, minY + i, z) * amplitude;
        }
    }

    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
}
