package com.worldgenv2.studio.ui;

import java.util.function.IntConsumer;

/** Thin draggable divider; reports the mouse x so the owner can resize the pane to its left. */
public class Splitter extends Widget {
    private final IntConsumer onDrag;
    private boolean active;

    public Splitter(IntConsumer onDrag) { this.onDrag = onDrag; }

    @Override
    protected void render(Gfx g, int mx, int my) {
        if (active || contains(mx, my)) g.fill(x, y, w, h, Ui.ACCENT_DIM);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        active = button == 0;
        return active;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        onDrag.accept((int) mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        active = false;
        return true;
    }
}
