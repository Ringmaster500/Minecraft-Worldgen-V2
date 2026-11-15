package com.worldgenv2.density;

import java.util.Objects;

/**
 * Thread-local caching density node.
 * Memoizes the last evaluated 3D coordinate and 2D column to eliminate redundant calculations
 * when multiple pins or stages query the same position.
 */
public final class CacheDensity implements DensityFunction {

    private final DensityFunction source;

    private static final class CacheEntry {
        float x = Float.NaN;
        float y = Float.NaN;
        float z = Float.NaN;
        float value = Float.NaN;

        float colX = Float.NaN;
        float colZ = Float.NaN;
        int minY = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        float[] colData = null;
    }

    private final ThreadLocal<CacheEntry> cache = ThreadLocal.withInitial(CacheEntry::new);

    public CacheDensity(DensityFunction source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public float compute(float x, float y, float z) {
        CacheEntry entry = cache.get();
        if (entry.x == x && entry.y == y && entry.z == z) {
            return entry.value;
        }

        float v = source.compute(x, y, z);
        entry.x = x;
        entry.y = y;
        entry.z = z;
        entry.value = v;
        return v;
    }

    private byte yFlat, xzFlat;
    private final YTable yTable = new YTable();
    @Override public boolean yIndependent() { byte f = yFlat; if (f == 0) yFlat = f = (byte) (source.yIndependent() ? 1 : 2); return f == 1; }
    @Override public boolean xzIndependent() { byte f = xzFlat; if (f == 0) xzFlat = f = (byte) (source.xzIndependent() ? 1 : 2); return f == 1; }

    @Override
    public void boundsSlabs(float x, float z, int minY, int maxY, int bs, float[] lo, float[] hi) {
        int nb = (maxY - minY + bs - 1) / bs;
        if (yIndependent() || xzIndependent()) { DensityFunction.super.boundsSlabs(x, z, minY, maxY, bs, lo, hi); return; }
        source.boundsSlabs(x, z, minY, maxY, bs, lo, hi);    }

    @Override
    public void bounds(float x, float z, int y0, int y1, float[] out) {
        if (yIndependent()) { DensityFunction.super.bounds(x, z, y0, y1, out); return; }
        if (xzIndependent()) { yTable.bounds(this, y0, y1, out); return; }
        source.bounds(x, z, y0, y1, out);    }

    @Override
    public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
        if (yIndependent()) { java.util.Arrays.fill(out, 0, maxY - minY, compute(x, minY, z)); return; }
        if (xzIndependent()) { yTable.fill(this, minY, maxY, out); return; }
        CacheEntry entry = cache.get();
        int height = maxY - minY;
        if (entry.colX == x && entry.colZ == z && entry.minY == minY && entry.maxY == maxY && entry.colData != null) {
            System.arraycopy(entry.colData, 0, out, 0, height);
            return;
        }

        source.computeColumn(x, z, minY, maxY, out);

        if (entry.colData == null || entry.colData.length < height) {
            entry.colData = new float[height];
        }
        System.arraycopy(out, 0, entry.colData, 0, height);
        entry.colX = x;
        entry.colZ = z;
        entry.minY = minY;
        entry.maxY = maxY;
    }

    public DensityFunction getSource() {
        return source;
    }
}
