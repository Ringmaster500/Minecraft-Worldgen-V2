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

    /** Which exact noise this is (settings and amplitude), for the shared per-thread memo; worked out on first use. */
    private volatile int memoId;

    private int memoId() {
        int id = memoId;
        if (id == 0) memoId = id = NoiseMemo.idFor(java.util.List.of("2d", noise.signature(), amplitude));
        return id;
    }

    private float sample(float x, float z) { return noise.getNoise(x, z) * amplitude; }

    @Override
    public float compute(float x, float y, float z) {
        return NoiseMemo.get(memoId(), x, z, this::sample);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        if (height <= 0) return;
        java.util.Arrays.fill(out, 0, height, NoiseMemo.get(memoId(), x, z, this::sample));
    }
    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
    @Override public boolean yIndependent() { return true; }
}
