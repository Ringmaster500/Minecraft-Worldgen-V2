package com.worldgenv2.studio.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A modpack worldgen project on disk: the single source of truth for everything the Studio edits.
 * <ul>
 *   <li>UI code never touches files; it calls {@link #edit}, {@link #create}, {@link #save}.</li>
 *   <li>Every edit is undoable and revalidated; listeners are told what changed.</li>
 * </ul>
 */
public final class StudioProject {

    private record Edit(String path, String before, String after, String label, long time) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public final String name;
    public final Path dir;
    private final Map<String, Asset> assets = new TreeMap<>();
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    /** Bumps on every change; UI uses it to know when to rebuild cached views. */
    public int revision;

    private StudioProject(String name, Path dir) {
        this.name = name;
        this.dir = dir;
    }

    // ---------------------------------------------------------------- loading / saving

    public static StudioProject load(String name, Path dir) throws IOException {
        StudioProject p = new StudioProject(name, dir);
        Files.createDirectories(dir);
        Path world = dir.resolve(Asset.WORLD_PATH);
        if (Files.exists(world)) p.readFile(Asset.WORLD_PATH, AssetType.WORLD);
        else {
            Asset w = new Asset(Asset.WORLD_PATH, AssetType.WORLD, Templates.defaultWorld());
            w.dirty = true;
            p.assets.put(w.path, w);
        }
        for (AssetType t : AssetType.values()) {
            if (t.folder.isEmpty()) continue;
            Path folder = dir.resolve(t.folder);
            if (!Files.isDirectory(folder)) continue;
            try (Stream<Path> s = Files.list(folder)) {
                for (Path f : (Iterable<Path>) s.filter(f -> f.toString().endsWith(".json")).sorted()::iterator) {
                    p.readFile(t.folder + "/" + f.getFileName(), t);
                }
            }
        }
        p.validateAll();
        return p;
    }

    private void readFile(String path, AssetType type) {
        Asset a = new Asset(path, type, new JsonObject());
        try {
            String raw = Files.readString(dir.resolve(path), StandardCharsets.UTF_8);
            a.rawText = raw;
            JsonElement e = JsonParser.parseString(raw);
            if (e.isJsonObject()) a.json = e.getAsJsonObject();
            else a.parseError = "root must be a JSON object";
        } catch (Exception ex) {
            a.parseError = String.valueOf(ex.getMessage());
        }
        assets.put(path, a);
    }

    /** Writes every dirty asset. Returns the number of files written. */
    public int saveAll() {
        int n = 0;
        for (Asset a : assets.values()) if (a.dirty && save(a)) n++;
        fire();
        return n;
    }

    public boolean save(Asset a) {
        if (a.parseError != null && a.rawText != null && !a.dirty) return false;
        try {
            Path target = dir.resolve(a.path);
            Files.createDirectories(target.getParent() == null ? dir : target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(a.json) + "\n", StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            a.dirty = false;
            a.parseError = null;
            return true;
        } catch (IOException e) {
            a.problems.add(new Asset.Problem(Asset.Severity.ERROR, a.path, "Could not save: " + e.getMessage()));
            return false;
        }
    }

    /** Adds an asset without saving; used by templates. */
    void putAsset(Asset a) {
        assets.put(a.path, a);
        revision++;
    }

    // ---------------------------------------------------------------- queries

    public Collection<Asset> assets() { return assets.values(); }
    public Asset get(String path) { return assets.get(path); }
    public boolean hasAsset(String path) { return assets.containsKey(path); }
    public Asset world() { return assets.get(Asset.WORLD_PATH); }

    public List<Asset> assetsOf(AssetType t) {
        List<Asset> out = new ArrayList<>();
        for (Asset a : assets.values()) if (a.type == t) out.add(a);
        if (t == AssetType.DIMENSION) {
            // vanilla order first (Overworld, Nether, End), then custom dimensions alphabetically
            List<String> order = List.of("overworld", "the_nether", "the_end");
            out.sort(Comparator.comparingInt((Asset a) -> { int i = order.indexOf(a.id()); return i < 0 ? 99 : i; })
                    .thenComparing(Asset::id));
        }
        return out;
    }

    public boolean isDirty() {
        for (Asset a : assets.values()) if (a.dirty) return true;
        return false;
    }

    public int dirtyCount() {
        int n = 0;
        for (Asset a : assets.values()) if (a.dirty) n++;
        return n;
    }

    public List<Asset.Problem> allProblems() {
        List<Asset.Problem> out = new ArrayList<>();
        for (Asset a : assets.values()) out.addAll(a.allProblems());
        out.sort(Comparator.comparingInt((Asset.Problem x) -> x.severity().ordinal()).thenComparing(Asset.Problem::assetPath));
        return out;
    }

    public int count(Asset.Severity s) {
        int n = 0;
        for (Asset a : assets.values()) for (Asset.Problem pr : a.allProblems()) if (pr.severity() == s) n++;
        return n;
    }

    // ---------------------------------------------------------------- editing

    /** Applies a mutation as one undoable step; rapid repeats of the same label on one asset coalesce. */
    public void edit(Asset a, String label, Consumer<JsonObject> mutator) {
        String before = a.json.toString();
        mutator.accept(a.json);
        String after = a.json.toString();
        if (before.equals(after)) return;
        a.dirty = true;
        a.parseError = null;
        long now = System.currentTimeMillis();
        Edit last = undo.peekLast();
        if (last != null && last.path.equals(a.path) && last.label.equals(label) && now - last.time < 1200) {
            undo.removeLast();
            undo.addLast(new Edit(a.path, last.before, after, label, now));
        } else {
            undo.addLast(new Edit(a.path, before, after, label, now));
            while (undo.size() > 200) undo.removeFirst();
        }
        redo.clear();
        Validator.validate(this, a);
        if (a.type == AssetType.BIOME) revalidateType(AssetType.DIMENSION);
        revision++;
        fire();
    }

    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public String undoLabel() { return undo.isEmpty() ? null : undo.peekLast().label; }
    public String redoLabel() { return redo.isEmpty() ? null : redo.peekLast().label; }

    public String undo() { return step(undo, redo, true); }
    public String redo() { return step(redo, undo, false); }

    private String step(Deque<Edit> from, Deque<Edit> to, boolean back) {
        Edit e = from.pollLast();
        if (e == null) return null;
        Asset a = assets.get(e.path);
        if (a == null) return null;
        a.json = JsonParser.parseString(back ? e.before : e.after).getAsJsonObject();
        a.dirty = true;
        to.addLast(e);
        Validator.validate(this, a);
        if (a.type == AssetType.BIOME) revalidateType(AssetType.DIMENSION);
        revision++;
        fire();
        return e.label;
    }

    // ---------------------------------------------------------------- structural operations

    public static String sanitize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("^_+|_+$", "");
    }

    /** @return error message, or null when the id is acceptable for a new asset of this type. */
    public String checkNewId(AssetType t, String raw) {
        String id = sanitize(raw);
        if (id.isEmpty()) return "Enter a name (letters, numbers, underscores).";
        if (assets.containsKey(t.folder + "/" + id + ".json")) return "A " + t.label.toLowerCase() + " named '" + id + "' already exists.";
        return null;
    }

    public Asset create(AssetType t, String rawName) {
        String id = sanitize(rawName);
        JsonObject json = switch (t) {
            case BIOME -> Templates.newBiome(id);
            case DIMENSION -> Templates.newDimension(id, this);
            default -> new JsonObject();
        };
        Asset a = new Asset(t.folder + "/" + id + ".json", t, json);
        a.dirty = true;
        assets.put(a.path, a);
        Validator.validate(this, a);
        revision++;
        save(a);
        fire();
        return a;
    }

    public Asset duplicate(Asset src, String rawName) {
        String id = sanitize(rawName);
        Asset a = new Asset(src.type.folder + "/" + id + ".json", src.type, src.json.deepCopy());
        if (a.type == AssetType.BIOME) a.json.addProperty("name", "worldgenv2:" + id);
        a.dirty = true;
        assets.put(a.path, a);
        Validator.validate(this, a);
        revision++;
        save(a);
        fire();
        return a;
    }

    /** Moves the file into {@code .trash/} rather than deleting it, so a mistake is recoverable. */
    public void delete(Asset a) {
        try {
            Path src = dir.resolve(a.path);
            if (Files.exists(src)) {
                Path trash = dir.resolve(".trash");
                Files.createDirectories(trash);
                Files.move(src, trash.resolve(a.id() + "-" + System.currentTimeMillis() + ".json"));
            }
        } catch (IOException ignored) {}
        assets.remove(a.path);
        undo.removeIf(e -> e.path.equals(a.path));
        redo.removeIf(e -> e.path.equals(a.path));
        revalidateType(AssetType.DIMENSION);
        revision++;
        fire();
    }

    public Asset rename(Asset a, String rawName) {
        String id = sanitize(rawName);
        Asset n = new Asset(a.type.folder + "/" + id + ".json", a.type, a.json);
        n.dirty = true;
        try { Files.deleteIfExists(dir.resolve(a.path)); } catch (IOException ignored) {}
        assets.remove(a.path);
        assets.put(n.path, n);
        // keep dimension pools pointing at the renamed biome
        if (a.type == AssetType.BIOME) {
            for (Asset d : assetsOf(AssetType.DIMENSION)) {
                var arr = J.arr(d.json, "biomes");
                if (arr == null) continue;
                boolean touched = false;
                for (JsonElement e : arr) {
                    if (e.isJsonObject() && a.id().equals(J.str(e.getAsJsonObject(), "biome", ""))) {
                        e.getAsJsonObject().addProperty("biome", id);
                        touched = true;
                    }
                }
                if (touched) { d.dirty = true; save(d); }
            }
        }
        undo.removeIf(e -> e.path.equals(a.path));
        redo.removeIf(e -> e.path.equals(a.path));
        save(n);
        validateAll();
        revision++;
        fire();
        return n;
    }

    public void validateAll() {
        for (Asset a : assets.values()) Validator.validate(this, a);
    }

    private void revalidateType(AssetType t) {
        for (Asset a : assets.values()) if (a.type == t) Validator.validate(this, a);
    }

    // ---------------------------------------------------------------- listeners

    public void onChange(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }
    private void fire() { for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    public static String pretty(JsonElement e) { return GSON.toJson(e); }
}
