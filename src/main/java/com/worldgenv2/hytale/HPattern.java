package com.worldgenv2.hytale;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.density.DensityFunction;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Hytale's Pattern: validates a world position by what is around it. A pattern is asked about an <em>origin</em> and a
 * <em>rotation</em> (quarter turns about the vertical axis); composite patterns look at positions relative to the origin, and
 * rotating turns those relative positions, which is how an Orienter tries a prop in four directions.
 *
 * <p>The semantics follow docs.hytale.com/hytale-generator/pattern: Constant, BlockType, BlockSet, Offset, Rotator, Floor,
 * Ceiling, Wall, Surface, Cuboid, And, Or, Not and FieldFunction.
 */
public interface HPattern<T> {

    /** What a pattern can look at. */
    final class Probe<T> {
        public final VoxelSpace<T> space;

        public Probe(VoxelSpace<T> space) { this.space = Objects.requireNonNull(space); }

        public T block(int x, int y, int z) { return space.getBlock(x, y, z); }

        public boolean air(int x, int y, int z) { return space.isAir(x, y, z); }
    }

    boolean test(Probe<T> probe, int x, int y, int z, int rot);

    /** How far horizontally from the origin this pattern can look. */
    default int reach() { return 0; }

    // ------------------------------------------------------------------ geometry helpers

    /** Rotates the horizontal vector (dx, dz) clockwise by {@code rot} quarter turns. */
    static int[] turn(int dx, int dz, int rot) {
        int r = Math.floorMod(rot, 4);
        for (int i = 0; i < r; i++) { int t = dx; dx = -dz; dz = t; }
        return new int[]{dx, dz};
    }

    // ------------------------------------------------------------------ the patterns

    static <T> HPattern<T> constant(boolean value) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) { return value; }
        };
    }

    /** The block must be exactly {@code block}; {@code air} means "empty space" (air of any kind). */
    static <T> HPattern<T> blockType(T block, boolean air) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                return air ? p.air(x, y, z) : Objects.equals(p.block(x, y, z), block);
            }
        };
    }

    static <T> HPattern<T> blockSet(Set<T> blocks, boolean includesAir) {
        return new HPattern<>() {
            /** Block states are shared objects, and a pattern sees the same few thousands of times: remember the answer per object. */
            final class Known { final Object block; final boolean member; Known(Object b, boolean m) { block = b; member = m; } }
            final Known[] known = new Known[64];

            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                if (includesAir && p.air(x, y, z)) return true;
                T b = p.block(x, y, z);
                int slot = System.identityHashCode(b) & 63;
                Known k = known[slot];
                if (k != null && k.block == b) return k.member;
                boolean member = blocks.contains(b);
                known[slot] = new Known(b, member);
                return member;
            }
        };
    }

    static <T> HPattern<T> offset(HPattern<T> child, int ox, int oy, int oz) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                int[] v = turn(ox, oz, rot);
                return child.test(p, x + v[0], y + oy, z + v[1], rot);
            }
            public int reach() { return Math.max(Math.abs(ox), Math.abs(oz)) + child.reach(); }
        };
    }

    static <T> HPattern<T> rotator(HPattern<T> child, int turns) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) { return child.test(p, x, y, z, rot + turns); }
            public int reach() { return child.reach(); }
        };
    }

    /** The origin position validates {@code origin}; the block directly under it validates {@code floor}. */
    static <T> HPattern<T> floor(HPattern<T> floor, HPattern<T> origin) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) { return origin.test(p, x, y, z, rot) && floor.test(p, x, y - 1, z, rot); }
            public int reach() { return Math.max(floor.reach(), origin.reach()); }
        };
    }

    static <T> HPattern<T> ceiling(HPattern<T> ceiling, HPattern<T> origin) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) { return origin.test(p, x, y, z, rot) && ceiling.test(p, x, y + 1, z, rot); }
            public int reach() { return Math.max(ceiling.reach(), origin.reach()); }
        };
    }

    /**
     * A wall beside the origin in the listed directions (0 = N, 1 = S, 2 = E, 3 = W; N is -Z, E is +X), turned by the rotation.
     * With {@code requireAll}, every listed direction must hold; otherwise one is enough.
     */
    static <T> HPattern<T> wall(HPattern<T> wall, HPattern<T> origin, List<Integer> directions, boolean requireAll) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                if (!origin.test(p, x, y, z, rot)) return false;
                if (directions.isEmpty()) return true;
                for (int d : directions) {
                    int dx = d == 2 ? 1 : d == 3 ? -1 : 0, dz = d == 0 ? -1 : d == 1 ? 1 : 0;
                    int[] v = turn(dx, dz, rot);
                    boolean ok = wall.test(p, x + v[0], y, z + v[1], rot);
                    if (requireAll && !ok) return false;
                    if (!requireAll && ok) return true;
                }
                return requireAll;
            }
            public int reach() { return 1 + Math.max(wall.reach(), origin.reach()); }
        };
    }

    /**
     * A surface: a transition from one material to another. For a facing, the <em>medium</em> is at the origin and the
     * <em>surface</em> lies behind it (an up-facing surface has the medium at the origin and the surface below). Each is
     * checked over a disc ({@code mediumRadius}, {@code surfaceRadius}); a gap lets the layer sit up to that many blocks further
     * away, which allows some roughness. Facings: 0 N, 1 S, 2 E, 3 W, 4 U, 5 D.
     */
    static <T> HPattern<T> surface(HPattern<T> surface, HPattern<T> medium, double surfaceRadius, double mediumRadius,
                                   int surfaceGap, int mediumGap, List<Integer> facings, boolean requireAll) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                List<Integer> fs = facings.isEmpty() ? List.of(4) : facings;
                for (int f : fs) {
                    boolean ok = facing(p, x, y, z, rot, f);
                    if (requireAll && !ok) return false;
                    if (!requireAll && ok) return true;
                }
                return requireAll;
            }

            private boolean facing(Probe<T> p, int x, int y, int z, int rot, int f) {
                // direction the surface lies in, relative to the medium
                int dx = 0, dy = 0, dz = 0;
                switch (f) {
                    case 0 -> dz = 1;
                    case 1 -> dz = -1;
                    case 2 -> dx = -1;
                    case 3 -> dx = 1;
                    case 4 -> dy = -1;
                    default -> dy = 1;
                }
                if (f < 4) { int[] v = turn(dx, dz, rot); dx = v[0]; dz = v[1]; }
                boolean mediumOk = false;
                for (int g = 0; g <= mediumGap && !mediumOk; g++) mediumOk = disc(p, medium, x - dx * g, y - dy * g, z - dz * g, dx, dy, dz, mediumRadius);
                if (!mediumOk) return false;
                for (int g = 1; g <= 1 + surfaceGap; g++) if (disc(p, surface, x + dx * g, y + dy * g, z + dz * g, dx, dy, dz, surfaceRadius)) return true;
                return false;
            }

            private boolean disc(Probe<T> p, HPattern<T> pat, int cx, int cy, int cz, int nx, int ny, int nz, double radius) {
                int r = (int) Math.floor(radius);
                for (int a = -r; a <= r; a++) {
                    for (int b = -r; b <= r; b++) {
                        if (a * a + b * b > radius * radius + 1e-6) continue;
                        int x = cx, y = cy, z = cz;
                        if (ny != 0) { x += a; z += b; } else if (nx != 0) { y += a; z += b; } else { x += a; y += b; }
                        if (!pat.test(p, x, y, z, 0)) return false;
                    }
                }
                return true;
            }

            public int reach() { return (int) Math.ceil(Math.max(surfaceRadius, mediumRadius)) + Math.max(surfaceGap, mediumGap) + 1; }
        };
    }

    /** Every position of the inclusive box [min, max] (relative to the origin, turned by the rotation) must validate. */
    static <T> HPattern<T> cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, HPattern<T> sub) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                for (int dx = minX; dx <= maxX; dx++)
                    for (int dz = minZ; dz <= maxZ; dz++) {
                        int[] v = turn(dx, dz, rot);
                        for (int dy = minY; dy <= maxY; dy++) if (!sub.test(p, x + v[0], y + dy, z + v[1], rot)) return false;
                    }
                return true;
            }
            public int reach() { return Math.max(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(minZ), Math.abs(maxZ))) + sub.reach(); }
        };
    }

    static <T> HPattern<T> and(List<HPattern<T>> patterns) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                for (HPattern<T> q : patterns) if (!q.test(p, x, y, z, rot)) return false;
                return true;
            }
            public int reach() { int r = 0; for (HPattern<T> q : patterns) r = Math.max(r, q.reach()); return r; }
        };
    }

    static <T> HPattern<T> or(List<HPattern<T>> patterns) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                for (HPattern<T> q : patterns) if (q.test(p, x, y, z, rot)) return true;
                return false;
            }
            public int reach() { int r = 0; for (HPattern<T> q : patterns) r = Math.max(r, q.reach()); return r; }
        };
    }

    static <T> HPattern<T> not(HPattern<T> child) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) { return !child.test(p, x, y, z, rot); }
            public int reach() { return child.reach(); }
        };
    }

    /** Valid where the density at the position falls in any delimiter: min inclusive, max exclusive. */
    static <T> HPattern<T> fieldFunction(DensityFunction field, double[][] delimiters) {
        return new HPattern<>() {
            public boolean test(Probe<T> p, int x, int y, int z, int rot) {
                double v = field.compute(x, y, z);
                for (double[] d : delimiters) if (v >= d[0] && v < d[1]) return true;
                return false;
            }
        };
    }
}
