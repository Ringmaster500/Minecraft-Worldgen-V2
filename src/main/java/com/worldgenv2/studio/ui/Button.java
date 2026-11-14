package com.worldgenv2.studio.ui;

public class Button extends Widget {
    public enum Style { NORMAL, PRIMARY, DANGER, GHOST }

    public String label;
    public Style style = Style.NORMAL;
    public Runnable onClick;
    public boolean selected;
    /** Small colored square drawn before the label (e.g. category). 0 = none. */
    public int dot;

    public Button(String label, Runnable onClick) {
        this.label = label;
        this.onClick = onClick;
        this.h = Ui.FIELD_H;
    }

    public Button style(Style s) { this.style = s; return this; }
    public Button tip(String t) { this.tooltip = t; return this; }

    /** Natural width for a given label. */
    public int fitWidth(Gfx g) { return g.width(label) + 14 + (dot != 0 ? 8 : 0); }

    @Override
    protected void render(Gfx g, int mx, int my) {
        boolean hov = enabled && contains(mx, my);
        int bg, border, fg;
        switch (style) {
            case PRIMARY -> { bg = hov ? 0xFFB77800 : 0xFF9A6500; border = 0xFFFFAA00; fg = 0xFFFFFFFF; }
            case DANGER -> { bg = hov ? 0xFF5A2320 : Ui.BG_RAISED; border = hov ? Ui.RED : Ui.LINE_STRONG; fg = hov ? 0xFFFF8A82 : Ui.TEXT_DIM; }
            case GHOST -> { bg = hov ? Ui.BG_HOVER : 0; border = 0; fg = hov ? Ui.TEXT : Ui.TEXT_DIM; }
            default -> { bg = selected ? Ui.BG_ACTIVE : (hov ? Ui.BG_HOVER : Ui.BG_RAISED); border = selected ? Ui.ACCENT : (hov ? 0xFF3D445C : Ui.LINE_STRONG); fg = selected ? Ui.ACCENT : Ui.TEXT; }
        }
        if (!enabled) { fg = Ui.TEXT_FAINT; bg = Ui.BG_PANEL; }
        if (bg != 0) g.fill(x, y, w, h, bg);
        if (border != 0) g.border(x, y, w, h, border);
        int tx = x;
        int tw = w;
        if (dot != 0) {
            g.fill(x + 5, y + h / 2 - 2, 4, 4, dot);
            tx += 8; tw -= 8;
        }
        g.textCentered(g.fit(label, w - 6), tx, y, tw, h, fg);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (button != 0 || !enabled) return false;
        if (onClick != null) onClick.run();
        return true;
    }
}
