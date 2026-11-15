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

        /** The tile if it is ready or can be computed right away, otherwise something to {@link #await} later. */
        @SuppressWarnings("unchecked")
        default Object tileOrPending(int chunkX, int chunkZ) { return tile(chunkX, chunkZ); }

        @SuppressWarnings("unchecked")
        default ImmutableChunkSnapshot<T> await(Object pending) { return (ImmutableChunkSnapshot<T>) pending; }
    }

    /** content: how far its blocks reach from an anchor; extra: how much farther it reads (scans and tests). */
    private record Item<T>(Prop<T> wrapper, Distribution<T> dist, int stage, long salt, int content, int extra) {
        /** How far, horizontally, a prop of this distribution reads or writes from its anchor. */
        int total() { return content + extra; }
    }

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
        // reach. A prop reaches (reads and writes) up to its horizontal reach R from its anchor. The blocks later stages read lie within
        // `after` of the chunk, so anchors within after + R matter, and those anchors read up to R further: each stage adds 2 R.
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
            // Compute every missing tile nobody else is working on, then wait for the ones other threads are still computing.
            List<Object[]> pending = null;
            for (int cx = chunkX - rc; cx <= chunkX + rc; cx++) {
                for (int cz = chunkZ - rc; cz <= chunkZ + rc; cz++) {
                    Object o = tiles.tileOrPending(cx, cz);
                    if (o instanceof ImmutableChunkSnapshot<?> s) ctx.loadTile(s, cx, cz);
                    else { if (pending == null) pending = new ArrayList<>(); pending.add(new Object[]{o, cx, cz}); }
                }
            }
            if (pending != null) for (Object[] p : pending) ctx.loadTile(tiles.await(p[0]), (Integer) p[1], (Integer) p[2]);

            for (int si = 0; si < ns; si++) {
                int stage = pl.stages[si];
                int need = after[si];
                // Anchor results are shared only for the first stage: it reads nothing but terrain, which is the same in every chunk's
                // context. A later stage reads what earlier stages built there, and which distributions a chunk considers present (biomes
                // are sampled every 4 blocks) can differ from chunk to chunk, so those results stay per chunk. Per-cell positions are
                // shared in every stage.
                Memo memo = new Memo(si == 0 && !NO_ANCHORS, (chunkX - rc) << 4, (chunkZ - rc) << 4, ((chunkX - rc) << 4) + side * 16, ((chunkZ - rc) << 4) + side * 16);
                ContextSpace<T>.View view = ctx.view(footMinX - need, footMinZ - need, footMinX + 16 + need, footMinZ + 16 + need);
                for (Item<T> it : active[si]) {
                    int m = after[si] + it.content();
                    int minX = footMinX - m, maxX = footMinX + 16 + m;
                    int minZ = footMinZ - m, maxZ = footMinZ + 16 + m;
                    long seedBase = ws.seed() * 0x9E3779B97F4A7C15L ^ it.salt() * 0xC2B2AE3D27D4EB4FL ^ stage;
                    it.dist().generateInRegion(minX, minZ, maxX, maxZ, footMinX - need, footMinZ - need, footMinX + 16 + need, footMinZ + 16 + need, view, seedBase,
                        (x, z) -> contains(biomeProps(biomeCache, x, z), it.wrapper()), solid, NO_MEMO ? null : memo);
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
    /** Biome props at recent sample points, shared by every chunk: the sample grid is global, so neighbouring chunks ask for the same points. */
    private record BiomeSlot<T>(long key, List<Prop<T>> props) {}
    private final java.util.concurrent.atomic.AtomicReferenceArray<BiomeSlot<T>> biomeSlots = new java.util.concurrent.atomic.AtomicReferenceArray<>(1 << 16);

    private List<Prop<T>> biomeProps(Map<Long, List<Prop<T>>> cache, int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        int slot = (int) (((k ^ (k >>> 31)) * 0x9E3779B97F4A7C15L) >>> 48);
        BiomeSlot<T> s = biomeSlots.get(slot);
        if (s != null && s.key == k) return s.props;
        List<Prop<T>> l = ws.propsAt(x, z, fallbackProps);
        biomeSlots.set(slot, new BiomeSlot<>(k, l));
        return l;
    }

    private static <T> boolean contains(List<Prop<T>> list, Prop<T> p) {
        for (Prop<T> q : list) if (q == p) return true;
        return false;
    }

    // ================================================================== remembered anchors

    private record AnchorKey(long seedBase, long pos, int prop) {}

    /** Per-cell values (a distribution's positions in a 16 x 16 cell), shared by all chunks; two generations like the anchors. */
    private record CellKey(Identity owner, long seedBase, int cellX, int cellZ) {}

    /** Wraps an object so that equality is identity (records compare their parts with equals). */
    private record Identity(Object o) {
        @Override public boolean equals(Object other) { return other instanceof Identity i && i.o == o; }
        @Override public int hashCode() { return System.identityHashCode(o); }
    }
    private static final int CELL_BUDGET = 400_000;
    private volatile java.util.concurrent.ConcurrentHashMap<CellKey, Object> cellsFresh = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile java.util.concurrent.ConcurrentHashMap<CellKey, Object> cellsAged = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger cellCount = new java.util.concurrent.atomic.AtomicInteger();

    /** Dev switch for A/B checks: WG2_NO_MEMO=1 turns the anchor memo off. */
    private static final boolean NO_MEMO = System.getenv("WG2_NO_MEMO") != null;
    /** Dev switch: WG2_NO_ANCHORS=1 keeps per-cell positions but stops remembering anchors. */
    private static final boolean NO_ANCHORS = System.getenv("WG2_NO_ANCHORS") != null;

    /** What one prop built at one anchor: the blocks in order, and any loot tables. */
    private static final class WriteSet {
        final Object prop;
        final int[] xyz;
        final Object[] blocks;
        final Object[] loot;      // {x, y, z, table, seed} per entry
        final long bytes;
        int readMinX = Integer.MAX_VALUE, readMinZ = Integer.MAX_VALUE, readMaxX = Integer.MIN_VALUE, readMaxZ = Integer.MIN_VALUE;

        WriteSet(Object prop, int[] xyz, Object[] blocks, Object[] loot) {
            this.prop = prop;
            this.xyz = xyz;
            this.blocks = blocks;
            this.loot = loot;
            this.bytes = 96 + 4L * xyz.length + 4L * blocks.length + 32L * loot.length;
        }
    }

    /** Two generations: new results go into {@code fresh}; when it is full it becomes {@code aged} and the old aged one is dropped. */
    private static final long ANCHOR_BUDGET = 96L * 1024 * 1024;
    private volatile java.util.concurrent.ConcurrentHashMap<AnchorKey, WriteSet> fresh = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile java.util.concurrent.ConcurrentHashMap<AnchorKey, WriteSet> aged = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong freshBytes = new java.util.concurrent.atomic.AtomicLong();

    private static long packPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    private WriteSet lookup(AnchorKey k) {
        WriteSet w = fresh.get(k);
        if (w != null) return w;
        w = aged.get(k);
        if (w != null) store(k, w);
        return w;
    }

    private void store(AnchorKey k, WriteSet w) {
        if (fresh.put(k, w) == null && freshBytes.addAndGet(w.bytes) > ANCHOR_BUDGET / 2) {
            synchronized (this) {
                if (freshBytes.get() > ANCHOR_BUDGET / 2) {
                    aged = fresh;
                    fresh = new java.util.concurrent.ConcurrentHashMap<>();
                    freshBytes.set(0);
                }
            }
        }
    }

    /** The memo one chunk's prop pass uses: results are shared by all chunks; a result is kept only if its reads stayed in this context. */
    private final class Memo implements com.worldgenv2.props.AnchorMemo<T> {
        final boolean anchors;
        final int x0, z0, x1, z1;

        Memo(boolean anchors, int x0, int z0, int x1, int z1) { this.anchors = anchors; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; }

        @Override public boolean remembers() { return anchors; }

        @Override @SuppressWarnings("unchecked")
        public boolean replay(long seedBase, int x, int y, int z, Object prop, VoxelSpace<T> space) {
            WriteSet w = lookup(new AnchorKey(seedBase, packPos(x, y, z), System.identityHashCode(prop)));
            if (w == null || w.prop != prop) return false;
            // Replay only if every block the prop read lies inside this context: then this chunk would have computed the same.
            if (w.readMinX < x0 || w.readMaxX >= x1 || w.readMinZ < z0 || w.readMaxZ >= z1) return false;
            int[] c = w.xyz;
            for (int i = 0, n = w.blocks.length; i < n; i++) space.setBlock(c[3 * i], c[3 * i + 1], c[3 * i + 2], (T) w.blocks[i]);
            for (int i = 0; i < w.loot.length; i += 5)
                space.setBlockLoot((Integer) w.loot[i], (Integer) w.loot[i + 1], (Integer) w.loot[i + 2], (String) w.loot[i + 3], (Long) w.loot[i + 4]);
            return true;
        }

        @Override
        public com.worldgenv2.props.AnchorMemo.Recorder<T> recorder(VoxelSpace<T> space) { return new Rec(space); }

        @Override
        public void verify(long seedBase, int x, int y, int z, Object prop, VoxelSpace<T> space, java.util.function.Consumer<VoxelSpace<T>> generate) {
            if (!anchors) return;
            WriteSet w = lookup(new AnchorKey(seedBase, packPos(x, y, z), System.identityHashCode(prop)));
            if (w == null || w.prop != prop) return;
            VoxelSpace<T> readOnly = new VoxelSpace<>() {
                public T getBlock(int a, int b, int c) { return space.getBlock(a, b, c); }
                public void setBlock(int a, int b, int c, T block) { }
                public boolean isAir(int a, int b, int c) { return space.isAir(a, b, c); }
                public int getMinY() { return space.getMinY(); }
                public int getMaxY() { return space.getMaxY(); }
            };
            Rec r = new Rec(readOnly);
            r.ax = x;
            r.az = z;
            generate.accept(r);
            boolean same = r.n == w.blocks.length;
            int firstDiff = -1;
            for (int i = 0; same && i < r.n; i++) {
                if (r.xyz[3 * i] != w.xyz[3 * i] || r.xyz[3 * i + 1] != w.xyz[3 * i + 1] || r.xyz[3 * i + 2] != w.xyz[3 * i + 2] || r.blocks.get(i) != w.blocks[i]) { same = false; firstDiff = i; }
            }
            if (same) return;
            synchronized (PropContextPass.class) {
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "wg2_memo_diff.txt"),
                        "anchor " + x + "," + y + "," + z + " prop " + prop.getClass().getName() + " remembered " + w.blocks.length + " writes, now " + r.n
                            + " (reads outside now: " + r.outside + ") first difference at write " + firstDiff
                            + (firstDiff >= 0 ? " remembered " + w.xyz[3 * firstDiff] + "," + w.xyz[3 * firstDiff + 1] + "," + w.xyz[3 * firstDiff + 2] + "=" + w.blocks[firstDiff]
                                + " now " + r.xyz[3 * firstDiff] + "," + r.xyz[3 * firstDiff + 1] + "," + r.xyz[3 * firstDiff + 2] + "=" + r.blocks.get(firstDiff) : "")
                            + " | context " + x0 + ".." + x1 + " x " + z0 + ".." + z1
                            + " | declared reach " + ((com.worldgenv2.props.Prop<?>) prop).horizontalReach() + " content " + ((com.worldgenv2.props.Prop<?>) prop).contentReach()
                            + " | actually read up to " + r.maxRead + ", wrote up to " + r.maxWrite + " | " + describe(prop) + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (Exception ignored) { }
            }
        }

        @Override
        public Object cell(Object owner, long seedBase, int cellX, int cellZ, java.util.function.Supplier<Object> compute) {
            CellKey k = new CellKey(new Identity(owner), seedBase, cellX, cellZ);
            Object v = cellsFresh.get(k);
            if (v == null) {
                v = cellsAged.get(k);
                if (v == null) v = compute.get();
                if (cellsFresh.put(k, v) == null && cellCount.incrementAndGet() > CELL_BUDGET / 2) {
                    synchronized (PropContextPass.this) {
                        if (cellCount.get() > CELL_BUDGET / 2) {
                            cellsAged = cellsFresh;
                            cellsFresh = new java.util.concurrent.ConcurrentHashMap<>();
                            cellCount.set(0);
                        }
                    }
                }
            }
            return v;
        }

        @Override
        public void remember(long seedBase, int x, int y, int z, Object prop, com.worldgenv2.props.AnchorMemo.Recorder<T> recorder) {
            Rec r = (Rec) recorder;
            if (r.outside) return;
            WriteSet w = new WriteSet(prop, java.util.Arrays.copyOf(r.xyz, r.n * 3), r.blocks.toArray(), r.loot.toArray());
            w.readMinX = r.rMinX; w.readMaxX = r.rMaxX; w.readMinZ = r.rMinZ; w.readMaxZ = r.rMaxZ;
            store(new AnchorKey(seedBase, packPos(x, y, z), System.identityHashCode(prop)), w);
        }

        /** Passes everything through and notes the writes; a read outside this context makes the result one not to keep. */
        final class Rec implements com.worldgenv2.props.AnchorMemo.Recorder<T> {
            final VoxelSpace<T> base;
            boolean outside;
            int[] xyz = new int[48];
            int n;
            final ArrayList<Object> blocks = new ArrayList<>();
            final ArrayList<Object> loot = new ArrayList<>();

            int ax = Integer.MIN_VALUE, az, maxRead, maxWrite;   // dev check only: distances from the anchor

            Rec(VoxelSpace<T> base) { this.base = base; }

            int rMinX = Integer.MAX_VALUE, rMinZ = Integer.MAX_VALUE, rMaxX = Integer.MIN_VALUE, rMaxZ = Integer.MIN_VALUE;

            private void read(int x, int z) {
                if (x < x0 || x >= x1 || z < z0 || z >= z1) outside = true;
                if (x < rMinX) rMinX = x;
                if (x > rMaxX) rMaxX = x;
                if (z < rMinZ) rMinZ = z;
                if (z > rMaxZ) rMaxZ = z;
                if (ax != Integer.MIN_VALUE) maxRead = Math.max(maxRead, Math.max(Math.abs(x - ax), Math.abs(z - az)));
            }

            @Override public T getBlock(int x, int y, int z) { read(x, z); return base.getBlock(x, y, z); }

            @Override public boolean isAir(int x, int y, int z) { read(x, z); return base.isAir(x, y, z); }

            @Override
            public void setBlock(int x, int y, int z, T block) {
                if (block != null && ax != Integer.MIN_VALUE) maxWrite = Math.max(maxWrite, Math.max(Math.abs(x - ax), Math.abs(z - az)));
                if (block != null) {
                    if (3 * n + 3 > xyz.length) xyz = java.util.Arrays.copyOf(xyz, xyz.length * 2);
                    xyz[3 * n] = x; xyz[3 * n + 1] = y; xyz[3 * n + 2] = z;
                    n++;
                    blocks.add(block);
                }
                base.setBlock(x, y, z, block);
            }

            @Override
            public void setBlockLoot(int x, int y, int z, String lootTableId, long seed) {
                loot.add(x); loot.add(y); loot.add(z); loot.add(lootTableId); loot.add(seed);
                base.setBlockLoot(x, y, z, lootTableId, seed);
            }

            @Override public int getMinY() { return base.getMinY(); }

            @Override public int getMaxY() { return base.getMaxY(); }
        }
    }

    /** Dev check: a prop and its children with their declared reaches (horizontal/content). */
    private static String describe(Object p) {
        StringBuilder sb = new StringBuilder(p.getClass().getSimpleName());
        if (p instanceof com.worldgenv2.props.Prop<?> pr) sb.append('(').append(pr.horizontalReach()).append('/').append(pr.contentReach()).append(')');
        try {
            for (var fld : p.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(fld.getModifiers())) continue;
                fld.setAccessible(true);
                Object v = fld.get(p);
                if (v instanceof com.worldgenv2.props.Prop<?> c) sb.append(' ').append(fld.getName()).append("={").append(describe(c)).append('}');
                else if (v instanceof java.util.List<?> l && !l.isEmpty() && l.get(0) instanceof com.worldgenv2.props.Prop<?>) {
                    sb.append(' ').append(fld.getName()).append("=[");
                    for (Object c : l) sb.append(describe(c)).append("; ");
                    sb.append(']');
                } else if (v != null && !(v instanceof Number) && !(v instanceof Boolean)) {
                    String name = fld.getName().toLowerCase(java.util.Locale.ROOT);
                    if (name.contains("scanner") || name.contains("pattern")) sb.append(' ').append(fld.getName()).append('=').append(v.getClass().getSimpleName());
                }
            }
        } catch (Exception ignored) { }
        return sb.toString();
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
        /** Reads of a column that is still plain terrain: a column read often is copied into an array, one read only a few times is not. */
        private final byte[] reads;
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
            this.reads = new byte[w * d];
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

        /** A view whose writes outside [needMinX, needMaxX) x [needMinZ, needMaxZ) are dropped: nothing reads them later. */
        View view(int needMinX, int needMinZ, int needMaxX, int needMaxZ) { return new View(needMinX, needMinZ, needMaxX, needMaxZ); }

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
            private final int needMinX, needMinZ, needMaxX, needMaxZ;

            View(int needMinX, int needMinZ, int needMaxX, int needMaxZ) {
                this.needMinX = needMinX;
                this.needMinZ = needMinZ;
                this.needMaxX = needMaxX;
                this.needMaxZ = needMaxZ;
            }

            @Override @SuppressWarnings("unchecked")
            public T getBlock(int x, int y, int z) {
                int col = column(x, z), ly = y - minY;
                if (col < 0 || ly < 0 || ly >= h) return null;
                Object[] a = cur[col];
                if (a == null) {
                    if (reads[col] < 6) { reads[col]++; return (T) terrain(x, y, z); }
                    a = materialize(col, x, z);
                }
                return (T) a[ly];
            }

            @Override
            public void setBlock(int x, int y, int z, T block) {
                if (x < needMinX || x >= needMaxX || z < needMinZ || z >= needMaxZ) return;
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
                Object b;
                if (a == null) {
                    if (reads[col] < 6) { reads[col]++; b = terrain(x, y, z); return b == null || b.equals(air); }
                    a = materialize(col, x, z);
                }
                b = a[ly];
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