package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.props.Distribution;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.runtime.PropRuntime;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.ArrayList;
import java.util.BitSet;
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

    private record Item<T>(Prop<T> wrapper, Distribution<T> dist, int stage, long salt) {}

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
                Item<T> it = new Item<>(bp.prop(), ap, PropRuntime.stageOf(bp.prop()), bp.salt());
                np.items.add(it);
                np.byProp.put(bp.prop(), it);
                int total = Math.min(MAX_STAGE_REACH, bp.prop().horizontalReach());
                int content = Math.min(total, bp.prop().contentReach());
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

    void run(int chunkX, int chunkZ, VoxelSpace<T> real, Predicate<T> solid, TileSource<T> tiles) {
        Plan<T> pl = plan();
        if (pl.items.isEmpty()) return;
        int minY = real.getMinY(), maxY = real.getMaxY();
        int rc = (pl.totalMargin + 15) >> 4;
        int side = 2 * rc + 1;
        ContextSpace<T> ctx = ContextSpace.acquire((chunkX - rc) << 4, (chunkZ - rc) << 4, side * 16, side * 16, minY, maxY, air);
        try {
            for (int cx = chunkX - rc; cx <= chunkX + rc; cx++)
                for (int cz = chunkZ - rc; cz <= chunkZ + rc; cz++) ctx.loadTile(tiles.tile(cx, cz), cx, cz);

            int[] after = new int[pl.stages.length];            // reach of all stages after index i
            int run = 0;
            for (int i = pl.stages.length - 1; i >= 0; i--) {
                after[i] = run;
                int[] r = pl.stageReach.get(pl.stages[i]);
                run += r[0] + r[1];
            }

            Map<Long, List<Prop<T>>> biomeCache = new HashMap<>();
            int footMinX = chunkX << 4, footMinZ = chunkZ << 4;
            for (int si = 0; si < pl.stages.length; si++) {
                int stage = pl.stages[si];
                int w = pl.stageReach.get(stage)[0];
                int anchorM = after[si] + w;
                int minX = footMinX - anchorM, maxX = footMinX + 16 + anchorM;
                int minZ = footMinZ - anchorM, maxZ = footMinZ + 16 + anchorM;

                // which of this stage's props have any column of their biome inside the anchor region
                IdentityHashMap<Prop<T>, Boolean> present = new IdentityHashMap<>();
                for (int x = minX; x < maxX; x += 4) {
                    for (int z = minZ; z < maxZ; z += 4) {
                        for (Prop<T> p : biomeProps(biomeCache, x, z)) {
                            Item<T> it = pl.byProp.get(p);
                            if (it != null && it.stage() == stage) present.put(p, Boolean.TRUE);
                        }
                    }
                }
                ContextSpace<T>.View view = ctx.view();
                for (Item<T> it : pl.items) {
                    if (it.stage() != stage || !present.containsKey(it.wrapper())) continue;
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

    private List<Prop<T>> biomeProps(Map<Long, List<Prop<T>>> cache, int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        List<Prop<T>> l = cache.get(k);
        if (l == null) {
            l = ws.queryBiome(x, z).resolveProps(fallbackProps);
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
        private static final ThreadLocal<Object[]> POOL_CUR = new ThreadLocal<>();
        private static final ThreadLocal<Object[]> POOL_PEND = new ThreadLocal<>();

        private final int x0, z0, w, d, minY, maxY, h;
        private final Object air;
        private Object[] cur, pend;
        private final BitSet written = new BitSet();
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
        }

        static <T> ContextSpace<T> acquire(int x0, int z0, int w, int d, int minY, int maxY, Object air) {
            ContextSpace<T> s = new ContextSpace<>(x0, z0, w, d, minY, maxY, air);
            int n = w * d * s.h;
            Object[] c = POOL_CUR.get(), p = POOL_PEND.get();
            if (c == null || c.length < n) { c = new Object[n]; POOL_CUR.set(c); }
            if (p == null || p.length < n) { p = new Object[n]; POOL_PEND.set(p); }
            Arrays.fill(c, 0, n, null);
            Arrays.fill(p, 0, n, null);
            s.cur = c;
            s.pend = p;
            return s;
        }

        void release() {
            // arrays stay in the thread-local pool; drop references so a finished chunk does not pin its blocks
            Arrays.fill(cur, 0, w * d * h, null);
            cur = null;
            pend = null;
        }

        private int index(int x, int y, int z) {
            int lx = x - x0, lz = z - z0, ly = y - minY;
            if (lx < 0 || lz < 0 || lx >= w || lz >= d || ly < 0 || ly >= h) return -1;
            return (lx * d + lz) * h + ly;
        }

        void loadTile(ImmutableChunkSnapshot<?> tile, int cx, int cz) {
            if (tile == null) return;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int idx = index((cx << 4) + lx, minY, (cz << 4) + lz);
                    if (idx >= 0) tile.copyColumn(lx, lz, cur, idx, h);
                }
            }
        }

        View view() { return new View(); }

        void commitStage() {
            for (int i = 0; i < touchedCount; i++) {
                int idx = touched[i];
                cur[idx] = pend[idx];
                pend[idx] = null;
                written.set(idx);
            }
            touchedCount = 0;
        }

        /** Copies every cell a prop wrote inside the chunk's own footprint into the real chunk. */
        @SuppressWarnings("unchecked")
        void copyFootprintTo(VoxelSpace<T> real, int chunkX, int chunkZ) {
            int fx = chunkX << 4, fz = chunkZ << 4;
            for (int idx = written.nextSetBit(0); idx >= 0; idx = written.nextSetBit(idx + 1)) {
                int ly = idx % h;
                int col = idx / h;
                int lz = col % d, lx = col / d;
                int x = x0 + lx, z = z0 + lz;
                if (x < fx || x >= fx + 16 || z < fz || z >= fz + 16) continue;
                real.setBlock(x, minY + ly, z, (T) cur[idx]);
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
                int idx = index(x, y, z);
                return idx < 0 ? null : (T) cur[idx];
            }

            @Override
            public void setBlock(int x, int y, int z, T block) {
                int idx = index(x, y, z);
                if (idx < 0 || block == null) return;
                if (pend[idx] == null) {
                    if (touchedCount == touched.length) touched = Arrays.copyOf(touched, touched.length * 2);
                    touched[touchedCount++] = idx;
                }
                pend[idx] = block;
            }

            @Override
            public boolean isAir(int x, int y, int z) {
                int idx = index(x, y, z);
                if (idx < 0) return true;
                Object b = cur[idx];
                return b == null || b.equals(air);
            }

            @Override public int getMinY() { return minY; }

            @Override public int getMaxY() { return maxY; }

            @Override
            public void setBlockLoot(int x, int y, int z, String lootTableId, long seed) {
                if (index(x, y, z) >= 0) loot.add(new Object[]{x, y, z, lootTableId, seed});
            }
        }
    }
}
