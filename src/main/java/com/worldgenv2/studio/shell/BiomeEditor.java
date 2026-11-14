package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.project.Templates;
import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Biome: identity, selection weight, climate position, and a readable outline of the terrain graph. */
final class BiomeEditor extends Editor {

    BiomeEditor(StudioApp app, Asset a) { super(app, a); }

    @Override
    protected void build() {
        section("Identity");
        field("Name", str("name", "", "Biome name"), "Namespaced id, e.g. worldgenv2:alpine_peaks");
        field("Category", choice("category", "plains", Templates.BIOME_CATEGORIES, s -> s, "Biome category"),
                "Used for grouping, colours, and spawn rules. 'cave' biomes become 3D underground volumes.");

        field("Minecraft biome", minecraftBiomeField(), "The vanilla or modded biome used for sky, water and foliage colour, mob spawns and music. Type any biome id.");

        if ("cave".equals(getStr("category", "plains"))) {
            section("Underground Volume", "3D cave floor & ceiling decoration");
            field("Min Y", num("underground.minY", -64, -2048, 2048, 4, true, "Underground minY"), "Lowest Y for this cave biome.");
            field("Max Y", num("underground.maxY", 48, -2048, 2048, 4, true, "Underground maxY"), "Highest Y for this cave biome.");
            field("Floor block", str("underground.floorBlock", "minecraft:moss_block", "Floor block"), "Block replacing exposed cave floors.");
            field("Floor chance", num("underground.floorChance", 0.35, 0, 1, 0.05, false, "Floor chance"), "Probability (0..1) on cave floors.");
            field("Ceiling block", str("underground.ceilingBlock", "minecraft:amethyst_block", "Ceiling block"), "Block replacing exposed cave ceilings.");
            field("Ceiling chance", num("underground.ceilingChance", 0.25, 0, 1, 0.05, false, "Ceiling chance"), "Probability (0..1) on cave ceilings.");
        }

        section("Distribution", "how often and how large this biome appears");
        field("Weight", num("weight", 10, 0.01, 1000, 1, false, "Biome weight"), "Relative chance versus other biomes in the pool.");
        field("Size modifier", num("sizeModifier", 1, 0.1, 10, 0.1, false, "Biome size"), "1 = normal cell size. Bigger values make larger regions.");
        field("Transition (blocks)", num("defaultTransitionDistance", 48, 0, 1024, 8, false, "Biome transition"),
                "Distance over which this biome blends into neighbours.");

        section("Climate", "position in climate space, -1 to 1");
        field("Continentalness", num("climate.continentalness", 0, -1, 1, 0.05, false, "Climate"), "-1 deep ocean ... 1 inland peaks.");
        field("Temperature", num("climate.temperature", 0, -1, 1, 0.05, false, "Climate"), "-1 frozen ... 1 scorching.");
        field("Humidity", num("climate.humidity", 0, -1, 1, 0.05, false, "Climate"), "-1 arid ... 1 wet.");
        block(new ClimatePad());

        section("Terrain graph", "structure of this biome's density function");
        block(new Outline());
        note("Visual node editing arrives with the Graph view; JSON view shows the full tree.", Ui.TEXT_FAINT);
    }

    /** Text field for the Minecraft biome this biome presents as, with a picker of vanilla biomes. */
    private Widget minecraftBiomeField() {
        TextField f = str("minecraftBiome", "", "Minecraft biome");
        f.placeholder = "auto: " + com.worldgenv2.pack.ProjectCompiler.defaultMinecraftBiome(getStr("category", "plains"));
        Button pick = new Button("Pick", () -> {
            if (root == null) return;
            java.util.TreeSet<String> ids = new java.util.TreeSet<>();
            for (com.worldgenv2.minecraft.DefaultBiomeCatalog.Entry e : com.worldgenv2.minecraft.DefaultBiomeCatalog.entries()) {
                if (e.vanilla() != null) ids.add(e.vanilla().identifier().toString());
            }
            List<PopupMenu.Item> items = new ArrayList<>();
            for (String id : ids) items.add(PopupMenu.Item.of(id, () -> edit("Minecraft biome", j -> j.addProperty("minecraftBiome", id))));
            PopupMenu.open(root, f.x, f.y + f.h, 200, items, -1, 16);
        });
        pick.tooltip = "Choose a vanilla biome. Modded biome ids can be typed in directly.";
        return new Pair(f, pick, 36);
    }

    /** 2D plot of temperature (x) against humidity (y) with this biome and its siblings marked. */
    private final class ClimatePad extends Widget {
        ClimatePad() { h = 70; }

        @Override
        protected void render(Gfx g, int mx, int my) {
            int px = x + 130, pw = Math.min(w - 130, 210);
            g.box(px, y, pw, h, Ui.BG_INPUT, Ui.LINE_STRONG);
            g.vline(px + pw / 2, y + 1, h - 2, Ui.LINE);
            g.hline(px + 1, y + h / 2, pw - 2, Ui.LINE);
            for (Asset b : project().assetsOf(com.worldgenv2.studio.project.AssetType.BIOME)) {
                if (b.path.equals(assetPath)) continue;
                dot(g, px, pw, b.json, Gfx.alpha(Cat.color(J.str(b.json, "category", "")), 0x90), 2);
            }
            Asset me = asset();
            if (me != null) {
                int cx = px + (int) ((J.num(J.obj(me.json, "climate"), "temperature", 0) + 1) / 2 * (pw - 1));
                int cy = y + h - 1 - (int) ((J.num(J.obj(me.json, "climate"), "humidity", 0) + 1) / 2 * (h - 1));
                g.fill(cx - 3, cy - 3, 7, 7, Ui.ACCENT);
                g.border(cx - 4, cy - 4, 9, 9, 0xFFFFFFFF);
            }
            g.text("temperature >", px + 3, y + h - 10, Ui.TEXT_FAINT);
            g.text("humidity", x, y + 2, Ui.TEXT_FAINT);
            g.text("this biome", x, y + 14, Ui.ACCENT);
            g.text("other biomes", x, y + 26, Ui.TEXT_FAINT);
        }

        private void dot(Gfx g, int px, int pw, JsonObject biome, int color, int r) {
            JsonObject c = J.obj(biome, "climate");
            int cx = px + (int) ((J.num(c, "temperature", 0) + 1) / 2 * (pw - 1));
            int cy = y + h - 1 - (int) ((J.num(c, "humidity", 0) + 1) / 2 * (h - 1));
            g.fill(cx - r, cy - r, r * 2 + 1, r * 2 + 1, color);
        }
    }

    /** Indented, depth-limited listing of the terrain AST: node type plus a few headline parameters. */
    private final class Outline extends Widget {
        private final List<String> lines = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private long builtRev = -1;

        Outline() { h = 20; }

        private void rebuildLines() {
            lines.clear();
            colors.clear();
            Asset a = asset();
            JsonObject t = a == null ? null : J.obj(a.json, "terrain");
            if (t == null) { lines.add("(no terrain defined)"); colors.add(Ui.TEXT_FAINT); }
            else walk(t, 0, "");
            h = Math.min(lines.size(), 26) * 12 + 8 + (lines.size() > 26 ? 12 : 0);
            builtRev = project().revision;
        }

        private void walk(JsonObject n, int depth, String prefix) {
            if (lines.size() > 60) return;
            String type = J.str(n, "type", "?");
            StringBuilder sb = new StringBuilder(prefix).append(type);
            int params = 0;
            for (Map.Entry<String, JsonElement> e : n.entrySet()) {
                if (e.getKey().equals("type") || !e.getValue().isJsonPrimitive() || params >= 3) continue;
                sb.append("  ").append(e.getKey()).append('=').append(e.getValue().getAsString());
                params++;
            }
            lines.add("  ".repeat(depth) + sb);
            colors.add(depth == 0 ? Ui.TEXT : Ui.TEXT_DIM);
            for (Map.Entry<String, JsonElement> e : n.entrySet()) {
                if (e.getValue().isJsonObject()) walk(e.getValue().getAsJsonObject(), depth + 1, e.getKey() + ": ");
                else if (e.getValue().isJsonArray()) {
                    JsonArray arr = e.getValue().getAsJsonArray();
                    for (JsonElement c : arr) if (c.isJsonObject()) walk(c.getAsJsonObject(), depth + 1, e.getKey() + "[]: ");
                }
            }
        }

        @Override
        protected void render(Gfx g, int mx, int my) {
            if (builtRev != project().revision) rebuildLines();
            g.box(x, y, w, h, Ui.BG_INPUT, Ui.LINE);
            g.clip(x, y, w, h);
            int ty = y + 4;
            for (int i = 0; i < lines.size() && i < 26; i++) {
                g.text(g.fit(lines.get(i), w - 10), x + 5, ty, colors.get(i));
                ty += 12;
            }
            g.unclip();
            if (lines.size() > 26) g.text("... " + (lines.size() - 26) + " more nodes (see JSON view)", x + 5, y + h - 11, Ui.TEXT_FAINT);
        }
    }
}
