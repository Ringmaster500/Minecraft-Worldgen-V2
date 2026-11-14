package com.worldgenv2.biome;

import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.hytale.HPattern;
import com.worldgenv2.hytale.HProps;
import com.worldgenv2.hytale.HScanner;
import com.worldgenv2.hytale.HytaleDistribution;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.runtime.ContextPrecision;
import com.worldgenv2.props.runtime.PropRuntime;

import java.io.InputStream;
import java.util.function.Function;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bridges Hytale's asset conventions to the engine's node implementations, so Hytale-authored JSON reads the same here.
 *
 * <p>Hytale gives every density node its children as one positional {@code Inputs} list (one entry for Abs, two for a
 * comparator, any number for Sum). The engine's nodes take named slots ({@code input}, {@code inputA}/{@code inputB}...).
 * This adapter fills the named slots from {@code Inputs} by position, using the node's slot order from the generated
 * node schema. Nodes that already take a list ({@code inputs}) need nothing.
 */
final class HytaleDialect {
    private HytaleDialect() {}

    /** density type (lower case) -> its single (non-list) slot keys in schema order; types with a list slot are omitted. */
    private static Map<String, List<String>> singleSlots;

    @SuppressWarnings("unchecked")
    private static synchronized Map<String, List<String>> table() {
        if (singleSlots != null) return singleSlots;
        Map<String, List<String>> out = new HashMap<>();
        try (InputStream in = HytaleDialect.class.getClassLoader().getResourceAsStream("assets/worldgenv2/node_schema.json")) {
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Object parsed = new JsonAstCompiler.SimpleJsonParser(text).parseValue();
                Object density = ((Map<String, Object>) parsed).get("density");
                if (density instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        Map<String, Object> node = (Map<String, Object>) e.getValue();
                        List<String> keys = new ArrayList<>();
                        boolean hasList = false;
                        if (node.get("slots") instanceof List<?> slots) {
                            for (Object so : slots) {
                                Map<String, Object> s = (Map<String, Object>) so;
                                if (Boolean.TRUE.equals(s.get("multi"))) hasList = true;
                                else keys.add(String.valueOf(s.get("key")));
                            }
                        }
                        if (!hasList && !keys.isEmpty()) out.put(e.getKey().toString().toLowerCase(Locale.ROOT), keys);
                    }
                }
            }
        } catch (Exception ignored) {
            // without the table only list-style nodes accept Inputs; the engine still works
        }
        singleSlots = out;
        return out;
    }

    /** Fills a density node's named slots from a Hytale-style {@code Inputs} list. */
    @SuppressWarnings("unchecked")
    static void adaptDensityInputs(String type, Map<String, Object> json) {
        if (!(json.get("inputs") instanceof List<?> inputs) || inputs.isEmpty()) return;
        String key = type.toLowerCase(Locale.ROOT);
        List<String> slots = table().get(key);
        if (slots == null) slots = table().get(key + "density");
        if (slots == null) return;
        for (int i = 0; i < slots.size() && i < inputs.size(); i++) {
            if (!json.containsKey(slots.get(i))) json.put(slots.get(i), inputs.get(i));
        }
    }

    // ================================================================== Hytale patterns, scanners and search props

    private static Map<String, Object> obj(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static Object get(Map<String, Object> m, String... keys) {
        for (String k : keys) if (m.containsKey(k)) return m.get(k);
        return null;
    }

    private static int num(Object o, int def) {
        if (o instanceof Number n) return (int) Math.round(n.doubleValue());
        if (o instanceof String s) { try { return (int) Math.round(Double.parseDouble(s.trim())); } catch (NumberFormatException e) { return def; } }
        return def;
    }

    private static double dnum(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) { try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return def; } }
        return def;
    }

    private static boolean flag(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    /** A 3D integer vector written {X, Y, Z} or [x, y, z]. */
    private static int[] vec(Object o) {
        if (o instanceof List<?> l && l.size() >= 3) return new int[]{num(l.get(0), 0), num(l.get(1), 0), num(l.get(2), 0)};
        Map<String, Object> m = obj(o);
        return m == null ? new int[]{0, 0, 0} : new int[]{num(get(m, "x"), 0), num(get(m, "y"), 0), num(get(m, "z"), 0)};
    }

    /** A rotation about the vertical axis in quarter turns: degrees as a number or string, or an object with Yaw. */
    static int turns(Object o) {
        Map<String, Object> m = obj(o);
        if (m != null) o = get(m, "yaw", "angle", "degrees", "rotation");
        if (o instanceof String s) s = s.trim().toUpperCase(Locale.ROOT).replace("R", "").replace("DEG_", "");
        double deg = o instanceof String s2 ? dnum(s2, 0) : dnum(o, 0);
        return Math.floorMod((int) Math.round(deg / 90.0), 4);
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) out.add(String.valueOf(x));
        else if (o instanceof String s) for (String p : s.split(",")) if (!p.isBlank()) out.add(p.trim());
        return out;
    }

    private static boolean isAirId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        return s.equals("minecraft:air") || s.equals("air") || s.equals("empty") || s.equals("minecraft:cave_air") || s.equals("minecraft:void_air");
    }

    /** Compiles a Hytale Pattern node (Type + PascalCase keys). */
    @SuppressWarnings("unchecked")
    static <T> HPattern<T> compileHPattern(Object raw, JsonAstCompiler.CompileContext ctx, Function<String, T> resolver) {
        Map<String, Object> j = obj(raw);
        if (j == null) return HPattern.constant(true);
        String type = String.valueOf(get(j, "type")).toLowerCase(Locale.ROOT);
        switch (type) {
            case "constant": return HPattern.constant(flag(get(j, "value"), true));
            case "blocktype": {
                Object mat = get(j, "material");
                if (obj(mat) != null) mat = get(obj(mat), "solid", "fluid", "block", "name", "id");
                String id = String.valueOf(mat);
                return HPattern.blockType(isAirId(id) ? null : resolver.apply(id), isAirId(id));
            }
            case "blockset": {
                Object bs = get(j, "blockset", "blocks", "materials");
                if (obj(bs) != null) bs = get(obj(bs), "materials", "blocks", "include");
                Set<T> set = new HashSet<>();
                boolean air = false;
                for (String id : strings(bs)) { if (isAirId(id)) air = true; else { T b = resolver.apply(id); if (b != null) set.add(b); } }
                return HPattern.blockSet(set, air);
            }
            case "offset": { int[] v = vec(get(j, "offset")); return HPattern.offset(compileHPattern(get(j, "pattern"), ctx, resolver), v[0], v[1], v[2]); }
            case "rotator": return HPattern.rotator(compileHPattern(get(j, "pattern"), ctx, resolver), turns(get(j, "rotation")));
            case "floor": return HPattern.floor(compileHPattern(get(j, "floor"), ctx, resolver), compileHPattern(get(j, "origin"), ctx, resolver));
            case "ceiling": return HPattern.ceiling(compileHPattern(get(j, "ceiling"), ctx, resolver), compileHPattern(get(j, "origin"), ctx, resolver));
            case "wall": {
                List<Integer> dirs = new ArrayList<>();
                for (String d : strings(get(j, "directions"))) { int i = "NSEW".indexOf(d.trim().toUpperCase(Locale.ROOT).charAt(0)); if (i >= 0) dirs.add(i); }
                return HPattern.wall(compileHPattern(get(j, "wall"), ctx, resolver), compileHPattern(get(j, "origin"), ctx, resolver), dirs, flag(get(j, "requirealldirections"), false));
            }
            case "surface": {
                List<Integer> facings = new ArrayList<>();
                for (String d : strings(get(j, "facings"))) { int i = "NSEWUD".indexOf(d.trim().toUpperCase(Locale.ROOT).charAt(0)); if (i >= 0) facings.add(i); }
                return HPattern.surface(compileHPattern(get(j, "surface"), ctx, resolver), compileHPattern(get(j, "medium"), ctx, resolver),
                    dnum(get(j, "surfaceradius"), 0), dnum(get(j, "mediumradius"), 0), num(get(j, "surfacegap"), 0), num(get(j, "mediumgap"), 0),
                    facings, flag(get(j, "requireallfacings"), false));
            }
            case "cuboid": {
                int[] a = vec(get(j, "min")), b = vec(get(j, "max"));
                return HPattern.cuboid(Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]),
                    compileHPattern(get(j, "subpattern", "pattern"), ctx, resolver));
            }
            case "and": case "or": {
                List<HPattern<T>> list = new ArrayList<>();
                if (get(j, "patterns") instanceof List<?> l) for (Object p : l) list.add(compileHPattern(p, ctx, resolver));
                return type.equals("and") ? HPattern.and(list) : HPattern.or(list);
            }
            case "not": return HPattern.not(compileHPattern(get(j, "pattern"), ctx, resolver));
            case "fieldfunction": {
                DensityFunction field = JsonAstCompiler.compileDensity(obj(get(j, "fieldfunction", "density")), ctx);
                List<double[]> ds = new ArrayList<>();
                if (get(j, "delimiters") instanceof List<?> l) for (Object d : l) { Map<String, Object> dm = obj(d); if (dm != null) ds.add(new double[]{dnum(get(dm, "min"), -1e9), dnum(get(dm, "max"), 1e9)}); }
                return HPattern.fieldFunction(field, ds.toArray(new double[0][]));
            }
            default: throw new IllegalArgumentException("Unknown Hytale Pattern '" + get(j, "type") + "'");
        }
    }

    /** Compiles a Hytale Scanner node. */
    static HScanner compileHScanner(Object raw, JsonAstCompiler.CompileContext ctx) {
        Map<String, Object> j = obj(raw);
        if (j == null) return HScanner.ORIGIN;
        String type = String.valueOf(get(j, "type")).toLowerCase(Locale.ROOT);
        HScanner child = get(j, "scanner") == null ? null : compileHScanner(get(j, "scanner"), ctx);
        switch (type) {
            case "origin": return HScanner.ORIGIN;
            case "linear": case "random": {
                Map<String, Object> range = obj(get(j, "range"));
                int min = range == null ? num(get(j, "min"), -16) : num(get(range, "min"), -16), max = range == null ? num(get(j, "max"), 16) : num(get(range, "max"), 16);
                String axisName = String.valueOf(get(j, "axis")).toUpperCase(Locale.ROOT);
                int axis = axisName.startsWith("X") ? 0 : axisName.startsWith("Z") ? 2 : 1;
                if (type.equals("linear")) return HScanner.linear(axis, min, max, child);
                return HScanner.random(axis, min, max, String.valueOf(get(j, "seed")).hashCode(), child);
            }
            case "radial": {
                Map<String, Object> b = obj(get(j, "bounds"));
                if (b == null) { int r = num(get(j, "radius", "range"), 4); return HScanner.radial(-r, -r, -r, r, r, r, child); }
                int[] lo = vec(get(b, "min")), hi = vec(get(b, "max"));
                return HScanner.radial(Math.min(lo[0], hi[0]), Math.min(lo[1], hi[1]), Math.min(lo[2], hi[2]), Math.max(lo[0], hi[0]), Math.max(lo[1], hi[1]), Math.max(lo[2], hi[2]), child);
            }
            case "queue": {
                List<HScanner> list = new ArrayList<>();
                if (get(j, "scanners") instanceof List<?> l) for (Object s : l) list.add(compileHScanner(s, ctx));
                return HScanner.queue(list);
            }
            default: throw new IllegalArgumentException("Unknown Hytale Scanner '" + get(j, "type") + "'");
        }
    }

    /** Registers Hytale's Locator and Orienter props under their Hytale names (the engine's own are the *Prop names). */
    static void register() {
        WorldGenV2Registries.PROP_TYPE.register("Locator", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function<String, T> resolver) {
                if (!j.containsKey("pattern") && !j.containsKey("scanner")) {
                    return WorldGenV2Registries.PROP_TYPE.get("LocatorProp").deserialize(j, ctx, resolver);   // the engine's own surface locator
                }
                Prop<T> child = JsonAstCompiler.compileProp(obj(get(j, "prop", "child")), ctx, resolver);
                return new HProps.Locator<>(child, compileHPattern(get(j, "pattern"), ctx, resolver), compileHScanner(get(j, "scanner"), ctx), num(get(j, "placementcap"), 0));
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Orienter", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function<String, T> resolver) {
                if (!j.containsKey("pattern") && !j.containsKey("rotations")) {
                    return WorldGenV2Registries.PROP_TYPE.get("OrienterProp").deserialize(j, ctx, resolver);   // the engine's own surface-tilt orienter
                }
                Prop<T> child = JsonAstCompiler.compileProp(obj(get(j, "prop", "child")), ctx, resolver);
                List<Integer> rots = new ArrayList<>();
                if (get(j, "rotations") instanceof List<?> l) for (Object r : l) rots.add(turns(r)); else rots.add(0);
                String mode = String.valueOf(get(j, "selectionmode")).toLowerCase(Locale.ROOT);
                HProps.Selection sel = mode.startsWith("all") ? HProps.Selection.ALL_VALID : mode.startsWith("random") ? HProps.Selection.RANDOM_VALID : HProps.Selection.FIRST_VALID;
                return new HProps.Orienter<>(child, rots, compileHPattern(get(j, "pattern"), ctx, resolver), compileHScanner(get(j, "scanner"), ctx), sel, String.valueOf(get(j, "seed")).hashCode());
            }
        });
    }

    /**
     * A biome's Props entry in Hytale's form: {@code { "Runtime": n, "Positions": {...}, "Assignments": {...}, "Skip": false }}.
     * Returns null for a skipped entry. Hytale numbers runtimes from 0; the engine's stages start at 1.
     */
    static <T> Prop<T> compileBiomeProp(Map<String, Object> entry, JsonAstCompiler.CompileContext ctx, Function<String, T> resolver) {
        if (flag(get(entry, "skip"), false)) return null;
        PositionsProvider positions = JsonAstCompiler.compilePositionsProvider(get(entry, "positions"), ctx);
        Assignments<T> assignments = JsonAstCompiler.compileAssignments(get(entry, "assignments"), ctx, resolver);
        ContextPrecision precision = ContextPrecision.BIOME;
        return new PropRuntime<>(num(get(entry, "runtime"), 0) + 1, precision, new HytaleDistribution<>(positions, assignments), 0, 0, 0);
    }

    /** True for a Props entry written in Hytale's form (no Type, but Positions and Assignments). */
    static boolean isHytaleBiomeProp(Map<String, Object> entry) {
        return !entry.containsKey("type") && (entry.containsKey("positions") || entry.containsKey("assignments"));
    }
}