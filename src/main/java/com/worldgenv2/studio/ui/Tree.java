package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Hierarchical list with section headers, selection, per-row markers and "+" quick-add buttons. */
public class Tree extends Widget {

    public static final class Node {
        public String id, label;
        public String hint = "";
        public int dot;           // colored square before the label
        public boolean header;    // section header row
        public boolean expanded = true;
        public boolean addable;   // header shows a "+" button
        public boolean dirty;     // unsaved marker
        public int marker;        // problem color (0 = none)
        public Object data;
        public final List<Node> kids = new ArrayList<>();
        public Node parent;

        public Node(String id, String label) { this.id = id; this.label = label; }

        public Node add(Node k) { k.parent = this; kids.add(k); return k; }
        int depth() { int d = 0; for (Node p = parent; p != null && p.parent != null; p = p.parent) d++; return d; }
    }

    private static final int ROW = 15;

    public Node rootNode = new Node("", "");
    public String selectedId;
    public String filter = "";
    public Consumer<Node> onOpen;
    public Consumer<Node> onAdd;
    public BiConsumer<Node, int[]> onContext;
    private int scroll;
    private final List<Node> rows = new ArrayList<>();
    private final List<Integer> rowDepth = new ArrayList<>();

    private void flatten() {
        rows.clear();
        rowDepth.clear();
        String f = filter.toLowerCase();
        for (Node n : rootNode.kids) walk(n, 0, f);
    }

    private boolean matches(Node n, String f) {
        if (f.isEmpty()) return true;
        if (!n.header && (n.label.toLowerCase().contains(f) || n.id.toLowerCase().contains(f))) return true;
        for (Node k : n.kids) if (matches(k, f)) return true;
        return false;
    }

    private void walk(Node n, int depth, String f) {
        if (!matches(n, f)) return;
        rows.add(n);
        rowDepth.add(depth);
        if ((n.expanded || !f.isEmpty()) && !n.kids.isEmpty()) for (Node k : n.kids) walk(k, depth + 1, f);
    }

    public Node find(String id) {
        return find(rootNode, id);
    }

    private Node find(Node n, String id) {
        if (id != null && id.equals(n.id)) return n;
        for (Node k : n.kids) { Node r = find(k, id); if (r != null) return r; }
        return null;
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        flatten();
        int maxScroll = Math.max(0, rows.size() * ROW - h);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        g.clip(x, y, w, h);
        for (int i = 0; i < rows.size(); i++) {
            int ry = y + i * ROW - scroll;
            if (ry + ROW < y || ry > y + h) continue;
            Node n = rows.get(i);
            int d = rowDepth.get(i);
            boolean hov = Gfx.in(mx, my, x, ry, w, ROW) && Gfx.in(mx, my, x, y, w, h);
            boolean sel = !n.header && n.id.equals(selectedId);
            if (sel) { g.fill(x, ry, w, ROW, Ui.BG_ACTIVE); g.fill(x, ry, 2, ROW, Ui.ACCENT); }
            else if (hov) g.fill(x, ry, w, ROW, Ui.BG_HOVER);
            int tx = x + 6 + d * 10;
            if (n.header) {
                g.textMid(n.expanded ? "v" : ">", tx, ry, ROW, Ui.TEXT_FAINT);
                String t = n.label.toUpperCase();
                g.textMid(t, tx + 9, ry, ROW, Ui.TEXT_DIM);
                String cnt = String.valueOf(n.kids.size());
                g.textMid(cnt, tx + 9 + g.width(t) + 6, ry, ROW, Ui.TEXT_FAINT);
                if (n.addable) {
                    boolean ph = Gfx.in(mx, my, x + w - 16, ry, 14, ROW);
                    g.textRight("+", x + w - 6, ry, ROW, ph ? Ui.ACCENT : Ui.TEXT_FAINT);
                }
            } else {
                if (!n.kids.isEmpty()) g.textMid(n.expanded ? "v" : ">", tx, ry, ROW, Ui.TEXT_FAINT);
                int lx = tx + (n.kids.isEmpty() ? 0 : 9);
                if (n.dot != 0) { g.fill(lx, ry + ROW / 2 - 2, 4, 4, n.dot); lx += 8; }
                int right = x + w - 6;
                if (n.marker != 0) { g.fill(right - 4, ry + ROW / 2 - 2, 4, 4, n.marker); right -= 9; }
                if (n.dirty) { g.textRight("*", right, ry, ROW, Ui.ACCENT); right -= 8; }
                g.textFit(n.label, lx, ry, ROW, right - lx, sel ? Ui.TEXT : Ui.TEXT_DIM);
            }
        }
        g.unclip();
        if (rows.size() * ROW > h) {
            int th = Math.max(12, h * h / (rows.size() * ROW));
            int ty = y + (int) ((h - th) * (scroll / (double) maxScroll));
            g.fill(x + w - 2, ty, 2, th, Ui.LINE_STRONG);
        }
    }

    private Node rowAt(double my) {
        int i = (int) ((my - y + scroll) / ROW);
        return i >= 0 && i < rows.size() ? rows.get(i) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        flatten();
        Node n = rowAt(my);
        if (n == null) return false;
        if (button == 1) {
            if (!n.header) selectedId = n.id;
            if (onContext != null) onContext.accept(n, new int[]{(int) mx, (int) my});
            return true;
        }
        if (button != 0) return false;
        if (n.header) {
            if (n.addable && mx >= x + w - 18 && onAdd != null) onAdd.accept(n);
            else n.expanded = !n.expanded;
            return true;
        }
        if (!n.kids.isEmpty() && mx < x + 18 + rowDepthOf(n) * 10) { n.expanded = !n.expanded; return true; }
        selectedId = n.id;
        if (onOpen != null) onOpen.accept(n);
        return true;
    }

    private int rowDepthOf(Node n) {
        int i = rows.indexOf(n);
        return i < 0 ? 0 : rowDepth.get(i);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy) * ROW * 2;
        return true;
    }
}
