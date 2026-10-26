package com.worldgenv2.worldstructure;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.props.Prop;

import java.util.List;

/**
 * Foundation cellular biome definition.
 * Distributed across macro zones via multiplicatively weighted Voronoi points.
 * Supports biome-specific default solid materials, fluid blocks, and local sea levels.
 */
public record TileBiome(
    int id,
    String name,
    float weight,
    float sizeModifier,
    float defaultTransitionDistance,
    DensityFunction terrainDensity,
    MaterialProvider<?> materialProvider,
    List<? extends Prop<?>> props,
    ClimateTarget climate,
    String defaultBlock,
    String fluidBlock,
    Integer seaLevel
) {
    public TileBiome {
        if (weight <= 0.0f) weight = 1.0f;
        sizeModifier = Math.clamp(sizeModifier <= 0.0f ? 1.0f : sizeModifier, 0.5f, 1.8f);
        if (defaultTransitionDistance <= 0.0f) defaultTransitionDistance = 32.0f;
        props = (props == null) ? List.of() : List.copyOf(props);
    }

    public TileBiome(
        int id,
        String name,
        float weight,
        float sizeModifier,
        float defaultTransitionDistance,
        DensityFunction terrainDensity
    ) {
        this(id, name, weight, sizeModifier, defaultTransitionDistance, terrainDensity, null, List.of(), null, null, null, null);
    }

    /**
     * Legacy constructor without a climate target (biome is selected via the 1D weight spectrum).
     */
    public TileBiome(
        int id,
        String name,
        float weight,
        float sizeModifier,
        float defaultTransitionDistance,
        DensityFunction terrainDensity,
        MaterialProvider<?> materialProvider,
        List<? extends Prop<?>> props
    ) {
        this(id, name, weight, sizeModifier, defaultTransitionDistance, terrainDensity, materialProvider, props, null, null, null, null);
    }

    public TileBiome(
        int id,
        String name,
        float weight,
        float sizeModifier,
        float defaultTransitionDistance,
        DensityFunction terrainDensity,
        MaterialProvider<?> materialProvider,
        List<? extends Prop<?>> props,
        ClimateTarget climate
    ) {
        this(id, name, weight, sizeModifier, defaultTransitionDistance, terrainDensity, materialProvider, props, climate, null, null, null);
    }

    public TileBiome withClimate(ClimateTarget target) {
        return new TileBiome(id, name, weight, sizeModifier, defaultTransitionDistance, terrainDensity, materialProvider, props, target, defaultBlock, fluidBlock, seaLevel);
    }

    public TileBiome withEnvironment(String defaultBlock, String fluidBlock, Integer seaLevel) {
        return new TileBiome(id, name, weight, sizeModifier, defaultTransitionDistance, terrainDensity, materialProvider, props, climate, defaultBlock, fluidBlock, seaLevel);
    }

    @SuppressWarnings("unchecked")
    public <T> MaterialProvider<T> typedMaterialProvider() {
        return (MaterialProvider<T>) materialProvider;
    }

    @SuppressWarnings("unchecked")
    public <T> List<Prop<T>> typedProps() {
        return (List<Prop<T>>) (List<?>) props;
    }
}
