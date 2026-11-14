package com.worldgenv2.studio.ui;

import java.util.function.Consumer;

public class Toggle extends Widget {
    public boolean checked;
    public String label = "";
    public Consumer<Boolean> onChange;

    public Toggle(boolean checked, Consumer<Boolean> onChange) {
        this.checked = checked;
        this.onChange = onChange;
        this.h = Ui.FIELD_H;
    }

    public Toggle label(String l) { this.label = l; return this; }

    @Override
    protected void render(Gfx g, int mx, int my) {
        boolean hov = enabled && contains(mx, my);
        int bx = x, by = y + (h - 11) / 2;
        g.fill(bx, by, 11, 11, Ui.BG_INPUT);
        g.border(bx, by, 11, 11, checked ? Ui.ACCENT : (hov ? 0xFF3D445C : Ui.LINE_STRONG));
        if (checked) g.fill(bx + 3, by + 3, 5, 5, Ui.ACCENT);
        if (!label.isEmpty()) g.textMid(label, bx + 16, y, h, enabled ? Ui.TEXT : Ui.TEXT_FAINT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (button != 0 || !enabled) return false;
        checked = !checked;
        if (onChange != null) onChange.accept(checked);
        return true;
    }
}
