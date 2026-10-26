package com.worldgenv2.worldstructure;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.props.Prop;

import java.util.List;
import java.util.Set;

/**
 * Conditional override biome.
 * Injects over parent TileBiomes when continuous placement noise falls within threshold boundaries,
 * applying smoothstep edge fading to prevent sheer cliff discontinuities.
 */
public record CustomBiome(
    int id,
    String name,
    int priority,
    Set<Integer> parentBiomeIds,
    float thresholdMin,
    float thresholdMax,
    float fadeMargin,
    DensityFunction placementNoise,
    DensityFunction terrainDensity,
    MaterialProvider<?> materialProvider,
    List<? extends Prop<?>> props
) {
    public CustomBiome {
        parentBiomeIds = (parentBiomeIds == null) ? Set.of() : Set.copyOf(parentBiomeIds);
        if (fadeMargin < 0.0f) {
            fadeMargin = Math.min(0.12f, Math.max(0.02f, (thresholdMax - thresholdMin) * 0.25f));
        }
        props = (props == null) ? List.of() : List.copyOf(props);
    }

    public CustomBiome(
        int id,
        String name,
        int priority,
        Set<Integer> parentBiomeIds,
        float thresholdMin,
        float thresholdMax,
        DensityFunction placementNoise,
        DensityFunction terrainDensity
    ) {
        this(
            id,
            name,
            priority,
            parentBiomeIds,
            thresholdMin,
            thresholdMax,
            Math.min(0.12f, Math.max(0.02f, (thresholdMax - thresholdMin) * 0.25f)),
            placementNoise,
            terrainDensity,
            null,
            List.of()
        );
    }

    /**
     * Tests whether this custom biome satisfies placement criteria over the parent biome.
     */
    public boolean matches(int parentId, float x, float z) {
        return computeBlendWeight(parentId, x, z) > 0.0f;
    }

    /**
     * Evaluates a smooth continuous interpolation weight in [0.0, 1.0] for this custom biome.
     * Prevents hard boolean cliff cuts along placement noise contours.
     */
    public float computeBlendWeight(int parentId, float x, float z) {
        if (!parentBiomeIds.isEmpty() && !parentBiomeIds.contains(parentId)) {
            return 0.0f;
        }
        float noise = placementNoise.compute(x, 0.0f, z);
        if (noise < thresholdMin || noise > thresholdMax) {
            return 0.0f;
        }
        if (fadeMargin <= 1e-5f) {
            return 1.0f;
        }

        // Smooth ramp-in above thresholdMin (unless lower bound is open at <= -0.999f)
        float tMin = (thresholdMin > -0.999f)
            ? Math.clamp((noise - thresholdMin) / fadeMargin, 0.0f, 1.0f)
            : 1.0f;

        // Smooth ramp-out below thresholdMax (unless upper bound is open at >= 0.999f)
        float tMax = (thresholdMax < 0.999f)
            ? Math.clamp((thresholdMax - noise) / fadeMargin, 0.0f, 1.0f)
            : 1.0f;

        float t = Math.min(tMin, tMax);
        return t * t * (3.0f - 2.0f * t);
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
