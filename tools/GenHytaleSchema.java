import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the Studio's palette entries for Hytale's own nodes from the Hytale generator documentation
 * (docs/research/hytale_generator_docs), so every documented node can be created with the documented parameter names.
 *
 *   java tools/GenHytaleSchema.java
 *
 * Output: src/main/resources/assets/worldgenv2/hytale_node_schema.json (same format as node_schema.json). A node the older
 * schema already describes with the same parameters is left to the older entry; any other documented node is emitted and
 * overrides it, so a Hytale name always offers Hytale's parameters.
 */
public final class GenHytaleSchema {

    static final String DOCS = "docs/research/hytale_generator_docs/";
    static final String[][] FILES = {
            {"density.md", "density"}, {"curve.md", "curve"}, {"vector-provider.md", "vector"}, {"material-provider.md", "material"},
            {"pattern.md", "pattern"}, {"prop.md", "prop"}, {"positions-provider.md", "positions"}, {"scanner.md", "scanner"},
            {"assignments.md", "assignments"}, {"prop-distribution.md", "propdistribution"},
            {"graph-generator/graph-pass.md", "graphpass"}, {"graph-generator/node-selector.md", "nodeselector"},
            {"graph-generator/node-action.md", "nodeaction"}, {"graph-generator/edge-selector.md", "edgeselector"},
            {"graph-generator/edge-action.md", "edgeaction"}, {"graph-generator/content-predicate.md", "contentpredicate"},
            {"graph-generator/content-supplier.md", "contentsupplier"}};

    record Param(String key, String type, String doc, String def) {
        Param(String key, String type, String doc) { this(key, type, doc, null); }
    }
    record Slot(String key, String kind, boolean multi) {}
    record Node(String name, List<Param> params, List<Slot> slots) {}

    public static void main(String[] args) throws IOException {
        String legacy = Files.readString(Path.of("src/main/resources/assets/worldgenv2/node_schema.json"));
        Map<String, Map<String, Set<String>>> legacyKeys = legacyKeys(legacy);
        StringBuilder out = new StringBuilder("{\n  \"_generatedBy\": \"tools/GenHytaleSchema.java from docs/research/hytale_generator_docs; do not edit by hand\"");
        int total = 0, skipped = 0;
        Map<String, List<Node>> extraKinds = new LinkedHashMap<>();
        for (String[] f : FILES) {
            List<Node> nodes = parse(Files.readString(Path.of(DOCS + f[0])), f[1]);
            if (f[1].equals("prop")) nodes.add(runtimeNode());
            if (f[1].equals("density")) { extraKinds.put("worldstructure", List.of(worldStructureNode())); extraKinds.put("graphgenerator", List.of(graphGeneratorNode())); }
            out.append(",\n  \"").append(f[1]).append("\": {");
            boolean first = true;
            for (Node n : nodes) {
                Set<String> have = legacyKeys.getOrDefault(f[1], Map.of()).get(n.name().toLowerCase(Locale.ROOT));
                Set<String> want = new TreeSet<>();
                for (Param p : n.params()) want.add(p.key().toLowerCase(Locale.ROOT));
                for (Slot s : n.slots()) want.add(s.key().toLowerCase(Locale.ROOT));
                if (have != null && have.equals(want)) { skipped++; continue; }
                if (!first) out.append(",");
                first = false;
                out.append("\n    \"").append(n.name()).append("\": {\"params\":[");
                for (int i = 0; i < n.params().size(); i++) {
                    Param p = n.params().get(i);
                    out.append(i > 0 ? "," : "").append("{\"key\":\"").append(p.key()).append("\",\"t\":\"").append(p.type()).append("\"");
                    if (!p.doc().isBlank()) out.append(",\"doc\":\"").append(esc(p.doc())).append("\"");
                    if (p.def() != null) out.append(",\"def\":").append(p.def());
                    out.append("}");
                }
                out.append("],\"slots\":[");
                for (int i = 0; i < n.slots().size(); i++) {
                    Slot s = n.slots().get(i);
                    out.append(i > 0 ? "," : "").append("{\"key\":\"").append(s.key()).append("\",\"kind\":\"").append(s.kind()).append("\",\"multi\":").append(s.multi()).append("}");
                }
                out.append("]}");
                total++;
            }
            out.append("\n  }");
        }
        for (var e : extraKinds.entrySet()) {
            out.append(",\n  \"").append(e.getKey()).append("\": {");
            for (Node n : e.getValue()) {
                out.append("\n    \"").append(n.name()).append("\": {\"params\":[");
                for (int i = 0; i < n.params().size(); i++) {
                    Param p = n.params().get(i);
                    out.append(i > 0 ? "," : "").append("{\"key\":\"").append(p.key()).append("\",\"t\":\"").append(p.type()).append("\"");
                    if (!p.doc().isBlank()) out.append(",\"doc\":\"").append(esc(p.doc())).append("\"");
                    if (p.def() != null) out.append(",\"def\":").append(p.def());
                    out.append("}");
                }
                out.append("],\"slots\":[");
                for (int i = 0; i < n.slots().size(); i++) { Slot s = n.slots().get(i); out.append(i > 0 ? "," : "").append("{\"key\":\"").append(s.key()).append("\",\"kind\":\"").append(s.kind()).append("\",\"multi\":").append(s.multi()).append("}"); }
                out.append("]}");
            }
            out.append("\n  }");
        }
        out.append("\n}\n");
        Files.writeString(Path.of("src/main/resources/assets/worldgenv2/hytale_node_schema.json"), out.toString(), StandardCharsets.UTF_8);
        System.out.println("Hytale schema: " + total + " nodes written, " + skipped + " already matched by the older schema.");
    }

    /** The dimension's world structure (docs/world-structure): the density that places biomes, plus its settings. */
    static Node worldStructureNode() {
        return new Node("WorldStructure", List.of(new Param("DefaultBiome", "string", "Biome used everywhere outside the delimiters (a biome of this dimension)."),
                new Param("DefaultTransitionDistance", "int", "Blocks it takes one biome to transition into another.", "32"),
                new Param("MaxBiomeEdgeDistance", "int", "How far from a biome edge distances are tracked; beyond it DistanceFromBiomeEdge reads -1.", "0"),
                new Param("Biomes", "json", "Biome delimiters: [{ Biome, Min, Max }] where the Density is within Min and Max.", "[]"),
                new Param("Framework", "json", "Named DecimalConstants and Positions shared by biomes.", "[]")),
                List.of(new Slot("Density", "density", false), new Slot("SpawnPositions", "positions", false)));
    }

    /** The Graph Generator itself (docs/graph-generator): the passes that build the graph, plus its cache settings. */
    static Node graphGeneratorNode() {
        return new Node("GraphGenerator", List.of(new Param("CacheCellSize", "json", "Size of one cached cell of the graph, in blocks.", "{\"X\":320,\"Y\":320,\"Z\":320}"),
                new Param("CacheCapacity", "int", "How many cells stay in memory at once.", "50")),
                List.of(new Slot("Passes", "graphpass", true)));
    }

    static Node runtimeNode() {
        return new Node("Runtime", List.of(new Param("Runtime", "int", "Runtime index; each unique index is another prop stage."),
                new Param("ContextPrecision", "string", "Biome, Chunk or Prop."), new Param("Skip", "bool", "Skips this runtime.")),
                List.of(new Slot("PropDistribution", "propdistribution", false)));
    }

    // ------------------------------------------------------------------ parsing

    static List<Node> parse(String md, String kind) {
        List<Node> out = new ArrayList<>();
        String[] lines = md.split("\\R");
        List<Param> common = new ArrayList<>();
        List<Slot> commonSlots = new ArrayList<>();
        String name = null;
        List<Param> params = null;
        List<Slot> slots = null;
        boolean inParams = false, inCode = false;
        for (String line : lines) {
            if (line.startsWith("```")) { inCode = !inCode; continue; }
            if (inCode) continue;
            Matcher h = Pattern.compile("^### \\[([A-Za-z0-9]+)\\]").matcher(line);
            if (h.find()) {
                if (name != null) out.add(new Node(name, params, slots));
                name = h.group(1);
                params = new ArrayList<>(common);
                slots = new ArrayList<>(commonSlots);
                inParams = false;
                continue;
            }
            if (line.startsWith("## ")) { // a section outside the node list ends the current node
                if (name != null) { out.add(new Node(name, params, slots)); name = null; }
                continue;
            }
            if (name == null) {
                Matcher b = Pattern.compile("^- ([A-Za-z0-9]+): (.*)$").matcher(line);
                if (b.find()) addParam(b.group(1), b.group(2), common, commonSlots);
                continue;
            }
            Matcher ei = Pattern.compile("^Expected Inputs: `([^`]+)`").matcher(line);
            if (ei.find() && !ei.group(1).trim().equals("0")) { slots.add(new Slot("Inputs", "density", true)); continue; }
            if (line.startsWith("Parameters:")) { inParams = true; continue; }
            if (line.startsWith("---")) { inParams = false; continue; }
            Matcher b = Pattern.compile("^- ([A-Za-z0-9]+): (.*)$").matcher(line);
            if (inParams && b.find()) addParam(b.group(1), b.group(2), params, slots);
        }
        if (name != null) out.add(new Node(name, params, slots));
        // a parameter may be listed twice by the docs' own prose; keep the first
        List<Node> clean = new ArrayList<>();
        for (Node n : out) {
            List<Param> ps = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Param p : n.params()) if (seen.add(p.key().toLowerCase(Locale.ROOT))) ps.add(p);
            List<Slot> ss = new ArrayList<>();
            for (Slot s : n.slots()) if (seen.add(s.key().toLowerCase(Locale.ROOT))) ss.add(s);
            clean.add(new Node(n.name(), ps, ss));
        }
        return clean;
    }

    static void addParam(String key, String rest, List<Param> params, List<Slot> slots) {
        String typeText = rest;
        String doc = "";
        int dot = rest.indexOf(". ");
        if (dot > 0) { typeText = rest.substring(0, dot); doc = rest.substring(dot + 2).trim(); }
        else if (rest.endsWith(".")) typeText = rest.substring(0, rest.length() - 1);
        String t = typeText.toLowerCase(Locale.ROOT).replace("`", "");
        if (doc.length() > 150) doc = doc.substring(0, 147) + "...";
        String slotKind = t.contains("slot") ? slotKind(t) : t.contains("asset") && slotKind(t) != null ? slotKind(t) : null;
        if (slotKind != null) {
            slots.add(new Slot(key, slotKind, t.startsWith("list of")));
            return;
        }
        String type;
        if (t.startsWith("list of strings")) type = "strings";
        else if (t.startsWith("list of") || t.contains("vector") || t.contains("bounds") || t.contains("range") || t.contains("rotation") || t.contains("blockset") || t.contains("blockmask") || t.contains("delimiter") || t.contains("entries")) type = "json";
        else if (t.contains("boolean")) type = "bool";
        else if (t.contains("integer")) type = "int";
        else if (t.contains("decimal") || t.contains("number") || t.contains("float")) type = "number";
        else if (t.contains("string") || t.startsWith("one of")) type = "string";
        else type = "json";
        params.add(new Param(key, type, doc));
    }

    /** The slot kind a type phrase names, or null when it is not a slot. */
    static String slotKind(String t) {
        if (t.contains("propdistribution")) return "propdistribution";
        if (t.contains("vectorprovider")) return "vector";
        if (t.contains("graphgenerator")) return "graphgenerator";
        if (t.contains("nodeselector")) return "nodeselector";
        if (t.contains("edgeselector")) return "edgeselector";
        if (t.contains("nodeaction")) return "nodeaction";
        if (t.contains("edgeaction")) return "edgeaction";
        if (t.contains("contentpredicate")) return "contentpredicate";
        if (t.contains("contentsupplier")) return "contentsupplier";
        if (t.contains("graphpass")) return "graphpass";
        if (t.contains("curve")) return "curve";
        if (t.contains("material provider") || t.contains("materialprovider")) return "material";
        if (t.contains("position")) return "positions";
        if (t.contains("pattern")) return "pattern";
        if (t.contains("scanner")) return "scanner";
        if (t.contains("assignments")) return "assignments";
        if (t.contains("density")) return "density";
        if (t.contains("prop slot") || t.contains("prop asset") || t.matches(".*\\bprop slot.*") || t.startsWith("list of prop")) return "prop";
        return null;
    }

    // ------------------------------------------------------------------ the older schema

    /** kind -> lower-cased node name -> lower-cased parameter and slot keys. */
    static Map<String, Map<String, Set<String>>> legacyKeys(String json) {
        Map<String, Map<String, Set<String>>> out = new HashMap<>();
        String kind = null;
        Pattern kindLine = Pattern.compile("^\\s\\s\"([a-z]+)\": \\{\\s*$");
        Pattern nodeLine = Pattern.compile("^\\s+\"([A-Za-z0-9]+)\": \\{\"params\":(.*)$");
        Pattern key = Pattern.compile("\"key\":\"([^\"]+)\"");
        for (String line : json.split("\\R")) {
            Matcher k = kindLine.matcher(line);
            if (k.find()) { kind = k.group(1); out.computeIfAbsent(kind, x -> new HashMap<>()); continue; }
            Matcher n = nodeLine.matcher(line);
            if (n.find() && kind != null) {
                Set<String> keys = new TreeSet<>();
                Matcher km = key.matcher(n.group(2));
                while (km.find()) keys.add(km.group(1).toLowerCase(Locale.ROOT));
                out.get(kind).put(n.group(1).toLowerCase(Locale.ROOT), keys);
            }
        }
        return out;
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
