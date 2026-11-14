package com.worldgenv2.studio.ui;

public class Label extends Widget {
    public String text;
    public int color = Ui.TEXT_DIM;
    public boolean right;

    public Label(String text) {
        this.text = text;
        this.h = Ui.FIELD_H;
    }

    public Label color(int c) { this.color = c; return this; }

    @Override
    protected void render(Gfx g, int mx, int my) {
        String s = g.fit(text, w);
        if (right) g.textRight(s, x + w, y, h, color);
        else g.textMid(s, x, y, h, color);
    }
}
