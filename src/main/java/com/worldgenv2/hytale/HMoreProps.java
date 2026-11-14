package com.worldgenv2.hytale;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.props.Prop;

import java.util.List;
import java.util.Set;

/** More of Hytale's Props, with the semantics of docs.hytale.com/hytale-generator/prop. */
public final class HMoreProps {
    private HMoreProps() {}

    /** Offsets the child's position by a vector. */
    public static final class Offset<T> implements Prop<T> {
        private final Prop<T> child;
        private final int ox, oy, oz;

        public Offset(Prop<T> child, int ox, int oy, int oz) { this.child = child; this.ox = ox; this.oy = oy; this.oz = oz; }

        @Override
        public boolean generate(Context<T> c) {
            return child.generate(new Context<>(c.worldX() + ox, c.worldY() + oy, c.worldZ() + oz, c.normalX(), c.normalY(), c.normalZ(), c.voxelSpace(), c.seed()));
        }

        @Override public int horizontalReach() { return Math.max(Math.abs(ox), Math.abs(oz)) + child.horizontalReach(); }
    }

    /** One Advanced rule of a BlockMask: blocks in {@code source} may replace blocks in {@code canReplace}. */
    public record MaskRule<T>(Set<T> source, Set<T> canReplace) {}

    /**
     * Masks the blocks placed by the child with a BlockMask: blocks in DontPlace are never placed; by default a placed block may not
     * replace a block in DontReplace, except where an Advanced rule lists the placed block as a Source and the existing block in
     * its CanReplace set.
     */
    public static final class Mask<T> implements Prop<T> {
        private final Prop<T> child;
        private final Set<T> dontPlace, dontReplace;
        private final List<MaskRule<T>> advanced;

        public Mask(Prop<T> child, Set<T> dontPlace, Set<T> dontReplace, List<MaskRule<T>> advanced) {
            this.child = child; this.dontPlace = dontPlace; this.dontReplace = dontReplace; this.advanced = advanced;
        }

        @Override
        public boolean generate(Context<T> c) {
            VoxelSpace<T> base = c.voxelSpace();
            VoxelSpace<T> masked = new VoxelSpace<>() {
                public T getBlock(int x, int y, int z) { return base.getBlock(x, y, z); }
                public boolean isAir(int x, int y, int z) { return base.isAir(x, y, z); }
                public int getMinY() { return base.getMinY(); }
                public int getMaxY() { return base.getMaxY(); }
                public void setBlockLoot(int x, int y, int z, String t, long s) { base.setBlockLoot(x, y, z, t, s); }

                public void setBlock(int x, int y, int z, T block) {
                    if (block == null || dontPlace.contains(block)) return;
                    T existing = base.getBlock(x, y, z);
                    if (existing != null && dontReplace.contains(existing)) {
                        boolean allowed = false;
                        for (MaskRule<T> r : advanced) if (r.source().contains(block) && r.canReplace().contains(existing)) { allowed = true; break; }
                        if (!allowed) return;
                    }
                    base.setBlock(x, y, z, block);
                }
            };
            return child.generate(new Context<>(c.worldX(), c.worldY(), c.worldZ(), c.normalX(), c.normalY(), c.normalZ(), masked, c.seed()));
        }

        @Override public int horizontalReach() { return child.horizontalReach(); }
    }

    /** One delimiter of a DensitySelector: density in [min, max) selects {@code prop}. */
    public record Delimiter<T>(double min, double max, Prop<T> prop) {}

    /** Picks the child by the density at the prop's position: the first delimiter whose range contains the value wins. */
    public static final class DensitySelector<T> implements Prop<T> {
        private final DensityFunction density;
        private final List<Delimiter<T>> delimiters;

        public DensitySelector(DensityFunction density, List<Delimiter<T>> delimiters) { this.density = density; this.delimiters = delimiters; }

        @Override
        public boolean generate(Context<T> c) {
            double v = density.compute(c.worldX(), c.worldY(), c.worldZ());
            for (Delimiter<T> d : delimiters) if (v >= d.min() && v < d.max()) return d.prop().generate(c);
            return false;
        }

        @Override
        public int horizontalReach() { int r = 0; for (Delimiter<T> d : delimiters) r = Math.max(r, d.prop().horizontalReach()); return r; }
    }

    /** Fills a box around the anchor with the blocks a MaterialProvider chooses. */
    public static final class Cuboid<T> implements Prop<T> {
        private final int[] min, max;
        private final MaterialProvider<T> material;

        public Cuboid(int[] min, int[] max, MaterialProvider<T> material) { this.min = min; this.max = max; this.material = material; }

        @Override
        public boolean generate(Context<T> c) {
            boolean any = false;
            for (int dx = min[0]; dx <= max[0]; dx++)
                for (int dz = min[2]; dz <= max[2]; dz++)
                    for (int dy = max[1]; dy >= min[1]; dy--) {
                        int x = c.worldX() + dx, y = c.worldY() + dy, z = c.worldZ() + dz;
                        T b = material.getMaterial(x, y, z, max[1] - dy, 0, 0f, c.voxelSpace());
                        if (b != null) { c.voxelSpace().setBlock(x, y, z, b); any = true; }
                    }
            return any;
        }

        @Override public int horizontalReach() { return Math.max(Math.max(Math.abs(min[0]), Math.abs(max[0])), Math.max(Math.abs(min[2]), Math.abs(max[2]))); }
    }

    /** Hand-placed blocks, each at an offset from the anchor. */
    public record Placed<T>(int dx, int dy, int dz, T block) {}

    public static final class Manual<T> implements Prop<T> {
        private final List<Placed<T>> blocks;

        public Manual(List<Placed<T>> blocks) { this.blocks = blocks; }

        @Override
        public boolean generate(Context<T> c) {
            for (Placed<T> p : blocks) c.voxelSpace().setBlock(c.worldX() + p.dx(), c.worldY() + p.dy(), c.worldZ() + p.dz(), p.block());
            return !blocks.isEmpty();
        }

        @Override public int horizontalReach() { int r = 0; for (Placed<T> p : blocks) r = Math.max(r, Math.max(Math.abs(p.dx()), Math.abs(p.dz()))); return r; }
    }
}
