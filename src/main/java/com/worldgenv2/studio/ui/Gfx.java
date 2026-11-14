package com.worldgenv2.studio.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Thin drawing facade over {@link GuiGraphicsExtractor} so widgets never touch raw graphics calls
 * with ad-hoc coordinates. All coordinates are in Studio "virtual pixels" (see {@link UiRoot}).
 */
public final class Gfx {

    public final GuiGraphicsExtractor g;
    public final Font font;

    public Gfx(GuiGraphicsExtractor g, Font font) {
        this.g = g;
        this.font = font;
    }

    public void fill(int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, color);
    }

    /** 1px inner border. */
    public void border(int x, int y, int w, int h, int color) {
        if (w < 2 || h < 2) return;
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public void box(int x, int y, int w, int h, int bg, int border) {
        fill(x, y, w, h, bg);
        border(x, y, w, h, border);
    }

    public void hline(int x, int y, int w, int color) {
        fill(x, y, w, 1, color);
    }

    public void vline(int x, int y, int h, int color) {
        fill(x, y, 1, h, color);
    }

    public int width(String s) {
        return font.width(s);
    }

    public int lineHeight() {
        return font.lineHeight;
    }

    public void text(String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    /** Text vertically centred inside a box of height {@code h} starting at {@code y}. */
    public void textMid(String s, int x, int y, int h, int color) {
        g.text(font, s, x, y + (h - font.lineHeight) / 2 + 1, color, false);
    }

    public void textCentered(String s, int x, int y, int w, int h, int color) {
        int tw = font.width(s);
        g.text(font, s, x + (w - tw) / 2, y + (h - font.lineHeight) / 2 + 1, color, false);
    }

    public void textRight(String s, int rightX, int y, int h, int color) {
        g.text(font, s, rightX - font.width(s), y + (h - font.lineHeight) / 2 + 1, color, false);
    }

    /** Truncates with an ellipsis so the string fits in maxW. */
    public String fit(String s, int maxW) {
        if (maxW <= 0) return "";
        if (font.width(s) <= maxW) return s;
        int ew = font.width("...");
        if (maxW <= ew) return "";
        return font.plainSubstrByWidth(s, maxW - ew) + "...";
    }

    public void textFit(String s, int x, int y, int h, int maxW, int color) {
        textMid(fit(s, maxW), x, y, h, color);
    }

    public void clip(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public void unclip() {
        g.disableScissor();
    }

    public static boolean in(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public static int alpha(int argb, int a) {
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = (a >>> 24);
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = (b >>> 24);
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
