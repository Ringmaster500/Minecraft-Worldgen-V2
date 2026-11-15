package com.worldgenv2;

import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.biome.HytaleDialect;
import com.worldgenv2.biome.HytaleImport;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.biome.WorldGenV2Registries;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Dev probe: compiles every real Hytale V2 asset (hytale_local/, git-ignored) with our compilers and groups the failures. */
public final class HytaleAssetConformanceProbe {

    /** Removes the node editor's $-keys in place, keeping the parser's own (case-insensitive) maps. */
    static Object strip(Object o) {
        if (o instanceof Map<?, ?> m) {
            m.keySet().removeIf(k -> String.valueOf(k).startsWith("$"));
            for (Object v : m.values()) strip(v);
        } else if (o instanceof List<?> l) {
            for (Object x : l) strip(x);
        }
        return o;
    }

    static void walk(Object o, String file, Map<String, int[]> counts, Map<String, Set<String>> keys, Map<String, String> where) {
        if (o instanceof Map<?, ?> m) {
            Object type = null;
            for (var e : m.entrySet()) if (String.valueOf(e.getKey()).equalsIgnoreCase("type")) type = e.getValue();
            if (type instanceof String s) {
                counts.computeIfAbsent(s, k -> new int[1])[0]++;
                where.putIfAbsent(s, file);
                Set<String> ks = keys.computeIfAbsent(s, k -> new TreeSet<>());
                for (Object k : m.keySet()) ks.add(String.valueOf(k));
            }
            for (Object v : m.values()) walk(v, file, counts, keys, where);
        } else if (o instanceof List<?> l) {
            for (Object x : l) walk(x, file, counts, keys, where);
        }
    }
    static void collectUnread(Object o, String parentKey, String file, Map<String, int[]> out, Map<String, String> where) {
        if (o instanceof JsonAstCompiler.CaseInsensitiveMap m) {
            Object type = m.get("Type");
            String label = type instanceof String s ? s : "(" + parentKey + ")";
            for (String k : m.unreadKeys()) {
                if (k.equalsIgnoreCase("Type") || k.equalsIgnoreCase("Skip") || k.equalsIgnoreCase("ExportAs") || k.equalsIgnoreCase("SingleInstance")) continue;
                String key = label + "." + k;
                out.computeIfAbsent(key, x -> new int[1])[0]++;
                where.putIfAbsent(key, file);
            }
            for (var e : m.entrySet()) collectUnread(e.getValue(), e.getKey(), file, out, where);
        } else if (o instanceof List<?> l) {
            for (Object x : l) collectUnread(x, parentKey, file, out, where);
        }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        JsonAstCompiler.registerBuiltInTypes();
        Path root = Path.of("hytale_local/Server/HytaleGenerator");
        Map<String, Map<String, Object>> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".json"))::iterator) {
                try { files.put(root.relativize(p).toString().replace('\\', '/'), (Map<String, Object>) strip(new JsonAstCompiler.SimpleJsonParser(Files.readString(p)).parseValue())); }
                catch (Throwable t) { System.out.println("CONF PARSE-FAIL " + p + " " + t); }
            }
        }
        JsonAstCompiler.CaseInsensitiveMap.trackReads = true;
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L);
        for (var e : files.entrySet()) JsonAstCompiler.collectExports(e.getValue(), ctx);
        System.out.println("CONF files " + files.size() + " exports " + ctx.exports().size());
        // ---- inventory: every Type used by the real assets, whether any of our registries knows it, and the keys it carries
        Map<String, int[]> counts = new TreeMap<>();
        Map<String, Set<String>> keys = new TreeMap<>();
        Map<String, String> where = new TreeMap<>();
        for (var e : files.entrySet()) walk(e.getValue(), e.getKey(), counts, keys, where);
        Object[] regs = {WorldGenV2Registries.DENSITY_NODE_TYPE, WorldGenV2Registries.CURVE_TYPE, WorldGenV2Registries.VECTOR_PROVIDER_TYPE, WorldGenV2Registries.MATERIAL_PROVIDER_TYPE,
                WorldGenV2Registries.PATTERN_TYPE, WorldGenV2Registries.PROP_TYPE, WorldGenV2Registries.POSITIONS_PROVIDER_TYPE, WorldGenV2Registries.SCANNER_TYPE, WorldGenV2Registries.ASSIGNMENTS_TYPE};
        Set<String> handledByDialect = Set.of("Floor", "Ceiling", "Wall", "Cuboid", "BlockSet", "BlockType", "Offset", "And", "Or", "Not", "Surface", "Rotator", "FieldFunction", "Constant", "Static", "Positioned");
        for (var c : counts.entrySet()) {
            boolean known = false;
            for (Object r : regs) { try { if (r.getClass().getMethod("get", String.class).invoke(r, c.getKey()) != null) { known = true; break; } } catch (Exception ex) { } }
            if (!known) System.out.println("CONF UNKNOWN-TYPE " + c.getKey() + " x" + c.getValue()[0] + " keys " + keys.get(c.getKey()) + " e.g. " + where.get(c.getKey()));
        }
        System.out.println("CONF types seen " + counts.size());        Map<String, List<String>> failures = new TreeMap<>();
        int ok = 0, total = 0;
        for (var e : files.entrySet()) {
            String path = e.getKey(), dir = path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
            Object v = e.getValue();
            try {
                total++;
                switch (dir) {
                    case "Density" -> JsonAstCompiler.compileDensity((Map<String, Object>) v, ctx);
                    case "Assignments" -> JsonAstCompiler.compileAssignments(v, ctx, id -> id);
                    case "Positions" -> JsonAstCompiler.compilePositionsProvider(v, ctx);
                    case "Props" -> JsonAstCompiler.compileProp((Map<String, Object>) v, ctx, id -> id);
                    case "PropDistributions" -> HytaleDialect.compileDistribution(v, ctx, id -> id);
                    case "Biomes" -> { var form = HytaleImport.biomeToEngineForm((Map<String, Object>) v); if (form.containsKey("terrain") || form.containsKey("props")) JsonAstCompiler.compileBiome(2, form, ctx, new HashMap<>(), id -> id); else { total--; continue; } }
                    case "BlockMasks" -> JsonAstCompiler.compileBlockMask(v, ctx, id -> id);
                    default -> { total--; continue; }
                }
                ok++;
            } catch (Throwable t) {
                if (failures.isEmpty() || Math.random() < 0.0) { }
                String msg = String.valueOf(t.getMessage()).split("\n")[0];
                String key0 = t.getClass().getSimpleName() + ": " + msg.substring(0, Math.min(150, msg.length()));
                if (!failures.containsKey(key0)) { StringBuilder st = new StringBuilder(); for (int i = 0; i < Math.min(7, t.getStackTrace().length); i++) st.append("\\n      at ").append(t.getStackTrace()[i]); System.out.println("CONF TRACE " + path + st); }
                failures.computeIfAbsent(t.getClass().getSimpleName() + ": " + msg.substring(0, Math.min(150, msg.length())), k -> new ArrayList<>()).add(path);
            }
        }
        // ---- macro check: does every real biome's terrain density make a surface? (Base/Water/Bedrock as in the Zone world structures)
        ctx.decimalConstants().put("Base", 110.0);
        ctx.decimalConstants().put("Water", 100.0);
        ctx.decimalConstants().put("Bedrock", 0.0);
        for (var e : files.entrySet()) {
            if (!e.getKey().startsWith("Biomes/")) continue;
            Object v = e.getValue();
            var form = HytaleImport.biomeToEngineForm((Map<String, Object>) v);
            if (!form.containsKey("terrain")) continue;
            try {
                var cb = JsonAstCompiler.<String>compileBiome(2, form, ctx, new HashMap<>(), id -> id);
                var d = cb.tileBiome().terrainDensity();
                StringBuilder sb = new StringBuilder();
                for (int[] pt : new int[][]{{0, 0}, {500, 300}, {-900, 1200}}) {
                    int top = -1, flips = 0; boolean prev = false, nan = false;
                    for (int y = 0; y < 320; y++) { float dv = d.compute(pt[0], y, pt[1]); if (Float.isNaN(dv)) nan = true; boolean solid = dv > 0; if (y > 0 && solid != prev) flips++; if (solid) top = y; prev = solid; }
                    sb.append(" (" + pt[0] + "," + pt[1] + ") top=" + top + " flips=" + flips + (nan ? " NaN!" : ""));
                }
                System.out.println("CONF SURFACE " + e.getKey().replace("Biomes/", "") + sb);
            } catch (Throwable t) { System.out.println("CONF SURFACE-FAIL " + e.getKey() + " " + t); }
        }        // keys that real assets carry and no compiler ever asked for: the likeliest shape mismatches
        Map<String, int[]> unread = new TreeMap<>();
        Map<String, String> unreadWhere = new TreeMap<>();
        for (var e : files.entrySet()) {
            String d0 = e.getKey().contains("/") ? e.getKey().substring(0, e.getKey().indexOf('/')) : "";
            if (d0.equals("Graphs") || d0.equals("Settings")) continue;
            collectUnread(e.getValue(), "", e.getKey(), unread, unreadWhere);
        }
        for (var u : unread.entrySet()) if (u.getValue()[0] > 0) System.out.println("CONF UNREAD " + u.getKey() + " x" + u.getValue()[0] + " e.g. " + unreadWhere.get(u.getKey()));
        System.out.println("CONF compiled " + ok + " of " + total);
        for (var f : failures.entrySet()) System.out.println("CONF FAIL x" + f.getValue().size() + " " + f.getKey() + "  e.g. " + f.getValue().get(0));
    }
}