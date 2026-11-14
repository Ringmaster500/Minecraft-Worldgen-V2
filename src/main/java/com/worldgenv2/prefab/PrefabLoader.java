package com.worldgenv2.prefab;

import com.worldgenv2.prefab.Prefab.AdaptiveFoundation;
import com.worldgenv2.prefab.Prefab.Facing;
import com.worldgenv2.prefab.Prefab.PaletteEntry;
import com.worldgenv2.prefab.Prefab.PrefabSocket;
import com.worldgenv2.prefab.Prefab.Vec3i;
import com.worldgenv2.prefab.Prefab.VoxelEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Native parser for structure prefab JSON blueprints (.prefab.json).
 * Directly loads Minecraft block IDs, state properties, sockets, and adaptive foundations.
 * An optional blockIdMapper hook allows external compatibility addons to translate foreign block namespaces.
 */
public final class PrefabLoader {

    private final UnaryOperator<String> blockIdMapper;

    public PrefabLoader() {
        this(UnaryOperator.identity());
    }

    public PrefabLoader(UnaryOperator<String> blockIdMapper) {
        this.blockIdMapper = Objects.requireNonNull(blockIdMapper, "blockIdMapper");
    }

    public Prefab loadFromStream(InputStream stream) throws IOException {
        String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return parseJson(json);
    }

    @SuppressWarnings("unchecked")
    public Prefab parseJson(String jsonContent) {
        Object parsed = new MiniJsonParser(jsonContent).parseValue();
        if (!(parsed instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("Expected JSON object at root of .prefab.json");
        }
        Map<String, Object> root = (Map<String, Object>) rootMap;

        String name = getString(root, "name", "worldgenv2:unnamed_prefab");
        int version = getInt(root, "version", 2);

        Vec3i size = parseVec3i((Map<String, Object>) root.get("size"), 1, 1, 1);
        Vec3i anchor = parseVec3i((Map<String, Object>) root.get("anchor"), 0, 0, 0);

        List<PaletteEntry> palette = new ArrayList<>();
        Object rawPalette = root.get("palette");
        if (rawPalette instanceof List<?> paletteList) {
            for (Object item : paletteList) {
                if (item instanceof Map<?, ?> entryMap) {
                    Map<String, Object> p = (Map<String, Object>) entryMap;
                    int id = getInt(p, "id", 0);
                    String rawName = getString(p, "name", "minecraft:air");
                    String mappedName = blockIdMapper.apply(rawName);
                    Map<String, String> props = new LinkedHashMap<>();
                    if (p.get("properties") instanceof Map<?, ?> propMap) {
                        for (Map.Entry<?, ?> pe : propMap.entrySet()) {
                            props.put(String.valueOf(pe.getKey()), String.valueOf(pe.getValue()));
                        }
                    }
                    palette.add(PaletteEntry.of(id, mappedName, props));
                }
            }
        }

        List<VoxelEntry> blocks = new ArrayList<>();
        Object rawBlocks = root.get("blocks");
        if (rawBlocks instanceof List<?> blockList) {
            for (Object item : blockList) {
                if (item instanceof Map<?, ?> blockMap) {
                    Map<String, Object> b = (Map<String, Object>) blockMap;
                    int x = getInt(b, "x", 0);
                    int y = getInt(b, "y", 0);
                    int z = getInt(b, "z", 0);
                    int paletteIndex = getInt(b, "paletteIndex", 0);
                    String lootTable = b.containsKey("lootTable") ? getString(b, "lootTable", null) : null;
                    blocks.add(new VoxelEntry(x, y, z, paletteIndex, lootTable));
                }
            }
        }

        List<PrefabSocket> sockets = new ArrayList<>();
        Object rawSockets = root.get("sockets");
        if (rawSockets instanceof List<?> socketList) {
            for (Object item : socketList) {
                if (item instanceof Map<?, ?> socketMap) {
                    Map<String, Object> s = (Map<String, Object>) socketMap;
                    String sName = getString(s, "name", "socket");
                    String sType = getString(s, "type", "CORRIDOR_CONNECTION");
                    String sGender = getString(s, "gender", "ANY");
                    Vec3i sPos = parseVec3i((Map<String, Object>) s.get("position"), 0, 0, 0);
                    Facing sFacing = Facing.fromString(getString(s, "facing", "NORTH"));
                    List<String> tags = new ArrayList<>();
                    if (s.get("compatibleTags") instanceof List<?> tagList) {
                        for (Object t : tagList) {
                            tags.add(String.valueOf(t));
                        }
                    }
                    sockets.add(new PrefabSocket(sName, sType, sGender, sPos, sFacing, tags));
                }
            }
        }

        List<AdaptiveFoundation> foundations = new ArrayList<>();
        Object rawFoundations = root.get("adaptiveFoundations");
        if (rawFoundations instanceof List<?> foundationList) {
            for (Object item : foundationList) {
                if (item instanceof Map<?, ?> fMap) {
                    Map<String, Object> f = (Map<String, Object>) fMap;
                    Map<String, Object> area = (Map<String, Object>) f.get("area");
                    int minX = area != null ? getInt(area, "minX", 0) : getInt(f, "minX", 0);
                    int minZ = area != null ? getInt(area, "minZ", 0) : getInt(f, "minZ", 0);
                    int maxX = area != null ? getInt(area, "maxX", size.x() - 1) : getInt(f, "maxX", size.x() - 1);
                    int maxZ = area != null ? getInt(area, "maxZ", size.z() - 1) : getInt(f, "maxZ", size.z() - 1);
                    String rawFill = getString(f, "fillMaterial", "minecraft:cobblestone");
                    String mappedFill = blockIdMapper.apply(rawFill);
                    int maxDepth = getInt(f, "maxDepth", 16);
                    foundations.add(new AdaptiveFoundation(minX, minZ, maxX, maxZ, mappedFill, maxDepth));
                }
            }
        }

        return new Prefab(name, version, size, anchor, palette, blocks, sockets, foundations);
    }

    private static Vec3i parseVec3i(Map<String, Object> map, int defX, int defY, int defZ) {
        if (map == null) {
            return new Vec3i(defX, defY, defZ);
        }
        return new Vec3i(
            getInt(map, "x", defX),
            getInt(map, "y", defY),
            getInt(map, "z", defZ)
        );
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static final class MiniJsonParser {
        private final String src;
        private int pos;

        MiniJsonParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < src.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' in JSON object at position " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < src.length()) {
                list.add(parseValue());
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' in JSON array at position " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Number parseNumber() {
            int start = pos;
            boolean isFloat = false;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                pos++;
            }
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloat = true;
                    pos++;
                } else {
                    break;
                }
            }
            String token = src.substring(start, pos);
            return isFloat ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean token at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null token at position " + pos);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
