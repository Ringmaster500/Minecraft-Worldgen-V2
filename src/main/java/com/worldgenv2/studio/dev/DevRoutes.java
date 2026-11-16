package com.worldgenv2.studio.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.shell.StudioApp;

import java.util.Map;

/**
 * The dev bridge's routes for driving the Studio without clicking: read and write assets, list and read problems, compile and see
 * what the compiler says, import biomes, delete, rename, undo, live preview and the guide. Everything returns JSON.
 *
 * <pre>
 *   GET /assets                          -> every asset: path, type, dirty, problem counts
 *   GET /asset?path=biomes/x.json        -> the asset's JSON (also: parseError)
 *   GET|POST /put?path=&json=            -> replace an asset's whole JSON (creates it if missing); POST body works too
 *   GET /set?path=&key=a.b.0.c&value=    -> set one value by dotted path (value is JSON; numbers index arrays; parents are created)
 *   GET /unset?path=&key=a.b             -> remove a key or array element
 *   GET /problems                        -> every error and warning in the open setup
 *   GET /compile?dim=overworld           -> compile the dimension and report warnings, biomes and sea level
 *   GET /delete?path=  /rename?path=&name=  /undo  /redo
 *   GET /importbiome?source=preset:ID/biome | hytale:Name &name=   -> add a ready-made biome
 *   GET /live?on=1&radius=3   GET /guide?open=1&page=ID   GET /blocks?ns=powah&like=glass  -> block ids the game knows (no filter: block count per mod)
 * </pre>
 */
final class DevRoutes {
    private DevRoutes() {}

    private static StudioApp app() { return StudioApp.get(); }

    private static String err(String m) {
        JsonObject o = new JsonObject();
        o.addProperty("error", m);
        return o.toString();
    }

    private static String ok(JsonObject o) { o.addProperty("ok", true); return o.toString(); }

    /** @return the JSON answer, or null when the path is not one of these routes. */
    static String handle(String path, Map<String, String> q) throws Exception {
        return switch (path) {
            case "/assets" -> DevBridge.onRender(DevRoutes::assets);
            case "/asset" -> DevBridge.onRender(() -> asset(q));
            case "/put" -> DevBridge.onRender(() -> put(q));
            case "/set" -> DevBridge.onRender(() -> set(q, false));
            case "/unset" -> DevBridge.onRender(() -> set(q, true));
            case "/problems" -> DevBridge.onRender(DevRoutes::problems);
            case "/compile" -> compile(q);
            case "/delete" -> DevBridge.onRender(() -> {
                Asset a = find(q);
                if (a == null) return err("no such asset");
                app().project.delete(a);
                app().closeDoc(a.path);
                app().uiRevision++;
                return ok(new JsonObject());
            });
            case "/rename" -> DevBridge.onRender(() -> {
                Asset a = find(q);
                if (a == null) return err("no such asset");
                String problem = app().project.checkNewId(a.type, q.getOrDefault("name", ""));
                if (problem != null) return err(problem);
                app().renameAsset(a, q.get("name"));
                return ok(new JsonObject());
            });
            case "/undo" -> DevBridge.onRender(() -> { app().undo(); return ok(new JsonObject()); });
            case "/redo" -> DevBridge.onRender(() -> { app().redo(); return ok(new JsonObject()); });
            case "/importbiome" -> DevBridge.onRender(() -> importBiome(q));
            case "/blocks" -> DevBridge.onRender(() -> {
                String ns = q.getOrDefault("ns", "");
                String like = q.getOrDefault("like", "");
                JsonArray ids = new JsonArray();
                java.util.TreeMap<String, Integer> perNamespace = new java.util.TreeMap<>();
                for (var id : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
                    perNamespace.merge(id.getNamespace(), 1, Integer::sum);
                    if ((ns.isEmpty() || id.getNamespace().equals(ns)) && (like.isEmpty() || id.getPath().contains(like)) && (!ns.isEmpty() || !like.isEmpty())) ids.add(id.toString());
                }
                JsonObject o = new JsonObject();
                JsonObject counts = new JsonObject();
                perNamespace.forEach(counts::addProperty);
                o.add("namespaces", counts);
                o.add("blocks", ids);
                return o.toString();
            });
            case "/live" -> DevBridge.onRender(() -> {
                if (q.containsKey("radius")) app().liveRadius = Math.max(1, Math.min(6, (int) DevBridge.d(q, "radius")));
                if (q.containsKey("on")) app().setLive(DevBridge.d(q, "on") != 0);
                JsonObject o = new JsonObject();
                o.addProperty("live", app().live);
                o.addProperty("radius", app().liveRadius);
                return ok(o);
            });
            case "/guide" -> DevBridge.onRender(() -> {
                if (q.containsKey("page")) app().guidePage = q.get("page");
                if (q.containsKey("open")) app().guideOpen = DevBridge.d(q, "open") != 0;
                return ok(new JsonObject());
            });
            default -> null;
        };
    }

    private static Asset find(Map<String, String> q) {
        return app().project == null ? null : app().project.get(q.getOrDefault("path", ""));
    }

    private static String assets() {
        if (app().project == null) return err("no setup open");
        JsonArray arr = new JsonArray();
        for (Asset a : app().project.assets()) {
            JsonObject o = new JsonObject();
            o.addProperty("path", a.path);
            o.addProperty("type", a.type.name());
            o.addProperty("dirty", a.dirty);
            int errors = 0, warnings = 0;
            for (Asset.Problem p : a.allProblems()) { if (p.severity() == Asset.Severity.ERROR) errors++; else if (p.severity() == Asset.Severity.WARNING) warnings++; }
            o.addProperty("errors", errors);
            o.addProperty("warnings", warnings);
            if (a.parseError != null) o.addProperty("parseError", a.parseError);
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("setup", app().project.name);
        out.add("assets", arr);
        return out.toString();
    }

    private static String asset(Map<String, String> q) {
        Asset a = find(q);
        if (a == null) return err("no such asset");
        JsonObject o = new JsonObject();
        o.addProperty("path", a.path);
        o.addProperty("type", a.type.name());
        o.addProperty("dirty", a.dirty);
        if (a.parseError != null) { o.addProperty("parseError", a.parseError); o.addProperty("raw", a.rawText); }
        else o.add("json", a.json);
        return o.toString();
    }

    private static JsonElement parseBody(Map<String, String> q) {
        String text = q.containsKey("body") ? q.get("body") : q.get("json");
        if (text == null) throw new IllegalArgumentException("no json given (use json= or a POST body)");
        return JsonParser.parseString(text);
    }

    private static String put(Map<String, String> q) {
        if (app().project == null) return err("no setup open");
        String path = q.getOrDefault("path", "");
        AssetType t = AssetType.ofPath(path);
        if (t == null) return err("path must be world.json or <folder>/<name>.json for dimensions, zones, placements, biomes or prefabs");
        JsonElement el = parseBody(q);
        if (!el.isJsonObject()) return err("json must be an object");
        JsonObject next = el.getAsJsonObject();
        Asset a = app().project.get(path);
        if (a == null) {
            String id = path.substring(path.lastIndexOf('/') + 1);
            a = app().project.create(t, id.endsWith(".json") ? id.substring(0, id.length() - 5) : id);
        }
        app().project.edit(a, "Dev put", j -> {
            for (String k : new java.util.ArrayList<>(j.keySet())) j.remove(k);
            next.entrySet().forEach(e -> j.add(e.getKey(), e.getValue()));
        });
        app().uiRevision++;
        return ok(new JsonObject());
    }

    /** Dotted-path set/unset: "terrain.inputs.0.amplitude". */
    private static String set(Map<String, String> q, boolean remove) {
        Asset a = find(q);
        if (a == null) return err("no such asset");
        String key = q.getOrDefault("key", "");
        if (key.isEmpty()) return err("key is empty");
        JsonElement value = remove ? null : JsonParser.parseString(q.containsKey("body") ? q.get("body") : q.getOrDefault("value", "null"));
        String[] seg = key.split("\\.");
        String[] fail = new String[1];
        app().project.edit(a, remove ? "Dev unset" : "Dev set", j -> {
            JsonElement cur = j;
            for (int i = 0; i < seg.length - 1; i++) {
                JsonElement next = child(cur, seg[i]);
                if (next == null || next.isJsonNull() || !(next.isJsonObject() || next.isJsonArray())) {
                    if (remove) { fail[0] = "path not found at '" + seg[i] + "'"; return; }
                    next = isIndex(seg[i + 1]) ? new JsonArray() : new JsonObject();
                    if (!store(cur, seg[i], next)) { fail[0] = "cannot create '" + seg[i] + "'"; return; }
                }
                cur = next;
            }
            String last = seg[seg.length - 1];
            if (remove) {
                if (cur.isJsonObject()) cur.getAsJsonObject().remove(last);
                else if (cur.isJsonArray() && isIndex(last) && Integer.parseInt(last) < cur.getAsJsonArray().size()) cur.getAsJsonArray().remove(Integer.parseInt(last));
                else fail[0] = "nothing at '" + last + "'";
            } else if (!store(cur, last, value)) fail[0] = "cannot set '" + last + "'";
        });
        app().uiRevision++;
        return fail[0] != null ? err(fail[0]) : ok(new JsonObject());
    }

    private static boolean isIndex(String s) { return !s.isEmpty() && s.chars().allMatch(Character::isDigit); }

    private static JsonElement child(JsonElement e, String seg) {
        if (e.isJsonObject()) return e.getAsJsonObject().get(seg);
        if (e.isJsonArray() && isIndex(seg)) { int i = Integer.parseInt(seg); return i < e.getAsJsonArray().size() ? e.getAsJsonArray().get(i) : null; }
        return null;
    }

    private static boolean store(JsonElement parent, String seg, JsonElement v) {
        if (parent.isJsonObject()) { parent.getAsJsonObject().add(seg, v); return true; }
        if (parent.isJsonArray() && isIndex(seg)) {
            JsonArray arr = parent.getAsJsonArray();
            int i = Integer.parseInt(seg);
            while (arr.size() <= i) arr.add(com.google.gson.JsonNull.INSTANCE);
            arr.set(i, v);
            return true;
        }
        return false;
    }

    private static String problems() {
        if (app().project == null) return err("no setup open");
        JsonArray arr = new JsonArray();
        for (Asset.Problem p : app().project.allProblems()) {
            JsonObject o = new JsonObject();
            o.addProperty("severity", p.severity().name());
            o.addProperty("asset", p.assetPath());
            o.addProperty("message", p.message());
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("errors", app().project.count(Asset.Severity.ERROR));
        out.addProperty("warnings", app().project.count(Asset.Severity.WARNING));
        out.add("problems", arr);
        return out.toString();
    }

    /** Compiles a dimension exactly as the game does and reports what the compiler says; this is where "nothing generated" gets explained. */
    private static String compile(Map<String, String> q) {
        var project = app().project;
        if (project == null) return err("no setup open");
        String dim = q.getOrDefault("dim", "overworld");
        long t0 = System.currentTimeMillis();
        try {
            var res = com.worldgenv2.pack.ProjectCompiler.compile(project, dim, q.containsKey("seed") ? (long) DevBridge.d(q, "seed") : Long.MIN_VALUE);
            JsonObject o = new JsonObject();
            o.addProperty("dimension", dim);
            o.addProperty("compiled", res.structure != null);
            o.addProperty("ms", System.currentTimeMillis() - t0);
            o.addProperty("seaLevel", res.seaLevel);
            o.addProperty("minY", res.minY);
            o.addProperty("maxY", res.maxY);
            JsonArray w = new JsonArray();
            for (String s : res.warnings) w.add(s);
            o.add("warnings", w);
            JsonArray bs = new JsonArray();
            for (var b : res.biomes) {
                JsonObject bo = new JsonObject();
                bo.addProperty("id", b.id());
                bo.addProperty("asset", b.assetId());
                bo.addProperty("name", b.name());
                bo.addProperty("minecraftBiome", b.minecraftBiome());
                bs.add(bo);
            }
            o.add("biomes", bs);
            return o.toString();
        } catch (Throwable t) {
            return err("compile threw: " + t);
        }
    }

    private static String importBiome(Map<String, String> q) {
        if (app().project == null) return err("no setup open");
        String src = q.getOrDefault("source", "");
        try {
            if (src.startsWith("preset:")) {
                String rest = src.substring(7);
                int slash = rest.lastIndexOf('/');
                if (slash < 0) return err("use preset:<presetId>/<biomeId>");
                var preset = com.worldgenv2.pack.WorldGenV2Addons.preset(rest.substring(0, slash));
                if (preset == null) return err("no such preset");
                var assets = com.worldgenv2.studio.project.J.obj(preset.bundle().get(), "assets");
                JsonElement b = assets == null ? null : assets.get("biomes/" + rest.substring(slash + 1) + ".json");
                if (b == null || !b.isJsonObject()) return err("that preset has no such biome");
                Asset a = app().importBiome(q.getOrDefault("name", rest.substring(slash + 1)), b.getAsJsonObject());
                JsonObject o = new JsonObject();
                o.addProperty("path", a.path);
                return ok(o);
            }
            if (src.startsWith("hytale:")) {
                String stem = src.substring(7);
                String assetsPath = q.getOrDefault("assets", com.worldgenv2.hytale.HytaleAssets.defaultAssetsPath());
                if (assetsPath == null) return err("no Hytale install found; pass assets=<path to Assets.zip>");
                try (var pack = com.worldgenv2.hytale.HytaleAssets.open(assetsPath)) {
                    String file = pack.biomePath(stem);
                    if (file == null) return err("no Hytale biome named " + stem);
                    Asset a = app().importBiome(q.getOrDefault("name", stem), JsonParser.parseString(pack.generatorText(file)).getAsJsonObject());
                    JsonObject o = new JsonObject();
                    o.addProperty("path", a.path);
                    return ok(o);
                }
            }
            return err("source must start with preset: or hytale:");
        } catch (Exception e) {
            return err(String.valueOf(e));
        }
    }
}
