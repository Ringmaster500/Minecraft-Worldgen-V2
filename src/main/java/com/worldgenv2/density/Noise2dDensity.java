package com.worldgenv2.density;

import java.util.Arrays;

/**
 * 2D horizontal noise density node.
 * Evaluates FastNoiseLite on the (X, Z) plane and projects vertically across the entire column.
 * Column evaluation computes the 2D noise only once per (X, Z) coordinate.
 */
public final class Noise2dDensity implements DensityFunction {

    private final FastNoiseLite noise;
    private final float amplitude;

    public Noise2dDensity(FastNoiseLite noise, float amplitude) {
        this.noise = noise;
        this.amplitude = amplitude;
    }

    public Noise2dDensity(int seed, FastNoiseLite.NoiseType noiseType, float frequency, int octaves, float amplitude) {
        this.noise = new FastNoiseLite(seed);
        this.noise.setNoiseType(noiseType);
        this.noise.setFrequency(frequency);
        if (octaves > 1) {
            this.noise.setFractalType(FastNoiseLite.FractalType.FBm);
            this.noise.setFractalOctaves(octaves);
        } else {
            this.noise.setFractalType(FastNoiseLite.FractalType.None);
        }
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
        Arrays.fill(out, 0, height, val);
    }

    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
}
