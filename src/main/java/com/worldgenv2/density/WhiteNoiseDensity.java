package com.worldgenv2.density;

/**
 * 3D White Noise generator delivering fast, uncorrelated pseudo-random values in [-1, 1].
 */
public final class WhiteNoiseDensity implements DensityFunction {

    private final int seed;

    public WhiteNoiseDensity(int seed) {
        this.seed = seed;
    }

    public int seed() {
        return seed;
    }

    @Override
    public float compute(float x, float y, float z) {
        int ix = Float.floatToIntBits(x);
        int iy = Float.floatToIntBits(y);
        int iz = Float.floatToIntBits(z);

        long h = seed ^ (((long) ix * 0x4F9939F508DA912DL) ^ ((long) iy * 0x1D8E4E27C47D124FL) ^ ((long) iz * 0x27D4EB2F165667C5L));
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= (h >>> 31);

        // Normalize 32-bit signed integer to [-1.0f, 1.0f]
        return (float) ((int) h) / (float) Integer.MAX_VALUE;
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            out[i] = compute(x, minY + i, z);
        }
    }
}
