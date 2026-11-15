package com.worldgenv2.density;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Every compiled copy of one exported density node (an {@code ExportAs} node used inline and again through {@code Imported}) shares
 * recent whole columns per thread. Another user of the node (for example a material that takes the density's gradient) then reads
 * the columns the terrain pass already computed instead of evaluating the node again.
 *
 * <p>The compiler only wraps a node when its copies are certain to be the same function (compiling it did not draw a seed from the
 * compile-order counter). Columns are only kept once some user asks for part of a column, which is what makes keeping them pay.
 */
public final class SharedColumnDensity implements DensityFunction {

    private static final class ColumnCache {
        final float[] xs = new float[512], zs = new float[512];
        final int[] y0 = new int[512], y1 = new int[512];
        final float[][] vals = new float[512][];
        ColumnCache() { java.util.Arrays.fill(xs, Float.NaN); }
    }

    /** What all copies of one exported node share. */
    private static final class Group {
        volatile boolean wanted;
        final ThreadLocal<ColumnCache> cache = ThreadLocal.withInitial(ColumnCache::new);
    }

    /** Keyed by the node's JSON content: equal content compiles to the same function (the compiler checks no counter seed was drawn). */
    private static final Map<Object, Group> GROUPS = new java.util.HashMap<>();

    private static Group groupFor(Object key) {
        synchronized (GROUPS) {
            Group g = GROUPS.get(key);
            if (System.getenv("WG2_SHARE_LOG") != null) {
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "wg2_share.txt"),
                        (g == null ? "new  " : "same ") + Integer.toHexString(key.hashCode()) + " " + String.valueOf(key).substring(0, Math.min(160, String.valueOf(key).length())) + "\n",
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                } catch (Exception ignored) { }
            }
            if (g == null) GROUPS.put(key, g = new Group());
            return g;
        }
    }

    /** Dev counters (WG2_COUNTERS): partial-column requests served from a kept column, and ones that were not. */
    private static final boolean COUNT = System.getenv("WG2_COUNTERS") != null;
    public static final java.util.concurrent.atomic.AtomicLong HITS = new java.util.concurrent.atomic.AtomicLong(), MISSES = new java.util.concurrent.atomic.AtomicLong();

    private final DensityFunction node;
    private final Group group;

    /** @param key the exported node's source JSON (equal content means the same node) */
    public SharedColumnDensity(Object key, DensityFunction node) {
        this.node = node;
        this.group = groupFor(key);
    }

    public DensityFunction node() { return node; }

    private static int slot(float x, float z) {
        return ((Float.floatToRawIntBits(x) * 73856093) ^ (Float.floatToRawIntBits(z) * 19349663)) & 511;
    }

    /** Single points are not served from columns: a point and a column can round differently, and the result must not change. */
    @Override
    public float compute(float x, float y, float z) {
        return node.compute(x, y, z);
    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        int n = maxY - minY;
        if (node.yIndependent() || node.xzIndependent()) { node.computeColumn(x, z, minY, maxY, out); return; }
        if (n < 64) group.wanted = true;
        if (group.wanted) {
            ColumnCache c = group.cache.get();
            int s = slot(x, z);
            if (c.xs[s] == x && c.zs[s] == z && minY >= c.y0[s] && maxY <= c.y1[s]) {
                System.arraycopy(c.vals[s], minY - c.y0[s], out, 0, n);
                if (COUNT) HITS.incrementAndGet();
                return;
            }
            if (COUNT && n < 64) MISSES.incrementAndGet();
            node.computeColumn(x, z, minY, maxY, out);
            if (n >= 64) {
                float[] v = c.vals[s];
                if (v == null || v.length < n) v = c.vals[s] = new float[n];
                System.arraycopy(out, 0, v, 0, n);
                c.xs[s] = x; c.zs[s] = z; c.y0[s] = minY; c.y1[s] = maxY;
            }
            return;
        }
        node.computeColumn(x, z, minY, maxY, out);
    }

    @Override public boolean yIndependent() { return node.yIndependent(); }

    @Override public boolean xzIndependent() { return node.xzIndependent(); }

    @Override public void bounds(float x, float z, int y0, int y1, float[] out) { node.bounds(x, z, y0, y1, out); }

    @Override public void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) { node.boundsSlabs(x, z, minY, maxY, bs, lo, hi); }
}
