package com.hypixel.hytale.builtin.hytalegenerator.compat;

import com.hypixel.hytale.builtin.hytalegenerator.density.ConstantDensity;
import com.hypixel.hytale.builtin.hytalegenerator.density.DensityFunction;
import com.hypixel.hytale.builtin.hytalegenerator.density.SumDensity;
import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.TileBiome;

/**
 * Automatically synthesizes Hytale V2 TileBiomes from Minecraft climate parameters.
 * Enables vanilla and third-party biome expansion mods (Biomes O' Plenty, Terralith, Regions Unexplored)
 * to be ingested directly into the Hytale V2 Voronoi cellular generation engine.
 */
public final class BiomeAutoSynthesizer {

    public record ClimateParameters(
        float continentalness, // -1.0 to 1.0 (Ocean to inland mountain)
        float erosion,          // -1.0 to 1.0 (Jagged peaks to flat plains)
        float temperature,      // 0.0 to 2.0
        float downfall          // 0.0 to 1.0
    ) {}

    /**
     * Synthesizes a Hytale V2 TileBiome automatically from raw climate parameters.
     */
    public static TileBiome synthesizeTileBiome(
        int numericId,
        String biomeName,
        ClimateParameters climate
    ) {
        // Continentalness determines base elevation: range from 50 (sea floor) to 110 (highlands)
        float baseHeight = 64.0f + (climate.continentalness * 40.0f);
        
        // Erosion determines roughness/scale: higher erosion = flatter smoother landscape
        float heightScale = Math.max(12.0f, 32.0f * (1.0f - (climate.erosion * 0.5f)));

        // Create foundational vertical gradient
        DensityFunction baseGradient = (x, y, z) -> (baseHeight - y) / heightScale;

        // In a full implementation, this combines with FastNoiseLite 2D and 3D octaves
        DensityFunction compositeTerrainDensity = new SumDensity(
            baseGradient,
            new ConstantDensity(0.0f)
        );

        // Calculate weighting and transition distance based on climate volatility
        float weight = 10.0f;
        float sizeModifier = 1.0f + Math.abs(climate.continentalness);
        float transitionDistance = 32.0f + (climate.erosion * 16.0f);

        return new TileBiome(
            numericId,
            biomeName,
            weight,
            sizeModifier,
            transitionDistance,
            compositeTerrainDensity
        );
    }
}
