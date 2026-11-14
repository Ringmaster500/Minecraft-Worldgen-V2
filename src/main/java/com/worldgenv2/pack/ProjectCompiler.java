package com.worldgenv2.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.minecraft.DefaultBiomeFactory;
import com.worldgenv2.minecraft.WorldGenV2ChunkGenerator;
import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.AssetType;
import com.worldgenv2.studio.project.J;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.worldstructure.*;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.RadialZoneRule;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.RingLandmarkRule;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.ZoneMode;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Turns a Studio project's JSON assets into a live engine {@link WorldStructure} for one dimension.
 * Shared by the Studio preview and by the runtime world loader, so what you preview is what generates.
 * <p>
 * Failure policy: a broken biome or rule never throws; it is skipped and reported in {@link Result#warnings}
 * so the creator sees exactly what was dropped and why.
 */
public final class ProjectCompiler {
    private ProjectCompiler() {}

    /** Numeric ids for project biomes start here so they can never collide with the built-in catalog (0..38). */
    public static final int FIRST_BIOME_ID = 64;

    public record BiomeBinding(int id, String assetId, String name, String minecraftBiome, String category) {}

    public static final class Result {
        public WorldStructure structure;
        public final List<BiomeBinding> biomes = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public int minY, maxY, seaLevel;
        public long seed;
        public String dimensionId = "";
        public SpawnAnchorManager.SpawnAnchorRule spawn;
        public ZoneLayout zones;
        public final Map<String, String> biomeNameToAsset = new HashMap<>();

        public BiomeBinding bindingById(int id) {
            for (BiomeBinding b : biomes) if (b.id() == id) return b;
            return null;
        }
    }

    /** Default Minecraft biome used for sky/foliage/mobs when a project biome does not name one. */
    public static String defaultMinecraftBiome(String category) {
        return switch (category == null ? "" : category.toLowerCase(Locale.ROOT)) {
            case "forest" -> "minecraft:forest";
            case "taiga" -> "minecraft:taiga";
            case "desert" -> "minecraft:desert";
            case "mountain" -> "minecraft:stony_peaks";
            case "tundra" -> "minecraft:snowy_plains";
            case "swamp" -> "minecraft:swamp";
            case "ocean" -> "minecraft:ocean";
            case "river" -> "minecraft:river";
            case "cave" -> "minecraft:dripstone_caves";
            default -> "minecraft:plains";
        };
    }

    public static Result compile(StudioProject project, String dimensionAssetId, long seedOverride) {
        Result r = new Result();
        JsonAstCompiler.registerBuiltInTypes();

        Asset world = project.world();
        JsonObject w = world != null ? world.json : new JsonObject();
        r.minY = J.integer(w, "minY", -64);
        int depth = J.integer(w, "genDepth", 384);
        r.maxY = r.minY + depth;
        r.seaLevel = J.integer(w, "seaLevel", 63);

        Asset dim = project.get("dimensions/" + dimensionAssetId + ".json");
        if (dim == null) {
            r.warnings.add("Dimension '" + dimensionAssetId + "' does not exist.");
            return r;
        }
        JsonObject d = dim.json;
        r.dimensionId = J.str(d, "id", dimensionAssetId);
        r.seed = seedOverride != Long.MIN_VALUE ? seedOverride : (long) J.num(d, "seed", 1337);
        int cellSize = Math.max(32, J.integer(d, "cellSize", 192));
        if (J.has(d, "seaLevel")) r.seaLevel = J.integer(d, "seaLevel", r.seaLevel);

        // ---- biomes (pool order defines numeric ids) ----
        List<TileBiome> tiles = new ArrayList<>();
        List<CustomBiome> customs = new ArrayList<>();
        Map<String, Integer> nameToId = new LinkedHashMap<>();
        JsonArray pool = J.arr(d, "biomes");
        int nextId = FIRST_BIOME_ID;
        List<Object[]> pending = new ArrayList<>();
        if (pool != null) {
            for (JsonElement e : pool) {
                if (!e.isJsonObject() || !J.bool(e.getAsJsonObject(), "enabled", true)) continue;
                JsonObject entry = e.getAsJsonObject();
                String assetId = J.str(entry, "biome", "");
                Asset b = project.get("biomes/" + assetId + ".json");
                if (b == null) { r.warnings.add("Biome '" + assetId + "' is in the pool but missing; skipped."); continue; }
                if (nextId > 250) { r.warnings.add("Too many biomes (limit ~190); '" + assetId + "' skipped."); continue; }
                String name = J.str(b.json, "name", "worldgenv2:" + assetId);
                nameToId.put(name, nextId);
                pending.add(new Object[]{nextId, assetId, b, entry});
                nextId++;
            }
        }
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(r.seed);
        for (Object[] p : pending) {
            int id = (Integer) p[0];
            String assetId = (String) p[1];
            Asset b = (Asset) p[2];
            JsonObject entry = (JsonObject) p[3];
            try {
                JsonAstCompiler.CompiledBiome<BlockState> cb = JsonAstCompiler.compileBiome(
                        id, b.json.toString(), ctx, nameToId,
                        blockId -> WorldGenV2ChunkGenerator.resolveBlockState(blockId, Blocks.STONE.defaultBlockState()));
                String category = J.str(b.json, "category", "plains");
                String mc = J.str(b.json, "minecraftBiome", defaultMinecraftBiome(category));
                r.biomes.add(new BiomeBinding(id, assetId, cb.name(), mc, category));
                r.biomeNameToAsset.put(cb.name(), assetId);
                if (cb.customBiome() != null) {
                    customs.add(cb.customBiome());
                } else if (cb.tileBiome() != null) {
                    TileBiome t = cb.tileBiome();
                    JsonObject climate = J.obj(b.json, "climate");
                    ClimateTarget ct = climate == null ? null : new ClimateTarget(
                            (float) J.num(climate, "continentalness", 0), (float) J.num(climate, "temperature", 0),
                            (float) J.num(climate, "humidity", 0));
                    tiles.add(new TileBiome(t.id(), t.name(),
                            (float) J.num(entry, "weight", t.weight()),
                            (float) J.num(entry, "size", t.sizeModifier()),
                            (float) J.num(entry, "transition", t.defaultTransitionDistance()),
                            t.terrainDensity(), t.materialProvider(), t.props(), ct,
                            t.defaultBlock(), t.fluidBlock(), t.seaLevel()));
                }
            } catch (Throwable ex) {
                r.warnings.add("Biome '" + assetId + "' failed to compile: " + ex.getMessage());
            }
        }
        if (tiles.isEmpty()) {
            r.warnings.add("No usable tile biomes in this dimension's pool - nothing to generate.");
            return r;
        }

        WorldStructure structure = new WorldStructure(tiles, customs, cellSize, r.seed);
        if (J.bool(d, "rivers", true)) {
            structure.withRiverCarver(new RiverCarver(r.seed, r.seaLevel, 6))
                    .withRiverMaterialProvider(DefaultBiomeFactory.createRiverMaterialProvider());
        }
        if (J.bool(d, "caves", true)) {
            structure.withCaveCarver(new CaveCarver(r.seed))
                    .withUndergroundBiomes(DefaultBiomeFactory.createUndergroundBiomes(), r.seed);
        }
        structure.withDimensionLayout(buildLayout(d, r));
        r.zones = on(d, "zones", nonEmpty(J.arr(d, "zones"))) ? buildZones(d, r, project) : null;
        if (r.zones != null) structure.withZoneLayout(r.zones);
        structure.withOreRules(resolveOres(d, r));

        // ---- spawn anchor ----
        JsonObject spawn = J.obj(d, "spawn");
        if (on(d, "spawn", spawn != null && J.bool(spawn, "enabled", false)) && spawn != null && !J.str(spawn, "prefab", "").isBlank()) {
            JsonArray off = J.arr(spawn, "offset");
            r.spawn = new SpawnAnchorManager.SpawnAnchorRule(
                    "spawn_anchor", J.str(spawn, "prefab", ""),
                    J.integer(spawn, "x", 0), J.integer(spawn, "z", 0),
                    (float) J.num(spawn, "craterRadius", 0), (float) J.num(spawn, "craterDepth", 0),
                    (float) J.num(spawn, "rimHeight", 0), J.bool(spawn, "lockSpawn", true),
                    off != null && off.size() > 0 ? off.get(0).getAsInt() : 0,
                    off != null && off.size() > 1 ? off.get(1).getAsInt() : 1,
                    off != null && off.size() > 2 ? off.get(2).getAsInt() : 0,
                    J.str(spawn, "scorch", "minecraft:stone"), J.str(spawn, "scorchSecondary", "minecraft:dirt"));
            structure.withSpawnAnchorManager(new SpawnAnchorManager(r.spawn));
            structure.withConditionalSpawn(new ConditionalSpawnProvider(
                    ConditionalSpawnProvider.LandmassType.CONTINENT, r.spawn.originX(), r.spawn.originZ(),
                    220f, 140f, Set.of(), r.seaLevel + 1, r.maxY, ConditionalSpawnProvider.FallbackStrategy.FORCE_GENERATION));
        }

        // ---- landmarks + roads ----
        JsonArray lms = on(d, "landmarks", nonEmpty(J.arr(d, "landmarks"))) ? J.arr(d, "landmarks") : null;
        if (lms != null && !lms.isEmpty()) {
            MacroFeatureGraph g = new MacroFeatureGraph();
            // The engine connects a child to a parent through a typed anchor the parent exports. Creators just pick
            // a parent and a side, so the anchors are synthesised here: one LINK_<child> anchor per child.
            Set<String> roots = new LinkedHashSet<>();
            boolean spawnParent = false;
            for (JsonElement e : lms) if (e.isJsonObject() && "spawn".equals(J.str(e.getAsJsonObject(), "parent", ""))) spawnParent = true;
            if (spawnParent) {
                if (r.spawn == null) {
                    r.warnings.add("Landmarks are connected to the spawn structure, but the spawn structure is not enabled.");
                } else {
                    if (Math.abs(r.spawn.originX()) > 16 || Math.abs(r.spawn.originZ()) > 16) {
                        r.warnings.add("Connecting to the spawn structure works best with spawn near 0, 0; it is at "
                                + r.spawn.originX() + ", " + r.spawn.originZ() + ".");
                    }
                    roots.add("spawn");
                }
            }
            Map<String, List<JsonObject>> childrenOf = new LinkedHashMap<>();
            for (JsonElement e : lms) {
                if (!e.isJsonObject()) continue;
                JsonObject l = e.getAsJsonObject();
                String id = J.str(l, "id", ""), parent = J.str(l, "parent", "");
                if (id.isBlank()) continue;
                if (parent.isBlank()) roots.add(id);
                else childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(l);
            }
            if (roots.contains("spawn")) {
                // the spawn structure takes part in the graph as a root landmark pinned to the origin
                List<MacroFeatureGraph.MacroAnchorDef> anchors = new ArrayList<>();
                List<JsonObject> kids = childrenOf.getOrDefault("spawn", List.of());
                for (int i = 0; i < kids.size(); i++) {
                    JsonObject kid = kids.get(i);
                    float[] dir = direction(J.str(kid, "direction", "auto"), i, kids.size());
                    anchors.add(new MacroFeatureGraph.MacroAnchorDef("to_" + J.str(kid, "id", "child"), "LINK_" + J.str(kid, "id", "child"), dir[0], dir[1]));
                }
                g.addPrimaryLandmark(new MacroFeatureGraph.PrimaryLandmarkRule("spawn", r.spawn.prefabId(), Set.of(), 0f, 1f, anchors));
            }
            for (JsonElement e : lms) {
                if (!e.isJsonObject()) continue;
                JsonObject l = e.getAsJsonObject();
                String id = J.str(l, "id", ""), parent = J.str(l, "parent", "");
                if (id.isBlank()) continue;
                Set<String> allowed = biomeNames(l, r);
                if (parent.isBlank()) {
                    List<MacroFeatureGraph.MacroAnchorDef> anchors = new ArrayList<>();
                    List<JsonObject> kids = childrenOf.getOrDefault(id, List.of());
                    for (int i = 0; i < kids.size(); i++) {
                        JsonObject kid = kids.get(i);
                        float[] dir = direction(J.str(kid, "direction", "auto"), i, kids.size());
                        anchors.add(new MacroFeatureGraph.MacroAnchorDef("to_" + J.str(kid, "id", "child"),
                                "LINK_" + J.str(kid, "id", "child"), dir[0], dir[1]));
                    }
                    g.addPrimaryLandmark(new MacroFeatureGraph.PrimaryLandmarkRule(id, J.str(l, "prefab", ""), allowed,
                            (float) J.num(l, "minDistance", 48), (float) J.num(l, "maxDistance", 140), anchors));
                } else if (!roots.contains(parent)) {
                    r.warnings.add("Landmark '" + id + "' is placed from '" + parent + "', which is not a root landmark; skipped.");
                } else {
                    g.addRelationalLandmark(new MacroFeatureGraph.RelationalLandmarkRule(id, parent,
                            "LINK_" + id, J.str(l, "prefab", ""), allowed,
                            (float) J.num(l, "minDistance", 120), (float) J.num(l, "maxDistance", 220),
                            J.bool(l, "faceParent", true), J.bool(l, "road", true),
                            (float) J.num(l, "roadWidth", 5), (float) J.num(l, "shoulderWidth", 3)));
                }
            }
            try {
                g.buildGraph(structure, r.minY, r.maxY, r.seaLevel, r.seed);
                SplineRoadEngine roads = new SplineRoadEngine();
                roads.buildFromMacroGraph(g, structure, r.minY, r.maxY, r.seaLevel);
                structure.withMacroFeatureGraph(g).withSplineRoadEngine(roads);
            } catch (Throwable ex) {
                r.warnings.add("Landmark graph failed: " + ex.getMessage());
            }
        }

        // ---- finite budgeted structures ----
        JsonArray budgets = on(d, "budgets", nonEmpty(J.arr(d, "budgets"))) ? J.arr(d, "budgets") : null;
        if (budgets != null && !budgets.isEmpty()) {
            StructureBudgetManager bm = new StructureBudgetManager();
            for (JsonElement e : budgets) {
                if (!e.isJsonObject()) continue;
                JsonObject b = e.getAsJsonObject();
                String id = J.str(b, "id", "");
                if (id.isBlank()) continue;
                bm.addRule(new StructureBudgetManager.BudgetedFeatureRule(id, J.str(b, "prefab", ""),
                        (float) J.num(b, "minRadius", 0), (float) J.num(b, "maxRadius", 1000),
                        (float) J.num(b, "separation", 128), J.integer(b, "minQuota", 1), J.integer(b, "maxQuota", 1),
                        biomeNames(b, r), J.str(b, "loot", null)));
            }
            try {
                bm.buildBudgets(structure, r.minY, r.maxY, r.seaLevel, r.seed);
                structure.withStructureBudgetManager(bm);
            } catch (Throwable ex) {
                r.warnings.add("Structure budgets failed: " + ex.getMessage());
            }
        }

        // ---- stamp list: what the chunk generator will actually build ----
        Map<String, com.worldgenv2.prefab.Prefab> prefabs = new HashMap<>();
        List<StructurePlacement> placements = new ArrayList<>();
        try {
            if (r.spawn != null) {
                var pf = loadPrefab(project, r.spawn.prefabId(), prefabs, r);
                if (pf != null) {
                    int sy = structure.sampleSurfaceY(r.spawn.originX(), r.spawn.originZ(), r.minY, r.maxY) + 1;
                    placements.add(new StructurePlacement("spawn", pf, r.spawn.originX(), sy, r.spawn.originZ()));
                }
            }
            MacroFeatureGraph graph = structure.getMacroFeatureGraph();
            if (graph != null) {
                for (MacroFeatureGraph.PlacedLandmark l : graph.getPlacedLandmarks().values()) {
                    if (l.id().equals("spawn")) continue; // the spawn structure is stamped above
                    var pf = loadPrefab(project, l.prefabId(), prefabs, r);
                    if (pf != null) placements.add(new StructurePlacement(l.id(), pf, l.x(), l.y(), l.z()));
                }
            }
            StructureBudgetManager bm = structure.getStructureBudgetManager();
            if (bm != null) {
                int n = 0;
                for (StructureBudgetManager.PlacedBudgetedFeature f : bm.getPlacedFeatures()) {
                    var pf = loadPrefab(project, f.prefabId(), prefabs, r);
                    if (pf != null) placements.add(new StructurePlacement(f.ruleId() + "_" + (n++), pf, f.x(), f.y(), f.z()));
                }
            }
        } catch (Throwable ex) {
            r.warnings.add("Placing structures failed: " + ex.getMessage());
        }
        structure.withStructurePlacements(placements);

        r.structure = structure;
        WorldGenV2Addons.runCompileHooks(project, r);
        return r;
    }

    private static com.worldgenv2.prefab.Prefab loadPrefab(StudioProject project, String id, Map<String, com.worldgenv2.prefab.Prefab> cache, Result r) {
        if (id == null || id.isBlank()) return null;
        if (cache.containsKey(id)) return cache.get(id);
        com.worldgenv2.prefab.Prefab pf = null;
        Asset a = project.get("prefabs/" + id + ".json");
        if (a == null) {
            r.warnings.add("Prefab '" + id + "' does not exist in this project; nothing is placed for it.");
        } else {
            try {
                pf = new com.worldgenv2.prefab.PrefabLoader().parseJson(a.json.toString());
            } catch (Throwable ex) {
                r.warnings.add("Prefab '" + id + "' could not be read: " + ex.getMessage());
            }
        }
        cache.put(id, pf);
        return pf;
    }

    /** Unit vector for a compass side; "auto" spreads children evenly around the parent starting north. */
    private static float[] direction(String side, int index, int total) {
        return switch (side.toUpperCase(Locale.ROOT)) {
            case "N" -> new float[]{0, -1};
            case "NE" -> new float[]{1, -1};
            case "E" -> new float[]{1, 0};
            case "SE" -> new float[]{1, 1};
            case "S" -> new float[]{0, 1};
            case "SW" -> new float[]{-1, 1};
            case "W" -> new float[]{-1, 0};
            case "NW" -> new float[]{-1, -1};
            default -> {
                double a = -Math.PI / 2 + 2 * Math.PI * index / Math.max(1, total);
                yield new float[]{(float) Math.cos(a), (float) Math.sin(a)};
            }
        };
    }

    /** Biome asset ids in a rule's {@code biomes} array -> the compiled biome names the engine matches on. */
    private static Set<String> biomeNames(JsonObject rule, Result r) {
        Set<String> out = new LinkedHashSet<>();
        JsonArray arr = J.arr(rule, "biomes");
        if (arr != null) {
            for (JsonElement e : arr) {
                String assetId = e.getAsString();
                for (BiomeBinding b : r.biomes) if (b.assetId().equals(assetId)) out.add(b.name());
            }
        }
        // A rule can also be restricted to a zone; combined with an explicit biome list it must satisfy both.
        String zoneId = J.str(rule, "zone", "");
        if (!zoneId.isBlank()) {
            Set<String> zoneNames = null;
            if (r.zones != null) {
                for (ZoneLayout.Zone z : r.zones.zones()) if (z.id.equals(zoneId)) zoneNames = new LinkedHashSet<>(z.biomeNames);
            }
            if (zoneNames == null) {
                r.warnings.add("'" + J.str(rule, "id", "?") + "' is limited to zone '" + zoneId + "', which does not exist.");
            } else if (out.isEmpty()) {
                out = zoneNames;
            } else {
                out.retainAll(zoneNames);
            }
        }
        return out;
    }

    private static int parseColor(String hex, int fallback) {
        try {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            if (h.length() == 6) return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }

    private static final int[] ZONE_COLORS = {0xFF5FBF6A, 0xFFE3B341, 0xFF9FD8F0, 0xFFE5534B, 0xFFB37BE8, 0xFF3FC5FF, 0xFFE89A3C, 0xFF8BC34A};

    /** Builds the Hytale-style zone layer from the dimension's {@code zones} array. */
    private static ZoneLayout buildZones(JsonObject d, Result r, StudioProject project) {
        JsonArray za = J.arr(d, "zones");
        if (za == null || za.isEmpty()) return null;
        ZoneLayout.Builder zb = new ZoneLayout.Builder(r.seed, (float) J.num(d, "zoneScale", 2000));
        int i = 0;
        for (JsonElement e : za) {
            JsonObject z;
            String id;
            if (e.isJsonPrimitive()) {
                // a zone asset referenced by id (zones/<id>.json)
                id = e.getAsString();
                Asset zoneAsset = project.get("zones/" + id + ".json");
                if (zoneAsset == null) { r.warnings.add("Zone '" + id + "' is listed but does not exist."); continue; }
                z = zoneAsset.json;
            } else if (e.isJsonObject()) {
                z = e.getAsJsonObject();
                id = J.str(z, "id", "zone_" + i);
            } else {
                continue;
            }
            List<String> names = new ArrayList<>();
            JsonArray ba = J.arr(z, "biomes");
            if (ba != null) {
                for (JsonElement be : ba) {
                    String assetId = be.getAsString();
                    boolean found = false;
                    for (BiomeBinding b : r.biomes) if (b.assetId().equals(assetId)) { names.add(b.name()); found = true; }
                    if (!found) r.warnings.add("Zone '" + id + "' lists biome '" + assetId + "', which is not in this dimension's biome pool.");
                }
            }
            if (names.isEmpty() && !J.str(z, "kind", "everywhere").equals("everywhere")) {
                r.warnings.add("Zone '" + id + "' has no biomes, so it does not restrict anything.");
            }
            String kind = J.str(z, "kind", "everywhere");
            int color = parseColor(J.str(z, "color", ""), ZONE_COLORS[i % ZONE_COLORS.length]);
            // The zone's own terrain graph (optional): extra density added inside the zone.
            com.worldgenv2.density.DensityFunction terrain = null;
            JsonObject terrainAst = J.obj(z, "terrain");
            if (terrainAst != null && !terrainAst.isEmpty()) {
                try {
                    terrain = new JsonAstCompiler().compileDensityJson(terrainAst.toString(), r.seed);
                } catch (Throwable ex) {
                    r.warnings.add("Zone '" + id + "' terrain graph failed to compile: " + ex.getMessage());
                }
            }
            float strength = (float) J.num(z, "terrainStrength", 1.0);
            if (kind.equals("graph")) {
                // The zone's shape is a density graph evaluated on the ground plane: inside where min <= value <= max.
                JsonObject maskAst = J.obj(z, "mask");
                if (maskAst == null || maskAst.isEmpty()) {
                    r.warnings.add("Zone '" + id + "' is shaped by a graph but has no mask graph yet; it matches nowhere.");
                    zb.addCustom(id, J.str(z, "name", id), color, (x, zz) -> false, names, terrain, strength);
                } else {
                    try {
                        com.worldgenv2.density.DensityFunction mask = new JsonAstCompiler().compileDensityJson(maskAst.toString(), r.seed);
                        float lo = (float) J.num(z, "min", 0.0), hi = (float) J.num(z, "max", 1.0), soft = (float) Math.max(0.0001, J.num(z, "softness", 0.15));
                        zb.addCustom(id, J.str(z, "name", id), color, new ZoneLayout.Mask() {
                            @Override public boolean matches(float x, float zz) {
                                float v = mask.compute(x, 64.0f, zz);
                                return v >= lo && v <= hi;
                            }
                            @Override public float weight(float x, float zz) {
                                float v = mask.compute(x, 64.0f, zz);
                                float a = Math.min(1.0f, Math.max(0.0f, (v - lo) / soft));
                                float b = Math.min(1.0f, Math.max(0.0f, (hi - v) / soft));
                                return a * b;
                            }
                        }, names, terrain, strength);
                    } catch (Throwable ex) {
                        r.warnings.add("Zone '" + id + "' mask graph failed to compile: " + ex.getMessage());
                        zb.addCustom(id, J.str(z, "name", id), color, (x, zz) -> false, names, terrain, strength);
                    }
                }
            } else {
                zb.add(id, J.str(z, "name", id), color, kind, z, names);
                if (terrain != null) r.warnings.add("Zone '" + id + "' has a terrain graph, which only applies to graph-shaped zones; set its shape to Graph.");
            }
            i++;
        }
        return zb.build();
    }

    // ---- feature modes: every optional feature can be Off, Default (built in) or Custom (the project's own data) ----

    private static boolean nonEmpty(JsonArray a) { return a != null && !a.isEmpty(); }

    /** Explicit {@code features.<name>} wins ("off" disables); otherwise a feature is on when it has data. */
    public static boolean on(JsonObject dim, String feature, boolean inferred) {
        JsonObject f = J.obj(dim, "features");
        String m = f == null ? "" : J.str(f, feature, "");
        return switch (m) {
            case "off" -> false;
            case "custom", "default" -> true;
            default -> inferred;
        };
    }

    /** {@code off}, {@code default} or {@code custom} for ores. A project with no ore data uses the built-in default. */
    public static String oresMode(JsonObject dim) {
        JsonObject f = J.obj(dim, "features");
        String m = f == null ? "" : J.str(f, "ores", "");
        if (m.equals("off") || m.equals("default") || m.equals("custom")) return m;
        return dim.has("ores") ? "custom" : "default";
    }

    private static List<OreRule> resolveOres(JsonObject d, Result r) {
        return switch (oresMode(d)) {
            case "off" -> List.of();
            case "default" -> {
                JsonObject tmp = new JsonObject();
                tmp.add("ores", WorldGenV2Addons.defaultOres());
                yield buildOres(tmp, r);
            }
            default -> buildOres(d, r);
        };
    }

    private static List<OreRule> buildOres(JsonObject d, Result r) {
        List<OreRule> out = new ArrayList<>();
        JsonArray oa = J.arr(d, "ores");
        if (oa == null) return out;
        for (JsonElement e : oa) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String id = J.str(o, "id", "");
            String block = J.str(o, "block", "");
            if (id.isBlank() || block.isBlank()) continue;
            if (WorldGenV2ChunkGenerator.resolveBlockState(block, null) == null) {
                r.warnings.add("Ore '" + id + "' uses unknown block '" + block + "'; skipped.");
                continue;
            }
            List<String> replaces = new ArrayList<>();
            for (String s : J.str(o, "replaces", "").split(",")) if (!s.isBlank()) replaces.add(s.trim());
            out.add(new OreRule(id, block, replaces, J.integer(o, "veinSize", 8), J.integer(o, "count", 10),
                    J.integer(o, "minY", r.minY), J.integer(o, "maxY", r.maxY),
                    "triangle".equals(J.str(o, "distribution", "uniform")), biomeNames(o, r)));
        }
        return out;
    }

    private static DimensionLayoutConfig buildLayout(JsonObject d, Result r) {
        JsonObject bounds = J.obj(d, "bounds");
        float radius = (float) J.num(bounds, "radius", 0);
        float falloff = (float) J.num(bounds, "falloff", 64);
        OutsideBoundsFallback outside = parseEnum(OutsideBoundsFallback.class, J.str(bounds, "outside", "DEFAULT_BIOMES"), OutsideBoundsFallback.DEFAULT_BIOMES);

        List<RadialZoneRule> zones = new ArrayList<>();
        JsonArray za = on(d, "shapes", nonEmpty(J.arr(d, "shapes"))) ? J.arr(d, "shapes") : null;
        if (za != null) for (JsonElement e : za) {
            if (!e.isJsonObject()) continue;
            JsonObject z = e.getAsJsonObject();
            String biome = J.str(z, "biome", "");
            String biomeName = biome;
            for (BiomeBinding b : r.biomes) if (b.assetId().equals(biome)) biomeName = b.name();
            zones.add(new RadialZoneRule(J.str(z, "id", "zone"), (float) J.num(z, "centerX", 0), (float) J.num(z, "centerZ", 0),
                    (float) J.num(z, "inner", 0), (float) J.num(z, "outer", 200), (float) J.num(z, "falloff", 32),
                    parseEnum(ZoneMode.class, J.str(z, "mode", "BIOME_OVERRIDE"), ZoneMode.BIOME_OVERRIDE),
                    biomeName, (float) J.num(z, "elevation", 70), (float) J.num(z, "thickness", 32), J.bool(z, "floating", false)));
        }
        List<RingLandmarkRule> rings = new ArrayList<>();
        JsonArray ra = on(d, "rings", nonEmpty(J.arr(d, "rings"))) ? J.arr(d, "rings") : null;
        if (ra != null) for (JsonElement e : ra) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            rings.add(new RingLandmarkRule(J.str(o, "id", "ring"), (float) J.num(o, "centerX", 0), (float) J.num(o, "centerZ", 0),
                    (float) J.num(o, "radius", 40), J.integer(o, "count", 8), J.integer(o, "pillarRadius", 3),
                    J.integer(o, "minHeight", 70), J.integer(o, "maxHeight", 100), J.str(o, "block", "minecraft:obsidian"),
                    J.str(o, "cap", "minecraft:bedrock")));
        }
        return new DimensionLayoutConfig(J.str(d, "layout", "custom"), radius, falloff, outside, r.seaLevel,
                J.str(d, "defaultBlock", "minecraft:stone"), J.str(d, "defaultFluid", "minecraft:water"),
                zones, rings, List.of(), r.seed);
    }

    static <E extends Enum<E>> E parseEnum(Class<E> c, String v, E def) {
        try { return Enum.valueOf(c, v.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { return def; }
    }
}
