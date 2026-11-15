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
final class DensityDemo extends Widget implements DemoInfo {
    @Override public List<String> standIns() { return standIns; }

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
        this.curve = type.kind().equals("curve");
        this.material = type.kind().equals("material");
        this.positions = type.kind().equals("positions");
        viewSize = add(curve ? new Slider(0.5, 20, 0.5, 2, false, v -> dirty = true)
                : material ? new Slider(0, 12, 1, 5, true, v -> dirty = true)
                : new Slider(32, 1024, 16, positions ? 192 : 256, true, v -> dirty = true));
        sliceY = add(material ? new Slider(0, 90, 5, 0, true, v -> dirty = true) : new Slider(-64, 320, 4, 90, true, v -> dirty = true));
        sliceY.visible = !curve && !positions;
        this.h = 214;
    }

    private final boolean curve, material, positions;
    private com.worldgenv2.material.MaterialProvider<String> matFn;
    private com.worldgenv2.positions.PositionsProvider posFn;
    private final String[] column = new String[26];
    private List<com.worldgenv2.vector.Vec3d> points = new ArrayList<>();
    private static final com.worldgenv2.api.VoxelSpace<String> SPACE = new com.worldgenv2.api.VoxelSpace<>() {
        public String getBlock(int x, int y, int z) { return "minecraft:stone"; }
        public void setBlock(int x, int y, int z, String b) {}
        public boolean isAir(int x, int y, int z) { return false; }
        public int getMinY() { return -64; }
        public int getMaxY() { return 320; }
    };
    private com.worldgenv2.curve.Curve curveFn;
    private final float[] plot = new float[160];

    /** A demo for this node, or null when it is not a density or curve node the engine can build on its own. */
    static DensityDemo forNode(NodeSchemas.NodeType t) {
        if (!(t.kind().equals("density") || t.kind().equals("curve") || t.kind().equals("material") || t.kind().equals("positions"))) return null;
        DensityDemo d = new DensityDemo(t);
        d.rebuild();
        return d.error == null ? d : null;
    }

    private int materialStandIns;

    private void fillInputs() {
        int n = 0;
        for (NodeSchemas.Slot s : type.slots()) {
            if (s.kind().equals("curve")) {
                boolean inCurve = type.kind().equals("curve");
                // a stand-in curve that rises from -1 to 1, so what this node does to its input is easy to see
                JsonObject c = new JsonObject();
                c.addProperty("type", "Manual");
                JsonArray pts = new JsonArray();
                for (double[] pt : inCurve ? new double[][]{{-1, -1}, {1, 1}} : new double[][]{{0, 1}, {120, -1}}) { JsonObject p = new JsonObject(); p.addProperty("in", pt[0]); p.addProperty("out", pt[1]); pts.add(p); }
                c.add("points", pts);
                if (s.multi()) { JsonArray arr = new JsonArray(); arr.add(c); arr.add(c.deepCopy()); json.add(s.key(), arr); } else json.add(s.key(), c);
                standIns.add(s.key() + (inCurve ? " = a straight line from -1 to 1" : " = a line falling from 1 at 0 to -1 at 120"));
                continue;
            }
            if (type.kind().equals("material") && s.kind().equals("material")) {
                String[] blocks = {"minecraft:grass_block", "minecraft:dirt", "minecraft:sandstone", "minecraft:stone"};
                java.util.function.IntFunction<JsonObject> mk = i -> {
                    if (i == 0 && s.multi()) {      // the first child only applies to the top few blocks, so the later ones show through below it
                        JsonObject band = new JsonObject();
                        band.addProperty("type", "SimpleHorizontal");
                        band.addProperty("topY", 65);
                        band.addProperty("bottomY", 62);
                        JsonObject inner = new JsonObject();
                        inner.addProperty("type", "Constant");
                        inner.addProperty("material", blocks[0]);
                        band.add("material", inner);
                        return band;
                    }
                    JsonObject c = new JsonObject(); c.addProperty("type", "Constant"); c.addProperty("material", blocks[i % blocks.length]); return c; };
                int base = materialStandIns;
                if (s.multi()) { JsonArray arr = new JsonArray(); for (int i = 0; i < 3; i++) arr.add(mk.apply(materialStandIns++)); json.add(s.key(), arr); }
                else json.add(s.key(), mk.apply(materialStandIns++));
                standIns.add(s.key() + " = " + (s.multi() ? "grass for the top 3 blocks, then dirt, then sandstone" : blocks[base % blocks.length]));
                continue;
            }
            if (type.kind().equals("positions") && s.kind().equals("positions")) {
                JsonObject g = new JsonObject();
                g.addProperty("type", "TriangularGrid2d");
                if (s.multi()) { JsonArray arr = new JsonArray(); arr.add(g); json.add(s.key(), arr); } else json.add(s.key(), g);
                standIns.add(s.key() + " = a regular triangular grid, one point per block");
                continue;
            }
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
            JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1234L);
            if (material) {
                matFn = JsonAstCompiler.compileMaterialProvider(map, ctx, id -> id);
                matFn.getMaterial(0, 64, 0, 0, 5, 0f, SPACE);
                error = null;
            } else if (positions) {
                posFn = JsonAstCompiler.compilePositionsProvider(map, ctx);
                posFn.getPositions(0, 0, 0, 16, 16, 16);
                error = null;
            } else if (curve) {
                curveFn = JsonAstCompiler.compileCurve(map, ctx);
                double probe = curveFn.evaluate(0.25);
                error = curveFn == null ? "no curve" : null;
                if (Double.isNaN(probe) && false) error = "no value";
            } else {
                fn = JsonAstCompiler.compileDensity(map, ctx);
                fn.compute(3.5f, 70f, 9.5f);
                error = null;
            }
        } catch (Throwable t) {
            fn = null;
            error = String.valueOf(t.getMessage());
        }
    }

    private void evaluate() {
        if (material) {
            if (matFn == null) return;
            try {
                for (int d = 0; d < column.length; d++) column[d] = String.valueOf(matFn.getMaterial(0, 64 - d, 0, d, (int) viewSize.value, (float) sliceY.value, SPACE));
            } catch (Throwable t) { error = String.valueOf(t.getMessage()); }
            return;
        }
        if (positions) {
            if (posFn == null) return;
            try {
                double half = viewSize.value / 2;
                points = posFn.getPositions(-half, -64, -half, half, 320, half);
            } catch (Throwable t) { error = String.valueOf(t.getMessage()); }
            return;
        }
        if (curve) {
            if (curveFn == null) return;
            double r = viewSize.value;
            try { for (int i = 0; i < plot.length; i++) plot[i] = (float) curveFn.evaluate(-r + 2 * r * i / (plot.length - 1.0)); }
            catch (Throwable t) { error = String.valueOf(t.getMessage()); }
            return;
        }
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

    private void drawCurve(Gfx g, int px, int py) {
        int pw = Math.min(GRID * CELL + 130, x + w - px - 10), ph = GRID * CELL;
        g.box(px, py, pw, ph, Ui.BG_INPUT, Ui.LINE_STRONG);
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float v : plot) { if (Float.isNaN(v) || Float.isInfinite(v)) continue; lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (lo > hi) return;
        lo = Math.min(lo, -1f); hi = Math.max(hi, 1f);
        float span = hi - lo;
        int zeroY = py + ph - 1 - (int) ((0 - lo) / span * (ph - 2));
        g.hline(px, zeroY, pw, Ui.LINE_STRONG);
        g.vline(px + pw / 2, py, ph, Ui.LINE_STRONG);
        int prevX = -1, prevY = -1;
        for (int i = 0; i < plot.length; i++) {
            if (Float.isNaN(plot[i])) { prevX = -1; continue; }
            int cx = px + 1 + (int) ((long) i * (pw - 3) / (plot.length - 1));
            int cy = py + ph - 1 - (int) ((plot[i] - lo) / span * (ph - 2));
            cy = Math.max(py, Math.min(py + ph - 1, cy));
            if (prevX >= 0) { int a = Math.min(prevY, cy), b = Math.max(prevY, cy); g.fill(cx, a, 2, Math.max(1, b - a + 1), Ui.ACCENT); }
            else g.fill(cx, cy, 2, 2, Ui.ACCENT);
            prevX = cx; prevY = cy;
        }
        g.textMid("output against input (the line you set; the grey cross is zero)", px, py + ph + 1, 12, Ui.TEXT_FAINT);
    }

    private static int blockColor(String id) {
        int h = id.hashCode();
        int r = 70 + ((h >> 16) & 0x7F), gg = 70 + ((h >> 8) & 0x7F), b = 70 + (h & 0x7F);
        if (id.contains("grass")) return 0xFF5FA843;
        if (id.contains("dirt")) return 0xFF8A5A33;
        if (id.contains("sand")) return 0xFFE0CF8E;
        if (id.contains("snow")) return 0xFFF2F6FA;
        if (id.contains("stone") || id.contains("andesite")) return 0xFF8A8A8A;
        if (id.contains("water")) return 0xFF3A6FD0;
        if (id.contains("air")) return 0xFF0D0F14;
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    /** A column of blocks going down from the surface: what this provider puts at each depth. */
    private void drawColumn(Gfx g, int px, int py) {
        int cw = 40, rh = 12;
        g.textMid("depth", px, py - 12, 12, Ui.TEXT_FAINT);
        for (int d = 0; d < column.length; d++) {
            int cy = py + d * rh;
            if (cy + rh > y + h - 4) break;
            g.fill(px + 24, cy, cw, rh - 1, blockColor(column[d]));
            g.textMid(String.valueOf(d), px, cy, rh, Ui.TEXT_FAINT);
            boolean change = d == 0 || !column[d].equals(column[d - 1]);
            if (change) {
                BlockCatalog.Entry e = BlockCatalog.of(column[d]);
                g.textMid(e != null ? e.name() : column[d], px + 24 + cw + 8, cy, rh, Ui.TEXT);
            }
        }
        g.textMid("blocks under a surface at Y 64, top to bottom", px + 24 + cw + 170, py, 12, Ui.TEXT_FAINT);
    }

    /** The points the node generates, seen from above. */
    private void drawPoints(Gfx g, int px, int py) {
        int size = GRID * CELL + 40;
        g.box(px, py, size, size, Ui.BG_INPUT, Ui.LINE_STRONG);
        double half = viewSize.value / 2;
        int shown = 0;
        for (com.worldgenv2.vector.Vec3d p : points) {
            int dx = (int) ((p.x() + half) / (2 * half) * (size - 2)), dz = (int) ((p.z() + half) / (2 * half) * (size - 2));
            if (dx < 0 || dz < 0 || dx >= size - 1 || dz >= size - 1) continue;
            g.fill(px + 1 + dx, py + 1 + dz, 2, 2, Ui.ACCENT);
            if (++shown > 6000) break;
        }
        g.textMid(points.size() + " points in " + (int) viewSize.value + " x " + (int) viewSize.value + " blocks, seen from above", px, py + size + 1, 12, Ui.TEXT_FAINT);
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
        g.textMid(curve ? "input range +-" : material ? "air above" : "view (blocks)", x + 6, viewSize.y, 16, Ui.TEXT_DIM);
        if (material) g.textMid("slope (deg)", x + 6, sliceY.y, 16, Ui.TEXT_DIM);
        else if (!curve && !positions) g.textMid("slice at Y", x + 6, sliceY.y, 16, Ui.TEXT_DIM);

        if (error != null) {
            g.textFit("Cannot run this node here: " + error, x + 214, y + 24, 14, w - 220, Ui.RED);
            return;
        }
        int px = x + 214, py = y + 22;
        if (curve) { drawCurve(g, px, py); return; }
        if (material) { drawColumn(g, px, py); return; }
        if (positions) { drawPoints(g, px, py); return; }
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
