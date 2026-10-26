package com.worldgenv2.density;

import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;
import java.util.Objects;

/**
 * 3D Domain warping density node driven by an arbitrary {@link VectorProvider}.
 * Evaluates the vector field at sample position (x, y, z) and queries the source
 * density at (x + vx, y + vy, z + vz).
 */
public final class VectorWarpDensity implements DensityFunction {

    private final DensityFunction source;
    private final VectorProvider vectorProvider;

    public VectorWarpDensity(DensityFunction source, VectorProvider vectorProvider) {
        this.source = Objects.requireNonNull(source, "source");
        this.vectorProvider = Objects.requireNonNull(vectorProvider, "vectorProvider");
    }

    @Override
    public float compute(float x, float y, float z) {
        Vec3d offset = vectorProvider.getVector(x, y, z);
        return source.compute(
            (float) (x + offset.x()),
            (float) (y + offset.y()),
            (float) (z + offset.z())
        );
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int height = maxY - minY;
        for (int i = 0; i < height; i++) {
            float y = minY + i;
            Vec3d offset = vectorProvider.getVector(x, y, z);
            out[i] = source.compute(
                (float) (x + offset.x()),
                (float) (y + offset.y()),
                (float) (z + offset.z())
            );
        }
    }

    public DensityFunction getSource() {
        return source;
    }

    public VectorProvider getVectorProvider() {
        return vectorProvider;
    }
}
