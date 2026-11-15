package com.worldgenv2.vector;

import com.worldgenv2.density.DensityFunction;
import java.util.Objects;

/**
 * Computes the 3D continuous gradient vector grad(D) = (dD/dx, dD/dy, dD/dz) of a DensityFunction
 * using central difference numerical differentiation:
 * dD/dx = (D(x + delta, y, z) - D(x - delta, y, z)) / (2 * delta).
 */
public final class DensityGradientVectorProvider implements VectorProvider {

    private final DensityFunction density;
    private final double sampleDistance;

    public DensityGradientVectorProvider(DensityFunction density) {
        this(density, 1.0);
    }

    public DensityGradientVectorProvider(DensityFunction density, double sampleDistance) {
        this.density = Objects.requireNonNull(density, "density");
        this.sampleDistance = sampleDistance > 0.0 ? sampleDistance : 1.0;
    }

    /**
     * Terrain materials ask for the gradient at a few blocks of a column, then at the next column, and so on. On whole-number
     * positions the density is read from short column windows that are kept per thread, so each column window is evaluated once and
     * shared by the gradients of all its neighbours, instead of six single-point evaluations of the whole density per block.
     */
    private static final class Windows {
        final int[] xs = new int[64], zs = new int[64], y0s = new int[64], y1s = new int[64];
        final float[][] vals = new float[64][];
        Windows() { java.util.Arrays.fill(y1s, Integer.MIN_VALUE); }
    }

    private static final int HALF = 8;
    private final ThreadLocal<Windows> windows = ThreadLocal.withInitial(Windows::new);

    private float at(Windows w, int x, int y, int z) {
        int slot = ((x * 73856093) ^ (z * 19349663)) & 63;
        if (w.xs[slot] != x || w.zs[slot] != z || y < w.y0s[slot] || y >= w.y1s[slot]) {
            int y0 = y - HALF, y1 = y + HALF;
            float[] v = w.vals[slot];
            if (v == null) v = w.vals[slot] = new float[y1 - y0];
            density.computeColumn(x, z, y0, y1, v);
            w.xs[slot] = x; w.zs[slot] = z; w.y0s[slot] = y0; w.y1s[slot] = y1;
        }
        return w.vals[slot][y - w.y0s[slot]];
    }

    private Vec3d windowed(int ix, int iy, int iz, int d) {
        Windows w = windows.get();
        float inv = 1.0f / (2.0f * d);
        float dx = (at(w, ix + d, iy, iz) - at(w, ix - d, iy, iz)) * inv;
        float dy = (at(w, ix, iy + d, iz) - at(w, ix, iy - d, iz)) * inv;
        float dz = (at(w, ix, iy, iz + d) - at(w, ix, iy, iz - d)) * inv;
        return new Vec3d(dx, dy, dz);
    }
    @Override
    public Vec3d getVector(double x, double y, double z) {
        if (sampleDistance >= 1.0 && sampleDistance <= HALF - 1 && sampleDistance == Math.rint(sampleDistance) && x == Math.rint(x) && y == Math.rint(y) && z == Math.rint(z)
                && Math.abs(x) < 1e7 && Math.abs(z) < 1e7) {
            return windowed((int) x, (int) y, (int) z, (int) sampleDistance);
        }
        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;
        float delta = (float) sampleDistance;
        float invTwoDelta = 1.0f / (2.0f * delta);

        float dx = (density.compute(fx + delta, fy, fz) - density.compute(fx - delta, fy, fz)) * invTwoDelta;
        float dy = (density.compute(fx, fy + delta, fz) - density.compute(fx, fy - delta, fz)) * invTwoDelta;
        float dz = (density.compute(fx, fy, fz + delta) - density.compute(fx, fy, fz - delta)) * invTwoDelta;

        return new Vec3d(dx, dy, dz);
    }

    public DensityFunction getDensity() {
        return density;
    }

    public double getSampleDistance() {
        return sampleDistance;
    }
}
