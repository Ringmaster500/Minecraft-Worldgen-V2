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

    public enum View { FORM, JSON }

    private static final StudioApp INSTANCE = new StudioApp();

    public static StudioApp get() { return INSTANCE; }

    public StudioProject project;
    public final List<String> openDocs = new ArrayList<>();
    public String activeDoc;
    public final Map<String, View> views = new HashMap<>();
    public boolean problemsOpen;
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
            say("Opened project '" + name + "'");
        } catch (IOException e) {
            say("Could not open '" + name + "': " + e.getMessage(), Ui.RED);
        }
    }

    public void createProject(String name) {
        try {
            setProject(ProjectStore.create(name));
            say("Created project '" + project.name + "' with starter biomes and dimensions", Ui.GREEN);
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

    /** Combined stamp: changes whenever either app state or project content changed. */
    public long stamp() {
        return (project == null ? 0 : (long) project.revision * 1_000_003L) + uiRevision;
    }
}
