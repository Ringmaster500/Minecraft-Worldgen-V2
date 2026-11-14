package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.ui.*;

/**
 * Centre area. Hosts the tab strip and, for the active tab, a header (title, path, view switch)
 * above the asset's editor. Editors are cached per document so scroll position survives tab switches.
 */
final class DocPane extends Widget {
    private static final int HEADER_H = 24;

    private final StudioApp app;
    private final TabBar tabs = add(new TabBar());
    private final Button formBtn, jsonBtn, mapBtn, graphBtn;
    private final MapPreview preview;
    private String shownDoc;
    private Widget body;
    private final java.util.Map<String, Editor> editors = new java.util.HashMap<>();
    private final java.util.Map<String, JsonView> jsons = new java.util.HashMap<>();
    private final java.util.Map<String, GraphEditor> graphs = new java.util.HashMap<>();
    private long builtStamp = -1;

    DocPane(StudioApp app) {
        this.app = app;
        tabs.onSelect = p -> app.openDoc(p);
        tabs.onClose = p -> app.closeDoc(p);
        formBtn = add(new Button("Form", () -> app.setView(app.activeDoc, StudioApp.View.FORM)));
        jsonBtn = add(new Button("JSON", () -> app.setView(app.activeDoc, StudioApp.View.JSON)));
        graphBtn = add(new Button("Graph", () -> app.setView(app.activeDoc, StudioApp.View.GRAPH)));
        mapBtn = add(new Button("Map", () -> { app.previewOpen = !app.previewOpen; app.uiRevision++; }));
        mapBtn.tooltip = "Show / hide the live map preview";
        preview = add(new MapPreview(app));
    }

    private boolean showMap() {
        Asset a = app.activeAsset();
        return a != null && a.type == AssetType.DIMENSION && app.previewOpen && body instanceof Editor
                && "worldgenv2".equals(com.worldgenv2.studio.project.J.str(a.json, "generator", "worldgenv2"));
    }

    private void refreshTabs() {
        tabs.tabs.clear();
        for (String p : app.openDocs) {
            Asset a = app.project.get(p);
            if (a == null) continue;
            TabBar.Tab t = new TabBar.Tab(p, a.displayName());
            t.dirty = a.dirty;
            t.dot = a.worstSeverityColor() != 0 ? a.worstSeverityColor() : a.type.color;
            tabs.tabs.add(t);
        }
        tabs.activeId = app.activeDoc;
    }

    private Widget makeBody(Asset a) {
        if (app.viewOf(a.path) == StudioApp.View.GRAPH && (a.type == AssetType.BIOME || a.type == AssetType.ZONE || a.type == AssetType.RULE || a.type == AssetType.DIMENSION) && a.parseError == null) {
            return graphs.computeIfAbsent(a.path, k -> new GraphEditor(app, a));
        }
        if (app.viewOf(a.path) == StudioApp.View.JSON || a.parseError != null || a.type == AssetType.PREFAB) {
            return jsons.computeIfAbsent(a.path, k -> new JsonView(app, a));
        }
        return editors.computeIfAbsent(a.path, k -> {
            Editor e = switch (a.type) {
                case WORLD -> new WorldEditor(app, a);
                case DIMENSION -> new DimensionEditor(app, a);
                case ZONE -> new ZoneEditor(app, a);
                case RULE -> new RuleEditor(app, a);
                default -> new BiomeEditor(app, a);
            };
            e.rebuild();
            return e;
        });
    }

    @Override
    public void layout() {
        tabs.setBounds(x, y, w, Ui.TAB_H);
        int by = y + Ui.TAB_H + HEADER_H;
        boolean map = showMap();
        int bodyW = map ? Math.max(260, w * 52 / 100) : w;
        if (body != null) body.setBounds(x, by, bodyW, Math.max(0, y + h - by));
        preview.visible = map;
        if (map) preview.setBounds(x + bodyW, by, w - bodyW, Math.max(0, y + h - by));
        graphBtn.setBounds(x + w - 136, y + Ui.TAB_H + 4, 42, 16);
        formBtn.setBounds(x + w - 90, y + Ui.TAB_H + 4, 40, 16);
        jsonBtn.setBounds(x + w - 46, y + Ui.TAB_H + 4, 40, 16);
        mapBtn.setBounds(x + w - 128, y + Ui.TAB_H + 4, 34, 16);
    }

    @Override
    protected void renderChildren(Gfx g, int mx, int my) {
        Asset a = app.activeAsset();
        // drop caches for deleted/renamed assets
        editors.keySet().removeIf(p -> app.project.get(p) == null);
        jsons.keySet().removeIf(p -> app.project.get(p) == null);
        graphs.keySet().removeIf(p -> app.project.get(p) == null);

        long st = app.stamp();
        if (st != builtStamp || a == null || !a.path.equals(shownDoc)) {
            refreshTabs();
            builtStamp = st;
        }
        if (a == null) { body = null; shownDoc = null; }
        else {
            Widget want = makeBody(a);
            if (want != body || !a.path.equals(shownDoc)) {
                if (body != null) remove(body);
                body = add(want);
                shownDoc = a.path;
                layout();
            }
            if (body instanceof Editor e) e.sync();
        }
        StudioApp.View vw = a == null ? StudioApp.View.FORM : app.viewOf(a.path);
        boolean json = a != null && vw == StudioApp.View.JSON;
        boolean graph = a != null && vw == StudioApp.View.GRAPH && (a.type == AssetType.BIOME || a.type == AssetType.ZONE || a.type == AssetType.RULE || a.type == AssetType.DIMENSION);
        graphBtn.visible = a != null && (a.type == AssetType.BIOME || a.type == AssetType.ZONE || a.type == AssetType.RULE || a.type == AssetType.DIMENSION) && a.parseError == null;
        graphBtn.selected = graph;
        mapBtn.visible = a != null && a.type == AssetType.DIMENSION && !json;
        mapBtn.selected = app.previewOpen;
        if (a != null && a.type == AssetType.DIMENSION) { preview.target(a.id()); layout(); }
        else { preview.visible = false; }
        formBtn.visible = a != null && a.type != AssetType.PREFAB && a.parseError == null;
        jsonBtn.visible = a != null && a.type != AssetType.PREFAB;
        formBtn.selected = !json && !graph;
        jsonBtn.selected = json;
        super.renderChildren(g, mx, my);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, Ui.BG_PANEL);
        Asset a = app.activeAsset();
        if (a == null) { drawEmpty(g); return; }
        int hy = y + Ui.TAB_H;
        g.fill(x, hy, w, HEADER_H, Ui.BG_PANEL);
        g.hline(x, hy + HEADER_H - 1, w, Ui.LINE);
        g.fill(x + 8, hy + HEADER_H / 2 - 3, 6, 6, a.type.color);
        g.textMid(a.displayName(), x + 20, hy, HEADER_H, Ui.TEXT);
        g.textMid(a.path + (a.dirty ? "  - unsaved" : ""), x + 28 + g.width(a.displayName()), hy, HEADER_H, a.dirty ? Ui.ACCENT : Ui.TEXT_FAINT);
        if (a.parseError != null) {
            g.textRight("File has JSON errors - fix them in an external editor", x + w - 100, hy, HEADER_H, Ui.RED);
        }
    }

    /** Which pane the Home screen draws into when nothing is open. */
    void drawEmpty(Gfx g) {
        g.textCentered("Select something in the Explorer to start editing.", x, y + h / 2 - 20, w, 12, Ui.TEXT_DIM);
        g.textCentered("Ctrl+S save   Ctrl+Z undo   Ctrl+Y redo   Ctrl+J problems", x, y + h / 2 - 4, w, 12, Ui.TEXT_FAINT);
    }
}
