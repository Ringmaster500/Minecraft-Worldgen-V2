package com.worldgenv2.props;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import java.util.Objects;

/**
 * Volumetric procedural prop that evaluates a continuous 3D {@link DensityFunction} across a
 * local bounding volume and deposits blocks from a {@link MaterialProvider} where density >= threshold.
 */
public final class DensityProp<T> implements Prop<T> {

    private final DensityFunction density;
    private final MaterialProvider<T> materialProvider;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final float threshold;

    public DensityProp(
        DensityFunction density,
        MaterialProvider<T> materialProvider,
        int radiusX, int radiusY, int radiusZ,
        float threshold
    ) {
        this(density, materialProvider, -radiusX, -radiusY, -radiusZ, radiusX, radiusY, radiusZ, threshold);
    }

    public DensityProp(
        DensityFunction density,
        MaterialProvider<T> materialProvider,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        float threshold
    ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid bounds min > max");
        }
        this.density = Objects.requireNonNull(density, "density");
        this.materialProvider = Objects.requireNonNull(materialProvider, "materialProvider");
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.threshold = threshold;
    }

    public DensityFunction density() {
        return density;
    }

    public MaterialProvider<T> materialProvider() {
        return materialProvider;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    public float threshold() {
        return threshold;
    }

    @Override
    public boolean generate(Context<T> context) {
        VoxelSpace<T> space = context.voxelSpace();
        int cx = context.worldX();
        int cy = context.worldY();
        int cz = context.worldZ();

        boolean placed = false;
        for (int y = cy + minY; y <= cy + maxY; y++) {
            if (y < space.getMinY() || y >= space.getMaxY()) {
                continue;
            }
            for (int z = cz + minZ; z <= cz + maxZ; z++) {
                for (int x = cx + minX; x <= cx + maxX; x++) {
                    float val = density.compute(x, y, z);
                    if (val >= threshold) {
                        T mat = materialProvider.getMaterial(x, y, z, 0, 0, 0.0f, space);
                        if (mat != null) {
                            space.setBlock(x, y, z, mat);
                            placed = true;
                        }
                    }
                }
            }
        }
        return placed;
    }

    @Override
    public int horizontalReach() {
        return Math.max(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(minZ), Math.abs(maxZ)));
    }
}
