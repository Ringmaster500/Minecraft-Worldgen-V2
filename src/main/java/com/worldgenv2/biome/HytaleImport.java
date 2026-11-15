package com.worldgenv2.biome;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Hytale's own V2 asset files (the JSON in Assets.zip under Server/HytaleGenerator) into this engine's forms.
 * The node editor's {@code $}-prefixed keys (positions, ids, comments) are dropped; everything else keeps Hytale's names.
 */
public final class HytaleImport {
    private HytaleImport() {}

    /** Removes the node editor's {@code $} keys in place. */
    public static Object stripEditorKeys(Object o) {
        if (o instanceof Map<?, ?> m) {
            m.keySet().removeIf(k -> String.valueOf(k).startsWith("$"));
            for (Object v : m.values()) stripEditorKeys(v);
        } else if (o instanceof List<?> l) {
            for (Object x : l) stripEditorKeys(x);
        }
        return o;
    }

    /** True for a biome written in Hytale's form (its "Terrain" is an object with a Type, not a density itself). */
    public static boolean isHytaleBiome(Map<String, Object> root) {
        Object t = get(root, "Terrain");
        return t instanceof Map<?, ?> tm && tm.keySet().stream().anyMatch(k -> String.valueOf(k).equalsIgnoreCase("Density")) && !tm.keySet().stream().anyMatch(k -> String.valueOf(k).equalsIgnoreCase("Inputs"));
    }

    /**
     * A Hytale biome ({@code Name}, {@code Terrain} {Type, Density}, {@code MaterialProvider}, {@code Props}) in the form
     * {@link JsonAstCompiler#compileBiome} reads. Environment and tint providers only affect looks Minecraft decides itself.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> biomeToEngineForm(Map<String, Object> hy) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object name = get(hy, "Name");
        out.put("name", name == null ? "worldgenv2:unnamed" : String.valueOf(name));
        Object terrain = get(hy, "Terrain");
        if (terrain instanceof Map<?, ?> t) {
            Object d = get((Map<String, Object>) t, "Density");
            if (d != null) out.put("terrain", d);
        }
        Object mp = get(hy, "MaterialProvider");
        if (mp != null) out.put("materialProvider", mp);
        Object props = get(hy, "Props");
        if (props != null) out.put("props", props);
        return out;
    }

    private static Object get(Map<String, Object> m, String key) {
        for (Map.Entry<String, Object> e : m.entrySet()) if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        return null;
    }
}