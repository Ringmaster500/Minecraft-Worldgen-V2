package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.project.ProjectStore;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.studio.ui.Ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session state of the Studio, independent of the Screen instance so it survives closing the UI,
 * hot-reloading code, and reopening. All commands (open, save, undo...) live here; widgets only call in.
 */
public final class StudioApp {

    public enum View { FORM, GRAPH, JSON }

    private static final StudioApp INSTANCE = new StudioApp();

    public static StudioApp get() { return INSTANCE; }

    public StudioProject project;
    public final List<String> openDocs = new ArrayList<>();
    public String activeDoc;
    public final Map<String, View> views = new HashMap<>();
    public boolean problemsOpen;
    public boolean previewOpen = true;
    public int uiScale = 2;
    /** Bumped by app-level changes (doc open/close, status) so views know to refresh. */
    public int uiRevision;

    private String status = "";
    private int statusColor = Ui.TEXT_DIM;
    private long statusUntil;

    private StudioApp() {}

    // ---- status ----

    public void say(String msg) { say(msg, Ui.TEXT_DIM); }

    public void say(String msg, int color) {
        status = msg;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 6000;
        uiRevision++;
    }

    public String status() { return System.currentTimeMillis() < statusUntil ? status : ""; }
    public int statusColor() { return statusColor; }

    // ---- project lifecycle ----

    public void openProject(String name) {
        try {
            setProject(ProjectStore.open(name));
            say("Opened '" + name + "'");
        } catch (IOException e) {
            say("Could not open '" + name + "': " + e.getMessage(), Ui.RED);
        }
    }

    public void createProject(String name, String presetId) {
        try {
            setProject(ProjectStore.create(name, presetId));
            say("Created '" + project.name + "'", Ui.GREEN);
            openDoc(Asset.WORLD_PATH);
        } catch (IOException e) {
            say("Could not create project: " + e.getMessage(), Ui.RED);
        }
    }

    /** Adds a preset's biomes/prefabs (or a whole world's assets) to the open project without overwriting anything. */
    public void addFromPreset(String presetId) {
        if (project == null) return;
        var preset = com.worldgenv2.pack.WorldGenV2Addons.preset(presetId);
        if (preset == null) { say("Preset not found.", Ui.RED); return; }
        int n = project.importBundle(preset.bundle().get(), true, preset.isPack());
        uiRevision++;
        say(n == 0 ? "Nothing new to add from '" + preset.name() + "'." : "Added " + n + " asset" + (n == 1 ? "" : "s") + " from '" + preset.name() + "'. Save to keep them.", n == 0 ? Ui.TEXT_DIM : Ui.GREEN);
    }

    /** Writes the project as a bundle file addons can ship (worldgenv2_presets/). */
    public void exportPreset() {
        if (project == null) return;
        try {
            project.saveAll();
            java.nio.file.Path dir = java.nio.file.Path.of("config", "worldgenv2", "exports");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path out = dir.resolve(project.name + ".json");
            java.nio.file.Files.writeString(out, StudioProject.pretty(project.toBundle("custom:" + project.name, project.name, "", "world")));
            say("Exported preset to " + out + "  (drop it in an addon's worldgenv2_presets/ folder)", Ui.GREEN);
        } catch (IOException e) {
            say("Export failed: " + e.getMessage(), Ui.RED);
        }
    }

    public void createProject(String name) {
        try {
            setProject(ProjectStore.create(name));
            say("Created '" + project.name + "' with starter biomes and dimensions", Ui.GREEN);
            openDoc(Asset.WORLD_PATH);
        } catch (IOException e) {
            say("Could not create project: " + e.getMessage(), Ui.RED);
        }
    }

    private void setProject(StudioProject p) {
        project = p;
        openDocs.clear();
        views.clear();
        activeDoc = null;
        uiRevision++;
    }

    public void closeProject() {
        project = null;
        openDocs.clear();
        activeDoc = null;
        uiRevision++;
    }

    // ---- documents ----

    public void openDoc(String path) {
        if (project == null || project.get(path) == null) return;
        if (!openDocs.contains(path)) openDocs.add(path);
        activeDoc = path;
        uiRevision++;
    }

    public void closeDoc(String path) {
        int i = openDocs.indexOf(path);
        if (i < 0) return;
        openDocs.remove(i);
        if (path.equals(activeDoc)) activeDoc = openDocs.isEmpty() ? null : openDocs.get(Math.min(i, openDocs.size() - 1));
        uiRevision++;
    }

    public Asset activeAsset() {
        return project == null || activeDoc == null ? null : project.get(activeDoc);
    }

    public View viewOf(String path) { return views.getOrDefault(path, View.FORM); }

    public void setView(String path, View v) { views.put(path, v); uiRevision++; }

    /** Drops tabs whose assets no longer exist (after delete/rename). */
    public void pruneDocs() {
        if (project == null) return;
        boolean changed = openDocs.removeIf(p -> project.get(p) == null);
        if (activeDoc != null && project.get(activeDoc) == null) activeDoc = openDocs.isEmpty() ? null : openDocs.get(openDocs.size() - 1);
        if (changed) uiRevision++;
    }

    // ---- commands ----

    public void save() {
        if (project == null) return;
        int n = project.saveAll();
        say(n == 0 ? "Nothing to save." : "Saved " + n + " file" + (n == 1 ? "" : "s") + ".", n == 0 ? Ui.TEXT_DIM : Ui.GREEN);
    }

    public void undo() {
        if (project == null) return;
        String l = project.undo();
        say(l == null ? "Nothing to undo." : "Undid: " + l);
    }

    public void redo() {
        if (project == null) return;
        String l = project.redo();
        say(l == null ? "Nothing to redo." : "Redid: " + l);
    }

    /** Adds a ready-made biome (from a preset or the Hytale files) to the open setup and opens it. */
    public Asset importBiome(String name, com.google.gson.JsonObject content) {
        Asset a = project.addBiome(name, content);
        openDoc(a.path);
        say("Added biome '" + a.id() + "'. Save to keep it.", Ui.GREEN);
        return a;
    }

    public Asset createAsset(AssetType t, String name) {
        Asset a = project.create(t, name);
        openDoc(a.path);
        say("Created " + t.label.toLowerCase() + " '" + a.id() + "'", Ui.GREEN);
        return a;
    }

    public void duplicateAsset(Asset src, String name) {
        Asset a = project.duplicate(src, name);
        openDoc(a.path);
        say("Duplicated to '" + a.id() + "'", Ui.GREEN);
    }

    public void renameAsset(Asset src, String name) {
        boolean wasOpen = openDocs.contains(src.path);
        String oldPath = src.path;
        Asset a = project.rename(src, name);
        int i = openDocs.indexOf(oldPath);
        if (i >= 0) openDocs.set(i, a.path);
        if (oldPath.equals(activeDoc)) activeDoc = a.path;
        if (views.containsKey(oldPath)) views.put(a.path, views.remove(oldPath));
        pruneDocs();
        if (wasOpen) uiRevision++;
        say("Renamed to '" + a.id() + "'", Ui.GREEN);
    }

    public void deleteAsset(Asset a) {
        project.delete(a);
        pruneDocs();
        say("Moved '" + a.id() + "' to .trash/ in the project folder");
    }

    public void applyToWorld() { LiveWorld.apply(this); }

    /** In a world, the Studio edits the setup that world was made from. Called when the Studio opens. */
    public void syncToWorld() {
        if (!LiveWorld.inWorld() || !com.worldgenv2.pack.ProjectRuntime.isWorldLinked()) return;
        String want = com.worldgenv2.pack.ProjectRuntime.activeProjectName();
        if (want == null || (project != null && project.name.equals(want))) return;
        if (project != null && project.isDirty()) { say("This world uses the setup '" + want + "', but you have unsaved changes in '" + project.name + "'. Save, then reopen the Studio.", Ui.YELLOW); return; }
        if (com.worldgenv2.studio.project.ProjectStore.list().stream().anyMatch(e -> e.name().equals(want))) openProject(want);
    }

    public void regenAroundPlayer() { LiveWorld.regenWorld(this); }

    // ---- live preview: rebuild a chosen square of chunks after each edit ----
    public boolean live;
    public int liveRadius = 3;
    public int liveCx, liveCz;
    private long liveSeenRev = -1, liveChangedAt;
    private boolean liveBusy;

    public void setLive(boolean on) {
        live = on;
        if (on) {
            int[] c = LiveWorld.playerChunk();
            if (c == null) { live = false; say("Open a world first: Live preview rebuilds chunks of the world you are in.", Ui.YELLOW); return; }
            liveCx = c[0]; liveCz = c[1];
            liveSeenRev = project == null ? -1 : project.revision;
            say("Live preview on: the " + (2 * liveRadius + 1) + "x" + (2 * liveRadius + 1) + " chunks around you rebuild after each edit.", Ui.GREEN);
        } else say("Live preview off.");
    }

    /** Move the live region to where the player is now. */
    public void repinLive() {
        int[] c = LiveWorld.playerChunk();
        if (c == null) return;
        liveCx = c[0]; liveCz = c[1];
        liveSeenRev = -1;      // rebuild straight away
    }

    public void cycleLiveRadius() { liveRadius = liveRadius >= 6 ? 1 : liveRadius + 1; liveSeenRev = -1; }

    /** Called every frame: a short pause after the last edit, then the region rebuilds. */
    public void tickLive() {
        if (!live || project == null || liveBusy || !LiveWorld.inWorld()) return;
        long rev = project.revision;
        long now = System.currentTimeMillis();
        if (rev != liveSeenRev) {
            if (liveChangedAt == 0 || rev != liveSeenRevPending) { liveSeenRevPending = rev; liveChangedAt = now; return; }
            if (now - liveChangedAt < 600) return;      // still typing / dragging
            liveBusy = true;
            liveSeenRev = rev;
            liveChangedAt = 0;
            LiveWorld.liveRefresh(this, liveCx, liveCz, liveRadius, () -> liveBusy = false);
        }
    }
    private long liveSeenRevPending = -2;

    public void teleportPlayer(int x, int z) { LiveWorld.teleport(this, x, z); }

    public void teleportPlayer(int x, int z, int up) { LiveWorld.teleport(this, x, z, up); }

    public String probeWorld(int x, int z, int radius) { return LiveWorld.probe(x, z, radius); }

    public String heightsLine(int x, int z, int dx, int dz, int n) { return LiveWorld.heights(x, z, dx, dz, n); }

    public String censusWorld(int x, int z) { return LiveWorld.census(x, z); }
    public String hashArea(int x, int z, int r) { return LiveWorld.hashArea(x, z, r); }
    public String serverCommand(String cmd) { return LiveWorld.command(cmd); }
    public String spansNear(int x, int z, int radius) { return LiveWorld.spans(x, z, radius); }

    /** Combined stamp: changes whenever either app state or project content changed. */
    public long stamp() {
        return (project == null ? 0 : (long) project.revision * 1_000_003L) + uiRevision;
    }
}
