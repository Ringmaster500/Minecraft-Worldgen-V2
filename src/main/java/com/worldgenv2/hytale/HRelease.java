package com.worldgenv2.hytale;

import com.worldgenv2.curve.Curve;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.WeightedProp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleSupplier;

/**
 * The scanners and props of the release schema of Hytale's V2 generator, the ones its own asset files still use: ColumnLinear and
 * ColumnRandom scanners, Area, and the Column, Cluster and Box props. In that schema a scanner's {@code ResultCap} limits how many
 * validated positions a prop is placed at.
 */
public final class HRelease {
    private HRelease() {}

    /** A scanner that also says how many validated positions it allows (Hytale's ResultCap). */
    private abstract static class CappedScanner implements HScanner {
        final int cap;
        CappedScanner(int cap) { this.cap = cap; }
        @Override public int resultCap() { return cap; }
    }

    private static List<int[]> column(int ax, int ay, int az, int minY, int maxY, boolean relative, DoubleSupplier base, boolean topDown) {
        int baseY = relative ? ay : (base == null ? 0 : (int) Math.round(base.getAsDouble()));
        List<int[]> out = new ArrayList<>();
        for (int y = Math.min(minY, maxY); y <= Math.max(minY, maxY); y++) out.add(new int[]{ax, baseY + y, az});
        if (topDown) Collections.reverse(out);
        return out;
    }

    /** MinY..MaxY (inclusive) in the column of the anchor, relative to the anchor's Y or to the named base height, top-down or bottom-up. */
    public static HScanner columnLinear(int minY, int maxY, int cap, boolean topDown, boolean relative, DoubleSupplier base) {
        return new CappedScanner(cap) {
            public List<int[]> scan(int ax, int ay, int az, long seed) { return column(ax, ay, az, minY, maxY, relative, base, topDown); }
        };
    }

    /** The same positions in a pseudo-random order. */
    public static HScanner columnRandom(int minY, int maxY, int cap, long seedSalt, boolean relative, DoubleSupplier base) {
        return new CappedScanner(cap) {
            public List<int[]> scan(int ax, int ay, int az, long seed) {
                List<int[]> out = column(ax, ay, az, minY, maxY, relative, base, false);
                Collections.shuffle(out, new Random(seedSalt ^ seed ^ (ax * 31L + az * 17L)));
                return out;
            }
        };
    }

    /** Every column within ScanRange of the anchor (a disc or a square), nearest first, each feeding the child scanner. */
    public static HScanner area(boolean circle, int range, int cap, HScanner child) {
        return new CappedScanner(cap) {
            public List<int[]> scan(int ax, int ay, int az, long seed) {
                List<int[]> cols = new ArrayList<>();
                for (int dx = -range; dx <= range; dx++) for (int dz = -range; dz <= range; dz++) if (!circle || dx * dx + dz * dz <= range * range) cols.add(new int[]{dx, dz});
                cols.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
                List<int[]> out = new ArrayList<>();
                for (int[] c : cols) {
                    if (child == null) out.add(new int[]{ax + c[0], ay, az + c[1]});
                    else out.addAll(child.scan(ax + c[0], ay, az + c[1], seed));
                }
                return out;
            }
            public int reach() { return range + (child == null ? 0 : child.reach()); }
        };
    }

    private static double hash01(long seed, int x, int z, int salt) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    /**
     * Hytale's Cluster: scatters weighted props around the anchor. Each column within Range gets a prop with the probability the
     * DistanceCurve gives for its distance from the anchor.
     */
    public static final class Cluster<T> implements Prop<T> {
        private final int range;
        private final Curve curve;
        private final long salt;
        private final List<WeightedProp.Entry<T>> entries;
        private final double total;

        public Cluster(int range, Curve curve, long salt, List<WeightedProp.Entry<T>> entries) {
            this.range = Math.max(0, range);
            this.curve = curve;
            this.salt = salt;
            this.entries = entries;
            double t = 0;
            for (WeightedProp.Entry<T> e : entries) t += e.weight();
            this.total = t;
        }

        @Override
        public boolean generate(Context<T> ctx) {
            if (entries.isEmpty() || total <= 0) return false;
            boolean any = false;
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > range) continue;
                    int x = ctx.worldX() + dx, z = ctx.worldZ() + dz;
                    if (hash01(salt ^ ctx.seed(), x, z, 1) >= curve.evaluate(d)) continue;
                    double r = hash01(salt ^ ctx.seed(), x, z, 2) * total;
                    Prop<T> pick = entries.get(entries.size() - 1).prop();
                    for (WeightedProp.Entry<T> e : entries) { r -= e.weight(); if (r < 0) { pick = e.prop(); break; } }
                    long seed = ctx.seed() ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
                    if (pick.generate(new Context<>(x, ctx.worldY(), z, ctx.normalX(), ctx.normalY(), ctx.normalZ(), ctx.voxelSpace(), seed))) any = true;
                }
            }
            return any;
        }

        @Override
        public int horizontalReach() {
            int r = 0;
            for (WeightedProp.Entry<T> e : entries) r = Math.max(r, e.prop().horizontalReach());
            return range + r;
        }
    }

    /**
     * Prefab with Hytale's {@code Directionality}: Static (one rotation), Random (any of the four), or Pattern (a pattern per compass
     * direction the prefab can face). The {@code Pattern} inside decides where the prefab may stand.
     */
    public static final class DirectionalPrefab<T> implements Prop<T> {
        private final Prop<T> child;
        private final HScanner scanner;
        private final HPattern<T>[] patterns;   // N, E, S, W; null entries are not allowed directions
        private final int initial;
        private final long salt;
        private final int cap;

        public DirectionalPrefab(Prop<T> child, HScanner scanner, HPattern<T>[] patterns, int initial, long salt, int cap) {
            this.child = child; this.scanner = scanner; this.patterns = patterns; this.initial = initial; this.salt = salt; this.cap = Math.max(1, cap);
        }

        @Override
        public boolean generate(Context<T> ctx) {
            HPattern.Probe<T> probe = new HPattern.Probe<>(ctx.voxelSpace());
            int placed = 0;
            for (int[] p : scanner.scan(ctx.worldX(), ctx.worldY(), ctx.worldZ(), ctx.seed())) {
                List<Integer> valid = new ArrayList<>();
                for (int d = 0; d < 4; d++) if (patterns[d] != null && patterns[d].test(probe, p[0], p[1], p[2], 0)) valid.add(d);
                if (valid.isEmpty()) continue;
                int d = valid.get((int) (hash01(salt ^ ctx.seed(), p[0], p[2], 7) * valid.size()));
                int turns = Math.floorMod(d - initial, 4);
                Prop<T> turned = turns == 0 ? child : new com.worldgenv2.props.StaticRotatorProp<>(child, com.worldgenv2.props.StaticRotatorProp.CardinalRotation.values()[turns]);
                long seed = ctx.seed() ^ (p[0] * 0x9E3779B97F4A7C15L) ^ (p[2] * 0xC2B2AE3D27D4EB4FL);
                if (turned.generate(new Context<>(p[0], p[1], p[2], ctx.normalX(), ctx.normalY(), ctx.normalZ(), ctx.voxelSpace(), seed))) {
                    placed++;
                    if (placed >= cap) break;
                }
            }
            return placed > 0;
        }

        @Override
        public int horizontalReach() { return scanner.reach() + child.horizontalReach(); }
    }
}