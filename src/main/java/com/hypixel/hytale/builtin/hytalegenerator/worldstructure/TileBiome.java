package com.hypixel.hytale.builtin.hytalegenerator.worldstructure;

import com.hypixel.hytale.builtin.hytalegenerator.density.DensityFunction;

/**
 * Foundation cellular biome definition.
 * Distributed across macro zones via weighted Voronoi points.
 */
public record TileBiome(
    int id,
    String name,
    float weight,
    float sizeModifier,
    float defaultTransitionDistance,
    DensityFunction terrainDensity
) {
    public TileBiome {
        if (weight <= 0.0f) weight = 1.0f;
        if (sizeModifier <= 0.0f) sizeModifier = 1.0f;
        if (defaultTransitionDistance <= 0.0f) defaultTransitionDistance = 32.0f;
    }
}
