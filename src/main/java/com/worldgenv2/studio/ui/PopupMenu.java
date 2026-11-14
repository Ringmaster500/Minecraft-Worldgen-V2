package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;

/** Context menu / dropdown list. Closes on outside click, Escape, or after choosing an item. */
public class PopupMenu extends UiRoot.Overlay {

    public record Item(String label, int color, boolean enabled, boolean separator, Runnable action) {
        public static Item of(String label, Runnable action) { return new Item(label, Ui.TEXT, true, false, action); }
        public static Item colored(String label, int color, Runnable action) { return new Item(label, color, true, false, action); }
        public static Item danger(String label, Runnable action) { return new Item(label, Ui.RED, true, false, action); }
        public static Item disabled(String label) { return new Item(label, Ui.TEXT_FAINT, false, false, null); }
        public static Item sep() { return new Item("", 0, false, true, null); }
    }

    private static final int ROW = 15;
    private final List<Item> items;
    private int scroll;
    private final int maxRows;
    private int hoverIdx = -1;
    private int selectedIdx = -1;

    private PopupMenu(List<Item> items, int maxRows) {
        this.items = items;
        this.maxRows = maxRows;
    }

    public static PopupMenu open(UiRoot root, int x, int y, int minW, List<Item> items) {
        return open(root, x, y, minW, items, -1, 14);
    }

    public static PopupMenu open(UiRoot root, int x, int y, int minW, List<Item> items, int selected, int maxRows) {
        PopupMenu m = new PopupMenu(new ArrayList<>(items), maxRows);
        Gfx g = Probe.gfx();
        int w = minW;
        for (Item it : items) w = Math.max(w, g.width(it.label()) + 20);
        int rows = Math.min(items.size(), maxRows);
        int h = rows * ROW + 4;
        m.x = Math.max(2, Math.min(x, root.vw - w - 2));
        m.y = Math.max(2, Math.min(y, root.vh - h - 2));
        m.w = w;
        m.h = h;
        m.selectedIdx = selected;
        if (selected >= maxRows) m.scroll = Math.min(selected - maxRows / 2, items.size() - maxRows);
        root.showOverlay(m);
        return m;
    }

    @Override
    public void dismiss() {
        if (root != null) root.closeOverlay(this);
    }

    @Override
    public void layout() {
        setBoundsNoLayout();
    }

    private void setBoundsNoLayout() {}

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x + 2, y + 2, w, h, 0x66000000);
        g.box(x, y, w, h, Ui.BG_RAISED, Ui.LINE_STRONG);
        hoverIdx = -1;
        g.clip(x + 1, y + 1, w - 2, h - 2);
        int rows = Math.min(items.size(), maxRows);
        for (int r = 0; r < rows; r++) {
            int i = r + scroll;
            if (i >= items.size()) break;
            Item it = items.get(i);
            int ry = y + 2 + r * ROW;
            if (it.separator()) {
                g.hline(x + 6, ry + ROW / 2, w - 12, Ui.LINE_STRONG);
                continue;
            }
            boolean hov = it.enabled() && Gfx.in(mx, my, x + 1, ry, w - 2, ROW);
            if (hov) { hoverIdx = i; g.fill(x + 1, ry, w - 2, ROW, Ui.BG_ACTIVE); }
            else if (i == selectedIdx) g.fill(x + 1, ry, w - 2, ROW, Ui.BG_HOVER);
            g.textMid(g.fit(it.label(), w - 14), x + 7, ry, ROW, it.enabled() ? it.color() : Ui.TEXT_FAINT);
            if (i == selectedIdx) g.textRight("<", x + w - 5, ry, ROW, Ui.ACCENT);
        }
        g.unclip();
        if (items.size() > maxRows) {
            int th = Math.max(10, (h - 4) * maxRows / items.size());
            int ty = y + 2 + (int) ((h - 4 - th) * (scroll / (double) (items.size() - maxRows)));
            g.fill(x + w - 3, ty, 2, th, Ui.LINE_STRONG);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        int r = (int) ((my - y - 2) / ROW);
        int i = r + scroll;
        if (r >= 0 && i >= 0 && i < items.size()) {
            Item it = items.get(i);
            if (it.enabled() && !it.separator()) {
                dismiss();
                if (it.action() != null) it.action().run();
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (items.size() > maxRows) scroll = Math.max(0, Math.min(items.size() - maxRows, scroll - (int) Math.signum(dy) * 2));
        return true;
    }
}
