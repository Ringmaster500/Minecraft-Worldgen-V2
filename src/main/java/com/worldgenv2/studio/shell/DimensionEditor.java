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

        builtRefs = refSignature();
        builtFeatures = featureSignature();

        section("Layout", "macro structure of the dimension");
        Dropdown layout = new Dropdown();
        layout.options(Templates.LAYOUTS);
        layout.display = Templates::layoutLabel;
        layout.onSelect = this::chooseLayout;
        bind(() -> layout.selected(getStr("layout", "voronoi_continent")));
        field("Layout", layout, "A starting template. Picking one fills in the zones, rings and bounds below, which you can then edit freely.");
        TextField seed = num("seed", 1337, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, true, "Seed");
        Button roll = new Button("Roll", () -> edit("Seed", j -> J.setNum(j, "seed", new Random().nextInt(900_000_000))));
        roll.tooltip = "Pick a random seed";
        field("Seed", new Pair(seed, roll, 36), "Seed for this dimension's layout. Biome noise also derives from it.");
        field("Seed mode", choice("seedMode", "world", List.of("world", "fixed"),
                s -> s.equals("fixed") ? "Fixed - always use this seed" : "Random per world", "Seed mode"),
                "Per-world: each new world rolls its own layout (the seed above is only the preview). Fixed: every world is identical, e.g. a story map.");
        field("Cell size", num("cellSize", 192, 16, 4096, 16, true, "Cell size"), "Average Voronoi cell width in blocks.");

        String zone = getStr("hytale.worldStructure", "");
        section("Biome map", "who decides where each biome goes");
        field("Biomes come from", choice("hytale.worldStructure", "", hytaleZoneOptions(), s -> s.isEmpty() ? "Your biome pool (random regions)" : "Hytale zone: " + s, "Biome map"),
                "Your biome pool: the biomes in the pool below are scattered in random regions. A Hytale zone: Hytale's own map places Hytale's biomes, and the pool below is not used.");
        if (!zone.isEmpty()) {
            note("This setup uses the Hytale zone '" + zone + "', so its map decides the biomes. Biomes you add to the pool are ignored. "
                    + "Switch the box above to 'Your biome pool' to use them.", Ui.YELLOW);
        }

        section("Biome pool", "which biomes this dimension may generate" + (zone.isEmpty() ? "" : " (not used while a Hytale zone is selected)"));
        block(new PoolTable());

        // ---- zones: Hytale-style macro regions, each with its own biome set ----
        if (feature("zones", "Zones", "regions of the world that each use their own biomes (like Hytale's Zone 1-4)", false)) {
        field("Patch scale", num("zoneScale", 2000, 256, 20000, 100, false, "Zone patch scale"),
                "Average width in blocks of 'patch' zones. Bigger = larger, rarer regions.");
        block(new ZoneRefs());
        }

        // ---- spawn: the "start with a structure" workflow ----
        if (feature("spawn", "Spawn structure", "a guaranteed structure where players start", false)) {
        field("Structure", choice("spawn.prefab", "", prefabOptions(), prefabLabel(), "Spawn prefab"),
                "Which prefab to build. Add prefabs under Prefabs in the explorer.");
        field("Position X", num("spawn.x", 0, -30000, 30000, 8, true, "Spawn X"), "World X of the structure's origin.");
        field("Position Z", num("spawn.z", 0, -30000, 30000, 8, true, "Spawn Z"), "World Z of the structure's origin.");
        field("Rotation", choice("spawn.rotation", "0", ROTATIONS, DimensionEditor::rotationLabel, "Spawn rotation"), "Turn the structure clockwise (seen from above).");
        field("Buried depth", num("spawn.depth", 0, 0, 200, 1, true, "Spawn depth"), "Sink the structure this many blocks below the surface (0 = on the surface).");
        field("Crater radius", num("spawn.craterRadius", 0, 0, 400, 2, false, "Crater radius"), "0 = no crater. A crash site excavates the ground around it.");
        field("Crater depth", num("spawn.craterDepth", 0, 0, 64, 1, false, "Crater depth"), "How deep the crater is carved.");
        field("Crater rim", num("spawn.rimHeight", 0, 0, 32, 1, false, "Crater rim"), "Height of the raised lip around the crater.");
        field("Lock player spawn", toggle("spawn.lockSpawn", true, "Lock spawn", "New players always start here"), null);
        }

        // ---- landmarks: connected structures ----
        if (feature("landmarks", "Connected structures", "landmarks placed relative to each other, optionally joined by roads", false)) {
        block(new CardList(this, "landmarks", "landmark", List.of(
                CardList.Field.str("id", "Name", "Unique name; other landmarks refer to it."),
                CardList.Field.choice("prefab", "Structure", this::prefabOptions, prefabLabel(), "Prefab to place."),
                CardList.Field.choice("rotation", "Rotation", () -> ROTATIONS, DimensionEditor::rotationLabel, "Turn the structure clockwise (seen from above)."),
                CardList.Field.integer("depth", "Buried depth", 0, 200, "Sink the structure this many blocks below the surface, for hidden vaults. Props can react to it with StructureNear."),
                CardList.Field.choice("parent", "Placed from", this::parentOptions,
                        s -> s.isEmpty() ? "(world origin)" : s.equals("spawn") ? "Spawn structure" : s, "Root landmarks are placed from the origin; others around a root landmark."),
                CardList.Field.num("minDistance", "Min distance", 0, 20000, 8, "Blocks from the origin (root) or from its parent."),
                CardList.Field.num("maxDistance", "Max distance", 0, 20000, 8, "Maximum blocks away."),
                CardList.Field.choice("direction", "Direction", () -> List.of("auto", "N", "NE", "E", "SE", "S", "SW", "W", "NW"),
                        s -> s.equals("auto") ? "Automatic" : s, "Which side of its parent this landmark sits on.").when(o -> !J.str(o, "parent", "").isEmpty()),
                CardList.Field.toggle("road", "Connect by road", "Build a road between this landmark and its parent.").when(o -> !J.str(o, "parent", "").isEmpty()),
                CardList.Field.num("roadWidth", "Road width", 2, 16, 1, null).when(o -> !J.str(o, "parent", "").isEmpty() && J.bool(o, "road", true)),
                CardList.Field.num("shoulderWidth", "Shoulder", 0, 12, 1, null).when(o -> !J.str(o, "parent", "").isEmpty() && J.bool(o, "road", true)),
                CardList.Field.choice("zone", "Only in zone", this::zoneOptions, s -> s.isEmpty() ? "(any zone)" : s, "Restrict to one of the zones above."),
                CardList.Field.biomes("biomes", "Allowed in", "Biomes this landmark may be placed in. Empty = anywhere.")),
                this::newLandmark, o -> J.str(o, "id", "landmark") + (J.str(o, "parent", "").isEmpty() ? "   (root)" : "   <- " + J.str(o, "parent", "")),
                "None yet. Add a root landmark, then others that connect to it."));
        }

        // ---- budgets: finite points of interest ----
        if (feature("budgets", "Points of interest", "a fixed number of structures scattered between two distances from spawn", false)) {
        block(new CardList(this, "budgets", "point of interest", List.of(
                CardList.Field.str("id", "Name", "Unique name."),
                CardList.Field.choice("prefab", "Structure", this::prefabOptions, prefabLabel(), "Prefab to place."),
                CardList.Field.choice("rotation", "Rotation", () -> ROTATIONS, DimensionEditor::rotationLabel, "Random turns each copy a stable, different way."),
                CardList.Field.integer("depth", "Buried depth", 0, 200, "Sink each structure this many blocks below the surface. Props can react to it with StructureNear."),
                CardList.Field.num("minRadius", "Min radius", 0, 30000, 50, "Closest distance from spawn."),
                CardList.Field.num("maxRadius", "Max radius", 0, 30000, 50, "Farthest distance from spawn."),
                CardList.Field.integer("minQuota", "Min count", 1, 256, "At least this many are placed."),
                CardList.Field.integer("maxQuota", "Max count", 1, 256, "Never more than this many, anywhere in the world."),
                CardList.Field.num("separation", "Spacing", 32, 5000, 16, "Minimum distance between two of them."),
                CardList.Field.str("loot", "Loot table", "Optional loot table override for chests inside."),
                CardList.Field.choice("zone", "Only in zone", this::zoneOptions, s -> s.isEmpty() ? "(any zone)" : s, "Restrict to one of the zones above."),
                CardList.Field.biomes("biomes", "Allowed in", "Empty = any biome.")),
                this::newBudget, o -> J.str(o, "id", "poi") + "   " + J.integer(o, "minQuota", 1) + "-" + J.integer(o, "maxQuota", 1) + " total",
                "None yet. Add one to scatter a finite set of structures."));
        }

        // ---- placement rules: node-built things that find a spot and build on it (bridges, camps, shrines...) ----
        if (feature("placements", "Placement rules", "rules that search the world for a spot and build there, like a fallen tree across a river", false)) {
        block(new PlacementRefs());
        }

        // ---- ores: all of them are rules here; vanilla ore generation is switched off ----
        if (feature("ores", "Ores", "veins of ore; nothing from vanilla generates, so ore comes only from here", true)) {
        block(new CardList(this, "ores", "ore", List.of(
                CardList.Field.str("id", "Name", null),
                CardList.Field.str("block", "Block", "e.g. minecraft:iron_ore or a modded ore id."),
                CardList.Field.integer("veinSize", "Vein size", 1, 64, "Blocks per vein, roughly."),
                CardList.Field.integer("count", "Veins / chunk", 0, 256, "Attempts per 16x16 chunk."),
                CardList.Field.integer("minY", "Min Y", -2048, 4096, null),
                CardList.Field.integer("maxY", "Max Y", -2048, 4096, null),
                CardList.Field.choice("distribution", "Spread", () -> List.of("uniform", "triangle"),
                        s -> s.equals("triangle") ? "Triangle (peaks mid-range)" : "Uniform", "Triangle concentrates veins in the middle of the Y range."),
                CardList.Field.str("replaces", "Replaces", "Comma-separated block ids. Empty = stone, deepslate, andesite, granite, diorite, tuff and the base block."),
                CardList.Field.choice("zone", "Only in zone", this::zoneOptions, s -> s.isEmpty() ? "(any zone)" : s, "Restrict to one of the zones above."),
                CardList.Field.biomes("biomes", "Allowed in", "Empty = any biome.")),
                this::newOre, o -> J.str(o, "id", "ore") + "   " + J.str(o, "block", "").replace("minecraft:", "") + "   " + J.integer(o, "count", 0) + "x" + J.integer(o, "veinSize", 0),
                "No ores: the world is solid rock. Add some, or switch to Default."));
        }

        // ---- terrain shapes / rings / bounds ----
        if (feature("shapes", "Terrain shapes", "circular regions that sculpt terrain: islands, moats, floating archipelagos", false)) {
        block(new CardList(this, "shapes", "shape", List.of(
                CardList.Field.str("id", "Name", null),
                CardList.Field.choice("mode", "Effect", () -> List.of("BIOME_OVERRIDE", "ISLAND_PLATEAU", "VOID_MOAT", "OUTER_ISLANDS"),
                        DimensionEditor::zoneLabel, "What this zone does to terrain."),
                CardList.Field.num("centerX", "Center X", -30000, 30000, 8, null),
                CardList.Field.num("centerZ", "Center Z", -30000, 30000, 8, null),
                CardList.Field.num("inner", "Inner radius", 0, 60000, 8, "Zone starts this far from its center."),
                CardList.Field.num("outer", "Outer radius", 0, 60000, 8, "Zone ends this far from its center."),
                CardList.Field.num("falloff", "Edge blend", 4, 500, 4, "Distance over which the effect fades."),
                CardList.Field.choice("biome", "Biome", this::biomeOptions, s -> s.isEmpty() ? "(unchanged)" : s, "Biome forced inside the zone."),
                CardList.Field.num("elevation", "Surface Y", -2048, 4096, 1, "Target ground height for plateau/island zones."),
                CardList.Field.num("thickness", "Thickness", 8, 400, 4, null),
                CardList.Field.toggle("floating", "Floating", "Round off the underside so it floats.")),
                this::newZone, o -> J.str(o, "id", "zone") + "   " + zoneLabel(J.str(o, "mode", "")) + "  r " + J.integer(o, "inner", 0) + "-" + J.integer(o, "outer", 0),
                "No shapes yet."));
        }

        if (feature("rings", "Pillar rings", "circular arrays of towers (e.g. the End's obsidian pillars)", false)) {
        block(new CardList(this, "rings", "ring", List.of(
                CardList.Field.str("id", "Name", null),
                CardList.Field.integer("count", "Count", 1, 64, null),
                CardList.Field.num("radius", "Ring radius", 8, 2000, 4, null),
                CardList.Field.integer("pillarRadius", "Pillar radius", 1, 8, null),
                CardList.Field.integer("minHeight", "Min height", 16, 400, null),
                CardList.Field.integer("maxHeight", "Max height", 16, 400, null),
                CardList.Field.str("block", "Block", "e.g. minecraft:obsidian"),
                CardList.Field.str("cap", "Cap block", "e.g. minecraft:bedrock")),
                this::newRing, o -> J.str(o, "id", "ring") + "   " + J.integer(o, "count", 0) + " pillars",
                "None yet."));
        }

        section("World edge & materials", "what happens beyond a finite world, and the base blocks");
        field("Outside bounds", choice("bounds.outside", "DEFAULT_BIOMES", List.of("DEFAULT_BIOMES", "VOID", "OCEAN", "VANILLA_DEFAULT"),
                s -> switch (s) { case "VOID" -> "Void"; case "OCEAN" -> "Ocean"; case "VANILLA_DEFAULT" -> "Vanilla terrain"; default -> "Continue biomes"; },
                "Outside bounds"), "What exists past the bounds radius.");
        field("Bounds radius", num("bounds.radius", 0, 0, 100000, 16, false, "Bounds radius"), "0 = infinite.");
        field("Edge blend", num("bounds.falloff", 64, 8, 2000, 8, false, "Bounds falloff"), "Blend distance at the bounds edge.");
        field("Base block", blockField("defaultBlock", "minecraft:stone", "Base block"), "Solid fill used for terrain.");
        field("Fluid", blockField("defaultFluid", "minecraft:water", "Fluid"), "Liquid for oceans and rivers. Use minecraft:air for none.");
        field("Caves", toggle("caves", true, "Caves", "Carve caves and underground biomes"), null);
        field("Cave layout", choice("caveMode", "classic", List.of("graph", "classic"), s -> s.equals("graph") ? "Node graph (Hytale)" : "Classic noise", "Cave layout"),
                "Node graph: caves are a Graph Generator whose nodes carry density (chambers and tunnels), editable in the Graph view. Classic noise: the older cheese and spaghetti carver.");
        field("Cave graph", new Button("Customize as nodes", () -> {
            var a = asset();
            if (a == null) return;
            project().edit(a, "Customize cave graph", j -> {
                try {
                    var g = com.google.gson.JsonParser.parseString(com.worldgenv2.pack.ProjectCompiler.defaultCaveGraph()).getAsJsonObject();
                    g.addProperty("type", "GraphGenerator");
                    j.add("caveGraph", g);
                    j.addProperty("caveMode", "graph");
                } catch (RuntimeException ignored) { }
            });
        }), "Copies the shipped cave graph into this dimension so its passes and content can be changed in the Graph view.");

        section("Rivers", "winding channels with gentle valleys; off removes them completely");
        field("Rivers", toggle("rivers", true, "Rivers", "Carve rivers"), null);
        field("River layout", choice("riverMode", "classic", List.of("graph", "classic"), s -> s.equals("graph") ? "Node graph (Hytale)" : "Classic noise", "River layout"),
                "Node graph: rivers follow a Graph Generator you can edit in the Graph view (Hytale's way). Classic noise: the older wandering ridge, tuned by Spacing and Meander.");
        field("River graph", new Button("Customize as nodes", () -> {
            var a = asset();
            if (a == null) return;
            project().edit(a, "Customize river graph", j -> {
                try {
                    var g = com.google.gson.JsonParser.parseString(com.worldgenv2.pack.ProjectCompiler.defaultRiverGraph()).getAsJsonObject();
                    g.addProperty("type", "GraphGenerator");
                    j.add("riverGraph", g);
                    j.addProperty("riverMode", "graph");
                } catch (RuntimeException ignored) { }
            });
        }), "Copies the shipped river graph into this dimension so its passes can be changed in the Graph view.");
        field("Spacing", num("riverSettings.spacing", 1000, 200, 6000, 50, false, "River spacing"), "Roughly how far apart rivers run. Bigger = fewer, longer rivers.");
        field("Water width", num("riverSettings.channelWidth", 30, 4, 200, 2, false, "River width"), "Width of the water surface, in blocks.");
        field("Bank to bank", num("riverSettings.valleyWidth", 60, 8, 400, 4, false, "River bank width"), "Total width including the soft banks on both sides. The ground blends into the water over this distance.");
        field("Depth", num("riverSettings.depth", 3, 1, 20, 1, true, "River depth"), "How deep the channel is below the water level at its centre. Rivers follow the land, so they stay flush with it.");
        field("Meander", num("riverSettings.meander", 96, 0, 400, 8, false, "River meander"), "How much rivers wander. 0 = straight.");
        field("Width variation", num("riverSettings.widthVariation", 0.22, 0, 1, 0.02, false, "River width variation"), "How much the width changes along a river.");

        section("Oceans", "how deep the seas are");
        field("Depth scale", num("ocean.depthScale", 1, 0.1, 4, 0.1, false, "Ocean depth scale"), "1 = as your biomes define it. 0.5 = shallow seas, 2 = deep ocean. Land is not affected.");
    }

    // ---- feature modes: Off / Default (built in) / Custom ----
    private String builtFeatures = "";
    private static final List<String> FEATURE_KEYS = List.of("zones", "spawn", "landmarks", "budgets", "placements", "ores", "shapes", "rings");
    private static final List<String> ROTATIONS = List.of("0", "90", "180", "270", "random");

    private static String rotationLabel(String s) {
        return switch (s) { case "random" -> "Random (stable per structure)"; case "90", "180", "270" -> s + " degrees"; default -> "None (as built)"; };
    }

    /** Current mode of a feature: explicit {@code features.<key>}, otherwise inferred from whether it has data. */
    private String modeOf(String key) {
        Asset a = asset();
        JsonObject d = a == null ? new JsonObject() : a.json;
        String m = J.str(J.obj(d, "features"), key, "");
        if (!m.isEmpty()) return m;
        return switch (key) {
            case "ores" -> com.worldgenv2.pack.ProjectCompiler.oresMode(d);
            case "spawn" -> J.bool(J.obj(d, "spawn"), "enabled", false) ? "custom" : "off";
            default -> { JsonArray arr = J.arr(d, key); yield arr != null && !arr.isEmpty() ? "custom" : "off"; }
        };
    }

    private String featureSignature() {
        StringBuilder sb = new StringBuilder();
        for (String k : FEATURE_KEYS) sb.append(k).append('=').append(modeOf(k)).append(';');
        return sb.toString();
    }

    /**
     * Adds a section header with an Off / Default / Custom switch. Returns true only for Custom, in which case the caller
     * builds the editing UI; otherwise a one-line explanation is shown and the section stays collapsed.
     * Switching a feature off never deletes its data.
     */
    private boolean feature(String key, String title, String subtitle, boolean hasDefault) {
        String mode = modeOf(key);
        section(title, subtitle);
        Dropdown d = new Dropdown();
        d.options(hasDefault ? List.of("off", "default", "custom") : List.of("off", "custom"));
        d.display = s -> switch (s) {
            case "off" -> "Off - not used";
            case "default" -> "Default - built in, nothing to set up";
            default -> "Custom - I'll set this up";
        };
        d.onSelect = v -> edit("Feature " + key, j -> J.objOrCreate(j, "features").addProperty(key, v));
        bind(() -> d.selected(modeOf(key)));
        field("Use", d, "Off: the feature does nothing here (your settings are kept). Default: the built-in behaviour, no setup. Custom: you define it.");
        if (mode.equals("off")) {
            note("Off. Nothing from this feature is generated in this dimension.", Ui.TEXT_FAINT);
            return false;
        }
        if (mode.equals("default")) {
            note("Using the built-in setup. Choose Custom to edit it.", Ui.TEXT_FAINT);
            if (key.equals("ores")) {
                block(new Button("Copy the default ores here so I can edit them", () ->
                        edit("Customize ores", j -> {
                            j.add("ores", com.worldgenv2.pack.WorldGenV2Addons.defaultOres());
                            J.objOrCreate(j, "features").addProperty("ores", "custom");
                        })));
            }
            return false;
        }
        return true;
    }

    // ---- option sources ----
    private String builtRefs = "";

    private String refSignature() {
        StringBuilder sb = new StringBuilder();
        for (Asset a : project().assetsOf(AssetType.PREFAB)) sb.append('p').append(a.id());
        for (Asset a : project().assetsOf(AssetType.BIOME)) sb.append('b').append(a.id());
        return sb.toString();
    }

    /** "" (use the biome pool) plus every world structure ("zone") in the player's Hytale assets, and the one already chosen. */
    private List<String> hytaleZoneOptions() {
        List<String> out = new ArrayList<>();
        out.add("");
        String path = getStr("hytale.assets", "");
        if (path.isBlank()) path = com.worldgenv2.hytale.HytaleAssets.defaultAssetsPath();
        if (path != null) {
            try (var pack = com.worldgenv2.hytale.HytaleAssets.open(path)) {
                for (String z : pack.worldStructures()) if (!out.contains(z)) out.add(z);
            } catch (Exception ignored) { }
        }
        String cur = getStr("hytale.worldStructure", "");
        if (!cur.isEmpty() && !out.contains(cur)) out.add(cur);
        return out;
    }

    private List<String> prefabOptions() {
        List<String> o = new ArrayList<>();
        o.add("");
        for (Asset a : project().assetsOf(AssetType.PREFAB)) o.add(a.id());
        return o;
    }

    private java.util.function.Function<String, String> prefabLabel() {
        return s -> s.isEmpty() ? "(none)" : s;
    }

    private List<String> biomeOptions() {
        List<String> o = new ArrayList<>();
        o.add("");
        for (Asset a : project().assetsOf(AssetType.BIOME)) o.add(a.id());
        return o;
    }

    private List<String> parentOptions() {
        List<String> o = new ArrayList<>();
        o.add("");
        Asset a = asset();
        if (modeOf("spawn").equals("custom")) o.add("spawn");
        JsonArray lms = a == null ? null : J.arr(a.json, "landmarks");
        if (lms != null) for (JsonElement e : lms) {
            if (e.isJsonObject() && J.str(e.getAsJsonObject(), "parent", "").isEmpty()) o.add(J.str(e.getAsJsonObject(), "id", ""));
        }
        return o;
    }

    private List<String> zoneOptions() {
        List<String> o = new ArrayList<>();
        o.add("");
        Asset a = asset();
        JsonArray zs = a == null ? null : J.arr(a.json, "zones");
        if (zs != null) for (JsonElement e : zs) { if (e.isJsonPrimitive()) o.add(e.getAsString()); else if (e.isJsonObject()) o.add(J.str(e.getAsJsonObject(), "id", "")); }
        return o;
    }

    private static String zoneKindLabel(String kind) {
        return switch (kind) {
            case "everywhere" -> "Everywhere (fallback)";
            case "patch" -> "Patches";
            case "radial" -> "Distance band";
            case "noise" -> "Noise range";
            default -> kind;
        };
    }

    private JsonObject newZoneRegion(int n) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "zone_" + (n + 1));
        o.addProperty("name", "Zone " + (n + 1));
        o.addProperty("kind", "patch");
        J.setNum(o, "weight", 1);
        o.add("biomes", new JsonArray());
        return o;
    }

    private JsonObject newOre(int n) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "ore_" + (n + 1));
        o.addProperty("block", "minecraft:iron_ore");
        J.setNum(o, "veinSize", 8);
        J.setNum(o, "count", 10);
        J.setNum(o, "minY", -64);
        J.setNum(o, "maxY", 64);
        o.addProperty("distribution", "uniform");
        o.addProperty("replaces", "");
        o.addProperty("zone", "");
        o.add("biomes", new JsonArray());
        return o;
    }

    private static String zoneLabel(String mode) {
        return switch (mode) {
            case "ISLAND_PLATEAU" -> "Island / plateau";
            case "VOID_MOAT" -> "Void moat";
            case "OUTER_ISLANDS" -> "Floating islands";
            case "BIOME_OVERRIDE" -> "Force biome";
            default -> mode;
        };
    }

    // ---- new-entry factories ----
    private int countOf(String key) {
        Asset a = asset();
        JsonArray arr = a == null ? null : J.arr(a.json, key);
        return arr == null ? 0 : arr.size();
    }

    private JsonObject newLandmark(int n) {
        JsonObject o = new JsonObject();
        boolean first = parentOptions().size() == 1;
        o.addProperty("id", first ? "central_site" : "outpost_" + n);
        o.addProperty("prefab", "");
        o.addProperty("parent", first ? "" : parentOptions().get(1));
        J.setNum(o, "minDistance", first ? 48 : 120);
        J.setNum(o, "maxDistance", first ? 140 : 240);
        o.addProperty("direction", "auto");
        o.addProperty("road", true);
        J.setNum(o, "roadWidth", 5);
        J.setNum(o, "shoulderWidth", 3);
        o.addProperty("faceParent", true);
        o.add("biomes", new JsonArray());
        return o;
    }

    private JsonObject newBudget(int n) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "poi_" + (n + 1));
        o.addProperty("prefab", "");
        J.setNum(o, "minRadius", 600);
        J.setNum(o, "maxRadius", 2500);
        J.setNum(o, "minQuota", 3);
        J.setNum(o, "maxQuota", 5);
        J.setNum(o, "separation", 300);
        o.addProperty("loot", "");
        o.add("biomes", new JsonArray());
        return o;
    }

    private JsonObject newZone(int n) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "zone_" + (n + 1));
        o.addProperty("mode", "BIOME_OVERRIDE");
        J.setNum(o, "centerX", 0);
        J.setNum(o, "centerZ", 0);
        J.setNum(o, "inner", 0);
        J.setNum(o, "outer", 300);
        J.setNum(o, "falloff", 32);
        o.addProperty("biome", "");
        J.setNum(o, "elevation", 70);
        J.setNum(o, "thickness", 32);
        o.addProperty("floating", false);
        return o;
    }

    private JsonObject newRing(int n) {
        JsonObject o = new JsonObject();
        o.addProperty("id", "ring_" + (n + 1));
        J.setNum(o, "centerX", 0);
        J.setNum(o, "centerZ", 0);
        J.setNum(o, "radius", 40);
        J.setNum(o, "count", 8);
        J.setNum(o, "pillarRadius", 3);
        J.setNum(o, "minHeight", 70);
        J.setNum(o, "maxHeight", 100);
        o.addProperty("block", "minecraft:obsidian");
        o.addProperty("cap", "minecraft:bedrock");
        return o;
    }

    private void chooseLayout(String layoutId) {
        if (layoutId.equals(getStr("layout", ""))) return;
        Runnable apply = () -> edit("Layout template", j -> Templates.applyLayout(j, layoutId, project()));
        if ((countOf("zones") > 0 || countOf("rings") > 0) && root != null) {
            Modal.confirm(root, "Replace zones?", "Applying '" + Templates.layoutLabel(layoutId) + "' replaces this dimension's\nzones, pillar rings and world bounds with the template's.",
                    "Apply template", true, apply);
        } else {
            apply.run();
        }
    }

    @Override
    protected void onSynced() {
        if (!getStr("generator", "worldgenv2").equals(builtGenerator) || !refSignature().equals(builtRefs)
                || (builtGenerator.equals("worldgenv2") && !featureSignature().equals(builtFeatures))) rebuild();
    }

    private JsonArray pool() {
        Asset a = asset();
        return a == null ? new JsonArray() : J.arrOrCreate(a.json, "biomes");
    }

    private final class PoolTable extends Widget {
        private static final int ROW = 18;
        private String signature = null;   // not built yet: the first render must build the rows and the add picker, even for an empty list
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
            h = 20 + Math.max(count, 1) * ROW + 30;
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
            int ay = y + 20 + Math.max(count, 1) * ROW + 8;
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
            if (signature == null || !signature.equals(sig())) { rebuildRows(); layout(); }
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

    /** The zones this dimension uses, in priority order (first match wins). Zones are separate assets with their own form and graph. */
    private final class ZoneRefs extends Widget {
        private static final int ROW = 18;
        private String signature = "";
        private final List<Button[]> rowBtns = new ArrayList<>();
        private final List<String> rowIds = new ArrayList<>();
        private Dropdown addPick;
        private Button addBtn, newBtn;

        ZoneRefs() { h = 50; }

        private List<String> ids() {
            List<String> out = new ArrayList<>();
            Asset a = asset();
            JsonArray zs = a == null ? null : J.arr(a.json, "zones");
            if (zs != null) for (JsonElement e : zs) if (e.isJsonPrimitive()) out.add(e.getAsString());
            return out;
        }

        private String sig() {
            StringBuilder sb = new StringBuilder();
            for (String id : ids()) sb.append(id).append(',');
            for (Asset z : project().assetsOf(AssetType.ZONE)) sb.append('|').append(z.id());
            return sb.toString();
        }

        private void rebuild() {
            clearChildren();
            rowBtns.clear();
            rowIds.clear();
            List<String> ids = ids();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                final String id = ids.get(i);
                Button edit = add(new Button("Edit", () -> app.openDoc("zones/" + id + ".json")));
                Button up = add(new Button("^", () -> move(idx, -1)));
                Button down = add(new Button("v", () -> move(idx, 1)));
                Button rm = add(new Button("x", () -> edit("Remove zone", j -> J.arrOrCreate(j, "zones").remove(idx))).style(Button.Style.DANGER));
                up.tooltip = "Higher priority: the first zone whose shape matches wins";
                down.tooltip = "Lower priority";
                rm.tooltip = "Stop using this zone here (the zone itself is kept)";
                rowBtns.add(new Button[]{edit, up, down, rm});
                rowIds.add(id);
            }
            addPick = add(new Dropdown());
            addPick.placeholder = "Use an existing zone...";
            List<String> free = new ArrayList<>();
            for (Asset z : project().assetsOf(AssetType.ZONE)) if (!ids.contains(z.id())) free.add(z.id());
            addPick.options(free);
            addPick.display = id -> { Asset z = project().get("zones/" + id + ".json"); return z == null ? id : J.str(z.json, "name", z.displayName()); };
            addPick.selected(free.isEmpty() ? "" : free.get(0));
            addPick.enabled = !free.isEmpty();
            addBtn = add(new Button("Add", () -> {
                String pick = addPick.selected();
                if (!pick.isEmpty()) edit("Use zone", j -> J.arrOrCreate(j, "zones").add(pick));
            }).style(Button.Style.PRIMARY));
            addBtn.enabled = !free.isEmpty();
            newBtn = add(new Button("+ New zone", () -> {
                int n = 1;
                while (project().checkNewId(AssetType.ZONE, "zone_" + n) != null) n++;
                String id = "zone_" + n;
                app.createAsset(AssetType.ZONE, id);
                edit("Use zone", j -> J.arrOrCreate(j, "zones").add(id));
            }));
            signature = sig();
            h = 20 + rowIds.size() * ROW + 30;
        }

        private void move(int idx, int dir) {
            int to = idx + dir;
            edit("Reorder zones", j -> {
                JsonArray a = J.arrOrCreate(j, "zones");
                if (to < 0 || to >= a.size()) return;
                JsonElement tmp = a.get(idx);
                a.set(idx, a.get(to));
                a.set(to, tmp);
            });
        }

        @Override
        public void layout() {
            for (int i = 0; i < rowBtns.size(); i++) {
                int ry = y + 20 + i * ROW;
                Button[] b = rowBtns.get(i);
                int right = x + w - 4;
                b[3].setBounds(right - 16, ry + 1, 16, 16);
                b[2].setBounds(right - 36, ry + 1, 16, 16);
                b[1].setBounds(right - 56, ry + 1, 16, 16);
                b[0].setBounds(right - 100, ry + 1, 40, 16);
            }
            int ay = y + 20 + rowIds.size() * ROW + 6;
            if (addPick != null) {
                addPick.setBounds(x, ay, Math.min(200, w - 240), 16);
                addBtn.setBounds(addPick.x + addPick.w + 4, ay, 44, 16);
                newBtn.setBounds(addBtn.x + addBtn.w + 4, ay, 84, 16);
            }
        }

        @Override
        protected void render(Gfx g, int mx, int my) {
            if (addPick == null || !signature.equals(sig())) { rebuild(); layout(); }
            layout();
            g.box(x, y, w, 18, Ui.BG_RAISED, Ui.LINE);
            g.textMid("Priority order (first matching zone wins)", x + 6, y, 18, Ui.TEXT_FAINT);
            for (int i = 0; i < rowIds.size(); i++) {
                int ry = y + 20 + i * ROW;
                Asset z = project().get("zones/" + rowIds.get(i) + ".json");
                if (i % 2 == 1) g.fill(x, ry, w, ROW, 0x14FFFFFF);
                String nm = z == null ? rowIds.get(i) + " (missing)" : J.str(z.json, "name", z.displayName());
                g.fill(x + 6, ry + 7, 4, 4, z == null ? Ui.RED : AssetType.ZONE.color);
                g.textFit((i + 1) + ".  " + nm, x + 14, ry, ROW, w - 130, z == null ? Ui.RED : Ui.TEXT);
                if (z != null) g.textRight("[" + zoneKindLabel(J.str(z.json, "kind", "")) + "]", x + w - 106, ry, ROW, Ui.TEXT_FAINT);
            }
            if (rowIds.isEmpty()) g.textMid("No zones in use. Add one, or create a new one.", x + 6, y + 20, ROW, Ui.TEXT_FAINT);
        }
    }

    /** The placement rules this dimension uses. Rules are separate assets built as node graphs: a finder, conditions, and what to place. */
    private final class PlacementRefs extends Widget {
        private static final int ROW = 18;
        private String signature = "";
        private final List<Button[]> rowBtns = new ArrayList<>();
        private final List<String> rowIds = new ArrayList<>();
        private Dropdown addPick;
        private Button addBtn, newBtn;

        PlacementRefs() { h = 50; }

        private List<String> ids() {
            List<String> out = new ArrayList<>();
            Asset a = asset();
            JsonArray zs = a == null ? null : J.arr(a.json, "placements");
            if (zs != null) for (JsonElement e : zs) if (e.isJsonPrimitive()) out.add(e.getAsString());
            return out;
        }

        private String sig() {
            StringBuilder sb = new StringBuilder();
            for (String id : ids()) sb.append(id).append(',');
            for (Asset z : project().assetsOf(AssetType.RULE)) sb.append('|').append(z.id());
            return sb.toString();
        }

        private void rebuild() {
            clearChildren();
            rowBtns.clear();
            rowIds.clear();
            List<String> ids = ids();
            for (int i = 0; i < ids.size(); i++) {
                final int idx = i;
                final String id = ids.get(i);
                Button edit = add(new Button("Edit", () -> app.openDoc("placements/" + id + ".json")));
                Button up = add(new Button("^", () -> move(idx, -1)));
                Button down = add(new Button("v", () -> move(idx, 1)));
                Button rm = add(new Button("x", () -> edit("Remove rule", j -> J.arrOrCreate(j, "placements").remove(idx))).style(Button.Style.DANGER));
                up.tooltip = "Earlier rules place first";
                down.tooltip = "Later";
                rm.tooltip = "Stop using this rule here (the rule itself is kept)";
                rowBtns.add(new Button[]{edit, up, down, rm});
                rowIds.add(id);
            }
            addPick = add(new Dropdown());
            addPick.placeholder = "Use an existing rule...";
            List<String> free = new ArrayList<>();
            for (Asset z : project().assetsOf(AssetType.RULE)) if (!ids.contains(z.id())) free.add(z.id());
            addPick.options(free);
            addPick.display = id -> { Asset z = project().get("placements/" + id + ".json"); return z == null ? id : J.str(z.json, "name", z.displayName()); };
            addPick.selected(free.isEmpty() ? "" : free.get(0));
            addPick.enabled = !free.isEmpty();
            addBtn = add(new Button("Add", () -> {
                String pick = addPick.selected();
                if (!pick.isEmpty()) edit("Use rule", j -> J.arrOrCreate(j, "placements").add(pick));
            }).style(Button.Style.PRIMARY));
            addBtn.enabled = !free.isEmpty();
            newBtn = add(new Button("+ New rule", () -> {
                int n = 1;
                while (project().checkNewId(AssetType.RULE, "rule_" + n) != null) n++;
                String id = "rule_" + n;
                app.createAsset(AssetType.RULE, id);
                edit("Use rule", j -> J.arrOrCreate(j, "placements").add(id));
            }));
            signature = sig();
            h = 20 + rowIds.size() * ROW + 30;
        }

        private void move(int idx, int dir) {
            int to = idx + dir;
            edit("Reorder rules", j -> {
                JsonArray a = J.arrOrCreate(j, "placements");
                if (to < 0 || to >= a.size()) return;
                JsonElement tmp = a.get(idx);
                a.set(idx, a.get(to));
                a.set(to, tmp);
            });
        }

        @Override
        public void layout() {
            for (int i = 0; i < rowBtns.size(); i++) {
                int ry = y + 20 + i * ROW;
                Button[] b = rowBtns.get(i);
                int right = x + w - 4;
                b[3].setBounds(right - 16, ry + 1, 16, 16);
                b[2].setBounds(right - 36, ry + 1, 16, 16);
                b[1].setBounds(right - 56, ry + 1, 16, 16);
                b[0].setBounds(right - 100, ry + 1, 40, 16);
            }
            int ay = y + 20 + rowIds.size() * ROW + 6;
            if (addPick != null) {
                addPick.setBounds(x, ay, Math.min(200, w - 240), 16);
                addBtn.setBounds(addPick.x + addPick.w + 4, ay, 44, 16);
                newBtn.setBounds(addBtn.x + addBtn.w + 4, ay, 84, 16);
            }
        }

        @Override
        protected void render(Gfx g, int mx, int my) {
            if (addPick == null || !signature.equals(sig())) { rebuild(); layout(); }
            layout();
            g.box(x, y, w, 18, Ui.BG_RAISED, Ui.LINE);
            g.textMid("Each rule searches the world on its own grid and places what it builds", x + 6, y, 18, Ui.TEXT_FAINT);
            for (int i = 0; i < rowIds.size(); i++) {
                int ry = y + 20 + i * ROW;
                Asset z = project().get("placements/" + rowIds.get(i) + ".json");
                if (i % 2 == 1) g.fill(x, ry, w, ROW, 0x14FFFFFF);
                String nm = z == null ? rowIds.get(i) + " (missing)" : J.str(z.json, "name", z.displayName());
                g.fill(x + 6, ry + 7, 4, 4, z == null ? Ui.RED : AssetType.RULE.color);
                g.textFit((i + 1) + ".  " + nm, x + 14, ry, ROW, w - 130, z == null ? Ui.RED : Ui.TEXT);
                if (z != null) g.textRight("[" + findLabel(z) + "]", x + w - 106, ry, ROW, Ui.TEXT_FAINT);
            }
            if (rowIds.isEmpty()) g.textMid("No placement rules in use. Add one, or create a new one.", x + 6, y + 20, ROW, Ui.TEXT_FAINT);
        }
    }
    private static String findLabel(Asset r) {
        var f = J.obj(r.json, "find");
        return f == null ? "no finder" : J.str(f, "type", "?");
    }
}
