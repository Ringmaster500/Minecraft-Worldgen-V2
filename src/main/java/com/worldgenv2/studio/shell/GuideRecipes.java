package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Teaching setups: small, working biomes with the reasoning written next to them. Each can be added to the open world setup with one
 * button, then opened in the graph to see every node and change it. They are built from the same nodes the guide documents.
 */
final class GuideRecipes {
    private GuideRecipes() {}

    record Recipe(String id, String title, List<String> lines, JsonObject biome) {}

    private static JsonObject o(String type) { JsonObject j = new JsonObject(); j.addProperty("type", type); return j; }

    private static JsonObject gradient(double base, double scale) {
        JsonObject g = o("VerticalGradient");
        g.addProperty("baseHeight", base);
        g.addProperty("scale", scale);
        return g;
    }

    private static JsonObject noise2d(double frequency, int octaves, double amplitude) {
        JsonObject n = o("Noise2dDensity");
        n.addProperty("noiseType", "OpenSimplex2");
        n.addProperty("frequency", frequency);
        n.addProperty("octaves", octaves);
        n.addProperty("amplitude", amplitude);
        return n;
    }

    private static JsonObject sum(JsonObject... parts) {
        JsonObject s = o("SumDensity");
        JsonArray in = new JsonArray();
        for (JsonObject p : parts) in.add(p);
        s.add("inputs", in);
        return s;
    }

    private static JsonObject layer(String material, int thickness) {
        JsonObject l = new JsonObject();
        l.addProperty("material", material);
        l.addProperty("thickness", thickness);
        return l;
    }

    /** Surface layers on gentle ground, rock on steep ground, stone below: the shape every shipped biome uses. */
    private static JsonObject materials(String top, int topThick, String under, int underThick, String cliff) {
        JsonObject q = o("QueueMaterialProvider");
        JsonArray providers = new JsonArray();

        JsonObject cover = new JsonObject();
        JsonObject and1 = o("AndPattern");
        JsonArray ps = new JsonArray();
        ps.add(o("SurfacePattern"));
        JsonObject slope = o("SlopePattern");
        slope.addProperty("maxAngleDegrees", 45.0);
        ps.add(slope);
        JsonObject space = o("SpaceCheckPattern");
        space.addProperty("minAirAbove", 2);
        ps.add(space);
        and1.add("patterns", ps);
        cover.add("pattern", and1);
        JsonObject sd = o("SpaceAndDepth");
        JsonArray layers = new JsonArray();
        layers.add(layer(top, topThick));
        layers.add(layer(under, underThick));
        layers.add(layer("minecraft:stone", 999));
        sd.add("layers", layers);
        sd.addProperty("fallbackMaterial", "minecraft:stone");
        sd.addProperty("cliffMaterial", cliff);
        sd.addProperty("maxSlopeForCover", 45.0);
        sd.addProperty("minSpaceForCover", 2);
        cover.add("provider", sd);
        providers.add(cover);

        JsonObject rock = new JsonObject();
        JsonObject and2 = o("AndPattern");
        JsonArray ps2 = new JsonArray();
        ps2.add(o("SurfacePattern"));
        JsonObject steep = o("SlopePattern");
        steep.addProperty("minAngleDegrees", 45.0);
        ps2.add(steep);
        and2.add("patterns", ps2);
        rock.add("pattern", and2);
        JsonObject weighted = o("WeightedMaterialProvider");
        JsonArray ws = new JsonArray();
        JsonObject a = new JsonObject(); a.addProperty("material", "minecraft:stone"); a.addProperty("weight", 70.0); ws.add(a);
        JsonObject b = new JsonObject(); b.addProperty("material", cliff); b.addProperty("weight", 30.0); ws.add(b);
        weighted.add("weights", ws);
        rock.add("provider", weighted);
        providers.add(rock);

        q.add("providers", providers);
        JsonObject fb = o("ConstantMaterialProvider");
        fb.addProperty("material", "minecraft:stone");
        q.add("fallback", fb);
        return q;
    }

    private static JsonObject biome(String id, String category, JsonObject terrain, JsonObject mats, double continentalness, double temperature, double humidity) {
        JsonObject b = new JsonObject();
        b.addProperty("name", "worldgenv2:" + id);
        b.addProperty("category", category);
        b.addProperty("type", "TileBiome");
        b.addProperty("weight", 10);
        b.addProperty("sizeModifier", 1);
        b.addProperty("defaultTransitionDistance", 48);
        JsonObject c = new JsonObject();
        c.addProperty("continentalness", continentalness);
        c.addProperty("temperature", temperature);
        c.addProperty("humidity", humidity);
        b.add("climate", c);
        b.add("terrain", terrain);
        b.add("materialProvider", mats);
        return b;
    }

    static List<Recipe> all() {
        List<Recipe> r = new ArrayList<>();

        r.add(new Recipe("flat_grass", "1. Flat grassland", List.of(
                "# What you get",
                "A perfectly flat plain: grass on top, dirt under it, stone below. It is the smallest useful biome, so it is the best one to read first.",
                "# The terrain",
                "One VerticalGradient node. It gives a positive number below Base Y and a negative one above it, so the ground is a flat sheet at that height. Base Y is 70, so the surface sits at Y 70. Scale controls how quickly it turns from solid to air: a small scale makes a hard edge.",
                "# The materials",
                "A QueueMaterialProvider tries its rules from the top. The first rule says: on the surface, on ground gentler than 45 degrees, with two blocks of air above, use SpaceAndDepth. SpaceAndDepth builds the layers downward: 1 block of grass, 3 of dirt, then stone. The second rule covers steep ground with stone and some of a rock block, and the fallback is plain stone.",
                "# Try changing",
                "- Change Base Y in the gradient to move the whole plain up or down.",
                "- Change the grass block for another block with the Pick button.",
                "- Make the dirt layer thicker or thinner."),
                biome("guide_flat_grass", "plains", sum(gradient(70, 24)), materials("minecraft:grass_block", 1, "minecraft:dirt", 3, "minecraft:andesite"), 0.1, 0.2, -0.2)));

        r.add(new Recipe("rolling_hills", "2. Rolling hills", List.of(
                "# What you get",
                "The flat plain with gentle hills on it.",
                "# What changed",
                "A Noise2dDensity was added to the sum. It gives a smooth wandering number between about -1 and 1 for every spot. Adding it to the gradient moves the height where the total crosses zero, so the ground rises and falls.",
                "# The dials",
                "- Frequency is how often the noise repeats: a small number gives wide hills, a large number gives choppy ground.",
                "- Amplitude is how tall the hills are. 0.3 against a gradient scale of 24 gives hills of a few blocks to a dozen blocks.",
                "- Octaves stacks smaller copies on top for detail. One octave is smooth, three is rough.",
                "# The pattern to remember",
                "Terrain is almost always: a base shape (the gradient) plus variation (noise). Everything bigger is more of these added together."),
                biome("guide_rolling_hills", "plains", sum(gradient(72, 24), noise2d(0.004, 2, 0.35)), materials("minecraft:grass_block", 1, "minecraft:dirt", 3, "minecraft:andesite"), 0.15, 0.2, -0.1)));

        r.add(new Recipe("snowy_peaks", "3. Snowy mountains", List.of(
                "# What you get",
                "Tall rough mountains covered in snow, with bare rock on the steep faces.",
                "# What changed",
                "The gradient's Base Y is higher, and the noise has a much larger amplitude and three octaves, so the height swings by tens of blocks and has fine detail on the slopes.",
                "# Materials",
                "The top layer is snow instead of grass and the layer under it is stone. Because the first rule only applies to ground gentler than 45 degrees, steep cliffs stay as rock, which is why real mountains look streaked rather than uniformly white.",
                "# Try changing",
                "- Raise the amplitude to 0.9 for very dramatic peaks.",
                "- Swap snow for packed ice or gravel."),
                biome("guide_snowy_peaks", "mountains", sum(gradient(95, 30), noise2d(0.006, 3, 0.8)), materials("minecraft:snow_block", 1, "minecraft:stone", 3, "minecraft:stone"), 0.7, -0.6, 0.0)));

        r.add(new Recipe("desert_dunes", "4. Desert dunes", List.of(
                "# What you get",
                "Sand dunes over sandstone.",
                "# What changed",
                "A long, low-frequency noise gives wide, gentle swells, like dunes. The sum is the same shape as the hills; only the numbers and blocks differ, which is the point: most biomes are small variations on this one pattern.",
                "# Materials",
                "Sand on top (3 blocks deep), sandstone below that, then stone."),
                biome("guide_desert_dunes", "desert", sum(gradient(68, 20), noise2d(0.0025, 2, 0.25)), materials("minecraft:sand", 3, "minecraft:sandstone", 4, "minecraft:sandstone"), 0.2, 0.9, -0.8)));

        return r;
    }

    static Recipe byId(String id) {
        for (Recipe r : all()) if (r.id().equals(id)) return r;
        return null;
    }
}
