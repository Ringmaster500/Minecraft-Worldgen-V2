package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;
import java.util.List;

/**
 * Stratified geological material provider.
 * Stacks materials vertically based on depth beneath the surface and air clearance.
 */
public final class SpaceAndDepthMaterialProvider<T> implements MaterialProvider<T> {

    public record Layer<T>(T material, int thickness) {}

    private final List<Layer<T>> layers;
    private final T fallbackMaterial;
    private final T cliffMaterial;
    private final float maxSlopeForCover;
    private final int minSpaceForCover;

    public SpaceAndDepthMaterialProvider(
        List<Layer<T>> layers,
        T fallbackMaterial,
        T cliffMaterial,
        float maxSlopeForCover,
        int minSpaceForCover
    ) {
        this.layers = List.copyOf(layers);
        this.fallbackMaterial = fallbackMaterial;
        this.cliffMaterial = cliffMaterial;
        this.maxSlopeForCover = maxSlopeForCover;
        this.minSpaceForCover = minSpaceForCover;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        // If the surface is too steep, expose cliff rock rather than soil/grass
        if (slopeAngle > maxSlopeForCover) {
            return cliffMaterial;
        }

        // If there's insufficient vertical clearance (low cave crawlspace), skip grass/surface cover
        if (space < minSpaceForCover && depth == 0 && layers.size() > 1) {
            return layers.get(1).material(); // Fallback to subsurface layer (e.g. dirt/gravel)
        }

        int accumulatedDepth = 0;
        for (Layer<T> layer : layers) {
            accumulatedDepth += layer.thickness();
            if (depth < accumulatedDepth) {
                return layer.material();
            }
        }

        return fallbackMaterial;
    }

    public List<Layer<T>> layers() {
        return layers;
    }

    public T fallbackMaterial() {
        return fallbackMaterial;
    }

    public T cliffMaterial() {
        return cliffMaterial;
    }

    public float maxSlopeForCover() {
        return maxSlopeForCover;
    }

    public int minSpaceForCover() {
        return minSpaceForCover;
    }
}
