package com.worldgenv2.props.runtime;

import com.worldgenv2.props.Prop;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Encapsulates a prop distribution within a numbered generation stage and defines its
 * horizontal context requirements and precision calculation mode.
 */
public final class PropRuntime<T> implements Prop<T> {

    private final int runtimeIndex;
    private final ContextPrecision precision;
    private final Prop<T> prop;
    private final int scannerReach;
    private final int patternOffset;
    private final int prefabRadius;
    private final int maxReach;
    private final BiPredicate<Integer, Integer> localPresenceFilter;

    public PropRuntime(
        int runtimeIndex,
        ContextPrecision precision,
        Prop<T> prop,
        int scannerReach,
        int patternOffset,
        int prefabRadius
    ) {
        this(runtimeIndex, precision, prop, scannerReach, patternOffset, prefabRadius, null);
    }

    public PropRuntime(
        int runtimeIndex,
        ContextPrecision precision,
        Prop<T> prop,
        int scannerReach,
        int patternOffset,
        int prefabRadius,
        BiPredicate<Integer, Integer> localPresenceFilter
    ) {
        this.runtimeIndex = Math.max(0, runtimeIndex);
        this.precision = precision != null ? precision : ContextPrecision.BIOME;
        this.prop = Objects.requireNonNull(prop, "prop");
        this.scannerReach = Math.max(0, scannerReach);
        this.patternOffset = Math.max(0, patternOffset);
        this.prefabRadius = Math.max(0, prefabRadius);
        this.maxReach = Math.max(this.scannerReach, Math.max(this.patternOffset, this.prefabRadius));
        this.localPresenceFilter = localPresenceFilter;
    }

    public int getRuntimeIndex() {
        return runtimeIndex;
    }

    public ContextPrecision getPrecision() {
        return precision;
    }

    public Prop<T> getProp() {
        return prop;
    }

    public Prop<T> prop() {
        return prop;
    }

    public int getScannerReach() {
        return scannerReach;
    }

    public int getPatternOffset() {
        return patternOffset;
    }

    public int getPrefabRadius() {
        return prefabRadius;
    }

    public int getMaxReach() {
        return maxReach;
    }

    /**
     * Determines whether any props in this runtime are placed locally in chunk (chunkX, chunkZ).
     */
    public boolean containsPropsInChunk(int chunkX, int chunkZ, long worldSeed) {
        if (localPresenceFilter != null) {
            return localPresenceFilter.test(chunkX, chunkZ);
        }
        // Default deterministic chunk presence check based on worldSeed
        long h = worldSeed ^ (((long) chunkX * 0x4F9939F508DA912DL) + ((long) chunkZ * 0x1D8E4E27C47D124FL));
        h ^= runtimeIndex * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return (h & 0x1) == 0; // 50% base presence if no explicit presence filter is provided
    }

    /**
     * Computes the effective horizontal reach in blocks required for this chunk based on the configured precision mode.
     */
    public int computeContextReach(int chunkX, int chunkZ, long worldSeed) {
        return switch (precision) {
            case BIOME -> maxReach;
            case CHUNK -> containsPropsInChunk(chunkX, chunkZ, worldSeed) ? maxReach : 0;
            case PROP -> {
                if (!containsPropsInChunk(chunkX, chunkZ, worldSeed)) {
                    yield 0;
                }
                // In PROP precision, calculate the exact required envelope
                // If scanner or pattern is zero, reach shrinks to the exact prefab radius
                yield Math.max(scannerReach, Math.max(patternOffset, prefabRadius));
            }
        };
    }

    public static <T> PropRuntime<T> of(int runtimeIndex, Prop<T> prop) {
        return new PropRuntime<>(runtimeIndex, ContextPrecision.BIOME, prop, 0, 0, 0);
    }

    public static <T> PropRuntime<T> of(int runtimeIndex, ContextPrecision precision, Prop<T> prop, int maxReach) {
        return new PropRuntime<>(runtimeIndex, precision, prop, maxReach, maxReach, maxReach);
    }

    /** A runtime is itself a prop: placing it places what it wraps. The generator reads its stage and reach before that. */
    @Override
    public boolean generate(Context<T> context) {
        return prop.generate(context);
    }

    /**
     * Horizontal context this runtime needs: whatever its prop can reach (scanner, pattern, content size), or the reach the
     * asset states explicitly, whichever is larger.
     */
    @Override
    public int horizontalReach() {
        return Math.max(maxReach, prop.horizontalReach());
    }

    /** The prop inside a runtime wrapper (or the prop itself when it is not wrapped). */
    @SuppressWarnings("unchecked")
    public static <T> Prop<T> unwrap(Prop<T> p) {
        while (p instanceof PropRuntime<?> r) p = (Prop<T>) r.prop();
        return p;
    }

    /** The stage a prop runs in: its runtime index, or 1 for a prop with no runtime wrapper. */
    public static int stageOf(Prop<?> p) {
        return p instanceof PropRuntime<?> r ? Math.max(1, r.getRuntimeIndex()) : 1;
    }

    @Override
    public int contentReach() {
        Prop<T> inner = unwrap(prop);
        return inner instanceof com.worldgenv2.props.AssignmentsProp<?> ap ? ap.contentReach() : inner.horizontalReach();
    }
}
