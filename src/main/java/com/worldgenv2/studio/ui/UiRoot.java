package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the widget tree, keyboard focus, drag capture and overlay stack (popups, modals).
 * The hosting Screen converts real GUI coordinates to virtual ones before calling in.
 */
public final class UiRoot {

    /** A widget shown above everything else; clicking outside it dismisses it. */
    public abstract static class Overlay extends Widget {
        public boolean modal;
        public abstract void dismiss();
        @Override public boolean contains(double px, double py) { return modal || super.contains(px, py); }
    }

    public final Widget content = new Widget() {
        @Override protected void render(Gfx g, int mx, int my) {}
    };
    private final List<Overlay> overlays = new ArrayList<>();
    private Widget focused;
    private Widget pressed;
    public int vw, vh;

    public UiRoot() {
        content.attach(this);
    }

    public void setSize(int vw, int vh) {
        this.vw = vw;
        this.vh = vh;
        content.setBounds(0, 0, vw, vh);
        for (Overlay o : overlays) o.layout();
    }

    public Widget focused() { return focused; }

    public void setFocus(Widget w) {
        if (focused == w) return;
        Widget old = focused;
        focused = w;
        if (old != null) old.onBlur();
    }

    public <T extends Overlay> T showOverlay(T o) {
        o.attach(this);
        overlays.add(o);
        o.setBounds(o.modal ? 0 : o.x, o.modal ? 0 : o.y, o.modal ? vw : o.w, o.modal ? vh : o.h);
        return o;
    }

    public void closeOverlay(Overlay o) {
        overlays.remove(o);
        if (focused != null && isDescendant(focused, o)) setFocus(null);
    }

    public boolean hasOverlay() { return !overlays.isEmpty(); }

    private static boolean isDescendant(Widget w, Widget anc) {
        for (Widget p = w; p != null; p = p.parent) if (p == anc) return true;
        return false;
    }

    public void render(Gfx g, int mx, int my) {
        content.renderTree(g, mx, my);
        for (Overlay o : new ArrayList<>(overlays)) o.renderTree(g, mx, my);
        renderTooltip(g, mx, my);
    }

    private void renderTooltip(Gfx g, int mx, int my) {
        Widget t = hitAny(mx, my);
        String tip = null;
        for (Widget p = t; p != null && tip == null; p = p.parent) tip = p.tooltip;
        if (tip == null || tip.isEmpty()) return;
        int tw = g.width(tip) + 10;
        int tx = Math.min(mx + 10, vw - tw - 2), ty = Math.min(my + 14, vh - 16);
        g.box(tx, ty, tw, 14, 0xF0050609, Ui.LINE_STRONG);
        g.textMid(tip, tx + 5, ty, 14, Ui.TEXT);
    }

    private Widget hitAny(double mx, double my) {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            Widget r = overlays.get(i).hit(mx, my);
            if (r != null) return r;
            if (overlays.get(i).modal) return null;
        }
        return content.hit(mx, my);
    }

    public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
        if (!overlays.isEmpty()) {
            Overlay top = overlays.get(overlays.size() - 1);
            Widget t = top.hit(mx, my);
            if (t == null || (top.modal && t == top && !top.contains(mx, my))) {
                top.dismiss();
                return true;
            }
            return dispatchClick(t, mx, my, button, dbl) || true;
        }
        Widget t = content.hit(mx, my);
        if (t == null) { setFocus(null); return false; }
        return dispatchClick(t, mx, my, button, dbl);
    }

    private boolean dispatchClick(Widget t, double mx, double my, int button, boolean dbl) {
        Widget before = focused;
        for (Widget w = t; w != null; w = w.parent) {
            if (w.enabled && w.mouseClicked(mx, my, button, dbl)) {
                pressed = w;
                // Only move focus if the handler didn't (a click may open a dialog that focuses its own field).
                if (focused == before) setFocus(w.focusable ? w : null);
                return true;
            }
        }
        setFocus(null);
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        Widget p = pressed;
        pressed = null;
        return p != null && p.mouseReleased(mx, my, button);
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return pressed != null && pressed.mouseDragged(mx, my, button, dx, dy);
    }

    public void mouseMoved(double mx, double my) {
        Widget t = hitAny(mx, my);
        if (t != null) t.mouseMoved(mx, my);
    }

    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        Widget t = hitAny(mx, my);
        for (Widget w = t; w != null; w = w.parent) if (w.mouseScrolled(mx, my, dx, dy)) return true;
        return false;
    }

    /** @return true if consumed by an overlay or the focused widget. */
    public boolean keyPressed(int key, int mods) {
        if (!overlays.isEmpty()) {
            Overlay top = overlays.get(overlays.size() - 1);
            if (focused != null && isDescendant(focused, top)) {
                for (Widget w = focused; w != null; w = w.parent) if (w.keyPressed(key, mods)) return true;
            }
            if (top.keyPressed(key, mods)) return true;
            if (key == Ui.KEY_ESC) { top.dismiss(); return true; }
            return top.modal;
        }
        for (Widget w = focused; w != null; w = w.parent) if (w.keyPressed(key, mods)) return true;
        return false;
    }

    public boolean charTyped(int cp) {
        if (focused != null && focused.charTyped(cp)) return true;
        return !overlays.isEmpty();
    }

    /** True when a text input currently owns the keyboard (so single-key shortcuts must not fire). */
    public boolean textInputActive() {
        return focused instanceof TextField;
    }
}
