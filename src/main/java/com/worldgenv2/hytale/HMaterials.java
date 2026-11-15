package com.worldgenv2.hytale;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Hytale's Material Providers (docs.hytale.com/hytale-generator/material-provider). A provider answers, for one block, which
 * material goes there, or nothing ({@code null}), in which case a {@link #queue Queue} moves on to the next. The engine passes
 * the depth into the floor and the free space above it, which are the contexts Hytale's layers and conditions read.
 */
public final class HMaterials {
    private HMaterials() {}

    private static double hash01(long seed, int x, int y, int z, int salt) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ (salt * 0x27D4EB2F165667C5L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    @FunctionalInterface
    private interface FullProvider<T> extends MaterialProvider<T> {
        @Override
        default T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
            return getMaterial(x, y, z, depth, space, -1, 0, slopeAngle, spaceRef);
        }

        @Override
        T getMaterial(int x, int y, int z, int depthIntoFloor, int spaceAboveFloor,
                      int depthIntoCeiling, int spaceBelowCeiling, float slopeAngle, VoxelSpace<T> spaceRef);
    }

    /** The lack of any material. */
    public static <T> MaterialProvider<T> transparent() {
        return (x, y, z, depth, space, slope, ref) -> null;
    }

    /** Solid where the terrain density is above 0 (the engine asks providers for solid blocks), Empty elsewhere. */
    public static <T> MaterialProvider<T> solidity(MaterialProvider<T> solid, MaterialProvider<T> empty) {
        return (FullProvider<T>) (x, y, z, df, sf, dc, sc, slope, ref) ->
                solid == null ? null : solid.getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
    }

    /** The first provider that provides a block. */
    public static <T> MaterialProvider<T> queue(List<MaterialProvider<T>> queue) {
        return (FullProvider<T>) (x, y, z, df, sf, dc, sc, slope, ref) -> {
            for (MaterialProvider<T> p : queue) { T m = p.getMaterial(x, y, z, df, sf, dc, sc, slope, ref); if (m != null) return m; }
            return null;
        };
    }

    /** Applies the child to Y in [bottomY, topY): bottom inclusive, top exclusive. A base height makes both relative to it. */
    public static <T> MaterialProvider<T> simpleHorizontal(int topY, int bottomY, DoubleSupplier topBase, DoubleSupplier bottomBase, MaterialProvider<T> child) {
        return (FullProvider<T>) (x, y, z, df, sf, dc, sc, slope, ref) -> {
            double top = topY + (topBase == null ? 0 : topBase.getAsDouble()), bottom = bottomY + (bottomBase == null ? 0 : bottomBase.getAsDouble());
            return y >= bottom && y < top ? child.getMaterial(x, y, z, df, sf, dc, sc, slope, ref) : null;
        };
    }

    /** Applies the child inside any stripe; both Y limits are inclusive. */
    public static <T> MaterialProvider<T> striped(List<int[]> stripes, MaterialProvider<T> child) {
        return (FullProvider<T>) (x, y, z, df, sf, dc, sc, slope, ref) -> {
            for (int[] s : stripes) if (y >= Math.min(s[0], s[1]) && y <= Math.max(s[0], s[1])) return child.getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
            return null;
        };
    }

    /** Picks a provider by weight for each block (a fixed function of position and seed); SkipChance of blocks get no material. */
    public static <T> MaterialProvider<T> weighted(long seed, double skipChance, List<MaterialProvider<T>> providers, List<Double> weights) {
        double total = 0;
        for (double w : weights) total += w;
        final double sum = total;
        return (FullProvider<T>) (x, y, z, df, sf, dc, sc, slope, ref) -> {
            if (providers.isEmpty() || sum <= 0) return null;
            if (skipChance > 0 && hash01(seed, x, y, z, 1) < skipChance) return null;
            double r = hash01(seed, x, y, z, 2) * sum;
            for (int i = 0; i < providers.size(); i++) { r -= weights.get(i); if (r < 0) return providers.get(i).getMaterial(x, y, z, df, sf, dc, sc, slope, ref); }
            return providers.get(providers.size() - 1).getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
        };
    }

    /** A delimiter of a FieldFunction material: field value in [from, to] selects the provider. Later entries win. */
    public record Delimiter<T>(double from, double to, MaterialProvider<T> provider) {}

    public static <T> MaterialProvider<T> fieldFunction(DensityFunction field, List<Delimiter<T>> delimiters) {
        return (FullProvider<T>) (x, y, z, df, sf, dc, sc, slope, ref) -> {
            double v = field.compute(x, y, z);
            for (int i = delimiters.size() - 1; i >= 0; i--) {
                Delimiter<T> d = delimiters.get(i);
                if (v >= d.from() && v <= d.to()) return d.provider().getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
            }
            return null;
        };
    }

    // ------------------------------------------------------------------ conditions and layers of SpaceAndDepth

    /** A Condition predicate testing either {@code SPACE_ABOVE_FLOOR} or {@code SPACE_BELOW_CEILING}. */
    @FunctionalInterface
    public interface SpaceCondition {
        boolean test(int spaceAboveFloor, int spaceBelowCeiling);
    }

    /** A Condition reads the space above the floor or below the ceiling. */
    public static SpaceCondition condition(String type, String context, int value, List<SpaceCondition> children) {
        boolean ceiling = "SPACE_BELOW_CEILING".equalsIgnoreCase(context);
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "equalscondition": return (sf, sc) -> (ceiling ? sc : sf) == value;
            case "greaterthancondition": return (sf, sc) -> (ceiling ? sc : sf) > value;
            case "smallerthancondition": return (sf, sc) -> (ceiling ? sc : sf) < value;
            case "andcondition": return (sf, sc) -> { for (SpaceCondition c : children) if (!c.test(sf, sc)) return false; return true; };
            case "orcondition": return (sf, sc) -> { for (SpaceCondition c : children) if (c.test(sf, sc)) return true; return false; };
            case "notcondition": return (sf, sc) -> !children.get(0).test(sf, sc);
            default: return (sf, sc) -> true;
        }
    }

    /** One layer of a SpaceAndDepth: a provider and how many blocks thick it is at an x/z column. */
    public interface Layer<T> {
        int thickness(int x, int z);
        MaterialProvider<T> provider();
    }

    public static <T> Layer<T> constantThickness(MaterialProvider<T> p, int thickness) {
        return new Layer<>() { public int thickness(int x, int z) { return thickness; } public MaterialProvider<T> provider() { return p; } };
    }

    public static <T> Layer<T> rangeThickness(MaterialProvider<T> p, int min, int max, long seed) {
        int lo = Math.min(min, max), hi = Math.max(min, max);
        return new Layer<>() {
            public int thickness(int x, int z) { return lo + (int) Math.floor(hash01(seed, x, 0, z, 3) * (hi - lo + 1)); }
            public MaterialProvider<T> provider() { return p; }
        };
    }

    public static <T> Layer<T> weightedThickness(MaterialProvider<T> p, List<Integer> thicknesses, List<Double> weights, long seed) {
        double total = 0;
        for (double w : weights) total += w;
        final double sum = total;
        return new Layer<>() {
            public int thickness(int x, int z) {
                if (thicknesses.isEmpty() || sum <= 0) return 0;
                double r = hash01(seed, x, 0, z, 4) * sum;
                for (int i = 0; i < thicknesses.size(); i++) { r -= weights.get(i); if (r < 0) return thicknesses.get(i); }
                return thicknesses.get(thicknesses.size() - 1);
            }
            public MaterialProvider<T> provider() { return p; }
        };
    }

    /** Thickness from a noise function sampled on the x/z plane (Y is 0), rounded to whole blocks. */
    public static <T> Layer<T> noiseThickness(MaterialProvider<T> p, DensityFunction fn) {
        return new Layer<>() {
            public int thickness(int x, int z) { return Math.max(0, Math.round(fn.compute(x, 0, z))); }
            public MaterialProvider<T> provider() { return p; }
        };
    }

    /**
     * Piles layers into the floor or ceiling like a cake: the first layer from the exposed surface for its thickness,
     * then the next, and so on. With a Condition that fails (for example not enough open space above/below) the provider
     * gives no block.
     */
    public static <T> MaterialProvider<T> spaceAndDepth(boolean floor, SpaceCondition condition, List<Layer<T>> layers) {
        return (FullProvider<T>) (x, y, z, df, sf, dc, sc, slope, ref) -> {
            int depth = floor ? df : dc;
            if (depth < 0 || (condition != null && !condition.test(sf, sc))) return null;
            int top = 0;
            for (Layer<T> l : layers) {
                int bottom = top + Math.max(0, l.thickness(x, z));
                if (depth < bottom) return l.provider().getMaterial(x, y, z, depth - top, sf, dc, sc, slope, ref);
                top = bottom;
            }
            return null;
        };
    }
}
