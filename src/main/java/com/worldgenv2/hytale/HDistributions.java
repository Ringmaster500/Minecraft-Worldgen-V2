package com.worldgenv2.hytale;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.props.Distribution;
import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Hytale's Prop Distribution nodes (docs.hytale.com/hytale-generator/prop-distribution): a field of positioned props, where a
 * position may still be empty (no prop yet). Constant pairs a prop with every position, Positions makes empty positions, Assigned
 * fills empty positions from Assignments, Union combines fields, Anchor moves the origin to the contextual anchor.
 */
public final class HDistributions {
    private HDistributions() {}

    /** One position of a distribution; {@code prop} is null while the position is empty. */
    public record Placed<T>(Vec3d position, Prop<T> prop) {}

    /** A field of positioned props over a box (bounds inclusive). */
    public interface Field<T> {
        List<Placed<T>> evaluate(double x0, double y0, double z0, double x1, double y1, double z1, long seed);

        /** How far the props this field can place reach horizontally. */
        default int reach() { return 0; }
    }

    public static <T> Field<T> constant(PositionsProvider positions, Prop<T> prop) {
        return new Field<>() {
            public List<Placed<T>> evaluate(double x0, double y0, double z0, double x1, double y1, double z1, long seed) {
                List<Placed<T>> out = new ArrayList<>();
                for (Vec3d p : positions.getPositions(x0, y0, z0, x1, y1, z1)) out.add(new Placed<>(p, prop));
                return out;
            }
            public int reach() { return prop == null ? 0 : prop.horizontalReach(); }
        };
    }

    public static <T> Field<T> positions(PositionsProvider positions) {
        return constant(positions, null);
    }

    public static <T> Field<T> assigned(Field<T> child, Assignments<T> assignments, boolean overrideAll) {
        return new Field<>() {
            public List<Placed<T>> evaluate(double x0, double y0, double z0, double x1, double y1, double z1, long seed) {
                List<Placed<T>> out = new ArrayList<>();
                for (Placed<T> c : child.evaluate(x0, y0, z0, x1, y1, z1, seed)) {
                    if (c.prop() != null && !overrideAll) { out.add(c); continue; }
                    long s = seedAt(seed, c.position());
                    out.add(new Placed<>(c.position(), assignments.assign(c.position(), s)));
                }
                return out;
            }
            public int reach() { return Math.max(child.reach(), assignments.horizontalReach()); }
        };
    }

    public static <T> Field<T> union(List<Field<T>> children) {
        return new Field<>() {
            public List<Placed<T>> evaluate(double x0, double y0, double z0, double x1, double y1, double z1, long seed) {
                List<Placed<T>> out = new ArrayList<>();
                for (Field<T> c : children) out.addAll(c.evaluate(x0, y0, z0, x1, y1, z1, seed));
                return out;
            }
            public int reach() { int r = 0; for (Field<T> c : children) r = Math.max(r, c.reach()); return r; }
        };
    }

    /** Moves the child's origin to the contextual anchor (no anchor: unchanged); Reversed moves it back to the world origin. */
    public static <T> Field<T> anchor(Field<T> child, boolean reversed) {
        return new Field<>() {
            public List<Placed<T>> evaluate(double x0, double y0, double z0, double x1, double y1, double z1, long seed) {
                if (reversed) return HPositions.currentAnchor() == null ? child.evaluate(x0, y0, z0, x1, y1, z1, seed) : HPositions.withOrigin(() -> child.evaluate(x0, y0, z0, x1, y1, z1, seed));
                double[] a = HPositions.currentAnchor();
                if (a == null) return child.evaluate(x0, y0, z0, x1, y1, z1, seed);
                List<Placed<T>> out = new ArrayList<>();
                for (Placed<T> p : child.evaluate(x0 - a[0], y0 - a[1], z0 - a[2], x1 - a[0], y1 - a[1], z1 - a[2], seed))
                    out.add(new Placed<>(new Vec3d(p.position().x() + a[0], p.position().y() + a[1], p.position().z() + a[2]), p.prop()));
                return out;
            }
            public int reach() { return child.reach(); }
        };
    }

    /** Hytale's Graph distribution: every node within Range contributes its PropDistribution entry on the layer, combined. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> Field<T> graph(com.worldgenv2.graph.GraphGenerator gen, String layer, long graphSeed) {
        return new Field<>() {
            public List<Placed<T>> evaluate(double x0, double y0, double z0, double x1, double y1, double z1, long seed) {
                Vec3d center = new Vec3d((x0 + x1) * 0.5, (y0 + y1) * 0.5, (z0 + z1) * 0.5);
                double half = Math.sqrt(Math.pow((x1 - x0) * 0.5, 2) + Math.pow((y1 - y0) * 0.5, 2) + Math.pow((z1 - z0) * 0.5, 2));
                List<Placed<T>> out = new ArrayList<>();
                for (com.worldgenv2.graph.GraphNode node : gen.getNodesInRange(graphSeed, center, 128.0, half)) {
                    var opt = node.getContent().getFirstProp(layer);
                    if (opt.isEmpty() || opt.get().range() <= 0.0) continue;
                    var entry = opt.get();
                    Field<T> f = entry.prop() instanceof FieldProp fp ? fp.field() : constant((a, b, c, d, e, g) -> List.of(node.getPosition()), (Prop<T>) entry.prop());
                    for (Placed<T> p : f.evaluate(x0, y0, z0, x1, y1, z1, seed)) if (p.position().distance(node.getPosition()) <= entry.range()) out.add(p);
                }
                return out;
            }
        };
    }

    static long seedAt(long seed, Vec3d p) {
        long s = seed ^ (((long) Math.floor(p.x())) * 0x9E3779B97F4A7C15L) ^ (((long) Math.floor(p.z())) * 0xC2B2AE3D27D4EB4FL) ^ (((long) Math.floor(p.y())) * 0x165667B19E3779F9L);
        return s ^ (s >>> 29);
    }

    /** Runs a field as a prop: every placed prop is generated at its position, in a fixed global order, with position-only seeds. */
    public static final class FieldProp<T> implements Distribution<T> {
        private final Field<T> field;

        public FieldProp(Field<T> field) { this.field = field; }

        public Field<T> field() { return field; }

        @Override
        public boolean generate(Context<T> ctx) {
            int x = ctx.worldX() - 8, z = ctx.worldZ() - 8;
            return generateInRegion(x, z, x + 16, z + 16, ctx.voxelSpace(), ctx.seed(), null, b -> b != null) > 0;
        }

        @Override
        public int generateInRegion(int minX, int minZ, int maxX, int maxZ, VoxelSpace<T> space, long seedBase,
                                    BiPredicate<Integer, Integer> accept, Predicate<T> solid) {
            return generateInRegion(minX, minZ, maxX, maxZ, minX, minZ, maxX, maxZ, space, seedBase, accept, solid);
        }

        @Override
        public int generateInRegion(int minX, int minZ, int maxX, int maxZ, int needMinX, int needMinZ, int needMaxX, int needMaxZ,
                                    VoxelSpace<T> space, long seedBase, BiPredicate<Integer, Integer> accept, Predicate<T> solid) {
            return generateInRegion(minX, minZ, maxX, maxZ, needMinX, needMinZ, needMaxX, needMaxZ, space, seedBase, accept, solid, null);
        }

        @Override
        public int generateInRegion(int minX, int minZ, int maxX, int maxZ, int needMinX, int needMinZ, int needMaxX, int needMaxZ,
                                    VoxelSpace<T> space, long seedBase, BiPredicate<Integer, Integer> accept, Predicate<T> solid,
                                    com.worldgenv2.props.AnchorMemo<T> memo) {
            if (memo == null || NO_CELLS) {
                List<Placed<T>> all = new ArrayList<>(field.evaluate(minX, space.getMinY(), minZ, maxX, space.getMaxY(), maxZ, seedBase));
                all.sort(ORDER);
                int placed = 0;
                for (Placed<T> p : all) placed += anchor(p, minX, minZ, maxX, maxZ, needMinX, needMinZ, needMaxX, needMaxZ, space, seedBase, accept, memo);
                return placed;
            }
            // The positions of a 16 x 16 cell are the same whichever chunk asks, so each cell is evaluated (and sorted) once and shared.
            // Cells of one column of x are merged in the global order (x, then z, then y); columns follow each other in x. Positions
            // outside the region are skipped before merging, which leaves the order of the others as it was.
            int placed = 0;
            int y0 = space.getMinY(), y1 = space.getMaxY();
            int cz0 = Math.floorDiv(minZ, 16), cz1 = Math.floorDiv(maxZ - 1, 16), k = cz1 - cz0 + 1;
            Cell<?>[] cells = new Cell<?>[k];
            int[] heads = new int[k], ends = new int[k];
            for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX - 1, 16); cx++) {
                for (int i = 0; i < k; i++) {
                    final int fcx = cx, fcz = cz0 + i;
                    Cell<?> c = (Cell<?>) memo.cell(this, seedBase, cx, fcz, () -> cellPositions(fcx, fcz, y0, y1, seedBase));
                    cells[i] = c;
                    heads[i] = c.firstAtLeast(minX);
                    ends[i] = c.firstAtLeast(maxX);
                }
                while (true) {
                    int best = -1;
                    double bx = 0, bz = 0, by = 0;
                    for (int i = 0; i < k; i++) {
                        Cell<?> c = cells[i];
                        int h = heads[i];
                        while (h < ends[i] && (c.z[h] < minZ || c.z[h] >= maxZ)) h++;
                        heads[i] = h;
                        if (h >= ends[i]) continue;
                        double x = c.x[h], z = c.z[h], y = c.y[h];
                        if (best < 0) { best = i; bx = x; bz = z; by = y; continue; }
                        // exactly the comparator's order (Double.compare also orders -0.0 before 0.0)
                        int cmp = Double.compare(x, bx);
                        if (cmp == 0) cmp = Double.compare(z, bz);
                        if (cmp == 0) cmp = Double.compare(y, by);
                        if (cmp < 0) { best = i; bx = x; bz = z; by = y; }
                    }
                    if (best < 0) break;
                    @SuppressWarnings("unchecked") Placed<T> p = (Placed<T>) cells[best].items[heads[best]++];
                    placed += anchor(p, minX, minZ, maxX, maxZ, needMinX, needMinZ, needMaxX, needMaxZ, space, seedBase, accept, memo);
                }
            }
            return placed;
        }

        /** A cell's positions in the global order, with their coordinates as plain arrays for a fast merge. */
        private static final class Cell<T> {
            final Placed<T>[] items;
            final double[] x, y, z;

            @SuppressWarnings("unchecked")
            Cell(List<Placed<T>> sorted) {
                items = sorted.toArray(new Placed[0]);
                x = new double[items.length];
                y = new double[items.length];
                z = new double[items.length];
                for (int i = 0; i < items.length; i++) { x[i] = items[i].position().x(); y[i] = items[i].position().y(); z[i] = items[i].position().z(); }
            }

            /** The first index whose x is at least v (x is sorted). */
            int firstAtLeast(double v) {
                int lo = 0, hi = x.length;
                while (lo < hi) { int mid = (lo + hi) >>> 1; if (x[mid] < v) lo = mid + 1; else hi = mid; }
                return lo;
            }
        }
        /** The global order of a distribution's positions: x, then z, then y (a stable sort keeps equal positions in field order). */
        private static final java.util.Comparator<Placed<?>> ORDER = (a, b) -> {
            int c = Double.compare(a.position().x(), b.position().x());
            if (c != 0) return c;
            c = Double.compare(a.position().z(), b.position().z());
            return c != 0 ? c : Double.compare(a.position().y(), b.position().y());
        };

        /** One position: builds its prop (or replays it) if it lies in the region, can reach what will be read, and its biome has it. */
        private int anchor(Placed<T> p, int minX, int minZ, int maxX, int maxZ, int needMinX, int needMinZ, int needMaxX, int needMaxZ,
                           VoxelSpace<T> space, long seedBase, BiPredicate<Integer, Integer> accept, com.worldgenv2.props.AnchorMemo<T> memo) {
            int px = (int) Math.floor(p.position().x()), py = (int) Math.floor(p.position().y()), pz = (int) Math.floor(p.position().z());
            if (p.prop() == null || px < minX || px >= maxX || pz < minZ || pz >= maxZ) return 0;
            int reach = p.prop().contentReach();
            if (px + reach < needMinX || px - reach >= needMaxX || pz + reach < needMinZ || pz - reach >= needMaxZ) return 0;   // builds nothing anyone reads
            if (accept != null && !accept.test(px, pz)) return 0;
            long seed = seedAt(seedBase, p.position());
            if (memo == null || !memo.remembers()) {
                return p.prop().generate(new Context<>(px, py, pz, 0.0f, 1.0f, 0.0f, space, seed)) ? 1 : 0;
            }
            if (MEMO_CHECK) { final int fx = px, fy = py, fz = pz; memo.verify(seedBase, px, py, pz, p.prop(), space, s -> p.prop().generate(new Context<>(fx, fy, fz, 0.0f, 1.0f, 0.0f, s, seed))); }
            if (memo.replay(seedBase, px, py, pz, p.prop(), space)) return 1;
            com.worldgenv2.props.AnchorMemo.Recorder<T> rec = memo.recorder(space);
            boolean ok = p.prop().generate(new Context<>(px, py, pz, 0.0f, 1.0f, 0.0f, rec, seed));
            memo.remember(seedBase, px, py, pz, p.prop(), rec);
            return ok ? 1 : 0;
        }
        /** The positions whose block lies in cell (cx, cz), each exactly once (a position on a cell edge belongs to the cell it starts). */
        private Cell<T> cellPositions(int cx, int cz, int y0, int y1, long seedBase) {

            int x0 = cx << 4, z0 = cz << 4;
            List<Placed<T>> out = new ArrayList<>();
            for (Placed<T> p : field.evaluate(x0, y0, z0, x0 + 16, y1, z0 + 16, seedBase)) {
                int px = (int) Math.floor(p.position().x()), pz = (int) Math.floor(p.position().z());
                if (px >= x0 && px < x0 + 16 && pz >= z0 && pz < z0 + 16) out.add(p);
            }
            out.sort(ORDER);
            return new Cell<>(out);
        }

        private static final boolean CELL_CHECK = System.getenv("WG2_CELL_CHECK") != null;

        /** Dev check: the per-cell positions must equal the region's own positions (same points, same props, any order). */
        private void checkCells(List<Placed<T>> cells, List<Placed<T>> region, int minX, int minZ, int maxX, int maxZ) {
            java.util.Map<String, Integer> a = new java.util.HashMap<>(), b = new java.util.HashMap<>();
            for (Placed<T> p : cells) a.merge(key(p), 1, Integer::sum);
            for (Placed<T> p : region) {
                int px = (int) Math.floor(p.position().x()), pz = (int) Math.floor(p.position().z());
                if (px >= minX && px < maxX && pz >= minZ && pz < maxZ) b.merge(key(p), 1, Integer::sum);
            }
            if (a.equals(b)) return;
            StringBuilder sb = new StringBuilder("cells " + cells.size() + " vs region " + b.values().stream().mapToInt(Integer::intValue).sum() + ":");
            int shown = 0, shown2 = 0;
            for (var e : b.entrySet()) if (!e.getValue().equals(a.get(e.getKey())) && shown++ < 3) sb.append(" missing-in-cells ").append(e.getKey());
            for (var e : a.entrySet()) if (!e.getValue().equals(b.get(e.getKey())) && shown2++ < 3) sb.append(" extra-in-cells ").append(e.getKey());
            sb.append(" | ").append(describeField());
            log(sb.toString());
        }

        private String describeField() { return field.getClass().getName(); }

        private static void log(String s) {
            try {
                synchronized (FieldProp.class) {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "wg2_cell_diff.txt"), s + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                }
            } catch (Exception ignored) { }
        }

        private void unusedLog(StringBuilder sb) {
            try {
                synchronized (FieldProp.class) {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "wg2_cell_diff.txt"), sb + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                }
            } catch (Exception ignored) { }
        }

        private static String key(Placed<?> p) {
            return p.position().x() + "," + p.position().y() + "," + p.position().z() + ":" + (p.prop() == null ? "-" : p.prop().getClass().getSimpleName() + "@" + System.identityHashCode(p.prop()));
        }

        /** Dev switch for A/B checks: WG2_NO_CELLS=1 evaluates positions per region as before. */
        private static final boolean NO_CELLS = System.getenv("WG2_NO_CELLS") != null;
        private static final boolean MEMO_CHECK = System.getenv("WG2_MEMO_CHECK") != null;

        @Override
        public int horizontalReach() { return field.reach(); }
    }
}