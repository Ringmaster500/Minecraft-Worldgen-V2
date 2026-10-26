package com.worldgenv2.engine.chunkgenerator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe LRU cache for multi-stage immutable chunk snapshots.
 * Allows stage S+1 in chunk (cx, cz) to query immutable voxels produced at stage S across chunk boundaries
 * without circular locks or cascading chunk generation.
 */
public final class StageContextCache<T> {

    public static final int DEFAULT_CAPACITY = 256;

    private final int capacity;
    private final Map<Long, ImmutableChunkSnapshot<T>> cache;

    public StageContextCache() {
        this(DEFAULT_CAPACITY);
    }

    public StageContextCache(int capacity) {
        this.capacity = Math.max(16, capacity);
        this.cache = new LinkedHashMap<>(this.capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ImmutableChunkSnapshot<T>> eldest) {
                return size() > StageContextCache.this.capacity;
            }
        };
    }

    public static long key(int chunkX, int chunkZ, int stage) {
        long k = (((long) chunkX & 0xFFFFFFL) << 36) | (((long) chunkZ & 0xFFFFFFL) << 12) | ((long) stage & 0xFFFL);
        return k;
    }

    public ImmutableChunkSnapshot<T> getSnapshot(int chunkX, int chunkZ, int stage) {
        long k = key(chunkX, chunkZ, stage);
        synchronized (cache) {
            return cache.get(k);
        }
    }

    public void putSnapshot(int chunkX, int chunkZ, int stage, ImmutableChunkSnapshot<T> snapshot) {
        if (snapshot == null) return;
        long k = key(chunkX, chunkZ, stage);
        synchronized (cache) {
            cache.put(k, snapshot);
        }
    }

    public boolean hasSnapshot(int chunkX, int chunkZ, int stage) {
        long k = key(chunkX, chunkZ, stage);
        synchronized (cache) {
            return cache.containsKey(k);
        }
    }

    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
