package com.worldgenv2.studio;

import com.worldgenv2.minecraft.DefaultBiomeCatalog;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.ZoneMode;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Singleton client state manager for the WorldGen V2 Studio companion mod.
 * Manages hierarchical drill-down navigation (World Overview -> Dimension Inspector -> Biome Node Editor),
 * per-dimension biome pools/weights, radial zones, ring landmarks, live testing chunks, and Hytale imports.
 */
public final class StudioState {

    private static final StudioState INSTANCE = new StudioState();

    public static StudioState get() {
        return INSTANCE;
    }

    public enum GraphMode {
        BIOME_GRAPH,
        DIMENSION_GRAPH
    }

    /**
     * Hierarchical drill-down level inside the primary "World & Dimensions" workspace.
     * Starts at {@link #WORLD_OVERVIEW} when pressing G so the user sees the whole world first,
     * drills into {@link #DIMENSION_INSPECTOR} when selecting a dimension, and only opens
     * {@link #NODE_EDITOR} when clicking into a specific biome (or macro graph).
     */
    public enum HierarchyLevel {
        WORLD_OVERVIEW,
        DIMENSION_INSPECTOR,
        NODE_EDITOR
    }

    /**
     * Compartmentalized sub-tabs within the {@link HierarchyLevel#DIMENSION_INSPECTOR} view.
     */
    public enum DimensionSubTab {
        BIOMES_LIST("Biomes & Weights"),
        ZONES_AND_LANDMARKS("Zones, Rings & Landmarks"),
        DIMENSION_SETTINGS("Dimension Settings & Bounds");

        public final String title;
        DimensionSubTab(String title) {
            this.title = title;
        }
    }

    /**
     * Mutable biome entry inside a dimension's biome pool.
     */
    public static final class BiomeEntryState {
        public final int id;
        public String name;
        public String category;
        public boolean enabled;
        public float weight;
        public float sizeModifier;
        public float transitionDistance;
        public boolean importedHytale;
        public String rawHytaleJson;

        public BiomeEntryState(
                int id,
                String name,
                String category,
                boolean enabled,
                float weight,
                float sizeModifier,
                float transitionDistance,
                boolean importedHytale
        ) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.enabled = enabled;
            this.weight = weight;
            this.sizeModifier = sizeModifier;
            this.transitionDistance = transitionDistance;
            this.importedHytale = importedHytale;
        }
    }

    /**
     * Mutable radial zone entry inside a dimension's Zones & Landmarks manager.
     */
    public static final class EditableZoneRule {
        public String id;
        public float innerRadius;
        public float outerRadius;
        public float falloff;
        public ZoneMode mode;
        public String targetBiome;
        public float targetElevation;
        public boolean floating;

        public EditableZoneRule(
                String id,
                float innerRadius,
                float outerRadius,
                float falloff,
                ZoneMode mode,
                String targetBiome,
                float targetElevation,
                boolean floating
        ) {
            this.id = id;
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
            this.falloff = falloff;
            this.mode = mode;
            this.targetBiome = targetBiome;
            this.targetElevation = targetElevation;
            this.floating = floating;
        }
    }

    /**
     * Mutable circular ring tower rule (e.g. End Obsidian Pillars) inside a dimension.
     */
    public static final class EditableRingRule {
        public String id;
        public float ringRadius;
        public int count;
        public int pillarRadius;
        public int minHeight;
        public int maxHeight;
        public String blockId;
        public boolean bedrockCap;

        public EditableRingRule(
                String id,
                float ringRadius,
                int count,
                int pillarRadius,
                int minHeight,
                int maxHeight,
                String blockId,
                boolean bedrockCap
        ) {
            this.id = id;
            this.ringRadius = ringRadius;
            this.count = count;
            this.pillarRadius = pillarRadius;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.blockId = blockId;
            this.bedrockCap = bedrockCap;
        }
    }

    /**
     * Mutable relational landmark rule (e.g. Spawn Island -> Watchtower with Spline Bridge).
     */
    public static final class EditableLandmarkRule {
        public String id;
        public String parentId;
        public float minDistance;
        public float maxDistance;
        public String structureId;
        public int count;
        public boolean connectBridge;
        public float bridgeHalfWidth;

        public EditableLandmarkRule(
                String id,
                String parentId,
                float minDistance,
                float maxDistance,
                String structureId,
                boolean connectBridge,
                float bridgeHalfWidth
        ) {
            this(id, parentId, minDistance, maxDistance, structureId, 1, connectBridge, bridgeHalfWidth);
        }

        public EditableLandmarkRule(
                String id,
                String parentId,
                float minDistance,
                float maxDistance,
                String structureId,
                int count,
                boolean connectBridge,
                float bridgeHalfWidth
        ) {
            this.id = id;
            this.parentId = parentId;
            this.minDistance = minDistance;
            this.maxDistance = maxDistance;
            this.structureId = structureId;
            this.count = Math.max(1, count);
            this.connectBridge = connectBridge;
            this.bridgeHalfWidth = bridgeHalfWidth;
        }
    }

    /**
     * Complete compartmentalized configuration for a single Dimension in the World Preset.
     */
    public static final class DimensionStudioProfile {
        public final String dimensionId;
        public final String displayName;
        public String formatDescription;
        public String layoutMode; // "Voronoi Continent", "Spawn Island + Landmarks", "Concentric Rings (End)", "Bounded Skyblock"
        public int minY;
        public int maxY;
        public int seaLevel;
        public int voronoiCellSize;
        public String defaultBlock;
        public String fluidBlock;
        public float boundsRadius;
        public float blendFalloff;
        public OutsideBoundsFallback outsideFallback;

        public final List<BiomeEntryState> biomes = new ArrayList<>();
        public final List<EditableZoneRule> zones = new ArrayList<>();
        public final List<EditableRingRule> rings = new ArrayList<>();
        public final List<EditableLandmarkRule> landmarks = new ArrayList<>();

        public DimensionStudioProfile(
                String dimensionId,
                String displayName,
                String formatDescription,
                String layoutMode,
                int minY,
                int maxY,
                int seaLevel,
                int voronoiCellSize,
                String defaultBlock,
                String fluidBlock,
                float boundsRadius,
                float blendFalloff,
                OutsideBoundsFallback outsideFallback
        ) {
            this.dimensionId = dimensionId;
            this.displayName = displayName;
            this.formatDescription = formatDescription;
            this.layoutMode = layoutMode;
            this.minY = minY;
            this.maxY = maxY;
            this.seaLevel = seaLevel;
            this.voronoiCellSize = voronoiCellSize;
            this.defaultBlock = defaultBlock;
            this.fluidBlock = fluidBlock;
            this.boundsRadius = boundsRadius;
            this.blendFalloff = blendFalloff;
            this.outsideFallback = outsideFallback;
        }

        public int getEnabledBiomeCount() {
            int c = 0;
            for (BiomeEntryState b : biomes) {
                if (b.enabled) c++;
            }
            return c;
        }
    }

    // Active navigation
    private String activeWorldPreset = "worldgenv2:volumetric";
    private String activeDimension = "minecraft:overworld";
    private String activeBiome = "TemperateForest";
    private GraphMode graphMode = GraphMode.BIOME_GRAPH;
    private HierarchyLevel hierarchyLevel = HierarchyLevel.WORLD_OVERVIEW;
    private DimensionSubTab dimensionSubTab = DimensionSubTab.BIOMES_LIST;

    // Compartmentalized Dimension Profiles
    private final Map<String, DimensionStudioProfile> dimensionProfiles = new LinkedHashMap<>();

    // Live testing chunks
    private final Set<ChunkPos> selectedChunks = new LinkedHashSet<>();
    private int viewportRadius = 1; // 1 = 3x3 chunks
    private boolean chunkHighlightActive = false; // 3D in-world highlight on/off
    private boolean followPlayer = false;
    private boolean initializedToPlayer = false;
    private ChunkPos centerChunk = null;
    private ChunkPos hoveredChunk = null;

    // Node Canvas positions and UI metadata
    private final Map<String, CanvasNodePos> nodePositions = new HashMap<>();
    private double canvasPanX = 40.0;
    private double canvasPanY = 40.0;
    private double canvasZoom = 1.0;

    // Status feedback
    private String statusMessage = "Studio ready. Double-click canvas to add any of the 295 nodes.";
    private long statusMessageTime = System.currentTimeMillis();

    private StudioState() {
        this.chunkHighlightActive = false; // Start hidden until user opens Test Chunks or toggles highlight
        initDefaultDimensionProfiles();
    }

    private void initDefaultDimensionProfiles() {
        // 1. Overworld Profile
        DimensionStudioProfile overworld = new DimensionStudioProfile(
                "minecraft:overworld",
                "Overworld",
                "3D Volumetric Density + Voronoi Cellular Biomes + Radial Spawn Zones",
                "Spawn Island + Voronoi Continent",
                -64, 320, 63, 220,
                "minecraft:stone", "minecraft:water",
                640.0f, 64.0f, OutsideBoundsFallback.DEFAULT_BIOMES
        );
        populateBiomesForDimension(overworld, true);
        overworld.zones.add(new EditableZoneRule("spawn_island_core", 0.0f, 220.0f, 40.0f, ZoneMode.ISLAND_PLATEAU, "TemperateForest", 72.0f, false));
        overworld.zones.add(new EditableZoneRule("spawn_ocean_moat", 220.0f, 480.0f, 44.0f, ZoneMode.BIOME_OVERRIDE, "Ocean", 45.0f, false));
        overworld.landmarks.add(new EditableLandmarkRule("northern_watchtower", "spawn_island_core", 120.0f, 195.0f, "worldgenv2:structures/watchtower_01", true, 3.5f));
        overworld.landmarks.add(new EditableLandmarkRule("eastern_waystation", "spawn_island_core", 130.0f, 210.0f, "worldgenv2:structures/watchtower_01", true, 3.0f));
        dimensionProfiles.put(overworld.dimensionId, overworld);

        // 2. The End Profile
        DimensionStudioProfile theEnd = new DimensionStudioProfile(
                "minecraft:the_end",
                "The End",
                "Concentric Radial Zones + 10 Obsidian Towers + Void Moat + Outer Archipelago",
                "Concentric Rings & Archipelago",
                0, 256, 0, 200,
                "minecraft:end_stone", "minecraft:air",
                30000.0f, 96.0f, OutsideBoundsFallback.VOID
        );
        theEnd.biomes.add(new BiomeEntryState(28, "StonyPeaks", "End Core", true, 1.5f, 1.0f, 24.0f, false));
        theEnd.biomes.add(new BiomeEntryState(1, "AridDunes", "Outer Highlands", true, 2.0f, 1.2f, 32.0f, false));
        theEnd.biomes.add(new BiomeEntryState(27, "WindsweptHills", "End Barrens", true, 1.0f, 0.9f, 24.0f, false));
        theEnd.zones.add(new EditableZoneRule("central_end_island", 0.0f, 190.0f, 32.0f, ZoneMode.ISLAND_PLATEAU, "StonyPeaks", 62.0f, true));
        theEnd.zones.add(new EditableZoneRule("end_void_moat", 190.0f, 960.0f, 36.0f, ZoneMode.VOID_MOAT, "WindsweptHills", 0.0f, true));
        theEnd.zones.add(new EditableZoneRule("outer_end_islands", 960.0f, 30000.0f, 64.0f, ZoneMode.OUTER_ISLANDS, "AridDunes", 60.0f, true));
        theEnd.rings.add(new EditableRingRule("obsidian_towers", 43.0f, 10, 3, 76, 103, "minecraft:obsidian", true));
        dimensionProfiles.put(theEnd.dimensionId, theEnd);

        // 3. The Nether Profile
        DimensionStudioProfile nether = new DimensionStudioProfile(
                "minecraft:the_nether",
                "The Nether",
                "3D Volumetric Caverns + Basalt Pillars + Lava Sea Basin",
                "3D Volumetric Caverns",
                0, 128, 31, 180,
                "minecraft:netherrack", "minecraft:lava",
                0.0f, 64.0f, OutsideBoundsFallback.DEFAULT_BIOMES
        );
        nether.biomes.add(new BiomeEntryState(13, "Badlands", "Nether Wastes", true, 2.0f, 1.0f, 28.0f, false));
        nether.biomes.add(new BiomeEntryState(35, "DripstoneCaves", "Basalt Deltas", true, 1.5f, 1.0f, 24.0f, false));
        nether.biomes.add(new BiomeEntryState(36, "DeepDark", "Soul Sand Valley", true, 1.2f, 1.1f, 28.0f, false));
        nether.biomes.add(new BiomeEntryState(37, "MushroomGrotto", "Crimson & Warped", true, 1.4f, 1.0f, 24.0f, false));
        dimensionProfiles.put(nether.dimensionId, nether);

        // 4. Hytale 576 Vertical Profile
        DimensionStudioProfile hytale576 = new DimensionStudioProfile(
                "worldgenv2:overworld_hytale",
                "Hytale 576 Vertical",
                "Extended 576-Block Vertical Span [-64..512] + Hytale AST Biomes",
                "Hytale 576 Continent & Zones",
                -64, 512, 115, 240,
                "minecraft:stone", "minecraft:water",
                0.0f, 64.0f, OutsideBoundsFallback.DEFAULT_BIOMES
        );
        populateBiomesForDimension(hytale576, true);
        dimensionProfiles.put(hytale576.dimensionId, hytale576);
    }

    private static void populateBiomesForDimension(DimensionStudioProfile profile, boolean includeAll) {
        for (DefaultBiomeCatalog.Entry e : DefaultBiomeCatalog.entries()) {
            String cat = switch (e.kind()) {
                case UNDERGROUND -> "Underground 3D";
                case RIVER -> "Rivers";
                case CUSTOM -> "Special Overlay";
                case TILE -> !e.land() ? "Oceans & Coasts" : (e.cold() ? "Alpine & Cold" : "Land & Forests");
            };
            float defaultWeight = (e.id() == DefaultBiomeCatalog.TEMPERATE_FOREST || e.id() == DefaultBiomeCatalog.SUNLIT_PLAINS) ? 1.5f : 1.0f;
            profile.biomes.add(new BiomeEntryState(e.id(), e.name(), cat, includeAll, defaultWeight, 1.0f, 32.0f, false));
        }
    }

    public HierarchyLevel getHierarchyLevel() {
        return hierarchyLevel;
    }

    public void setHierarchyLevel(HierarchyLevel level) {
        this.hierarchyLevel = level != null ? level : HierarchyLevel.WORLD_OVERVIEW;
    }

    public DimensionSubTab getDimensionSubTab() {
        return dimensionSubTab;
    }

    public void setDimensionSubTab(DimensionSubTab subTab) {
        this.dimensionSubTab = subTab != null ? subTab : DimensionSubTab.BIOMES_LIST;
    }

    public Collection<DimensionStudioProfile> getDimensionProfiles() {
        return dimensionProfiles.values();
    }

    public DimensionStudioProfile getDimensionProfile(String dimensionId) {
        DimensionStudioProfile p = dimensionProfiles.get(dimensionId);
        if (p == null) {
            p = dimensionProfiles.get("minecraft:overworld");
        }
        return p;
    }

    public DimensionStudioProfile getActiveDimensionProfile() {
        return getDimensionProfile(this.activeDimension);
    }

    public BiomeEntryState registerImportedHytaleBiome(String dimensionId, String biomeName, float weight, float sizeMod, float transDist, String rawJson) {
        DimensionStudioProfile profile = getDimensionProfile(dimensionId);
        for (BiomeEntryState existing : profile.biomes) {
            if (existing.name.equalsIgnoreCase(biomeName)) {
                existing.enabled = true;
                existing.weight = weight;
                existing.sizeModifier = sizeMod;
                existing.transitionDistance = transDist;
                existing.importedHytale = true;
                existing.rawHytaleJson = rawJson;
                return existing;
            }
        }
        int nextId = 100 + profile.biomes.size();
        BiomeEntryState added = new BiomeEntryState(nextId, biomeName, "Imported Hytale", true, weight, sizeMod, transDist, true);
        added.rawHytaleJson = rawJson;
        profile.biomes.addFirst(added);
        return added;
    }

    public String getActiveWorldPreset() {
        return activeWorldPreset;
    }

    public void setActiveWorldPreset(String preset) {
        this.activeWorldPreset = preset;
    }

    public GraphMode getGraphMode() {
        return graphMode;
    }

    public void setGraphMode(GraphMode mode) {
        this.graphMode = mode != null ? mode : GraphMode.BIOME_GRAPH;
    }

    public boolean isDimensionGraphMode() {
        return graphMode == GraphMode.DIMENSION_GRAPH;
    }

    public String getActiveDimension() {
        return activeDimension;
    }

    public void setActiveDimension(String dimension) {
        this.activeDimension = dimension;
    }

    public String getActiveBiome() {
        return activeBiome;
    }

    public void setActiveBiome(String biome) {
        this.activeBiome = biome;
    }

    public Set<ChunkPos> getSelectedChunks() {
        return Collections.unmodifiableSet(selectedChunks);
    }

    public boolean isChunkSelected(int chunkX, int chunkZ) {
        return selectedChunks.contains(new ChunkPos(chunkX, chunkZ));
    }

    public void toggleChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (selectedChunks.contains(pos)) {
            selectedChunks.remove(pos);
        } else {
            selectedChunks.add(pos);
        }
        this.chunkHighlightActive = true;
        setStatusMessage("Selected " + selectedChunks.size() + " dev test chunks (3D highlight ON).");
    }

    public void setChunkSelected(int chunkX, int chunkZ, boolean select) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (select) {
            selectedChunks.add(pos);
        } else {
            selectedChunks.remove(pos);
        }
        this.chunkHighlightActive = true;
    }

    public void clearSelectedChunks() {
        selectedChunks.clear();
        setStatusMessage("Cleared selected dev chunks.");
    }

    public void setTestingArea(ChunkPos center, int radius) {
        this.centerChunk = center;
        this.viewportRadius = radius;
        this.selectedChunks.clear();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                selectedChunks.add(new ChunkPos(center.x() + dx, center.z() + dz));
            }
        }
        this.chunkHighlightActive = true;
    }

    /**
     * Confirmation Action ("Confirm Chunks" / "Apply Viewport"):
     * Locks the active testing volume and AUTOMATICALLY HIDES the visual indicator
     * so it doesn't obstruct the creator's view of generated mountains, caves, and terrain.
     */
    public void confirmChunks() {
        this.chunkHighlightActive = false;
        setStatusMessage("Confirmed " + selectedChunks.size() + " dev chunks. 3D highlight hidden (toggle anytime with H).");
    }

    public boolean isChunkHighlightActive() {
        return chunkHighlightActive;
    }

    public void setChunkHighlightActive(boolean active) {
        this.chunkHighlightActive = active;
        setStatusMessage("3D Dev Chunk Highlight: " + (active ? "VISIBLE in world" : "HIDDEN"));
    }

    public void toggleChunkHighlight() {
        setChunkHighlightActive(!this.chunkHighlightActive);
    }

    public ChunkPos getHoveredChunk() {
        return hoveredChunk;
    }

    public void setHoveredChunk(ChunkPos hoveredChunk) {
        this.hoveredChunk = hoveredChunk;
    }

    public boolean isFollowPlayer() {
        return followPlayer;
    }

    public void setFollowPlayer(boolean followPlayer) {
        this.followPlayer = followPlayer;
    }

    public ChunkPos getCenterChunk() {
        if (centerChunk == null) {
            centerChunk = new ChunkPos(0, 0);
            if (selectedChunks.isEmpty()) {
                setTestingArea(centerChunk, viewportRadius);
                this.chunkHighlightActive = false;
            }
        }
        return centerChunk;
    }

    public void setCenterChunk(ChunkPos centerChunk) {
        this.centerChunk = centerChunk;
    }

    public void centerOnPlayer(ChunkPos playerChunk) {
        int r = this.viewportRadius;
        setTestingArea(playerChunk, r);
        setStatusMessage("Centered dev chunks on player chunk [" + playerChunk.x() + ", " + playerChunk.z() + "].");
    }

    public void updatePlayerChunk(ChunkPos playerChunk) {
        if (!initializedToPlayer) {
            initializedToPlayer = true;
            this.centerChunk = playerChunk;
            boolean wasActive = this.chunkHighlightActive;
            setTestingArea(playerChunk, viewportRadius);
            this.chunkHighlightActive = wasActive;
            return;
        }
        if (followPlayer && !centerChunk.equals(playerChunk)) {
            boolean wasActive = this.chunkHighlightActive;
            setTestingArea(playerChunk, viewportRadius);
            this.chunkHighlightActive = wasActive;
        }
    }

    public void resetForNewWorld() {
        this.initializedToPlayer = false;
        this.selectedChunks.clear();
        this.chunkHighlightActive = false;
        this.hoveredChunk = null;
    }

    public int getViewportRadius() {
        return viewportRadius;
    }

    public void setViewportRadius(int radius) {
        this.viewportRadius = Math.max(0, Math.min(4, radius));
        setTestingArea(centerChunk, this.viewportRadius);
    }

    public double getCanvasPanX() {
        return canvasPanX;
    }

    public void setCanvasPanX(double canvasPanX) {
        this.canvasPanX = canvasPanX;
    }

    public double getCanvasPanY() {
        return canvasPanY;
    }

    public void setCanvasPanY(double canvasPanY) {
        this.canvasPanY = canvasPanY;
    }

    public double getCanvasZoom() {
        return canvasZoom;
    }

    public void setCanvasZoom(double canvasZoom) {
        this.canvasZoom = Math.max(0.35, Math.min(2.25, canvasZoom));
    }

    public CanvasNodePos getNodePos(String nodeId, double defaultX, double defaultY) {
        return nodePositions.computeIfAbsent(nodeId, k -> new CanvasNodePos(defaultX, defaultY));
    }

    public void setNodePos(String nodeId, double x, double y) {
        nodePositions.put(nodeId, new CanvasNodePos(x, y));
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String message) {
        this.statusMessage = message;
        this.statusMessageTime = System.currentTimeMillis();
    }

    public long getStatusMessageTime() {
        return statusMessageTime;
    }

    public record CanvasNodePos(double x, double y) {}
}
