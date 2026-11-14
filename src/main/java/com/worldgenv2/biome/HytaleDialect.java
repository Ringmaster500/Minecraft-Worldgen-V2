package com.worldgenv2.biome;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bridges Hytale's asset conventions to the engine's node implementations, so Hytale-authored JSON reads the same here.
 *
 * <p>Hytale gives every density node its children as one positional {@code Inputs} list (one entry for Abs, two for a
 * comparator, any number for Sum). The engine's nodes take named slots ({@code input}, {@code inputA}/{@code inputB}...).
 * This adapter fills the named slots from {@code Inputs} by position, using the node's slot order from the generated
 * node schema. Nodes that already take a list ({@code inputs}) need nothing.
 */
final class HytaleDialect {
    private HytaleDialect() {}

    /** density type (lower case) -> its single (non-list) slot keys in schema order; types with a list slot are omitted. */
    private static Map<String, List<String>> singleSlots;

    @SuppressWarnings("unchecked")
    private static synchronized Map<String, List<String>> table() {
        if (singleSlots != null) return singleSlots;
        Map<String, List<String>> out = new HashMap<>();
        try (InputStream in = HytaleDialect.class.getClassLoader().getResourceAsStream("assets/worldgenv2/node_schema.json")) {
            if (in != null) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Object parsed = new JsonAstCompiler.SimpleJsonParser(text).parseValue();
                Object density = ((Map<String, Object>) parsed).get("density");
                if (density instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        Map<String, Object> node = (Map<String, Object>) e.getValue();
                        List<String> keys = new ArrayList<>();
                        boolean hasList = false;
                        if (node.get("slots") instanceof List<?> slots) {
                            for (Object so : slots) {
                                Map<String, Object> s = (Map<String, Object>) so;
                                if (Boolean.TRUE.equals(s.get("multi"))) hasList = true;
                                else keys.add(String.valueOf(s.get("key")));
                            }
                        }
                        if (!hasList && !keys.isEmpty()) out.put(e.getKey().toString().toLowerCase(Locale.ROOT), keys);
                    }
                }
            }
        } catch (Exception ignored) {
            // without the table only list-style nodes accept Inputs; the engine still works
        }
        singleSlots = out;
        return out;
    }

    /** Fills a density node's named slots from a Hytale-style {@code Inputs} list. */
    @SuppressWarnings("unchecked")
    static void adaptDensityInputs(String type, Map<String, Object> json) {
        if (!(json.get("inputs") instanceof List<?> inputs) || inputs.isEmpty()) return;
        String key = type.toLowerCase(Locale.ROOT);
        List<String> slots = table().get(key);
        if (slots == null) slots = table().get(key + "density");
        if (slots == null) return;
        for (int i = 0; i < slots.size() && i < inputs.size(); i++) {
            if (!json.containsKey(slots.get(i))) json.put(slots.get(i), inputs.get(i));
        }
    }
}
