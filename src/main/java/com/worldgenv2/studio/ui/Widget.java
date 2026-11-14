package com.worldgenv2.studio.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every Studio widget. A widget owns its bounds; the parent assigns them in a single
 * {@link #layout()} pass, and both rendering and hit-testing read the same fields. There is
 * deliberately no second place where coordinates are recomputed.
 */
public abstract class Widget {
    public int x, y, w, h;
    public boolean visible = true;
    public boolean enabled = true;
    public boolean focusable = false;
    public Widget parent;
    public String tooltip;
    protected final List<Widget> children = new ArrayList<>();
    protected UiRoot root;

    public <T extends Widget> T add(T child) {
        child.parent = this;
        child.attach(root);
        children.add(child);
        return child;
    }

    public void remove(Widget child) {
        children.remove(child);
        child.parent = null;
    }

    public void clearChildren() {
        for (Widget c : children) c.parent = null;
        children.clear();
    }

    public List<Widget> children() {
        return children;
    }

    void attach(UiRoot r) {
        this.root = r;
        for (Widget c : children) c.attach(r);
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        layout();
    }

    /** Position children. Called whenever bounds change or content changes. */
    public void layout() {}

    public boolean contains(double px, double py) {
        return Gfx.in(px, py, x, y, w, h);
    }

    public boolean isFocused() {
        return root != null && root.focused() == this;
    }

    public void focus() {
        if (root != null) root.setFocus(this);
    }

    // ---- rendering ----

    public final void renderTree(Gfx g, int mx, int my) {
        if (!visible) return;
        render(g, mx, my);
        renderChildren(g, mx, my);
        renderOver(g, mx, my);
    }

    protected void renderChildren(Gfx g, int mx, int my) {
        for (Widget c : children) c.renderTree(g, mx, my);
    }

    protected abstract void render(Gfx g, int mx, int my);

    /** Drawn after children (e.g. scrollbars, focus rings). */
    protected void renderOver(Gfx g, int mx, int my) {}

    // ---- input: topmost-first hit test, then bubble up ----

    /** Deepest visible widget under the point, honouring clipping to ancestors. */
    public Widget hit(double px, double py) {
        if (!visible || !contains(px, py)) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget r = children.get(i).hit(px, py);
            if (r != null) return r;
        }
        return this;
    }

    public boolean mouseClicked(double mx, double my, int button, boolean dbl) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
    public boolean mouseScrolled(double mx, double my, double dx, double dy) { return false; }
    public boolean keyPressed(int key, int mods) { return false; }
    public boolean charTyped(int codepoint) { return false; }
    public void mouseMoved(double mx, double my) {}
    /** Called when this widget loses keyboard focus. */
    public void onBlur() {}
}
