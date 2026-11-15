package com.worldgenv2.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldgenv2.biome.HytaleImport;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.prefab.Prefab;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The player's own copy of Hytale's assets ({@code Assets.zip}, or the folder it unpacks to): the V2 generator files under
 * {@code Server/HytaleGenerator} and the prefabs under {@code Server/Prefabs}. Nothing from it is copied into this project; a world
 * setup points at the files and they are read when the setup is compiled.
 */
public final class HytaleAssets implements Closeable {

    private static final String GEN = "Server/HytaleGenerator/";
    private static final String PREFABS = "Server/Prefabs/";

    private final ZipFile zip;
    private final Path dir;
    private final Map<String, String> generator = new TreeMap<>();   // path under HytaleGenerator -> entry name
    private final Map<String, String> prefabs = new TreeMap<>();     // prefab path without extension -> entry name
    private final Map<String, Object> parsed = new HashMap<>();
    private final Map<String, Prefab> prefabCache = new HashMap<>();

    private HytaleAssets(ZipFile zip, Path dir) throws IOException {
        this.zip = zip;
        this.dir = dir;
        if (zip != null) {
            var en = zip.entries();
            while (en.hasMoreElements()) index(en.nextElement().getName());
        } else {
            try (var walk = Files.walk(dir)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) index(dir.relativize(p).toString().replace('\\', '/'));
            }
        }
    }

    private void index(String name) {
        if (name.startsWith(GEN) && name.endsWith(".json")) generator.put(name.substring(GEN.length()), name);
        else if (name.startsWith(PREFABS) && name.endsWith(".prefab.json")) prefabs.put(name.substring(PREFABS.length(), name.length() - ".prefab.json".length()), name);
    }

    /** Opens Assets.zip or an unpacked folder (the folder that contains {@code Server/}). */
    public static HytaleAssets open(String path) throws IOException {
        Path p = Path.of(path);
        if (Files.isDirectory(p)) return new HytaleAssets(null, p);
        return new HytaleAssets(new ZipFile(p.toFile()), null);
    }

    private String read(String entry) throws IOException {
        if (zip != null) {
            ZipEntry e = zip.getEntry(entry);
            if (e == null) throw new IOException("Not found in the Hytale assets: " + entry);
            try (var in = zip.getInputStream(e)) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        }
        return Files.readString(dir.resolve(entry));
    }

    public boolean hasGeneratorAssets() { return !generator.isEmpty(); }

    public int prefabCount() { return prefabs.size(); }

    /** A generator file parsed with the node editor's $-keys removed (keys compare without regard to case). */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> generatorFile(String relPath) {
        return (Map<String, Object>) parsed.computeIfAbsent(relPath, k -> {
            try {
                Object o = new JsonAstCompiler.SimpleJsonParser(read(generator.get(k))).parseValue();
                return HytaleImport.stripEditorKeys(o);
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException("Could not read Hytale asset " + k + ": " + e.getMessage(), e);
            }
        });
    }

    /** The raw text of a generator file. */
    public String generatorText(String relPath) throws IOException { return read(generator.get(relPath)); }

    /** Every generator file's export names become importable (Imported nodes find them by name). */
    public void registerExports(JsonAstCompiler.CompileContext ctx) {
        for (String f : generator.keySet()) {
            try { JsonAstCompiler.collectExports(generatorFile(f), ctx); } catch (RuntimeException ignored) { }
        }
    }

    /** The relative path of a biome file by file name or by its {@code Name} field, or null. */
    public String biomePath(String name) {
        for (String f : generator.keySet()) {
            if (!f.startsWith("Biomes/")) continue;
            String stem = f.substring(f.lastIndexOf('/') + 1, f.length() - 5);
            if (stem.equals(name)) return f;
        }
        for (String f : generator.keySet()) {
            if (!f.startsWith("Biomes/")) continue;
            try { if (name.equals(String.valueOf(generatorFile(f).get("Name")))) return f; } catch (RuntimeException ignored) { }
        }
        return null;
    }

    public String worldStructurePath(String name) {
        String f = "WorldStructures/" + name + ".json";
        return generator.containsKey(f) ? f : null;
    }

    public List<String> worldStructures() {
        List<String> out = new ArrayList<>();
        for (String f : generator.keySet()) if (f.startsWith("WorldStructures/") && f.indexOf('/', 16) < 0) out.add(f.substring(16, f.length() - 5));
        return out;
    }

    // ------------------------------------------------------------------ prefabs

    private static String normalizePrefabPath(String id) {
        String s = id.replace('\\', '/');
        if (s.startsWith(PREFABS)) s = s.substring(PREFABS.length());
        if (s.endsWith(".prefab.json")) s = s.substring(0, s.length() - ".prefab.json".length());
        return s;
    }

    /** The prefab at a path (a file under Server/Prefabs, without its extension), or null. */
    public synchronized Prefab prefab(String id) {
        String p = normalizePrefabPath(id);
        if (!prefabs.containsKey(p)) return null;
        return prefabCache.computeIfAbsent(p, k -> {
            try { return convertPrefab(k, read(prefabs.get(k))); } catch (IOException | RuntimeException e) { return null; }
        });
    }

    /** The prefabs inside a folder (recursively), by path; empty when the path is not a folder. */
    public List<String> prefabFolder(String path) {
        String p = normalizePrefabPath(path);
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (prefabs.containsKey(p)) return List.of();
        List<String> out = new ArrayList<>();
        String prefix = p + "/";
        for (String k : prefabs.keySet()) if (k.startsWith(prefix)) out.add(k);
        return out;
    }

    /** Hytale's prefab JSON ({ anchorX.., blocks: [{x, y, z, name, rotation}] }) as an engine prefab; names map to Minecraft blocks. */
    static Prefab convertPrefab(String id, String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray blocks = root.has("blocks") ? root.getAsJsonArray("blocks") : new JsonArray();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (JsonElement e : blocks) {
            JsonObject b = e.getAsJsonObject();
            int x = b.get("x").getAsInt(), y = b.get("y").getAsInt(), z = b.get("z").getAsInt();
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        if (blocks.isEmpty()) { minX = minY = minZ = 0; maxX = maxY = maxZ = 0; }
        Map<String, Integer> paletteIds = new LinkedHashMap<>();
        List<Prefab.PaletteEntry> palette = new ArrayList<>();
        List<Prefab.VoxelEntry> voxels = new ArrayList<>();
        for (JsonElement e : blocks) {
            JsonObject b = e.getAsJsonObject();
            String name = b.get("name").getAsString();
            String mc = HytaleBlocks.toMinecraft(name);
            if (mc == null) mc = name;
            if ("minecraft:air".equals(mc)) continue;
            Integer idx = paletteIds.get(mc);
            if (idx == null) { idx = palette.size(); paletteIds.put(mc, idx); palette.add(Prefab.PaletteEntry.of(idx, mc, Map.of())); }
            voxels.add(new Prefab.VoxelEntry(b.get("x").getAsInt() - minX, b.get("y").getAsInt() - minY, b.get("z").getAsInt() - minZ, idx));
        }
        int ax = root.has("anchorX") ? root.get("anchorX").getAsInt() : 0, ay = root.has("anchorY") ? root.get("anchorY").getAsInt() : 0, az = root.has("anchorZ") ? root.get("anchorZ").getAsInt() : 0;
        return new Prefab(id, 2, new Prefab.Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1), new Prefab.Vec3i(ax - minX, ay - minY, az - minZ), palette, voxels, List.of(), List.of());
    }

    @Override
    public void close() throws IOException { if (zip != null) zip.close(); }
}