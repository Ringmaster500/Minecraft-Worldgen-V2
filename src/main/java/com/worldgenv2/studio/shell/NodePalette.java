package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.graph.NodeSchemas;
import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Searchable popup of node types. Type to filter, Up/Down + Enter or click to pick. */
final class NodePalette extends UiRoot.Overlay {
    private static final int ROW = 14, MAX_ROWS = 14, WIDTH = 230;

    private final TextField search = new TextField().placeholder("Search nodes...");
    private final List<NodeSchemas.NodeType> all;
    private List<NodeSchemas.NodeType> shown = new ArrayList<>();
    private final Consumer<NodeSchemas.NodeType> onPick;
    private final String title;
    private int sel, scroll;

    private NodePalette(String title, List<NodeSchemas.NodeType> candidates, Consumer<NodeSchemas.NodeType> onPick) {
        this.title = title;
        this.all = candidates;
        this.onPick = onPick;
        add(search);
        search.onChange = s -> refilter();
        search.onEnter = s -> pick(sel);
        refilter();
    }

    static NodePalette open(UiRoot root, int x, int y, String title, List<NodeSchemas.NodeType> candidates,
                            Consumer<NodeSchemas.NodeType> onPick) {
        NodePalette p = new NodePalette(title, candidates, onPick);
        p.w = WIDTH;
        p.h = 36 + MAX_ROWS * ROW + 6;
        p.x = Math.max(2, Math.min(x, root.vw - p.w - 2));
        p.y = Math.max(2, Math.min(y, root.vh - p.h - 2));
        root.showOverlay(p);
        p.search.setBounds(p.x + 6, p.y + 16, p.w - 12, Ui.FIELD_H);
        p.search.focus();
        return p;
    }

    private void refilter() {
        String q = search.text().trim().toLowerCase();
        shown = new ArrayList<>();
        for (NodeSchemas.NodeType t : all) if (q.isEmpty() || t.name().toLowerCase().contains(q)) shown.add(t);
        // prefix matches first
        if (!q.isEmpty()) shown.sort((a, b) -> Boolean.compare(!b.name().toLowerCase().startsWith(q), !a.name().toLowerCase().startsWith(q)));
        sel = 0;
        scroll = 0;
    }

    @Override public void dismiss() { if (root != null) root.closeOverlay(this); }

    @Override
    public void layout() {
        search.setBounds(x + 6, y + 16, w - 12, Ui.FIELD_H);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x + 2, y + 2, w, h, 0x66000000);
        g.box(x, y, w, h, Ui.BG_RAISED, Ui.LINE_STRONG);
        g.textMid(title, x + 8, y + 1, 14, Ui.TEXT_FAINT);
        int top = y + 36;
        g.clip(x + 1, top, w - 2, MAX_ROWS * ROW);
        for (int r = 0; r < MAX_ROWS; r++) {
            int i = r + scroll;
            if (i >= shown.size()) break;
            NodeSchemas.NodeType t = shown.get(i);
            int ry = top + r * ROW;
            boolean hov = Gfx.in(mx, my, x + 1, ry, w - 2, ROW);
            if (hov) sel = i;
            if (i == sel) g.fill(x + 1, ry, w - 2, ROW, Ui.BG_ACTIVE);
            g.fill(x + 6, ry + 5, 4, 4, NodeSchemas.kindColor(t.kind()));
            g.textFit(t.name(), x + 14, ry, ROW, w - 90, i == sel ? Ui.TEXT : Ui.TEXT_DIM);
            g.textRight(t.kind(), x + w - 8, ry, ROW, Ui.TEXT_FAINT);
        }
        if (shown.isEmpty()) g.textMid("No matching nodes", x + 8, top, ROW, Ui.TEXT_FAINT);
        g.unclip();
    }

    private void pick(int i) {
        if (i < 0 || i >= shown.size()) return;
        NodeSchemas.NodeType t = shown.get(i);
        dismiss();
        onPick.accept(t);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        int r = (int) ((my - (y + 36)) / ROW);
        if (my >= y + 36 && r >= 0 && r < MAX_ROWS) pick(r + scroll);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - MAX_ROWS), scroll - (int) Math.signum(dy) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        switch (key) {
            case Ui.KEY_DOWN -> { sel = Math.min(shown.size() - 1, sel + 1); if (sel >= scroll + MAX_ROWS) scroll++; return true; }
            case Ui.KEY_UP -> { sel = Math.max(0, sel - 1); if (sel < scroll) scroll = sel; return true; }
            case Ui.KEY_ENTER, Ui.KEY_KP_ENTER -> { pick(sel); return true; }
            default -> { return false; }
        }
    }
}
