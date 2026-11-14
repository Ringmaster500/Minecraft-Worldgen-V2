package com.worldgenv2.studio.ui;

/** Lays out one flexible widget with one fixed-width widget beside it (e.g. a field plus a small button). */
public class Pair extends Widget {
    private final Widget main, side;
    private final int sideW;

    public Pair(Widget main, Widget side, int sideW) {
        this.main = add(main);
        this.side = add(side);
        this.sideW = sideW;
        this.h = Ui.FIELD_H;
    }

    @Override
    public void layout() {
        main.setBounds(x, y, w - sideW - Ui.GAP, h);
        side.setBounds(x + w - sideW, y, sideW, h);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {}
}
