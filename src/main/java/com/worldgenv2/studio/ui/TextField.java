package com.worldgenv2.studio.ui;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/** Single-line text input with caret, selection, clipboard and numeric/identifier filters. */
public class TextField extends Widget {
    public enum Filter { TEXT, INT, DECIMAL, ID }

    private String text = "";
    private int caret, anchor, scrollX;
    public String placeholder = "";
    public Filter filter = Filter.TEXT;
    public int maxLen = 256;
    /** Fires on every edit. */
    public Consumer<String> onChange;
    /** Fires on Enter or when focus is lost after an edit. */
    public Consumer<String> onCommit;
    /** Fires on Enter (every time). If set, Enter keeps focus instead of blurring. */
    public Consumer<String> onEnter;
    public double step = 1;
    public double min = -Double.MAX_VALUE, max = Double.MAX_VALUE;
    private boolean dirtySinceCommit;
    private boolean invalid;

    public TextField() {
        focusable = true;
        h = Ui.FIELD_H;
    }

    public TextField filter(Filter f) { this.filter = f; return this; }
    public TextField placeholder(String p) { this.placeholder = p; return this; }
    public TextField range(double min, double max, double step) { this.min = min; this.max = max; this.step = step; return this; }
    public TextField invalid(boolean v) { this.invalid = v; return this; }

    public String text() { return text; }

    public void setText(String s) {
        text = s == null ? "" : s;
        caret = anchor = Math.min(text.length(), caret);
        dirtySinceCommit = false;
    }

    public double number(double def) {
        try { return Double.parseDouble(text.trim()); } catch (NumberFormatException e) { return def; }
    }

    private boolean allowed(int cp) {
        char c = (char) cp;
        return switch (filter) {
            case TEXT -> cp >= 32 && cp != 127;
            case INT -> Character.isDigit(c) || (c == '-' && caret == 0 && !text.startsWith("-"));
            case DECIMAL -> Character.isDigit(c) || c == '.' || (c == '-' && caret == 0 && !text.startsWith("-")) || c == 'e' || c == 'E';
            case ID -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '/' || c == '.';
        };
    }

    private boolean hasSel() { return caret != anchor; }
    private int selStart() { return Math.min(caret, anchor); }
    private int selEnd() { return Math.max(caret, anchor); }

    private void deleteSel() {
        if (!hasSel()) return;
        text = text.substring(0, selStart()) + text.substring(selEnd());
        caret = anchor = selStart();
    }

    private void insert(String s) {
        deleteSel();
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < s.length(); i++) if (allowed(s.charAt(i))) f.append(s.charAt(i));
        String ins = f.toString();
        if (text.length() + ins.length() > maxLen) ins = ins.substring(0, Math.max(0, maxLen - text.length()));
        text = text.substring(0, caret) + ins + text.substring(caret);
        caret = anchor = caret + ins.length();
        edited();
    }

    private void edited() {
        dirtySinceCommit = true;
        if (onChange != null) onChange.accept(text);
    }

    public void commit() {
        if (dirtySinceCommit) {
            dirtySinceCommit = false;
            if (onCommit != null) onCommit.accept(text);
        }
    }

    @Override
    public void onBlur() {
        commit();
    }

    private int indexAt(Gfx g, double mx) {
        int rel = (int) mx - (x + 4) + scrollX;
        int best = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (g.width(text.substring(0, i)) <= rel + 1) best = i; else break;
        }
        return best;
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        boolean foc = isFocused();
        boolean hov = enabled && contains(mx, my);
        g.fill(x, y, w, h, enabled ? Ui.BG_INPUT : Ui.BG_PANEL);
        g.border(x, y, w, h, invalid ? Ui.RED : foc ? Ui.ACCENT : (hov ? 0xFF3D445C : Ui.LINE_STRONG));
        int inner = w - 8;
        int cx = g.width(text.substring(0, caret));
        if (cx - scrollX > inner - 2) scrollX = cx - inner + 2;
        if (cx - scrollX < 0) scrollX = cx;
        if (g.width(text) <= inner) scrollX = 0;
        g.clip(x + 2, y + 1, w - 4, h - 2);
        if (text.isEmpty() && !foc) {
            g.textMid(placeholder, x + 4, y, h, Ui.TEXT_FAINT);
        } else {
            if (foc && hasSel()) {
                int sx = g.width(text.substring(0, selStart())) - scrollX;
                int ex = g.width(text.substring(0, selEnd())) - scrollX;
                g.fill(x + 4 + sx, y + 2, ex - sx, h - 4, 0xFF3A4A6E);
            }
            g.textMid(text, x + 4 - scrollX, y, h, enabled ? Ui.TEXT : Ui.TEXT_FAINT);
            if (foc && (System.currentTimeMillis() / 500) % 2 == 0) {
                g.fill(x + 4 + cx - scrollX, y + 3, 1, h - 6, Ui.ACCENT);
            }
        }
        g.unclip();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (button != 0) return false;
        Gfx g = root == null ? null : Probe.gfx();
        if (dbl) { anchor = 0; caret = text.length(); return true; }
        if (g != null) { caret = indexAt(g, mx); anchor = caret; }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Gfx g = Probe.gfx();
        if (g != null) caret = indexAt(g, mx);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if ((filter == Filter.INT || filter == Filter.DECIMAL) && isFocused()) {
            nudge(dy > 0 ? 1 : -1);
            return true;
        }
        return false;
    }

    private void nudge(int dir) {
        double v = Math.max(min, Math.min(max, number(0) + dir * step));
        String s = filter == Filter.INT || v == Math.rint(v) && step >= 1 ? String.valueOf((long) v) : trim(v);
        text = s;
        caret = anchor = text.length();
        edited();
    }

    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.4f", v);
        s = s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
        return s;
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        boolean shift = (mods & Ui.MOD_SHIFT) != 0, ctrl = (mods & Ui.MOD_CTRL) != 0;
        switch (key) {
            case Ui.KEY_LEFT -> { caret = Math.max(0, caret - 1); if (!shift) anchor = caret; return true; }
            case Ui.KEY_RIGHT -> { caret = Math.min(text.length(), caret + 1); if (!shift) anchor = caret; return true; }
            case Ui.KEY_HOME -> { caret = 0; if (!shift) anchor = caret; return true; }
            case Ui.KEY_END -> { caret = text.length(); if (!shift) anchor = caret; return true; }
            case Ui.KEY_BACKSPACE -> {
                if (hasSel()) deleteSel(); else if (caret > 0) { text = text.substring(0, caret - 1) + text.substring(caret); caret--; anchor = caret; }
                edited();
                return true;
            }
            case Ui.KEY_DELETE -> {
                if (hasSel()) deleteSel(); else if (caret < text.length()) text = text.substring(0, caret) + text.substring(caret + 1);
                edited();
                return true;
            }
            case Ui.KEY_ENTER, Ui.KEY_KP_ENTER -> {
                commit();
                if (onEnter != null) onEnter.accept(text);
                else if (root != null) root.setFocus(null);
                return true;
            }
            case Ui.KEY_ESC -> { if (root != null) root.setFocus(null); return true; }
            case Ui.KEY_UP -> { if (filter == Filter.INT || filter == Filter.DECIMAL) { nudge(1); return true; } }
            case Ui.KEY_DOWN -> { if (filter == Filter.INT || filter == Filter.DECIMAL) { nudge(-1); return true; } }
            default -> {}
        }
        if (ctrl) {
            switch (key) {
                case 65 -> { anchor = 0; caret = text.length(); return true; }
                case 67 -> { if (hasSel()) Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selStart(), selEnd())); return true; }
                case 88 -> {
                    if (hasSel()) { Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(selStart(), selEnd())); deleteSel(); edited(); }
                    return true;
                }
                case 86 -> { insert(Minecraft.getInstance().keyboardHandler.getClipboard()); return true; }
                default -> {}
            }
            return false; // let Ctrl+S / Ctrl+Z bubble to the app
        }
        return key >= 32 && key < 127; // swallow printable keys so global shortcuts never fire while typing
    }

    @Override
    public boolean charTyped(int cp) {
        if (!allowed(cp)) return true;
        insert(new String(Character.toChars(cp)));
        return true;
    }
}
