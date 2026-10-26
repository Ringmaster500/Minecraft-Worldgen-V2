package com.worldgenv2.vector;

import java.util.Objects;

/**
 * Thread-local cache for the downstream VectorProvider.
 * Prevents redundant evaluations when the same coordinate is queried multiple times.
 */
public final class CacheVectorProvider implements VectorProvider {

    private final VectorProvider source;

    private static final class CacheEntry {
        double x = Double.NaN;
        double y = Double.NaN;
        double z = Double.NaN;
        Vec3d cached = null;
    }

    private final ThreadLocal<CacheEntry> cache = ThreadLocal.withInitial(CacheEntry::new);

    public CacheVectorProvider(VectorProvider source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public Vec3d getVector(double x, double y, double z) {
        CacheEntry entry = cache.get();
        if (entry.cached != null && entry.x == x && entry.y == y && entry.z == z) {
            return entry.cached;
        }

        Vec3d result = source.getVector(x, y, z);
        entry.x = x;
        entry.y = y;
        entry.z = z;
        entry.cached = result;
        return result;
    }

    public VectorProvider getSource() {
        return source;
    }
}
