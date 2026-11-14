package com.worldgenv2.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.worldgenv2.pack.WorldGenV2Addons.Preset;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.studio.project.Templates;

import java.util.function.Consumer;

/**
 * Presets that ship with Worldgen V2. They go through the same registry as addon presets, so there is nothing
 * special about them: they are starting points, and "custom" (Blank) is the intended path.
 */
final class BuiltinPresets {
    private BuiltinPresets() {}

    static void register() {
        WorldGenV2Addons.registerPreset(new Preset("worldgenv2:blank", "Blank",
                "One plain biome and the three dimensions. Nothing else: build everything yourself.", "world",
                () -> bundle("worldgenv2:blank", "Blank", p -> {
                    Asset biome = new Asset("biomes/plains.json", AssetType.BIOME, Templates.newBiome("plains"));
                    p.putAsset(biome);
                    JsonObject ow = Templates.newDimension("overworld", p);
                    ow.addProperty("id", "minecraft:overworld");
                    ow.add("ores", new JsonArray());
                    p.putAsset(new Asset("dimensions/overworld.json", AssetType.DIMENSION, ow));
                    addVanilla(p, "the_nether", "minecraft:the_nether");
                    addVanilla(p, "the_end", "minecraft:the_end");
                })));

        WorldGenV2Addons.registerPreset(new Preset("worldgenv2:starter", "Starter",
                "Six example biomes with ores, two prefabs, Overworld / Nether / End.", "world",
                () -> bundle("worldgenv2:starter", "Starter", Templates::populateStarter)));

        WorldGenV2Addons.registerPreset(new Preset("worldgenv2:four_zones", "Four Zones",
                "The starter biomes split into four Hytale-style macro zones (Emerald Grove, Howling Sands, Boreal Reach, Shattered Peaks).", "world",
                () -> bundle("worldgenv2:four_zones", "Four Zones", p -> {
                    Templates.populateStarter(p);
                    Asset ow = p.get("dimensions/overworld.json");
                    Templates.addDemoZones(ow.json);
                })));

        WorldGenV2Addons.registerPreset(new Preset("worldgenv2:spawn_island", "Spawn Island",
                "Start on an island with a spawn structure and two connected outposts, surrounded by ocean.", "world",
                () -> bundle("worldgenv2:spawn_island", "Spawn Island", p -> {
                    Templates.populateStarter(p);
                    Asset ow = p.get("dimensions/overworld.json");
                    Templates.applyLayout(ow.json, "spawn_island_landmarks", p);
                    JsonObject spawn = new JsonObject();
                    spawn.addProperty("enabled", true);
                    spawn.addProperty("prefab", "watchtower_01");
                    J.setNum(spawn, "x", 0);
                    J.setNum(spawn, "z", 0);
                    spawn.addProperty("lockSpawn", true);
                    ow.json.add("spawn", spawn);
                    JsonArray lms = new JsonArray();
                    for (String[] o : new String[][]{{"north_outpost", "N"}, {"east_outpost", "E"}}) {
                        JsonObject l = new JsonObject();
                        l.addProperty("id", o[0]);
                        l.addProperty("prefab", "watchtower_01");
                        l.addProperty("parent", "spawn");
                        J.setNum(l, "minDistance", 90);
                        J.setNum(l, "maxDistance", 160);
                        l.addProperty("direction", o[1]);
                        l.addProperty("road", true);
                        J.setNum(l, "roadWidth", 5);
                        J.setNum(l, "shoulderWidth", 3);
                        l.addProperty("faceParent", true);
                        l.add("biomes", new JsonArray());
                        lms.add(l);
                    }
                    ow.json.add("landmarks", lms);
                })));
    }

    private static void addVanilla(StudioProject p, String file, String id) {
        JsonObject d = new JsonObject();
        d.addProperty("id", id);
        d.addProperty("generator", "vanilla");
        d.add("biomes", new JsonArray());
        p.putAsset(new Asset("dimensions/" + file + ".json", AssetType.DIMENSION, d));
    }

    private static JsonObject bundle(String id, String name, Consumer<StudioProject> fill) {
        StudioProject p = StudioProject.fromBundle(name, new JsonObject());
        fill.accept(p);
        return p.toBundle(id, name, "", "world");
    }
}
