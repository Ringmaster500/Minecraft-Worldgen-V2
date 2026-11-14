package com.worldgenv2.worldstructure;

import com.google.gson.JsonObject;
import com.worldgenv2.density.FastNoiseLite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Macro "zones" in the Hytale sense: large regions of the world that each draw from their own set of biomes.
 * Zone membership is decided at the Voronoi cell centre by {@code BiCarta}, so zone borders follow biome-cell
 * borders and blend like any other biome transition.
 * <p>
 * Zones are evaluated in order; the first whose mask matches wins. Built-in masks:
 * {@code everywhere}, {@code radial} (distance band), {@code noise} (low-frequency noise range) and
 * {@code patch} (a macro Voronoi lottery shared by all patch zones, weighted). Addons can register more
 * kinds with {@link #registerMaskKind}.
 */
public final class ZoneLayout {

    public interface Mask { boolean matches(float x, float z); }

    /** Creates a mask from a zone's JSON parameters. */
    public interface MaskFactory { Mask create(JsonObject params, long seed); }

    private static final Map<String, MaskFactory> EXTRA_KINDS = new HashMap<>();

    /** Extension point for addon mods: add a new zone mask kind (usable as {@code "kind": "<name>"}). */
    public static synchronized void registerMaskKind(String kind, MaskFactory factory) {
        EXTRA_KINDS.put(kind, factory);
    }

    public static synchronized List<String> extraMaskKinds() {
        return new ArrayList<>(EXTRA_KINDS.keySet());
    }

    public static final class Zone {
        public final String id, name;
        public final int color;
        public final Mask mask;
        public final List<String> biomeNames;
        private volatile Set<Integer> ids = Set.of();

        Zone(String id, String name, int color, Mask mask, List<String> biomeNames) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.mask = mask;
            this.biomeNames = biomeNames;
        }

        public Set<Integer> biomeIds() { return ids; }
    }

    private final List<Zone> zones;

    private ZoneLayout(List<Zone> zones) { this.zones = List.copyOf(zones); }

    public List<Zone> zones() { return zones; }

    public Zone zoneAt(float x, float z) {
        for (Zone zone : zones) if (zone.mask.matches(x, z)) return zone;
        return null;
    }

    /** Biome ids allowed at (x, z), or null when no zone claims the point (then every biome is allowed). */
    public Set<Integer> eligibleIds(float x, float z) {
        Zone zone = zoneAt(x, z);
        return zone == null || zone.ids.isEmpty() ? null : zone.ids;
    }

    /** Resolves each zone's biome names to numeric ids once the tile biomes are known. */
    public void bind(List<TileBiome> tiles) {
        for (Zone zone : zones) {
            Set<Integer> out = new HashSet<>();
            for (TileBiome t : tiles) {
                for (String n : zone.biomeNames) {
                    if (t.name().equalsIgnoreCase(n) || t.name().endsWith(":" + n)) out.add(t.id());
                }
            }
            zone.ids = out;
        }
    }

    // ------------------------------------------------------------------ builder

    public static final class Builder {
        private record Spec(String id, String name, int color, String kind, JsonObject params, List<String> biomes) {}

        private final long seed;
        private final float patchCell;
        private final List<Spec> specs = new ArrayList<>();

        public Builder(long seed, float patchCellSize) {
            this.seed = seed;
            this.patchCell = Math.max(256f, patchCellSize);
        }

        public Builder add(String id, String name, int color, String kind, JsonObject params, List<String> biomes) {
            specs.add(new Spec(id, name, color, kind, params == null ? new JsonObject() : params, biomes));
            return this;
        }

        public ZoneLayout build() {
            List<Spec> patches = new ArrayList<>();
            for (Spec s : specs) if (s.kind().equals("patch")) patches.add(s);
            float[] weights = new float[patches.size()];
            for (int i = 0; i < weights.length; i++) {
                weights[i] = num(patches.get(i).params(), "weight", 1f);
            }
            PatchField field = new PatchField(seed ^ 0x2F0A3E1BL, patchCell, weights);

            List<Zone> out = new ArrayList<>();
            int ordinal = 0;
            for (Spec s : specs) {
                Mask mask;
                switch (s.kind()) {
                    case "everywhere" -> mask = (x, z) -> true;
                    case "radial" -> {
                        float cx = num(s.params(), "centerX", 0), cz = num(s.params(), "centerZ", 0);
                        float inner = num(s.params(), "inner", 0), outer = num(s.params(), "outer", 1000);
                        mask = (x, z) -> {
                            float dx = x - cx, dz = z - cz;
                            float d2 = dx * dx + dz * dz;
                            return d2 >= inner * inner && d2 <= outer * outer;
                        };
                    }
                    case "noise" -> {
                        FastNoiseLite n = new FastNoiseLite((int) (seed ^ (seed >>> 32)) + 9100 + ordinal * 37);
                        n.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
                        n.setFrequency(num(s.params(), "frequency", 0.0004f));
                        n.setFractalType(FastNoiseLite.FractalType.FBm);
                        n.setFractalOctaves(2);
                        float lo = num(s.params(), "min", -1f), hi = num(s.params(), "max", 1f);
                        mask = (x, z) -> { float v = n.getNoise(x, z); return v >= lo && v <= hi; };
                    }
                    case "patch" -> {
                        final int idx = patches.indexOf(s);
                        mask = (x, z) -> field.pick(x, z) == idx;
                    }
                    default -> {
                        MaskFactory f;
                        synchronized (ZoneLayout.class) { f = EXTRA_KINDS.get(s.kind()); }
                        mask = f != null ? f.create(s.params(), seed + ordinal) : (x, z) -> false;
                    }
                }
                out.add(new Zone(s.id(), s.name(), s.color(), mask, s.biomes()));
                ordinal++;
            }
            return new ZoneLayout(out);
        }

        private static float num(JsonObject o, String k, float def) {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsFloat() : def;
        }
    }

    /** Macro Voronoi lottery: each big cell is assigned one patch zone by weight. */
    private static final class PatchField {
        private final long seed;
        private final float cell;
        private final float[] cumulative;
        private final float total;

        PatchField(long seed, float cell, float[] weights) {
            this.seed = seed;
            this.cell = cell;
            this.cumulative = new float[weights.length];
            float t = 0;
            for (int i = 0; i < weights.length; i++) { t += Math.max(0.0001f, weights[i]); cumulative[i] = t; }
            this.total = t;
        }

        private static long hash(long a, int x, int z) {
            long h = a + x * 374761393L + z * 668265263L;
            h = (h ^ (h >> 13)) * 1274126177L;
            return h ^ (h >> 16);
        }

        int pick(float x, float z) {
            if (cumulative.length == 0) return -1;
            int cx = (int) Math.floor(x / cell), cz = (int) Math.floor(z / cell);
            float best = Float.MAX_VALUE;
            int bx = cx, bz = cz;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int ix = cx + dx, iz = cz + dz;
                    long h = hash(seed, ix, iz);
                    float px = (ix + 0.15f + 0.7f * ((h & 0xFFFF) / 65535f)) * cell;
                    float pz = (iz + 0.15f + 0.7f * (((h >>> 16) & 0xFFFF) / 65535f)) * cell;
                    float d = (px - x) * (px - x) + (pz - z) * (pz - z);
                    if (d < best) { best = d; bx = ix; bz = iz; }
                }
            }
            long h = hash(seed ^ 0x51ED270BL, bx, bz);
            float r = ((h >>> 8) & 0xFFFFFF) / (float) 0x1000000 * total;
            for (int i = 0; i < cumulative.length; i++) if (r <= cumulative[i]) return i;
            return cumulative.length - 1;
        }
    }
}
