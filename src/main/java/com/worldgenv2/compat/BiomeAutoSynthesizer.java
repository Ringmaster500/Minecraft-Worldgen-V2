package com.worldgenv2.compat;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.density.FastNoiseLite;
import com.worldgenv2.density.Noise2dDensity;
import com.worldgenv2.density.Noise3dDensity;
import com.worldgenv2.density.SumDensity;
import com.worldgenv2.density.VerticalGradientDensity;
import com.worldgenv2.density.YSampledDensity;
import com.worldgenv2.worldstructure.TileBiome;

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
        DensityFunction baseGradient = new VerticalGradientDensity(baseHeight, heightScale);

        int biomeSeed = 1337 ^ (numericId * 374761393) ^ biomeName.hashCode();
        float roughness = Math.clamp(1.0f - ((climate.erosion + 1.0f) * 0.4f), 0.2f, 1.2f);

        // Regional 2D hill contours
        DensityFunction macroHills2D = new Noise2dDensity(
            biomeSeed,
            FastNoiseLite.NoiseType.OpenSimplex2,
            0.0045f,
            3,
            0.55f * roughness
        );

        // Vertically subsampled 3D volumetric overhang/arch noise
        DensityFunction volumetric3D = new YSampledDensity(
            new Noise3dDensity(
                biomeSeed + 1013904223,
                FastNoiseLite.NoiseType.OpenSimplex2,
                0.012f,
                4,
                0.45f * roughness
            ),
            8
        );

        DensityFunction compositeTerrainDensity = new SumDensity(
            baseGradient,
            macroHills2D,
            volumetric3D
        );

        // Calculate weighting and transition distance based on climate volatility
        float weight = 10.0f;
        float sizeModifier = Math.clamp(1.0f + Math.abs(climate.continentalness) * 0.6f, 0.5f, 1.8f);
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
