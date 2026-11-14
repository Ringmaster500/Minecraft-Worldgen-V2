package com.worldgenv2.studio.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.studio.graph.NodeSchemas;
import com.worldgenv2.studio.project.Asset.Severity;

/** Rules that turn "the file is valid JSON" into "the asset will actually work". */
final class Validator {
    private Validator() {}

    static void validate(StudioProject p, Asset a) {
        a.problems.clear();
        if (a.parseError != null) {
            err(a, "File is not valid JSON: " + a.parseError);
            return;
        }
        switch (a.type) {
            case WORLD -> world(a);
            case DIMENSION -> dimension(p, a);
            case BIOME -> biome(a);
            case ZONE -> zone(p, a);
            case PREFAB -> {}
        }
    }

    private static void zone(StudioProject p, Asset a) {
        JsonObject z = a.json;
        String kind = J.str(z, "kind", "everywhere");
        JsonArray biomes = J.arr(z, "biomes");
        if (biomes == null || biomes.isEmpty()) {
            if (!kind.equals("everywhere")) warn(a, "This zone has no biomes, so it restricts nothing.");
        } else {
            for (JsonElement be : biomes) {
                if (!p.hasAsset("biomes/" + be.getAsString() + ".json")) err(a, "Lists biome '" + be.getAsString() + "', which does not exist.");
            }
        }
        if (kind.equals("graph")) {
            JsonObject mask = J.obj(z, "mask");
            if (mask == null || mask.isEmpty()) warn(a, "Shaped by a graph but the Mask graph is empty; the zone matches nowhere. Open the Graph view and wire the Mask.");
            else checkNodes(a, mask, "mask");
        }
        JsonObject terrain = J.obj(z, "terrain");
        if (terrain != null) checkNodes(a, terrain, "terrain");
        if (J.num(z, "min", 0) > J.num(z, "max", 1)) warn(a, "Min is above Max, so the zone can never match.");
    }

    private static void err(Asset a, String m) { a.problems.add(new Asset.Problem(Severity.ERROR, a.path, m)); }
    private static void warn(Asset a, String m) { a.problems.add(new Asset.Problem(Severity.WARNING, a.path, m)); }

    private static void world(Asset a) {
        JsonObject o = a.json;
        int minY = J.integer(o, "minY", -64), depth = J.integer(o, "genDepth", 384), sea = J.integer(o, "seaLevel", 63);
        if (depth < 64 || depth > 4096) err(a, "World depth must be between 64 and 4096 (is " + depth + ").");
        if (minY > 0 || minY < -2048) err(a, "Min Y must be between -2048 and 0 (is " + minY + ").");
        if (minY % 16 != 0) warn(a, "Min Y should be a multiple of 16 (chunk section height).");
        if (depth % 16 != 0) warn(a, "World depth should be a multiple of 16.");
        if (sea < minY || sea > minY + depth) err(a, "Sea level " + sea + " is outside the world (" + minY + " to " + (minY + depth) + ").");
    }

    private static void dimension(StudioProject p, Asset a) {
        JsonObject o = a.json;
        if (J.str(o, "id", "").isBlank()) err(a, "Dimension has no id.");
        int cell = J.integer(o, "cellSize", 192);
        if (cell < 16 || cell > 4096) err(a, "Cell size must be between 16 and 4096 (is " + cell + ").");
        boolean native_ = "worldgenv2".equals(J.str(o, "generator", "worldgenv2"));
        JsonArray biomes = J.arr(o, "biomes");
        // Ring/skyblock layouts place their own fixed content; only continent-style layouts need a pool.
        String layout = J.str(o, "layout", "voronoi_continent");
        boolean needsPool = layout.equals("voronoi_continent") || layout.equals("spawn_island_landmarks");
        if (native_ && needsPool && (biomes == null || biomes.isEmpty())) {
            warn(a, "No biomes in this dimension's pool - nothing will generate.");
            return;
        }
        int enabled = 0;
        if (biomes != null) {
            for (JsonElement e : biomes) {
                if (!e.isJsonObject()) { err(a, "Biome pool entry is not an object."); continue; }
                JsonObject b = e.getAsJsonObject();
                String ref = J.str(b, "biome", "");
                if (J.bool(b, "enabled", true)) enabled++;
                if (!p.hasAsset("biomes/" + ref + ".json")) err(a, "Biome '" + ref + "' is in the pool but does not exist in this project.");
                if (J.num(b, "weight", 1) < 0) err(a, "Biome '" + ref + "' has a negative weight.");
            }
        }
        if (native_ && needsPool && enabled == 0) warn(a, "Every biome in the pool is disabled.");
        JsonObject spawn = J.obj(o, "spawn");
        if (spawn != null && com.worldgenv2.pack.ProjectCompiler.on(o, "spawn", J.bool(spawn, "enabled", false))) {
            String pf = J.str(spawn, "prefab", "");
            if (pf.isBlank()) warn(a, "Spawn structure is enabled but no structure is chosen.");
            else if (!p.hasAsset("prefabs/" + pf + ".json")) err(a, "Spawn structure prefab \u0027" + pf + "\u0027 does not exist in this project.");
        }
        JsonArray zonesArr = J.arr(o, "zones");
        if (zonesArr != null) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (JsonElement ze : zonesArr) {
                if (ze.isJsonPrimitive()) {
                    String zid = ze.getAsString();
                    if (!seen.add(zid)) err(a, "Zone '" + zid + "' is listed twice.");
                    if (!p.hasAsset("zones/" + zid + ".json")) err(a, "Zone '" + zid + "' is listed but does not exist.");
                    continue;
                }
                if (!ze.isJsonObject()) continue;
                JsonObject z = ze.getAsJsonObject();
                String zid = J.str(z, "id", "?");
                if (!seen.add(zid)) err(a, "Two zones are both named '" + zid + "'.");
                JsonArray zb = J.arr(z, "biomes");
                if (zb == null || zb.isEmpty()) {
                    if (!J.str(z, "kind", "everywhere").equals("everywhere")) warn(a, "Zone '" + zid + "' has no biomes, so it restricts nothing.");
                } else {
                    for (JsonElement be : zb) {
                        if (!p.hasAsset("biomes/" + be.getAsString() + ".json")) err(a, "Zone '" + zid + "' lists biome '" + be.getAsString() + "', which does not exist.");
                    }
                }
            }
        }
        JsonArray oresArr = J.arr(o, "ores");
        if (oresArr != null) {
            for (JsonElement oe : oresArr) {
                if (!oe.isJsonObject()) continue;
                JsonObject ore = oe.getAsJsonObject();
                String oid = J.str(ore, "id", "?"), block = J.str(ore, "block", "");
                if (block.isBlank()) err(a, "Ore '" + oid + "' has no block.");
                else if (com.worldgenv2.minecraft.WorldGenV2ChunkGenerator.resolveBlockState(block, null) == null) warn(a, "Ore '" + oid + "' uses '" + block + "', which is not a registered block (is the mod installed?).");
                if (J.integer(ore, "minY", 0) > J.integer(ore, "maxY", 0)) warn(a, "Ore '" + oid + "' has Min Y above Max Y.");
            }
        }
        JsonArray spansArr = J.arr(o, "spans");
        if (spansArr != null && com.worldgenv2.pack.ProjectCompiler.on(o, "spans", !spansArr.isEmpty())) {
            for (JsonElement e : spansArr) {
                if (!e.isJsonObject()) continue;
                JsonObject s = e.getAsJsonObject();
                String nm = J.str(s, "id", "?");
                JsonArray pf = J.arr(s, "prefabs");
                if (pf == null || pf.isEmpty()) { warn(a, "Bridge '" + nm + "' has no structures chosen, so nothing is placed."); }
                else for (JsonElement pe : pf) {
                    if (!p.hasAsset("prefabs/" + pe.getAsString() + ".json")) err(a, "Bridge '" + nm + "' uses prefab '" + pe.getAsString() + "', which does not exist.");
                }
                if (J.integer(s, "minGap", 4) > J.integer(s, "maxGap", 14)) warn(a, "Bridge '" + nm + "' has Min gap above Max gap, so it can never be placed.");
            }
        }
        for (String listKey : new String[]{"landmarks", "budgets"}) {
            JsonArray lst = J.arr(o, listKey);
            if (lst == null) continue;
            for (JsonElement e : lst) {
                if (!e.isJsonObject()) continue;
                JsonObject item = e.getAsJsonObject();
                String pf = J.str(item, "prefab", "");
                String nm = J.str(item, "id", "?");
                if (pf.isBlank()) warn(a, "\u0027" + nm + "\u0027 has no structure chosen.");
                else if (!p.hasAsset("prefabs/" + pf + ".json")) err(a, "\u0027" + nm + "\u0027 uses prefab \u0027" + pf + "\u0027, which does not exist.");
            }
        }
    }

    private static void biome(Asset a) {
        JsonObject o = a.json;
        if (J.str(o, "name", "").isBlank()) err(a, "Biome has no name.");
        if (J.num(o, "weight", 1) <= 0) err(a, "Weight must be greater than 0.");
        if (J.num(o, "sizeModifier", 1) <= 0) err(a, "Size modifier must be greater than 0.");
        if (J.num(o, "defaultTransitionDistance", 32) < 0) err(a, "Transition distance cannot be negative.");
        JsonObject climate = J.obj(o, "climate");
        if (climate != null) {
            for (String k : new String[]{"continentalness", "temperature", "humidity"}) {
                double v = J.num(climate, k, 0);
                if (v < -1 || v > 1) warn(a, "Climate '" + k + "' = " + v + " is outside the -1..1 range.");
            }
        }
        JsonObject terrain = J.obj(o, "terrain");
        if (terrain == null) warn(a, "No terrain graph - this biome will be flat.");
        else checkNodes(a, terrain, "terrain");
    }

    private static void checkNodes(Asset a, JsonElement el, String where) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            String type = J.str(o, "type", null);
            if (type != null && !NodeSchemas.known(type)) {
                warn(a, "Unknown node type '" + type + "' at " + where + ".");
            }
            for (var e : o.entrySet()) checkNodes(a, e.getValue(), where + "." + e.getKey());
        } else if (el.isJsonArray()) {
            int i = 0;
            for (JsonElement c : el.getAsJsonArray()) checkNodes(a, c, where + "[" + (i++) + "]");
        }
    }
}
