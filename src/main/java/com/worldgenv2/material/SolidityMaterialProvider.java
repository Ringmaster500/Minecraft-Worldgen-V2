package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.density.DensityFunction;

import java.util.Objects;

/**
 * Dispatches between solid and non-solid/fluid material providers based on a scalar density evaluation.
 */
public final class SolidityMaterialProvider<T> implements MaterialProvider<T> {

    private final DensityFunction density;
    private final float threshold;
    private final MaterialProvider<T> solidMaterial;
    private final MaterialProvider<T> fluidMaterial;

    public SolidityMaterialProvider(
        DensityFunction density,
        float threshold,
        MaterialProvider<T> solidMaterial,
        MaterialProvider<T> fluidMaterial
    ) {
        this.density = Objects.requireNonNull(density, "density");
        this.threshold = threshold;
        this.solidMaterial = Objects.requireNonNull(solidMaterial, "solidMaterial");
        this.fluidMaterial = Objects.requireNonNull(fluidMaterial, "fluidMaterial");
    }

    public DensityFunction density() {
        return density;
    }

    public float threshold() {
        return threshold;
    }

    public MaterialProvider<T> solidMaterial() {
        return solidMaterial;
    }

    public MaterialProvider<T> fluidMaterial() {
        return fluidMaterial;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        float d = density.compute(x, y, z);
        if (d >= threshold) {
            return solidMaterial.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
        } else {
            return fluidMaterial.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
        }
    }
}
