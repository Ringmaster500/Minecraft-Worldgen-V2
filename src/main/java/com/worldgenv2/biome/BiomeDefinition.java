package com.worldgenv2.biome;

import java.util.List;

/**
 * Native data container representing a parsed Worldgen V2 Biome JSON definition.
 * Uses native Minecraft block identifiers directly and supports biome-specific environment overrides.
 */
public record BiomeDefinition(
    String name,
    String category,
    String type,
    float weight,
    float sizeModifier,
    float defaultTransitionDistance,
    List<LayerDefinition> layers,
    String fallbackMaterial,
    String cliffMaterial,
    float maxSlopeForCover,
    int minSpaceForCover,
    String defaultBlock,
    String fluidBlock,
    Integer seaLevel
) {
    public record LayerDefinition(String material, int thickness) {}

    public BiomeDefinition(
        String name,
        String category,
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
        this(
            name,
            category,
            type,
            weight,
            sizeModifier,
            defaultTransitionDistance,
            layers,
            fallbackMaterial,
            cliffMaterial,
            maxSlopeForCover,
            minSpaceForCover,
            fallbackMaterial,
            null,
            null
        );
    }
}
