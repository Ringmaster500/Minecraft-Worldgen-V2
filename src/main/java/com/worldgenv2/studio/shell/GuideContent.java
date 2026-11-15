package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldgenv2.studio.graph.NodeSchemas;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Guide's pages: written how-to pages from {@code guide.json}, and one page per node generated from the node schemas and the
 * node documentation in {@code node_wiki.json}. Nothing here is copied by hand, so the reference follows the engine.
 */
final class GuideContent {
    private GuideContent() {}

    /** One page. For a how-to page {@code lines} is its text; for a node page {@code node} is set and the text is built from it. */
    record Page(String id, String section, String title, List<String> lines, NodeSchemas.NodeType node, String search) {}

    private static List<Page> pages;
    private static Map<String, JsonObject> wiki;

    static synchronized List<Page> pages() {
        if (pages != null) return pages;
        List<Page> out = new ArrayList<>();
        try (InputStream in = GuideContent.class.getClassLoader().getResourceAsStream("assets/worldgenv2/guide.json")) {
            if (in != null) {
                for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    List<String> body = new ArrayList<>();
                    for (JsonElement l : o.getAsJsonArray("body")) body.add(l.getAsString());
                    out.add(new Page(o.get("id").getAsString(), o.get("section").getAsString(), o.get("title").getAsString(), body, null,
                            (o.get("title").getAsString() + " " + String.join(" ", body)).toLowerCase(Locale.ROOT)));
                }
            }
        } catch (Exception ignored) { }
        for (String kind : NodeSchemas.KINDS) {
            for (NodeSchemas.NodeType t : NodeSchemas.ofKind(kind)) {
                JsonObject w = wikiFor(t.name());
                String text = t.name() + " " + kind + " " + (w == null ? "" : w.has("doc") ? w.get("doc").getAsString() : "");
                out.add(new Page("node:" + kind + ":" + t.name(), "Nodes: " + kind, t.name(), List.of(), t, text.toLowerCase(Locale.ROOT)));
            }
        }
        pages = out;
        return out;
    }

    static synchronized JsonObject wikiFor(String type) {
        if (wiki == null) {
            wiki = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            try (InputStream in = GuideContent.class.getClassLoader().getResourceAsStream("assets/worldgenv2/node_wiki.json")) {
                if (in != null) for (var e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
                    if (e.getValue().isJsonObject()) wiki.put(e.getKey(), e.getValue().getAsJsonObject());
                }
            } catch (Exception ignored) { }
        }
        for (String k : new String[]{type, type + "Density", type + "Prop", type + "Provider", type + "MaterialProvider", type + "Pattern", type + "Scanner", type + "Curve"}) {
            JsonObject o = wiki.get(k);
            if (o != null) return o;
        }
        return null;
    }

    /** The text of a node page as markup lines ({@code #} heading, {@code -} bullet, {@code `} formula, else a paragraph). */
    static List<String> nodeLines(NodeSchemas.NodeType t) {
        List<String> l = new ArrayList<>();
        JsonObject w = wikiFor(t.name());
        l.add("# What it does");
        l.add(w != null && w.has("doc") ? w.get("doc").getAsString() : "A " + t.kind() + " node.");
        if (w != null && w.has("formula") && !w.get("formula").getAsString().isBlank()) l.add("`" + w.get("formula").getAsString());
        if (!t.params().isEmpty()) {
            l.add("# Settings");
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (NodeSchemas.Param p : t.params()) {
                if (!seen.add(p.key().toLowerCase(Locale.ROOT))) continue;
                String def = p.def() == null || p.def().isJsonNull() || (p.def().isJsonArray() && p.def().getAsJsonArray().isEmpty()) ? "" : "  (default " + (p.def().isJsonPrimitive() ? p.def().getAsString() : p.def().toString()) + ")";
                String doc = NodeSchemas.doc(t.kind(), t.name(), p.key());
                l.add("- " + p.key() + ": " + typeWord(p.type()) + def + (doc == null ? "" : " - " + doc));
            }
        }
        if (!t.slots().isEmpty()) {
            l.add("# What plugs into it");
            for (NodeSchemas.Slot s : t.slots()) l.add("- " + s.key() + ": " + (s.multi() ? "a list of " : "one ") + s.kind() + " node" + (s.multi() ? "s" : ""));
        } else {
            l.add("# What plugs into it");
            l.add("Nothing: it has no inputs, so it stands on its own.");
        }
        if (w != null && w.has("tags") && w.get("tags").isJsonArray() && !w.getAsJsonArray("tags").isEmpty()) {
            List<String> tg = new ArrayList<>();
            for (JsonElement e : w.getAsJsonArray("tags")) tg.add(e.getAsString());
            l.add("# Also known as");
            l.add(String.join(", ", tg));
        }
        return l;
    }

    private static String typeWord(String t) {
        return switch (t) {
            case "number" -> "number";
            case "int" -> "whole number";
            case "bool" -> "on or off";
            case "strings" -> "list of names";
            case "json" -> "structured value";
            default -> "text";
        };
    }
}
