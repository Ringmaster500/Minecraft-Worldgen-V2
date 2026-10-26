package com.worldgenv2.density;

/**
 * 3D volumetric noise density node.
 * Evaluates full 3D FastNoiseLite across (X, Y, Z) to sculpt overhangs, arches, and caverns.
 */
public final class Noise3dDensity implements DensityFunction {

    private final FastNoiseLite noise;
    private final float amplitude;
    private final float xzScale;
    private final float yScale;

    public Noise3dDensity(FastNoiseLite noise, float amplitude) {
        this(noise, amplitude, 1.0f, 1.0f);
    }

    public Noise3dDensity(FastNoiseLite noise, float amplitude, float xzScale, float yScale) {
        this.noise = noise;
        this.amplitude = amplitude;
        this.xzScale = xzScale;
        this.yScale = yScale;
    }

    public Noise3dDensity(
        int seed,
        FastNoiseLite.NoiseType noiseType,
        float frequency,
        int octaves,
        float amplitude
    ) {
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
        this.xzScale = 1.0f;
        this.yScale = 1.0f;
    }

    @Override
    public float compute(float x, float y, float z) {
        return noise.getNoise(x * xzScale, y * yScale, z * xzScale) * amplitude;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        float sx = x * xzScale;
        float sz = z * xzScale;
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = noise.getNoise(sx, (minY + i) * yScale, sz) * amplitude;
        }
    }

    public FastNoiseLite getNoise() {
        return noise;
    }

    public float getAmplitude() {
        return amplitude;
    }
}
