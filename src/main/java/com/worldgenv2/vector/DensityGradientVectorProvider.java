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
     * Terrain materials ask for the gradient at a few blocks of the same column in a row. On whole-number positions the five columns
     * around it (centre and the four neighbours) are evaluated once over a short window of heights, instead of six separate
     * single-point evaluations of the whole density for every block.
     */
    private static final class Window {
        int x, z, y0, y1;
        boolean valid;
        final float[][] cols = new float[5][];     // centre, +x, -x, +z, -z
    }

    private final ThreadLocal<Window> windows = ThreadLocal.withInitial(Window::new);

    private Vec3d windowed(int ix, int iy, int iz, int d) {
        Window w = windows.get();
        if (!w.valid || w.x != ix || w.z != iz || iy - d < w.y0 || iy + d >= w.y1) {
            int y0 = iy - d - 3, y1 = iy + d + 2;
            int n = y1 - y0;
            for (int k = 0; k < 5; k++) if (w.cols[k] == null || w.cols[k].length < n) w.cols[k] = new float[Math.max(n, 12)];
            density.computeColumn(ix, iz, y0, y1, w.cols[0]);
            density.computeColumn(ix + d, iz, y0, y1, w.cols[1]);
            density.computeColumn(ix - d, iz, y0, y1, w.cols[2]);
            density.computeColumn(ix, iz + d, y0, y1, w.cols[3]);
            density.computeColumn(ix, iz - d, y0, y1, w.cols[4]);
            w.x = ix; w.z = iz; w.y0 = y0; w.y1 = y1; w.valid = true;
        }
        int i = iy - w.y0;
        float inv = 1.0f / (2.0f * d);
        float dx = (w.cols[1][i] - w.cols[2][i]) * inv;
        float dy = (w.cols[0][i + d] - w.cols[0][i - d]) * inv;
        float dz = (w.cols[3][i] - w.cols[4][i]) * inv;
        return new Vec3d(dx, dy, dz);
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        if (sampleDistance >= 1.0 && sampleDistance == Math.rint(sampleDistance) && x == Math.rint(x) && y == Math.rint(y) && z == Math.rint(z)
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
