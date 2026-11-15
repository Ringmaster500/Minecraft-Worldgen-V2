package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.material.patterns.Pattern;
import com.worldgenv2.props.Prop;
import com.worldgenv2.scanner.Scanner;
import com.worldgenv2.studio.graph.NodeSchemas;
import com.worldgenv2.studio.ui.*;
import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An interactive demo for the nodes that work on blocks: patterns (where they match), scanners (what they search), props (what they
 * build), vector providers (the arrows they make) and assignments (which prop lands where). Everything runs on a small scratch
 * landscape through the real engine. Hill height changes the ground, so slopes and surfaces change with it.
 */
final class SceneDemo extends Widget implements DemoInfo {
    private enum Mode { PATTERN, SCANNER, PROP, VECTOR, ASSIGN }

    private static final int X0 = -24, W = 49, Y0 = 50, H = 50, CELL = 3;
    private static final String AIR = "minecraft:air";

    /** The scratch landscape: rolling ground (grass, dirt, stone) with whatever a prop builds laid over it. */
    private static final class Scene implements VoxelSpace<String> {
        final double hill;
        final Map<Long, String> over = new HashMap<>();

        Scene(double hill) { this.hill = hill; }

        static long key(int x, int y, int z) { return ((long) (x + 4096) << 40) | ((long) (y + 4096) << 20) | (z + 4096); }

        int surface(int x, int z) { return 64 + (int) Math.round(hill * Math.sin(x / 9.0)) + (int) Math.round(hill * 0.4 * Math.cos(z / 7.0)); }

        @Override public String getBlock(int x, int y, int z) {
            String o = over.get(key(x, y, z));
            if (o != null) return o;
            int s = surface(x, z);
            if (y > s) return AIR;
            if (y == s) return "minecraft:grass_block";
            if (y > s - 4) return "minecraft:dirt";
            return "minecraft:stone";
        }
        @Override public void setBlock(int x, int y, int z, String b) { over.put(key(x, y, z), b == null ? AIR : b); }
        @Override public boolean isAir(int x, int y, int z) { return AIR.equals(getBlock(x, y, z)); }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 128; }
    }

    private final NodeSchemas.NodeType type;
    private final Mode mode;
    private final JsonObject json;
    private final List<Widget> controls = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final Slider hillSlider, zSlider;
    private final List<String> standIns = new ArrayList<>();
    private boolean dirty = true;
    private String error;

    private Pattern<String> pattern;
    private Scanner scanner;
    private Prop<String> prop;
    private VectorProvider vectors;
    private Assignments<String> assignments;

    private Scene scene = new Scene(3);
    private final boolean[] match = new boolean[W * H];
    private List<Vec3d> found = new ArrayList<>();
    private final String[] assigned = new String[81];
    private final Vec3d[] arrows = new Vec3d[144];

    private SceneDemo(NodeSchemas.NodeType type, Mode mode) {
        this.type = type;
        this.mode = mode;
        this.json = NodeSchemas.newNode(type);
        fillInputs();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (NodeSchemas.Param p : type.params()) {
            if (controls.size() >= 6) break;
            if (!seen.add(p.key().toLowerCase(java.util.Locale.ROOT))) continue;
            addControl(p);
        }
        hillSlider = add(new Slider(0, 10, 1, 3, true, v -> dirty = true));
        zSlider = add(new Slider(-12, 12, 1, 0, true, v -> dirty = true));
        zSlider.visible = mode == Mode.PROP;
        this.h = 214;
    }

    /** A demo for this node, or null when it is not one of these kinds or the engine cannot build it on its own. */
    static SceneDemo forNode(NodeSchemas.NodeType t) {
        Mode m = switch (t.kind()) {
            case "pattern" -> Mode.PATTERN;
            case "scanner" -> Mode.SCANNER;
            case "prop" -> Mode.PROP;
            case "vector" -> Mode.VECTOR;
            case "assignments" -> Mode.ASSIGN;
            default -> null;
        };
        if (m == null) return null;
        SceneDemo d = new SceneDemo(t, m);
        d.rebuild();
        if (d.error != null) lastFailure = t.name() + ": " + d.error;
        return d.error == null ? d : null;
    }

    static String lastFailure;

    @Override public List<String> standIns() { return standIns; }

    private JsonObject block(String id, int height) {
        JsonObject b = new JsonObject();
        b.addProperty("type", "BlockProp");
        b.addProperty("block", id);
        b.addProperty("material", id);
        b.addProperty("height", height);
        return b;
    }

    private JsonObject stand(String kind, int index, String slot) {
        JsonObject o = new JsonObject();
        switch (kind) {
            case "pattern" -> {
                // Hytale's own pattern nodes take Hytale's patterns; ours take ours
                boolean hytale = java.util.Set.of("And", "Or", "Not", "Offset", "Floor", "Ceiling", "Wall", "Cuboid", "Locator", "Orienter", "FieldFunction").contains(type.name());
                o.addProperty("type", hytale ? "Floor" : "SurfacePattern");
                standIns.add(slot + (hytale ? " = a floor (solid ground with air above)" : " = the ground surface"));
            }
            case "assignments" -> {
                o.addProperty("type", "Constant");
                o.add("prop", block("minecraft:oak_log", 4));
                standIns.add(slot + " = always a log 4 blocks tall");
            }
            case "scanner" -> {
                boolean hytaleScan = type.name().equals("Locator") || type.name().equals("Orienter");
                o.addProperty("type", hytaleScan ? "Linear" : "LinearScanner");
                standIns.add(slot + " = a straight scan up and down");
            }
            case "positions" -> { o.addProperty("type", "TriangularGrid2d"); standIns.add(slot + " = a regular grid of points"); }
            case "prop" -> {
                String[] ids = {"minecraft:oak_log", "minecraft:stone", "minecraft:glowstone"};
                int[] hs = {4, 1, 1};
                JsonObject b = block(ids[index % ids.length], hs[index % hs.length]);
                standIns.add(slot + " = " + (hs[index % hs.length] > 1 ? "a log " + hs[index % hs.length] + " blocks tall" : ids[index % ids.length].substring(10)));
                return b;
            }
            default -> { return null; }
        }
        return o;
    }

    private void fillInputs() {
        int n = 0;
        for (NodeSchemas.Param bp : type.params()) {
            if (bp.key().equalsIgnoreCase("bounds") && !json.has(bp.key())) {      // a box around the anchor, so scanners and cuboids have something to work with
                json.add(bp.key(), com.google.gson.JsonParser.parseString("{\"Min\":{\"X\":-6,\"Y\":-4,\"Z\":-6},\"Max\":{\"X\":6,\"Y\":4,\"Z\":6}}"));
                standIns.add(bp.key() + " = a box 13 wide and 9 tall around the start");
            }
        }
        for (NodeSchemas.Slot s : type.slots()) {
            String k = s.kind();
            if (k.equals("vector")) {
                JsonArray v = new JsonArray();
                v.add(1); v.add(0); v.add(0.5);
                json.add(s.key(), v);
                standIns.add(s.key() + " = the direction (1, 0, 0.5)");
                continue;
            }
            if (k.equals("density")) {
                JsonObject d = new JsonObject();
                d.addProperty("type", "SimplexNoise2D");
                d.addProperty("scale", 20);
                d.addProperty("seed", "guide");
                json.add(s.key(), d);
                standIns.add(s.key() + " = soft noise (scale 20)");
                continue;
            }
            if (!(k.equals("pattern") || k.equals("scanner") || k.equals("positions") || k.equals("prop") || k.equals("assignments"))) continue;
            if (s.multi()) {
                JsonArray arr = new JsonArray();
                int count = k.equals("prop") ? 2 : 1;
                for (int i = 0; i < count; i++) arr.add(stand(k, n++, s.key() + "[" + i + "]"));
                json.add(s.key(), arr);
            } else {
                json.add(s.key(), stand(k, n++, s.key()));
            }
        }
    }

    private void addControl(NodeSchemas.Param p) {
        String t = p.type();
        JsonElement def = p.def();
        if ((t.equals("number") || t.equals("int")) && def != null && def.isJsonPrimitive() && def.getAsJsonPrimitive().isNumber()) {
            boolean integer = t.equals("int");
            double d = def.getAsDouble();
            double lo, hi;
            if (d == 0) { lo = -1; hi = 1; if (integer) { lo = 0; hi = 8; } }
            else if (d > 0) { lo = 0; hi = Math.max(1, Math.abs(d) * 4); }
            else { lo = -Math.abs(d) * 4; hi = Math.abs(d) * 2; }
            double step = integer ? 1 : (hi - lo) / 200.0;
            if (!integer) step = Math.max(0.001, Math.pow(10, Math.floor(Math.log10(step))));
            Slider s = new Slider(lo, hi, step, d, integer, v -> { json.addProperty(p.key(), integer ? (Number) (long) Math.rint(v) : (Number) v); dirty = true; });
            controls.add(add(s));
            labels.add(p.key());
        } else if (t.equals("bool")) {
            boolean b = def != null && def.isJsonPrimitive() && def.getAsBoolean();
            controls.add(add(new Toggle(b, v -> { json.addProperty(p.key(), v); dirty = true; })));
            labels.add(p.key());
        } else if (!p.enumOptions().isEmpty()) {
            Dropdown dd = new Dropdown();
            dd.options(p.enumOptions());
            dd.selected(def != null && def.isJsonPrimitive() ? def.getAsString() : p.enumOptions().get(0));
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
            switch (mode) {
                case PATTERN -> pattern = JsonAstCompiler.<String>compilePattern(map, ctx, id -> id);
                case SCANNER -> scanner = JsonAstCompiler.compileScanner(map, ctx);
                case PROP -> prop = JsonAstCompiler.<String>compileProp(map, ctx, id -> id);
                case VECTOR -> vectors = JsonAstCompiler.compileVectorProvider(map, ctx);
                case ASSIGN -> assignments = JsonAstCompiler.<String>compileAssignments(map, ctx, id -> id);
            }
            error = null;
            scene = new Scene(3);
            evaluate();
        } catch (Throwable t) {
            error = String.valueOf(t.getMessage());
        }
    }

    private void evaluate() {
        scene = new Scene(hillSlider == null ? 3 : hillSlider.value);
        try {
            switch (mode) {
                case PATTERN -> {
                    for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
                        int x = X0 + i, y = Y0 + j;
                        int sy = scene.surface(x, 0);
                        int depth = y <= sy ? sy - y : -1;
                        int space = Math.max(0, 127 - sy);
                        float slope = (float) Math.toDegrees(Math.atan(Math.abs(scene.surface(x + 1, 0) - scene.surface(x - 1, 0)) / 2.0));
                        match[j * W + i] = pattern.matches(x, y, 0, depth, space, slope, scene);
                    }
                }
                case SCANNER -> found = scanner.scan(new Vec3d(0, scene.surface(0, 0), 0), scene, b -> !AIR.equals(b));
                case PROP -> prop.generate(new Prop.Context<>(0, scene.surface(0, 0) + 1, 0, 0f, 1f, 0f, scene, 1234L));
                case VECTOR -> {
                    for (int j = 0; j < 12; j++) for (int i = 0; i < 12; i++) arrows[j * 12 + i] = vectors.getVector(-24 + i * 4.4, 64, -24 + j * 4.4);
                }
                case ASSIGN -> {
                    for (int j = 0; j < 9; j++) for (int i = 0; i < 9; i++) {
                        int x = -24 + i * 6, z = -24 + j * 6, y = scene.surface(x, z) + 1;
                        Prop<String> p = assignments.assign(new Vec3d(x, y, z), 1234L + i * 31L + j);
                        String before = scene.getBlock(x, y, z);
                        if (p != null) p.generate(new Prop.Context<>(x, y, z, 0f, 1f, 0f, scene, 1234L));
                        String after = scene.getBlock(x, y, z);
                        assigned[j * 9 + i] = AIR.equals(after) || after.equals(before) ? null : after;
                    }
                }
            }
        } catch (Throwable t) {
            error = String.valueOf(t.getMessage());
        }
    }

    @Override
    public void layout() {
        int cx = x + 4, cw = 200;
        for (int i = 0; i < controls.size(); i++) controls.get(i).setBounds(cx + 74, y + 22 + i * 20, cw - 74, 16);
        hillSlider.setBounds(cx + 74, y + h - 40, cw - 74, 16);
        zSlider.setBounds(cx + 74, y + h - 20, cw - 74, 16);
    }

    private static int color(String id) {
        if (id == null || AIR.equals(id)) return 0xFF10141C;
        if (id.contains("grass")) return 0xFF5FA843;
        if (id.contains("dirt")) return 0xFF8A5A33;
        if (id.contains("stone") && !id.contains("glow")) return 0xFF7C7C7C;
        if (id.contains("log")) return 0xFF6B4A2B;
        if (id.contains("leaves")) return 0xFF2F7A2F;
        if (id.contains("glow")) return 0xFFFFD866;
        if (id.contains("water")) return 0xFF3A6FD0;
        if (id.contains("sand")) return 0xFFE0CF8E;
        int hsh = id.hashCode();
        return 0xFF000000 | ((70 + ((hsh >> 16) & 0x7F)) << 16) | ((70 + ((hsh >> 8) & 0x7F)) << 8) | (70 + (hsh & 0x7F));
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        if (dirty) { evaluate(); dirty = false; }
        g.box(x, y, w, h, Ui.BG_RAISED, Ui.LINE);
        g.textMid("TRY IT  (the real node, run live)", x + 6, y + 2, 16, Ui.ACCENT);
        for (int i = 0; i < controls.size(); i++) g.textMid(g.fit(labels.get(i), 70), x + 6, controls.get(i).y, 16, Ui.TEXT_DIM);
        g.textMid("hill height", x + 6, hillSlider.y, 16, Ui.TEXT_DIM);
        if (zSlider.visible) g.textMid("slice depth", x + 6, zSlider.y, 16, Ui.TEXT_DIM);
        if (error != null) { g.textFit("Cannot run this node here: " + error, x + 214, y + 24, 14, w - 220, Ui.RED); return; }

        int px = x + 214, py = y + 22;
        switch (mode) {
            case VECTOR -> drawArrows(g, px, py);
            case ASSIGN -> drawAssigned(g, px, py);
            default -> drawSide(g, px, py);
        }
    }

    private void drawSide(Gfx g, int px, int py) {
        int z = mode == Mode.PROP ? (int) zSlider.value : 0;
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) {
            int bx = X0 + i, by = Y0 + H - 1 - j;
            String b = scene.getBlock(bx, by, z);
            int c = color(b);
            if (mode == Mode.PATTERN && match[(H - 1 - j) * W + i]) c = Gfx.mix(c, 0xFFFFAA00, 0.7f);
            g.fill(px + i * CELL, py + j * CELL, CELL, CELL, c);
        }
        g.border(px - 1, py - 1, W * CELL + 2, H * CELL + 2, Ui.LINE_STRONG);
        if (mode == Mode.SCANNER) {
            for (Vec3d p : found) {
                int ix = (int) Math.floor(p.x()) - X0, iy = Y0 + H - 1 - (int) Math.floor(p.y());
                if (ix < 0 || ix >= W || iy < 0 || iy >= H) continue;
                g.fill(px + ix * CELL, py + iy * CELL, CELL, CELL, Gfx.mix(color(scene.getBlock((int) p.x(), (int) p.y(), (int) p.z())), 0xFFFFAA00, 0.75f));
            }
            int ax = -X0, ay = Y0 + H - 1 - scene.surface(0, 0);
            g.fill(px + ax * CELL - 1, py + ay * CELL - 1, CELL + 2, CELL + 2, 0xFFFF3040);
        }
        String note = switch (mode) {
            case PATTERN -> "side view; orange = blocks the pattern matches";
            case SCANNER -> found.size() + " positions found (orange); red = where the search starts";
            default -> "side view of what the prop builds at the centre (slice depth moves the cut)";
        };
        g.textMid(note, px, py + H * CELL + 1, 12, Ui.TEXT_FAINT);
        // a legend on the right
        int lx = px + W * CELL + 14;
        if (lx + 80 < x + w) {
            g.text("grass", lx + 12, py, Ui.TEXT_DIM);  g.fill(lx, py + 1, 8, 8, color("minecraft:grass_block"));
            g.text("dirt", lx + 12, py + 14, Ui.TEXT_DIM); g.fill(lx, py + 15, 8, 8, color("minecraft:dirt"));
            g.text("stone", lx + 12, py + 28, Ui.TEXT_DIM); g.fill(lx, py + 29, 8, 8, color("minecraft:stone"));
            g.text("air", lx + 12, py + 42, Ui.TEXT_DIM); g.fill(lx, py + 43, 8, 8, color(AIR));
        }
    }

    private void drawArrows(Gfx g, int px, int py) {
        int size = 12 * 16;
        g.box(px, py, size, size, Ui.BG_INPUT, Ui.LINE_STRONG);
        for (int j = 0; j < 12; j++) for (int i = 0; i < 12; i++) {
            Vec3d v = arrows[j * 12 + i];
            if (v == null) continue;
            double len = Math.sqrt(v.x() * v.x() + v.z() * v.z());
            int cx = px + i * 16 + 8, cy = py + j * 16 + 8;
            g.fill(cx - 1, cy - 1, 3, 3, Ui.TEXT_FAINT);
            if (len < 1e-6) continue;
            double s = Math.min(7.0, 7.0 * len) / len;
            int ex = cx + (int) Math.round(v.x() * s), ey = cy + (int) Math.round(v.z() * s);
            int steps = Math.max(Math.abs(ex - cx), Math.abs(ey - cy));
            for (int k = 0; k <= steps; k++) {
                int sx = steps == 0 ? cx : cx + (ex - cx) * k / steps, sy = steps == 0 ? cy : cy + (ey - cy) * k / steps;
                g.fill(sx, sy, 1, 1, Ui.ACCENT);
            }
            g.fill(ex - 1, ey - 1, 3, 3, Ui.CYAN);
        }
        g.textMid("top-down: each arrow is the vector there (across and down are X and Z); the blue tip is its head", px, py + size + 1, 12, Ui.TEXT_FAINT);
    }

    private void drawAssigned(Gfx g, int px, int py) {
        int size = 9 * 20;
        g.box(px, py, size, size, Ui.BG_INPUT, Ui.LINE_STRONG);
        for (int j = 0; j < 9; j++) for (int i = 0; i < 9; i++) {
            String a = assigned[j * 9 + i];
            int cx = px + i * 20 + 4, cy = py + j * 20 + 4;
            if (a == null) g.fill(cx + 4, cy + 4, 4, 4, Ui.LINE_STRONG);
            else g.fill(cx, cy, 12, 12, color(a));
        }
        g.textMid("top-down: a coloured square is the prop picked at that spot; grey = none", px, py + size + 1, 12, Ui.TEXT_FAINT);
    }
}
