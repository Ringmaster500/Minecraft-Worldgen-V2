package com.hypixel.hytale.builtin.hytalegenerator.worldstructure;

import com.hypixel.hytale.builtin.hytalegenerator.density.DensityFunction;
import java.util.Set;

/**
 * Conditional override biome.
 * Injects over parent TileBiomes when continuous noise falls within threshold boundaries.
 */
public record CustomBiome(
    int id,
    String name,
    int priority,
    Set<Integer> parentBiomeIds,
    float thresholdMin,
    float thresholdMax,
    DensityFunction placementNoise,
    DensityFunction terrainDensity
) {
    /**
     * Tests whether this custom biome satisfies placement criteria over the parent biome.
     */
    public boolean matches(int parentId, float x, float z) {
        if (!parentBiomeIds.isEmpty() && !parentBiomeIds.contains(parentId)) {
            return false;
        }
        float noise = placementNoise.compute(x, 0.0f, z);
        return noise >= thresholdMin && noise <= thresholdMax;
    }
}
