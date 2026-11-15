package com.worldgenv2.studio.shell;

import com.google.gson.JsonObject;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base for form-style asset editors. Field helpers bind a widget to a (dot-separated) JSON path: the widget
 * writes through {@link StudioProject#edit} and re-reads the model whenever it changes (undo, rename, ...)
 * unless the user is currently typing in it.
 */
abstract class Editor extends Form {
    protected final StudioApp app;
    protected final String assetPath;
    private final List<Runnable> syncers = new ArrayList<>();
    private long builtFor = -1;

    Editor(StudioApp app, Asset asset) {
        this.app = app;
        this.assetPath = asset.path;
    }

    protected final Asset asset() { return app.project.get(assetPath); }
    protected final StudioProject project() { return app.project; }
    protected abstract void build();

    /** (Re)creates all widgets from scratch. */
    public final void rebuild() {
        clear();
        syncers.clear();
        build();
        builtFor = project().revision;
    }

    /** Pushes model values into widgets that are not being edited. */
    public final void sync() {
        if (builtFor == project().revision) return;
        // A change that did not come from this editor's own widgets (undo, redo, rename...) must overwrite
        // even a focused field, otherwise it would keep showing a value the model no longer has.
        forceSync = project().revision != selfRevision;
        builtFor = project().revision;
        for (Runnable r : syncers) r.run();
        onSynced();
        forceSync = false;
    }

    /** True while syncing after an external change; syncers then update focused fields too. */
    protected boolean forceSync;
    private int selfRevision = -1;

    protected void onSynced() {}
    protected void bind(Runnable r) { syncers.add(r); r.run(); }

    protected void edit(String label, Consumer<JsonObject> m) {
        Asset a = asset();
        if (a == null) return;
        project().edit(a, label, m);
        selfRevision = project().revision;
    }

    // ---- dot-path access ----

    private static JsonObject parent(JsonObject root, String path, boolean create) {
        String[] parts = path.split("\\.");
        JsonObject o = root;
        for (int i = 0; i < parts.length - 1; i++) {
            o = create ? J.objOrCreate(o, parts[i]) : J.obj(o, parts[i]);
            if (o == null) return null;
        }
        return o;
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    protected final String getStr(String path, String def) {
        Asset a = asset();
        JsonObject p = a == null ? null : parent(a.json, path, false);
        return J.str(p, leaf(path), def);
    }

    protected final double getNum(String path, double def) {
        Asset a = asset();
        JsonObject p = a == null ? null : parent(a.json, path, false);
        return J.num(p, leaf(path), def);
    }

    protected final boolean getBool(String path, boolean def) {
        Asset a = asset();
        JsonObject p = a == null ? null : parent(a.json, path, false);
        return J.bool(p, leaf(path), def);
    }

    // ---- bound widgets ----

    protected final TextField str(String path, String def, String label) {
        TextField f = new TextField();
        f.onChange = s -> edit(label, j -> parent(j, path, true).addProperty(leaf(path), s));
        bind(() -> { if (forceSync || !f.isFocused()) f.setText(getStr(path, def)); });
        return f;
    }

    /** A block id field with icon and a searchable picker. */
    protected final Widget blockField(String path, String def, String label) {
        TextField f = str(path, def, label);
        return new BlockField(f, id -> edit(label, j -> parent(j, path, true).addProperty(leaf(path), id)));
    }

    protected final TextField num(String path, double def, double min, double max, double step, boolean integer, String label) {
        TextField f = new TextField().filter(integer ? TextField.Filter.INT : TextField.Filter.DECIMAL).range(min, max, step);
        f.onChange = s -> {
            double v;
            try { v = Double.parseDouble(s.trim()); } catch (NumberFormatException e) { f.invalid(true); return; }
            f.invalid(v < min || v > max);
            edit(label, j -> J.setNum(parent(j, path, true), leaf(path), integer ? Math.rint(v) : v));
        };
        f.onCommit = s -> f.setText(fmt(getNum(path, def), integer));
        bind(() -> {
            if (forceSync || !f.isFocused()) {
                double v = getNum(path, def);
                f.setText(fmt(v, integer));
                f.invalid(v < min || v > max);
            }
        });
        return f;
    }

    protected final Dropdown choice(String path, String def, List<String> options, java.util.function.Function<String, String> display, String label) {
        Dropdown d = new Dropdown();
        d.options(options);
        d.display = display;
        d.onSelect = v -> edit(label, j -> parent(j, path, true).addProperty(leaf(path), v));
        bind(() -> d.selected(getStr(path, def)));
        return d;
    }

    protected final Toggle toggle(String path, boolean def, String label, String caption) {
        Toggle t = new Toggle(def, v -> edit(label, j -> parent(j, path, true).addProperty(leaf(path), v)));
        t.label(caption);
        bind(() -> t.checked = getBool(path, def));
        return t;
    }

    protected final Label dim(String text) { return new Label(text); }

    static String fmt(double v, boolean integer) {
        if (integer || v == Math.rint(v)) return String.valueOf((long) v);
        String s = String.format(java.util.Locale.ROOT, "%.4f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
