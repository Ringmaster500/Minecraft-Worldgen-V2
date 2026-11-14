package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.ui.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Editable list of rule "cards" stored as a JSON array on the asset (zones, landmarks, budgets...).
 * Each card shows a title, a remove button and a two-column grid of bound fields described by {@link Field}.
 */
final class CardList extends Widget {

    enum Kind { STR, NUM, INT, CHOICE, TOGGLE, BIOMES }

    record Field(String key, String label, Kind kind, double min, double max, double step,
                 Supplier<List<String>> options, Function<String, String> display, String hint,
                 boolean wide, Predicate<JsonObject> visibleIf) {

        static Field str(String key, String label, String hint) {
            return new Field(key, label, Kind.STR, 0, 0, 0, null, null, hint, false, null);
        }
        static Field num(String key, String label, double min, double max, double step, String hint) {
            return new Field(key, label, Kind.NUM, min, max, step, null, null, hint, false, null);
        }
        static Field integer(String key, String label, int min, int max, String hint) {
            return new Field(key, label, Kind.INT, min, max, 1, null, null, hint, false, null);
        }
        static Field choice(String key, String label, Supplier<List<String>> options, Function<String, String> display, String hint) {
            return new Field(key, label, Kind.CHOICE, 0, 0, 0, options, display, hint, false, null);
        }
        static Field toggle(String key, String label, String hint) {
            return new Field(key, label, Kind.TOGGLE, 0, 0, 0, null, null, hint, false, null);
        }
        static Field biomes(String key, String label, String hint) {
            return new Field(key, label, Kind.BIOMES, 0, 0, 0, null, null, hint, true, null);
        }
        Field when(Predicate<JsonObject> p) {
            return new Field(key, label, kind, min, max, step, options, display, hint, wide, p);
        }
    }

    private static final int CARD_HEAD = 20, ROW = 20, PAD = 6;

    private final Editor ed;
    private final String arrayKey;
    private final String noun;
    private final List<Field> fields;
    private final Function<Integer, JsonObject> newEntry;
    private final Function<JsonObject, String> title;
    private final String emptyHint;

    private String signature = "";
    private long syncedRev = -1;
    private int count;
    private final List<Widget[]> editors = new ArrayList<>();   // [card][field] (null when hidden)
    private final List<Button> removes = new ArrayList<>();
    private Button addBtn;

    CardList(Editor ed, String arrayKey, String noun, List<Field> fields, Function<Integer, JsonObject> newEntry,
             Function<JsonObject, String> title, String emptyHint) {
        this.ed = ed;
        this.arrayKey = arrayKey;
        this.noun = noun;
        this.fields = fields;
        this.newEntry = newEntry;
        this.title = title;
        this.emptyHint = emptyHint;
        h = 40;
    }

    private JsonArray arr() {
        Asset a = ed.asset();
        return a == null ? new JsonArray() : J.arrOrCreate(a.json, arrayKey);
    }

    private JsonObject item(int i) {
        JsonArray a = arr();
        return i < a.size() && a.get(i).isJsonObject() ? a.get(i).getAsJsonObject() : new JsonObject();
    }

    private String sig() {
        StringBuilder sb = new StringBuilder();
        JsonArray a = arr();
        for (int i = 0; i < a.size(); i++) {
            JsonObject o = item(i);
            sb.append(J.str(o, "id", "#" + i)).append(':');
            for (Field f : fields) sb.append(f.visibleIf() == null || f.visibleIf().test(o) ? '1' : '0');
            sb.append(',');
        }
        return sb.toString();
    }

    private boolean visible(Field f, JsonObject o) { return f.visibleIf() == null || f.visibleIf().test(o); }

    private void rebuild() {
        clearChildren();
        editors.clear();
        removes.clear();
        count = arr().size();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            JsonObject o = item(i);
            Widget[] row = new Widget[fields.size()];
            for (int k = 0; k < fields.size(); k++) {
                Field f = fields.get(k);
                if (!visible(f, o)) continue;
                Widget w = makeEditor(idx, f);
                if (f.hint() != null) w.tooltip = f.hint();
                row[k] = add(w);
            }
            editors.add(row);
            Button rm = add(new Button("x", () -> ed.edit("Remove " + noun, j -> J.arrOrCreate(j, arrayKey).remove(idx))).style(Button.Style.DANGER));
            rm.tooltip = "Remove this " + noun;
            removes.add(rm);
        }
        addBtn = add(new Button("+ Add " + noun, () -> {
            int n = arr().size();
            ed.edit("Add " + noun, j -> J.arrOrCreate(j, arrayKey).add(newEntry.apply(n)));
        }).style(Button.Style.PRIMARY));
        signature = sig();
        syncedRev = -1;
        recomputeHeight();
    }

    private Widget makeEditor(int idx, Field f) {
        switch (f.kind()) {
            case STR -> {
                TextField t = new TextField();
                t.onChange = s -> set(idx, f, o -> o.addProperty(f.key(), s));
                return t;
            }
            case NUM, INT -> {
                boolean integer = f.kind() == Kind.INT;
                TextField t = new TextField().filter(integer ? TextField.Filter.INT : TextField.Filter.DECIMAL).range(f.min(), f.max(), f.step());
                t.onChange = s -> {
                    double v;
                    try { v = Double.parseDouble(s.trim()); } catch (NumberFormatException e) { t.invalid(true); return; }
                    t.invalid(v < f.min() || v > f.max());
                    set(idx, f, o -> J.setNum(o, f.key(), integer ? Math.rint(v) : v));
                };
                t.onCommit = s -> syncedRev = -1;
                return t;
            }
            case TOGGLE -> {
                return new Toggle(false, v -> set(idx, f, o -> o.addProperty(f.key(), v)));
            }
            case CHOICE -> {
                Dropdown d = new Dropdown();
                d.display = f.display() == null ? s -> s : f.display();
                d.onSelect = v -> set(idx, f, o -> o.addProperty(f.key(), v));
                return d;
            }
            default -> {
                Button b = new Button("", null);
                b.onClick = () -> openBiomes(idx, f, b);
                return b;
            }
        }
    }

    private void set(int idx, Field f, java.util.function.Consumer<JsonObject> m) {
        ed.edit(noun + " " + f.key(), j -> {
            JsonArray a = J.arrOrCreate(j, arrayKey);
            if (idx < a.size() && a.get(idx).isJsonObject()) m.accept(a.get(idx).getAsJsonObject());
        });
    }

    private void openBiomes(int idx, Field f, Widget anchor) {
        if (root == null || ed.project() == null) return;
        Map<String, String> items = new LinkedHashMap<>();
        for (Asset b : ed.project().assetsOf(AssetType.BIOME)) items.put(b.id(), b.displayName());
        Set<String> cur = new LinkedHashSet<>();
        JsonArray ja = J.arr(item(idx), f.key());
        if (ja != null) for (JsonElement e : ja) cur.add(e.getAsString());
        Checklist.open(root, anchor.x, anchor.y + anchor.h, Math.max(anchor.w, 160), items, cur, chosen ->
                set(idx, f, o -> { JsonArray out = new JsonArray(); for (String s : chosen) out.add(s); o.add(f.key(), out); }));
    }

    private void syncValues() {
        for (int i = 0; i < count && i < editors.size(); i++) {
            JsonObject o = item(i);
            for (int k = 0; k < fields.size(); k++) {
                Widget w = editors.get(i)[k];
                if (w == null) continue;
                Field f = fields.get(k);
                switch (f.kind()) {
                    case STR -> { TextField t = (TextField) w; if (ed.forceSync || !t.isFocused()) t.setText(J.str(o, f.key(), "")); }
                    case NUM, INT -> {
                        TextField t = (TextField) w;
                        if (ed.forceSync || !t.isFocused()) {
                            double v = J.num(o, f.key(), 0);
                            t.setText(Editor.fmt(v, f.kind() == Kind.INT));
                            t.invalid(v < f.min() || v > f.max());
                        }
                    }
                    case TOGGLE -> ((Toggle) w).checked = J.bool(o, f.key(), false);
                    case CHOICE -> {
                        Dropdown d = (Dropdown) w;
                        d.options(f.options().get());
                        d.selected(J.str(o, f.key(), ""));
                    }
                    default -> {
                        JsonArray ja = J.arr(o, f.key());
                        int n = ja == null ? 0 : ja.size();
                        ((Button) w).label = n == 0 ? "any biome" : n + (n == 1 ? " biome" : " biomes");
                    }
                }
            }
        }
        syncedRev = ed.project().revision;
    }

    private int cardHeight(int i) {
        JsonObject o = item(i);
        int rows = 0, col = 0;
        for (Field f : fields) {
            if (!visible(f, o)) continue;
            if (f.wide()) { if (col == 1) { rows++; col = 0; } rows++; }
            else { col++; if (col == 2) { rows++; col = 0; } }
        }
        if (col == 1) rows++;
        return CARD_HEAD + rows * ROW + PAD;
    }

    private void recomputeHeight() {
        int hh = 0;
        for (int i = 0; i < count; i++) hh += cardHeight(i) + 4;
        if (count == 0) hh = 18;
        h = hh + 24;
    }

    @Override
    public void layout() {
        int cy = y;
        for (int i = 0; i < count && i < editors.size(); i++) {
            int ch = cardHeight(i);
            removes.get(i).setBounds(x + w - 22, cy + 2, 16, 16);
            JsonObject o = item(i);
            int colW = (w - 2 * PAD - 8) / 2;
            int row = 0, col = 0;
            for (int k = 0; k < fields.size(); k++) {
                Field f = fields.get(k);
                Widget wd = editors.get(i)[k];
                if (wd == null) continue;
                int labelW = 74;
                if (f.wide()) {
                    if (col == 1) { row++; col = 0; }
                    wd.setBounds(x + PAD + labelW, cy + CARD_HEAD + row * ROW + 1, w - 2 * PAD - labelW, 16);
                    row++;
                } else {
                    int cx = x + PAD + col * (colW + 8);
                    wd.setBounds(cx + labelW, cy + CARD_HEAD + row * ROW + 1, colW - labelW, 16);
                    col++;
                    if (col == 2) { row++; col = 0; }
                }
            }
            cy += ch + 4;
        }
        if (addBtn != null) addBtn.setBounds(x, cy + 4, 120, 16);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        if (!signature.equals(sig())) { rebuild(); }
        if (syncedRev != ed.project().revision) syncValues();
        layout();
        int cy = y;
        if (count == 0) g.textMid(emptyHint, x + 2, y, 16, Ui.TEXT_FAINT);
        for (int i = 0; i < count && i < editors.size(); i++) {
            int ch = cardHeight(i);
            JsonObject o = item(i);
            g.box(x, cy, w, ch, Ui.BG_RAISED, Ui.LINE);
            g.fill(x, cy, 2, ch, Ui.ACCENT_DIM);
            g.textFit(title.apply(o), x + 8, cy, CARD_HEAD, w - 40, Ui.TEXT);
            int colW = (w - 2 * PAD - 8) / 2;
            int row = 0, col = 0;
            for (int k = 0; k < fields.size(); k++) {
                Field f = fields.get(k);
                if (!visible(f, o)) continue;
                int labelW = 74;
                if (f.wide()) {
                    if (col == 1) { row++; col = 0; }
                    g.textFit(f.label(), x + PAD, cy + CARD_HEAD + row * ROW, ROW, labelW - 4, Ui.TEXT_DIM);
                    row++;
                } else {
                    int cx = x + PAD + col * (colW + 8);
                    g.textFit(f.label(), cx, cy + CARD_HEAD + row * ROW, ROW, labelW - 4, Ui.TEXT_DIM);
                    col++;
                    if (col == 2) { row++; col = 0; }
                }
            }
            cy += ch + 4;
        }
    }
}
