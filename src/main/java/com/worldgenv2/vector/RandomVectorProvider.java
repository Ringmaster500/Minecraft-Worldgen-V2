package com.worldgenv2.vector;

/**
 * Generates a unit vector in a pseudo-random, deterministic direction per coordinate sample.
 */
public final class RandomVectorProvider implements VectorProvider {

    private final long seed;

    public RandomVectorProvider(long seed) {
        this.seed = seed;
    }

    public RandomVectorProvider(String seedStr) {
        this(seedStr != null ? seedStr.hashCode() : 0L);
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        // Deterministic 64-bit coordinate hash
        long hx = Double.doubleToLongBits(x);
        long hy = Double.doubleToLongBits(y);
        long hz = Double.doubleToLongBits(z);

        long h1 = hash(hx ^ seed, hy, hz);
        long h2 = hash(h1, seed, hz);

        // Map to uniform sphere surface
        double u1 = (h1 & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;
        double u2 = (h2 & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;

        double theta = 2.0 * Math.PI * u1;
        double cosPhi = 2.0 * u2 - 1.0;
        double sinPhi = Math.sqrt(Math.max(0.0, 1.0 - cosPhi * cosPhi));

        double vx = sinPhi * Math.cos(theta);
        double vy = sinPhi * Math.sin(theta);
        double vz = cosPhi;

        return new Vec3d(vx, vy, vz);
    }

    private static long hash(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L + b * 0xBF58476D1CE4E5B9L + c * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    public long getSeed() {
        return seed;
    }
}
