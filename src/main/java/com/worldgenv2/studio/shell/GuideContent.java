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
        for (GuideRecipes.Recipe r : GuideRecipes.all()) {
            out.add(new Page("recipe:" + r.id(), "Teaching setups", r.title(), r.lines(), null,
                    (r.title() + " teaching setup example tutorial " + String.join(" ", r.lines())).toLowerCase(Locale.ROOT)));
        }
        String[] aliasSuffixes = {"Density", "MaterialProvider", "Provider", "Prop", "Pattern", "Scanner", "Curve"};
        for (String kind : NodeSchemas.KINDS) {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (NodeSchemas.NodeType t : NodeSchemas.ofKind(kind)) names.add(t.name().toLowerCase(Locale.ROOT));
            for (NodeSchemas.NodeType t : NodeSchemas.ofKind(kind)) {
                // Hytale's short name is the real one; our long spelling of the same node (AbsDensity) would only be a duplicate page
                boolean alias = false;
                for (String suf : aliasSuffixes) {
                    if (t.name().endsWith(suf) && t.name().length() > suf.length() && names.contains(t.name().substring(0, t.name().length() - suf.length()).toLowerCase(Locale.ROOT))) { alias = true; break; }
                }
                if (alias) continue;
                JsonObject w = wikiFor(t.name(), kind);
                String text = t.name() + " " + kind + " " + (w == null ? "" : w.has("doc") ? w.get("doc").getAsString() : "");
                out.add(new Page("node:" + kind + ":" + t.name(), "Nodes: " + kind, t.name(), List.of(), t, text.toLowerCase(Locale.ROOT)));
            }
        }
        pages = out;
        return out;
    }

    /** The documentation for a node. Entries that name a different kind (the density Cuboid, for the prop Cuboid) are not used. */
    static JsonObject wikiFor(String type, String kind) {
        JsonObject w = wikiFor(type);
        if (w != null && w.has("kind") && !w.get("kind").getAsString().equalsIgnoreCase(kind)) return null;
        return w;
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
        JsonObject w = wikiFor(t.name(), t.kind());
        l.add("# What it does");
        String mainDoc = w != null && w.has("doc") ? w.get("doc").getAsString() : "";
        l.add(mainDoc.length() >= 15 ? mainDoc : "A " + t.kind() + " node. " + kindWord(t.kind()));
        if (w != null && w.has("formula") && w.get("formula").getAsString().trim().length() >= 6) l.add("`" + w.get("formula").getAsString());
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

    private static String kindWord(String kind) {
        return switch (kind) {
            case "density" -> "It makes or changes a number for every point in space (above zero is solid, below is air).";
            case "curve" -> "It turns one number into another, drawn as a line.";
            case "vector" -> "It produces a direction or position in space.";
            case "material" -> "It decides which block goes at a spot of solid ground.";
            case "pattern" -> "It answers a yes or no question about the blocks around a spot.";
            case "positions" -> "It makes a set of points, for example one per tree.";
            case "scanner" -> "It searches around a point for a good spot.";
            case "assignments" -> "It says which prop goes at which position.";
            case "prop" -> "It builds something in the world.";
            case "mask" -> "It stops a prop from being placed over or replacing certain blocks.";
            default -> "";
        };
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
