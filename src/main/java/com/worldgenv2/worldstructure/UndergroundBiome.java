package com.worldgenv2.worldstructure;

import java.util.Set;

/**
 * True 3D biome volume (e.g. Lush Caves, Dripstone Caves, Deep Dark).
 * Unlike {@link TileBiome} / {@link CustomBiome}, which resolve on the (x, z) plane, an UndergroundBiome
 * resolves from a full (x, y, z) query: it occupies a vertical band, is placed by volumetric zone noise,
 * and may be restricted to specific surface biomes so caverns inherit the personality of the land above.
 *
 * @param floorBlock    block that replaces exposed cave floors (opaque to the core engine; cast by the adapter), or null
 * @param ceilingBlock  block that replaces exposed cave ceilings, or null
 * @param allowedSurfaceIds surface biome ids permitted above this volume (empty = any)
 */
public record UndergroundBiome(
    int id,
    String name,
    int minY,
    int maxY,
    float weight,
    Set<Integer> allowedSurfaceIds,
    Object floorBlock,
    float floorChance,
    Object ceilingBlock,
    float ceilingChance
) {
    public UndergroundBiome {
        if (weight <= 0.0f) weight = 1.0f;
        allowedSurfaceIds = allowedSurfaceIds == null ? Set.of() : Set.copyOf(allowedSurfaceIds);
    }
}
