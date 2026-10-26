package com.worldgenv2.studio.tabs;

import com.worldgenv2.studio.StudioLiveCompiler;
import com.worldgenv2.studio.StudioNodeCatalog;
import com.worldgenv2.studio.StudioNodeCatalog.NodeSchema;
import com.worldgenv2.studio.StudioNodeCatalog.ParamDef;
import com.worldgenv2.studio.StudioNodeCatalog.ParamType;
import com.worldgenv2.studio.StudioNodeCatalog.SocketDef;
import com.worldgenv2.studio.StudioNodeCatalog.SocketDomain;
import com.worldgenv2.studio.StudioState;
import com.worldgenv2.studio.StudioTheme;
import com.worldgenv2.studio.widgets.VanillaDropdownWidget;
import com.worldgenv2.studio.widgets.VisualBlockPickerModal;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.ZoneMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Interactive Visual Node Editor Canvas Tab.
 * <ul>
 *   <li>Renders all cards, sockets, editable parameter fields, and Bezier wires using
 *       {@link GuiGraphicsExtractor#pose()} ({@code Matrix3x2fStack}) so text and cards scale uniformly without overflow.</li>
 *   <li>Supports inline editable fields on every node card (click-drag numeric scrubbing, direct keyboard typing,
 *       -/+ step buttons, enum cycling, boolean toggles, and visual Block Picker integration).</li>
 *   <li>Supports full interactive socket wiring: click-drag from any socket to connect, click-drag a connected
 *       input socket to detach/rewire it, or right-click any socket to unattach wires.</li>
 *   <li>Double-clicking anywhere on the canvas (or clicking [+ Add Node]) opens the Smart Search Drawer
 *       populated with all ~295 registered AST node types across all 24 categories.</li>
 * </ul>
 */
public class NodeCanvasTab {

    public static final class ParamInstance {
        public final ParamDef def;
        public String value;

        public ParamInstance(ParamDef def) {
            this.def = def;
            this.value = def.defaultValue();
        }

        public double asDouble() {
            try {
                return Double.parseDouble(value);
            } catch (Exception e) {
                return 0.0;
            }
        }

        public int asInt() {
            try {
                return (int) Math.round(Double.parseDouble(value));
            } catch (Exception e) {
                return 0;
            }
        }

        public boolean asBoolean() {
            return Boolean.parseBoolean(value);
        }

        public void setDouble(double v) {
            double clamped = Math.max(def.min(), Math.min(def.max(), v));
            if (def.type() == ParamType.INT) {
                this.value = String.valueOf((int) Math.round(clamped));
            } else {
                // Format cleanly to avoid float precision noise
                if (def.step() < 0.01) {
                    this.value = String.format(Locale.ROOT, "%.4f", clamped).replaceAll("0+$", "").replaceAll("\\.$", ".0");
                } else if (def.step() < 0.1) {
                    this.value = String.format(Locale.ROOT, "%.3f", clamped).replaceAll("0+$", "").replaceAll("\\.$", ".0");
                } else {
                    this.value = String.format(Locale.ROOT, "%.2f", clamped).replaceAll("0+$", "").replaceAll("\\.$", ".0");
                }
            }
        }

        public void stepBy(int dir) {
            if (def.type() == ParamType.INT) {
                int step = Math.max(1, (int) Math.round(def.step()));
                setDouble(asInt() + dir * step);
            } else if (def.type() == ParamType.FLOAT) {
                setDouble(asDouble() + dir * def.step());
            } else if (def.type() == ParamType.ENUM && def.enumOptions() != null && def.enumOptions().length > 0) {
                String[] opts = def.enumOptions();
                int idx = 0;
                for (int i = 0; i < opts.length; i++) {
                    if (opts[i].equalsIgnoreCase(value)) {
                        idx = i;
                        break;
                    }
                }
                int next = (idx + dir + opts.length) % opts.length;
                this.value = opts[next];
            } else if (def.type() == ParamType.BOOLEAN) {
                this.value = String.valueOf(!asBoolean());
            }
        }
    }

    public static final class NodeCard {
        public final String id;
        public final NodeSchema schema;
        public String title;
        public double canvasX;
        public double canvasY;
        public int cardWidth = 168;
        public int cardHeight = 62;
        public final List<SocketDef> inputs;
        public final List<SocketDef> outputs;
        public final List<ParamInstance> params = new ArrayList<>();

        public static final int HEADER_H = 14;
        public static final int SOCKET_ROW_H = 12;
        public static final int PARAM_ROW_H = 13;

        public NodeCard(String id, NodeSchema schema, double x, double y) {
            this.id = id;
            this.schema = schema;
            this.title = schema.displayName();
            this.canvasX = x;
            this.canvasY = y;
            this.inputs = new ArrayList<>(schema.inputs());
            this.outputs = new ArrayList<>(schema.outputs());
            for (ParamDef p : schema.params()) {
                this.params.add(new ParamInstance(p));
            }
            recalculateLayout();
        }

        public void recalculateLayout() {
            int socketRows = Math.max(inputs.size(), outputs.size());
            int socketsH = socketRows * SOCKET_ROW_H + (socketRows > 0 ? 3 : 0);
            int paramsH = params.size() * PARAM_ROW_H + (params.isEmpty() ? 0 : 4);
            this.cardWidth = 168;
            this.cardHeight = Math.max(30, HEADER_H + socketsH + paramsH + 4);
        }

        public int getInputSocketY(int index) {
            return (int) canvasY + HEADER_H + 6 + index * SOCKET_ROW_H;
        }

        public int getOutputSocketY(int index) {
            return (int) canvasY + HEADER_H + 6 + index * SOCKET_ROW_H;
        }

        public int getParamRowY(int paramIndex) {
            int socketRows = Math.max(inputs.size(), outputs.size());
            int socketsH = socketRows * SOCKET_ROW_H + (socketRows > 0 ? 3 : 0);
            return (int) canvasY + HEADER_H + socketsH + 2 + paramIndex * PARAM_ROW_H;
        }

        public void setParam(String key, String val) {
            for (ParamInstance pi : params) {
                if (pi.def.key().equalsIgnoreCase(key)) {
                    pi.value = val;
                    return;
                }
            }
        }
    }

    public static final class WireConnection {
        public String fromNodeId;
        public String fromSocketId;
        public String toNodeId;
        public String toSocketId;
        public int color;

        public WireConnection(String fromNodeId, String fromSocketId, String toNodeId, String toSocketId, int color) {
            this.fromNodeId = fromNodeId;
            this.fromSocketId = fromSocketId;
            this.toNodeId = toNodeId;
            this.toSocketId = toSocketId;
            this.color = color;
        }
    }

    private int x;
    private int y;
    private int width;
    private int height;

    private final VisualBlockPickerModal blockPicker;
    private final List<NodeCard> nodes = new ArrayList<>();
    private final List<WireConnection> wires = new ArrayList<>();
    private int nodeCounter = 1;

    // Selection & Active Field Editing
    private NodeCard selectedNode = null;
    private ParamInstance editingParam = null;
    private String editingBuffer = "";
    private ParamInstance scrubbingParam = null;
    private double scrubStartX = 0;
    private double scrubStartVal = 0;

    // Interactive Wire Dragging State
    private boolean isDraggingWire = false;
    private boolean wireDragFromOutput = true; // true = dragging from an output socket to an input; false = dragging from an input socket to an output
    private NodeCard wireSourceNode = null;
    private SocketDef wireSourceSocket = null;
    private double wireMouseCanvasX = 0;
    private double wireMouseCanvasY = 0;

    // Node & Canvas Dragging State
    private NodeCard draggedNode = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean isPanning = false;
    private double panStartX = 0;
    private double panStartY = 0;

    // Smart Search Drawer State (All ~295 nodes)
    private boolean searchDrawerOpen = false;
    private int searchDrawerX = 0;
    private int searchDrawerY = 0;
    private double spawnTargetCanvasX = 100;
    private double spawnTargetCanvasY = 100;
    private String searchFilter = "";
    private int selectedCategoryIdx = 0; // 0 = "All"
    private final List<NodeSchema> filteredCatalog = new ArrayList<>();
    private int searchScroll = 0;

    // Preset Graph Dropdown Selector
    private final VanillaDropdownWidget<Integer> presetDropdown;
    private int activePresetIdx = 0;
    private long lastLiveApplyMs = 0L;

    // Structure / Prefab Picker Modal State
    public record StructureCatalogEntry(String id, String displayName, String category, String description, ItemStack icon) {}

    public static final List<StructureCatalogEntry> STRUCTURE_CATALOG = List.of(
            new StructureCatalogEntry("worldgenv2:watchtower_01", "Watchtower Outpost", "WorldGen V2", "Stone brick fortification tower with beacon braziers & road outlet.", new ItemStack(Blocks.STONE_BRICKS)),
            new StructureCatalogEntry("worldgenv2:spawn_shrine", "Starter Spawn Shrine", "WorldGen V2", "Central island spawn monument with 8 radial bridge outlets.", new ItemStack(Blocks.LODESTONE)),
            new StructureCatalogEntry("worldgenv2:ancient_shrine", "Ancient Ruined Shrine", "WorldGen V2", "Mossy temple ruins ideal for satellite islands & forest clearings.", new ItemStack(Blocks.MOSSY_STONE_BRICKS)),
            new StructureCatalogEntry("worldgenv2:waystation_inn", "Roadside Waystation", "WorldGen V2", "Timber & cobblestone traveler's inn connected via spline road.", new ItemStack(Blocks.LANTERN)),
            new StructureCatalogEntry("worldgenv2:ruined_keep", "Ruined Citadel Keep", "WorldGen V2", "Large multi-tower fortress landmark for outer island rings.", new ItemStack(Blocks.CRACKED_STONE_BRICKS)),
            new StructureCatalogEntry("minecraft:end_city", "End City & Ship", "End & Nether", "Purpur & end stone sky-tower complex for outer End islands.", new ItemStack(Blocks.PURPUR_BLOCK)),
            new StructureCatalogEntry("minecraft:bastion_remnant", "Bastion Remnant", "End & Nether", "Blackstone fortress with gold vault chambers.", new ItemStack(Blocks.GILDED_BLACKSTONE)),
            new StructureCatalogEntry("minecraft:nether_fortress", "Nether Fortress", "End & Nether", "Nether brick bridge & blaze spawner citadel.", new ItemStack(Blocks.NETHER_BRICKS)),
            new StructureCatalogEntry("minecraft:pillager_outpost", "Pillager Outpost", "Overworld", "Dark oak watchtower with tents and cages.", new ItemStack(Blocks.DARK_OAK_LOG)),
            new StructureCatalogEntry("minecraft:village_plains", "Plains Village", "Overworld", "Oak & cobblestone settlement with bell square.", new ItemStack(Blocks.BELL)),
            new StructureCatalogEntry("minecraft:village_taiga", "Taiga Spruce Village", "Overworld", "Spruce & mossy cobblestone woodland village.", new ItemStack(Blocks.SPRUCE_LOG)),
            new StructureCatalogEntry("minecraft:desert_pyramid", "Desert Temple Pyramid", "Overworld", "Sandstone pyramid with hidden subterranean treasure chamber.", new ItemStack(Blocks.CHISELED_SANDSTONE)),
            new StructureCatalogEntry("minecraft:jungle_pyramid", "Jungle Temple", "Overworld", "Mossy cobblestone puzzle temple in dense jungle.", new ItemStack(Blocks.MOSSY_COBBLESTONE)),
            new StructureCatalogEntry("minecraft:trial_chambers", "Trial Chambers", "Overworld", "Copper & tuff underground combat vault complex.", new ItemStack(Blocks.CHISELED_COPPER)),
            new StructureCatalogEntry("minecraft:ancient_city", "Ancient Deep Dark City", "Overworld", "Deepslate & sculk subterranean metropolis.", new ItemStack(Blocks.SCULK_SHRIEKER)),
            new StructureCatalogEntry("minecraft:woodland_mansion", "Woodland Mansion", "Overworld", "Massive dark forest estate landmark.", new ItemStack(Blocks.DARK_OAK_PLANKS)),
            new StructureCatalogEntry("minecraft:monument", "Ocean Monument", "Overworld", "Prismarine sea temple for ocean moat or pelagic zones.", new ItemStack(Blocks.PRISMARINE_BRICKS)),
            new StructureCatalogEntry("minecraft:swamp_hut", "Swamp Witch Hut", "Overworld", "Stilted spruce cabin for wetland & marsh landmarks.", new ItemStack(Blocks.CAULDRON)),
            new StructureCatalogEntry("minecraft:igloo", "Snowy Igloo", "Overworld", "Snow shelter with optional subterranean lab.", new ItemStack(Blocks.SNOW_BLOCK)),
            new StructureCatalogEntry("oak_tree_1", "Ancient Great Oak", "Trees & Flora", "Procedural canopy oak landmark tree.", new ItemStack(Blocks.OAK_SAPLING)),
            new StructureCatalogEntry("pine_spruce_1", "Tall Alpine Pine", "Trees & Flora", "Towering coniferous pine landmark.", new ItemStack(Blocks.SPRUCE_SAPLING))
    );

    private boolean structurePickerOpen = false;
    private String structureSearch = "";
    private int structureScroll = 0;
    private java.util.function.Consumer<String> structureSelectCallback = null;

    // Top-Down Dimension Faux-Minimap Zoom Radius (in blocks)
    private float fauxMinimapRadius = 700.0f;
    private boolean showFauxMinimap = true;

    public NodeCanvasTab(VisualBlockPickerModal blockPicker) {
        this.blockPicker = blockPicker;
        this.presetDropdown = new VanillaDropdownWidget<>(96, 6, 148, 18);
        this.presetDropdown.addOption("chunk_biome", Component.literal("Active Biome Graph"), new ItemStack(Blocks.GRASS_BLOCK), -1);
        this.presetDropdown.addOption("macro_spawn", Component.literal("§6Macro: Spawn Island & Landmarks"), new ItemStack(Blocks.LODESTONE), 10);
        this.presetDropdown.addOption("macro_end", Component.literal("§dMacro: The End Rings & Towers"), new ItemStack(Blocks.END_STONE), 11);
        this.presetDropdown.addOption("macro_skyblock", Component.literal("§bMacro: Bounded Skyblock (Void)"), new ItemStack(Blocks.AMETHYST_BLOCK), 12);
        this.presetDropdown.addOption("preset_0", Component.literal("Biome: Mountain Overhangs"), new ItemStack(Blocks.STONE), 0);
        this.presetDropdown.addOption("preset_1", Component.literal("Biome: Swiss Cheese Caves"), new ItemStack(Blocks.DEEPSLATE), 1);
        this.presetDropdown.addOption("preset_2", Component.literal("Biome: Terraced Mesa"), new ItemStack(Blocks.TERRACOTTA), 2);
        this.presetDropdown.addOption("preset_3", Component.literal("Biome: Flora & Trees"), new ItemStack(Blocks.OAK_SAPLING), 3);
        this.presetDropdown.setSelectedById("chunk_biome");
        this.presetDropdown.setOnSelect(opt -> {
            int val = opt.value();
            if (val < 0) {
                StudioState.get().setGraphMode(StudioState.GraphMode.BIOME_GRAPH);
                StudioLiveCompiler.loadBiomeIntoCanvas(StudioState.get().getActiveBiome(), this);
            } else if (val >= 10) {
                StudioState.get().setGraphMode(StudioState.GraphMode.DIMENSION_GRAPH);
                loadDimensionMacroPreset(val - 10);
                StudioLiveCompiler.compileAndApplyLive(this);
            } else {
                StudioState.get().setGraphMode(StudioState.GraphMode.BIOME_GRAPH);
                loadPresetGraph(val);
                StudioLiveCompiler.compileAndApplyLive(this);
            }
        });

        updateCatalogFilter();
        StudioLiveCompiler.loadBiomeIntoCanvas(StudioState.get().getActiveBiome(), this);
    }

    public List<NodeCard> getNodes() {
        return nodes;
    }

    public List<WireConnection> getWires() {
        return wires;
    }

    public void clearCanvas() {
        nodes.clear();
        wires.clear();
        selectedNode = null;
        editingParam = null;
    }

    public void connectNodes(NodeCard from, String fromSock, NodeCard to, String toSock) {
        connectSockets(from, fromSock, to, toSock);
    }

    public String getParamValue(NodeCard card, String key, String defaultVal) {
        if (card == null) return defaultVal;
        for (ParamInstance pi : card.params) {
            if (pi.def.key().equalsIgnoreCase(key)) {
                return pi.value;
            }
        }
        return defaultVal;
    }

    public double getParamDouble(NodeCard card, String key, double defaultVal) {
        if (card == null) return defaultVal;
        for (ParamInstance pi : card.params) {
            if (pi.def.key().equalsIgnoreCase(key)) {
                return pi.asDouble();
            }
        }
        return defaultVal;
    }

    public int getParamInt(NodeCard card, String key, int defaultVal) {
        if (card == null) return defaultVal;
        for (ParamInstance pi : card.params) {
            if (pi.def.key().equalsIgnoreCase(key)) {
                return pi.asInt();
            }
        }
        return defaultVal;
    }

    public void setPresetDropdownToBiome() {
        this.presetDropdown.setSelectedById("chunk_biome");
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void loadPresetGraph(int presetIdx) {
        this.activePresetIdx = ((presetIdx % 4) + 4) % 4;
        this.presetDropdown.setSelectedById("preset_" + this.activePresetIdx);
        nodes.clear();
        wires.clear();
        selectedNode = null;
        editingParam = null;

        StudioState.get().setCanvasPanX(20.0);
        StudioState.get().setCanvasPanY(34.0);
        StudioState.get().setCanvasZoom(1.0);

        if (this.activePresetIdx == 0) {
            // Preset 0: Mountain Overhangs (Gravity + 3D Simplex + Gradient Warp -> Sum -> SpaceAndDepth -> BiomeRoot)
            NodeCard grav = spawnNode("Density:VerticalGradientDensity", 12, 10);
            grav.setParam("baseHeight", "72.0");
            grav.setParam("scale", "36.0");

            NodeCard noise3d = spawnNode("Density:SimplexNoise3D", 12, 100);
            noise3d.setParam("frequency", "0.008");
            noise3d.setParam("octaves", "4");
            noise3d.setParam("amplitude", "1.4");

            NodeCard warp = spawnNode("Density:GradientWarpDensity", 190, 95);
            warp.setParam("warpStrength", "18.0");

            NodeCard sum = spawnNode("Density:SumDensity", 190, 10);

            NodeCard strata = spawnNode("Material:SpaceAndDepthMaterialProvider", 368, 10);
            strata.setParam("surfaceBlock", "minecraft:grass_block");
            strata.setParam("subsoilBlock", "minecraft:dirt");
            strata.setParam("cliffBlock", "minecraft:stone");
            strata.setParam("maxSlope", "45");

            NodeCard root = spawnNode("World:BiomeRoot", 548, 20);

            connectSockets(noise3d, "out", warp, "input");
            connectSockets(grav, "out", sum, "in_a");
            connectSockets(warp, "out", sum, "in_b");
            connectSockets(sum, "out", strata, "in_terrain");
            connectSockets(sum, "out", root, "terrain");
            connectSockets(strata, "out", root, "material");
        } else if (this.activePresetIdx == 1) {
            // Preset 1: Swiss Cheese Caverns
            NodeCard cell = spawnNode("Density:CellNoise3D", 12, 20);
            cell.setParam("frequency", "0.015");
            cell.setParam("jitter", "0.4");

            NodeCard clamp = spawnNode("Density:ClampDensity", 190, 20);
            clamp.setParam("min", "-0.6");
            clamp.setParam("max", "0.8");

            NodeCard ySample = spawnNode("Density:YSampledDensity", 368, 20);
            ySample.setParam("yInterval", "4");

            NodeCard strata = spawnNode("Material:StrataMaterialProvider", 368, 100);
            strata.setParam("upperBlock", "minecraft:stone");
            strata.setParam("lowerBlock", "minecraft:deepslate");

            NodeCard root = spawnNode("World:BiomeRoot", 548, 30);

            connectSockets(cell, "out", clamp, "input");
            connectSockets(clamp, "out", ySample, "input");
            connectSockets(ySample, "out", root, "terrain");
            connectSockets(strata, "out", root, "material");
        } else if (this.activePresetIdx == 2) {
            // Preset 2: Terraced Mesa Strata
            NodeCard noise2d = spawnNode("Density:SimplexNoise2D", 12, 15);
            NodeCard spline = spawnNode("Curve:ManualCurve", 12, 115);
            spline.setParam("mode", "STEP");

            NodeCard mapper = spawnNode("Density:CurveMapperDensity", 190, 30);
            NodeCard striped = spawnNode("Material:StripedMaterialProvider", 368, 30);
            NodeCard root = spawnNode("World:BiomeRoot", 548, 30);

            connectSockets(noise2d, "out", mapper, "input");
            connectSockets(spline, "out", mapper, "curve");
            connectSockets(mapper, "out", root, "terrain");
            connectSockets(striped, "out", root, "material");
        } else {
            // Preset 3: Flora & Tree Scatter Pipeline
            NodeCard poisson = spawnNode("Positions:PoissonDisk", 12, 15);
            poisson.setParam("spacing", "10.0");

            NodeCard surfPat = spawnNode("Pattern:SurfacePattern", 12, 95);
            NodeCard scanner = spawnNode("Scanner:LinearScanner", 190, 85);
            NodeCard prefab = spawnNode("Prop:PrefabProp", 190, 175);
            NodeCard assign = spawnNode("Assign:ConstantAssignment", 368, 155);
            NodeCard dist = spawnNode("Prop:AssignmentsProp", 546, 50);

            connectSockets(surfPat, "out", scanner, "pattern");
            connectSockets(prefab, "out", assign, "prop");
            connectSockets(poisson, "out", dist, "positions");
            connectSockets(scanner, "out", dist, "scanner");
            connectSockets(assign, "out", dist, "assignments");
        }
    }

    public void loadDimensionMacroPreset(int macroIdx) {
        int idx = ((macroIdx % 3) + 3) % 3;
        if (idx == 0) {
            this.presetDropdown.setSelectedById("macro_spawn");
        } else if (idx == 1) {
            this.presetDropdown.setSelectedById("macro_end");
        } else {
            this.presetDropdown.setSelectedById("macro_skyblock");
        }

        nodes.clear();
        wires.clear();
        selectedNode = null;
        editingParam = null;

        StudioState.get().setGraphMode(StudioState.GraphMode.DIMENSION_GRAPH);
        StudioState.get().setCanvasPanX(18.0);
        StudioState.get().setCanvasPanY(32.0);
        StudioState.get().setCanvasZoom(1.0);

        if (idx == 0) {
            this.fauxMinimapRadius = 700.0f;
            // Macro 0: Spawn Island + Connected Landmarks (A => B) with DEFAULT_BIOMES fallback
            NodeCard spawnIsland = spawnNode("World:SpawnIslandAnchor", 10, 12);
            spawnIsland.setParam("islandRadius", "220.0");
            spawnIsland.setParam("coastFalloff", "48.0");
            spawnIsland.setParam("moatOuterRadius", "360.0");
            spawnIsland.setParam("plateauHeight", "74.0");
            spawnIsland.setParam("biome", "TemperateForest");
            spawnIsland.setParam("centerStructure", "worldgenv2:spawn_shrine");

            NodeCard landmark1 = spawnNode("World:RelationalLandmark", 192, 12);
            landmark1.setParam("landmarkId", "northern_watchtower");
            landmark1.setParam("prefabId", "worldgenv2:watchtower_01");
            landmark1.setParam("count", "1");
            landmark1.setParam("minDistance", "110.0");
            landmark1.setParam("maxDistance", "175.0");
            landmark1.setParam("connectBridge", "true");
            landmark1.setParam("bridgeHalfWidth", "3.5");
            landmark1.setParam("bridgeBlock", "minecraft:stone_bricks");

            NodeCard landmark2 = spawnNode("World:RelationalLandmark", 192, 168);
            landmark2.setParam("landmarkId", "eastern_waystation");
            landmark2.setParam("prefabId", "worldgenv2:waystation_inn");
            landmark2.setParam("count", "1");
            landmark2.setParam("minDistance", "140.0");
            landmark2.setParam("maxDistance", "210.0");
            landmark2.setParam("connectBridge", "true");
            landmark2.setParam("bridgeHalfWidth", "3.0");
            landmark2.setParam("bridgeBlock", "minecraft:cobblestone");

            NodeCard biome1 = spawnNode("World:BiomeWeightEntry", 10, 168);
            biome1.setParam("biome", "TemperateForest");
            biome1.setParam("weight", "2.0");
            biome1.setParam("sizeMultiplier", "1.2");
            biome1.setParam("transitionDistance", "36.0");

            NodeCard biome2 = spawnNode("World:BiomeWeightEntry", 10, 278);
            biome2.setParam("biome", "SunlitPlains");
            biome2.setParam("weight", "1.5");
            biome2.setParam("sizeMultiplier", "1.0");
            biome2.setParam("transitionDistance", "32.0");

            NodeCard dimOut = spawnNode("World:DimensionOutput", 378, 30);
            dimOut.setParam("outsideFallback", "DEFAULT_BIOMES");
            dimOut.setParam("boundsRadius", "640.0");
            dimOut.setParam("blendFalloff", "64.0");
            dimOut.setParam("seaLevel", "63");
            dimOut.setParam("defaultBlock", "minecraft:stone");
            dimOut.setParam("fluidBlock", "minecraft:water");

            connectSockets(spawnIsland, "anchor", landmark1, "parent_anchor");
            connectSockets(spawnIsland, "anchor", landmark2, "parent_anchor");
            connectSockets(spawnIsland, "out_zone", dimOut, "zones");
            connectSockets(landmark1, "out_zone", dimOut, "landmarks");
            connectSockets(biome1, "out_pool", biome2, "in_pool");
            connectSockets(biome2, "out_pool", dimOut, "biome_pool");
        } else if (idx == 1) {
            this.fauxMinimapRadius = 1500.0f;
            // Macro 1: The End Concentric Rings & Obsidian Towers (VOID fallback)
            NodeCard centralIsland = spawnNode("World:RadialZone", 10, 10);
            centralIsland.setParam("innerRadius", "0.0");
            centralIsland.setParam("outerRadius", "190.0");
            centralIsland.setParam("falloffWidth", "36.0");
            centralIsland.setParam("zoneMode", "ISLAND_PLATEAU");
            centralIsland.setParam("biomeOverride", "StonyPeaks");
            centralIsland.setParam("targetHeight", "64.0");
            centralIsland.setParam("floating", "true");

            NodeCard towers = spawnNode("World:RingLandmarks", 192, 10);
            towers.setParam("ringRadius", "43.0");
            towers.setParam("count", "10");
            towers.setParam("minHeight", "76");
            towers.setParam("maxHeight", "103");
            towers.setParam("pillarRadius", "4");
            towers.setParam("pillarBlock", "minecraft:obsidian");
            towers.setParam("bedrockCap", "true");

            NodeCard voidMoat = spawnNode("World:RadialZone", 192, 152);
            voidMoat.setParam("innerRadius", "190.0");
            voidMoat.setParam("outerRadius", "960.0");
            voidMoat.setParam("falloffWidth", "48.0");
            voidMoat.setParam("zoneMode", "VOID_MOAT");
            voidMoat.setParam("biomeOverride", "WindsweptHills");
            voidMoat.setParam("targetHeight", "0.0");
            voidMoat.setParam("floating", "true");

            NodeCard outerIslands = spawnNode("World:RadialZone", 10, 165);
            outerIslands.setParam("innerRadius", "960.0");
            outerIslands.setParam("outerRadius", "30000.0");
            outerIslands.setParam("falloffWidth", "96.0");
            outerIslands.setParam("zoneMode", "OUTER_ISLANDS");
            outerIslands.setParam("biomeOverride", "AridDunes");
            outerIslands.setParam("targetHeight", "62.0");
            outerIslands.setParam("floating", "true");

            NodeCard dimOut = spawnNode("World:DimensionOutput", 378, 30);
            dimOut.setParam("outsideFallback", "VOID");
            dimOut.setParam("boundsRadius", "30000.0");
            dimOut.setParam("blendFalloff", "96.0");
            dimOut.setParam("seaLevel", "0");
            dimOut.setParam("defaultBlock", "minecraft:end_stone");
            dimOut.setParam("fluidBlock", "minecraft:air");

            connectSockets(centralIsland, "out_zone", towers, "in_zone");
            connectSockets(towers, "out_zone", voidMoat, "in_zone");
            connectSockets(voidMoat, "out_zone", outerIslands, "in_zone");
            connectSockets(outerIslands, "out_zone", dimOut, "zones");
            connectSockets(towers, "out_zone", dimOut, "landmarks");
        } else {
            this.fauxMinimapRadius = 420.0f;
            // Macro 2: Bounded Skyblock Archipelago (Outside Bounds -> VOID)
            NodeCard spawnIsland = spawnNode("World:SpawnIslandAnchor", 10, 18);
            spawnIsland.setParam("islandRadius", "150.0");
            spawnIsland.setParam("coastFalloff", "36.0");
            spawnIsland.setParam("moatOuterRadius", "180.0");
            spawnIsland.setParam("plateauHeight", "72.0");
            spawnIsland.setParam("biome", "TemperateForest");
            spawnIsland.setParam("centerStructure", "worldgenv2:spawn_shrine");

            NodeCard landmark = spawnNode("World:RelationalLandmark", 192, 18);
            landmark.setParam("landmarkId", "sky_shrine");
            landmark.setParam("prefabId", "worldgenv2:ancient_shrine");
            landmark.setParam("count", "1");
            landmark.setParam("minDistance", "95.0");
            landmark.setParam("maxDistance", "145.0");
            landmark.setParam("connectBridge", "true");
            landmark.setParam("bridgeHalfWidth", "3.0");
            landmark.setParam("bridgeBlock", "minecraft:stone_bricks");

            NodeCard dimOut = spawnNode("World:DimensionOutput", 378, 24);
            dimOut.setParam("outsideFallback", "VOID");
            dimOut.setParam("boundsRadius", "340.0");
            dimOut.setParam("blendFalloff", "48.0");
            dimOut.setParam("seaLevel", "0");
            dimOut.setParam("defaultBlock", "minecraft:stone");
            dimOut.setParam("fluidBlock", "minecraft:air");

            connectSockets(spawnIsland, "anchor", landmark, "parent_anchor");
            connectSockets(spawnIsland, "out_zone", dimOut, "zones");
            connectSockets(landmark, "out_zone", dimOut, "landmarks");
        }
    }

    public NodeCard spawnNode(String schemaIdOrType, double cx, double cy) {
        NodeSchema schema = StudioNodeCatalog.getSchema(schemaIdOrType);
        NodeCard card = new NodeCard("node_" + (nodeCounter++), schema, cx, cy);
        nodes.add(card);
        selectedNode = card;
        return card;
    }

    private void connectSockets(NodeCard from, String fromSock, NodeCard to, String toSock) {
        int col = StudioTheme.COLOR_DENSITY;
        for (SocketDef s : from.outputs) {
            if (s.id().equals(fromSock)) {
                col = s.domain().color;
                break;
            }
        }
        wires.removeIf(w -> w.toNodeId.equals(to.id) && w.toSocketId.equals(toSock));
        wires.add(new WireConnection(from.id, fromSock, to.id, toSock, col));
    }

    private void updateCatalogFilter() {
        filteredCatalog.clear();
        String q = searchFilter.trim().toLowerCase(Locale.ROOT);
        String catFilter = StudioNodeCatalog.CATEGORIES[selectedCategoryIdx];

        for (NodeSchema s : StudioNodeCatalog.getAllSchemas()) {
            if (!"All".equals(catFilter) && !s.category().equalsIgnoreCase(catFilter)) {
                continue;
            }
            if (q.isEmpty()
                    || s.displayName().toLowerCase(Locale.ROOT).contains(q)
                    || s.typeName().toLowerCase(Locale.ROOT).contains(q)
                    || s.category().toLowerCase(Locale.ROOT).contains(q)
                    || s.description().toLowerCase(Locale.ROOT).contains(q)) {
                filteredCatalog.add(s);
                continue;
            }
            for (String tag : s.tags()) {
                if (tag.contains(q)) {
                    filteredCatalog.add(s);
                    break;
                }
            }
        }
        searchScroll = 0;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        StudioState state = StudioState.get();
        double panX = state.getCanvasPanX();
        double panY = state.getCanvasPanY();
        double zoom = state.getCanvasZoom();

        // Convert screen mouse coordinates to canvas space coordinates
        double mouseCanvasX = (mouseX - x - panX) / zoom;
        double mouseCanvasY = (mouseY - y - panY) / zoom;
        this.wireMouseCanvasX = mouseCanvasX;
        this.wireMouseCanvasY = mouseCanvasY;

        // 1. Draw Canvas Background & Grid inside Scissor
        StudioTheme.drawInsetWell(graphics, x, y, width, height, StudioTheme.CANVAS_BG);
        graphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);

        int gridSize = Math.max(10, (int) Math.round(28 * zoom));
        int startX = ((int) panX % gridSize + gridSize) % gridSize;
        int startY = ((int) panY % gridSize + gridSize) % gridSize;
        for (int gx = startX; gx < width; gx += gridSize) {
            graphics.verticalLine(x + gx, y, y + height, StudioTheme.GRID_LINE);
        }
        for (int gy = startY; gy < height; gy += gridSize) {
            graphics.horizontalLine(x, x + width, y + gy, StudioTheme.GRID_LINE);
        }

        // 2. Push 2D Affine Transform (Matrix3x2fStack) so cards, wires, sockets, and text scale cleanly!
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) (x + panX), (float) (y + panY));
        graphics.pose().scale((float) zoom, (float) zoom);

        // 2a. Render Committed Bezier Wires in Canvas Space
        for (WireConnection wire : wires) {
            NodeCard fromNode = findNode(wire.fromNodeId);
            NodeCard toNode = findNode(wire.toNodeId);
            if (fromNode != null && toNode != null) {
                int outIdx = findSocketIndex(fromNode.outputs, wire.fromSocketId);
                int inIdx = findSocketIndex(toNode.inputs, wire.toSocketId);
                if (outIdx >= 0 && inIdx >= 0) {
                    int sx = (int) fromNode.canvasX + fromNode.cardWidth;
                    int sy = fromNode.getOutputSocketY(outIdx);
                    int ex = (int) toNode.canvasX;
                    int ey = toNode.getInputSocketY(inIdx);
                    renderBezierWire(graphics, sx, sy, ex, ey, wire.color);
                }
            }
        }

        // 2b. Render Active Dragged Wire in Canvas Space
        if (isDraggingWire && wireSourceNode != null && wireSourceSocket != null) {
            int sockIdx = wireDragFromOutput
                    ? findSocketIndex(wireSourceNode.outputs, wireSourceSocket.id())
                    : findSocketIndex(wireSourceNode.inputs, wireSourceSocket.id());
            if (sockIdx >= 0) {
                int sx = wireDragFromOutput
                        ? (int) wireSourceNode.canvasX + wireSourceNode.cardWidth
                        : (int) wireSourceNode.canvasX;
                int sy = wireDragFromOutput
                        ? wireSourceNode.getOutputSocketY(sockIdx)
                        : wireSourceNode.getInputSocketY(sockIdx);
                int ex = (int) mouseCanvasX;
                int ey = (int) mouseCanvasY;
                if (wireDragFromOutput) {
                    renderBezierWire(graphics, sx, sy, ex, ey, wireSourceSocket.domain().color);
                } else {
                    renderBezierWire(graphics, ex, ey, sx, sy, wireSourceSocket.domain().color);
                }
            }
        }

        // 2c. Render Node Cards, Sockets & Inline Editable Parameter Fields
        NodeCard hoveredNode = null;
        SocketDef hoveredSocket = null;
        ParamInstance hoveredParam = null;

        for (NodeCard node : nodes) {
            int cx = (int) node.canvasX;
            int cy = (int) node.canvasY;
            int cw = node.cardWidth;
            int ch = node.cardHeight;

            boolean cardHovered = !searchDrawerOpen && !structurePickerOpen
                    && mouseCanvasX >= cx && mouseCanvasX < cx + cw
                    && mouseCanvasY >= cy && mouseCanvasY < cy + ch;
            if (cardHovered) {
                hoveredNode = node;
            }
            boolean isSel = (node == selectedNode);

            // Card Shadow & Body
            graphics.fill(cx + 2, cy + 2, cx + cw + 2, cy + ch + 2, 0x66000000);
            StudioTheme.drawBeveledPanel(graphics, cx, cy, cw, ch, 0xF21B1B24);

            // Category Header Bar
            graphics.fill(cx + 1, cy + 1, cx + cw - 1, cy + NodeCard.HEADER_H, node.schema.headerColor());

            // Clamped Title (Leaves 14px on right for [x] delete button)
            String titleStr = font.plainSubstrByWidth(node.title, cw - 20);
            graphics.text(font, titleStr, cx + 5, cy + 4, StudioTheme.TEXT_WHITE, true);

            // Delete [x] Button in Header
            int delX = cx + cw - 12;
            int delY = cy + 3;
            boolean delHov = !searchDrawerOpen && !structurePickerOpen && mouseCanvasX >= delX && mouseCanvasX < delX + 10 && mouseCanvasY >= delY && mouseCanvasY < delY + 10;
            graphics.text(font, "x", delX + 2, delY + 1, delHov ? 0xFFFF5555 : 0xFFCCCCCC, true);

            // Input Sockets (Left Side)
            for (int si = 0; si < node.inputs.size(); si++) {
                SocketDef sock = node.inputs.get(si);
                int sy = node.getInputSocketY(si);
                boolean connected = isInputConnected(node.id, sock.id());
                boolean sHov = !searchDrawerOpen && !structurePickerOpen && Math.abs(mouseCanvasX - cx) <= 6 && Math.abs(mouseCanvasY - sy) <= 6;
                if (sHov) hoveredSocket = sock;

                int r = sHov ? 3 : 2;
                graphics.fill(cx - r, sy - r, cx + r, sy + r, sHov ? 0xFFFFFFFF : sock.domain().color);
                if (!connected) {
                    graphics.fill(cx - r + 1, sy - r + 1, cx + r - 1, sy + r - 1, 0xFF121218);
                }

                String inLabel = font.plainSubstrByWidth(sock.label(), (cw / 2) - 6);
                graphics.text(font, inLabel, cx + 5, sy - 4, sHov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_GRAY, false);
            }

            // Output Sockets (Right Side)
            for (int so = 0; so < node.outputs.size(); so++) {
                SocketDef sock = node.outputs.get(so);
                int sy = node.getOutputSocketY(so);
                boolean sHov = !searchDrawerOpen && !structurePickerOpen && Math.abs(mouseCanvasX - (cx + cw)) <= 6 && Math.abs(mouseCanvasY - sy) <= 6;
                if (sHov) hoveredSocket = sock;

                int r = sHov ? 3 : 2;
                graphics.fill(cx + cw - r, sy - r, cx + cw + r, sy + r, sHov ? 0xFFFFFFFF : sock.domain().color);

                String outLabel = font.plainSubstrByWidth(sock.label(), (cw / 2) - 6);
                int lw = font.width(outLabel);
                graphics.text(font, outLabel, cx + cw - lw - 5, sy - 4, sHov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_WHITE, false);
            }

            // Divider line before editable parameters
            if (!node.params.isEmpty()) {
                int divY = node.getParamRowY(0) - 2;
                graphics.horizontalLine(cx + 4, cx + cw - 4, divY, 0xFF2D2D3A);
            }

            // Editable Parameter Rows
            for (int pi = 0; pi < node.params.size(); pi++) {
                ParamInstance param = node.params.get(pi);
                int py = node.getParamRowY(pi);
                if (cardHovered && mouseCanvasY >= py && mouseCanvasY < py + 12) {
                    hoveredParam = param;
                }
                renderParamRow(graphics, font, cx, py, cw, param, mouseCanvasX, mouseCanvasY);
            }

            // Selection / Hover Outline
            if (isSel) {
                graphics.outline(cx - 1, cy - 1, cw + 2, ch + 2, StudioTheme.TEXT_GOLD);
            } else if (cardHovered) {
                graphics.outline(cx, cy, cw, ch, 0xFF8888AA);
            }
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        // 2d. Live Top-Down Dimension Faux-Minimap / Probability & Bounds Visualizer (when in Dimension Graph Mode)
        if (state.isDimensionGraphMode() && showFauxMinimap && width >= 260 && height >= 140) {
            renderCanvasFauxMinimap(graphics, font, mouseX, mouseY);
        }

        // 3. Compact Top Toolbar (Screen Space — Never Overlaps)
        renderCanvasToolbar(graphics, font, mouseX, mouseY);

        // 4. Smart Search Drawer (All ~295 Nodes across 24 Categories)
        if (searchDrawerOpen) {
            renderSearchDrawer(graphics, font, mouseX, mouseY);
        }

        // 4b. Structure / Prefab Picker Modal
        if (structurePickerOpen) {
            renderStructurePickerModal(graphics, font, mouseX, mouseY);
        }

        // 4c. Preset Dropdown Popout Overlay
        presetDropdown.renderOverlay(graphics, font, mouseX, mouseY);

        // 5. Contextual Tooltip (Prevents any truncated label from ever being unreadable!)
        if (!searchDrawerOpen && !structurePickerOpen && !presetDropdown.isExpanded() && !blockPicker.isVisible()) {
            if (hoveredSocket != null) {
                graphics.setTooltipForNextFrame(
                        Component.literal("§eSocket: §f" + hoveredSocket.label() + " §7(" + hoveredSocket.domain().label + ") • Drag to wire, Right-Click to detach"),
                        mouseX, mouseY
                );
            } else if (hoveredParam != null && hoveredNode != null && mouseY > y + 26) {
                String rangeInfo = (hoveredParam.def.type() == ParamType.FLOAT || hoveredParam.def.type() == ParamType.INT)
                        ? " §7[Range: " + fmtNum(hoveredParam.def.min()) + " .. " + fmtNum(hoveredParam.def.max()) + "]"
                        : (hoveredParam.def.type() == ParamType.STRUCTURE ? " §d(Click to open Structure Picker)" : "");
                graphics.setTooltipForNextFrame(
                        Component.literal("§e" + hoveredParam.def.label() + " §7(" + hoveredParam.def.key() + "): §f" + hoveredParam.value + rangeInfo),
                        mouseX, mouseY
                );
            } else if (hoveredNode != null && mouseY > y + 26) {
                graphics.setTooltipForNextFrame(
                        Component.literal("§6" + hoveredNode.title + " §7[" + hoveredNode.schema.category() + "] — §f" + hoveredNode.schema.description()),
                        mouseX, mouseY
                );
            }
        }
    }

    private static String fmtNum(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf(Math.round(v));
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private void renderParamRow(
        GuiGraphicsExtractor graphics,
        Font font,
        int cardX,
        int rowY,
        int cardW,
        ParamInstance param,
        double mcx,
        double mcy
    ) {
        // Left label (clean 60px label width - prevents cutting off text)
        String lbl = font.plainSubstrByWidth(param.def.label(), 60);
        graphics.text(font, lbl, cardX + 4, rowY + 2, 0xFFBBBBCC, false);

        // Right Interactive Control Box
        int boxX = cardX + 64;
        int boxW = cardW - 68;
        int boxH = 11;
        boolean boxHov = !searchDrawerOpen && !structurePickerOpen && mcx >= boxX && mcx < boxX + boxW && mcy >= rowY && mcy < rowY + boxH;
        boolean isEditing = (editingParam == param);

        ParamType type = param.def.type();
        if (type == ParamType.FLOAT || type == ParamType.INT) {
            // Numeric field with [-] [ value ] [+]
            int btnW = 10;
            int valX = boxX + btnW;
            int valW = boxW - (btnW * 2);

            boolean minusHov = boxHov && mcx < valX;
            boolean plusHov = boxHov && mcx >= valX + valW;
            boolean valHov = boxHov && !minusHov && !plusHov;

            // [-] button
            graphics.fill(boxX, rowY, boxX + btnW, rowY + boxH, minusHov ? 0xFF3A3A4C : 0xFF252532);
            graphics.text(font, "-", boxX + 2, rowY + 1, minusHov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_GRAY, false);

            // Center value well
            int wellBg = isEditing ? 0xFF1E2D3D : (valHov ? 0xFF1A1A26 : 0xFF101016);
            graphics.fill(valX, rowY, valX + valW, rowY + boxH, wellBg);
            graphics.outline(valX, rowY, valW, boxH, isEditing ? StudioTheme.TEXT_CYAN : 0xFF323242);

            String dispVal = isEditing ? (editingBuffer + "_") : param.value;
            dispVal = font.plainSubstrByWidth(dispVal, valW - 2);
            graphics.centeredText(font, dispVal, valX + (valW / 2), rowY + 2, isEditing ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_WHITE);

            // [+] button
            int plusX = valX + valW;
            graphics.fill(plusX, rowY, plusX + btnW, rowY + boxH, plusHov ? 0xFF3A3A4C : 0xFF252532);
            graphics.text(font, "+", plusX + 2, rowY + 1, plusHov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_GRAY, false);

        } else if (type == ParamType.BOOLEAN) {
            boolean on = param.asBoolean();
            int bg = on ? (boxHov ? 0xFF2A6E38 : 0xFF1E5229) : (boxHov ? 0xFF383846 : 0xFF24242E);
            graphics.fill(boxX, rowY, boxX + boxW, rowY + boxH, bg);
            graphics.outline(boxX, rowY, boxW, boxH, on ? 0xFF44DD66 : 0xFF444454);
            graphics.centeredText(font, on ? "ON" : "OFF", boxX + (boxW / 2), rowY + 2, on ? 0xFF88FF88 : StudioTheme.TEXT_GRAY);

        } else if (type == ParamType.ENUM) {
            int bg = boxHov ? 0xFF2E384E : 0xFF1E2434;
            graphics.fill(boxX, rowY, boxX + boxW, rowY + boxH, bg);
            graphics.outline(boxX, rowY, boxW, boxH, 0xFF445577);
            String disp = font.plainSubstrByWidth(param.value, boxW - 12);
            graphics.centeredText(font, "< " + disp + " >", boxX + (boxW / 2), rowY + 2, boxHov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_CYAN);

        } else if (type == ParamType.STRUCTURE) {
            int bg = boxHov ? 0xFF36244A : 0xFF231832;
            graphics.fill(boxX, rowY, boxX + boxW, rowY + boxH, bg);
            graphics.outline(boxX, rowY, boxW, boxH, boxHov ? StudioTheme.TEXT_YELLOW : 0xFF8855BB);

            ItemStack iconStack = resolveStructureIcon(param.value);
            int textX = boxX + 3;
            if (!iconStack.isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(boxX + 1, rowY);
                graphics.pose().scale(0.6875f, 0.6875f);
                graphics.fakeItem(iconStack, 0, 0);
                graphics.pose().popMatrix();
                textX = boxX + 13;
            }

            String shortStruct = param.value.contains(":") ? param.value.substring(param.value.indexOf(':') + 1) : param.value;
            shortStruct = font.plainSubstrByWidth(shortStruct, Math.max(10, boxW - (textX - boxX) - 2));
            graphics.text(font, shortStruct, textX, rowY + 2, boxHov ? StudioTheme.TEXT_YELLOW : 0xFFE0AAFF, false);

        } else if (type == ParamType.BLOCK) {
            int bg = boxHov ? 0xFF3A3224 : 0xFF221E18;
            graphics.fill(boxX, rowY, boxX + boxW, rowY + boxH, bg);
            graphics.outline(boxX, rowY, boxW, boxH, boxHov ? StudioTheme.TEXT_GOLD : 0xFF665533);

            ItemStack iconStack = resolveBlockIcon(param.value);
            int textX = boxX + 3;
            if (!iconStack.isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(boxX + 1, rowY);
                graphics.pose().scale(0.6875f, 0.6875f);
                graphics.fakeItem(iconStack, 0, 0);
                graphics.pose().popMatrix();
                textX = boxX + 13;
            }

            String shortBlock = param.value.contains(":") ? param.value.substring(param.value.indexOf(':') + 1) : param.value;
            shortBlock = font.plainSubstrByWidth(shortBlock, Math.max(10, boxW - (textX - boxX) - 2));
            graphics.text(font, shortBlock, textX, rowY + 2, boxHov ? StudioTheme.TEXT_YELLOW : 0xFFFFCC66, false);

        } else {
            // STRING field
            int wellBg = isEditing ? 0xFF1E2D3D : (boxHov ? 0xFF1A1A26 : 0xFF101016);
            graphics.fill(boxX, rowY, boxX + boxW, rowY + boxH, wellBg);
            graphics.outline(boxX, rowY, boxW, boxH, isEditing ? StudioTheme.TEXT_CYAN : 0xFF323242);
            String dispVal = isEditing ? (editingBuffer + "_") : param.value;
            dispVal = font.plainSubstrByWidth(dispVal, boxW - 4);
            graphics.text(font, dispVal, boxX + 3, rowY + 2, isEditing ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_WHITE, false);
        }
    }

    public static ItemStack resolveStructureIcon(String structId) {
        if (structId == null || structId.isBlank()) return new ItemStack(Blocks.STONE_BRICKS);
        for (StructureCatalogEntry e : STRUCTURE_CATALOG) {
            if (e.id().equalsIgnoreCase(structId) || structId.endsWith(e.id())) {
                return e.icon();
            }
        }
        return new ItemStack(Blocks.LODESTONE);
    }

    private static final Map<String, ItemStack> BLOCK_ICON_CACHE = new HashMap<>();

    public static ItemStack resolveBlockIcon(String blockId) {
        if (blockId == null || blockId.isBlank()) return ItemStack.EMPTY;
        ItemStack cached = BLOCK_ICON_CACHE.get(blockId);
        if (cached != null) return cached;

        ItemStack stack = ItemStack.EMPTY;
        try {
            Identifier id = Identifier.tryParse(blockId);
            if (id != null) {
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block != null && block != Blocks.AIR) {
                    stack = block.asItem().getDefaultInstance();
                }
                if (stack.isEmpty()) {
                    if ("minecraft:water".equals(blockId) || (block == Blocks.WATER) || "water".equals(id.getPath())) {
                        stack = new ItemStack(Items.WATER_BUCKET);
                    } else if ("minecraft:lava".equals(blockId) || (block == Blocks.LAVA) || "lava".equals(id.getPath())) {
                        stack = new ItemStack(Items.LAVA_BUCKET);
                    } else if (BuiltInRegistries.FLUID.containsKey(id)) {
                        var fluid = BuiltInRegistries.FLUID.getValue(id);
                        var bucket = fluid.getBucket();
                        if (bucket != null && bucket != Items.AIR) {
                            stack = new ItemStack(bucket);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        BLOCK_ICON_CACHE.put(blockId, stack);
        return stack;
    }

    private void renderCanvasToolbar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int barX = x + 6;
        int barY = y + 6;
        int btnH = 18;

        // 1. [+ Add Node] Button
        int addW = 74;
        boolean addHov = mouseX >= barX && mouseX < barX + addW && mouseY >= barY && mouseY < barY + btnH;
        StudioTheme.drawButton(graphics, font, Component.literal("+ Add Node"), barX, barY, addW, btnH, addHov, true);

        // 2. Preset Dropdown (Replaces cycle button)
        int preX = barX + addW + 4;
        int preW = Math.min(156, Math.max(96, width - 256));
        presetDropdown.setBounds(preX, barY, preW, btnH);
        presetDropdown.render(graphics, font, mouseX, mouseY);

        // 3. [Apply Live] Button (Compiles & regenerates test chunks immediately)
        int appX = preX + preW + 4;
        int appW = 64;
        boolean appHov = mouseX >= appX && mouseX < appX + appW && mouseY >= barY && mouseY < barY + btnH;
        StudioTheme.drawButton(graphics, font, Component.literal("§aApply Live"), appX, barY, appW, btnH, appHov, true);

        // 4. [Center] Button
        int rstX = appX + appW + 4;
        int rstW = 46;
        if (rstX + rstW < x + width - 4) {
            boolean rstHov = mouseX >= rstX && mouseX < rstX + rstW && mouseY >= barY && mouseY < barY + btnH;
            StudioTheme.drawButton(graphics, font, Component.literal("Center"), rstX, barY, rstW, btnH, rstHov, true);
        }

        // 5. [Clear] Button
        int clrX = rstX + rstW + 4;
        int clrW = 40;
        if (clrX + clrW < x + width - 4) {
            boolean clrHov = mouseX >= clrX && mouseX < clrX + clrW && mouseY >= barY && mouseY < barY + btnH;
            StudioTheme.drawButton(graphics, font, Component.literal("Clear"), clrX, barY, clrW, btnH, clrHov, true);
        }

        // 6. [Mode: Biome / Dimension] 2-Level Graph Mode Toggle Button
        int modeX = clrX + clrW + 4;
        int modeW = 86;
        if (modeX + modeW < x + width - 4) {
            boolean modeHov = mouseX >= modeX && mouseX < modeX + modeW && mouseY >= barY && mouseY < barY + btnH;
            boolean isDimMode = StudioState.get().getGraphMode() == StudioState.GraphMode.DIMENSION_GRAPH;
            String modeLabel = isDimMode ? "§6Mode: Dimension" : "§bMode: Biome";
            StudioTheme.drawButton(graphics, font, Component.literal(modeLabel), modeX, barY, modeW, btnH, modeHov, true);
        }

        // Bottom-left mini help bar
        int helpY = y + height - 15;
        String helpText = font.plainSubstrByWidth(
                "Double-Click Canvas: Add Node (" + StudioNodeCatalog.getAllSchemas().size() + " nodes) | Double-Click BiomeWeight Header: Step Into Biome Graph | Edits Auto-Update",
                width - 16
        );
        graphics.text(font, helpText, x + 6, helpY, 0xFF888899, true);
    }

    private void renderSearchDrawer(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int drawerW = Math.min(250, width - 16);
        int drawerH = Math.min(196, height - 16);
        int dx = Math.max(x + 8, Math.min(x + width - drawerW - 8, searchDrawerX));
        int dy = Math.max(y + 8, Math.min(y + height - drawerH - 8, searchDrawerY));

        StudioTheme.drawBeveledPanel(graphics, dx, dy, drawerW, drawerH, 0xF8161620);

        // Header & Count
        String header = "Add Node (" + filteredCatalog.size() + " / " + StudioNodeCatalog.getAllSchemas().size() + ")";
        graphics.text(font, header, dx + 8, dy + 6, StudioTheme.TEXT_GOLD, true);

        // Close [x]
        int closeX = dx + drawerW - 16;
        graphics.text(font, "x", closeX + 3, dy + 6, StudioTheme.TEXT_GRAY, true);

        // Search Input Box
        int searchY = dy + 18;
        StudioTheme.drawInsetWell(graphics, dx + 6, searchY, drawerW - 12, 15, 0xFF0E0E14);
        String displayQ = searchFilter.isEmpty() ? "§7Type node name, category, or tag..." : searchFilter + "_";
        graphics.text(font, font.plainSubstrByWidth(displayQ, drawerW - 20), dx + 10, searchY + 4, StudioTheme.TEXT_WHITE, false);

        // Category Filter Selector Row: [<] Category: All [>]
        int catY = searchY + 18;
        StudioTheme.drawBeveledPanel(graphics, dx + 6, catY, drawerW - 12, 14, 0xFF20202C);
        String catName = StudioNodeCatalog.CATEGORIES[selectedCategoryIdx];
        graphics.text(font, "<", dx + 10, catY + 3, StudioTheme.TEXT_YELLOW, true);
        graphics.centeredText(font, "Category: " + catName + " (Click to cycle)", dx + (drawerW / 2), catY + 3, StudioTheme.TEXT_CYAN);
        graphics.text(font, ">", dx + drawerW - 14, catY + 3, StudioTheme.TEXT_YELLOW, true);

        // Scrollable Results List
        int listY = catY + 17;
        int listH = drawerH - (listY - dy) - 6;
        int listW = drawerW - 12;
        StudioTheme.drawInsetWell(graphics, dx + 6, listY, listW, listH, 0xFF101016);

        int itemH = 15;
        int maxVis = Math.max(1, (listH - 4) / itemH);
        int maxScroll = Math.max(0, filteredCatalog.size() - maxVis);
        searchScroll = Math.max(0, Math.min(maxScroll, searchScroll));

        NodeSchema hoveredSchema = null;

        for (int i = 0; i < maxVis; i++) {
            int idx = i + searchScroll;
            if (idx >= filteredCatalog.size()) break;

            NodeSchema entry = filteredCatalog.get(idx);
            int rowY = listY + 2 + (i * itemH);
            boolean hov = mouseX >= dx + 7 && mouseX < dx + drawerW - 13 && mouseY >= rowY && mouseY < rowY + itemH;

            if (hov) {
                graphics.fill(dx + 7, rowY, dx + drawerW - 13, rowY + itemH, 0x45FFAA00);
                hoveredSchema = entry;
            }

            // Domain color badge
            graphics.fill(dx + 10, rowY + 4, dx + 16, rowY + 10, entry.headerColor());

            // Category prefix + Node name (clamped)
            String catBadge = "[" + entry.category() + "] ";
            int catW = font.width(catBadge);
            graphics.text(font, catBadge, dx + 20, rowY + 4, 0xFF8899AA, false);

            String nameStr = font.plainSubstrByWidth(entry.displayName(), listW - catW - 26);
            graphics.text(font, nameStr, dx + 20 + catW, rowY + 4, hov ? StudioTheme.TEXT_YELLOW : StudioTheme.TEXT_WHITE, true);
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = dx + drawerW - 11;
            int sbH = listH - 4;
            graphics.fill(sbX, listY + 2, sbX + 4, listY + 2 + sbH, 0xFF0A0A0E);
            int thumbH = Math.max(10, sbH * maxVis / Math.max(1, filteredCatalog.size()));
            int thumbY = listY + 2 + (int) ((sbH - thumbH) * ((float) searchScroll / maxScroll));
            graphics.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, StudioTheme.PANEL_BORDER_LIGHT);
        }

        if (hoveredSchema != null) {
            graphics.setTooltipForNextFrame(
                    Component.literal("§6" + hoveredSchema.displayName() + " §7— " + hoveredSchema.description()),
                    mouseX, mouseY
            );
        }
    }

    private void renderBezierWire(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int dist = Math.abs(x2 - x1) + Math.abs(y2 - y1);
        int steps = Math.max(28, Math.min(140, dist / 2));
        int deltaX = Math.max(28, Math.abs(x2 - x1) / 2);
        int cx1 = x1 + deltaX;
        int cy1 = y1;
        int cx2 = x2 - deltaX;
        int cy2 = y2;

        int prevPx = x1;
        int prevPy = y1;

        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float u = 1.0f - t;
            int curPx = Math.round(u * u * u * x1 + 3 * u * u * t * cx1 + 3 * u * t * t * cx2 + t * t * t * x2);
            int curPy = Math.round(u * u * u * y1 + 3 * u * u * t * cy1 + 3 * u * t * t * cy2 + t * t * t * y2);

            drawSegment(graphics, prevPx, prevPy, curPx, curPy, color);
            prevPx = curPx;
            prevPy = curPy;
        }
    }

    private void drawSegment(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);

        if (adx <= 1 && ady <= 1) {
            int minX = Math.min(x0, x1);
            int maxX = Math.max(x0, x1);
            int minY = Math.min(y0, y1);
            int maxY = Math.max(y0, y1);
            graphics.fill(minX - 1, minY - 1, maxX + 1, maxY + 1, color);
            return;
        }

        int lineSteps = Math.max(adx, ady);
        for (int s = 0; s <= lineSteps; s++) {
            int px = x0 + (dx * s) / lineSteps;
            int py = y0 + (dy * s) / lineSteps;
            graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }

    private NodeCard findNode(String id) {
        for (NodeCard n : nodes) {
            if (n.id.equals(id)) return n;
        }
        return null;
    }

    private int findSocketIndex(List<SocketDef> list, String socketId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(socketId)) return i;
        }
        return -1;
    }

    private boolean isInputConnected(String nodeId, String socketId) {
        for (WireConnection w : wires) {
            if (w.toNodeId.equals(nodeId) && w.toSocketId.equals(socketId)) return true;
        }
        return false;
    }

    private WireConnection findWireToInput(String nodeId, String socketId) {
        for (WireConnection w : wires) {
            if (w.toNodeId.equals(nodeId) && w.toSocketId.equals(socketId)) return w;
        }
        return null;
    }

    private void commitEditingParam() {
        if (editingParam != null) {
            if (editingParam.def.type() == ParamType.FLOAT || editingParam.def.type() == ParamType.INT) {
                try {
                    double v = Double.parseDouble(editingBuffer.trim());
                    editingParam.setDouble(v);
                } catch (Exception ignored) {}
            } else {
                editingParam.value = editingBuffer;
            }
            editingParam = null;
            StudioLiveCompiler.compileAndApplyLive(this);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        StudioState state = StudioState.get();
        double panX = state.getCanvasPanX();
        double panY = state.getCanvasPanY();
        double zoom = state.getCanvasZoom();

        double mcx = (mx - x - panX) / zoom;
        double mcy = (my - y - panY) / zoom;

        // Commit any active text field edit first
        if (editingParam != null) {
            commitEditingParam();
        }

        // 0. Structure Picker Modal click
        if (structurePickerOpen) {
            return handleStructurePickerClick(mx, my, button);
        }

        // 0b. Preset Dropdown Popout or Box click
        if (presetDropdown.isExpanded()) {
            if (presetDropdown.mouseClicked(event, doubleClick)) return true;
        }
        if (presetDropdown.mouseClicked(event, doubleClick)) {
            return true;
        }

        // 0c. Top-Down Dimension Faux-Minimap Zoom [-] / [+] / [Hide] clicks
        if (state.isDimensionGraphMode() && width >= 260 && height >= 140) {
            int mapSize = Math.min(104, Math.max(82, height - 50));
            int mapX = x + width - mapSize - 8;
            int mapY = y + height - mapSize - 20;
            if (showFauxMinimap && mx >= mapX && mx < mapX + mapSize && my >= mapY - 14 && my < mapY + mapSize) {
                if (my < mapY) {
                    if (mx >= mapX + mapSize - 30 && mx < mapX + mapSize - 16) {
                        fauxMinimapRadius = Math.min(4000.0f, fauxMinimapRadius * 1.4f);
                    } else if (mx >= mapX + mapSize - 15) {
                        fauxMinimapRadius = Math.max(220.0f, fauxMinimapRadius / 1.4f);
                    }
                    return true;
                }
                return true;
            }
        }

        // 1. Smart Search Drawer interaction
        if (searchDrawerOpen) {
            int drawerW = Math.min(250, width - 16);
            int drawerH = Math.min(196, height - 16);
            int dx = Math.max(x + 8, Math.min(x + width - drawerW - 8, searchDrawerX));
            int dy = Math.max(y + 8, Math.min(y + height - drawerH - 8, searchDrawerY));

            // Close [x]
            if (mx >= dx + drawerW - 20 && mx < dx + drawerW && my >= dy && my < dy + 18) {
                searchDrawerOpen = false;
                return true;
            }

            // Category cycle bar
            int catY = dy + 36;
            if (mx >= dx + 6 && mx < dx + drawerW - 6 && my >= catY && my < catY + 14) {
                int dir = (mx < dx + 30 || button == 1) ? -1 : 1;
                int len = StudioNodeCatalog.CATEGORIES.length;
                selectedCategoryIdx = (selectedCategoryIdx + dir + len) % len;
                updateCatalogFilter();
                return true;
            }

            // Results list click
            int listY = catY + 17;
            int listH = drawerH - (listY - dy) - 6;
            int itemH = 15;
            int maxVis = Math.max(1, (listH - 4) / itemH);

            if (mx >= dx + 6 && mx < dx + drawerW - 6 && my >= listY && my < listY + listH) {
                int row = (int) ((my - listY - 2) / itemH);
                int idx = row + searchScroll;
                if (idx >= 0 && idx < filteredCatalog.size()) {
                    NodeSchema chosen = filteredCatalog.get(idx);
                    spawnNode(chosen.id(), spawnTargetCanvasX, spawnTargetCanvasY);
                    searchDrawerOpen = false;
                    return true;
                }
            }

            // Click outside closes drawer
            if (mx < dx || mx >= dx + drawerW || my < dy || my >= dy + drawerH) {
                searchDrawerOpen = false;
            }
            return true;
        }

        // 2. Top Toolbar Buttons (Screen Space)
        int barX = x + 6;
        int barY = y + 6;
        int btnH = 18;
        int addW = 74;
        int preX = barX + addW + 4;
        int preW = Math.min(156, Math.max(96, width - 256));
        int appX = preX + preW + 4;
        int appW = 64;
        int rstX = appX + appW + 4;
        int rstW = 46;
        int clrX = rstX + rstW + 4;
        int clrW = 40;
        int modeX = clrX + clrW + 4;
        int modeW = 86;

        if (my >= barY && my < barY + btnH) {
            if (mx >= barX && mx < barX + addW) {
                openSearchDrawerAt(x + 20, y + 28, (width / 2.0 - panX) / zoom - 75, (height / 2.0 - panY) / zoom - 35);
                return true;
            }
            if (mx >= appX && mx < appX + appW) {
                StudioLiveCompiler.compileAndApplyLive(this);
                return true;
            }
            if (rstX + rstW < x + width - 4 && mx >= rstX && mx < rstX + rstW) {
                state.setCanvasPanX(20.0);
                state.setCanvasPanY(34.0);
                state.setCanvasZoom(1.0);
                return true;
            }
            if (clrX + clrW < x + width - 4 && mx >= clrX && mx < clrX + clrW) {
                nodes.clear();
                wires.clear();
                selectedNode = null;
                return true;
            }
            if (modeX + modeW < x + width - 4 && mx >= modeX && mx < modeX + modeW) {
                if (state.getGraphMode() == StudioState.GraphMode.BIOME_GRAPH) {
                    state.setGraphMode(StudioState.GraphMode.DIMENSION_GRAPH);
                    String dim = state.getActiveDimension();
                    if (dim != null && dim.contains("the_end")) {
                        loadDimensionMacroPreset(1);
                    } else {
                        loadDimensionMacroPreset(0);
                    }
                    StudioLiveCompiler.compileAndApplyLive(this);
                } else {
                    state.setGraphMode(StudioState.GraphMode.BIOME_GRAPH);
                    StudioLiveCompiler.loadBiomeIntoCanvas(state.getActiveBiome(), this);
                }
                return true;
            }
        }

        // 3. Check Sockets FIRST (Output and Input sockets on all cards)
        for (int i = nodes.size() - 1; i >= 0; i--) {
            NodeCard node = nodes.get(i);
            int cx = (int) node.canvasX;
            int cy = (int) node.canvasY;
            int cw = node.cardWidth;

            // Output sockets (Right edge)
            for (int so = 0; so < node.outputs.size(); so++) {
                SocketDef sock = node.outputs.get(so);
                int sy = node.getOutputSocketY(so);
                if (Math.abs(mcx - (cx + cw)) <= 7 && Math.abs(mcy - sy) <= 7) {
                    if (button == 1) {
                        // Right-click output socket -> detach all wires from this output!
                        wires.removeIf(w -> w.fromNodeId.equals(node.id) && w.fromSocketId.equals(sock.id()));
                        state.setStatusMessage("Detached wires from " + node.title + "." + sock.label());
                        return true;
                    }
                    // Left-click output socket -> start dragging wire from output
                    isDraggingWire = true;
                    wireDragFromOutput = true;
                    wireSourceNode = node;
                    wireSourceSocket = sock;
                    return true;
                }
            }

            // Input sockets (Left edge)
            for (int si = 0; si < node.inputs.size(); si++) {
                SocketDef sock = node.inputs.get(si);
                int sy = node.getInputSocketY(si);
                if (Math.abs(mcx - cx) <= 7 && Math.abs(mcy - sy) <= 7) {
                    WireConnection existing = findWireToInput(node.id, sock.id());
                    if (button == 1) {
                        // Right-click input socket -> detach wire!
                        if (existing != null) {
                            wires.remove(existing);
                            state.setStatusMessage("Unattached wire from " + node.title + "." + sock.label());
                        }
                        return true;
                    }
                    if (existing != null) {
                        // Left-click connected input socket -> pick up / detach wire end so user can rewire or drop to delete!
                        wires.remove(existing);
                        NodeCard origFrom = findNode(existing.fromNodeId);
                        if (origFrom != null) {
                            int outIdx = findSocketIndex(origFrom.outputs, existing.fromSocketId);
                            if (outIdx >= 0) {
                                isDraggingWire = true;
                                wireDragFromOutput = true;
                                wireSourceNode = origFrom;
                                wireSourceSocket = origFrom.outputs.get(outIdx);
                                return true;
                            }
                        }
                    } else {
                        // Left-click unconnected input socket -> drag wire from input toward an output
                        isDraggingWire = true;
                        wireDragFromOutput = false;
                        wireSourceNode = node;
                        wireSourceSocket = sock;
                        return true;
                    }
                }
            }
        }

        // 4. Check Node Card Header, Delete [x], and Editable Parameter Fields
        for (int i = nodes.size() - 1; i >= 0; i--) {
            NodeCard node = nodes.get(i);
            int cx = (int) node.canvasX;
            int cy = (int) node.canvasY;
            int cw = node.cardWidth;
            int ch = node.cardHeight;

            if (mcx >= cx && mcx < cx + cw && mcy >= cy && mcy < cy + ch) {
                selectedNode = node;
                // Bring clicked node to front
                nodes.remove(i);
                nodes.add(node);

                // Right-click on header or clicking [x] deletes node
                int delX = cx + cw - 12;
                int delY = cy + 3;
                if ((mcx >= delX && mcx < delX + 10 && mcy >= delY && mcy < delY + 10)
                        || (button == 1 && mcy < cy + NodeCard.HEADER_H)) {
                    deleteNode(node);
                    return true;
                }

                // Double-clicking header on a Biome-referencing Macro node steps directly into that Biome's Terrain Graph!
                if (doubleClick && button == 0 && mcy < cy + NodeCard.HEADER_H) {
                    String targetBiome = getParamValue(node, "biome", null);
                    if (targetBiome == null) {
                        targetBiome = getParamValue(node, "biomeOverride", null);
                    }
                    if (targetBiome != null && !targetBiome.isBlank()) {
                        state.setActiveBiome(targetBiome);
                        state.setGraphMode(StudioState.GraphMode.BIOME_GRAPH);
                        setPresetDropdownToBiome();
                        StudioLiveCompiler.loadBiomeIntoCanvas(targetBiome, this);
                        state.setStatusMessage("Stepped into Biome Terrain Graph: [" + targetBiome + "]");
                        return true;
                    }
                }

                // Check Editable Parameter Rows
                for (int pi = 0; pi < node.params.size(); pi++) {
                    ParamInstance param = node.params.get(pi);
                    int py = node.getParamRowY(pi);
                    int boxX = cx + 64;
                    int boxW = cw - 68;
                    int boxH = 11;

                    if (mcx >= boxX && mcx < boxX + boxW && mcy >= py && mcy < py + boxH) {
                        handleParamClick(param, mcx, boxX, boxW, button);
                        return true;
                    }
                }

                // Otherwise start dragging the NodeCard
                draggedNode = node;
                dragOffsetX = mcx - node.canvasX;
                dragOffsetY = mcy - node.canvasY;
                return true;
            }
        }

        // 5. Double-Click (or Right-Click) on empty canvas summons Smart Search Drawer
        if ((doubleClick && button == 0) || button == 1) {
            if (mx >= x && mx < x + width && my >= y && my < y + height) {
                openSearchDrawerAt((int) mx, (int) my, mcx, mcy);
                return true;
            }
        }

        // 6. Start Canvas Panning
        if (mx >= x && mx < x + width && my >= y && my < y + height) {
            selectedNode = null;
            this.isPanning = true;
            this.panStartX = mx - panX;
            this.panStartY = my - panY;
            return true;
        }

        return false;
    }

    private void handleParamClick(ParamInstance param, double mcx, int boxX, int boxW, int button) {
        ParamType type = param.def.type();
        if (type == ParamType.FLOAT || type == ParamType.INT) {
            int btnW = 11;
            int valX = boxX + btnW;
            int valW = boxW - (btnW * 2);
            if (mcx < valX) {
                param.stepBy(-1);
                StudioLiveCompiler.compileAndApplyLive(this);
            } else if (mcx >= valX + valW) {
                param.stepBy(1);
                StudioLiveCompiler.compileAndApplyLive(this);
            } else {
                // Start horizontal scrubbing AND allow typing!
                this.scrubbingParam = param;
                this.scrubStartX = mcx;
                this.scrubStartVal = param.asDouble();
                this.editingParam = param;
                this.editingBuffer = param.value;
            }
        } else if (type == ParamType.BOOLEAN) {
            param.stepBy(1);
            StudioLiveCompiler.compileAndApplyLive(this);
        } else if (type == ParamType.ENUM) {
            param.stepBy(button == 1 ? -1 : 1);
            StudioLiveCompiler.compileAndApplyLive(this);
        } else if (type == ParamType.STRUCTURE) {
            openStructurePicker((String selectedStructId) -> {
                param.value = selectedStructId;
                StudioLiveCompiler.compileAndApplyLive(this);
            });
        } else if (type == ParamType.BLOCK) {
            if (blockPicker != null) {
                blockPicker.open((Identifier selectedId) -> {
                    param.value = selectedId.toString();
                    StudioLiveCompiler.compileAndApplyLive(this);
                });
            }
        } else {
            this.editingParam = param;
            this.editingBuffer = param.value;
        }
    }

    public void openStructurePicker(java.util.function.Consumer<String> callback) {
        this.structurePickerOpen = true;
        this.structureSearch = "";
        this.structureScroll = 0;
        this.structureSelectCallback = callback;
    }

    public boolean isStructurePickerOpen() {
        return structurePickerOpen;
    }

    public void renderStructurePickerModal(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, 0xBB000000);

        int mw = Math.min(316, width - 16);
        int mh = Math.min(198, height - 12);
        int mx = x + (width - mw) / 2;
        int my = y + (height - mh) / 2;

        StudioTheme.drawBeveledPanel(graphics, mx, my, mw, mh, 0xF8181626);
        graphics.text(font, "§d§lSelect Landmark Structure / Prefab", mx + 10, my + 7, StudioTheme.TEXT_GOLD, true);

        int closeX = mx + mw - 22;
        boolean closeHov = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= my + 4 && mouseY < my + 18;
        StudioTheme.drawButton(graphics, font, Component.literal("X"), closeX, my + 4, 16, 14, closeHov, true);

        // Search input box
        int searchY = my + 21;
        StudioTheme.drawInsetWell(graphics, mx + 8, searchY, mw - 16, 15, 0xFF0D0E16);
        String dispQ = structureSearch.isEmpty()
                ? "§7Filter structures (or type custom ID & press Enter)..."
                : "§f" + structureSearch + "_";
        graphics.text(font, font.plainSubstrByWidth(dispQ, mw - 24), mx + 12, searchY + 4, StudioTheme.TEXT_WHITE, false);

        List<StructureCatalogEntry> filtered = getFilteredStructures();
        int listY = searchY + 18;
        int listH = (my + mh) - listY - 6;
        StudioTheme.drawInsetWell(graphics, mx + 8, listY, mw - 16, listH, 0xFF0E1018);

        int rowH = 22;
        int maxVis = Math.max(1, (listH - 4) / rowH);
        int maxScroll = Math.max(0, filtered.size() - maxVis);
        structureScroll = Math.clamp(structureScroll, 0, maxScroll);

        StructureCatalogEntry hoveredEntry = null;
        for (int i = 0; i < maxVis; i++) {
            int idx = i + structureScroll;
            if (idx >= filtered.size()) break;
            StructureCatalogEntry entry = filtered.get(idx);
            int ry = listY + 2 + i * rowH;
            boolean hov = mouseX >= mx + 10 && mouseX < mx + mw - 14 && mouseY >= ry && mouseY < ry + rowH - 2;
            if (hov) hoveredEntry = entry;

            graphics.fill(mx + 10, ry, mx + mw - 14, ry + rowH - 2, hov ? 0xFF32244C : ((idx % 2 == 0) ? 0xFF171524 : 0xFF13121E));

            if (!entry.icon().isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(mx + 13, ry + 2);
                graphics.pose().scale(0.9f, 0.9f);
                graphics.fakeItem(entry.icon(), 0, 0);
                graphics.pose().popMatrix();
            }

            int btnW = 56;
            int btnX = mx + mw - 16 - btnW;
            int textMaxW = Math.max(60, btnX - (mx + 32) - 6);
            String titleStr = "§f" + entry.displayName() + " §7[" + entry.category() + "]";
            graphics.text(font, font.plainSubstrByWidth(titleStr, textMaxW), mx + 32, ry + 3, StudioTheme.TEXT_WHITE, false);
            graphics.text(font, font.plainSubstrByWidth("§d" + entry.id(), textMaxW), mx + 32, ry + 12, StudioTheme.TEXT_GRAY, false);

            StudioTheme.drawButton(graphics, font, Component.literal("§aChoose"), btnX, ry + 2, btnW, rowH - 6, hov, true);
        }

        if (maxScroll > 0) {
            int sbX = mx + mw - 12;
            int sbH = listH - 4;
            graphics.fill(sbX, listY + 2, sbX + 3, listY + 2 + sbH, 0xFF08090E);
            int thumbH = Math.max(12, sbH * maxVis / Math.max(1, filtered.size()));
            int thumbY = listY + 2 + (int) ((sbH - thumbH) * ((float) structureScroll / maxScroll));
            graphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, StudioTheme.PANEL_BORDER_LIGHT);
        }

        if (hoveredEntry != null) {
            graphics.setTooltipForNextFrame(
                    Component.literal("§d" + hoveredEntry.displayName() + " §7(" + hoveredEntry.id() + ") — §f" + hoveredEntry.description()),
                    mouseX, mouseY
            );
        }
    }

    public boolean handleStructurePickerClick(double mx, double my, int button) {
        int mw = Math.min(316, width - 16);
        int mh = Math.min(198, height - 12);
        int modalX = x + (width - mw) / 2;
        int modalY = y + (height - mh) / 2;

        if (mx < modalX || mx >= modalX + mw || my < modalY || my >= modalY + mh) {
            structurePickerOpen = false;
            return true;
        }
        if (mx >= modalX + mw - 22 && mx < modalX + mw - 6 && my >= modalY + 4 && my < modalY + 18) {
            structurePickerOpen = false;
            return true;
        }

        List<StructureCatalogEntry> filtered = getFilteredStructures();
        int listY = modalY + 39;
        int listH = (modalY + mh) - listY - 6;
        int rowH = 22;
        int maxVis = Math.max(1, (listH - 4) / rowH);

        for (int i = 0; i < maxVis; i++) {
            int idx = i + structureScroll;
            if (idx >= filtered.size()) break;
            int ry = listY + 2 + i * rowH;
            if (my >= ry && my < ry + rowH - 2 && mx >= modalX + 10 && mx < modalX + mw - 14) {
                StructureCatalogEntry chosen = filtered.get(idx);
                structurePickerOpen = false;
                if (structureSelectCallback != null) {
                    structureSelectCallback.accept(chosen.id());
                }
                return true;
            }
        }
        return true;
    }

    private List<StructureCatalogEntry> getFilteredStructures() {
        String q = structureSearch.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return STRUCTURE_CATALOG;
        List<StructureCatalogEntry> out = new ArrayList<>();
        for (StructureCatalogEntry e : STRUCTURE_CATALOG) {
            if (e.id().toLowerCase(Locale.ROOT).contains(q)
                    || e.displayName().toLowerCase(Locale.ROOT).contains(q)
                    || e.category().toLowerCase(Locale.ROOT).contains(q)
                    || e.description().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * Collects the live Dimension Macro Nodes on this canvas and renders a Top-Down Dimension Faux-Minimap
     * in the bottom-right corner of the Node Editor showing Island Size, Radial Zones, Ring Towers,
     * Landmark Probability Annuli, Spline Bridges, and Outside-Bounds Fallback!
     */
    private void renderCanvasFauxMinimap(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int mapSize = Math.min(104, Math.max(82, height - 50));
        int mapX = x + width - mapSize - 8;
        int mapY = y + height - mapSize - 20;

        OutsideBoundsFallback fallback = OutsideBoundsFallback.DEFAULT_BIOMES;
        float boundsRadius = 640.0f;
        List<StudioState.EditableZoneRule> zones = new ArrayList<>();
        List<StudioState.EditableRingRule> rings = new ArrayList<>();
        List<StudioState.EditableLandmarkRule> landmarks = new ArrayList<>();

        for (NodeCard card : nodes) {
            String type = card.schema.typeName();
            if ("DimensionOutput".equals(type)) {
                try {
                    fallback = OutsideBoundsFallback.valueOf(getParamValue(card, "outsideFallback", "DEFAULT_BIOMES").toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {}
                boundsRadius = (float) getParamDouble(card, "boundsRadius", 640.0);
            } else if ("SpawnIslandAnchor".equals(type)) {
                float ir = (float) getParamDouble(card, "islandRadius", 220.0);
                float fall = (float) getParamDouble(card, "coastFalloff", 48.0);
                float mr = (float) getParamDouble(card, "moatOuterRadius", 360.0);
                String b = getParamValue(card, "biome", "TemperateForest");
                zones.add(new StudioState.EditableZoneRule("spawn_island", 0.0f, ir, fall, ZoneMode.ISLAND_PLATEAU, b, 74.0f, false));
                if (mr > ir + 8.0f) {
                    zones.add(new StudioState.EditableZoneRule("spawn_moat", ir, mr, fall, ZoneMode.BIOME_OVERRIDE, "Ocean", 44.0f, false));
                }
            } else if ("RadialZone".equals(type)) {
                float inR = (float) getParamDouble(card, "innerRadius", 0.0);
                float outR = (float) getParamDouble(card, "outerRadius", 180.0);
                float fall = (float) getParamDouble(card, "falloffWidth", 32.0);
                ZoneMode mode = ZoneMode.ISLAND_PLATEAU;
                try {
                    mode = ZoneMode.valueOf(getParamValue(card, "zoneMode", "ISLAND_PLATEAU").toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {}
                String b = getParamValue(card, "biomeOverride", "TemperateForest");
                zones.add(new StudioState.EditableZoneRule(card.id, inR, outR, fall, mode, b, 68.0f, false));
            } else if ("RingLandmarks".equals(type)) {
                float rr = (float) getParamDouble(card, "ringRadius", 43.0);
                int cnt = getParamInt(card, "count", 10);
                rings.add(new StudioState.EditableRingRule(card.id, rr, cnt, 4, 76, 103, "minecraft:obsidian", true));
            } else if ("RelationalLandmark".equals(type)) {
                String lid = getParamValue(card, "landmarkId", card.id);
                String st = getParamValue(card, "prefabId", "worldgenv2:watchtower_01");
                int cnt = Math.clamp(getParamInt(card, "count", 1), 1, 12);
                float minD = (float) getParamDouble(card, "minDistance", 110.0);
                float maxD = (float) Math.max(minD + 16.0, getParamDouble(card, "maxDistance", 180.0));
                boolean br = Boolean.parseBoolean(getParamValue(card, "connectBridge", "true"));
                landmarks.add(new StudioState.EditableLandmarkRule(lid, "spawn_island", minD, maxD, st, cnt, br, 3.5f));
            } else if ("SubIslandCluster".equals(type)) {
                int cnt = Math.clamp(getParamInt(card, "count", 4), 1, 16);
                float minD = (float) getParamDouble(card, "minDistance", 220.0);
                float maxD = (float) Math.max(minD + 24.0, getParamDouble(card, "maxDistance", 440.0));
                float subR = (float) getParamDouble(card, "islandRadius", 48.0);
                String st = getParamValue(card, "structure", "worldgenv2:ancient_shrine");
                zones.add(new StudioState.EditableZoneRule("sub_islands", minD, maxD, subR, ZoneMode.OUTER_ISLANDS, "TemperateForest", 66.0f, false));
                landmarks.add(new StudioState.EditableLandmarkRule("satellite_cluster", "spawn_island", minD, maxD, st, cnt, false, 3.0f));
            }
        }

        // Top header bar above the Faux-Minimap with [-] / [+] zoom buttons
        StudioTheme.drawBeveledPanel(graphics, mapX, mapY - 14, mapSize, 14, 0xF0181C2B);
        graphics.text(font, "§eRadar §7(±" + (int) fauxMinimapRadius + "m)", mapX + 4, mapY - 11, StudioTheme.TEXT_WHITE, false);
        graphics.text(font, "§f[-] [+]", mapX + mapSize - 30, mapY - 11, StudioTheme.TEXT_CYAN, false);

        renderDimensionFauxMinimap(
                graphics, font, mapX, mapY, mapSize, fauxMinimapRadius,
                fallback, boundsRadius, zones, rings, landmarks, mouseX, mouseY
        );
    }

    /**
     * Reusable Top-Down Dimension Faux-Minimap / Probability & Bounds Visualizer.
     * Renders:
     * <ul>
     *   <li>Outside-Bounds Fallback region ({@code DEFAULT_BIOMES} Voronoi, {@code VOID} abyss, {@code OCEAN} water)</li>
     *   <li>Modification Bounds Circle ({@code boundsRadius})</li>
     *   <li>Approximate Island Size & Organic Coastline ({@code ISLAND_PLATEAU})</li>
     *   <li>Concentric Radial Zones ({@code VOID_MOAT} ring, {@code OUTER_ISLANDS} archipelago speckles, {@code BIOME_OVERRIDE} ocean moat)</li>
     *   <li>Ring Landmarks (e.g. 10 Obsidian Towers around {@code R=43m})</li>
     *   <li>Connected Landmark Probability Band ({@code [minDistance .. maxDistance]}), Spline Bridges & Structure Pins!</li>
     * </ul>
     */
    public static void renderDimensionFauxMinimap(
            GuiGraphicsExtractor graphics,
            Font font,
            int mapX,
            int mapY,
            int mapSize,
            float viewRadiusBlocks,
            OutsideBoundsFallback fallback,
            float boundsRadius,
            List<StudioState.EditableZoneRule> zones,
            List<StudioState.EditableRingRule> rings,
            List<StudioState.EditableLandmarkRule> landmarks,
            int mouseX,
            int mouseY
    ) {
        StudioTheme.drawBeveledPanel(graphics, mapX, mapY, mapSize, mapSize, 0xFF0A0C12);
        int innerX = mapX + 2;
        int innerY = mapY + 2;
        int innerS = mapSize - 4;
        float halfS = innerS / 2.0f;
        float scale = Math.max(100.0f, viewRadiusBlocks);

        // 1. Sample 3x3 pixel cells for organic island size, concentric zones, outer islands & outside-bounds fallback
        int step = 3;
        for (int py = 0; py < innerS; py += step) {
            for (int px = 0; px < innerS; px += step) {
                float wx = ((px + step * 0.5f - halfS) / halfS) * scale;
                float wz = ((py + step * 0.5f - halfS) / halfS) * scale;
                float dist = (float) Math.hypot(wx, wz);
                double ang = Math.atan2(wz, wx);

                // Organic coastline noise perturbation
                float coastWarp = (float) (Math.sin(ang * 5.0 + 0.7) * 0.08 + Math.cos(ang * 9.0 - 1.2) * 0.05);
                float warpedDist = dist * (1.0f + coastWarp);

                int cellColor = sampleFauxBackground(wx, wz, fallback);

                // Check Radial Zones
                for (StudioState.EditableZoneRule z : zones) {
                    if (warpedDist >= z.innerRadius && warpedDist <= z.outerRadius) {
                        if (z.mode == ZoneMode.ISLAND_PLATEAU) {
                            cellColor = (warpedDist > z.outerRadius - Math.max(18.0f, z.falloff * 0.6f))
                                    ? 0xFFD2B868 // Sandy beach / coast rim
                                    : (z.floating ? 0xFFBEB886 : 0xFF3A8C46); // End island stone or lush island green
                        } else if (z.mode == ZoneMode.VOID_MOAT) {
                            cellColor = 0xFF07050D;
                        } else if (z.mode == ZoneMode.OUTER_ISLANDS) {
                            // Procedural archipelago speckles
                            double islandNoise = Math.sin(wx * 0.028) * Math.cos(wz * 0.028) + 0.35 * Math.sin((wx + wz) * 0.06);
                            cellColor = (islandNoise > 0.32) ? 0xFF9E9668 : 0xFF090712;
                        } else if (z.mode == ZoneMode.BIOME_OVERRIDE) {
                            cellColor = (z.targetBiome != null && z.targetBiome.toLowerCase(Locale.ROOT).contains("ocean"))
                                    ? 0xFF164A84
                                    : 0xFF2D7642;
                        }
                    }
                }

                // Outside Modification Bounds override
                if (boundsRadius > 0.0f && dist > boundsRadius) {
                    cellColor = sampleFauxBackground(wx, wz, fallback);
                }

                int drawW = Math.min(step, innerS - px);
                int drawH = Math.min(step, innerS - py);
                graphics.fill(innerX + px, innerY + py, innerX + px + drawW, innerY + py + drawH, cellColor);
            }
        }

        int cx = innerX + (int) halfS;
        int cy = innerY + (int) halfS;

        // 2. Draw Landmark Probability Annuli ([minDistance .. maxDistance] bands)
        int lmIdx = 0;
        for (StudioState.EditableLandmarkRule lm : landmarks) {
            int rMinPx = Math.round((lm.minDistance / scale) * halfS);
            int rMaxPx = Math.round((lm.maxDistance / scale) * halfS);
            if (rMaxPx > 2 && rMinPx < innerS / 2) {
                drawFauxCircle(graphics, cx, cy, rMinPx, innerX, innerY, innerS, 0x88FFAA33);
                drawFauxCircle(graphics, cx, cy, rMaxPx, innerX, innerY, innerS, 0x88FFAA33);
            }

            // Draw count instances of this landmark around the probability band + spline bridge
            int count = Math.max(1, lm.count);
            float midDist = (lm.minDistance + lm.maxDistance) * 0.5f;
            float rMidPx = (midDist / scale) * halfS;
            for (int c = 0; c < count; c++) {
                double theta = -Math.PI * 0.35 + (lmIdx * 1.35) + (c * (Math.PI * 2.0 / count));
                int lx = cx + Math.round((float) Math.cos(theta) * rMidPx);
                int ly = cy + Math.round((float) Math.sin(theta) * rMidPx);
                if (lx >= innerX + 2 && lx < innerX + innerS - 2 && ly >= innerY + 2 && ly < innerY + innerS - 2) {
                    if (lm.connectBridge) {
                        drawMiniLine(graphics, cx, cy, lx, ly, 0xCCBBAA88);
                    }
                    graphics.fill(lx - 2, ly - 2, lx + 3, ly + 3, 0xFFFFCC00);
                    graphics.fill(lx - 1, ly - 1, lx + 2, ly + 2, 0xFFFF5500);
                }
            }
            lmIdx++;
        }

        // 3. Draw Ring Landmarks (e.g. 10 Obsidian Towers at R=43m)
        for (StudioState.EditableRingRule ring : rings) {
            float rPx = Math.max(5.0f, (ring.ringRadius / scale) * halfS);
            int cnt = Math.clamp(ring.count, 1, 32);
            for (int i = 0; i < cnt; i++) {
                double ang = (Math.PI * 2.0 * i) / cnt;
                int tx = cx + Math.round((float) Math.cos(ang) * rPx);
                int ty = cy + Math.round((float) Math.sin(ang) * rPx);
                if (tx >= innerX + 1 && tx < innerX + innerS - 1 && ty >= innerY + 1 && ty < innerY + innerS - 1) {
                    graphics.fill(tx - 1, ty - 1, tx + 2, ty + 2, 0xFFD866FF);
                }
            }
        }

        // 4. Draw Modification Bounds Circle (boundsRadius)
        if (boundsRadius > 0.0f) {
            int bPx = Math.round((boundsRadius / scale) * halfS);
            if (bPx > 3 && bPx < innerS / 2) {
                drawFauxCircle(graphics, cx, cy, bPx, innerX, innerY, innerS, 0xFFFF5555);
            }
        }

        // 5. Origin Crosshair (0, 0)
        graphics.fill(cx - 2, cy, cx + 3, cy + 1, 0xFFFFFFFF);
        graphics.fill(cx, cy - 2, cx + 1, cy + 3, 0xFFFFFFFF);

        // 6. Bottom Legend Overlay inside Map
        String fbLabel = (boundsRadius <= 0.0f) ? "Bounds: Inf" : ("Bounds: " + (int) boundsRadius + "m->" + fallback.name());
        graphics.fill(innerX, innerY + innerS - 10, innerX + innerS, innerY + innerS, 0xAA08090F);
        graphics.text(font, font.plainSubstrByWidth("§7" + fbLabel, innerS - 4), innerX + 2, innerY + innerS - 9, StudioTheme.TEXT_GRAY, false);

        // 7. Interactive Hover Readout Tooltip
        if (mouseX >= innerX && mouseX < innerX + innerS && mouseY >= innerY && mouseY < innerY + innerS) {
            float hwx = ((mouseX - innerX - halfS) / halfS) * scale;
            float hwz = ((mouseY - innerY - halfS) / halfS) * scale;
            int hDist = Math.round((float) Math.hypot(hwx, hwz));
            String regionDesc = "Outside Bounds (" + fallback.name() + ")";
            for (StudioState.EditableZoneRule z : zones) {
                if (hDist >= z.innerRadius && hDist <= z.outerRadius) {
                    regionDesc = "Zone: " + z.id + " (" + z.mode.name() + ")";
                }
            }
            for (StudioState.EditableLandmarkRule lm : landmarks) {
                if (hDist >= lm.minDistance && hDist <= lm.maxDistance) {
                    regionDesc += " • Landmark Probability Band: " + lm.id + " [" + shortStructId(lm.structureId) + " x" + lm.count + "]";
                }
            }
            graphics.setTooltipForNextFrame(
                    Component.literal("§6Top-Down Dimension Preview §7(R = " + hDist + "m from 0,0) • §e" + regionDesc),
                    mouseX, mouseY
            );
        }
    }

    private static String shortStructId(String id) {
        if (id == null) return "watchtower_01";
        return id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
    }

    private static int sampleFauxBackground(float wx, float wz, OutsideBoundsFallback fallback) {
        if (fallback == OutsideBoundsFallback.VOID) {
            int hash = (((int) wx * 734287) ^ ((int) wz * 912931)) & 0xFF;
            return (hash < 6) ? 0xFF282040 : 0xFF07060E;
        }
        if (fallback == OutsideBoundsFallback.OCEAN) {
            return 0xFF143C72;
        }
        // DEFAULT_BIOMES or SINGLE_BIOME: Multi-biome Voronoi mosaic
        double cell = Math.sin(wx * 0.008) + Math.cos(wz * 0.008);
        if (cell > 0.6) return 0xFF2E7D38;
        if (cell > -0.2) return 0xFF4A9440;
        if (cell > -0.8) return 0xFF245E3E;
        return 0xFF1B4E80;
    }

    private static void drawFauxCircle(GuiGraphicsExtractor graphics, int cx, int cy, int rPx, int minX, int minY, int size, int color) {
        int steps = Math.max(36, rPx * 4);
        for (int i = 0; i < steps; i += 2) {
            double a = (Math.PI * 2.0 * i) / steps;
            int px = cx + Math.round((float) Math.cos(a) * rPx);
            int py = cy + Math.round((float) Math.sin(a) * rPx);
            if (px >= minX && px < minX + size && py >= minY && py < minY + size) {
                graphics.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    private static void drawMiniLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int s = 0; s <= steps; s++) {
            int px = x0 + ((x1 - x0) * s) / steps;
            int py = y0 + ((y1 - y0) * s) / steps;
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void openSearchDrawerAt(int screenX, int screenY, double targetCanvasX, double targetCanvasY) {
        this.searchDrawerOpen = true;
        this.searchDrawerX = screenX;
        this.searchDrawerY = screenY;
        this.spawnTargetCanvasX = targetCanvasX;
        this.spawnTargetCanvasY = targetCanvasY;
        this.searchFilter = "";
        updateCatalogFilter();
    }

    private void deleteNode(NodeCard node) {
        nodes.remove(node);
        wires.removeIf(w -> w.fromNodeId.equals(node.id) || w.toNodeId.equals(node.id));
        if (selectedNode == node) {
            selectedNode = null;
        }
        StudioState.get().setStatusMessage("Deleted node " + node.title);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        boolean shouldRecompile = false;
        if (isDraggingWire && wireSourceNode != null && wireSourceSocket != null) {
            // Check if released over a compatible target socket
            StudioState state = StudioState.get();
            double mcx = (event.x() - x - state.getCanvasPanX()) / state.getCanvasZoom();
            double mcy = (event.y() - y - state.getCanvasPanY()) / state.getCanvasZoom();

            for (int i = nodes.size() - 1; i >= 0; i--) {
                NodeCard targetNode = nodes.get(i);
                if (targetNode == wireSourceNode) continue;

                int cx = (int) targetNode.canvasX;
                int cw = targetNode.cardWidth;

                if (wireDragFromOutput) {
                    // Look for an Input socket on targetNode
                    for (int si = 0; si < targetNode.inputs.size(); si++) {
                        SocketDef targetSock = targetNode.inputs.get(si);
                        int sy = targetNode.getInputSocketY(si);
                        if (Math.abs(mcx - cx) <= 9 && Math.abs(mcy - sy) <= 9) {
                            if (wireSourceSocket.domain().isCompatibleWith(targetSock.domain())) {
                                connectSockets(wireSourceNode, wireSourceSocket.id(), targetNode, targetSock.id());
                                state.setStatusMessage("Connected " + wireSourceNode.title + " -> " + targetNode.title);
                                shouldRecompile = true;
                            } else {
                                state.setStatusMessage("Incompatible socket types: " + wireSourceSocket.domain().label + " vs " + targetSock.domain().label);
                            }
                            break;
                        }
                    }
                } else {
                    // Look for an Output socket on targetNode
                    for (int so = 0; so < targetNode.outputs.size(); so++) {
                        SocketDef targetSock = targetNode.outputs.get(so);
                        int sy = targetNode.getOutputSocketY(so);
                        if (Math.abs(mcx - (cx + cw)) <= 9 && Math.abs(mcy - sy) <= 9) {
                            if (targetSock.domain().isCompatibleWith(wireSourceSocket.domain())) {
                                connectSockets(targetNode, targetSock.id(), wireSourceNode, wireSourceSocket.id());
                                state.setStatusMessage("Connected " + targetNode.title + " -> " + wireSourceNode.title);
                                shouldRecompile = true;
                            } else {
                                state.setStatusMessage("Incompatible socket types: " + targetSock.domain().label + " vs " + wireSourceSocket.domain().label);
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (scrubbingParam != null && editingParam == null) {
            shouldRecompile = true;
        }

        isDraggingWire = false;
        wireSourceNode = null;
        wireSourceSocket = null;
        draggedNode = null;
        scrubbingParam = null;
        isPanning = false;

        if (shouldRecompile) {
            StudioLiveCompiler.compileAndApplyLive(this);
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        StudioState state = StudioState.get();
        double zoom = state.getCanvasZoom();
        double mcx = (event.x() - x - state.getCanvasPanX()) / zoom;
        double mcy = (event.y() - y - state.getCanvasPanY()) / zoom;

        if (isDraggingWire) {
            this.wireMouseCanvasX = mcx;
            this.wireMouseCanvasY = mcy;
            return true;
        }

        if (scrubbingParam != null) {
            double deltaX = mcx - scrubStartX;
            if (Math.abs(deltaX) > 1.5) {
                // Scrubbing active -> exit typing mode and update value live
                editingParam = null;
                double step = scrubbingParam.def.step() > 0 ? scrubbingParam.def.step() : 0.1;
                int steps = (int) (deltaX / 2.0);
                scrubbingParam.setDouble(scrubStartVal + steps * step);
                long now = System.currentTimeMillis();
                if (now - lastLiveApplyMs > 220L) {
                    lastLiveApplyMs = now;
                    StudioLiveCompiler.compileAndApplyLive(this);
                }
            }
            return true;
        }

        if (draggedNode != null) {
            draggedNode.canvasX = mcx - dragOffsetX;
            draggedNode.canvasY = mcy - dragOffsetY;
            return true;
        }

        if (isPanning) {
            state.setCanvasPanX(event.x() - panStartX);
            state.setCanvasPanY(event.y() - panStartY);
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (structurePickerOpen) {
            int maxScroll = Math.max(0, getFilteredStructures().size() - 6);
            this.structureScroll = (int) Math.max(0, Math.min(maxScroll, structureScroll - scrollY * 2));
            return true;
        }
        if (presetDropdown.isExpanded() && presetDropdown.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (searchDrawerOpen) {
            int maxScroll = Math.max(0, filteredCatalog.size() - 8);
            this.searchScroll = (int) Math.max(0, Math.min(maxScroll, searchScroll - scrollY * 2));
            return true;
        }

        // Scroll over Top-Down Dimension Faux-Minimap zooms the radar radius!
        if (StudioState.get().isDimensionGraphMode() && showFauxMinimap && width >= 260 && height >= 140) {
            int mapSize = Math.min(104, Math.max(82, height - 50));
            int mapX = x + width - mapSize - 8;
            int mapY = y + height - mapSize - 20;
            if (mouseX >= mapX && mouseX < mapX + mapSize && mouseY >= mapY - 14 && mouseY < mapY + mapSize) {
                float factor = scrollY > 0 ? (1.0f / 1.25f) : 1.25f;
                fauxMinimapRadius = Math.clamp(fauxMinimapRadius * factor, 200.0f, 5000.0f);
                return true;
            }
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            StudioState state = StudioState.get();
            double oldZoom = state.getCanvasZoom();
            double newZoom = Math.max(0.4, Math.min(2.2, oldZoom + (scrollY * 0.1)));
            if (newZoom != oldZoom) {
                // Zoom toward mouse cursor
                double panX = state.getCanvasPanX();
                double panY = state.getCanvasPanY();
                double wx = (mouseX - x - panX) / oldZoom;
                double wy = (mouseY - y - panY) / oldZoom;
                state.setCanvasZoom(newZoom);
                state.setCanvasPanX(mouseX - x - wx * newZoom);
                state.setCanvasPanY(mouseY - y - wy * newZoom);
            }
            return true;
        }
        return false;
    }

    public boolean isEditingParam() {
        return editingParam != null || searchDrawerOpen || structurePickerOpen;
    }

    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (structurePickerOpen) {
            if (key == 256) {
                structurePickerOpen = false;
                return true;
            }
            if (key == 259 && !structureSearch.isEmpty()) {
                structureSearch = structureSearch.substring(0, structureSearch.length() - 1);
                structureScroll = 0;
                return true;
            }
            if (key == 257 || key == 335) {
                List<StructureCatalogEntry> filtered = getFilteredStructures();
                String chosen = !filtered.isEmpty() ? filtered.getFirst().id() : structureSearch.trim();
                if (!chosen.isEmpty() && structureSelectCallback != null) {
                    structurePickerOpen = false;
                    structureSelectCallback.accept(chosen);
                }
                return true;
            }
            return true;
        }

        // Active parameter keyboard editing
        if (editingParam != null) {
            if (key == 257 || key == 335 || key == 256) { // Enter, KP_Enter, Escape
                commitEditingParam();
                return true;
            }
            if (key == 259) { // Backspace
                if (!editingBuffer.isEmpty()) {
                    editingBuffer = editingBuffer.substring(0, editingBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }

        // Smart Search Drawer keyboard controls
        if (searchDrawerOpen) {
            if (key == 256) { // Escape
                searchDrawerOpen = false;
                return true;
            }
            if (key == 259) { // Backspace
                if (!searchFilter.isEmpty()) {
                    searchFilter = searchFilter.substring(0, searchFilter.length() - 1);
                    updateCatalogFilter();
                }
                return true;
            }
            if (key == 257 && !filteredCatalog.isEmpty()) { // Enter spawns top match
                NodeSchema top = filteredCatalog.getFirst();
                spawnNode(top.id(), spawnTargetCanvasX, spawnTargetCanvasY);
                searchDrawerOpen = false;
                return true;
            }
            return false;
        }

        // Delete or Backspace deletes selected node
        if ((key == 261 || key == 259) && selectedNode != null) {
            deleteNode(selectedNode);
            return true;
        }

        // Spacebar summons Smart Search Drawer
        if (key == 32) {
            StudioState state = StudioState.get();
            openSearchDrawerAt(x + width / 2 - 110, y + height / 2 - 90,
                    (width / 2.0 - state.getCanvasPanX()) / state.getCanvasZoom() - 75,
                    (height / 2.0 - state.getCanvasPanY()) / state.getCanvasZoom() - 35);
            return true;
        }

        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (c < 32 || c == 127) return false;

        if (structurePickerOpen) {
            structureSearch += c;
            structureScroll = 0;
            return true;
        }

        if (editingParam != null) {
            ParamType t = editingParam.def.type();
            if (t == ParamType.FLOAT || t == ParamType.INT) {
                if ((c >= '0' && c <= '9') || c == '-' || (c == '.' && t == ParamType.FLOAT)) {
                    editingBuffer += c;
                }
            } else {
                editingBuffer += c;
            }
            return true;
        }

        if (searchDrawerOpen) {
            searchFilter += c;
            updateCatalogFilter();
            return true;
        }

        return false;
    }

    /**
     * Serializes the current node canvas graph into clean, indented JSON with preserved canvas positions.
     */
    public String exportGraphToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(StudioState.get().getActiveBiome()).append("\",\n");
        sb.append("  \"dimension\": \"").append(StudioState.get().getActiveDimension()).append("\",\n");
        sb.append("  \"nodes\": [\n");
        for (int i = 0; i < nodes.size(); i++) {
            NodeCard n = nodes.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": \"").append(n.id).append("\",\n");
            sb.append("      \"type\": \"").append(n.schema.typeName()).append("\",\n");
            sb.append("      \"Editor\": { \"Position\": { \"X\": ").append(Math.round(n.canvasX))
              .append(", \"Y\": ").append(Math.round(n.canvasY)).append(" } }");
            for (ParamInstance p : n.params) {
                sb.append(",\n      \"").append(p.def.key()).append("\": ");
                if (p.def.type() == ParamType.FLOAT || p.def.type() == ParamType.INT || p.def.type() == ParamType.BOOLEAN) {
                    sb.append(p.value);
                } else {
                    sb.append("\"").append(p.value).append("\"");
                }
            }
            sb.append("\n    }").append(i < nodes.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"connections\": [\n");
        for (int i = 0; i < wires.size(); i++) {
            WireConnection w = wires.get(i);
            sb.append("    { \"from\": \"").append(w.fromNodeId).append(".").append(w.fromSocketId)
              .append("\", \"to\": \"").append(w.toNodeId).append(".").append(w.toSocketId).append("\" }")
              .append(i < wires.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }
}
