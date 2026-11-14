package com.worldgenv2.studio.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.studio.StudioNodeCatalog;
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
            case PREFAB -> {}
        }
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
            if (type != null && StudioNodeCatalog.getSchema(type) == null) {
                warn(a, "Unknown node type '" + type + "' at " + where + ".");
            }
            for (var e : o.entrySet()) checkNodes(a, e.getValue(), where + "." + e.getKey());
        } else if (el.isJsonArray()) {
            int i = 0;
            for (JsonElement c : el.getAsJsonArray()) checkNodes(a, c, where + "[" + (i++) + "]");
        }
    }
}
