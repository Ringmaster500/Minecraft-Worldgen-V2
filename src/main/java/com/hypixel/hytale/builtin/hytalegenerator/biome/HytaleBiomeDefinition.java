package com.hypixel.hytale.builtin.hytalegenerator.biome;

import java.util.List;

/**
 * Data container representing a parsed Hytale V2 Biome JSON definition.
 */
public record HytaleBiomeDefinition(
    String name,
    String zone,
    String type,
    float weight,
    float sizeModifier,
    float defaultTransitionDistance,
    List<LayerDefinition> layers,
    String fallbackMaterial,
    String cliffMaterial,
    float maxSlopeForCover,
    int minSpaceForCover
) {
    public record LayerDefinition(String material, int thickness) {}
}
