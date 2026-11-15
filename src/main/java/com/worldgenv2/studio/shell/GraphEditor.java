package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldgenv2.studio.graph.GraphOps;
import com.worldgenv2.studio.graph.NodeSchemas;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.ui.*;
import net.minecraft.client.Minecraft;

import java.util.*;

/**
 * Node graph over a biome's JSON. The whole biome is one graph: a Biome output node with sockets for terrain,
 * materials, props and placement noise, each wired to the tree of nodes that feeds it. There is no separate
 * graph file format; every edit rewrites the biome JSON through {@code StudioProject.edit}.
 */
final class GraphEditor extends Widget {

    // ---- layout constants (graph units) ----
    private static final int NODE_W = 172, HEAD_H = 15, SLOT_H = 13, PARAM_H = 11, GAP_X = 46, GAP_Y = 10, MAX_PARAMS = 5;
    private static final int INSPECTOR_W = 252;

    private static final class GSlot {
        String key, label, kind;
        boolean multi;
        final List<GNode> children = new ArrayList<>();
        String inline; // primitive value shown instead of a child (e.g. a block id)
        int rowOffset;
    }

    private static final class GNode {
        List<Object> path;
        JsonObject json;
        String type, kind;
        NodeSchemas.NodeType schema;
        boolean virtualRoot;
        final List<GSlot> slots = new ArrayList<>();
        final List<String[]> params = new ArrayList<>();
        int extraParams;
        int x, y, w = NODE_W, h;
        GNode parent;
        GSlot inSlot;
        int depth;
    }

    private final StudioApp app;
    private final String assetPath;
    private final Canvas canvas;
    private final Inspector inspector;

    private GNode root;
    private final Map<String, GNode> byId = new HashMap<>();
    private List<Object> selected = List.of();
    private long builtRev = -1;
    private double panX, panY, zoom = 1;
    private boolean needFit = true;
    private int nodeCount;

    GraphEditor(StudioApp app, Asset a) {
        this.app = app;
        this.assetPath = a.path;
        canvas = add(new Canvas());
        inspector = add(new Inspector());
    }

    private Asset asset() { return app.project.get(assetPath); }

    private void edit(String label, java.util.function.Consumer<JsonObject> m) {
        Asset a = asset();
        if (a != null) app.project.edit(a, label, m);
    }

    @Override
    public void layout() {
        canvas.setBounds(x, y, Math.max(100, w - INSPECTOR_W), h);
        inspector.setBounds(x + canvas.w, y, Math.min(INSPECTOR_W, w), h);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        layout();
        if (builtRev != app.project.revision) rebuildGraph();
    }

    // ================================================================== model build

    private void rebuildGraph() {
        builtRev = app.project.revision;
        Asset a = asset();
        byId.clear();
        nodeCount = 0;
        if (a == null) { root = null; return; }
        JsonObject biome = a.json;
        GNode r = new GNode();
        r.path = List.of();
        r.json = biome;
        r.virtualRoot = true;
        if (a.type == com.worldgenv2.studio.project.AssetType.ZONE) {
            // A zone: where it is (mask graph, used when the shape is "Graph") and what it adds to the terrain.
            r.type = "Zone";
            r.kind = "zone";
            addSlot(r, "mask", "Shape mask", "density", false);
            addSlot(r, "terrain", "Terrain", "density", false);
        } else if (a.type == com.worldgenv2.studio.project.AssetType.DIMENSION) {
            // A dimension: its world structure (the density that places biomes) is the one thing built as a graph.
            r.type = "Dimension";
            r.kind = "dimension";
            addSlot(r, "worldStructure", "World structure", "worldstructure", false);
            addSlot(r, "riverGraph", "River graph", "graphgenerator", false);
            addSlot(r, "caveGraph", "Cave graph", "graphgenerator", false);
        } else if (a.type == com.worldgenv2.studio.project.AssetType.RULE) {
            // A placement rule: where to look (finder), what must hold (conditions), what to build (actions).
            r.type = "Rule";
            r.kind = "rule";
            addSlot(r, "find", "Find", "finder", false);
            addSlot(r, "conditions", "Conditions", "condition", true);
            addSlot(r, "place", "Place", "action", true);
        } else {
            r.type = "Biome";
            r.kind = "biome";
            boolean custom = "CustomBiome".equalsIgnoreCase(J.str(biome, "type", ""));
            addSlot(r, "terrain", "Terrain", "density", false);
            addSlot(r, "materialProvider", "Materials", "material", false);
            addSlot(r, "props", "Props", "prop", true);
            if (custom || biome.has("placementNoise")) addSlot(r, "placementNoise", "Placement", "density", false);
        }
        populate(r, 0);
        root = r;
        finishLayout();
        if (!byId.containsKey(GraphOps.id(selected))) selected = List.of();
        if (needFit) { fit(); needFit = false; }
    }

    private void addSlot(GNode n, String key, String label, String kind, boolean multi) {
        GSlot s = new GSlot();
        s.key = key;
        s.label = label;
        s.kind = kind;
        s.multi = multi;
        n.slots.add(s);
    }

    /** Fills slots/children/params of a node from its JSON (schema-assisted, but generic for unknown nodes). */
    private void populate(GNode n, int depth) {
        n.depth = depth;
        byId.put(GraphOps.id(n.path), n);
        nodeCount++;
        JsonObject o = n.json;

        if (!n.virtualRoot) {
            normalizeKeys(o, n.kind);
            n.type = J.str(o, "type", "?");
            n.schema = NodeSchemas.find(n.kind, n.type);
            if (n.schema != null) normalizeKeys(o, n.schema);
            Set<String> declared = new HashSet<>();
            if (n.schema != null) {
                for (NodeSchemas.Slot s : n.schema.slots()) {
                    addSlot(n, s.key(), s.key(), s.kind(), s.multi());
                    declared.add(s.key());
                    if (s.alias() != null) declared.add(s.alias());
                }
            }
            // JSON keys the schema does not know about but that hold nodes still become sockets
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (declared.contains(e.getKey()) || e.getKey().equals("type")) continue;
                JsonElement v = e.getValue();
                if (v.isJsonObject() && v.getAsJsonObject().has("type")) addSlot(n, e.getKey(), e.getKey(), "any", false);
                else if (v.isJsonArray() && !v.getAsJsonArray().isEmpty() && v.getAsJsonArray().get(0).isJsonObject()
                        && v.getAsJsonArray().get(0).getAsJsonObject().has("type")) addSlot(n, e.getKey(), e.getKey(), "any", true);
            }
            // params: schema params first (explicit value or default), then unknown primitives
            Set<String> shown = new HashSet<>();
            Set<String> shownLower = new HashSet<>();
            if (n.schema != null) {
                for (NodeSchemas.Param p : n.schema.params()) {
                    if (!shownLower.add(p.key().toLowerCase(Locale.ROOT))) continue;   // the engine accepts both spellings; show one
                    String key = o.has(p.key()) ? p.key() : (p.alias() != null && o.has(p.alias()) ? p.alias() : p.key());
                    shown.add(key);
                    if (!o.has(key) && (p.def() == null || p.def().isJsonNull())) continue;
                    JsonElement v = o.has(key) ? o.get(key) : p.def();
                    n.params.add(new String[]{key, v.isJsonPrimitive() ? v.getAsString() : joinStrings(v), o.has(key) ? "1" : "0"});
                }
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getKey().equals("type") || shown.contains(e.getKey()) || shownLower.contains(e.getKey().toLowerCase(Locale.ROOT))) continue;
                if (e.getValue().isJsonPrimitive()) n.params.add(new String[]{e.getKey(), e.getValue().getAsString(), "1"});
                else if (isStringList(e.getValue())) n.params.add(new String[]{e.getKey(), joinStrings(e.getValue()), "1"});
            }
        }

        // children
        for (GSlot s : n.slots) {
            JsonElement v = o.get(s.key);
            if (v == null && n.schema != null) {
                NodeSchemas.Slot sc = n.schema.slot(s.key);
                if (sc != null && sc.alias() != null) v = o.get(sc.alias());
            }
            if (v == null || v.isJsonNull()) continue;
            if (v.isJsonObject()) {
                s.children.add(makeChild(n, s, GraphOps.child(n.path, s.key), v.getAsJsonObject(), depth + 1));
            } else if (v.isJsonArray()) {
                s.multi = true;
                JsonArray arr = v.getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i).isJsonObject()) {
                        s.children.add(makeChild(n, s, GraphOps.child(GraphOps.child(n.path, s.key), i), arr.get(i).getAsJsonObject(), depth + 1));
                    }
                }
            } else if (v.isJsonPrimitive()) {
                s.inline = v.getAsString();
            }
        }
    }

    /** Hytale's files write "Type" and PascalCase keys; the editor addresses keys exactly, so fold them to the schema's spelling. */
    private static void normalizeKeys(JsonObject o, String kind) {
        for (String k : new ArrayList<>(o.keySet())) {
            if (!k.equals("type") && k.equalsIgnoreCase("type") && !o.has("type")) { o.add("type", o.remove(k)); }
        }
        if (!o.has("type") && o.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("propdistribution"))) o.addProperty("type", "Runtime");
    }

    private static void normalizeKeys(JsonObject o, NodeSchemas.NodeType schema) {
        List<String> want = new ArrayList<>();
        for (NodeSchemas.Param p : schema.params()) want.add(p.key());
        for (NodeSchemas.Slot s : schema.slots()) want.add(s.key());
        for (String w : want) {
            if (o.has(w)) continue;
            for (String k : new ArrayList<>(o.keySet())) if (k.equalsIgnoreCase(w)) { o.add(w, o.remove(k)); break; }
        }
    }

    private GNode makeChild(GNode parent, GSlot slot, List<Object> path, JsonObject json, int depth) {
        normalizeKeys(json, slot.kind);
        GNode c = new GNode();
        c.path = path;
        c.json = json;
        c.kind = slot.kind.equals("any") ? guessKind(J.str(json, "type", "")) : slot.kind;
        c.parent = parent;
        c.inSlot = slot;
        populate(c, depth);
        return c;
    }

    private static boolean isStringList(JsonElement v) {
        if (!v.isJsonArray()) return false;
        for (JsonElement x : v.getAsJsonArray()) if (!x.isJsonPrimitive()) return false;
        return true;
    }

    private static String joinStrings(JsonElement v) {
        if (!isStringList(v)) return "...";
        StringBuilder sb = new StringBuilder();
        for (JsonElement x : v.getAsJsonArray()) { if (sb.length() > 0) sb.append(", "); sb.append(x.getAsString()); }
        return sb.toString();
    }

    private static String guessKind(String type) {
        for (String k : NodeSchemas.KINDS) if (NodeSchemas.get(k, type) != null) return k;
        return "density";
    }

    private int nextY;

    private void finishLayout() {
        for (GNode n : byId.values()) {
            n.extraParams = Math.max(0, n.params.size() - MAX_PARAMS);
            int rows = Math.min(MAX_PARAMS, n.params.size()) + (n.extraParams > 0 ? 1 : 0);
            int slotRows = 0;
            for (GSlot s : n.slots) {
                s.rowOffset = HEAD_H + 2 + slotRows * SLOT_H;
                slotRows += 1;
            }
            n.h = HEAD_H + 2 + slotRows * SLOT_H + (rows > 0 ? 3 + rows * PARAM_H : 0) + 4;
        }
        nextY = 0;
        place(root);
    }

    private void place(GNode n) {
        n.x = -n.depth * (NODE_W + GAP_X);
        List<GNode> kids = new ArrayList<>();
        for (GSlot s : n.slots) kids.addAll(s.children);
        if (kids.isEmpty()) {
            n.y = nextY;
        } else {
            for (GNode c : kids) place(c);
            GNode first = kids.get(0), last = kids.get(kids.size() - 1);
            n.y = (first.y + last.y + last.h) / 2 - n.h / 2;
            n.y = Math.max(n.y, first.y);
        }
        nextY = Math.max(nextY, n.y + n.h + GAP_Y);
    }

    private void fit() {
        if (root == null) return;
        int minX = 0, minY = Integer.MAX_VALUE, maxX = NODE_W, maxY = 0;
        for (GNode n : byId.values()) {
            minX = Math.min(minX, n.x);
            minY = Math.min(minY, n.y);
            maxY = Math.max(maxY, n.y + n.h);
        }
        double gw = maxX - minX + 40, gh = maxY - minY + 40;
        zoom = Math.max(0.5, Math.min(1.0, Math.min((canvas.w - 20) / gw, (canvas.h - 20) / gh)));
        panX = (canvas.w - (maxX + minX) * zoom) / 2;
        panY = (canvas.h - (maxY + minY) * zoom) / 2;
    }

    // ================================================================== canvas

    private final class Canvas extends Widget {
        private boolean panning;
        private GNode hoverNode;

        @Override
        protected void render(Gfx g, int mx, int my) {
            g.fill(x, y, w, h, 0xFF0A0C11);
            drawGrid(g);
            if (root == null) return;
            g.clip(x, y, w, h);
            g.g.pose().pushMatrix();
            g.g.pose().translate((float) (x + panX), (float) (y + panY));
            g.g.pose().scale((float) zoom, (float) zoom);
            double gx = (mx - x - panX) / zoom, gy = (my - y - panY) / zoom;
            hoverNode = hitNode(gx, gy);
            for (GNode n : byId.values()) for (GSlot s : n.slots) for (GNode c : s.children) drawWire(g, c, n, s);
            for (GNode n : byId.values()) drawNode(g, n, gx, gy);
            g.g.pose().popMatrix();
            g.unclip();
            g.textMid(nodeCount + " nodes   wheel zoom   drag to pan   right-click for actions", x + 8, y + h - 14, 12, Ui.TEXT_FAINT);
        }

        private void drawGrid(Gfx g) {
            int step = (int) Math.max(12, 24 * zoom);
            int ox = (int) ((panX % step + step) % step), oy = (int) ((panY % step + step) % step);
            for (int gx = ox; gx < w; gx += step) g.fill(x + gx, y, 1, h, 0xFF0E1117);
            for (int gy = oy; gy < h; gy += step) g.fill(x, y + gy, w, 1, 0xFF0E1117);
        }

        private void drawWire(Gfx g, GNode child, GNode parent, GSlot slot) {
            double x0 = child.x + child.w, y0 = child.y + HEAD_H / 2.0;
            double x1 = parent.x, y1 = parent.y + slot.rowOffset + SLOT_H / 2.0;
            int color = NodeSchemas.kindColor(child.kind);
            boolean sel = GraphOps.id(child.path).equals(GraphOps.id(selected));
            double dx = Math.max(24, (x1 - x0) * 0.5);
            double px = x0, py = y0;
            for (int i = 1; i <= 24; i++) {
                double t = i / 24.0, u = 1 - t;
                double bx = u * u * u * x0 + 3 * u * u * t * (x0 + dx) + 3 * u * t * t * (x1 - dx) + t * t * t * x1;
                double by = u * u * u * y0 + 3 * u * u * t * y0 + 3 * u * t * t * y1 + t * t * t * y1;
                segment(g, px, py, bx, by, sel ? 0xFFFFFFFF : Gfx.alpha(color, 0xD0));
                px = bx;
                py = by;
            }
        }

        private void segment(Gfx g, double x0, double y0, double x1, double y1, int color) {
            int n = (int) Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
            for (int i = 0; i <= n; i++) {
                double t = (double) i / n;
                g.fill((int) (x0 + (x1 - x0) * t), (int) (y0 + (y1 - y0) * t), 2, 2, color);
            }
        }

        private void drawNode(Gfx g, GNode n, double gx, double gy) {
            boolean sel = GraphOps.id(n.path).equals(GraphOps.id(selected));
            boolean hov = n == hoverNode;
            int color = n.virtualRoot ? Ui.ACCENT : NodeSchemas.kindColor(n.kind);
            g.fill(n.x + 2, n.y + 2, n.w, n.h, 0x55000000);
            g.fill(n.x, n.y, n.w, n.h, hov ? 0xFF1B2030 : 0xFF151925);
            g.border(n.x, n.y, n.w, n.h, sel ? 0xFFFFFFFF : (hov ? 0xFF4A5270 : 0xFF2A3042));
            g.fill(n.x + 1, n.y + 1, n.w - 2, HEAD_H - 1, Gfx.mix(0xFF151925, color, 0.55f));
            String title = n.virtualRoot ? n.type + "  " + J.str(n.json, "name", "") : n.type;
            g.textFit(title, n.x + 6, n.y, HEAD_H + 1, n.w - 12, 0xFFFFFFFF);
            if (!n.virtualRoot && n.schema == null) g.textRight("?", n.x + n.w - 5, n.y, HEAD_H + 1, Ui.YELLOW);

            for (GSlot s : n.slots) {
                int ry = n.y + s.rowOffset;
                int sc = NodeSchemas.kindColor(s.kind);
                boolean filled = !s.children.isEmpty() || s.inline != null;
                g.fill(n.x - 3, ry + SLOT_H / 2 - 3, 7, 7, 0xFF000000);
                g.fill(n.x - 2, ry + SLOT_H / 2 - 2, 5, 5, filled ? sc : 0xFF1A1E2A);
                if (!filled) g.border(n.x - 2, ry + SLOT_H / 2 - 2, 5, 5, sc);
                String label = s.label + (s.multi ? "  [" + s.children.size() + "]" : "");
                g.textMid(label, n.x + 8, ry, SLOT_H, filled ? Ui.TEXT_DIM : Ui.TEXT_FAINT);
                if (s.inline != null) g.textRight(g.fit(s.inline, 80), n.x + n.w - 6, ry, SLOT_H, 0xFF8FD694);
                else if (s.children.isEmpty()) g.textRight("+ add", n.x + n.w - 6, ry, SLOT_H, Ui.TEXT_FAINT);
            }
            if (!n.params.isEmpty()) {
                int py = n.y + HEAD_H + 2 + n.slots.size() * SLOT_H + 3;
                g.hline(n.x + 4, py - 2, n.w - 8, 0xFF232A3C);
                int shown = Math.min(MAX_PARAMS, n.params.size());
                for (int i = 0; i < shown; i++) {
                    String[] p = n.params.get(i);
                    boolean explicit = p[2].equals("1");
                    g.textMid(p[0], n.x + 6, py + i * PARAM_H, PARAM_H, explicit ? Ui.TEXT_DIM : Ui.TEXT_FAINT);
                    BlockCatalog.Entry be = BlockCatalog.of(p[1]);
                    if (be != null) {      // a block: show its icon and name
                        g.item(be.stack(), n.x + n.w - 14, py + i * PARAM_H, 10);
                        g.textRight(g.fit(be.name(), 70), n.x + n.w - 17, py + i * PARAM_H, PARAM_H, explicit ? 0xFFE8A96B : 0xFF6A5A48);
                    } else {
                        g.textRight(g.fit(p[1], 70), n.x + n.w - 6, py + i * PARAM_H, PARAM_H, explicit ? 0xFFE8A96B : 0xFF6A5A48);
                    }
                }
                if (n.extraParams > 0) g.textMid("+ " + n.extraParams + " more", n.x + 6, py + shown * PARAM_H, PARAM_H, Ui.TEXT_FAINT);
            }
            // output socket
            if (!n.virtualRoot) {
                g.fill(n.x + n.w - 3, n.y + HEAD_H / 2 - 3, 7, 7, 0xFF000000);
                g.fill(n.x + n.w - 2, n.y + HEAD_H / 2 - 2, 5, 5, color);
            }
        }

        private GNode hitNode(double gx, double gy) {
            List<GNode> list = new ArrayList<>(byId.values());
            for (int i = list.size() - 1; i >= 0; i--) {
                GNode n = list.get(i);
                if (gx >= n.x - 6 && gx <= n.x + n.w + 6 && gy >= n.y && gy <= n.y + n.h) return n;
            }
            return null;
        }

        /** Which input socket row is under the point, or null. */
        private GSlot hitSlot(GNode n, double gx, double gy) {
            if (gx > n.x + 12 && gx < n.x + n.w - 60 + 60) { /* whole row counts, so labels are click targets too */ }
            for (GSlot s : n.slots) {
                double ry = n.y + s.rowOffset;
                if (gy >= ry && gy < ry + SLOT_H && gx >= n.x - 8 && gx <= n.x + n.w) return s;
            }
            return null;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button, boolean dbl) {
            double gx = (mx - x - panX) / zoom, gy = (my - y - panY) / zoom;
            GNode n = hitNode(gx, gy);
            if (n == null) {
                if (button == 1) { quickAdd(selectedNode() != null ? selectedNode() : GraphEditor.this.root, (int) mx, (int) my); return true; }
                if (button == 0 && dbl) { quickAdd(selectedNode() != null ? selectedNode() : GraphEditor.this.root, (int) mx, (int) my); return true; }
                if (button == 0 || button == 2) { panning = true; return true; }
                return false;
            }
            GSlot slot = hitSlot(n, gx, gy);
            if (button == 1) { selected = n.path; contextMenu(n, slot, (int) mx, (int) my); return true; }
            if (button != 0) return false;
            if (slot != null && (slot.children.isEmpty() || (slot.multi && gx < n.x + 14)) && slot.inline == null) {
                selected = n.path;
                openPalette(n, slot, (int) mx, (int) my);
                return true;
            }
            selected = n.path;
            inspector.invalidate();
            if (dbl) quickAdd(n, (int) mx, (int) my);
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (panning) { panX += dx; panY += dy; return true; }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            panning = false;
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double dx, double dy) {
            double old = zoom;
            zoom = Math.max(0.4, Math.min(2.0, zoom * (dy > 0 ? 1.12 : 1 / 1.12)));
            // keep the point under the cursor fixed
            double cx = mx - x, cy = my - y;
            panX = cx - (cx - panX) * zoom / old;
            panY = cy - (cy - panY) * zoom / old;
            return true;
        }

        @Override
        public boolean keyPressed(int key, int mods) {
            if (key == Ui.KEY_DELETE || key == Ui.KEY_BACKSPACE) { deleteSelected(); return true; }
            if (key == 70 && (mods & Ui.MOD_CTRL) == 0) { fit(); return true; } // F
            return false;
        }
    }

    // ================================================================== actions

    private GNode selectedNode() { return byId.get(GraphOps.id(selected)); }

    private void openPalette(GNode n, GSlot slot, int mx, int my) {
        if (root == null || canvas.uiRoot() == null) return;
        List<NodeSchemas.NodeType> cand = new ArrayList<>();
        if (slot.kind.equals("any")) for (String k : NodeSchemas.KINDS) cand.addAll(NodeSchemas.ofKind(k));
        else cand.addAll(NodeSchemas.ofKind(slot.kind));
        NodePalette.open(canvas.uiRoot(), mx, my, "Add " + slot.kind + " node to " + slot.label, cand, t -> {
            List<Object> parentPath = n.path;
            edit("Add node", j -> {
                List<Object> created = GraphOps.attach(j, parentPath, slot.key, slot.multi, NodeSchemas.newNode(t));
                selected = created;
            });
        });
    }

    private void deleteSelected() {
        GNode n = selectedNode();
        if (n == null || n.virtualRoot) return;
        List<Object> path = n.path;
        edit("Delete node", j -> selected = GraphOps.remove(j, path));
    }

    private void contextMenu(GNode n, GSlot slot, int mx, int my) {
        if (canvas.uiRoot() == null) return;
        List<PopupMenu.Item> items = new ArrayList<>();
        if (slot != null) items.add(PopupMenu.Item.of("Add node to '" + slot.label + "'...", () -> openPalette(n, slot, mx, my)));
        for (GSlot s : n.slots) {
            if (s == slot || (s.children.size() > 0 && !s.multi)) continue;
            items.add(PopupMenu.Item.of("Add to '" + s.label + "'...", () -> openPalette(n, s, mx, my)));
        }
        if (!n.virtualRoot) {
            items.add(PopupMenu.Item.sep());
            items.add(PopupMenu.Item.of("Wrap in...", () -> wrapPalette(n, mx, my)));
            items.add(PopupMenu.Item.of("Replace with...", () -> replacePalette(n, mx, my)));
            if (n.path.get(n.path.size() - 1) instanceof Integer) {
                items.add(PopupMenu.Item.of("Duplicate", () -> { List<Object> p = n.path; edit("Duplicate node", j -> GraphOps.duplicate(j, p)); }));
            }
            items.add(PopupMenu.Item.of("Copy subtree", () -> copy(n)));
            items.add(PopupMenu.Item.danger("Delete subtree", this::deleteSelected));
        }
        if (clipboardLooksLikeNode()) {
            GSlot target = slot != null ? slot : firstFreeSlot(n);
            if (target != null) items.add(PopupMenu.Item.of("Paste into '" + target.label + "'", () -> paste(n, target)));
        }
        PopupMenu.open(canvas.uiRoot(), mx, my, 150, items);
    }

    /** Double-click / right-click on empty canvas: one searchable list of everything that fits any open slot of the node. */
    private void quickAdd(GNode n, int mx, int my) {
        if (n == null || canvas.uiRoot() == null) return;
        List<GSlot> open = new ArrayList<>();
        for (GSlot s : n.slots) if (s.inline == null && (s.multi || s.children.isEmpty())) open.add(s);
        if (open.isEmpty()) { app.say("'" + n.type + "' has no free slot. Right-click a node for more options.", Ui.TEXT_DIM); return; }
        java.util.LinkedHashSet<NodeSchemas.NodeType> cand = new java.util.LinkedHashSet<>();
        for (GSlot s : open) {
            if (s.kind.equals("any")) for (String k : NodeSchemas.KINDS) cand.addAll(NodeSchemas.ofKind(k));
            else cand.addAll(NodeSchemas.ofKind(s.kind));
        }
        NodePalette.open(canvas.uiRoot(), mx, my, "Add to '" + n.type + "'  (type to search)", new ArrayList<>(cand), t -> {
            GSlot target = null;
            for (GSlot s : open) if (s.kind.equals(t.kind()) || s.kind.equals("any")) { target = s; break; }
            if (target == null) return;
            List<Object> parentPath = n.path;
            String key = target.key;
            boolean multi = target.multi;
            edit("Add node", j -> { selected = GraphOps.attach(j, parentPath, key, multi, NodeSchemas.newNode(t)); });
        });
    }

    private GSlot firstFreeSlot(GNode n) {
        for (GSlot s : n.slots) if (s.multi || s.children.isEmpty()) return s;
        return null;
    }

    private void wrapPalette(GNode n, int mx, int my) {
        List<NodeSchemas.NodeType> cand = new ArrayList<>();
        for (NodeSchemas.NodeType t : NodeSchemas.ofKind(n.kind)) {
            for (NodeSchemas.Slot s : t.slots()) if (s.kind().equals(n.kind)) { cand.add(t); break; }
        }
        NodePalette.open(canvas.uiRoot(), mx, my, "Wrap '" + n.type + "' in", cand, t -> {
            List<Object> p = n.path;
            edit("Wrap node", j -> GraphOps.wrap(j, p, n.kind, t));
        });
    }

    private void replacePalette(GNode n, int mx, int my) {
        NodePalette.open(canvas.uiRoot(), mx, my, "Replace '" + n.type + "' with", NodeSchemas.ofKind(n.kind), t -> {
            List<Object> p = n.path;
            edit("Replace node", j -> GraphOps.replace(j, p, NodeSchemas.newNode(t)));
        });
    }

    private void copy(GNode n) {
        Minecraft.getInstance().keyboardHandler.setClipboard(n.json.toString());
        app.say("Copied '" + n.type + "' subtree to clipboard");
    }

    private boolean clipboardLooksLikeNode() {
        try {
            String c = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (c == null || !c.trim().startsWith("{")) return false;
            JsonElement e = JsonParser.parseString(c);
            return e.isJsonObject() && e.getAsJsonObject().has("type");
        } catch (Exception e) {
            return false;
        }
    }

    private void paste(GNode n, GSlot slot) {
        try {
            JsonObject pasted = JsonParser.parseString(Minecraft.getInstance().keyboardHandler.getClipboard()).getAsJsonObject();
            List<Object> p = n.path;
            edit("Paste node", j -> selected = GraphOps.attach(j, p, slot.key, slot.multi, pasted));
        } catch (Exception ignored) {
            app.say("Clipboard does not contain a node", Ui.RED);
        }
    }

    // ================================================================== inspector

    private final class Inspector extends Widget {
        private final Form form = add(new Form());
        private String builtFor = "";
        private long builtRevision = -1;

        void invalidate() { builtFor = ""; }

        @Override
        public void layout() {
            form.setBounds(x + 1, y + 22, w - 1, Math.max(0, h - 22));
        }

        @Override
        protected void render(Gfx g, int mx, int my) {
            g.fill(x, y, w, h, Ui.BG_PANEL);
            g.vline(x, y, h, Ui.LINE_STRONG);
            GNode n = selectedNode();
            String sel = GraphOps.id(selected);
            if (!sel.equals(builtFor) || builtRevision != app.project.revision && structureChanged(n)) {
                rebuild(n);
                builtFor = sel;
            }
            builtRevision = app.project.revision;
            g.textMid("INSPECTOR", x + 8, y + 3, 16, Ui.TEXT_FAINT);
            layout();
        }

        private String lastShape = "";

        private boolean structureChanged(GNode n) {
            String shape = n == null ? "" : n.type + n.slots.size() + n.params.size() + n.json.keySet();
            boolean ch = !shape.equals(lastShape);
            lastShape = shape;
            return ch;
        }

        private void rebuild(GNode n) {
            form.clear();
            lastShape = n == null ? "" : n.type + n.slots.size() + n.params.size() + n.json.keySet();
            if (n == null) {
                form.note("Select a node to edit it.", Ui.TEXT_FAINT);
                form.note("Right-click a node for add / wrap / delete.", Ui.TEXT_FAINT);
                return;
            }
            form.labelW = 84;
            final List<Object> path = n.path;
            if (n.virtualRoot) {
                if (n.kind.equals("rule")) {
                    form.section("Placement rule");
                    form.note("Find: how a spot is found (a gap over water, a point).", Ui.TEXT_DIM);
                    form.note("Conditions: what must hold there (biome, density...).", Ui.TEXT_DIM);
                    form.note("Place: what to build. Later actions can refer to earlier", Ui.TEXT_DIM);
                    form.note("ones by name, e.g. at = log.socket.root.", Ui.TEXT_DIM);
                    form.note("Cell size and chance are in the Form view.", Ui.TEXT_FAINT);
                } else if (n.kind.equals("dimension")) {
                    form.section("Dimension");
                    form.note("Add a World structure to place biomes with one", Ui.TEXT_DIM);
                    form.note("density field and biome delimiters (like Hytale).", Ui.TEXT_DIM);
                    form.note("Without one, biomes come from the layout.", Ui.TEXT_FAINT);
                } else if (n.kind.equals("zone")) {
                    form.section("Zone output");
                    form.note("Shape mask: where the zone is. Its value is read", Ui.TEXT_DIM);
                    form.note("on the ground plane; the zone applies where the", Ui.TEXT_DIM);
                    form.note("value is between Min and Max (set in Form view).", Ui.TEXT_DIM);
                    form.note("Terrain: extra ground shape added inside the zone.", Ui.TEXT_DIM);
                    form.note("The shape must be set to Graph for the mask to apply.", Ui.TEXT_FAINT);
                } else {
                    form.section("Biome output");
                    form.note("Wire graphs into its sockets: terrain shapes the", Ui.TEXT_DIM);
                    form.note("ground, materials paint it, props decorate it.", Ui.TEXT_DIM);
                    form.note("Name, weight and climate are in the Form view.", Ui.TEXT_FAINT);
                }
                return;
            }
            form.section(n.type, n.kind);
            Dropdown type = new Dropdown();
            List<String> names = new ArrayList<>();
            for (NodeSchemas.NodeType t : NodeSchemas.ofKind(n.kind)) names.add(t.name());
            if (!names.contains(n.type)) names.add(0, n.type);
            type.options(names).selected(n.type);
            type.onSelect = v -> edit("Change node type", j -> GraphOps.setType(j, path, v));
            form.field("Type", type, null);

            if (n.schema == null) form.note("Not in the engine schema - editing raw keys only.", Ui.YELLOW);

            if (!n.params.isEmpty()) form.section("Parameters");
            Set<String> done = new HashSet<>();
            for (String[] p : n.params) {
                if (!done.add(p[0])) continue;
                NodeSchemas.Param sp = null;
                if (n.schema != null) for (NodeSchemas.Param q : n.schema.params()) if (q.key().equals(p[0]) || p[0].equals(q.alias())) sp = q;
                String pdoc = NodeSchemas.doc(n.kind, n.type, p[0]);
                form.field(p[0], paramEditor(path, p[0], p[1], sp), pdoc != null ? pdoc : sp != null && sp.def() != null && !sp.def().isJsonNull() ? "default " + sp.def().getAsString() : null);
            }

            if (!n.slots.isEmpty()) form.section("Inputs");
            for (GSlot s : n.slots) {
                Button add = new Button("+ " + s.label + (s.multi ? " (" + s.children.size() + ")" : ""), () -> {
                    GNode node = byId.get(GraphOps.id(path));
                    if (node != null) openPalette(node, s, inspector.x - 200, inspector.y + 60);
                });
                add.enabled = s.multi || s.children.isEmpty();
                form.block(add);
                for (GNode c : s.children) {
                    Button go = new Button(c.type, () -> { selected = c.path; invalidate(); });
                    go.dot = NodeSchemas.kindColor(c.kind);
                    form.block(go);
                }
            }
            Button del = new Button("Delete this node", this::deleteFromInspector).style(Button.Style.DANGER);
            form.block(del);
        }

        private void deleteFromInspector() { deleteSelected(); }

        private Widget paramEditor(List<Object> path, String key, String current, NodeSchemas.Param sp) {
            String t = sp != null ? sp.type() : "string";
            JsonObject now = nodeNow(path);
            JsonElement cur = now == null ? null : now.get(key);
            if (sp == null && cur != null && cur.isJsonPrimitive()) {
                if (cur.getAsJsonPrimitive().isBoolean()) t = "bool";
                else if (cur.getAsJsonPrimitive().isNumber()) t = "number";
            }
            switch (t) {
                case "bool" -> {
                    Toggle tg = new Toggle(Boolean.parseBoolean(current), v -> edit("Node " + key, j -> setPrim(j, path, key, v)));
                    return tg;
                }
                case "number", "int" -> {
                    boolean integer = t.equals("int");
                    TextField f = new TextField().filter(integer ? TextField.Filter.INT : TextField.Filter.DECIMAL).range(-1e9, 1e9, integer ? 1 : stepFor(current));
                    f.setText(current);
                    f.onChange = s -> {
                        double v;
                        try { v = Double.parseDouble(s.trim()); } catch (NumberFormatException e) { f.invalid(true); return; }
                        f.invalid(false);
                        edit("Node " + key, j -> setNum(j, path, key, integer ? Math.rint(v) : v));
                    };
                    return f;
                }
                case "json" -> {
                    // vectors, bounds, ranges and lists of entries: edited as JSON text
                    TextField f = new TextField();
                    f.setText(cur == null ? "" : cur.toString());
                    f.placeholder("{ ... } or [ ... ]");
                    f.onChange = s -> {
                        try {
                            JsonElement parsed = s.isBlank() ? null : com.google.gson.JsonParser.parseString(s);
                            f.invalid(false);
                            edit("Node " + key, j -> { JsonObject n = GraphOps.nodeAt(j, path); if (n != null) { if (parsed == null) n.remove(key); else n.add(key, parsed); } });
                        } catch (RuntimeException e) { f.invalid(true); }
                    };
                    return f;
                }
                case "strings" -> {
                    TextField f = new TextField();
                    f.setText(current);
                    f.placeholder("comma, separated, ids");
                    f.onChange = s -> edit("Node " + key, j -> setStrings(j, path, key, s));
                    return f;
                }
                default -> {
                    List<String> opts = sp == null ? List.of() : sp.enumOptions();
                    if (!opts.isEmpty()) {
                        Dropdown d = new Dropdown();
                        d.options(opts).selected(current);
                        d.onSelect = v -> edit("Node " + key, j -> setPrim(j, path, key, v));
                        return d;
                    }
                    TextField f = new TextField();
                    f.setText(current);
                    f.onChange = s -> edit("Node " + key, j -> setPrim(j, path, key, s));
                    String lk = key.toLowerCase();
                    if (lk.equals("block") || lk.equals("blockid") || lk.equals("material") || lk.equals("solid") || lk.equals("fluid")
                            || lk.endsWith("block") || BlockCatalog.of(current) != null) {
                        return new BlockField(f, id -> edit("Node " + key, j -> setPrim(j, path, key, id)));
                    }
                    return f;
                }
            }
        }

        private double stepFor(String cur) {
            try { double v = Math.abs(Double.parseDouble(cur)); return v == 0 ? 0.1 : Math.max(0.0005, Math.pow(10, Math.floor(Math.log10(v)) - 1)); }
            catch (NumberFormatException e) { return 0.1; }
        }

        private JsonObject nodeNow(List<Object> path) {
            Asset a = asset();
            return a == null ? null : GraphOps.nodeAt(a.json, path);
        }

        private void setPrim(JsonObject root, List<Object> path, String key, Object v) {
            JsonObject n = GraphOps.nodeAt(root, path);
            if (n == null) return;
            if (v instanceof Boolean b) n.addProperty(key, b);
            else n.addProperty(key, String.valueOf(v));
        }

        private void setStrings(JsonObject root, List<Object> path, String key, String text) {
            JsonObject n = GraphOps.nodeAt(root, path);
            if (n == null) return;
            JsonArray arr = new JsonArray();
            for (String s : text.split(",")) if (!s.isBlank()) arr.add(s.trim());
            n.add(key, arr);
        }

        private void setNum(JsonObject root, List<Object> path, String key, double v) {
            JsonObject n = GraphOps.nodeAt(root, path);
            if (n != null) J.setNum(n, key, v);
        }
    }
}
