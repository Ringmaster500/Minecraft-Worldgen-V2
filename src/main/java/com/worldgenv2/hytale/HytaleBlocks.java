package com.worldgenv2.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hytale block names to Minecraft blocks. Hytale files name their blocks ({@code Soil_Grass_Deep}, {@code Plant_Bush_Lush}); this
 * table maps each to the nearest Minecraft block (exact names first, then the longest matching prefix), so a Hytale world setup
 * generates something readable. The table is a resource ({@code hytale_blocks.json}), not code, so it can be tuned.
 */
public final class HytaleBlocks {
    private HytaleBlocks() {}

    private static Map<String, String> exact;
    private static List<String[]> prefixes;

    private static synchronized void load() {
        if (exact != null) return;
        exact = new HashMap<>();
        prefixes = new ArrayList<>();
        try (InputStream in = HytaleBlocks.class.getResourceAsStream("/assets/worldgenv2/hytale_blocks.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.getAsJsonObject("exact").entrySet()) exact.put(e.getKey(), e.getValue().getAsString());
            JsonArray p = root.getAsJsonArray("prefix");
            for (JsonElement el : p) { JsonArray a = el.getAsJsonArray(); prefixes.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()}); }
            prefixes.sort((a, b) -> Integer.compare(b[0].length(), a[0].length()));
        } catch (Exception ignored) {
        }
    }

    /** True when the id looks like a Hytale block name (no namespace, an underscore-separated category prefix). */
    public static boolean isHytaleName(String id) {
        if (id == null || id.indexOf(':') >= 0) return false;
        load();
        String base = stripProperties(id);
        return exact.containsKey(base) || base.startsWith("Soil_") || base.startsWith("Rock_") || base.startsWith("Plant_") || base.startsWith("Wood_")
                || base.startsWith("Ore_") || base.startsWith("Rubble_") || base.startsWith("Deco_") || base.startsWith("Water_") || base.startsWith("Fluid_") || base.startsWith("Lava_");
    }

    private static String stripProperties(String id) {
        int bar = id.indexOf('|');
        return (bar < 0 ? id : id.substring(0, bar)).trim();
    }

    /** The Minecraft block id for a Hytale block name, or null when the name is not a Hytale block. */
    public static String toMinecraft(String hytaleName) {
        if (!isHytaleName(hytaleName)) return null;
        String base = stripProperties(hytaleName);
        String hit = exact.get(base);
        if (hit != null) return hit;
        for (String[] p : prefixes) if (base.startsWith(p[0])) return p[1];
        return "minecraft:stone";
    }
}