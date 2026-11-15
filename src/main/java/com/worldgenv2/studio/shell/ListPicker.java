package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A centred, searchable list of choices grouped by source. Type to filter, Up/Down + Enter or click to pick, Esc to close. */
final class ListPicker extends UiRoot.Overlay {
    record Item(String group, String label, String sub, Runnable action) {}

    private static final int ROW = 20, MAX_ROWS = 13, WIDTH = 400;

    private final TextField search = new TextField();
    private final List<Item> all;
    private List<Item> shown = new ArrayList<>();
    private final String title, hint;
    private int sel, scroll;

    private ListPicker(String title, String hint, String placeholder, List<Item> items) {
        this.title = title;
        this.hint = hint;
        this.all = items;
        this.modal = true;
        search.placeholder(placeholder);
        add(search);
        search.onChange = s -> refilter();
        search.onEnter = s -> pick(sel);
        refilter();
    }

    static ListPicker open(UiRoot root, String title, String hint, String placeholder, List<Item> items) {
        ListPicker p = new ListPicker(title, hint, placeholder, items);
        root.showOverlay(p);
        p.layout();
        p.search.focus();
        return p;
    }

    private int px, py, pw, ph;

    @Override
    public void layout() {
        pw = Math.min(WIDTH, vwOr(WIDTH) - 20);
        ph = 44 + MAX_ROWS * ROW + 8;
        px = (vwOr(WIDTH) - pw) / 2;
        py = Math.max(8, (vhOr(ph) - ph) / 2);
        search.setBounds(px + 8, py + 24, pw - 16, Ui.FIELD_H);
    }

    private int vwOr(int d) { return root == null ? d : root.vw; }
    private int vhOr(int d) { return root == null ? d : root.vh; }

    private void refilter() {
        String q = search.text().trim().toLowerCase(Locale.ROOT);
        shown = new ArrayList<>();
        for (Item it : all) {
            if (q.isEmpty() || (it.label() + " " + (it.sub() == null ? "" : it.sub()) + " " + it.group()).toLowerCase(Locale.ROOT).contains(q)) shown.add(it);
        }
        if (!q.isEmpty()) shown.sort((a, b) -> Boolean.compare(!b.label().toLowerCase(Locale.ROOT).startsWith(q), !a.label().toLowerCase(Locale.ROOT).startsWith(q)));
        sel = 0;
        scroll = 0;
    }

    @Override public void dismiss() { if (root != null) root.closeOverlay(this); }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        g.fill(x, y, w, h, 0x99000000);
        g.fill(px + 2, py + 2, pw, ph, 0x66000000);
        g.box(px, py, pw, ph, Ui.BG_RAISED, Ui.LINE_STRONG);
        g.textMid(title, px + 8, py + 2, 18, Ui.ACCENT);
        g.textRight(hint, px + pw - 8, py + 2, 18, Ui.TEXT_FAINT);
        int top = py + 44;
        g.clip(px + 1, top, pw - 2, MAX_ROWS * ROW);
        String lastGroup = null;
        for (int r = 0; r < MAX_ROWS; r++) {
            int i = r + scroll;
            if (i >= shown.size()) break;
            Item it = shown.get(i);
            int ry = top + r * ROW;
            if (Gfx.in(mx, my, px + 1, ry, pw - 2, ROW)) sel = i;
            if (i == sel) g.fill(px + 1, ry, pw - 2, ROW, Ui.BG_ACTIVE);
            g.textFit(it.label(), px + 10, ry, ROW, pw / 2, i == sel ? Ui.TEXT : Ui.TEXT_DIM);
            if (it.sub() != null) g.textFit(it.sub(), px + 10 + pw / 2, ry, ROW, pw / 2 - 90, Ui.TEXT_FAINT);
            g.textRight(it.group(), px + pw - 10, ry, ROW, Ui.CYAN);
        }
        if (shown.isEmpty()) g.textMid("Nothing matches.", px + 10, top, ROW, Ui.TEXT_FAINT);
        g.unclip();
    }

    private void pick(int i) {
        if (i < 0 || i >= shown.size()) return;
        Item it = shown.get(i);
        dismiss();
        it.action().run();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (!Gfx.in(mx, my, px, py, pw, ph)) { dismiss(); return true; }
        int r = (int) ((my - (py + 44)) / ROW);
        if (my >= py + 44 && r >= 0 && r < MAX_ROWS) pick(r + scroll);
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
            case Ui.KEY_ESC -> { dismiss(); return true; }
            case Ui.KEY_DOWN -> { sel = Math.min(shown.size() - 1, sel + 1); if (sel >= scroll + MAX_ROWS) scroll++; return true; }
            case Ui.KEY_UP -> { sel = Math.max(0, sel - 1); if (sel < scroll) scroll = sel; return true; }
            case Ui.KEY_ENTER, Ui.KEY_KP_ENTER -> { pick(sel); return true; }
            default -> { return false; }
        }
    }
}
