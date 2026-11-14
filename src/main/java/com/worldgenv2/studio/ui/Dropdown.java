package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Closed field that opens a {@link PopupMenu} of options. */
public class Dropdown extends Widget {
    private List<String> options = new ArrayList<>();
    private String selected = "";
    public Function<String, String> display = s -> s;
    public Consumer<String> onSelect;
    public String placeholder = "Select...";
    /** Optional leading caption, e.g. "Dimension". */
    public String caption;

    public Dropdown() {
        h = Ui.FIELD_H;
    }

    public Dropdown options(List<String> o) { this.options = new ArrayList<>(o); return this; }
    public Dropdown selected(String s) { this.selected = s == null ? "" : s; return this; }
    public String selected() { return selected; }

    @Override
    protected void render(Gfx g, int mx, int my) {
        boolean hov = enabled && contains(mx, my);
        g.fill(x, y, w, h, hov ? Ui.BG_HOVER : Ui.BG_INPUT);
        g.border(x, y, w, h, hov ? 0xFF3D445C : Ui.LINE_STRONG);
        int tx = x + 5;
        if (caption != null) {
            g.textMid(caption, tx, y, h, Ui.TEXT_FAINT);
            tx += g.width(caption) + 5;
        }
        String shown = selected.isEmpty() ? placeholder : display.apply(selected);
        g.textFit(shown, tx, y, h, x + w - 14 - tx, selected.isEmpty() ? Ui.TEXT_FAINT : (enabled ? Ui.TEXT : Ui.TEXT_FAINT));
        g.textRight("v", x + w - 5, y, h, Ui.TEXT_DIM);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (button != 0 || !enabled || root == null) return false;
        List<PopupMenu.Item> items = new ArrayList<>();
        int sel = -1;
        for (int i = 0; i < options.size(); i++) {
            String o = options.get(i);
            if (o.equals(selected)) sel = i;
            items.add(PopupMenu.Item.of(display.apply(o), () -> {
                selected = o;
                if (onSelect != null) onSelect.accept(o);
            }));
        }
        if (items.isEmpty()) items.add(PopupMenu.Item.disabled("(none)"));
        PopupMenu.open(root, x, y + h, w, items, sel, 14);
        return true;
    }
}
