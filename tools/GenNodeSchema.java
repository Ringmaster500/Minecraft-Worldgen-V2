import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the Studio's node schema from the engine's own compiler so the graph editor can never drift from
 * what the engine actually accepts.
 *
 * Reads src/main/java/com/worldgenv2/biome/JsonAstCompiler.java, finds every
 *   WorldGenV2Registries.<KIND>_TYPE.register("TypeName", (json, ...) -> ...)
 * and records the JSON keys that registration reads:
 *   getFloat/getDouble/getInt/getString/getBoolean(json, "key", default)  -> parameters
 *   compileChild*(json, "key"[, "alias"]) / compile*List(json, "key")     -> child slots (single / list)
 *
 * Run:  java tools/GenNodeSchema.java   (from the repository root)
 * Output: src/main/resources/assets/worldgenv2/node_schema.json
 */
public class GenNodeSchema {

    static final Map<String, String> KIND = new LinkedHashMap<>();
    static {
        KIND.put("DENSITY_NODE_TYPE", "density");
        KIND.put("CURVE_TYPE", "curve");
        KIND.put("VECTOR_PROVIDER_TYPE", "vector");
        KIND.put("MATERIAL_PROVIDER_TYPE", "material");
        KIND.put("PATTERN_TYPE", "pattern");
        KIND.put("PROP_TYPE", "prop");
        KIND.put("POSITIONS_PROVIDER_TYPE", "positions");
        KIND.put("SCANNER_TYPE", "scanner");
        KIND.put("ASSIGNMENTS_TYPE", "assignments");
        KIND.put("BLOCK_MASK_TYPE", "mask");
        KIND.put("PROP_RUNTIME_TYPE", "propruntime");
    }

    /** Child compile helper name suffix -> slot kind. */
    static String slotKind(String helper) {
        String h = helper.toLowerCase(Locale.ROOT);
        if (h.contains("density")) return "density";
        if (h.contains("curve")) return "curve";
        if (h.contains("vector")) return "vector";
        if (h.contains("material")) return "material";
        if (h.contains("pattern")) return "pattern";
        if (h.contains("propruntime")) return "propruntime";
        if (h.contains("prop")) return "prop";
        if (h.contains("position")) return "positions";
        if (h.contains("scanner")) return "scanner";
        if (h.contains("mask")) return "mask";
        if (h.contains("assign")) return "assignments";
        return "any";
    }

    static final Pattern REG = Pattern.compile("WorldGenV2Registries\\.(\\w+)\\.register\\(\"(\\w+)\"");
    static final Pattern PARAM = Pattern.compile("\\bget(Float|Double|Int|String|Boolean|Bool)\\(\\s*json\\s*,\\s*\"(\\w+)\"\\s*,");
    static final Pattern CHILD = Pattern.compile("\\bcompileChild(\\w+)\\(\\s*json\\s*,\\s*\"(\\w+)\"(?:\\s*,\\s*\"(\\w+)\")?");
    static final Pattern LIST = Pattern.compile("\\bcompile(\\w+?)List\\(\\s*json\\s*,\\s*\"(\\w+)\"");
    static final Pattern GENERIC = Pattern.compile("\\bcompile(Density|Curve|VectorProvider|Pattern|Prop|MaterialProvider|PositionsProvider|Scanner|NodeSelector|BlockMask)\\([^;]*?json\\.get(?:OrDefault)?\\(\"(\\w+)\"");

    public static void main(String[] args) throws IOException {
        Path src = Path.of("src/main/java/com/worldgenv2/biome/JsonAstCompiler.java");
        String text = Files.readString(src, StandardCharsets.UTF_8);
        Matcher m = REG.matcher(text);
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (String k : KIND.values()) out.put(k, new LinkedHashMap<>());

        while (m.find()) {
            String kind = KIND.get(m.group(1));
            if (kind == null) continue;
            String type = m.group(2);
            int open = text.indexOf('(', m.start() + "WorldGenV2Registries.".length());
            int end = matchParen(text, open);
            String body = text.substring(m.end(), end);

            StringBuilder params = new StringBuilder();
            Set<String> seenKeys = new LinkedHashSet<>();
            Matcher pm = PARAM.matcher(body);
            while (pm.find()) {
                String key = pm.group(2);
                if (!seenKeys.add(key)) continue;
                int argStart = pm.end();
                String def = argText(body, argStart);
                String alias = null;
                Matcher am = PARAM.matcher(def);
                if (am.find()) { // nested alias: getFloat(json,"a", getFloat(json,"b", 0.0f))
                    alias = am.group(2);
                    def = argText(def, am.end());
                }
                String t = switch (pm.group(1)) {
                    case "Float", "Double" -> "number";
                    case "Int" -> "int";
                    case "Boolean", "Bool" -> "bool";
                    default -> "string";
                };
                if (params.length() > 0) params.append(',');
                params.append("{\"key\":").append(q(key)).append(",\"t\":").append(q(t)).append(",\"def\":").append(literal(def, t));
                if (alias != null) params.append(",\"alias\":").append(q(alias));
                params.append('}');
            }

            StringBuilder slots = new StringBuilder();
            Set<String> seenSlots = new LinkedHashSet<>();
            Matcher cm = CHILD.matcher(body);
            while (cm.find()) {
                if (!seenSlots.add(cm.group(2))) continue;
                if (slots.length() > 0) slots.append(',');
                slots.append("{\"key\":").append(q(cm.group(2))).append(",\"kind\":").append(q(slotKind(cm.group(1))))
                        .append(",\"multi\":false");
                if (cm.group(3) != null) slots.append(",\"alias\":").append(q(cm.group(3)));
                slots.append('}');
            }
            Matcher lm = LIST.matcher(body);
            while (lm.find()) {
                if (!seenSlots.add(lm.group(2))) continue;
                if (slots.length() > 0) slots.append(',');
                slots.append("{\"key\":").append(q(lm.group(2))).append(",\"kind\":").append(q(slotKind(lm.group(1)))).append(",\"multi\":true}");
            }
            Matcher gm = GENERIC.matcher(body);
            while (gm.find()) {
                if (!seenSlots.add(gm.group(2))) continue;
                if (slots.length() > 0) slots.append(',');
                slots.append("{\"key\":").append(q(gm.group(2))).append(",\"kind\":").append(q(slotKind(gm.group(1)))).append(",\"multi\":false}");
            }
            out.get(kind).put(type, "{\"params\":[" + params + "],\"slots\":[" + slots + "]}");
        }

        StringBuilder sb = new StringBuilder("{\n  \"_generatedBy\": \"tools/GenNodeSchema.java from JsonAstCompiler.java; do not edit by hand\"");
        int total = 0;
        for (var e : out.entrySet()) {
            sb.append(",\n  ").append(q(e.getKey())).append(": {");
            boolean first = true;
            for (var t : e.getValue().entrySet()) {
                sb.append(first ? "\n    " : ",\n    ").append(q(t.getKey())).append(": ").append(t.getValue());
                first = false;
                total++;
            }
            sb.append("\n  }");
        }
        sb.append("\n}\n");
        Path dest = Path.of("src/main/resources/assets/worldgenv2/node_schema.json");
        Files.createDirectories(dest.getParent());
        Files.writeString(dest, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Wrote " + total + " node types to " + dest);
        for (var e : out.entrySet()) System.out.println("  " + e.getKey() + ": " + e.getValue().size());
    }

    static int matchParen(String s, int open) {
        int depth = 0;
        boolean str = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (str) { if (c == '\\') i++; else if (c == '"') str = false; continue; }
            if (c == '"') str = true;
            else if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth == 0) return i; }
        }
        return s.length();
    }

    /** Text of the argument starting at {@code from} up to the closing paren of the enclosing call. */
    static String argText(String s, int from) {
        int depth = 0;
        boolean str = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (str) { if (c == '\\') i++; else if (c == '"') str = false; continue; }
            if (c == '"') str = true;
            else if (c == '(') depth++;
            else if (c == ')') { if (depth == 0) return s.substring(from, i).trim(); depth--; }
        }
        return s.substring(from).trim();
    }

    /** Turns a Java default-value expression into a JSON literal, or null when it is not a plain literal. */
    static String literal(String expr, String type) {
        String e = expr.trim();
        if (e.startsWith("\"") && e.endsWith("\"") && e.length() >= 2 && e.indexOf('"', 1) == e.length() - 1) return e;
        if (e.equals("true") || e.equals("false")) return e;
        if (e.equals("null")) return "null";
        Matcher n = Pattern.compile("^(-?[0-9]*\\.?[0-9]+(?:[eE]-?[0-9]+)?)[fFdDlL]?$").matcher(e);
        if (n.matches()) return n.group(1);
        return "null"; // computed default; the editor falls back to the type's zero value
    }

    static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
