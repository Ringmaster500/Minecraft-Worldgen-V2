package com.worldgenv2.props.runtime;

/**
 * Precision level at which runtime context requirements and halo bounds are calculated for chunks.
 */
public enum ContextPrecision {
    /**
     * Context size is computed uniformly at the macro Biome scale.
     * Lowest initialization overhead; does not evaluate local prop distributions.
     */
    BIOME,

    /**
     * Context size is determined chunk-by-chunk based on whether any props exist locally in the chunk.
     * Large empty areas (e.g. prairies, oceans) incur zero context overhead.
     */
    CHUNK,

    /**
     * Context size extends by the exact tightest bounding envelope needed by the props
     * distributed locally in each chunk. Optimal for large sparse prefabs and structures.
     */
    PROP
}
