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

    /** Base of the providers here: they all take the floor and ceiling contexts, and say how long their answer holds down a column. */
    private abstract static class Prov<T> implements MaterialProvider<T> {
        @Override
        public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
            return getMaterial(x, y, z, depth, space, -1, 0, slopeAngle, spaceRef);
        }

        @Override
        public abstract T getMaterial(int x, int y, int z, int depthIntoFloor, int spaceAboveFloor,
                                      int depthIntoCeiling, int spaceBelowCeiling, float slopeAngle, VoxelSpace<T> spaceRef);

        @Override
        public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) { return 1; }
    }

    private static int smallestAtLeast(double v) { return (int) Math.ceil(v); }

    /** The lack of any material. */
    public static <T> MaterialProvider<T> transparent() {
        return new Prov<T>() {
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) { return null; }
            @Override public boolean usesSlope() { return false; }
            @Override public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) { return Integer.MAX_VALUE; }
            @Override public T getMaterialRun(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref, int[] run) { run[0] = Integer.MAX_VALUE; return null; }
        };
    }

    /** Solid where the terrain density is above 0 (the engine asks the solid side for solid blocks), and the Empty side for the rest. */
    public static <T> MaterialProvider<T> solidity(MaterialProvider<T> solid, MaterialProvider<T> empty) {
        return new Prov<T>() {
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                return solid == null ? null : solid.getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
            }
            @Override public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                return solid == null ? Integer.MAX_VALUE : solid.runLength(x, y, z, df, sf, dc, sc, slope, ref);
            }
            @Override public boolean providesEmpty() { return empty != null; }
            @Override public T getMaterialRun(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref, int[] run) {
                if (solid == null) { run[0] = Integer.MAX_VALUE; return null; }
                return solid.getMaterialRun(x, y, z, df, sf, dc, sc, slope, ref, run);
            }
            final boolean slope = (solid != null && solid.usesSlope()) || (empty != null && empty.usesSlope());
            @Override public boolean usesSlope() { return slope; }
            @Override public T getEmptyMaterial(int x, int y, int z, VoxelSpace<T> ref) {
                return empty == null ? null : empty.getMaterial(x, y, z, 0, 0, -1, 0, 0f, ref);
            }
            @Override public int emptyRunLength(int x, int y, int z, VoxelSpace<T> ref) {
                return empty == null ? Integer.MAX_VALUE : empty.runLength(x, y, z, 0, 0, -1, 0, 0f, ref);
            }
        };
    }

    /** The first provider that provides a block. */
    @SuppressWarnings("unchecked")
    public static <T> MaterialProvider<T> queue(List<MaterialProvider<T>> queue) {
        final MaterialProvider<T>[] arr = queue.toArray(new MaterialProvider[0]);
        return new Prov<T>() {
            final boolean slope = java.util.Arrays.stream(arr).anyMatch(p -> p != null && p.usesSlope());
            @Override public boolean usesSlope() { return slope; }
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                for (int i = 0; i < arr.length; i++) { T m = arr[i].getMaterial(x, y, z, df, sf, dc, sc, slope, ref); if (m != null) return m; }
                return null;
            }
            @Override public T getMaterialRun(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref, int[] run) {
                int r = Integer.MAX_VALUE;
                for (int i = 0; i < arr.length; i++) {
                    T m = arr[i].getMaterialRun(x, y, z, df, sf, dc, sc, slope, ref, run);
                    r = Math.min(r, run[0]);
                    if (m != null) { run[0] = r; return m; }
                }
                run[0] = r;
                return null;
            }
            @Override public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                int run = Integer.MAX_VALUE;
                for (int i = 0; i < arr.length; i++) {
                    T m = arr[i].getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
                    run = Math.min(run, arr[i].runLength(x, y, z, df, sf, dc, sc, slope, ref));
                    if (run <= 1) return 1;
                    if (m != null) return run;
                }
                return run;
            }
        };
    }

    private static final class Limits { volatile boolean ready; double top, bottom; }

    /** Applies the child to Y in [bottomY, topY): bottom inclusive, top exclusive. A base height makes both relative to it. */
    public static <T> MaterialProvider<T> simpleHorizontal(int topY, int bottomY, DoubleSupplier topBase, DoubleSupplier bottomBase, MaterialProvider<T> child) {
        // the base heights are world-structure constants: read them once, on first use, not for every block
        final Limits lim = new Limits();
        return new Prov<T>() {
            @Override public boolean usesSlope() { return child != null && child.usesSlope(); }
            private void init() {
                if (!lim.ready) {
                    lim.top = topY + (topBase == null ? 0 : topBase.getAsDouble());
                    lim.bottom = bottomY + (bottomBase == null ? 0 : bottomBase.getAsDouble());
                    lim.ready = true;   // volatile: published after the numbers
                }
            }
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                init();
                return y >= lim.bottom && y < lim.top ? child.getMaterial(x, y, z, df, sf, dc, sc, slope, ref) : null;
            }
            @Override public T getMaterialRun(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref, int[] run) {
                init();
                if (y >= lim.top) { run[0] = Math.max(1, y - smallestAtLeast(lim.top) + 1); return null; }
                if (y < lim.bottom) { run[0] = Integer.MAX_VALUE; return null; }
                T m = child.getMaterialRun(x, y, z, df, sf, dc, sc, slope, ref, run);
                run[0] = Math.min(run[0], Math.max(1, y - smallestAtLeast(lim.bottom) + 1));
                return m;
            }
            @Override public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                init();
                if (y >= lim.top) return Math.max(1, y - smallestAtLeast(lim.top) + 1);          // nothing until the band begins
                if (y < lim.bottom) return Integer.MAX_VALUE;                                       // below the band: nothing from here down
                int insideBand = Math.max(1, y - smallestAtLeast(lim.bottom) + 1);                  // blocks left in the band
                return Math.min(insideBand, child.runLength(x, y, z, df, sf, dc, sc, slope, ref));
            }
        };
    }

    /** Applies the child inside any stripe; both Y limits are inclusive. */
    public static <T> MaterialProvider<T> striped(List<int[]> stripes, MaterialProvider<T> child) {
        return new Prov<T>() {
            @Override public boolean usesSlope() { return child != null && child.usesSlope(); }
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                for (int[] s : stripes) if (y >= Math.min(s[0], s[1]) && y <= Math.max(s[0], s[1])) return child.getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
                return null;
            }
        };
    }

    /** Picks a provider by weight for each block (a fixed function of position and seed); SkipChance of blocks get no material. */
    public static <T> MaterialProvider<T> weighted(long seed, double skipChance, List<MaterialProvider<T>> providers, List<Double> weights) {
        double total = 0;
        for (double w : weights) total += w;
        final double sum = total;
        return new Prov<T>() {
            final boolean slopeUsed = providers.stream().anyMatch(p -> p != null && p.usesSlope());
            @Override public boolean usesSlope() { return slopeUsed; }
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                if (providers.isEmpty() || sum <= 0) return null;
                if (skipChance > 0 && hash01(seed, x, y, z, 1) < skipChance) return null;
                double r = hash01(seed, x, y, z, 2) * sum;
                for (int i = 0; i < providers.size(); i++) { r -= weights.get(i); if (r < 0) return providers.get(i).getMaterial(x, y, z, df, sf, dc, sc, slope, ref); }
                return providers.get(providers.size() - 1).getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
            }
            @Override public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                return providers.isEmpty() || sum <= 0 ? Integer.MAX_VALUE : 1;                       // chosen per block
            }
        };
    }

    /** A delimiter of a FieldFunction material: field value in [from, to] selects the provider. Later entries win. */
    public record Delimiter<T>(double from, double to, MaterialProvider<T> provider) {}

    public static <T> MaterialProvider<T> fieldFunction(DensityFunction field, List<Delimiter<T>> delimiters) {
        return new Prov<T>() {
            final boolean slopeUsed = delimiters.stream().anyMatch(d -> d.provider() != null && d.provider().usesSlope());
            @Override public boolean usesSlope() { return slopeUsed; }
            private MaterialProvider<T> pick(int x, int y, int z) {
                double v = field.compute(x, y, z);
                for (int i = delimiters.size() - 1; i >= 0; i--) {
                    Delimiter<T> d = delimiters.get(i);
                    if (v >= d.from() && v <= d.to()) return d.provider();
                }
                return null;
            }
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                MaterialProvider<T> p = pick(x, y, z);
                return p == null ? null : p.getMaterial(x, y, z, df, sf, dc, sc, slope, ref);
            }
            @Override public T getMaterialRun(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref, int[] run) {
                MaterialProvider<T> p = pick(x, y, z);
                if (!field.yIndependent()) { run[0] = 1; return p == null ? null : p.getMaterial(x, y, z, df, sf, dc, sc, slope, ref); }
                if (p == null) { run[0] = Integer.MAX_VALUE; return null; }
                return p.getMaterialRun(x, y, z, df, sf, dc, sc, slope, ref, run);
            }
            @Override public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                if (!field.yIndependent()) return 1;                                                  // the field changes from block to block
                MaterialProvider<T> p = pick(x, y, z);
                return p == null ? Integer.MAX_VALUE : p.runLength(x, y, z, df, sf, dc, sc, slope, ref);
            }
        };
    }

    // ------------------------------------------------------------------ conditions and layers of SpaceAndDepth

    /** A Condition predicate testing either {@code SPACE_ABOVE_FLOOR} or {@code SPACE_BELOW_CEILING}. */
    @FunctionalInterface
    public interface SpaceCondition {
        boolean test(int spaceAboveFloor, int spaceBelowCeiling);

        /** True when the answer can change from one block of a column to the next (it reads the space below the ceiling). */
        default boolean usesCeiling() { return true; }
    }

    private static SpaceCondition conditionOf(boolean usesCeiling, SpaceCondition test) {
        return new SpaceCondition() {
            @Override public boolean test(int sf, int sc) { return test.test(sf, sc); }
            @Override public boolean usesCeiling() { return usesCeiling; }
        };
    }

    /** A Condition reads the space above the floor or below the ceiling. */
    public static SpaceCondition condition(String type, String context, int value, List<SpaceCondition> children) {
        boolean ceiling = "SPACE_BELOW_CEILING".equalsIgnoreCase(context);
        boolean kids = false;
        for (SpaceCondition c : children) kids |= c.usesCeiling();
        switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "equalscondition": return conditionOf(ceiling, (sf, sc) -> (ceiling ? sc : sf) == value);
            case "greaterthancondition": return conditionOf(ceiling, (sf, sc) -> (ceiling ? sc : sf) > value);
            case "smallerthancondition": return conditionOf(ceiling, (sf, sc) -> (ceiling ? sc : sf) < value);
            case "andcondition": return conditionOf(kids, (sf, sc) -> { for (SpaceCondition c : children) if (!c.test(sf, sc)) return false; return true; });
            case "orcondition": return conditionOf(kids, (sf, sc) -> { for (SpaceCondition c : children) if (c.test(sf, sc)) return true; return false; });
            case "notcondition": return conditionOf(kids, (sf, sc) -> !children.get(0).test(sf, sc));
            default: return conditionOf(false, (sf, sc) -> true);
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
        return new Prov<T>() {
            final boolean slopeUsed = layers.stream().anyMatch(l -> l.provider() != null && l.provider().usesSlope());
            @Override public boolean usesSlope() { return slopeUsed; }
            @Override public T getMaterial(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                int depth = floor ? df : dc;
                if (depth < 0 || (condition != null && !condition.test(sf, sc))) return null;
                int top = 0;
                for (Layer<T> l : layers) {
                    int bottom = top + Math.max(0, l.thickness(x, z));
                    if (depth < bottom) return l.provider().getMaterial(x, y, z, depth - top, sf, dc, sc, slope, ref);
                    top = bottom;
                }
                return null;
            }
            @Override public T getMaterialRun(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref, int[] run) {
                if (!floor) { run[0] = 1; return getMaterial(x, y, z, df, sf, dc, sc, slope, ref); }
                int depth = df;
                if (depth < 0) { run[0] = 1; return null; }
                boolean ceilingCond = condition != null && condition.usesCeiling();
                if (condition != null && !condition.test(sf, sc)) { run[0] = ceilingCond ? 1 : Integer.MAX_VALUE; return null; }
                int top = 0;
                for (Layer<T> l : layers) {
                    int bottom = top + Math.max(0, l.thickness(x, z));
                    if (depth < bottom) {
                        T m = l.provider().getMaterialRun(x, y, z, depth - top, sf, dc, sc, slope, ref, run);
                        run[0] = ceilingCond ? 1 : Math.min(bottom - depth, run[0]);
                        return m;
                    }
                    top = bottom;
                }
                run[0] = ceilingCond ? 1 : Integer.MAX_VALUE;
                return null;
            }
            @Override public int runLength(int x, int y, int z, int df, int sf, int dc, int sc, float slope, VoxelSpace<T> ref) {
                if (!floor) return 1;                                                                  // the ceiling above changes block to block
                int depth = df;
                if (depth < 0) return 1;
                if (condition != null) {
                    if (condition.usesCeiling()) return 1;
                    if (!condition.test(sf, sc)) return Integer.MAX_VALUE;                            // the space above does not change down a solid run
                }
                int top = 0;
                for (Layer<T> l : layers) {
                    int bottom = top + Math.max(0, l.thickness(x, z));
                    if (depth < bottom) return Math.min(bottom - depth, l.provider().runLength(x, y, z, depth - top, sf, dc, sc, slope, ref));
                    top = bottom;
                }
                return Integer.MAX_VALUE;                                                              // below every layer: nothing from here down
            }
        };
    }
}