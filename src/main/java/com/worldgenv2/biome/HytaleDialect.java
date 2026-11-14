package com.worldgenv2.biome;

import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.hytale.HPattern;
import com.worldgenv2.hytale.HProps;
import com.worldgenv2.hytale.HDensity;
import com.worldgenv2.hytale.HMaterials;
import com.worldgenv2.hytale.HMoreProps;
import com.worldgenv2.hytale.HPositions;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.props.WeightedProp;
import com.worldgenv2.prefab.PrefabProp;
import com.worldgenv2.hytale.HScanner;
import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.hytale.HDistributions;
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
public final class HytaleDialect {
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
        registerPositions();
        registerMoreProps();
        registerDensity();
        registerMaterials();
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
    public static <T> Prop<T> compileBiomeProp(Map<String, Object> entry, JsonAstCompiler.CompileContext ctx, Function<String, T> resolver) {
        if (flag(get(entry, "skip"), false)) return null;
        JsonAstCompiler.collectExports(entry, ctx);
        HDistributions.Field<T> field;
        if (entry.containsKey("propdistribution")) {
            field = compileDistribution(get(entry, "propdistribution"), ctx, resolver);
        } else {
            PositionsProvider positions = JsonAstCompiler.compilePositionsProvider(get(entry, "positions"), ctx);
            Assignments<T> assignments = JsonAstCompiler.compileAssignments(get(entry, "assignments"), ctx, resolver);
            field = HDistributions.assigned(HDistributions.positions(positions), assignments, false);
        }
        ContextPrecision precision = ContextPrecision.BIOME;
        String cp = String.valueOf(get(entry, "contextprecision")).toUpperCase(Locale.ROOT);
        for (ContextPrecision c : ContextPrecision.values()) if (c.name().equals(cp)) precision = c;
        return new PropRuntime<>(num(get(entry, "runtime"), 0) + 1, precision, new HDistributions.FieldProp<>(field), 0, 0, 0);
    }

    /** True for a Props entry written in Hytale's form (no Type, but a PropDistribution, or Positions and Assignments). */
    static boolean isHytaleBiomeProp(Map<String, Object> entry) {
        return !entry.containsKey("type") && (entry.containsKey("positions") || entry.containsKey("assignments") || entry.containsKey("propdistribution"));
    }

    /** A Prop Distribution node: Constant, Positions, Assigned, Union, Anchor, Imported. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static <T> HDistributions.Field<T> compileDistribution(Object raw, JsonAstCompiler.CompileContext ctx, Function<String, T> resolver) {
        Map<String, Object> j = obj(raw);
        if (j == null) return HDistributions.positions((x0, y0, z0, x1, y1, z1) -> List.of());
        String type = String.valueOf(get(j, "type")).toLowerCase(Locale.ROOT);
        switch (type) {
            case "constant":
                return HDistributions.constant(JsonAstCompiler.compilePositionsProvider(get(j, "positions"), ctx), JsonAstCompiler.compileProp(obj(get(j, "prop")), ctx, resolver));
            case "positions":
                return HDistributions.positions(JsonAstCompiler.compilePositionsProvider(get(j, "positions"), ctx));
            case "assigned":
                return HDistributions.assigned(compileDistribution(get(j, "propdistribution"), ctx, resolver),
                        JsonAstCompiler.compileAssignments(get(j, "assignments"), ctx, resolver), flag(get(j, "overrideallprops"), false));
            case "union": {
                List<HDistributions.Field<T>> kids = new ArrayList<>();
                if (get(j, "propdistributions") instanceof List<?> l) for (Object o : l) kids.add(compileDistribution(o, ctx, resolver));
                return HDistributions.union(kids);
            }
            case "anchor":
                return HDistributions.anchor(compileDistribution(get(j, "propdistribution"), ctx, resolver), flag(get(j, "reversed"), false));
            case "graph":
                return HDistributions.graph(JsonAstCompiler.compileGraphGenerator(get(j, "graphgenerator"), ctx, resolver), String.valueOf(get(j, "contentlayer") == null ? "" : get(j, "contentlayer")), ctx.worldSeed());
            case "imported": {
                Object loaded = ctx.resolveAsset(String.valueOf(get(j, "name")));
                if (loaded == null) throw new IllegalArgumentException("Imported PropDistribution '" + get(j, "name") + "' was not found");
                return compileDistribution(loaded, ctx, resolver);
            }
            default:
                throw new IllegalArgumentException("Unknown PropDistribution type '" + get(j, "type") + "'");
        }
    }
    // ================================================================== Hytale positions providers

    private static long seedOf(Object o, JsonAstCompiler.CompileContext ctx) {
        return o == null ? ctx.worldSeed() : String.valueOf(o).hashCode() * 0x9E3779B97F4A7C15L ^ ctx.worldSeed();
    }

    /** A decimal 3D vector written {X, Y, Z}, [x, y, z] or a single number for all three. */
    private static double[] vecD(Object o, double def) {
        if (o instanceof Number n) return new double[]{n.doubleValue(), n.doubleValue(), n.doubleValue()};
        if (o instanceof List<?> l && l.size() >= 3) return new double[]{dnum(l.get(0), def), dnum(l.get(1), def), dnum(l.get(2), def)};
        Map<String, Object> m = obj(o);
        return m == null ? new double[]{def, def, def} : new double[]{dnum(get(m, "x"), def), dnum(get(m, "y"), def), dnum(get(m, "z"), def)};
    }

    /** Bounds written {Min: {...}, Max: {...}}; returns {min[3], max[3]}. */
    static double[][] boundsD(Object o, double def) {
        Map<String, Object> m = obj(o);
        if (m == null) return new double[][]{{-def, -def, -def}, {def, def, def}};
        return new double[][]{vecD(get(m, "min"), -def), vecD(get(m, "max"), def)};
    }

    private static PositionsProvider child(Map<String, Object> j, JsonAstCompiler.CompileContext ctx) {
        return JsonAstCompiler.compilePositionsProvider(get(j, "positions"), ctx);
    }

    /** The engine's grids once took a spacing; Hytale's take none. A legacy spacing is still honoured as a Scaler on top. */
    private static PositionsProvider withLegacySpacing(PositionsProvider grid, Map<String, Object> j, boolean threeD) {
        Object s = get(j, "spacing", "scale");
        double sp = s == null ? 1.0 : dnum(s, 1.0);
        return sp == 1.0 ? grid : HPositions.scaler(grid, sp, threeD ? sp : 1.0, sp);
    }

    static void registerPositions() {
        var R = WorldGenV2Registries.POSITIONS_PROVIDER_TYPE;
        R.register("SquareGrid2d", (j, ctx) -> withLegacySpacing(HPositions.squareGrid2d(), j, false));
        R.register("SquareGrid3d", (j, ctx) -> withLegacySpacing(HPositions.squareGrid3d(), j, true));
        R.register("TriangularGrid2d", (j, ctx) -> withLegacySpacing(HPositions.triangularGrid2d(), j, false));
        R.register("Scaler", (j, ctx) -> { double[] s = vecD(get(j, "scale"), 1.0); return HPositions.scaler(child(j, ctx), s[0], s[1], s[2]); });
        R.register("Jitter2d", (j, ctx) -> HPositions.jitter(child(j, ctx), dnum(get(j, "magnitude"), 1.0), seedOf(get(j, "seed"), ctx), false));
        R.register("Jitter3d", (j, ctx) -> HPositions.jitter(child(j, ctx), dnum(get(j, "magnitude"), 1.0), seedOf(get(j, "seed"), ctx), true));
        R.register("DirectionalJitter", (j, ctx) -> {
            double[] d = vecD(get(j, "direction"), 0.0);
            return HPositions.directionalJitter(child(j, ctx), dnum(get(j, "magnitude"), 1.0), d[0], d[1], d[2], flag(get(j, "isbidirectional"), false), seedOf(get(j, "seed"), ctx));
        });
        R.register("Clusters", (j, ctx) -> {
            double[][] b = boundsD(get(j, "clusterbounds"), 8.0);
            double hx = Math.max(Math.abs(b[0][0]), Math.abs(b[1][0])), hy = Math.max(Math.abs(b[0][1]), Math.abs(b[1][1])), hz = Math.max(Math.abs(b[0][2]), Math.abs(b[1][2]));
            return HPositions.clusters(JsonAstCompiler.compilePositionsProvider(get(j, "cluster"), ctx), JsonAstCompiler.compilePositionsProvider(get(j, "distributor"), ctx), hx, hy, hz);
        });
        R.register("List", (j, ctx) -> {
            List<Vec3d> pts = new ArrayList<>();
            if (get(j, "positions") instanceof List<?> l) for (Object o : l) { double[] v = vecD(o, 0.0); pts.add(new Vec3d(v[0], v[1], v[2])); }
            return HPositions.list(pts);
        });
        R.register("Anchor", (j, ctx) -> HPositions.anchor(child(j, ctx), flag(get(j, "reversed"), false)));
        R.register("Bound", (j, ctx) -> { double[][] b = boundsD(get(j, "bounds"), 1e9); return HPositions.bound(child(j, ctx), b[0], b[1]); });
        R.register("FieldFunction", (j, ctx) -> {
            DensityFunction d = JsonAstCompiler.compileDensity(obj(get(j, "density", "fieldfunction")), ctx);
            List<double[]> ds = new ArrayList<>();
            if (get(j, "delimiters") instanceof List<?> l) for (Object o : l) { Map<String, Object> m = obj(o); if (m != null) ds.add(new double[]{dnum(get(m, "min"), -1e9), dnum(get(m, "max"), 1e9)}); }
            return HPositions.fieldFunction(child(j, ctx), d, ds.toArray(new double[0][]));
        });
        R.register("Occurrence", (j, ctx) -> HPositions.occurrence(child(j, ctx), JsonAstCompiler.compileDensity(obj(get(j, "fieldfunction", "density")), ctx), seedOf(get(j, "seed"), ctx)));
        R.register("Offset", (j, ctx) -> { double[] o = vecD(get(j, "offset"), 0.0); return HPositions.offset(child(j, ctx), o[0], o[1], o[2]); });
        R.register("VectorOffset", (j, ctx) -> {
            double[][] b = boundsD(get(j, "movementbounds"), 1e9);
            return HPositions.vectorOffset(child(j, ctx), JsonAstCompiler.compileVectorProvider(get(j, "vectorprovider"), ctx), b[0], b[1]);
        });
        R.register("BaseHeight", (j, ctx) -> {
            String name = String.valueOf(get(j, "baseheightname"));
            return HPositions.baseHeight(child(j, ctx), () -> ctx.decimalConstants().getOrDefault(name, 0.0));
        });
        R.register("Framework", (j, ctx) -> {
            String name = String.valueOf(get(j, "name"));
            return (x0, y0, z0, x1, y1, z1) -> {
                PositionsProvider p = ctx.frameworkPositions().get(name);
                return p == null ? List.of() : p.getPositions(x0, y0, z0, x1, y1, z1);
            };
        });
        R.register("Union", (j, ctx) -> {
            List<PositionsProvider> parts = new ArrayList<>();
            if (get(j, "positions") instanceof List<?> l) for (Object o : l) parts.add(JsonAstCompiler.compilePositionsProvider(o, ctx));
            return HPositions.union(parts);
        });
        R.register("SimpleHorizontal", (j, ctx) -> {
            Object r = get(j, "rangey");
            Map<String, Object> rm = obj(r);
            double lo = rm != null ? dnum(get(rm, "min"), -1e9) : -Math.abs(dnum(r, 1e9)), hi = rm != null ? dnum(get(rm, "max"), 1e9) : Math.abs(dnum(r, 1e9));
            return HPositions.simpleHorizontal(child(j, ctx), lo, hi);
        });
        R.register("Cache", (j, ctx) -> HPositions.cache(child(j, ctx), num(get(j, "sectionsize"), 32), num(get(j, "cachesize"), 50)));
    }

    // ================================================================== more Hytale props and the Constant material

    /** Builds a prop from Hytale-shaped JSON (raw types: the JSON decides the block type at run time). */
    @FunctionalInterface
    @SuppressWarnings("rawtypes")
    private interface HProp {
        Prop make(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function r);
    }

    /** Registers a Hytale prop under its Hytale name; JSON that is not in Hytale's shape still goes to what was registered before. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void over(String name, java.util.function.Predicate<Map<String, Object>> isHytale, HProp hy) {
        WorldGenV2Registries.PropDeserializer legacy = WorldGenV2Registries.PROP_TYPE.get(name);
        WorldGenV2Registries.PROP_TYPE.register(name, new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function<String, T> r) {
                if (legacy == null || isHytale.test(j)) return (Prop<T>) hy.make(j, ctx, r);
                return legacy.deserialize(j, ctx, r);
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Prop child(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function r) {
        return JsonAstCompiler.compileProp(obj(get(j, "prop", "child")), ctx, r);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Set blockSet(Object o, Function r) {
        Set out = new HashSet();
        if (obj(o) != null) o = get(obj(o), "materials", "blocks", "include");
        for (String id : strings(o)) { Object b = r.apply(id); if (b != null) out.add(b); }
        return out;
    }

    /** A Material: a block id, or {Solid: id} / {Fluid: id}. */
    private static String materialId(Object o) {
        Map<String, Object> m = obj(o);
        if (m != null) { Object v = get(m, "solid", "block", "name", "id", "fluid"); return v == null ? "minecraft:air" : String.valueOf(v); }
        return String.valueOf(o);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerMoreProps() {
        over("Offset", j -> j.containsKey("offset"), (j, ctx, r) -> {
            int[] v = vec(get(j, "offset"));
            return new HMoreProps.Offset(child(j, ctx, r), v[0], v[1], v[2]);
        });
        over("Mask", j -> j.containsKey("prop") && !j.containsKey("requiredblock") && !j.containsKey("requiredblocks") && !j.containsKey("minslopedegrees") && !j.containsKey("maxslopedegrees"), (j, ctx, r) -> {
            Map<String, Object> m = obj(get(j, "mask"));
            Set dontPlace = m == null ? new HashSet() : blockSet(get(m, "dontplace"), r), dontReplace = m == null ? new HashSet() : blockSet(get(m, "dontreplace"), r);
            List<HMoreProps.MaskRule> adv = new ArrayList<>();
            if (m != null && get(m, "advanced") instanceof List<?> l) for (Object o : l) { Map<String, Object> rm = obj(o); if (rm != null) adv.add(new HMoreProps.MaskRule(blockSet(get(rm, "source"), r), blockSet(get(rm, "canreplace"), r))); }
            return new HMoreProps.Mask(child(j, ctx, r), dontPlace, dontReplace, adv);
        });
        over("StaticRotator", j -> j.containsKey("prop"), (j, ctx, r) -> {
            int t = turns(get(j, "rotation"));
            Prop c = child(j, ctx, r);
            return t == 0 ? c : new com.worldgenv2.props.StaticRotatorProp(c, com.worldgenv2.props.StaticRotatorProp.CardinalRotation.fromQuarterTurns(t));
        });
        over("DensitySelector", j -> j.containsKey("delimiters"), (j, ctx, r) -> {
            DensityFunction d = JsonAstCompiler.compileDensity(obj(get(j, "density")), ctx);
            List<HMoreProps.Delimiter> ds = new ArrayList<>();
            if (get(j, "delimiters") instanceof List<?> l) for (Object o : l) {
                Map<String, Object> m = obj(o);
                if (m != null) ds.add(new HMoreProps.Delimiter(dnum(get(m, "min"), -1e9), dnum(get(m, "max"), 1e9), JsonAstCompiler.compileProp(obj(get(m, "prop")), ctx, r)));
            }
            return new HMoreProps.DensitySelector(d, ds);
        });
        over("Density", j -> j.containsKey("bounds"), (j, ctx, r) -> {
            double[][] b = boundsD(get(j, "bounds"), 4.0);
            MaterialProvider mat = JsonAstCompiler.compileMaterialProvider(get(j, "material"), ctx, r);
            return new com.worldgenv2.props.DensityProp(JsonAstCompiler.compileDensity(obj(get(j, "density")), ctx), mat,
                (int) b[0][0], (int) b[0][1], (int) b[0][2], (int) b[1][0], (int) b[1][1], (int) b[1][2], 0.0f);
        });
        over("Cuboid", j -> j.containsKey("bounds"), (j, ctx, r) -> {
            double[][] b = boundsD(get(j, "bounds"), 1.0);
            MaterialProvider mat = JsonAstCompiler.compileMaterialProvider(get(j, "material"), ctx, r);
            return new HMoreProps.Cuboid(new int[]{(int) b[0][0], (int) b[0][1], (int) b[0][2]}, new int[]{(int) b[1][0], (int) b[1][1], (int) b[1][2]}, mat);
        });
        over("Manual", j -> j.containsKey("blocks"), (j, ctx, r) -> {
            List<HMoreProps.Placed> out = new ArrayList<>();
            if (get(j, "blocks") instanceof List<?> l) for (Object o : l) {
                Map<String, Object> m = obj(o);
                if (m == null) continue;
                int[] v = vec(get(m, "offset", "position") != null ? get(m, "offset", "position") : m);
                Object block = r.apply(materialId(get(m, "material", "block")));
                if (block != null) out.add(new HMoreProps.Placed(v[0], v[1], v[2], block));
            }
            return new HMoreProps.Manual(out);
        });
        over("Prefab", j -> j.containsKey("weightedprefabpaths"), (j, ctx, r) -> {
            List<WeightedProp.Entry> entries = new ArrayList<>();
            if (get(j, "weightedprefabpaths") instanceof List<?> l) for (Object o : l) {
                Map<String, Object> m = obj(o);
                if (m == null) continue;
                var pf = ctx.resolvePrefab(String.valueOf(get(m, "path")));
                if (pf != null) entries.add(new WeightedProp.Entry(new PrefabProp(pf, r, false, true, List.of(), 0, null), (float) dnum(get(m, "weight"), 1.0)));
            }
            return entries.isEmpty() ? (c -> false) : new WeightedProp(entries);
        });

        // Hytale's Constant MaterialProvider provides one Material
        WorldGenV2Registries.MaterialProviderDeserializer legacyMat = WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get("Constant");
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Constant", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function<String, T> r) {
                if (!j.containsKey("material") && legacyMat != null) return legacyMat.deserialize(j, ctx, r);
                return new ConstantMaterialProvider<>(r.apply(materialId(get(j, "material"))));
            }
        });
    }

    // ================================================================== Hytale density nodes

    /** The i-th child of a Hytale density node's positional Inputs list. */
    private static DensityFunction input(Map<String, Object> j, int i, JsonAstCompiler.CompileContext ctx) {
        if (get(j, "inputs") instanceof List<?> l && i < l.size()) return JsonAstCompiler.compileDensity(obj(l.get(i)), ctx);
        return (x, y, z) -> 0f;
    }

    private static List<DensityFunction> inputs(Map<String, Object> j, JsonAstCompiler.CompileContext ctx) {
        List<DensityFunction> out = new ArrayList<>();
        if (get(j, "inputs") instanceof List<?> l) for (Object o : l) out.add(JsonAstCompiler.compileDensity(obj(o), ctx));
        return out;
    }

    static void registerDensity() {
        var R = WorldGenV2Registries.DENSITY_NODE_TYPE;
        R.register("Multiplier", (j, ctx) -> HDensity.multiplier(inputs(j, ctx)));
        R.register("SmoothMax", (j, ctx) -> HDensity.smoothMax(input(j, 0, ctx), input(j, 1, ctx), (float) dnum(get(j, "range"), 0.2)));
        R.register("SmoothMin", (j, ctx) -> HDensity.smoothMin(input(j, 0, ctx), input(j, 1, ctx), (float) dnum(get(j, "range"), 0.2)));
        R.register("SmoothClamp", (j, ctx) -> HDensity.smoothClamp(input(j, 0, ctx), (float) dnum(get(j, "walla"), 0), (float) dnum(get(j, "wallb"), 1), (float) dnum(get(j, "range"), 0.2)));
        R.register("Inverter", (j, ctx) -> HDensity.inverter(input(j, 0, ctx)));
        R.register("Scale", (j, ctx) -> HDensity.scale(input(j, 0, ctx), (float) dnum(get(j, "x"), 1), (float) dnum(get(j, "y"), 1), (float) dnum(get(j, "z"), 1)));
        R.register("Normalizer", (j, ctx) -> HDensity.normalizer(input(j, 0, ctx), (float) dnum(get(j, "frommin"), 0), (float) dnum(get(j, "frommax"), 1), (float) dnum(get(j, "tomin"), 0), (float) dnum(get(j, "tomax"), 1)));
        R.register("Slider", (j, ctx) -> HDensity.slider(input(j, 0, ctx), (float) dnum(get(j, "slidex"), 0), (float) dnum(get(j, "slidey"), 0), (float) dnum(get(j, "slidez"), 0)));
        var legacySelector = R.get("Selector");
        R.register("Selector", (j, ctx) -> legacySelector != null && !j.containsKey("inputs") ? legacySelector.deserialize(j, ctx) : HDensity.selector(inputs(j, ctx)));
        R.register("DistanceFromBiomeEdge", (j, ctx) -> (x, y, z) -> {
            double d = ctx.biomeEdgeDistance(x, z);
            return d > ctx.maxBiomeEdgeDistance() ? -1f : (float) d;
        });
        R.register("BaseHeight", (j, ctx) -> {
            String name = String.valueOf(get(j, "baseheightname"));
            return HDensity.baseHeight(() -> ctx.decimalConstants().getOrDefault(name, 0.0), flag(get(j, "distance"), false));
        });
        R.register("Distance", (j, ctx) -> HDensity.distance(JsonAstCompiler.compileCurve(get(j, "curve"), ctx)));
        R.register("Axis", (j, ctx) -> { double[] a = vecD(get(j, "axis"), 0.0); return HDensity.axis(a[0], a[1], a[2], JsonAstCompiler.compileCurve(get(j, "curve"), ctx)); });
        R.register("CellWallDistance", (j, ctx) -> HDensity.cellWallDistance());
    }

    // ================================================================== Hytale material providers

    @FunctionalInterface
    @SuppressWarnings("rawtypes")
    private interface HMat {
        MaterialProvider make(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function r);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void overMat(String name, java.util.function.Predicate<Map<String, Object>> isHytale, HMat hy) {
        WorldGenV2Registries.MaterialProviderDeserializer legacy = WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get(name);
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register(name, new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> j, JsonAstCompiler.CompileContext ctx, Function<String, T> r) {
                if (legacy == null || isHytale.test(j)) return (MaterialProvider<T>) hy.make(j, ctx, r);
                return legacy.deserialize(j, ctx, r);
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static MaterialProvider slot(Object o, JsonAstCompiler.CompileContext ctx, Function r) {
        return JsonAstCompiler.compileMaterialProvider(o, ctx, r);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static java.util.function.IntPredicate compileCondition(Object raw) {
        Map<String, Object> m = obj(raw);
        if (m == null) return s -> true;
        String type = String.valueOf(get(m, "type"));
        List<java.util.function.IntPredicate> kids = new ArrayList<>();
        if (get(m, "conditions") instanceof List<?> l) for (Object o : l) kids.add(compileCondition(o));
        if (get(m, "condition") != null) kids.add(compileCondition(get(m, "condition")));
        int value = num(get(m, "value", "threshold"), 0);
        return HMaterials.condition(type, String.valueOf(get(m, "contexttocheck")), value, kids);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static HMaterials.Layer compileLayer(Map<String, Object> lm, JsonAstCompiler.CompileContext ctx, Function r) {
        MaterialProvider p = slot(get(lm, "material"), ctx, r);
        String type = String.valueOf(get(lm, "type")).toLowerCase(Locale.ROOT);
        long seed = seedOf(get(lm, "seed"), ctx);
        switch (type) {
            case "rangethickness": return HMaterials.rangeThickness(p, num(get(lm, "rangemin"), 1), num(get(lm, "rangemax"), 1), seed);
            case "weightedthickness": {
                List<Integer> ts = new ArrayList<>();
                List<Double> ws = new ArrayList<>();
                if (get(lm, "possiblethicknesses") instanceof List<?> l) for (Object o : l) { Map<String, Object> m = obj(o); if (m != null) { ts.add(num(get(m, "thickness", "value"), 1)); ws.add(dnum(get(m, "weight"), 1.0)); } }
                return HMaterials.weightedThickness(p, ts, ws, seed);
            }
            case "noisethickness": return HMaterials.noiseThickness(p, JsonAstCompiler.compileDensity(obj(get(lm, "thicknessfunctionxz")), ctx));
            default: return HMaterials.constantThickness(p, num(get(lm, "thickness"), 1));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerMaterials() {
        overMat("Transparent", j -> true, (j, ctx, r) -> HMaterials.transparent());
        overMat("Solidity", j -> j.containsKey("solid") || j.containsKey("empty"), (j, ctx, r) -> HMaterials.solidity(slot(get(j, "solid"), ctx, r), j.containsKey("empty") ? slot(get(j, "empty"), ctx, r) : null));
        overMat("Queue", j -> true, (j, ctx, r) -> {
            List<MaterialProvider> q = new ArrayList<>();
            if (get(j, "queue") instanceof List<?> l) for (Object o : l) q.add(slot(o, ctx, r));
            return HMaterials.queue((List) q);
        });
        overMat("SimpleHorizontal", j -> j.containsKey("topy") || j.containsKey("bottomy"), (j, ctx, r) -> {
            String tb = String.valueOf(get(j, "topbaseheight", "topbaseheightname")), bb = String.valueOf(get(j, "bottombaseheight", "bottombaseheightname"));
            java.util.function.DoubleSupplier top = get(j, "topbaseheight", "topbaseheightname") == null ? null : () -> ctx.decimalConstants().getOrDefault(tb, 0.0);
            java.util.function.DoubleSupplier bottom = get(j, "bottombaseheight", "bottombaseheightname") == null ? null : () -> ctx.decimalConstants().getOrDefault(bb, 0.0);
            return HMaterials.simpleHorizontal(num(get(j, "topy"), 256), num(get(j, "bottomy"), 0), top, bottom, slot(get(j, "material"), ctx, r));
        });
        overMat("Striped", j -> j.containsKey("stripes") && get(j, "stripes") instanceof List<?> l && !l.isEmpty() && obj(l.get(0)) != null && obj(l.get(0)).containsKey("topy"), (j, ctx, r) -> {
            List<int[]> stripes = new ArrayList<>();
            if (get(j, "stripes") instanceof List<?> l) for (Object o : l) { Map<String, Object> m = obj(o); if (m != null) stripes.add(new int[]{num(get(m, "topy"), 0), num(get(m, "bottomy"), 0)}); }
            return HMaterials.striped(stripes, slot(get(j, "material"), ctx, r));
        });
        overMat("Weighted", j -> true, (j, ctx, r) -> {
            List<MaterialProvider> ps = new ArrayList<>();
            List<Double> ws = new ArrayList<>();
            if (get(j, "weightedmaterials") instanceof List<?> l) for (Object o : l) { Map<String, Object> m = obj(o); if (m != null) { ps.add(slot(get(m, "material"), ctx, r)); ws.add(dnum(get(m, "weight"), 1.0)); } }
            return HMaterials.weighted(seedOf(get(j, "seed"), ctx), dnum(get(j, "skipchance"), 0.0), (List) ps, ws);
        });
        overMat("FieldFunction", j -> true, (j, ctx, r) -> {
            List<HMaterials.Delimiter> ds = new ArrayList<>();
            if (get(j, "delimiters") instanceof List<?> l) for (Object o : l) { Map<String, Object> m = obj(o); if (m != null) ds.add(new HMaterials.Delimiter(dnum(get(m, "from", "min"), -1e9), dnum(get(m, "to", "max"), 1e9), slot(get(m, "material"), ctx, r))); }
            return HMaterials.fieldFunction(JsonAstCompiler.compileDensity(obj(get(j, "fieldfunction", "density")), ctx), (List) ds);
        });
        overMat("SpaceAndDepth", j -> j.containsKey("layercontext") || (get(j, "layers") instanceof List<?> l && !l.isEmpty() && obj(l.get(0)) != null && obj(l.get(0)).containsKey("type")), (j, ctx, r) -> {
            List<HMaterials.Layer> layers = new ArrayList<>();
            if (get(j, "layers") instanceof List<?> l) for (Object o : l) { Map<String, Object> m = obj(o); if (m != null) layers.add(compileLayer(m, ctx, r)); }
            boolean floor = !String.valueOf(get(j, "layercontext")).toUpperCase(Locale.ROOT).contains("CEILING");
            return HMaterials.spaceAndDepth(floor, get(j, "condition") == null ? null : compileCondition(get(j, "condition")), (List) layers);
        });
    }
}