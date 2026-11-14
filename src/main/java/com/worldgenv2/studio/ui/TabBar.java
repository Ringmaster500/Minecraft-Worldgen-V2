package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Document tab strip: select, close (x / middle-click), dirty marker, problem colour. */
public class TabBar extends Widget {

    public static final class Tab {
        public final String id;
        public String title;
        public boolean dirty;
        public int dot;
        public Tab(String id, String title) { this.id = id; this.title = title; }
    }

    public final List<Tab> tabs = new ArrayList<>();
    public String activeId;
    public Consumer<String> onSelect, onClose;
    private int scroll;

    public TabBar() {
        h = Ui.TAB_H;
    }

    private int tabW(Gfx g, Tab t) {
        return Math.min(170, g.width(t.title) + 34 + (t.dot != 0 ? 8 : 0));
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, Ui.BG_APP);
        g.hline(x, y + h - 1, w, Ui.LINE);
        g.clip(x, y, w, h);
        int tx = x - scroll;
        for (Tab t : tabs) {
            int tw = tabW(g, t);
            boolean act = t.id.equals(activeId);
            boolean hov = Gfx.in(mx, my, tx, y, tw, h);
            g.fill(tx, y, tw, h, act ? Ui.BG_PANEL : (hov ? Ui.BG_RAISED : Ui.BG_APP));
            if (act) { g.fill(tx, y, tw, 2, Ui.ACCENT); g.fill(tx, y + h - 1, tw, 1, Ui.BG_PANEL); }
            g.vline(tx + tw - 1, y + 3, h - 6, Ui.LINE);
            int lx = tx + 8;
            if (t.dot != 0) { g.fill(lx, y + h / 2 - 2, 4, 4, t.dot); lx += 8; }
            g.textFit(t.title, lx, y, h, tw - (lx - tx) - 18, act ? Ui.TEXT : Ui.TEXT_DIM);
            boolean xh = Gfx.in(mx, my, tx + tw - 16, y + 3, 12, h - 6);
            if (t.dirty && !xh) g.fill(tx + tw - 12, y + h / 2 - 2, 4, 4, Ui.ACCENT);
            else g.textCentered("x", tx + tw - 16, y, 12, h, xh ? Ui.RED : (hov || act ? Ui.TEXT_DIM : Ui.TEXT_FAINT));
            tx += tw;
        }
        g.unclip();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        Gfx g = Probe.gfx();
        int tx = x - scroll;
        for (Tab t : new ArrayList<>(tabs)) {
            int tw = tabW(g, t);
            if (Gfx.in(mx, my, tx, y, tw, h)) {
                boolean onX = mx >= tx + tw - 16;
                if (button == 2 || (button == 0 && onX)) { if (onClose != null) onClose.accept(t.id); }
                else if (button == 0 && onSelect != null) onSelect.accept(t.id);
                return true;
            }
            tx += tw;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        Gfx g = Probe.gfx();
        int total = 0;
        for (Tab t : tabs) total += tabW(g, t);
        scroll = Math.max(0, Math.min(Math.max(0, total - w), scroll - (int) Math.signum(dy) * 30));
        return true;
    }
}
