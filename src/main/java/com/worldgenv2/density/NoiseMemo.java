package com.worldgenv2.density;

import java.util.HashMap;
import java.util.Map;

/**
 * Recent values of 2D noises, per thread, shared by every node computing the same noise (equal settings, equal amplitude, equal
 * kind): the terrain pass and a material that looks at the terrain's slope evaluate the same noises at the same columns, through
 * different copies of the density tree.
 */
public final class NoiseMemo {
    private NoiseMemo() {}

    private static final Map<Object, Integer> IDS = new HashMap<>();

    /** A small number that stands for one exact noise (kind, settings, amplitude). */
    public static int idFor(Object signature) {
        synchronized (IDS) {
            Integer id = IDS.get(signature);
            if (id == null) IDS.put(signature, id = IDS.size() + 1);
            return id;
        }
    }

    private static final int SLOTS = 1 << 14;

    private static final class Table {
        final int[] ids = new int[SLOTS];
        final float[] xs = new float[SLOTS], zs = new float[SLOTS], vs = new float[SLOTS];
    }

    private static final ThreadLocal<Table> TABLE = ThreadLocal.withInitial(Table::new);

    @FunctionalInterface
    public interface Source { float at(float x, float z); }

    /** The noise {@code id} at (x, z), computed by {@code source} only when not remembered. */
    public static float get(int id, float x, float z, Source source) {
        Table t = TABLE.get();
        int s = ((Float.floatToRawIntBits(x) * 73856093) ^ (Float.floatToRawIntBits(z) * 19349663) ^ (id * 83492791)) & (SLOTS - 1);
        if (t.ids[s] == id && t.xs[s] == x && t.zs[s] == z) return t.vs[s];
        float v = source.at(x, z);
        t.ids[s] = id; t.xs[s] = x; t.zs[s] = z; t.vs[s] = v;
        return v;
    }
}