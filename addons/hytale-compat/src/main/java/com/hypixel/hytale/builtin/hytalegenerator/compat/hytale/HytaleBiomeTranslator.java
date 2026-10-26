package com.hypixel.hytale.builtin.hytalegenerator.compat.hytale;

import com.hypixel.hytale.builtin.hytalegenerator.density.DensityFunction;
import com.hypixel.hytale.builtin.hytalegenerator.material.MaterialProvider;
import com.hypixel.hytale.builtin.hytalegenerator.material.SpaceAndDepthMaterialProvider;
import com.hypixel.hytale.builtin.hytalegenerator.props.Prop;
import com.hypixel.hytale.builtin.hytalegenerator.worldstructure.TileBiome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Translates Hytale V2 Biome definitions into live Minecraft-ready generator instances.
 * Part of the Hytale Compatibility Addon module.
 */
public final class HytaleBiomeTranslator {

    private final BlockMappingRegistry blockMappingRegistry;

    public HytaleBiomeTranslator(BlockMappingRegistry blockMappingRegistry) {
        this.blockMappingRegistry = blockMappingRegistry;
    }

    public TileBiome translateTileBiome(
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

    public <T> TileBiome translateTileBiome(
        int numericId,
        HytaleBiomeDefinition definition,
        DensityFunction densityFunction,
        MaterialProvider<T> materialProvider,
        List<Prop<T>> props
    ) {
        return new TileBiome(
            numericId,
            definition.name(),
            definition.weight(),
            definition.sizeModifier(),
            definition.defaultTransitionDistance(),
            densityFunction,
            materialProvider,
            props
        );
    }

    public <T> MaterialProvider<T> translateMaterialProvider(
        HytaleBiomeDefinition definition,
        Function<String, T> blockStateResolver
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
