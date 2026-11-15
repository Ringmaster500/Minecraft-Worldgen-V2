package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.props.Distribution;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.runtime.PropRuntime;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Prop Runtime execution with Hytale's staged context.
 *
 * <p>Props near a chunk edge must see the real terrain on the other side, and must build the same thing whichever of the two
 * chunks is generated first. So a chunk does not place props only from anchors inside itself. Instead:
 * <ol>
 *   <li>Each prop reports how far it reaches (what it scans and tests, and the size of what it builds). The reach of every
 *       <em>stage</em> (runtime index) is the largest of its props. Later stages need the output of earlier ones over a wider
 *       area, so the terrain a chunk needs is its own footprint plus the sum of all stage reaches.</li>
 *   <li>That region of terrain is read from deterministic per-chunk terrain tiles (neighbours are computed on demand, never
 *       read from whatever happens to have been generated).</li>
 *   <li>Stage by stage, every anchor whose prop can reach into this chunk is evaluated, in a fixed global order, with seeds that
 *       depend only on the anchor. A stage reads the previous stage's result and writes its own, so props in one stage never
 *       depend on each other's order.</li>
 *   <li>Only the cells inside this chunk are copied back. The neighbour does the same from its side and gets the same answer.</li>
 * </ol>
 * This is the "context size" model of Hytale's Prop Runtime. Context precision is currently always the safe {@code Biome}
 * behaviour (the maximum reach over every biome), which is what guarantees seam-free results.
 */
final class PropContextPass<T> {

    /** Source of deterministic terrain for any chunk. */
    interface TileSource<T> {
        ImmutableChunkSnapshot<T> tile(int chunkX, int chunkZ);
    }

    /** content: how far its blocks reach from an anchor; extra: how much farther it reads (scans and tests). */
    private record Item<T>(Prop<T> wrapper, Distribution<T> dist, int stage, long salt, int content, int extra) {}

    private static final class Plan<T> {
        final List<Item<T>> items = new ArrayList<>();
        final Map<Integer, int[]> stageReach = new TreeMap<>();       // stage -> {content W, search P}
        final IdentityHashMap<Prop<T>, Item<T>> byProp = new IdentityHashMap<>();
        int totalMargin;
        int[] stages = new int[0];
    }

    private static final int MAX_STAGE_REACH = 64;

    private final WorldStructure ws;
    private final List<Prop<T>> fallbackProps;
    private final T air;
    private volatile Plan<T> plan;

    PropContextPass(WorldStructure ws, List<Prop<T>> fallbackProps, T air) {
        this.ws = ws;
        this.fallbackProps = fallbackProps;
        this.air = air;
    }

    /** True for props handled by this pass (distributions); everything else stays per surface column. */
    static boolean isDistribution(Prop<?> p) {
        return PropRuntime.unwrap(p) instanceof Distribution<?>;
    }

    @SuppressWarnings("unchecked")
    private Plan<T> plan() {
        Plan<T> p = plan;
        if (p != null) return p;
        synchronized (this) {
            if (plan != null) return plan;
            Plan<T> np = new Plan<>();
            for (WorldStructure.BiomeProp<T> bp : ws.<T>allBiomeProps()) {
                if (!isDistribution(bp.prop()) || np.byProp.containsKey(bp.prop())) continue;
                Distribution<T> ap = (Distribution<T>) PropRuntime.unwrap(bp.prop());
                int total = Math.min(MAX_STAGE_REACH, bp.prop().horizontalReach());
                int content = Math.min(total, bp.prop().contentReach());
                Item<T> it = new Item<>(bp.prop(), ap, PropRuntime.stageOf(bp.prop()), bp.salt(), content, Math.max(0, total - content));
                np.items.add(it);
                np.byProp.put(bp.prop(), it);
                int[] r = np.stageReach.computeIfAbsent(it.stage(), k -> new int[2]);
                r[0] = Math.max(r[0], content);
                r[1] = Math.max(r[1], Math.max(0, total - content));
            }
            np.items.sort(Comparator.comparingInt((Item<T> i) -> i.stage()).thenComparingLong(Item::salt));
            np.stages = np.stageReach.keySet().stream().mapToInt(Integer::intValue).toArray();
            int sum = 0;
            for (int[] r : np.stageReach.values()) sum += r[0] + r[1];
            np.totalMargin = sum;
            plan = np;
            return np;
        }
    }

    /** The most terrain tiles one chunk's props can read (the worst case over every biome; most chunks need far fewer). */
    int tilesPerChunk() { int side = 2 * ((plan().totalMargin + 15) >> 4) + 1; return side * side; }

    void run(int chunkX, int chunkZ, VoxelSpace<T> real, Predicate<T> solid, TileSource<T> tiles) {
        Plan<T> pl = plan();
        if (pl.items.isEmpty()) return;
        int minY = real.getMinY(), maxY = real.getMaxY();
        int footMinX = chunkX << 4, footMinZ = chunkZ << 4;
        Map<Long, List<Prop<T>>> biomeCache = new HashMap<>();

        // Work out, from the last stage backwards, which distributions can touch this chunk at all and how far the context must
        // reach: a distribution only matters if its biome lies within (later stages' reach + its own build size) of the chunk,
        // and the context only needs the largest reach of the distributions that do.
        int ns = pl.stages.length;
        int[] after = new int[ns];
        @SuppressWarnings("unchecked") List<Item<T>>[] active = new List[ns];
        int run = 0;
        boolean any = false;
        for (int si = ns - 1; si >= 0; si--) {
            after[si] = run;
            int stage = pl.stages[si];
            int r0 = 0, r1 = 0;
            List<Item<T>> act = new ArrayList<>();
            for (Item<T> it : pl.items) {
                if (it.stage() != stage) continue;
                int m = after[si] + it.content();
                if (!biomeInRect(biomeCache, it.wrapper(), footMinX - m, footMinZ - m, footMinX + 16 + m, footMinZ + 16 + m)) continue;
                act.add(it);
                r0 = Math.max(r0, it.content());
                r1 = Math.max(r1, it.extra());
            }
            active[si] = act;
            if (!act.isEmpty()) any = true;
            run += r0 + r1;
        }
        if (!any) return;

        int rc = (run + 15) >> 4;
        int side = 2 * rc + 1;
        ContextSpace<T> ctx = ContextSpace.acquire((chunkX - rc) << 4, (chunkZ - rc) << 4, side * 16, side * 16, minY, maxY, air);
        try {
            for (int cx = chunkX - rc; cx <= chunkX + rc; cx++)
                for (int cz = chunkZ - rc; cz <= chunkZ + rc; cz++) ctx.loadTile(tiles.tile(cx, cz), cx, cz);

            for (int si = 0; si < ns; si++) {
                int stage = pl.stages[si];
                ContextSpace<T>.View view = ctx.view();
                for (Item<T> it : active[si]) {
                    int m = after[si] + it.content();
                    int minX = footMinX - m, maxX = footMinX + 16 + m;
                    int minZ = footMinZ - m, maxZ = footMinZ + 16 + m;
                    long seedBase = ws.seed() * 0x9E3779B97F4A7C15L ^ it.salt() * 0xC2B2AE3D27D4EB4FL ^ stage;
                    it.dist().generateInRegion(minX, minZ, maxX, maxZ, view, seedBase,
                        (x, z) -> contains(biomeProps(biomeCache, x, z), it.wrapper()), solid);
                }
                ctx.commitStage();
            }
            ctx.copyFootprintTo(real, chunkX, chunkZ);
        } finally {
            ctx.release();
        }
    }

    /** True when some column of the rectangle (sampled every 4 blocks on a fixed grid) belongs to a biome that has this prop. */
    private boolean biomeInRect(Map<Long, List<Prop<T>>> cache, Prop<T> wrapper, int minX, int minZ, int maxX, int maxZ) {
        for (int x = Math.floorDiv(minX, 4) * 4; x < maxX; x += 4)
            for (int z = Math.floorDiv(minZ, 4) * 4; z < maxZ; z += 4)
                if (contains(biomeProps(cache, x, z), wrapper)) return true;
        return false;
    }
    private List<Prop<T>> biomeProps(Map<Long, List<Prop<T>>> cache, int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        List<Prop<T>> l = cache.get(k);
        if (l == null) {
            l = ws.propsAt(x, z, fallbackProps);
            cache.put(k, l);
        }
        return l;
    }

    private static <T> boolean contains(List<Prop<T>> list, Prop<T> p) {
        for (Prop<T> q : list) if (q == p) return true;
        return false;
    }

    // ================================================================== the region

    /**
     * A writable region of blocks (terrain from tiles) with stage isolation: a {@link View} reads the result of the previous
     * stage and records its writes, which {@link #commitStage()} then applies.
     */
    static final class ContextSpace<T> {
        private final int x0, z0, w, d, minY, maxY, h;
        private final Object air;
        private final int cx0, cz0, side;
        private final ImmutableChunkSnapshot<?>[] tileGrid;
        /** Per column (x, z) of the region: the blocks after the committed stages, or null while the column is still plain terrain. */
        private final Object[][] cur;
        /** Per column: what the running stage wrote. */
        private final Object[][] pend;
        /** Per column: which heights any stage has written. */
        private final long[][] written;
        private int[] touched = new int[1024];
        private int touchedCount;
        private final List<Object[]> loot = new ArrayList<>();

        private ContextSpace(int x0, int z0, int w, int d, int minY, int maxY, Object air) {
            this.air = air;
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.d = d;
            this.minY = minY;
            this.maxY = maxY;
            this.h = maxY - minY;
            this.cx0 = x0 >> 4;
            this.cz0 = z0 >> 4;
            this.side = w >> 4;
            this.tileGrid = new ImmutableChunkSnapshot<?>[side * side];
            this.cur = new Object[w * d][];
            this.pend = new Object[w * d][];
            this.written = new long[w * d][];
        }

        static <T> ContextSpace<T> acquire(int x0, int z0, int w, int d, int minY, int maxY, Object air) {
            return new ContextSpace<>(x0, z0, w, d, minY, maxY, air);
        }

        void release() { }

        private int column(int x, int z) {
            int lx = x - x0, lz = z - z0;
            if (lx < 0 || lz < 0 || lx >= w || lz >= d) return -1;
            return lx * d + lz;
        }

        /** Terrain of one chunk; the region reads through to it until a prop writes the column. */
        void loadTile(ImmutableChunkSnapshot<?> tile, int cx, int cz) {
            int gx = cx - cx0, gz = cz - cz0;
            if (tile != null && gx >= 0 && gz >= 0 && gx < side && gz < side) tileGrid[gx * side + gz] = tile;
        }

        private Object terrain(int x, int y, int z) {
            ImmutableChunkSnapshot<?> t = tileGrid[((x >> 4) - cx0) * side + ((z >> 4) - cz0)];
            return t == null ? null : t.getRaw(x, y, z);
        }

        private Object[] materialize(int col, int x, int z) {
            Object[] a = cur[col];
            if (a == null) {
                a = new Object[h];
                ImmutableChunkSnapshot<?> t = tileGrid[((x >> 4) - cx0) * side + ((z >> 4) - cz0)];
                if (t != null) t.copyColumn(x & 15, z & 15, a, 0, h);
                cur[col] = a;
            }
            return a;
        }

        View view() { return new View(); }

        void commitStage() {
            for (int i = 0; i < touchedCount; i++) {
                int idx = touched[i];
                int col = idx / h, ly = idx % h;
                Object[] a = materialize(col, x0 + col / d, z0 + col % d);
                a[ly] = pend[col][ly];
                pend[col][ly] = null;
                long[] wr = written[col];
                if (wr == null) wr = written[col] = new long[(h + 63) >> 6];
                wr[ly >> 6] |= 1L << (ly & 63);
            }
            touchedCount = 0;
        }

        /** Copies every cell a prop wrote inside the chunk's own footprint into the real chunk. */
        @SuppressWarnings("unchecked")
        void copyFootprintTo(VoxelSpace<T> real, int chunkX, int chunkZ) {
            int fx = chunkX << 4, fz = chunkZ << 4;
            for (int x = fx; x < fx + 16; x++) {
                for (int z = fz; z < fz + 16; z++) {
                    int col = column(x, z);
                    long[] wr = col < 0 ? null : written[col];
                    if (wr == null) continue;
                    Object[] a = cur[col];
                    for (int wi = 0; wi < wr.length; wi++) {
                        for (long bits = wr[wi]; bits != 0; bits &= bits - 1) {
                            int ly = (wi << 6) + Long.numberOfTrailingZeros(bits);
                            real.setBlock(x, minY + ly, z, (T) a[ly]);
                        }
                    }
                }
            }
            for (Object[] l : loot) {
                int x = (Integer) l[0], y = (Integer) l[1], z = (Integer) l[2];
                if (x >= fx && x < fx + 16 && z >= fz && z < fz + 16) real.setBlockLoot(x, y, z, (String) l[3], (Long) l[4]);
            }
        }

        /** What props see: the previous stage's blocks to read, a private buffer to write. */
        final class View implements VoxelSpace<T> {
            @Override @SuppressWarnings("unchecked")
            public T getBlock(int x, int y, int z) {
                int col = column(x, z), ly = y - minY;
                if (col < 0 || ly < 0 || ly >= h) return null;
                Object[] a = cur[col];
                return (T) (a != null ? a[ly] : terrain(x, y, z));
            }

            @Override
            public void setBlock(int x, int y, int z, T block) {
                int col = column(x, z), ly = y - minY;
                if (col < 0 || ly < 0 || ly >= h || block == null) return;
                Object[] p = pend[col];
                if (p == null) p = pend[col] = new Object[h];
                if (p[ly] == null) {
                    if (touchedCount == touched.length) touched = Arrays.copyOf(touched, touched.length * 2);
                    touched[touchedCount++] = col * h + ly;
                }
                p[ly] = block;
            }

            @Override
            public boolean isAir(int x, int y, int z) {
                int col = column(x, z), ly = y - minY;
                if (col < 0 || ly < 0 || ly >= h) return true;
                Object[] a = cur[col];
                Object b = a != null ? a[ly] : terrain(x, y, z);
                return b == null || b.equals(air);
            }

            @Override public int getMinY() { return minY; }

            @Override public int getMaxY() { return maxY; }

            @Override
            public void setBlockLoot(int x, int y, int z, String lootTableId, long seed) {
                if (column(x, z) >= 0) loot.add(new Object[]{x, y, z, lootTableId, seed});
            }
        }
    }
}