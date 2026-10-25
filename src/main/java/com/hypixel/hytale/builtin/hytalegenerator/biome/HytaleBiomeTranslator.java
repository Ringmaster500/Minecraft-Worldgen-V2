package com.hypixel.hytale.builtin.hytalegenerator.biome;

import com.hypixel.hytale.builtin.hytalegenerator.density.DensityFunction;
import com.hypixel.hytale.builtin.hytalegenerator.material.MaterialProvider;
import com.hypixel.hytale.builtin.hytalegenerator.material.SpaceAndDepthMaterialProvider;
import com.hypixel.hytale.builtin.hytalegenerator.prefab.BlockMappingRegistry;
import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.TileBiome;

import java.util.ArrayList;
import java.util.List;

/**
 * Translates Hytale V2 Biome definitions into live Minecraft-ready generator instances.
 */
public final class HytaleBiomeTranslator {

    private final BlockMappingRegistry blockMappingRegistry;

    public HytaleBiomeTranslator(BlockMappingRegistry blockMappingRegistry) {
        this.blockMappingRegistry = blockMappingRegistry;
    }

    /**
     * Translates a Hytale Biome Definition and its compiled density function into a live TileBiome.
     */
    public <T> TileBiome translateTileBiome(
        int numericId,
        HytaleBiomeDefinition definition,
        DensityFunction densityFunction
    ) {
        return new TileBiome(
            numericId,
            definition.name(),
            definition.weight(),
            definition.sizeModifier(),
            definition.defaultTransitionDistance(),
            densityFunction
        );
    }

    /**
     * Translates the Hytale SpaceAndDepth material rules into a Minecraft MaterialProvider.
     */
    public <T> MaterialProvider<T> translateMaterialProvider(
        HytaleBiomeDefinition definition,
        java.util.function.Function<String, T> blockStateResolver
    ) {
        List<SpaceAndDepthMaterialProvider.Layer<T>> layers = new ArrayList<>();
        for (HytaleBiomeDefinition.LayerDefinition layerDef : definition.layers()) {
            String mcBlockId = blockMappingRegistry.toMinecraft(layerDef.material());
            T blockState = blockStateResolver.apply(mcBlockId);
            layers.add(new SpaceAndDepthMaterialProvider.Layer<>(blockState, layerDef.thickness()));
        }

        T fallback = blockStateResolver.apply(blockMappingRegistry.toMinecraft(definition.fallbackMaterial()));
        T cliff = blockStateResolver.apply(blockMappingRegistry.toMinecraft(definition.cliffMaterial()));

        return new SpaceAndDepthMaterialProvider<>(
            layers,
            fallback,
            cliff,
            definition.maxSlopeForCover(),
            definition.minSpaceForCover()
        );
    }
}
