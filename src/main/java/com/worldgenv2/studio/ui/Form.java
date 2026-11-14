package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertically scrolling form: sections, labelled rows and free-form blocks. Rows are laid out in one
 * pass; child widgets are positioned directly so hit-testing and rendering always agree.
 */
public class Form extends Widget {

    private abstract static class Row {
        int y, h;
    }

    private static final class Section extends Row {
        final String title;
        final String subtitle;
        Section(String t, String s) { title = t; subtitle = s; h = 26; }
    }

    private static final class Field extends Row {
        final String label, hint;
        final Widget editor;
        Field(String l, Widget e, String hint) { label = l; editor = e; this.hint = hint; h = Math.max(e.h, Ui.FIELD_H) + 4; }
    }

    private static final class Block extends Row {
        final Widget widget;
        Block(Widget wd) { widget = wd; h = wd.h + 4; }
    }

    private static final class Note extends Row {
        final String text;
        final int color;
        Note(String t, int c) { text = t; color = c; h = 16; }
    }

    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private int contentH;
    public int labelW = 130;
    public int maxContentW = 620;

    public Form() {}

    public Form section(String title) { return section(title, null); }

    public Form section(String title, String subtitle) {
        rows.add(new Section(title, subtitle));
        return this;
    }

    public Form field(String label, Widget editor) { return field(label, editor, null); }

    public Form field(String label, Widget editor, String hint) {
        add(editor);
        rows.add(new Field(label, editor, hint));
        return this;
    }

    public Form block(Widget widget) {
        add(widget);
        rows.add(new Block(widget));
        return this;
    }

    public Form note(String text, int color) {
        rows.add(new Note(text, color));
        return this;
    }

    public void clear() {
        clearChildren();
        rows.clear();
        scroll = 0;
    }

    @Override
    public void layout() {
        int cw = Math.min(w - 2 * Ui.PAD - 6, maxContentW);
        int left = x + Ui.PAD + 2;
        int cy = y + Ui.PAD - scroll;
        for (Row r : rows) {
            r.y = cy;
            if (r instanceof Block b) b.h = b.widget.h + 4;
            if (r instanceof Field f) f.h = Math.max(f.editor.h, Ui.FIELD_H) + 4;
            if (r instanceof Field f) {
                int ex = left + labelW;
                f.editor.setBounds(ex, cy + 2, Math.max(40, cw - labelW), f.editor.h);
            } else if (r instanceof Block b) {
                b.widget.setBounds(left, cy + 2, cw, b.widget.h);
            }
            cy += r.h;
        }
        contentH = cy + scroll - y + Ui.PAD;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(Math.max(0, contentH - h), scroll));
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.fill(x, y, w, h, Ui.BG_PANEL);
    }

    @Override
    protected void renderChildren(Gfx g, int mx, int my) {
        clampScroll();
        layout();
        g.clip(x, y, w, h);
        int left = x + Ui.PAD + 2;
        int cw = Math.min(w - 2 * Ui.PAD - 6, maxContentW);
        for (Row r : rows) {
            if (r.y + r.h < y || r.y > y + h) continue;
            if (r instanceof Section s) {
                g.textMid(s.title.toUpperCase(), left, r.y + 6, 14, Ui.ACCENT);
                if (s.subtitle != null) g.textMid(s.subtitle, left + g.width(s.title) + 14, r.y + 6, 14, Ui.TEXT_FAINT);
                g.hline(left, r.y + 22, cw, Ui.LINE);
            } else if (r instanceof Field f) {
                g.textFit(f.label, left, f.editor.y, f.editor.h, labelW - 8, Ui.TEXT_DIM);
                if (f.hint != null) {
                    int hx = f.editor.x + f.editor.w - g.width(f.hint) - 4;
                    // hints are shown by tooltip so they never fight the editor for space
                    f.editor.tooltip = f.editor.tooltip == null ? f.hint : f.editor.tooltip;
                }
            } else if (r instanceof Note n) {
                g.textMid(g.fit(n.text, cw), left, r.y, n.h, n.color);
            }
        }
        for (Widget c : children) c.renderTree(g, mx, my);
        g.unclip();
        if (contentH > h) {
            int th = Math.max(14, h * h / contentH);
            int ty = y + (int) ((h - th) * (scroll / (double) Math.max(1, contentH - h)));
            g.fill(x + w - 3, ty, 2, th, Ui.LINE_STRONG);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy) * 24;
        clampScroll();
        return true;
    }

    public int contentHeight() { return contentH; }
}
