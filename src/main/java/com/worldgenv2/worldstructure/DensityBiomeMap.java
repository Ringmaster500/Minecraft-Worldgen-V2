package com.worldgenv2.worldstructure;

import com.worldgenv2.density.DensityFunction;

import java.util.List;
import java.util.function.IntFunction;

/**
 * Hytale's World Structure biome distribution (docs.hytale.com/hytale-generator/world-structure): one Density field decides
 * the biome everywhere. Each delimiter gives a biome to the places where the field is within its bounds; everywhere else is
 * the default biome. Near an edge between two biomes the terrain is blended over {@code transitionDistance} blocks.
 *
 * <p>The distance to an edge is estimated from the field itself: how far the value is from the nearest bound, divided by how
 * fast the field changes there (its gradient). That costs five samples per query and needs no search.
 */
public final class DensityBiomeMap {

    /** Biome id for field values in [min, max]. The first delimiter that matches wins. */
    public record Delimiter(double min, double max, int biomeId) {
        boolean contains(double v) { return v >= Math.min(min, max) && v <= Math.max(min, max); }
    }

    private final DensityFunction density;
    private final List<Delimiter> delimiters;
    private final int defaultBiomeId;
    private final float transitionDistance;
    private final int maxBiomeEdgeDistance;
    private final IntFunction<TileBiome> resolver;

    public DensityBiomeMap(DensityFunction density, List<Delimiter> delimiters, int defaultBiomeId, float transitionDistance,
                           int maxBiomeEdgeDistance, IntFunction<TileBiome> resolver) {
        this.density = density;
        this.delimiters = List.copyOf(delimiters);
        this.defaultBiomeId = defaultBiomeId;
        this.transitionDistance = Math.max(1f, transitionDistance);
        this.maxBiomeEdgeDistance = Math.max(0, maxBiomeEdgeDistance);
        this.resolver = resolver;
    }

    public int maxBiomeEdgeDistance() { return maxBiomeEdgeDistance; }

    /** The biome id at a field value. */
    public int biomeIdAt(double v) {
        for (Delimiter d : delimiters) if (d.contains(v)) return d.biomeId();
        return defaultBiomeId;
    }

    /**
     * Recent field values, shared by all threads: a chunk asks for each column and its four neighbours, and neighbouring chunks (and
     * the prop pass's sample grid) ask for the same points again. Entries are immutable, so readers need no locks.
     */
    private record FieldEntry(long key, double value) {}
    private static final int FIELD_SLOTS = 1 << 18;
    private final FieldEntry[] fieldSlots = new FieldEntry[FIELD_SLOTS];

    private double field(float x, float z) {
        long key = ((long) Float.floatToIntBits(x) << 32) | (Float.floatToIntBits(z) & 0xFFFFFFFFL);
        int slot = (int) (((key ^ (key >>> 29)) * 0x9E3779B97F4A7C15L) >>> 46);
        FieldEntry e = fieldSlots[slot];
        if (e != null && e.key == key) return e.value;
        double v = density.compute(x, 0f, z);
        fieldSlots[slot] = new FieldEntry(key, v);
        return v;
    }
    /** The biome a position belongs to, from a single sample of the field (no edge distance or blending). */
    public TileBiome primaryAt(float x, float z) { return resolver.apply(biomeIdAt(field(x, z))); }

    public BiCarta.CellQueryResult<TileBiome> query(float x, float z) {
        double v = field(x, z);
        int primaryId = biomeIdAt(v);
        TileBiome primary = resolver.apply(primaryId);

        // Nearest bound of the region the value is in, in field units, and the side it lies on.
        double bestGap = Double.MAX_VALUE;
        double beyond = v;
        Delimiter owner = null;
        for (Delimiter d : delimiters) if (d.contains(v) && d.biomeId() == primaryId) { owner = d; break; }
        if (owner != null) {
            double lo = Math.min(owner.min(), owner.max()), hi = Math.max(owner.min(), owner.max());
            if (v - lo < hi - v) { bestGap = v - lo; beyond = lo - 1e-4; } else { bestGap = hi - v; beyond = hi + 1e-4; }
        }
        // The default region (and the neighbours of a delimiter) end at some other delimiter's bound.
        for (Delimiter d : delimiters) {
            for (double bound : new double[]{Math.min(d.min(), d.max()), Math.max(d.min(), d.max())}) {
                double gap = Math.abs(v - bound);
                boolean outside = !d.contains(v);
                if (outside && gap < bestGap) { bestGap = gap; beyond = v + Math.signum(bound - v) * (gap + 1e-4); }
            }
        }
        if (bestGap == Double.MAX_VALUE) return new BiCarta.CellQueryResult<>(primary, primary, primary, Float.MAX_VALUE, 0f, 0f);

        double gx = (field(x + 1f, z) - field(x - 1f, z)) * 0.5, gz = (field(x, z + 1f) - field(x, z - 1f)) * 0.5;
        double slope = Math.sqrt(gx * gx + gz * gz);
        TileBiome secondary = resolver.apply(biomeIdAt(beyond));
        if (secondary == null || secondary.id() == primary.id() || slope < 1e-9) {
            return new BiCarta.CellQueryResult<>(primary, primary, primary, Float.MAX_VALUE, 0f, 0f);
        }
        float dist = (float) (bestGap / slope);
        float t = Math.clamp(1f - dist / transitionDistance, 0f, 1f);
        float alpha = t * t * (3f - 2f * t);
        return new BiCarta.CellQueryResult<>(primary, secondary, primary, dist, alpha / (1f + alpha), 0f);
    }
}
