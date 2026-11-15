package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.studio.graph.NodeSchemas;
import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An interactive demo of one density node, run by the real engine. Sliders set the node's parameters; every input it needs is
 * filled with a stand-in (the first with gentle noise, the rest with a constant), so the node's effect is visible. Shows a top-down
 * slice (x, z at a chosen height) and the vertical profile through the origin.
 */
final class DensityDemo extends Widget {
    private static final int GRID = 56, CELL = 3, PROFILE_N = 96;

    private final NodeSchemas.NodeType type;
    private final JsonObject json;
    private final List<Widget> controls = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final Slider viewSize, sliceY;
    private DensityFunction fn;
    private String error;
    private final float[] slice = new float[GRID * GRID];
    private final float[] profile = new float[PROFILE_N];
    private boolean dirty = true;
    final List<String> standIns = new ArrayList<>();

    private DensityDemo(NodeSchemas.NodeType type) {
        this.type = type;
        this.json = NodeSchemas.newNode(type);
        fillInputs();
        java.util.Set<String> seenKeys = new java.util.HashSet<>();
        for (NodeSchemas.Param p : type.params()) {
            if (controls.size() >= 7) break;
            if (!seenKeys.add(p.key().toLowerCase(java.util.Locale.ROOT))) continue;
            addControl(p);
        }
        viewSize = add(new Slider(32, 1024, 16, 256, true, v -> dirty = true));
        sliceY = add(new Slider(-64, 320, 4, 90, true, v -> dirty = true));
        this.h = 214;
    }

    /** A demo for this node, or null when it is not a density node the engine can build on its own. */
    static DensityDemo forNode(NodeSchemas.NodeType t) {
        if (!t.kind().equals("density")) return null;
        DensityDemo d = new DensityDemo(t);
        d.rebuild();
        return d.error == null ? d : null;
    }

    private void fillInputs() {
        int n = 0;
        for (NodeSchemas.Slot s : type.slots()) {
            if (!(s.kind().equals("density") || s.kind().equals("any"))) continue;
            if (s.multi()) {
                JsonArray arr = new JsonArray();
                int count = 2;
                for (int i = 0; i < count; i++) arr.add(standIn(n++, s.key() + "[" + i + "]"));
                json.add(s.key(), arr);
            } else {
                json.add(s.key(), standIn(n++, s.key()));
            }
        }
    }

    private JsonObject standIn(int index, String slot) {
        JsonObject o = new JsonObject();
        if (index == 0) {
            o.addProperty("type", "SimplexNoise2D");
            o.addProperty("scale", 48);
            o.addProperty("octaves", 2);
            o.addProperty("seed", "guide");
            standIns.add(slot + " = soft noise (scale 48)");
        } else {
            o.addProperty("type", "Constant");
            o.addProperty("value", 0.3);
            standIns.add(slot + " = constant 0.3");
        }
        return o;
    }

    private void addControl(NodeSchemas.Param p) {
        String t = p.type();
        JsonElement def = p.def();
        if ((t.equals("number") || t.equals("int")) && def != null && def.isJsonPrimitive() && def.getAsJsonPrimitive().isNumber()) {
            boolean integer = t.equals("int");
            double d = def.getAsDouble();
            double lo, hi;
            if (d == 0) { lo = -1; hi = 1; }
            else if (d > 0) { lo = 0; hi = Math.max(1, Math.abs(d) * 4); }
            else { lo = -Math.abs(d) * 4; hi = Math.abs(d) * 2; }
            double step = integer ? 1 : (hi - lo) / 200.0;
            if (!integer) step = Math.max(0.001, Math.pow(10, Math.floor(Math.log10(step))));
            Slider s = new Slider(lo, hi, step, d, integer, v -> { json.addProperty(p.key(), integer ? (Number) (long) Math.rint(v) : (Number) v); dirty = true; });
            controls.add(add(s));
            labels.add(p.key());
        } else if (t.equals("bool")) {
            boolean b = def != null && def.isJsonPrimitive() && def.getAsBoolean();
            Toggle tg = new Toggle(b, v -> { json.addProperty(p.key(), v); dirty = true; });
            controls.add(add(tg));
            labels.add(p.key());
        } else if (!p.enumOptions().isEmpty()) {
            Dropdown dd = new Dropdown();
            dd.options(p.enumOptions());
            String cur = def != null && def.isJsonPrimitive() ? def.getAsString() : p.enumOptions().get(0);
            dd.selected(cur);
            dd.onSelect = v -> { json.addProperty(p.key(), v); dirty = true; };
            controls.add(add(dd));
            labels.add(p.key());
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        try {
            JsonAstCompiler.registerBuiltInTypes();
            Map<String, Object> map = (Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(json.toString()).parseValue();
            fn = JsonAstCompiler.compileDensity(map, new JsonAstCompiler.CompileContext(1234L));
            float probe = fn.compute(3.5f, 70f, 9.5f);
            error = Float.isNaN(probe) && false ? "gives no value" : null;
        } catch (Throwable t) {
            fn = null;
            error = String.valueOf(t.getMessage());
        }
    }

    private void evaluate() {
        if (fn == null) return;
        float span = (float) viewSize.value;
        float y = (float) sliceY.value;
        try {
            for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) {
                float wx = (i - GRID / 2f) * span / GRID, wz = (j - GRID / 2f) * span / GRID;
                slice[j * GRID + i] = fn.compute(wx, y, wz);
            }
            for (int i = 0; i < PROFILE_N; i++) profile[i] = fn.compute(0.5f, -64 + (i + 0.5f) * 384f / PROFILE_N, 0.5f);
        } catch (Throwable t) {
            error = String.valueOf(t.getMessage());
        }
    }

    @Override
    public void layout() {
        int cx = x + 4, cw = 200;
        int ry = y + 22;
        for (int i = 0; i < controls.size(); i++) {
            controls.get(i).setBounds(cx + 74, ry + i * 20, cw - 74, 16);
        }
        int by = y + h - 22 - 20;
        viewSize.setBounds(cx + 74, y + h - 40, cw - 74, 16);
        sliceY.setBounds(cx + 74, y + h - 20, cw - 74, 16);
    }

    private static int shade(float v) {
        if (Float.isNaN(v)) return 0xFFFF00FF;
        float t = Math.max(-1f, Math.min(1f, v));
        // air (negative) deep blue, zero pale, solid (positive) warm stone
        if (t < 0) return Gfx.mix(0xFFE8ECF4, 0xFF15305E, -t);
        return Gfx.mix(0xFFE8ECF4, 0xFFB5651D, t);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        if (dirty) { rebuild(); evaluate(); dirty = false; }
        g.box(x, y, w, h, Ui.BG_RAISED, Ui.LINE);
        g.textMid("TRY IT  (the real node, run live)", x + 6, y + 2, 16, Ui.ACCENT);
        for (int i = 0; i < controls.size(); i++) g.textMid(g.fit(labels.get(i), 70), x + 6, controls.get(i).y, 16, Ui.TEXT_DIM);
        g.textMid("view (blocks)", x + 6, viewSize.y, 16, Ui.TEXT_DIM);
        g.textMid("slice at Y", x + 6, sliceY.y, 16, Ui.TEXT_DIM);

        if (error != null) {
            g.textFit("Cannot run this node here: " + error, x + 214, y + 24, 14, w - 220, Ui.RED);
            return;
        }
        int px = x + 214, py = y + 22;
        for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) g.fill(px + i * CELL, py + j * CELL, CELL, CELL, shade(slice[j * GRID + i]));
        g.border(px - 1, py - 1, GRID * CELL + 2, GRID * CELL + 2, Ui.LINE_STRONG);
        g.textMid("top-down slice", px, py + GRID * CELL + 1, 12, Ui.TEXT_FAINT);

        // vertical profile at the origin: density against height
        int fx = px + GRID * CELL + 14, fw = Math.min(110, x + w - fx - 8), fh = GRID * CELL;
        if (fw > 30) {
            g.box(fx, py, fw, fh, Ui.BG_INPUT, Ui.LINE_STRONG);
            g.vline(fx + fw / 2, py, fh, Ui.LINE_STRONG);
            for (int i = 0; i < PROFILE_N; i++) {
                float v = Math.max(-1f, Math.min(1f, Float.isNaN(profile[i]) ? 0 : profile[i]));
                int yy = py + fh - 1 - (int) ((i + 0.5f) / PROFILE_N * (fh - 2));
                int half = (fw - 4) / 2;
                int len = (int) (Math.abs(v) * half);
                g.fill(v >= 0 ? fx + fw / 2 : fx + fw / 2 - len, yy, Math.max(1, len), 1, v >= 0 ? 0xFFB5651D : 0xFF2F5FA8);
            }
            int sy = py + fh - 1 - (int) ((sliceY.value + 64) / 384.0 * (fh - 2));
            g.hline(fx, Math.max(py, Math.min(py + fh - 1, sy)), fw, Ui.ACCENT);
            g.textMid("height Y -64 to 320", fx, py + fh + 1, 12, Ui.TEXT_FAINT);
        }
    }
}
