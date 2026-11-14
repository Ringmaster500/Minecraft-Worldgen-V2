package com.worldgenv2.studio.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Starter content for new projects and new assets. */
public final class Templates {
    private Templates() {}

    public static final List<String> LAYOUTS = List.of(
            "voronoi_continent", "spawn_island_landmarks", "end_rings_archipelago", "bounded_skyblock");

    public static String layoutLabel(String id) {
        return switch (id) {
            case "voronoi_continent" -> "Voronoi Continents";
            case "spawn_island_landmarks" -> "Spawn Island + Landmarks";
            case "end_rings_archipelago" -> "Concentric Rings (End)";
            case "bounded_skyblock" -> "Bounded Skyblock";
            default -> id;
        };
    }

    public static final List<String> BIOME_CATEGORIES = List.of(
            "plains", "forest", "taiga", "desert", "mountain", "tundra", "swamp", "ocean", "cave", "river", "custom");

    public static final List<String> HEIGHT_PRESETS = List.of("standard", "hytale_512", "hytale_extended", "epic_1024", "custom");

    private static final String[] SHIPPED_BIOMES = {
            "alpine_peaks", "arid_dunes", "boreal_tundra", "old_growth_grove", "sunlit_plains", "temperate_forest"};
    private static final String[] SHIPPED_PREFABS = {"stone_walkway_01.prefab", "watchtower_01.prefab"};

    public static JsonObject defaultWorld() {
        JsonObject o = new JsonObject();
        o.addProperty("format", 1);
        o.addProperty("preset", "standard");
        o.addProperty("minY", -64);
        o.addProperty("genDepth", 384);
        o.addProperty("seaLevel", 63);
        return o;
    }

    public static JsonObject newBiome(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("name", "worldgenv2:" + id);
        o.addProperty("category", "plains");
        o.addProperty("type", "TileBiome");
        o.addProperty("weight", 10);
        o.addProperty("sizeModifier", 1);
        o.addProperty("defaultTransitionDistance", 48);
        JsonObject climate = new JsonObject();
        climate.addProperty("continentalness", 0.3);
        climate.addProperty("temperature", 0);
        climate.addProperty("humidity", 0);
        o.add("climate", climate);
        JsonObject terrain = new JsonObject();
        terrain.addProperty("type", "SumDensity");
        JsonArray inputs = new JsonArray();
        JsonObject grad = new JsonObject();
        grad.addProperty("type", "VerticalGradient");
        grad.addProperty("baseHeight", 70);
        grad.addProperty("scale", 32);
        inputs.add(grad);
        terrain.add("inputs", inputs);
        o.add("terrain", terrain);
        return o;
    }

    public static JsonObject newDimension(String id, StudioProject p) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id.contains(":") ? id : "worldgenv2:" + id);
        o.addProperty("generator", "worldgenv2");
        o.addProperty("layout", "voronoi_continent");
        o.addProperty("seed", 1337);
        o.addProperty("cellSize", 192);
        JsonArray biomes = new JsonArray();
        if (p != null) for (Asset b : p.assetsOf(AssetType.BIOME)) biomes.add(poolEntry(b.id(), b.json));
        o.add("biomes", biomes);
        return o;
    }

    /** A dimension pool entry seeded from the biome's own defaults. */
    public static JsonObject poolEntry(String biomeId, JsonObject biome) {
        JsonObject e = new JsonObject();
        e.addProperty("biome", biomeId);
        e.addProperty("enabled", true);
        J.setNum(e, "weight", J.num(biome, "weight", 10));
        J.setNum(e, "size", J.num(biome, "sizeModifier", 1));
        J.setNum(e, "transition", J.num(biome, "defaultTransitionDistance", 48));
        return e;
    }

    /** Creates a fully populated starter project in memory form; caller saves it. */
    public static void populateStarter(StudioProject p) {
        for (String b : SHIPPED_BIOMES) {
            JsonObject json = readResource("data/worldgenv2/biomes/" + b + ".json");
            if (json == null) continue;
            Asset a = new Asset("biomes/" + b + ".json", AssetType.BIOME, json);
            a.dirty = true;
            p.putAsset(a);
        }
        for (String f : SHIPPED_PREFABS) {
            JsonObject json = readResource("data/worldgenv2/prefabs/" + f + ".json");
            if (json == null) continue;
            Asset a = new Asset("prefabs/" + f.replace(".prefab", "") + ".json", AssetType.PREFAB, json);
            a.dirty = true;
            p.putAsset(a);
        }
        JsonObject ow = newDimension("overworld", p);
        ow.addProperty("id", "minecraft:overworld");
        JsonObject nether = new JsonObject();
        nether.addProperty("id", "minecraft:the_nether");
        nether.addProperty("generator", "vanilla");
        nether.add("biomes", new JsonArray());
        JsonObject end = new JsonObject();
        end.addProperty("id", "minecraft:the_end");
        end.addProperty("generator", "worldgenv2");
        end.addProperty("layout", "end_rings_archipelago");
        end.addProperty("seed", 1337);
        end.addProperty("cellSize", 192);
        end.add("biomes", new JsonArray());
        for (var d : new Object[][]{{"overworld", ow}, {"the_nether", nether}, {"the_end", end}}) {
            Asset a = new Asset("dimensions/" + d[0] + ".json", AssetType.DIMENSION, (JsonObject) d[1]);
            a.dirty = true;
            p.putAsset(a);
        }
    }

    private static JsonObject readResource(String path) {
        try (InputStream in = Templates.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
