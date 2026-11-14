package com.worldgenv2.placement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.prefab.Prefab;
import com.worldgenv2.worldstructure.StructurePlacement;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The placement-rule extension point.
 *
 * <p>A <b>placement rule</b> is data (a node graph in the Studio, JSON on disk) made of three kinds of piece:
 * <ul>
 *   <li>a <b>Finder</b> looks at the terrain around a candidate point and either finds something to build on (a gap
 *       between two banks, a shoreline, just a point on the ground...) or gives up. It publishes what it found as
 *       {@link Vars} such as {@code span.length}.</li>
 *   <li><b>Conditions</b> then accept or reject the find: biome, zone, height, a density graph, a nearby structure, or any
 *       {@link Expr expression} over the variables.</li>
 *   <li><b>Actions</b> build things: place a prefab (turned, offset, fitted by an expression) relative to the find or to
 *       something an earlier action placed. If a required action cannot be satisfied, nothing at all is placed.</li>
 * </ul>
 * Everything built in is registered through the same calls an addon uses, so an addon can add a new finder, condition or
 * action in a few lines and creators immediately get it as a node in the Studio (the registry feeds the node schema):
 *
 * <pre>{@code
 * Placement.registerCondition("MoonPhase", "Only on full-moon nights", List.of(Placement.param("phase", "int", 0, "0-7")),
 *     (cfg, ctx) -> { int phase = cfg.get("phase").getAsInt(); return (env, vars) -> myMod.phaseAt(env) == phase; });
 * }</pre>
 *
 * <p>Rules are deterministic: given the world seed and terrain they place the same things whichever chunk is generated
 * first. Finders, conditions and actions must therefore be pure functions of their inputs and the {@link Env}.
 */
public final class Placement {
    private Placement() {}

    // ------------------------------------------------------------------ what a piece looks like

    /** Looks around candidate point (px, pz). Returns what it found, or null for "nothing here". */
    public interface Finder {
        Vars find(Env env, int px, int pz, SplittableRandom rng);
        /** How far from the candidate point this finder can look, in blocks (used to know which cells touch a chunk). */
        default int reach() { return 32; }
    }

    /** Accepts or rejects a find. */
    public interface Condition {
        boolean test(Env env, Vars vars);
    }

    /** Builds something. Return false when a required step cannot be satisfied (the whole rule then places nothing). */
    public interface Action {
        boolean run(Env env, Vars vars, SplittableRandom rng, List<StructurePlacement> out);
        /** How far from the find this action can place things, in blocks. */
        default int reach() { return 32; }
    }

    /** What a piece needs while it is being created from its JSON. */
    public interface Ctx {
        /** A prefab by asset id (null, with a warning, when it does not exist). */
        Prefab prefab(String id);
        /** Biome asset ids to the names the engine knows them by. */
        Set<String> biomeNames(JsonArray assetIds);
        /** Compiles a density node graph. */
        DensityFunction density(JsonObject graph);
        long seed();
        void warn(String message);
    }

    @FunctionalInterface
    public interface Factory<T> {
        T create(JsonObject config, Ctx ctx);
    }

    /** One parameter of a piece, shown as a field on its node. Types: int, number, bool, string, strings. */
    public record Param(String key, String type, Object def, String doc) {}

    /** A child node socket of a piece (for example a Density condition's {@code graph}). */
    public record Slot(String key, String kind, boolean multi) {}

    public static Param param(String key, String type, Object def, String doc) { return new Param(key, type, def, doc); }

    public static Slot slot(String key, String kind) { return new Slot(key, kind, false); }

    // ------------------------------------------------------------------ the world a piece can see

    /** Terrain probes with a per-evaluation cache, so a rule can look at hundreds of columns cheaply. */
    public static final class Env {
        public final WorldStructure ws;
        public final int minY, maxY, seaLevel;
        private final Map<Long, Integer> memo = new HashMap<>();

        public Env(WorldStructure ws, int minY, int maxY, int seaLevel) {
            this.ws = ws;
            this.minY = minY;
            this.maxY = maxY;
            this.seaLevel = seaLevel;
        }

        /** Height of the ground (top solid block) at a column, exactly as chunk generation will produce it. */
        public int surface(int x, int z) {
            long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            Integer v = memo.get(k);
            if (v == null) {
                v = ws.sampleSurfaceY(x, z, minY, maxY);
                memo.put(k, v);
            }
            return v;
        }

        /** Water level at a column: a river's own level inside its channel, otherwise sea level. */
        public int waterLevel(int x, int z) {
            int rl = ws.riverWaterLevel(x, z);
            return rl == Integer.MIN_VALUE ? seaLevel : rl;
        }

        /** True when the ground at this column lies below the local water level. */
        public boolean wet(int x, int z) {
            return surface(x, z) < waterLevel(x, z);
        }
    }

    // ------------------------------------------------------------------ registry

    private record Entry<T>(String name, String doc, List<Param> params, List<Slot> slots, Factory<T> factory) {}

    private static final Map<String, Entry<Finder>> FINDERS = new LinkedHashMap<>();
    private static final Map<String, Entry<Condition>> CONDITIONS = new LinkedHashMap<>();
    private static final Map<String, Entry<Action>> ACTIONS = new LinkedHashMap<>();
    private static boolean builtinsLoaded;

    public static synchronized void registerFinder(String name, String doc, List<Param> params, Factory<Finder> f) {
        FINDERS.put(name, new Entry<>(name, doc, params, List.of(), f));
    }

    public static synchronized void registerCondition(String name, String doc, List<Param> params, Factory<Condition> f) {
        CONDITIONS.put(name, new Entry<>(name, doc, params, List.of(), f));
    }

    public static synchronized void registerCondition(String name, String doc, List<Param> params, List<Slot> slots, Factory<Condition> f) {
        CONDITIONS.put(name, new Entry<>(name, doc, params, slots, f));
    }

    public static synchronized void registerAction(String name, String doc, List<Param> params, Factory<Action> f) {
        ACTIONS.put(name, new Entry<>(name, doc, params, List.of(), f));
    }

    private static synchronized void ensureBuiltins() {
        if (builtinsLoaded) return;
        builtinsLoaded = true;
        BuiltinPlacement.register();
    }

    public static boolean has(String kind, String type) {
        ensureBuiltins();
        return switch (kind) {
            case "finder" -> FINDERS.containsKey(type);
            case "condition" -> CONDITIONS.containsKey(type);
            case "action" -> ACTIONS.containsKey(type);
            default -> false;
        };
    }

    public static Finder createFinder(JsonObject cfg, Ctx ctx) { return create("finder", FINDERS, cfg, ctx); }

    public static Condition createCondition(JsonObject cfg, Ctx ctx) { return create("condition", CONDITIONS, cfg, ctx); }

    public static Action createAction(JsonObject cfg, Ctx ctx) { return create("action", ACTIONS, cfg, ctx); }

    private static <T> T create(String kind, Map<String, Entry<T>> map, JsonObject cfg, Ctx ctx) {
        ensureBuiltins();
        String type = cfg.has("type") ? cfg.get("type").getAsString() : "";
        Entry<T> e = map.get(type);
        if (e == null) throw new IllegalArgumentException("Unknown " + kind + " '" + type + "'");
        return e.factory().create(cfg, ctx);
    }

    /**
     * Every registered piece as node-schema JSON (the format of {@code node_schema.json}), under the kinds
     * {@code finder}, {@code condition} and {@code action}. The Studio merges this so the graph palette always matches
     * the registry, including pieces added by addons.
     */
    public static JsonObject schemaJson() {
        ensureBuiltins();
        JsonObject root = new JsonObject();
        root.add("finder", describe(FINDERS));
        root.add("condition", describe(CONDITIONS));
        root.add("action", describe(ACTIONS));
        return root;
    }

    private static <T> JsonObject describe(Map<String, Entry<T>> map) {
        JsonObject kind = new JsonObject();
        for (Entry<T> e : new ArrayList<>(map.values())) {
            JsonObject t = new JsonObject();
            JsonArray params = new JsonArray();
            for (Param p : e.params()) {
                JsonObject po = new JsonObject();
                po.addProperty("key", p.key());
                po.addProperty("t", p.type());
                if (p.def() instanceof Number n) po.addProperty("def", n);
                else if (p.def() instanceof Boolean b) po.addProperty("def", b);
                else if (p.def() != null) po.addProperty("def", String.valueOf(p.def()));
                if (p.doc() != null) po.addProperty("doc", p.doc());
                params.add(po);
            }
            t.add("params", params);
            JsonArray slots = new JsonArray();
            for (Slot s : e.slots()) {
                JsonObject so = new JsonObject();
                so.addProperty("key", s.key());
                so.addProperty("kind", s.kind());
                so.addProperty("multi", s.multi());
                slots.add(so);
            }
            t.add("slots", slots);
            if (e.doc() != null) t.addProperty("doc", e.doc());
            kind.add(e.name(), t);
        }
        return kind;
    }
}
