package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Multi-select popup: stays open while toggling; closes on outside click or Escape. */
public class Checklist extends UiRoot.Overlay {
    private static final int ROW = 15;
    private final List<String> ids = new ArrayList<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Set<String> checked;
    private final Consumer<Set<String>> onChange;
    private int scroll;
    private final int maxRows = 12;

    private Checklist(Map<String, String> items, Set<String> checked, Consumer<Set<String>> onChange) {
        labels.putAll(items);
        ids.addAll(items.keySet());
        this.checked = checked;
        this.onChange = onChange;
    }

    public static Checklist open(UiRoot root, int x, int y, int minW, Map<String, String> items, Set<String> checked,
                                 Consumer<Set<String>> onChange) {
        Checklist c = new Checklist(items, new java.util.LinkedHashSet<>(checked), onChange);
        Gfx g = Probe.gfx();
        int w = minW;
        for (String l : items.values()) w = Math.max(w, g.width(l) + 40);
        int rows = Math.min(Math.max(1, items.size()), c.maxRows) + 1;
        int h = rows * ROW + 4;
        c.x = Math.max(2, Math.min(x, root.vw - w - 2));
        c.y = Math.max(2, Math.min(y, root.vh - h - 2));
        c.w = w;
        c.h = h;
        root.showOverlay(c);
        return c;
    }

    @Override
    public void dismiss() {
        if (root != null) root.closeOverlay(this);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x + 2, y + 2, w, h, 0x66000000);
        g.box(x, y, w, h, Ui.BG_RAISED, Ui.LINE_STRONG);
        // header row: all / none
        boolean hAll = Gfx.in(mx, my, x + 1, y + 2, w / 2 - 1, ROW), hNone = Gfx.in(mx, my, x + w / 2, y + 2, w / 2 - 1, ROW);
        g.textCentered("All", x + 1, y + 2, w / 2 - 1, ROW, hAll ? Ui.ACCENT : Ui.TEXT_DIM);
        g.textCentered("None", x + w / 2, y + 2, w / 2 - 1, ROW, hNone ? Ui.ACCENT : Ui.TEXT_DIM);
        g.hline(x + 4, y + 2 + ROW, w - 8, Ui.LINE);
        int rows = Math.min(ids.size(), maxRows);
        g.clip(x + 1, y + 3 + ROW, w - 2, h - ROW - 4);
        for (int r = 0; r < rows; r++) {
            int i = r + scroll;
            if (i >= ids.size()) break;
            String id = ids.get(i);
            int ry = y + 3 + ROW + r * ROW;
            if (Gfx.in(mx, my, x + 1, ry, w - 2, ROW)) g.fill(x + 1, ry, w - 2, ROW, Ui.BG_ACTIVE);
            boolean on = checked.contains(id);
            g.box(x + 6, ry + 3, 9, 9, Ui.BG_INPUT, on ? Ui.ACCENT : Ui.LINE_STRONG);
            if (on) g.fill(x + 8, ry + 5, 5, 5, Ui.ACCENT);
            g.textFit(labels.get(id), x + 20, ry, ROW, w - 26, on ? Ui.TEXT : Ui.TEXT_DIM);
        }
        g.unclip();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (my < y + 2 + ROW) {
            if (mx < x + w / 2) checked.addAll(ids); else checked.clear();
            onChange.accept(new java.util.LinkedHashSet<>(checked));
            return true;
        }
        int i = (int) ((my - (y + 3 + ROW)) / ROW) + scroll;
        if (i >= 0 && i < ids.size()) {
            String id = ids.get(i);
            if (!checked.remove(id)) checked.add(id);
            onChange.accept(new java.util.LinkedHashSet<>(checked));
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (ids.size() > maxRows) scroll = Math.max(0, Math.min(ids.size() - maxRows, scroll - (int) Math.signum(dy) * 2));
        return true;
    }
}
