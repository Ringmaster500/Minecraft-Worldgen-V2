package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.ui.*;
import com.worldgenv2.worldstructure.ZoneLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A zone: where it is (a shape, or a node graph), what it contains (biomes, optional extra terrain), and how it looks on
 * the map. Use the Graph view to build the shape mask and the terrain from nodes.
 */
final class ZoneEditor extends Editor {

    private String builtKind = "";

    ZoneEditor(StudioApp app, Asset a) { super(app, a); }

    static String kindLabel(String kind) {
        return switch (kind) {
            case "everywhere" -> "Everywhere (fallback)";
            case "patch" -> "Patches (weighted cells)";
            case "radial" -> "Distance band from a point";
            case "noise" -> "Noise range";
            case "graph" -> "Graph (build the shape from nodes)";
            default -> kind;
        };
    }

    @Override
    protected void build() {
        builtKind = getStr("kind", "patch");
        List<String> kinds = new ArrayList<>(List.of("patch", "radial", "noise", "graph", "everywhere"));
        kinds.addAll(ZoneLayout.extraMaskKinds());

        section("Identity");
        field("Label", str("name", "", "Zone label"), "Shown on the map and in lists.");
        field("Map colour", str("color", "", "Zone colour"), "Optional hex like #E3B341; otherwise automatic.");

        section("Where", "the shape of this zone");
        field("Shape", choice("kind", "patch", kinds, ZoneEditor::kindLabel, "Zone shape"),
                "Patches: weighted macro cells. Distance band: a ring around a point. Noise: a range of low-frequency noise. Graph: any node graph you build.");
        switch (builtKind) {
            case "patch" -> field("Weight", num("weight", 1, 0.1, 100, 0.5, false, "Zone weight"), "Relative size of this zone among patch zones in the dimension.");
            case "radial" -> {
                field("Center X", num("centerX", 0, -60000, 60000, 8, false, "Zone center X"), null);
                field("Center Z", num("centerZ", 0, -60000, 60000, 8, false, "Zone center Z"), null);
                field("Inner radius", num("inner", 0, 0, 100000, 16, false, "Zone inner radius"), "The zone begins this far from the center.");
                field("Outer radius", num("outer", 1000, 0, 100000, 16, false, "Zone outer radius"), "The zone ends this far from the center.");
            }
            case "noise" -> {
                field("Frequency", num("frequency", 0.0004, 0.00005, 0.01, 0.0001, false, "Zone frequency"), "Lower = bigger regions.");
                field("Noise min", num("min", -1, -1, 1, 0.05, false, "Zone noise min"), "The zone applies where noise is above this...");
                field("Noise max", num("max", 1, -1, 1, 0.05, false, "Zone noise max"), "...and below this.");
            }
            case "graph" -> {
                field("Min value", num("min", 0, -10, 10, 0.05, false, "Zone min"), "The zone applies where the Shape mask graph is at least this...");
                field("Max value", num("max", 1, -10, 10, 0.05, false, "Zone max"), "...and at most this.");
                field("Edge softness", num("softness", 0.15, 0.001, 5, 0.05, false, "Zone softness"), "How gradually the zone's own terrain fades in near its edge.");
                note("Open the Graph view and wire nodes into 'Shape mask' to define where this zone is.", Ui.ACCENT);
            }
            default -> {}
        }

        section("What is inside", "biomes and terrain used in this zone");
        block(biomePicker());
        if (builtKind.equals("graph")) {
            field("Terrain strength", num("terrainStrength", 1, -5, 5, 0.1, false, "Zone terrain strength"),
                    "Scales the zone's own Terrain graph (Graph view). 0 = off.");
            note("Wire nodes into 'Terrain' in the Graph view to add extra ground shape inside this zone.", Ui.TEXT_FAINT);
        }
    }

    @Override
    protected void onSynced() {
        if (!getStr("kind", "patch").equals(builtKind)) rebuild();
    }

    /** Button that opens a checklist of the project's biomes; shows what is currently selected. */
    private Widget biomePicker() {
        Button b = new Button("", null);
        b.onClick = () -> {
            if (root == null) return;
            Map<String, String> items = new LinkedHashMap<>();
            for (Asset biome : project().assetsOf(AssetType.BIOME)) items.put(biome.id(), biome.displayName());
            Set<String> cur = new LinkedHashSet<>();
            Asset a = asset();
            JsonArray ja = a == null ? null : J.arr(a.json, "biomes");
            if (ja != null) for (JsonElement e : ja) cur.add(e.getAsString());
            Checklist.open(root, b.x, b.y + b.h, Math.max(b.w, 200), items, cur, chosen ->
                    edit("Zone biomes", j -> { JsonArray out = new JsonArray(); for (String s : chosen) out.add(s); j.add("biomes", out); }));
        };
        bind(() -> {
            Asset a = asset();
            JsonArray ja = a == null ? null : J.arr(a.json, "biomes");
            int n = ja == null ? 0 : ja.size();
            StringBuilder sb = new StringBuilder();
            if (ja != null) for (int i = 0; i < ja.size() && i < 4; i++) {
                Asset biome = project().get("biomes/" + ja.get(i).getAsString() + ".json");
                sb.append(i == 0 ? "" : ", ").append(biome == null ? ja.get(i).getAsString() : biome.displayName());
            }
            b.label = n == 0 ? "No biomes chosen (this zone restricts nothing)" : n + (n == 1 ? " biome: " : " biomes: ") + sb + (n > 4 ? ", ..." : "");
        });
        return b;
    }
}
