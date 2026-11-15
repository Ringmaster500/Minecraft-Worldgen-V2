package com.worldgenv2.studio.ui;

import java.util.function.DoubleConsumer;

/** A horizontal slider with its value written on it. Drag or click to set; the value snaps to {@code step}. */
public class Slider extends Widget {
    public double min, max, step, value;
    public boolean integer;
    public DoubleConsumer onChange;
    private boolean dragging;

    public Slider(double min, double max, double step, double value, boolean integer, DoubleConsumer onChange) {
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.integer = integer;
        this.onChange = onChange;
        this.h = Ui.FIELD_H;
    }

    private String text() {
        if (integer) return String.valueOf((long) Math.rint(value));
        String s = String.format("%.3f", value);
        s = s.indexOf('.') < 0 ? s : s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private void setFrom(double mx) {
        double t = Math.max(0, Math.min(1, (mx - x - 2) / Math.max(1.0, w - 4)));
        double v = min + t * (max - min);
        if (step > 0) v = Math.rint(v / step) * step;
        v = Math.max(min, Math.min(max, v));
        if (v != value) {
            value = v;
            if (onChange != null) onChange.accept(v);
        }
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        g.box(x, y, w, h, Ui.BG_INPUT, dragging ? Ui.ACCENT : Ui.LINE_STRONG);
        double t = max > min ? (value - min) / (max - min) : 0;
        int fillW = (int) Math.max(0, Math.min(w - 2, t * (w - 2)));
        g.fill(x + 1, y + 1, fillW, h - 2, 0xFF3A2A08);
        g.fill(x + 1 + Math.max(0, fillW - 1), y + 1, 2, h - 2, Ui.ACCENT);
        g.textCentered(text(), x, y, w, h, Ui.TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (button != 0 || !enabled) return false;
        dragging = true;
        setFrom(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        setFrom(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }
}
