package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;

import java.util.Objects;

/**
 * Material provider applying a specific material within a vertical [minY, maxY] window, falling back elsewhere.
 */
public final class LayerMaterialProvider<T> implements MaterialProvider<T> {

    private final int minY;
    private final int maxY;
    private final MaterialProvider<T> layerMaterial;
    private final MaterialProvider<T> fallback;

    public LayerMaterialProvider(int minY, int maxY, MaterialProvider<T> layerMaterial, MaterialProvider<T> fallback) {
        this.minY = minY;
        this.maxY = maxY;
        this.layerMaterial = Objects.requireNonNull(layerMaterial, "layerMaterial");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public MaterialProvider<T> layerMaterial() {
        return layerMaterial;
    }

    public MaterialProvider<T> fallback() {
        return fallback;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        if (y >= minY && y <= maxY) {
            return layerMaterial.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
        }
        return fallback.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
    }
}
