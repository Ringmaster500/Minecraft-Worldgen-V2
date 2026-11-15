package com.worldgenv2.hytale;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.vector.VectorProvider;
import com.worldgenv2.vector.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Hytale's Positions Providers, with the semantics of docs.hytale.com/hytale-generator/positions-provider. A provider yields
 * every point of an infinite field inside the queried box (bounds inclusive). The grids have no parameters and a one-block
 * spacing; spacing comes from a Scaler.
 */
public final class HPositions {
    private HPositions() {}

    // Cluster anchors form a stack: a Clusters node sets one for the Cluster slot, an Anchor node reads it.
    private static final ThreadLocal<Deque<double[]>> ANCHORS = ThreadLocal.withInitial(ArrayDeque::new);

    /** The contextual anchor (null when none or at the origin). Distribution Anchor nodes share it with positions Anchors. */
    public static double[] currentAnchor() {
        double[] a = ANCHORS.get().peek();
        return a == null || (a[0] == 0 && a[1] == 0 && a[2] == 0) ? null : a;
    }

    /** Runs the action with the anchor reset to the origin (the effect of a reversed Anchor). */
    public static <R> R withOrigin(java.util.function.Supplier<R> action) {
        Deque<double[]> stack = ANCHORS.get();
        stack.push(new double[]{0, 0, 0});
        try { return action.get(); } finally { stack.pop(); }
    }

    private static double hash01(long seed, double x, double y, double z, int salt) {
        long h = seed ^ (Double.doubleToLongBits(x) * 0x9E3779B97F4A7C15L) ^ (Double.doubleToLongBits(y) * 0xC2B2AE3D27D4EB4FL)
                ^ (Double.doubleToLongBits(z) * 0x165667B19E3779F9L) ^ (salt * 0x27D4EB2F165667C5L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    private static boolean in(Vec3d p, double x0, double y0, double z0, double x1, double y1, double z1) {
        return p.x() >= x0 && p.x() <= x1 && p.y() >= y0 && p.y() <= y1 && p.z() >= z0 && p.z() <= z1;
    }

    // ------------------------------------------------------------------ grids

    public static PositionsProvider squareGrid2d() {
        return (x0, y0, z0, x1, y1, z1) -> {
            if (y0 > 0 || y1 < 0) return new ArrayList<>();
            long xa = (long) Math.ceil(x0), xb = (long) Math.floor(x1), za = (long) Math.ceil(z0), zb = (long) Math.floor(z1);
            List<Vec3d> out = new ArrayList<>((int) Math.max(0, Math.min(1_000_000L, (xb - xa + 1) * (zb - za + 1))));
            for (long x = xa; x <= xb; x++)
                for (long z = za; z <= zb; z++) out.add(new Vec3d(x, 0, z));
            return out;
        };
    }

    public static PositionsProvider squareGrid3d() {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            for (long x = (long) Math.ceil(x0); x <= (long) Math.floor(x1); x++)
                for (long y = (long) Math.ceil(y0); y <= (long) Math.floor(y1); y++)
                    for (long z = (long) Math.ceil(z0); z <= (long) Math.floor(z1); z++) out.add(new Vec3d(x, y, z));
            return out;
        };
    }

    /** A triangular mesh with one block between neighbours: rows sqrt(3)/2 apart, every other row shifted half a block. */
    public static PositionsProvider triangularGrid2d() {
        final double rowStep = Math.sqrt(3.0) / 2.0;
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            if (y0 > 0 || y1 < 0) return out;
            for (long row = (long) Math.ceil(z0 / rowStep); row * rowStep <= z1; row++) {
                double z = row * rowStep, shift = (Math.floorMod(row, 2L) == 1) ? 0.5 : 0.0;
                for (long c = (long) Math.ceil(x0 - shift); c + shift <= x1; c++) out.add(new Vec3d(c + shift, 0, z));
            }
            return out;
        };
    }

    // ------------------------------------------------------------------ transforms

    public static PositionsProvider scaler(PositionsProvider child, double sx, double sy, double sz) {
        final double ax = sx == 0 ? 1 : sx, ay = sy == 0 ? 1 : sy, az = sz == 0 ? 1 : sz;
        return (x0, y0, z0, x1, y1, z1) -> {
            double lx = Math.min(x0 / ax, x1 / ax), hx = Math.max(x0 / ax, x1 / ax);
            double ly = Math.min(y0 / ay, y1 / ay), hy = Math.max(y0 / ay, y1 / ay);
            double lz = Math.min(z0 / az, z1 / az), hz = Math.max(z0 / az, z1 / az);
            List<Vec3d> src = child.getPositions(lx, ly, lz, hx, hy, hz);
            List<Vec3d> out = new ArrayList<>(src.size());
            for (Vec3d p : src) {
                Vec3d q = new Vec3d(p.x() * ax, p.y() * ay, p.z() * az);
                if (in(q, x0, y0, z0, x1, y1, z1)) out.add(q);
            }
            return out;
        };
    }

    /** Moves each position by a deterministic random distance in [0, magnitude] in a random direction (horizontal or all). */
    public static PositionsProvider jitter(PositionsProvider child, double magnitude, long seed, boolean threeD) {
        return (x0, y0, z0, x1, y1, z1) -> {
            double m = Math.abs(magnitude);
            List<Vec3d> src = child.getPositions(x0 - m, threeD ? y0 - m : y0, z0 - m, x1 + m, threeD ? y1 + m : y1, z1 + m);
            List<Vec3d> out = new ArrayList<>(src.size());
            for (Vec3d p : src) {
                double dist = hash01(seed, p.x(), p.y(), p.z(), 1) * m;
                double a = hash01(seed, p.x(), p.y(), p.z(), 2) * Math.PI * 2;
                Vec3d q;
                if (threeD) {
                    double u = hash01(seed, p.x(), p.y(), p.z(), 3) * 2 - 1, r = Math.sqrt(1 - u * u);
                    q = new Vec3d(p.x() + dist * r * Math.cos(a), p.y() + dist * u, p.z() + dist * r * Math.sin(a));
                } else {
                    q = new Vec3d(p.x() + dist * Math.cos(a), p.y(), p.z() + dist * Math.sin(a));
                }
                if (in(q, x0, y0, z0, x1, y1, z1)) out.add(q);
            }
            return out;
        };
    }

    public static PositionsProvider directionalJitter(PositionsProvider child, double magnitude, double dx, double dy, double dz, boolean bidirectional, long seed) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final double ux = len == 0 ? 0 : dx / len, uy = len == 0 ? 1 : dy / len, uz = len == 0 ? 0 : dz / len;
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            double m = Math.abs(magnitude);
            for (Vec3d p : child.getPositions(x0 - m * Math.abs(ux), y0 - m * Math.abs(uy), z0 - m * Math.abs(uz), x1 + m * Math.abs(ux), y1 + m * Math.abs(uy), z1 + m * Math.abs(uz))) {
                double dist = hash01(seed, p.x(), p.y(), p.z(), 1) * m;
                if (bidirectional && hash01(seed, p.x(), p.y(), p.z(), 2) < 0.5) dist = -dist;
                Vec3d q = new Vec3d(p.x() + ux * dist, p.y() + uy * dist, p.z() + uz * dist);
                if (in(q, x0, y0, z0, x1, y1, z1)) out.add(q);
            }
            return out;
        };
    }

    public static PositionsProvider offset(PositionsProvider child, double ox, double oy, double oz) {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> src = child.getPositions(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz);
            List<Vec3d> out = new ArrayList<>(src.size());
            for (Vec3d p : src) out.add(new Vec3d(p.x() + ox, p.y() + oy, p.z() + oz));
            return out;
        };
    }

    /** Offsets each position by the vector at it; a move that leaves the movement bounds (relative to the position) is not made. */
    public static PositionsProvider vectorOffset(PositionsProvider child, VectorProvider vectors, double[] moveMin, double[] moveMax) {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            double rx = Math.max(Math.abs(moveMin[0]), Math.abs(moveMax[0])), ry = Math.max(Math.abs(moveMin[1]), Math.abs(moveMax[1])), rz = Math.max(Math.abs(moveMin[2]), Math.abs(moveMax[2]));
            for (Vec3d p : child.getPositions(x0 - rx, y0 - ry, z0 - rz, x1 + rx, y1 + ry, z1 + rz)) {
                Vec3d v = vectors.getVector(p.x(), p.y(), p.z());
                boolean allowed = v.x() >= moveMin[0] && v.x() <= moveMax[0] && v.y() >= moveMin[1] && v.y() <= moveMax[1] && v.z() >= moveMin[2] && v.z() <= moveMax[2];
                Vec3d q = allowed ? new Vec3d(p.x() + v.x(), p.y() + v.y(), p.z() + v.z()) : p;
                if (in(q, x0, y0, z0, x1, y1, z1)) out.add(q);
            }
            return out;
        };
    }

    // ------------------------------------------------------------------ filters

    /** Limits the field to a box (decimal bounds). */
    public static PositionsProvider bound(PositionsProvider child, double[] min, double[] max) {
        return (x0, y0, z0, x1, y1, z1) -> child.getPositions(Math.max(x0, min[0]), Math.max(y0, min[1]), Math.max(z0, min[2]),
                Math.min(x1, max[0]), Math.min(y1, max[1]), Math.min(z1, max[2]));
    }

    /** Keeps positions where the density is inside a delimiter (min inclusive, max exclusive). */
    public static PositionsProvider fieldFunction(PositionsProvider child, DensityFunction density, double[][] delimiters) {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            for (Vec3d p : child.getPositions(x0, y0, z0, x1, y1, z1)) {
                double v = density.compute((float) p.x(), (float) p.y(), (float) p.z());
                for (double[] d : delimiters) if (v >= d[0] && v < d[1]) { out.add(p); break; }
            }
            return out;
        };
    }

    /** Keeps each position with the probability the density gives (0 or less never, 1 or more always, in between proportional). */
    public static PositionsProvider occurrence(PositionsProvider child, DensityFunction density, long seed) {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            for (Vec3d p : child.getPositions(x0, y0, z0, x1, y1, z1)) {
                double chance = density.compute((float) p.x(), (float) p.y(), (float) p.z());
                if (chance >= 1.0 || (chance > 0.0 && hash01(seed, p.x(), p.y(), p.z(), 9) < chance)) out.add(p);
            }
            return out;
        };
    }

    /** Keeps only positions whose Y lies in [minY, maxY]. */
    public static PositionsProvider simpleHorizontal(PositionsProvider child, double minY, double maxY) {
        return (x0, y0, z0, x1, y1, z1) -> child.getPositions(x0, Math.max(y0, minY), z0, x1, Math.min(y1, maxY), z1);
    }

    /** Raises positions by a named decimal constant (from the World Structure's Framework). */
    public static PositionsProvider baseHeight(PositionsProvider child, java.util.function.DoubleSupplier height) {
        return (x0, y0, z0, x1, y1, z1) -> {
            double h = height.getAsDouble();
            List<Vec3d> out = new ArrayList<>();
            for (Vec3d p : child.getPositions(x0, y0 - h, z0, x1, y1 - h, z1)) out.add(new Vec3d(p.x(), p.y() + h, p.z()));
            return out;
        };
    }

    // ------------------------------------------------------------------ combining

    public static PositionsProvider list(List<Vec3d> points) {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            for (Vec3d p : points) if (in(p, x0, y0, z0, x1, y1, z1)) out.add(p);
            return out;
        };
    }

    public static PositionsProvider union(List<PositionsProvider> parts) {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            for (PositionsProvider p : parts) out.addAll(p.getPositions(x0, y0, z0, x1, y1, z1));
            return out;
        };
    }

    /**
     * The origin of the child field moves to the contextual anchor, when a parent (Clusters) provides one; with no anchor the
     * child is unchanged. {@code reversed} puts the origin back to where it was before the anchor.
     */
    public static PositionsProvider anchor(PositionsProvider child, boolean reversed) {
        return (x0, y0, z0, x1, y1, z1) -> {
            Deque<double[]> stack = ANCHORS.get();
            if (reversed) {
                double[] top = stack.peek();
                if (top == null) return child.getPositions(x0, y0, z0, x1, y1, z1);
                stack.push(new double[]{0, 0, 0});
                try { return child.getPositions(x0, y0, z0, x1, y1, z1); } finally { stack.pop(); }
            }
            double[] a = stack.peek();
            if (a == null || (a[0] == 0 && a[1] == 0 && a[2] == 0)) return child.getPositions(x0, y0, z0, x1, y1, z1);
            List<Vec3d> out = new ArrayList<>();
            for (Vec3d p : child.getPositions(x0 - a[0], y0 - a[1], z0 - a[2], x1 - a[0], y1 - a[1], z1 - a[2])) out.add(new Vec3d(p.x() + a[0], p.y() + a[1], p.z() + a[2]));
            return out;
        };
    }

    /**
     * Clusters of positions: every Distributor position is a cluster's anchor, the Cluster field supplies its contents (placed
     * around the anchor through Anchor nodes), and a cluster reaches at most {@code half} blocks from its anchor on each axis.
     */
    public static PositionsProvider clusters(PositionsProvider cluster, PositionsProvider distributor, double halfX, double halfY, double halfZ) {
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            for (Vec3d a : distributor.getPositions(x0 - halfX, y0 - halfY, z0 - halfZ, x1 + halfX, y1 + halfY, z1 + halfZ)) {
                Deque<double[]> stack = ANCHORS.get();
                stack.push(new double[]{a.x(), a.y(), a.z()});
                try {
                    for (Vec3d p : cluster.getPositions(Math.max(x0, a.x() - halfX), Math.max(y0, a.y() - halfY), Math.max(z0, a.z() - halfZ),
                            Math.min(x1, a.x() + halfX), Math.min(y1, a.y() + halfY), Math.min(z1, a.z() + halfZ))) out.add(p);
                } finally {
                    stack.pop();
                }
            }
            return out;
        };
    }

    /** Remembers the child's output in cubic sections; the answers are identical, only repeated queries get cheaper. */
    public static PositionsProvider cache(PositionsProvider child, int sectionSize, int cacheSize) {
        if (cacheSize <= 0 || sectionSize <= 0) return child;
        final java.util.Map<Long, List<Vec3d>> sections = new java.util.LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long, List<Vec3d>> e) { return size() > cacheSize; }
        };
        return (x0, y0, z0, x1, y1, z1) -> {
            List<Vec3d> out = new ArrayList<>();
            int s = sectionSize;
            for (long sx = Math.floorDiv((long) Math.floor(x0), s); sx <= Math.floorDiv((long) Math.floor(x1), s); sx++)
                for (long sy = Math.floorDiv((long) Math.floor(y0), s); sy <= Math.floorDiv((long) Math.floor(y1), s); sy++)
                    for (long sz = Math.floorDiv((long) Math.floor(z0), s); sz <= Math.floorDiv((long) Math.floor(z1), s); sz++) {
                        long key = (sx * 73856093L) ^ (sy * 19349663L) ^ (sz * 83492791L);
                        List<Vec3d> cached;
                        synchronized (sections) { cached = sections.get(key); }
                        if (cached == null) {
                            cached = child.getPositions(sx * s, sy * s, sz * s, (sx + 1) * s - 1e-9, (sy + 1) * s - 1e-9, (sz + 1) * s - 1e-9);
                            synchronized (sections) { sections.put(key, cached); }
                        }
                        for (Vec3d p : cached) if (in(p, x0, y0, z0, x1, y1, z1)) out.add(p);
                    }
            return out;
        };
    }
}
