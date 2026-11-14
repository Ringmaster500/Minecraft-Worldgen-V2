package com.worldgenv2.studio.shell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.project.Templates;
import com.worldgenv2.studio.ui.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Dimension: which generator runs it, its macro layout, and the pool of biomes it may use. */
final class DimensionEditor extends Editor {

    private String builtGenerator = "";

    DimensionEditor(StudioApp app, Asset a) { super(app, a); }

    @Override
    protected void build() {
        builtGenerator = getStr("generator", "worldgenv2");
        section("Identity");
        field("Dimension id", str("id", "", "Dimension id"), "e.g. minecraft:overworld or mymod:skylands");
        Dropdown gen = choice("generator", "worldgenv2", List.of("worldgenv2", "vanilla"),
                s -> s.equals("vanilla") ? "Vanilla (leave untouched)" : "Worldgen V2", "Generator");
        field("Generator", gen, "Vanilla leaves this dimension exactly as Minecraft generates it.");

        if (builtGenerator.equals("vanilla")) {
            note("This dimension uses Minecraft's own generator. Switch to Worldgen V2 to configure it.", Ui.TEXT_FAINT);
            return;
        }

        section("Layout", "macro structure of the dimension");
        field("Layout", choice("layout", "voronoi_continent", Templates.LAYOUTS, Templates::layoutLabel, "Layout"),
                "How landmasses and zones are arranged.");
        TextField seed = num("seed", 1337, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, true, "Seed");
        Button roll = new Button("Roll", () -> edit("Seed", j -> J.setNum(j, "seed", new Random().nextInt(900_000_000))));
        roll.tooltip = "Pick a random seed";
        field("Seed", new Pair(seed, roll, 36), "Seed for this dimension's layout. Biome noise also derives from it.");
        field("Cell size", num("cellSize", 192, 16, 4096, 16, true, "Cell size"), "Average Voronoi cell width in blocks.");

        section("Biome pool", "which biomes this dimension may generate");
        block(new PoolTable());
        note("Zones, rings and landmarks editing arrives in the next milestone.", Ui.TEXT_FAINT);
    }

    @Override
    protected void onSynced() {
        if (!getStr("generator", "worldgenv2").equals(builtGenerator)) rebuild();
    }

    private JsonArray pool() {
        Asset a = asset();
        return a == null ? new JsonArray() : J.arrOrCreate(a.json, "biomes");
    }

    private final class PoolTable extends Widget {
        private static final int ROW = 18;
        private String signature = "";
        private long syncedRev = -1;
        private final List<Widget> rowWidgets = new ArrayList<>();
        private final List<TextField[]> fields = new ArrayList<>();
        private final List<Toggle> toggles = new ArrayList<>();
        private final List<Label> shares = new ArrayList<>();
        private Dropdown addPick;
        private Button addBtn, addAll;
        private int count;

        PoolTable() { h = 60; }

        private String sig() {
            StringBuilder sb = new StringBuilder();
            for (JsonElement e : pool()) sb.append(e.isJsonObject() ? J.str(e.getAsJsonObject(), "biome", "?") : "?").append(',');
            for (Asset b : project().assetsOf(AssetType.BIOME)) sb.append(b.id()).append(';');
            return sb.toString();
        }

        private void rebuildRows() {
            clearChildren();
            rowWidgets.clear(); fields.clear(); toggles.clear(); shares.clear();
            JsonArray arr = pool();
            count = arr.size();
            for (int i = 0; i < count; i++) {
                final int idx = i;
                Toggle t = add(new Toggle(true, v -> poolEdit(idx, "Pool enable", o -> o.addProperty("enabled", v))));
                toggles.add(t);
                TextField wf = add(poolNum(idx, "weight", 0, 1000, 1, "Pool weight"));
                TextField sf = add(poolNum(idx, "size", 0.1, 10, 0.1, "Pool size"));
                TextField tf = add(poolNum(idx, "transition", 0, 1024, 8, "Pool transition"));
                fields.add(new TextField[]{wf, sf, tf});
                shares.add(add(new Label("")));
                Button rm = add(new Button("x", () -> {
                    edit("Remove from pool", j -> J.arrOrCreate(j, "biomes").remove(idx));
                }).style(Button.Style.DANGER));
                rm.tooltip = "Remove from this dimension's pool";
                rowWidgets.add(rm);
            }
            addPick = add(new Dropdown());
            addPick.placeholder = "Add biome...";
            addBtn = add(new Button("Add", this::addSelected).style(Button.Style.PRIMARY));
            addAll = add(new Button("Add all missing", this::addAllMissing));
            refreshAddOptions();
            h = 20 + count * ROW + 30;
            signature = sig();
            syncedRev = -1;
        }

        private TextField poolNum(int idx, String key, double min, double max, double step, String label) {
            TextField f = new TextField().filter(TextField.Filter.DECIMAL).range(min, max, step);
            f.onChange = s -> {
                double v;
                try { v = Double.parseDouble(s.trim()); } catch (NumberFormatException e) { f.invalid(true); return; }
                f.invalid(v < min || v > max);
                poolEdit(idx, label, o -> J.setNum(o, key, v));
            };
            f.onCommit = s -> syncedRev = -1;
            return f;
        }

        private void poolEdit(int idx, String label, java.util.function.Consumer<JsonObject> m) {
            edit(label, j -> {
                JsonArray arr = J.arrOrCreate(j, "biomes");
                if (idx < arr.size() && arr.get(idx).isJsonObject()) m.accept(arr.get(idx).getAsJsonObject());
            });
        }

        private Set<String> inPool() {
            Set<String> s = new HashSet<>();
            for (JsonElement e : pool()) if (e.isJsonObject()) s.add(J.str(e.getAsJsonObject(), "biome", ""));
            return s;
        }

        private void refreshAddOptions() {
            Set<String> have = inPool();
            List<String> opts = new ArrayList<>();
            for (Asset b : project().assetsOf(AssetType.BIOME)) if (!have.contains(b.id())) opts.add(b.id());
            addPick.options(opts);
            addPick.display = id -> {
                Asset b = project().get("biomes/" + id + ".json");
                return b == null ? id : b.displayName();
            };
            if (!opts.contains(addPick.selected())) addPick.selected(opts.isEmpty() ? "" : opts.get(0));
            addPick.enabled = !opts.isEmpty();
            addBtn.enabled = !opts.isEmpty();
            addAll.enabled = !opts.isEmpty();
        }

        private void addSelected() {
            String id = addPick.selected();
            Asset b = project().get("biomes/" + id + ".json");
            if (b == null) return;
            edit("Add to pool", j -> J.arrOrCreate(j, "biomes").add(Templates.poolEntry(id, b.json)));
        }

        private void addAllMissing() {
            Set<String> have = inPool();
            edit("Add all biomes", j -> {
                for (Asset b : project().assetsOf(AssetType.BIOME)) {
                    if (!have.contains(b.id())) J.arrOrCreate(j, "biomes").add(Templates.poolEntry(b.id(), b.json));
                }
            });
        }

        private void syncValues() {
            JsonArray arr = pool();
            double total = 0;
            for (JsonElement e : arr) {
                if (e.isJsonObject() && J.bool(e.getAsJsonObject(), "enabled", true)) total += Math.max(0, J.num(e.getAsJsonObject(), "weight", 1));
            }
            for (int i = 0; i < count && i < arr.size(); i++) {
                JsonObject o = arr.get(i).isJsonObject() ? arr.get(i).getAsJsonObject() : new JsonObject();
                toggles.get(i).checked = J.bool(o, "enabled", true);
                String[] keys = {"weight", "size", "transition"};
                double[] defs = {1, 1, 48};
                for (int k = 0; k < 3; k++) {
                    TextField f = fields.get(i)[k];
                    if (forceSync || !f.isFocused()) f.setText(fmt(J.num(o, keys[k], defs[k]), false));
                }
                boolean en = J.bool(o, "enabled", true);
                double wgt = Math.max(0, J.num(o, "weight", 1));
                shares.get(i).text = en && total > 0 ? String.format("%.0f%%", 100 * wgt / total) : "-";
                shares.get(i).color = en ? Ui.TEXT_DIM : Ui.TEXT_FAINT;
            }
            syncedRev = project().revision;
        }

        @Override
        public void layout() {
            int cx = x + 4;
            int nameX = x + 24;
            int right = x + w - 4;
            int xw = 16;
            int shareW = 34, tW = 46, sW = 42, wW = 46;
            int rm = right - xw;
            int shareX = rm - shareW - 4;
            int tX = shareX - tW - 4;
            int sX = tX - sW - 4;
            int wX = sX - wW - 4;
            for (int i = 0; i < count; i++) {
                int ry = y + 20 + i * ROW;
                toggles.get(i).setBounds(cx, ry + 2, 14, 14);
                fields.get(i)[0].setBounds(wX, ry + 1, wW, 16);
                fields.get(i)[1].setBounds(sX, ry + 1, sW, 16);
                fields.get(i)[2].setBounds(tX, ry + 1, tW, 16);
                shares.get(i).setBounds(shareX, ry + 1, shareW, 16);
                rowWidgets.get(i).setBounds(rm, ry + 1, xw, 16);
            }
            int ay = y + 20 + count * ROW + 8;
            if (addPick != null) {
                addPick.setBounds(x, ay, Math.min(190, w - 220), 16);
                addBtn.setBounds(addPick.x + addPick.w + 4, ay, 44, 16);
                addAll.setBounds(addBtn.x + addBtn.w + 4, ay, 96, 16);
            }
            this.nameX = nameX;
            this.wXs = wX;
        }

        private int nameX, wXs;

        @Override
        protected void render(Gfx g, int mx, int my) {
            if (!signature.equals(sig())) { rebuildRows(); layout(); }
            if (syncedRev != project().revision) { syncValues(); refreshAddOptions(); }
            g.box(x, y, w, 18, Ui.BG_RAISED, Ui.LINE);
            g.textMid("Biome", nameX, y, 18, Ui.TEXT_FAINT);
            g.textMid("Weight", wXs + 2, y, 18, Ui.TEXT_FAINT);
            g.textMid("Size", wXs + 46 + 4 + 2, y, 18, Ui.TEXT_FAINT);
            g.textMid("Blend", wXs + 46 + 4 + 42 + 4 + 2, y, 18, Ui.TEXT_FAINT);
            g.textMid("Share", wXs + 46 + 4 + 42 + 4 + 46 + 4 + 2, y, 18, Ui.TEXT_FAINT);
            JsonArray arr = pool();
            for (int i = 0; i < count && i < arr.size(); i++) {
                int ry = y + 20 + i * ROW;
                JsonObject o = arr.get(i).isJsonObject() ? arr.get(i).getAsJsonObject() : new JsonObject();
                String id = J.str(o, "biome", "?");
                Asset b = project().get("biomes/" + id + ".json");
                boolean en = J.bool(o, "enabled", true);
                if (i % 2 == 1) g.fill(x, ry, w, ROW, 0x14FFFFFF);
                int dotColor = b == null ? Ui.RED : Cat.color(J.str(b.json, "category", ""));
                g.fill(nameX, ry + 7, 4, 4, en ? dotColor : Gfx.alpha(dotColor, 0x50));
                String name = b == null ? id + " (missing)" : b.displayName();
                g.textFit(name, nameX + 9, ry, ROW, wXs - nameX - 14, b == null ? Ui.RED : (en ? Ui.TEXT : Ui.TEXT_FAINT));
            }
            if (count == 0) g.textMid("No biomes yet. Add some below.", x + 6, y + 20, ROW, Ui.TEXT_FAINT);
        }
    }
}
