package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Left pane: every asset in the project, grouped by type, with search, quick-add and context menus. */
final class Explorer extends Widget {
    private final StudioApp app;
    private final Tree tree = new Tree();
    private final TextField search = new TextField().placeholder("Search assets...");
    private final Map<String, Boolean> expanded = new HashMap<>();
    private long builtStamp = -1;
    private String builtActive;

    Explorer(StudioApp app) {
        this.app = app;
        add(search);
        add(tree);
        search.onChange = s -> tree.filter = s.trim();
        tree.onOpen = n -> { if (n.data instanceof Asset a) app.openDoc(a.path); };
        tree.onAdd = n -> { if (n.data instanceof AssetType t && root != null) Dialogs.newAsset(root, app, t); };
        tree.onContext = (n, pos) -> contextMenu(n, pos[0], pos[1]);
    }

    @Override
    public void layout() {
        search.setBounds(x + 6, y + 24, w - 12, Ui.FIELD_H);
        tree.setBounds(x, y + 46, w, Math.max(0, h - 46));
    }

    private void contextMenu(Tree.Node n, int mx, int my) {
        if (!(n.data instanceof Asset a) || root == null) return;
        List<PopupMenu.Item> items = new ArrayList<>();
        items.add(PopupMenu.Item.of("Open", () -> app.openDoc(a.path)));
        if (a.type != AssetType.WORLD) {
            items.add(PopupMenu.Item.of("Duplicate...", () -> Dialogs.duplicate(root, app, a)));
            items.add(PopupMenu.Item.of("Rename...", () -> Dialogs.rename(root, app, a)));
            items.add(PopupMenu.Item.sep());
            items.add(PopupMenu.Item.danger("Delete...", () -> Dialogs.delete(root, app, a)));
        }
        PopupMenu.open(root, mx, my, 130, items);
    }

    private void rebuild() {
        for (Tree.Node h : tree.rootNode.kids) expanded.put(h.id, h.expanded);
        tree.rootNode.kids.clear();
        if (app.project == null) return;
        for (AssetType t : AssetType.values()) {
            List<Asset> list = app.project.assetsOf(t);
            Tree.Node header = new Tree.Node("hdr:" + t.name(), t.plural());
            header.header = true;
            header.addable = t == AssetType.DIMENSION || t == AssetType.BIOME;
            header.data = t;
            header.expanded = expanded.getOrDefault(header.id, true);
            for (Asset a : list) {
                Tree.Node leaf = new Tree.Node(a.path, a.displayName());
                leaf.data = a;
                leaf.dot = t == AssetType.BIOME ? Cat.color(com.worldgenv2.studio.project.J.str(a.json, "category", "")) : t.color;
                leaf.marker = a.worstSeverityColor();
                leaf.dirty = a.dirty;
                header.add(leaf);
            }
            if (t == AssetType.WORLD) {
                // the single world file is shown directly, without a collapsible section
                for (Tree.Node k : header.kids) { k.parent = tree.rootNode; tree.rootNode.kids.add(k); }
                continue;
            }
            tree.rootNode.add(header);
        }
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, Ui.BG_PANEL);
        g.vline(x + w - 1, y, h, Ui.LINE);
        g.textMid("EXPLORER", x + 8, y + 4, 16, Ui.TEXT_FAINT);
        if (app.project != null) g.textRight(g.fit(app.project.name, w - 80), x + w - 8, y + 4, 16, Ui.TEXT_DIM);
        if (builtStamp != app.stamp()) {
            rebuild();
            builtStamp = app.stamp();
        }
        tree.selectedId = app.activeDoc;
    }
}
