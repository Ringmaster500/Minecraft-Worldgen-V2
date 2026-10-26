package com.worldgenv2.biome;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.material.SpaceAndDepthMaterialProvider;
import com.worldgenv2.props.Prop;
import com.worldgenv2.worldstructure.TileBiome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Builds native TileBiome and MaterialProvider instances directly from native BiomeDefinitions.
 */
public final class BiomeFactory {

    public TileBiome createTileBiome(
        int numericId,
        BiomeDefinition definition,
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

    public <T> TileBiome createTileBiome(
        int numericId,
        BiomeDefinition definition,
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
            props,
            null,
            definition.defaultBlock(),
            definition.fluidBlock(),
            definition.seaLevel()
        );
    }

    public <T> MaterialProvider<T> createMaterialProvider(
        BiomeDefinition definition,
        Function<String, T> blockStateResolver
    ) {
        List<SpaceAndDepthMaterialProvider.Layer<T>> layers = new ArrayList<>();
        for (BiomeDefinition.LayerDefinition layerDef : definition.layers()) {
            T blockState = blockStateResolver.apply(layerDef.material());
            layers.add(new SpaceAndDepthMaterialProvider.Layer<>(blockState, layerDef.thickness()));
        }

        T fallback = blockStateResolver.apply(definition.fallbackMaterial());
        T cliff = blockStateResolver.apply(definition.cliffMaterial());

        return new SpaceAndDepthMaterialProvider<>(
            layers,
            fallback,
            cliff,
            definition.maxSlopeForCover(),
            definition.minSpaceForCover()
        );
    }
}
