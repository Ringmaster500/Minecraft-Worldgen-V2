package com.worldgenv2.studio;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.curve.Curve;
import com.worldgenv2.curve.DistanceExponentialCurve;
import com.worldgenv2.curve.DistanceSCurve;
import com.worldgenv2.curve.ManualCurve;
import com.worldgenv2.density.*;
import com.worldgenv2.material.*;
import com.worldgenv2.minecraft.DefaultBiomeCatalog;
import com.worldgenv2.minecraft.DefaultBiomeFactory;
import com.worldgenv2.minecraft.DefaultWorldFactory;
import com.worldgenv2.minecraft.WorldGenV2ChunkGenerator;
import com.worldgenv2.studio.tabs.NodeCanvasTab;
import com.worldgenv2.studio.tabs.NodeCanvasTab.NodeCard;
import com.worldgenv2.studio.tabs.NodeCanvasTab.WireConnection;
import com.worldgenv2.worldstructure.CustomBiome;
import com.worldgenv2.worldstructure.DimensionLayoutConfig;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.BiomePoolEntry;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.RadialZoneRule;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.RingLandmarkRule;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.ZoneMode;
import com.worldgenv2.worldstructure.MacroFeatureGraph;
import com.worldgenv2.worldstructure.SplineRoadEngine;
import com.worldgenv2.worldstructure.TileBiome;
import com.worldgenv2.worldstructure.WorldStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.*;

/**
 * Live Graph-to-Engine Compiler, Biome Decompiler, and In-World Test Chunk Regenerator.
 * <ul>
 *   <li>Inspects any live {@link TileBiome} (from a clicked test chunk or biome dropdown) and
 *       reconstructs its visual {@link NodeCard} DAG and exact parameter values on {@link NodeCanvasTab}.</li>
 *   <li>Compiles {@link NodeCanvasTab} node cards and wires back into a live {@link DensityFunction}
 *       and {@link MaterialProvider}, hot-swaps the biome in {@link WorldStructure}, and regenerates
 *       all selected test chunks in-place on the integrated {@link ServerLevel}.</li>
 * </ul>
 */
public final class StudioLiveCompiler {

    private static volatile WorldStructure clientFallbackStructure;

    private StudioLiveCompiler() {
    }

    /**
     * Resolves the active {@link WorldGenV2ChunkGenerator} on the integrated server if available.
     */
    public static WorldGenV2ChunkGenerator getActiveServerGenerator() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return null;

        ServerLevel serverLevel = server.getLevel(mc.level.dimension());
        if (serverLevel == null) {
            serverLevel = server.overworld();
        }
        if (serverLevel != null) {
            ChunkGenerator gen = serverLevel.getChunkSource().getGenerator();
            if (gen instanceof WorldGenV2ChunkGenerator v2Gen) {
                return v2Gen;
            }
        }
        return null;
    }

    /**
     * Resolves the active {@link WorldStructure} from the integrated server or a local fallback.
     */
    public static WorldStructure getActiveWorldStructure() {
        WorldGenV2ChunkGenerator v2Gen = getActiveServerGenerator();
        if (v2Gen != null) {
            return v2Gen.getActiveWorldStructure();
        }
        if (clientFallbackStructure == null) {
            clientFallbackStructure = DefaultWorldFactory.createDefaultWorldStructure(
                1337L,
                DefaultWorldFactory.DEFAULT_VORONOI_CELL_SIZE
            );
        }
        return clientFallbackStructure;
    }

    /**
     * Queries the primary {@link TileBiome} at the center of the given chunk coordinates.
     */
    public static TileBiome queryChunkPrimaryBiome(int chunkX, int chunkZ) {
        WorldStructure structure = getActiveWorldStructure();
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        return structure.queryBiome(blockX, blockZ).primaryTileBiome();
    }

    /**
     * Samples the approximate surface elevation at the center of a chunk for minimap shading.
     */
    public static int sampleChunkSurfaceY(int chunkX, int chunkZ) {
        WorldStructure structure = getActiveWorldStructure();
        int bx = (chunkX << 4) + 8;
        int bz = (chunkZ << 4) + 8;
        TileBiome b = structure.queryBiome(bx, bz).primaryTileBiome();
        if (b == null || b.terrainDensity() == null) return 64;
        for (int y = 160; y >= 16; y -= 4) {
            if (b.terrainDensity().compute(bx, y, bz) > 0.0f) {
                return y;
            }
        }
        return 32;
    }

    /**
     * Distinct, readable ARGB color per biome ID for the 2D Chunk Minimap.
     */
    public static int getBiomeMapColor(int biomeId) {
        return switch (biomeId) {
            case DefaultBiomeCatalog.TEMPERATE_FOREST -> 0xFF2D7A36;
            case DefaultBiomeCatalog.ARID_DUNES -> 0xFFD9B85B;
            case DefaultBiomeCatalog.ALPINE_PEAKS -> 0xFFBFD8E6;
            case DefaultBiomeCatalog.OLD_GROWTH_GROVE -> 0xFF1F5428;
            case DefaultBiomeCatalog.SUNLIT_PLAINS -> 0xFF5FA842;
            case DefaultBiomeCatalog.DEEP_OCEAN -> 0xFF142E68;
            case DefaultBiomeCatalog.DEEP_FROZEN_OCEAN -> 0xFF22427A;
            case DefaultBiomeCatalog.WARM_OCEAN -> 0xFF1E88B8;
            case DefaultBiomeCatalog.LUKEWARM_OCEAN -> 0xFF1B689E;
            case DefaultBiomeCatalog.OCEAN -> 0xFF184C88;
            case DefaultBiomeCatalog.COLD_OCEAN -> 0xFF204274;
            case DefaultBiomeCatalog.FROZEN_OCEAN -> 0xFF4C729E;
            case DefaultBiomeCatalog.MUSHROOM_ISLAND -> 0xFF8A5E82;
            case DefaultBiomeCatalog.BADLANDS -> 0xFFC86432;
            case DefaultBiomeCatalog.SAVANNA -> 0xFF9E9844;
            case DefaultBiomeCatalog.JUNGLE -> 0xFF1E8C28;
            case DefaultBiomeCatalog.SWAMP -> 0xFF3E5838;
            case DefaultBiomeCatalog.MANGROVE_SWAMP -> 0xFF345238;
            case DefaultBiomeCatalog.FLOWER_FOREST -> 0xFF429848;
            case DefaultBiomeCatalog.BIRCH_FOREST -> 0xFF4E9A4E;
            case DefaultBiomeCatalog.DARK_FOREST -> 0xFF224E22;
            case DefaultBiomeCatalog.MEADOW -> 0xFF66B260;
            case DefaultBiomeCatalog.CHERRY_GROVE -> 0xFFD878A8;
            case DefaultBiomeCatalog.TAIGA -> 0xFF285E48;
            case DefaultBiomeCatalog.SNOWY_PLAINS -> 0xFFD8E6EC;
            case DefaultBiomeCatalog.SNOWY_TAIGA -> 0xFF487468;
            case DefaultBiomeCatalog.GROVE -> 0xFF789894;
            case DefaultBiomeCatalog.WINDSWEPT_HILLS -> 0xFF687A68;
            case DefaultBiomeCatalog.STONY_PEAKS -> 0xFF888C90;
            case DefaultBiomeCatalog.JAGGED_PEAKS -> 0xFFD0DCE4;
            case DefaultBiomeCatalog.FROZEN_PEAKS -> 0xFFB8D4EC;
            case DefaultBiomeCatalog.ICE_SPIKES -> 0xFF88C8E8;
            case DefaultBiomeCatalog.RIVER -> 0xFF2868A8;
            case DefaultBiomeCatalog.FROZEN_RIVER -> 0xFF5888B8;
            default -> 0xFF3A6A4C;
        };
    }

    /**
     * Synchronizes the Studio's active biome and loads its node graph into {@link NodeCanvasTab}
     * based on the given chunk coordinates.
     */
    public static void selectBiomeFromChunk(int chunkX, int chunkZ, NodeCanvasTab canvas) {
        TileBiome tileBiome = queryChunkPrimaryBiome(chunkX, chunkZ);
        if (tileBiome != null) {
            StudioState.get().setActiveBiome(tileBiome.name());
            if (canvas != null) {
                loadBiomeIntoCanvas(tileBiome.name(), canvas);
            }
        }
    }

    /**
     * Decompiles the live {@link TileBiome} (or {@link CustomBiome}) matching {@code biomeNameOrId}
     * directly into interactive {@link NodeCard}s and wires on {@link NodeCanvasTab}.
     */
    public static void loadBiomeIntoCanvas(String biomeNameOrId, NodeCanvasTab canvas) {
        if (canvas == null || biomeNameOrId == null) return;

        WorldStructure structure = getActiveWorldStructure();
        TileBiome tile = structure.findTileBiomeByName(biomeNameOrId);
        CustomBiome custom = (tile == null) ? structure.findCustomBiomeByName(biomeNameOrId) : null;

        if (tile == null && custom == null) {
            // Default to TemperateForest if not found
            tile = structure.findTileBiome(DefaultBiomeCatalog.TEMPERATE_FOREST);
        }
        if (tile == null && custom == null) return;

        DensityFunction terrain = (tile != null) ? tile.terrainDensity() : custom.terrainDensity();
        MaterialProvider<?> rawMat = (tile != null) ? tile.materialProvider() : custom.materialProvider();
        String dispName = (tile != null) ? tile.name() : custom.name();

        canvas.clearCanvas();
        StudioState.get().setCanvasPanX(18.0);
        StudioState.get().setCanvasPanY(32.0);
        StudioState.get().setCanvasZoom(1.0);

        // Unpack outer GradientWarpDensity if present
        GradientWarpDensity outerWarp = null;
        DensityFunction coreTerrain = terrain;
        if (terrain instanceof GradientWarpDensity gw) {
            outerWarp = gw;
            coreTerrain = gw.getSource();
        }

        // Unpack SumDensity children or single density node
        List<DensityFunction> terms = new ArrayList<>();
        if (coreTerrain instanceof SumDensity sum) {
            Collections.addAll(terms, sum.getInputs());
        } else if (coreTerrain != null) {
            terms.add(coreTerrain);
        }

        List<NodeCard> termOutputCards = new ArrayList<>();
        int curY = 10;

        for (DensityFunction term : terms) {
            if (term instanceof VerticalGradientDensity vg) {
                NodeCard grav = canvas.spawnNode("Density:VerticalGradientDensity", 12, curY);
                grav.setParam("baseHeight", fmt(vg.getBaseHeight()));
                grav.setParam("scale", fmt(vg.getHeightScale()));
                termOutputCards.add(grav);
                curY += 74;
            } else if (term instanceof Noise2dDensity n2) {
                NodeCard card2d = spawnNoise2dCard(canvas, n2, 12, curY);
                termOutputCards.add(card2d);
                curY += 102;
            } else if (term instanceof YSampledDensity ys) {
                NodeCard ysCard = unpackYSampledBranch(canvas, ys, 12, curY);
                termOutputCards.add(ysCard);
                curY += 116;
            } else if (term instanceof CurveMapperDensity cm) {
                NodeCard cmCard = unpackCurveMapperBranch(canvas, cm, 12, curY);
                termOutputCards.add(cmCard);
                curY += 116;
            } else if (term instanceof ClampDensity cl) {
                NodeCard srcCard = unpackGenericDensity(canvas, cl.input(), 12, curY);
                NodeCard clCard = canvas.spawnNode("Density:ClampDensity", 190, curY);
                clCard.setParam("min", fmt(cl.min()));
                clCard.setParam("max", fmt(cl.max()));
                canvas.connectNodes(srcCard, "out", clCard, "input");
                termOutputCards.add(clCard);
                curY += 90;
            } else {
                NodeCard gen = unpackGenericDensity(canvas, term, 12, curY);
                termOutputCards.add(gen);
                curY += 90;
            }
        }

        // Combine terms with SumDensity if multiple terms exist
        NodeCard finalDensityCard;
        int midColX = 196;
        if (termOutputCards.isEmpty()) {
            finalDensityCard = canvas.spawnNode("Density:VerticalGradientDensity", 12, 20);
        } else if (termOutputCards.size() == 1) {
            finalDensityCard = termOutputCards.getFirst();
        } else {
            NodeCard sumCard = canvas.spawnNode("Density:SumDensity", midColX, 20);
            String[] inSockets = {"in_a", "in_b", "in_c"};
            for (int i = 0; i < Math.min(termOutputCards.size(), inSockets.length); i++) {
                canvas.connectNodes(termOutputCards.get(i), "out", sumCard, inSockets[i]);
            }
            // If there is a 4th term (e.g. Base + Hills + Ridge + 3D), chain a second SumDensity
            if (termOutputCards.size() > 3) {
                NodeCard sum2 = canvas.spawnNode("Density:SumDensity", midColX + 170, 20);
                canvas.connectNodes(sumCard, "out", sum2, "in_a");
                canvas.connectNodes(termOutputCards.get(3), "out", sum2, "in_b");
                finalDensityCard = sum2;
                midColX += 170;
            } else {
                finalDensityCard = sumCard;
            }
        }

        // Attach GradientWarpDensity if present
        if (outerWarp != null) {
            int warpX = (int) finalDensityCard.canvasX + 176;
            NodeCard warpCard = canvas.spawnNode("Density:GradientWarpDensity", warpX, 24);
            warpCard.setParam("warpStrength", fmt(outerWarp.getWarpStrength()));
            canvas.connectNodes(finalDensityCard, "out", warpCard, "input");

            if (outerWarp.getWarpSource() instanceof Noise2dDensity wn2) {
                NodeCard warpNoise = spawnNoise2dCard(canvas, wn2, (int) finalDensityCard.canvasX, 118);
                canvas.connectNodes(warpNoise, "out", warpCard, "warpSource");
            }
            finalDensityCard = warpCard;
        }

        // Unpack MaterialProvider into SpaceAndDepthMaterialProvider card
        int matX = (int) finalDensityCard.canvasX + 176;
        NodeCard matCard = unpackMaterialCard(canvas, rawMat, matX, 20);
        canvas.connectNodes(finalDensityCard, "out", matCard, "in_terrain");

        // Spawn BiomeRoot
        int rootX = matX + 176;
        NodeCard rootCard = canvas.spawnNode("World:BiomeRoot", rootX, 24);
        if (tile != null) {
            if (tile.seaLevel() != null) {
                rootCard.setParam("seaLevel", String.valueOf(tile.seaLevel()));
            }
            if (tile.fluidBlock() != null) {
                rootCard.setParam("fluidBlock", tile.fluidBlock());
            }
        }
        canvas.connectNodes(finalDensityCard, "out", rootCard, "terrain");
        canvas.connectNodes(matCard, "out", rootCard, "material");

        StudioState.get().setStatusMessage("Loaded live biome graph for " + dispName + " (" + canvas.getNodes().size() + " nodes).");
    }

    private static NodeCard spawnNoise2dCard(NodeCanvasTab canvas, Noise2dDensity n2, int x, int y) {
        FastNoiseLite fnl = n2.getNoise();
        if (fnl.getFractalType() == FastNoiseLite.FractalType.Ridged) {
            NodeCard card = canvas.spawnNode("Density:Noise2dDensity", x, y);
            card.setParam("noiseType", fnl.getNoiseType().name());
            card.setParam("frequency", fmtFreq(fnl.getFrequency()));
            card.setParam("octaves", String.valueOf(fnl.getFractalOctaves()));
            card.setParam("amplitude", fmt(n2.getAmplitude()));
            card.setParam("fractalType", "Ridged");
            return card;
        }
        NodeCard card = canvas.spawnNode("Density:SimplexNoise2D", x, y);
        card.setParam("frequency", fmtFreq(fnl.getFrequency()));
        card.setParam("octaves", String.valueOf(fnl.getFractalOctaves()));
        card.setParam("amplitude", fmt(n2.getAmplitude()));
        card.setParam("seed", String.valueOf(Math.abs(fnl.getSeed() % 99999)));
        return card;
    }

    private static NodeCard unpackYSampledBranch(NodeCanvasTab canvas, YSampledDensity ys, int x, int y) {
        NodeCard childCard = unpackGenericDensity(canvas, ys.getSource(), x, y);
        NodeCard ysCard = canvas.spawnNode("Density:YSampledDensity", x + 176, y + 6);
        ysCard.setParam("yInterval", String.valueOf(ys.getYInterval()));
        canvas.connectNodes(childCard, "out", ysCard, "input");
        return ysCard;
    }

    private static NodeCard unpackCurveMapperBranch(NodeCanvasTab canvas, CurveMapperDensity cm, int x, int y) {
        DensityFunction src = cm.getSource();
        if (src instanceof YSampledDensity ys) {
            return unpackYSampledBranch(canvas, ys, x, y);
        }
        return unpackGenericDensity(canvas, src, x, y);
    }

    private static NodeCard unpackGenericDensity(NodeCanvasTab canvas, DensityFunction df, int x, int y) {
        if (df instanceof Noise3dDensity n3) {
            FastNoiseLite fnl = n3.getNoise();
            NodeCard c3 = canvas.spawnNode("Density:SimplexNoise3D", x, y);
            c3.setParam("frequency", fmtFreq(fnl.getFrequency()));
            c3.setParam("octaves", String.valueOf(fnl.getFractalOctaves()));
            c3.setParam("amplitude", fmt(n3.getAmplitude()));
            c3.setParam("seed", String.valueOf(Math.abs(fnl.getSeed() % 99999)));
            return c3;
        }
        if (df instanceof Noise2dDensity n2) {
            return spawnNoise2dCard(canvas, n2, x, y);
        }
        if (df instanceof VerticalGradientDensity vg) {
            NodeCard grav = canvas.spawnNode("Density:VerticalGradientDensity", x, y);
            grav.setParam("baseHeight", fmt(vg.getBaseHeight()));
            grav.setParam("scale", fmt(vg.getHeightScale()));
            return grav;
        }
        if (df instanceof CellNoise3dDensity cn3) {
            NodeCard cell = canvas.spawnNode("Density:CellNoise3D", x, y);
            cell.setParam("frequency", fmtFreq(cn3.getNoise().getFrequency()));
            cell.setParam("amplitude", fmt(cn3.getAmplitude()));
            return cell;
        }
        return canvas.spawnNode("Density:SimplexNoise3D", x, y);
    }

    @SuppressWarnings("unchecked")
    private static NodeCard unpackMaterialCard(NodeCanvasTab canvas, MaterialProvider<?> rawMat, int x, int y) {
        SpaceAndDepthMaterialProvider<BlockState> sad = findSpaceAndDepth(rawMat);
        NodeCard matCard = canvas.spawnNode("Material:SpaceAndDepthMaterialProvider", x, y);
        if (sad != null) {
            List<SpaceAndDepthMaterialProvider.Layer<BlockState>> layers = sad.layers();
            if (!layers.isEmpty()) {
                matCard.setParam("surfaceBlock", blockId(layers.get(0).material()));
            }
            if (layers.size() > 1) {
                matCard.setParam("subsoilBlock", blockId(layers.get(1).material()));
                matCard.setParam("subsoilDepth", String.valueOf(Math.clamp(layers.get(1).thickness(), 1, 16)));
            } else if (!layers.isEmpty()) {
                matCard.setParam("subsoilBlock", blockId(layers.get(0).material()));
            }
            if (sad.cliffMaterial() != null) {
                matCard.setParam("cliffBlock", blockId(sad.cliffMaterial()));
            }
            matCard.setParam("maxSlope", String.valueOf(Math.round(sad.maxSlopeForCover())));
        }
        return matCard;
    }

    @SuppressWarnings("unchecked")
    private static SpaceAndDepthMaterialProvider<BlockState> findSpaceAndDepth(MaterialProvider<?> provider) {
        if (provider == null) return null;
        if (provider instanceof SpaceAndDepthMaterialProvider<?> sad) {
            return (SpaceAndDepthMaterialProvider<BlockState>) sad;
        }
        if (provider instanceof DefaultBiomeFactory.WrappedMaterialProvider<?> wrapped) {
            return findSpaceAndDepth(wrapped.inner());
        }
        if (provider instanceof QueueMaterialProvider<?> qmp) {
            for (QueueMaterialProvider.ConditionalRule<?> rule : qmp.rules()) {
                SpaceAndDepthMaterialProvider<BlockState> found = findSpaceAndDepth(rule.provider());
                if (found != null) return found;
            }
            return findSpaceAndDepth(qmp.fallbackProvider());
        }
        return null;
    }

    private static String blockId(BlockState state) {
        if (state == null) return "minecraft:stone";
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /**
     * Compiles the current node graph on {@link NodeCanvasTab} into a live {@link DensityFunction}
     * and {@link MaterialProvider}, updates the active biome in {@link WorldStructure}, and
     * regenerates all selected test chunks in-place on the integrated server!
     */
    public static void compileAndApplyLive(NodeCanvasTab canvas) {
        if (canvas == null || canvas.getNodes().isEmpty()) return;

        // 0. Check if this canvas contains a Dimension Macro Graph (DimensionOutput / RadialZone / SpawnIslandAnchor)
        NodeCard dimOutputCard = null;
        boolean hasMacroNodes = false;
        for (NodeCard card : canvas.getNodes()) {
            String type = card.schema.typeName();
            if ("DimensionOutput".equals(type)) {
                dimOutputCard = card;
                hasMacroNodes = true;
            } else if ("RadialZone".equals(type)
                    || "RingLandmarks".equals(type)
                    || "SpawnIslandAnchor".equals(type)
                    || "RelationalLandmark".equals(type)
                    || "SubIslandCluster".equals(type)
                    || "BiomeWeightEntry".equals(type)) {
                hasMacroNodes = true;
            }
        }
        if (hasMacroNodes && (dimOutputCard != null || StudioState.get().getGraphMode() == StudioState.GraphMode.DIMENSION_GRAPH)) {
            compileDimensionMacroGraph(canvas, dimOutputCard);
            return;
        }

        // 1. Locate BiomeRoot or rightmost output cards
        NodeCard rootCard = null;
        NodeCard fallbackDensityCard = null;
        NodeCard fallbackMaterialCard = null;

        for (NodeCard card : canvas.getNodes()) {
            if ("World:BiomeRoot".equals(card.schema.id()) || "BiomeRoot".equals(card.schema.typeName())) {
                rootCard = card;
            }
            if ("Density".equalsIgnoreCase(card.schema.category())) {
                if (fallbackDensityCard == null || card.canvasX >= fallbackDensityCard.canvasX) {
                    fallbackDensityCard = card;
                }
            }
            if ("Material".equalsIgnoreCase(card.schema.category())) {
                if (fallbackMaterialCard == null || card.canvasX >= fallbackMaterialCard.canvasX) {
                    fallbackMaterialCard = card;
                }
            }
        }

        NodeCard terrainRoot = null;
        NodeCard materialRoot = null;
        Integer overrideSeaLevel = null;
        String overrideFluidBlock = null;

        if (rootCard != null) {
            terrainRoot = findConnectedInputCard(canvas, rootCard.id, "terrain");
            materialRoot = findConnectedInputCard(canvas, rootCard.id, "material");
            overrideSeaLevel = canvas.getParamInt(rootCard, "seaLevel", 63);
            overrideFluidBlock = canvas.getParamValue(rootCard, "fluidBlock", "minecraft:water");
        }
        if (terrainRoot == null) terrainRoot = fallbackDensityCard;
        if (materialRoot == null) materialRoot = fallbackMaterialCard;

        DensityFunction compiledDensity = compileDensityNode(canvas, terrainRoot, new HashSet<>());
        MaterialProvider<BlockState> compiledMaterial = compileMaterialNode(canvas, materialRoot);

        // 2. Inject compiled DensityFunction & MaterialProvider into the active WorldStructure
        WorldStructure structure = getActiveWorldStructure();
        StudioState state = StudioState.get();
        String activeBiomeName = state.getActiveBiome();

        Set<Integer> updatedBiomeIds = new HashSet<>();
        TileBiome targetBiome = structure.findTileBiomeByName(activeBiomeName);
        if (targetBiome != null) {
            TileBiome updated = new TileBiome(
                targetBiome.id(),
                targetBiome.name(),
                targetBiome.weight(),
                targetBiome.sizeModifier(),
                targetBiome.defaultTransitionDistance(),
                compiledDensity != null ? compiledDensity : targetBiome.terrainDensity(),
                compiledMaterial != null ? compiledMaterial : targetBiome.materialProvider(),
                targetBiome.props(),
                targetBiome.climate(),
                targetBiome.defaultBlock(),
                overrideFluidBlock != null ? overrideFluidBlock : targetBiome.fluidBlock(),
                overrideSeaLevel != null ? overrideSeaLevel : targetBiome.seaLevel()
            );
            structure.updateTileBiome(updated);
            updatedBiomeIds.add(updated.id());
        }

        // Also ensure the primary TileBiome of every selected test chunk gets updated if not yet updated,
        // so editing ANY graph or preset immediately updates the selected test chunks in-world!
        for (ChunkPos cp : state.getSelectedChunks()) {
            TileBiome chunkBiome = queryChunkPrimaryBiome(cp.x(), cp.z());
            if (chunkBiome != null && !updatedBiomeIds.contains(chunkBiome.id())) {
                TileBiome updatedChunkBiome = new TileBiome(
                    chunkBiome.id(),
                    chunkBiome.name(),
                    chunkBiome.weight(),
                    chunkBiome.sizeModifier(),
                    chunkBiome.defaultTransitionDistance(),
                    compiledDensity != null ? compiledDensity : chunkBiome.terrainDensity(),
                    compiledMaterial != null ? compiledMaterial : chunkBiome.materialProvider(),
                    chunkBiome.props(),
                    chunkBiome.climate(),
                    chunkBiome.defaultBlock(),
                    overrideFluidBlock != null ? overrideFluidBlock : chunkBiome.fluidBlock(),
                    overrideSeaLevel != null ? overrideSeaLevel : chunkBiome.seaLevel()
                );
                structure.updateTileBiome(updatedChunkBiome);
                updatedBiomeIds.add(chunkBiome.id());
            }
        }

        // 3. Regenerate the selected test chunks on the integrated singleplayer server!
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.getSingleplayerServer() != null) {
            MinecraftServer server = mc.getSingleplayerServer();
            var dimKey = mc.level.dimension();
            List<ChunkPos> chunksToRegen = new ArrayList<>(state.getSelectedChunks());
            if (chunksToRegen.isEmpty() && mc.player != null) {
                var bp = mc.player.blockPosition();
                chunksToRegen.add(new ChunkPos(bp.getX() >> 4, bp.getZ() >> 4));
            }

            server.execute(() -> {
                ServerLevel serverLevel = server.getLevel(dimKey);
                if (serverLevel == null) serverLevel = server.overworld();
                if (serverLevel != null && serverLevel.getChunkSource().getGenerator() instanceof WorldGenV2ChunkGenerator v2Gen) {
                    for (ChunkPos pos : chunksToRegen) {
                        v2Gen.regenerateLiveChunk(serverLevel, pos);
                    }
                }
            });

            state.setStatusMessage("Live-updated " + chunksToRegen.size() + " test chunk(s) in world [" + activeBiomeName + "]!");
        } else {
            state.setStatusMessage("Compiled graph for " + activeBiomeName + ".");
        }
    }

    /**
     * Compiles a Dimension Macro Graph (containing {@code World:DimensionOutput}, {@code World:RadialZone},
     * {@code World:RingLandmarks}, {@code World:SpawnIslandAnchor}, {@code World:RelationalLandmark},
     * {@code World:SubIslandCluster}, and {@code World:BiomeWeightEntry}) into a live {@link DimensionLayoutConfig},
     * {@link MacroFeatureGraph}, and {@link SplineRoadEngine}, syncs it to {@link StudioState.DimensionStudioProfile},
     * applies it to {@link WorldStructure}, and regenerates selected test chunks.
     */
    private static void compileDimensionMacroGraph(NodeCanvasTab canvas, NodeCard dimOutCard) {
        WorldStructure structure = getActiveWorldStructure();
        StudioState state = StudioState.get();
        StudioState.DimensionStudioProfile profile = state.getActiveDimensionProfile();

        // 1. Parse DimensionOutput parameters
        OutsideBoundsFallback fallback = profile.outsideFallback != null ? profile.outsideFallback : OutsideBoundsFallback.DEFAULT_BIOMES;
        double boundsRadius = profile.boundsRadius;
        double blendFalloff = profile.blendFalloff;
        int seaLevel = profile.seaLevel;
        String defaultBlock = profile.defaultBlock;
        String fluidBlock = profile.fluidBlock;

        if (dimOutCard != null) {
            String fbStr = canvas.getParamValue(dimOutCard, "outsideFallback", "DEFAULT_BIOMES");
            try {
                fallback = OutsideBoundsFallback.valueOf(fbStr.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                fallback = OutsideBoundsFallback.DEFAULT_BIOMES;
            }
            boundsRadius = canvas.getParamDouble(dimOutCard, "boundsRadius", 0.0);
            blendFalloff = canvas.getParamDouble(dimOutCard, "blendFalloff", 64.0);
            seaLevel = canvas.getParamInt(dimOutCard, "seaLevel", 63);
            defaultBlock = canvas.getParamValue(dimOutCard, "defaultBlock", "minecraft:stone");
            fluidBlock = canvas.getParamValue(dimOutCard, "fluidBlock", "minecraft:water");

            profile.outsideFallback = fallback;
            profile.boundsRadius = (float) boundsRadius;
            profile.blendFalloff = (float) blendFalloff;
            profile.seaLevel = seaLevel;
            profile.defaultBlock = defaultBlock;
            profile.fluidBlock = fluidBlock;
        }

        // 2. Collect RadialZoneRule, RingLandmarkRule, BiomePoolEntry, and Relational Landmarks
        List<RadialZoneRule> radialZones = new ArrayList<>();
        List<RingLandmarkRule> ringRules = new ArrayList<>();
        List<BiomePoolEntry> biomePool = new ArrayList<>();
        List<StudioState.EditableZoneRule> syncedZones = new ArrayList<>();
        List<StudioState.EditableRingRule> syncedRings = new ArrayList<>();
        List<StudioState.EditableLandmarkRule> syncedLandmarks = new ArrayList<>();

        MacroFeatureGraph macroGraph = new MacroFeatureGraph();
        boolean hasLandmarks = false;

        for (NodeCard card : canvas.getNodes()) {
            String type = card.schema.typeName();
            if ("RadialZone".equals(type)) {
                float innerR = (float) canvas.getParamDouble(card, "innerRadius", 0.0);
                float outerR = (float) canvas.getParamDouble(card, "outerRadius", 180.0);
                float falloff = (float) canvas.getParamDouble(card, "falloffWidth", 32.0);
                String modeStr = canvas.getParamValue(card, "zoneMode", "ISLAND_PLATEAU");
                ZoneMode mode;
                try {
                    mode = ZoneMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {
                    mode = ZoneMode.ISLAND_PLATEAU;
                }
                String biomeOver = canvas.getParamValue(card, "biomeOverride", "TemperateForest");
                float targetH = (float) canvas.getParamDouble(card, "targetHeight", 68.0);
                boolean floating = Boolean.parseBoolean(canvas.getParamValue(card, "floating", "false"));
                radialZones.add(new RadialZoneRule(
                        card.id, 0.0f, 0.0f, innerR, outerR, falloff, mode, biomeOver, targetH, 36.0f, floating
                ));
                syncedZones.add(new StudioState.EditableZoneRule(
                        card.id, innerR, outerR, falloff, mode, biomeOver, targetH, floating
                ));

            } else if ("SpawnIslandAnchor".equals(type)) {
                float islandR = (float) canvas.getParamDouble(card, "islandRadius", 220.0);
                float falloff = (float) canvas.getParamDouble(card, "coastFalloff", 48.0);
                float moatOuterR = (float) canvas.getParamDouble(card, "moatOuterRadius", 360.0);
                float plateauH = (float) canvas.getParamDouble(card, "plateauHeight", 74.0);
                String biome = canvas.getParamValue(card, "biome", "TemperateForest");
                String centerStruct = canvas.getParamValue(card, "centerStructure", "worldgenv2:spawn_shrine");
                boolean isVoidFallback = (fallback == OutsideBoundsFallback.VOID);

                radialZones.add(new RadialZoneRule(
                        card.id + "_core", 0.0f, 0.0f, 0.0f, islandR, falloff, ZoneMode.ISLAND_PLATEAU, biome, plateauH, 40.0f, isVoidFallback
                ));
                syncedZones.add(new StudioState.EditableZoneRule(
                        "spawn_island", 0.0f, islandR, falloff, ZoneMode.ISLAND_PLATEAU, biome, plateauH, isVoidFallback
                ));
                if (moatOuterR > islandR + 8.0f) {
                    radialZones.add(new RadialZoneRule(
                            card.id + "_moat",
                            0.0f,
                            0.0f,
                            islandR,
                            moatOuterR,
                            falloff,
                            isVoidFallback ? ZoneMode.VOID_MOAT : ZoneMode.BIOME_OVERRIDE,
                            isVoidFallback ? biome : "Ocean",
                            isVoidFallback ? 0.0f : 44.0f,
                            24.0f,
                            isVoidFallback
                    ));
                    syncedZones.add(new StudioState.EditableZoneRule(
                            "spawn_moat",
                            islandR,
                            moatOuterR,
                            falloff,
                            isVoidFallback ? ZoneMode.VOID_MOAT : ZoneMode.BIOME_OVERRIDE,
                            isVoidFallback ? biome : "Ocean",
                            isVoidFallback ? 0.0f : 44.0f,
                            isVoidFallback
                    ));
                }

                List<MacroFeatureGraph.MacroAnchorDef> outlets = new ArrayList<>();
                for (int a = 0; a < 8; a++) {
                    double ang = (Math.PI * 2.0 * a) / 8.0;
                    outlets.add(new MacroFeatureGraph.MacroAnchorDef(
                            "outlet_" + a, "ROAD_OUTLET", (float) Math.cos(ang), (float) Math.sin(ang)
                    ));
                }
                macroGraph.addPrimaryLandmark(new MacroFeatureGraph.PrimaryLandmarkRule(
                        card.id,
                        centerStruct,
                        Set.of(biome, "TemperateForest", "SunlitPlains"),
                        48.0f,
                        120.0f,
                        outlets
                ));
                hasLandmarks = true;

            } else if ("RingLandmarks".equals(type)) {
                float ringR = (float) canvas.getParamDouble(card, "ringRadius", 43.0);
                int count = canvas.getParamInt(card, "count", 10);
                int minH = canvas.getParamInt(card, "minHeight", 76);
                int maxH = canvas.getParamInt(card, "maxHeight", 103);
                int pilR = canvas.getParamInt(card, "pillarRadius", 4);
                String pilBlk = canvas.getParamValue(card, "pillarBlock", "minecraft:obsidian");
                boolean cap = Boolean.parseBoolean(canvas.getParamValue(card, "bedrockCap", "true"));
                ringRules.add(new RingLandmarkRule(
                        card.id, 0.0f, 0.0f, ringR, count, pilR, minH, maxH, pilBlk, cap ? "minecraft:bedrock" : pilBlk
                ));
                syncedRings.add(new StudioState.EditableRingRule(
                        card.id, ringR, count, pilR, minH, maxH, pilBlk, cap
                ));

            } else if ("SubIslandCluster".equals(type)) {
                int count = Math.clamp(canvas.getParamInt(card, "count", 4), 1, 16);
                float minD = (float) canvas.getParamDouble(card, "minDistance", 220.0);
                float maxD = (float) Math.max(minD + 24.0, canvas.getParamDouble(card, "maxDistance", 440.0));
                float subR = (float) canvas.getParamDouble(card, "islandRadius", 48.0);
                String biome = canvas.getParamValue(card, "biome", "TemperateForest");
                String structId = canvas.getParamValue(card, "structure", "worldgenv2:ancient_shrine");
                boolean isVoid = (fallback == OutsideBoundsFallback.VOID);

                radialZones.add(new RadialZoneRule(
                        card.id + "_cluster", 0.0f, 0.0f, minD, maxD, subR, ZoneMode.OUTER_ISLANDS, biome, 66.0f, 32.0f, isVoid
                ));
                syncedZones.add(new StudioState.EditableZoneRule(
                        "sub_islands", minD, maxD, subR, ZoneMode.OUTER_ISLANDS, biome, 66.0f, isVoid
                ));
                syncedLandmarks.add(new StudioState.EditableLandmarkRule(
                        "satellite_cluster", "spawn_island", minD, maxD, structId, count, false, 3.0f
                ));

            } else if ("BiomeWeightEntry".equals(type)) {
                String bName = canvas.getParamValue(card, "biome", "TemperateForest");
                float weight = (float) canvas.getParamDouble(card, "weight", 1.0);
                float sizeMult = (float) canvas.getParamDouble(card, "sizeMultiplier", 1.0);
                float transDist = (float) canvas.getParamDouble(card, "transitionDistance", 32.0);
                biomePool.add(new BiomePoolEntry(bName, weight, sizeMult, transDist));
            }
        }

        // Second pass for RelationalLandmark (so parent SpawnIslandAnchor is already registered)
        for (NodeCard card : canvas.getNodes()) {
            if ("RelationalLandmark".equals(card.schema.typeName())) {
                NodeCard parentCard = findConnectedInputCard(canvas, card.id, "parent_anchor");
                String parentId = (parentCard != null) ? parentCard.id : "spawn_island";
                String landmarkId = canvas.getParamValue(card, "landmarkId", card.id);
                int count = Math.clamp(canvas.getParamInt(card, "count", 1), 1, 12);
                float minDist = (float) canvas.getParamDouble(card, "minDistance", 100.0);
                float maxDist = (float) Math.max(minDist + 16.0, canvas.getParamDouble(card, "maxDistance", 180.0));
                String prefabId = canvas.getParamValue(card, "prefabId", "worldgenv2:watchtower_01");
                boolean connectBridge = Boolean.parseBoolean(canvas.getParamValue(card, "connectBridge", "true"));
                float roadHalfWidth = (float) canvas.getParamDouble(card, "bridgeHalfWidth", 3.5);

                for (int c = 0; c < count; c++) {
                    macroGraph.addRelationalLandmark(new MacroFeatureGraph.RelationalLandmarkRule(
                            landmarkId + "_" + card.id + "_" + c,
                            parentId,
                            "ROAD_OUTLET",
                            prefabId,
                            Set.of("TemperateForest", "SunlitPlains", "Ocean"),
                            minDist,
                            maxDist,
                            true,
                            connectBridge,
                            roadHalfWidth,
                            2.5f
                    ));
                }
                syncedLandmarks.add(new StudioState.EditableLandmarkRule(
                        landmarkId, parentId, minDist, maxDist, prefabId, count, connectBridge, roadHalfWidth
                ));
                hasLandmarks = true;
            }
        }

        // Sync back to active DimensionStudioProfile so the Dimension Inspector & Faux-Minimap match!
        profile.zones.clear();
        profile.zones.addAll(syncedZones);
        profile.rings.clear();
        profile.rings.addAll(syncedRings);
        profile.landmarks.clear();
        profile.landmarks.addAll(syncedLandmarks);

        // 3. Build DimensionLayoutConfig and apply to WorldStructure
        DimensionLayoutConfig layoutConfig = new DimensionLayoutConfig(
                state.getActiveDimension(),
                (float) boundsRadius,
                (float) blendFalloff,
                fallback,
                seaLevel,
                defaultBlock,
                fluidBlock,
                radialZones,
                ringRules,
                biomePool,
                1337L
        );
        structure.withDimensionLayout(layoutConfig);

        if (hasLandmarks) {
            macroGraph.buildGraph(structure, -64, 320, seaLevel, 1337L);
            SplineRoadEngine roadEngine = new SplineRoadEngine();
            roadEngine.buildFromMacroGraph(macroGraph, structure, -64, 320, seaLevel);
            structure.withMacroFeatureGraph(macroGraph).withSplineRoadEngine(roadEngine);
        }

        // 4. Regenerate selected test chunks on the integrated server (ONLY because we are in the Node Graph!)
        regenerateSelectedChunksLive();
        state.setStatusMessage("Applied Dimension Macro Graph (" + radialZones.size() + " zones, "
                + syncedLandmarks.size() + " landmarks, fallback=" + fallback + ") to test chunks!");
    }

    /**
     * Reconstructs a live {@link StudioState.DimensionStudioProfile}'s zones, islands, rings, landmarks,
     * and outside-bounds settings directly onto {@link NodeCanvasTab} as an interactive Dimension Macro Node Graph!
     */
    public static void loadDimensionProfileIntoCanvas(StudioState.DimensionStudioProfile profile, NodeCanvasTab canvas) {
        if (profile == null || canvas == null) return;
        StudioState state = StudioState.get();
        state.setGraphMode(StudioState.GraphMode.DIMENSION_GRAPH);
        canvas.clearCanvas();
        state.setCanvasPanX(18.0);
        state.setCanvasPanY(32.0);
        state.setCanvasZoom(1.0);

        NodeCard dimOut = canvas.spawnNode("World:DimensionOutput", 386, 28);
        dimOut.setParam("outsideFallback", profile.outsideFallback != null ? profile.outsideFallback.name() : "DEFAULT_BIOMES");
        dimOut.setParam("boundsRadius", fmt(profile.boundsRadius));
        dimOut.setParam("blendFalloff", fmt(profile.blendFalloff));
        dimOut.setParam("seaLevel", String.valueOf(profile.seaLevel));
        dimOut.setParam("defaultBlock", profile.defaultBlock != null ? profile.defaultBlock : "minecraft:stone");
        dimOut.setParam("fluidBlock", profile.fluidBlock != null ? profile.fluidBlock : "minecraft:water");

        NodeCard prevZoneCard = null;
        NodeCard spawnAnchorCard = null;
        int leftY = 12;
        int midY = 12;

        // Check if first zone is a Spawn Island plateau (innerRadius == 0) with landmarks
        int startZoneIdx = 0;
        if (!profile.zones.isEmpty() && profile.zones.getFirst().innerRadius <= 1.0f && !profile.landmarks.isEmpty()) {
            StudioState.EditableZoneRule firstZ = profile.zones.getFirst();
            float moatR = firstZ.outerRadius + 140.0f;
            if (profile.zones.size() > 1 && Math.abs(profile.zones.get(1).innerRadius - firstZ.outerRadius) < 10.0f) {
                moatR = profile.zones.get(1).outerRadius;
                startZoneIdx = 2;
            } else {
                startZoneIdx = 1;
            }
            spawnAnchorCard = canvas.spawnNode("World:SpawnIslandAnchor", 10, leftY);
            spawnAnchorCard.setParam("islandRadius", fmt(firstZ.outerRadius));
            spawnAnchorCard.setParam("coastFalloff", fmt(firstZ.falloff));
            spawnAnchorCard.setParam("moatOuterRadius", fmt(moatR));
            spawnAnchorCard.setParam("plateauHeight", fmt(firstZ.targetElevation));
            spawnAnchorCard.setParam("biome", firstZ.targetBiome != null ? firstZ.targetBiome : "TemperateForest");
            spawnAnchorCard.setParam("centerStructure", "worldgenv2:spawn_shrine");
            prevZoneCard = spawnAnchorCard;
            leftY += spawnAnchorCard.cardHeight + 12;
        }

        for (int i = startZoneIdx; i < profile.zones.size(); i++) {
            StudioState.EditableZoneRule z = profile.zones.get(i);
            NodeCard zCard = canvas.spawnNode("World:RadialZone", 10, leftY);
            zCard.setParam("zoneMode", z.mode != null ? z.mode.name() : "ISLAND_PLATEAU");
            zCard.setParam("innerRadius", fmt(z.innerRadius));
            zCard.setParam("outerRadius", fmt(z.outerRadius));
            zCard.setParam("falloffWidth", fmt(z.falloff));
            zCard.setParam("targetHeight", fmt(z.targetElevation));
            zCard.setParam("floating", String.valueOf(z.floating));
            zCard.setParam("biomeOverride", z.targetBiome != null ? z.targetBiome : "TemperateForest");
            if (prevZoneCard != null) {
                canvas.connectNodes(prevZoneCard, "out_zone", zCard, "in_zone");
            }
            prevZoneCard = zCard;
            leftY += zCard.cardHeight + 10;
        }

        if (prevZoneCard != null) {
            canvas.connectNodes(prevZoneCard, "out_zone", dimOut, "zones");
        }

        // Spawn Ring Landmarks (e.g. End Obsidian Towers)
        NodeCard lastLandmarkOut = null;
        for (StudioState.EditableRingRule r : profile.rings) {
            NodeCard rCard = canvas.spawnNode("World:RingLandmarks", 196, midY);
            rCard.setParam("ringRadius", fmt(r.ringRadius));
            rCard.setParam("count", String.valueOf(r.count));
            rCard.setParam("pillarRadius", String.valueOf(r.pillarRadius));
            rCard.setParam("minHeight", String.valueOf(r.minHeight));
            rCard.setParam("maxHeight", String.valueOf(r.maxHeight));
            rCard.setParam("pillarBlock", r.blockId != null ? r.blockId : "minecraft:obsidian");
            rCard.setParam("bedrockCap", String.valueOf(r.bedrockCap));
            if (prevZoneCard != null) {
                canvas.connectNodes(prevZoneCard, "out_zone", rCard, "in_zone");
            }
            lastLandmarkOut = rCard;
            midY += rCard.cardHeight + 10;
        }

        // Spawn Connected Landmarks (with Structure ID & Count!)
        for (StudioState.EditableLandmarkRule lm : profile.landmarks) {
            if (spawnAnchorCard == null) {
                spawnAnchorCard = canvas.spawnNode("World:SpawnIslandAnchor", 10, leftY);
                leftY += spawnAnchorCard.cardHeight + 10;
                if (prevZoneCard == null) {
                    canvas.connectNodes(spawnAnchorCard, "out_zone", dimOut, "zones");
                    prevZoneCard = spawnAnchorCard;
                }
            }
            NodeCard lmCard = canvas.spawnNode("World:RelationalLandmark", 196, midY);
            lmCard.setParam("landmarkId", lm.id);
            lmCard.setParam("prefabId", lm.structureId != null ? lm.structureId : "worldgenv2:watchtower_01");
            lmCard.setParam("count", String.valueOf(Math.max(1, lm.count)));
            lmCard.setParam("minDistance", fmt(lm.minDistance));
            lmCard.setParam("maxDistance", fmt(lm.maxDistance));
            lmCard.setParam("connectBridge", String.valueOf(lm.connectBridge));
            lmCard.setParam("bridgeHalfWidth", fmt(lm.bridgeHalfWidth));
            canvas.connectNodes(spawnAnchorCard, "anchor", lmCard, "parent_anchor");
            lastLandmarkOut = lmCard;
            midY += lmCard.cardHeight + 10;
        }

        if (lastLandmarkOut != null) {
            canvas.connectNodes(lastLandmarkOut, "out_zone", dimOut, "landmarks");
        }
    }

    /**
     * Updates the in-memory {@link WorldStructure} from a {@link StudioState.DimensionStudioProfile}
     * WITHOUT regenerating live dev chunks around the player (so navigating menus never mutates dev chunks!).
     */
    public static void applyDimensionProfileLive(StudioState.DimensionStudioProfile profile) {
        applyDimensionProfile(profile, false);
    }

    /**
     * Compiles and applies a compartmentalized {@link StudioState.DimensionStudioProfile} (including
     * its Biome Pool weights/toggles, Radial Zones, Ring Towers, Relational Landmarks, and
     * Outside-Bounds Fallback) to {@link WorldStructure}, optionally regenerating live test chunks.
     */
    public static void applyDimensionProfile(StudioState.DimensionStudioProfile profile, boolean regenerateDevChunks) {
        if (profile == null) return;
        WorldStructure structure = getActiveWorldStructure();
        StudioState state = StudioState.get();

        List<RadialZoneRule> radialZones = new ArrayList<>();
        for (StudioState.EditableZoneRule z : profile.zones) {
            radialZones.add(new RadialZoneRule(
                    z.id, 0.0f, 0.0f, z.innerRadius, z.outerRadius, z.falloff,
                    z.mode, z.targetBiome, z.targetElevation, 36.0f, z.floating
            ));
        }

        List<RingLandmarkRule> ringRules = new ArrayList<>();
        for (StudioState.EditableRingRule r : profile.rings) {
            ringRules.add(new RingLandmarkRule(
                    r.id, 0.0f, 0.0f, r.ringRadius, r.count, r.pillarRadius,
                    r.minHeight, r.maxHeight, r.blockId, r.bedrockCap ? "minecraft:bedrock" : r.blockId
            ));
        }

        List<BiomePoolEntry> biomePool = new ArrayList<>();
        for (StudioState.BiomeEntryState b : profile.biomes) {
            float effectiveWeight = b.enabled ? Math.max(0.05f, b.weight) : 0.001f;
            biomePool.add(new BiomePoolEntry(b.name, effectiveWeight, b.sizeModifier, b.transitionDistance));
        }

        DimensionLayoutConfig layoutConfig = new DimensionLayoutConfig(
                profile.dimensionId,
                profile.boundsRadius,
                profile.blendFalloff,
                profile.outsideFallback,
                profile.seaLevel,
                profile.defaultBlock,
                profile.fluidBlock,
                radialZones,
                ringRules,
                biomePool,
                1337L
        );
        structure.withDimensionLayout(layoutConfig);

        if (!profile.landmarks.isEmpty()) {
            MacroFeatureGraph macroGraph = new MacroFeatureGraph();
            String rootId = profile.zones.isEmpty() ? "spawn_island_core" : profile.zones.getFirst().id;
            List<MacroFeatureGraph.MacroAnchorDef> outlets = new ArrayList<>();
            for (int a = 0; a < 8; a++) {
                double ang = (Math.PI * 2.0 * a) / 8.0;
                outlets.add(new MacroFeatureGraph.MacroAnchorDef(
                        "outlet_" + a, "ROAD_OUTLET", (float) Math.cos(ang), (float) Math.sin(ang)
                ));
            }
            macroGraph.addPrimaryLandmark(new MacroFeatureGraph.PrimaryLandmarkRule(
                    rootId,
                    "worldgenv2:structures/watchtower_01",
                    Set.of("TemperateForest", "SunlitPlains", "Ocean"),
                    48.0f,
                    120.0f,
                    outlets
            ));
            for (StudioState.EditableLandmarkRule lm : profile.landmarks) {
                int count = Math.max(1, lm.count);
                for (int c = 0; c < count; c++) {
                    macroGraph.addRelationalLandmark(new MacroFeatureGraph.RelationalLandmarkRule(
                            lm.id + "_" + c,
                            rootId,
                            "ROAD_OUTLET",
                            lm.structureId,
                            Set.of("TemperateForest", "SunlitPlains", "Ocean"),
                            lm.minDistance,
                            Math.max(lm.minDistance + 16.0f, lm.maxDistance),
                            true,
                            lm.connectBridge,
                            lm.bridgeHalfWidth,
                            2.5f
                    ));
                }
            }
            macroGraph.buildGraph(structure, profile.minY, profile.maxY, profile.seaLevel, 1337L);
            SplineRoadEngine roadEngine = new SplineRoadEngine();
            roadEngine.buildFromMacroGraph(macroGraph, structure, profile.minY, profile.maxY, profile.seaLevel);
            structure.withMacroFeatureGraph(macroGraph).withSplineRoadEngine(roadEngine);
        }

        if (regenerateDevChunks) {
            regenerateSelectedChunksLive();
        }
        state.setStatusMessage("Updated [" + profile.displayName + "] layout ("
                + profile.getEnabledBiomeCount() + " biomes, " + radialZones.size() + " zones, fallback=" + profile.outsideFallback + ").");
    }

    /**
     * Parses and compiles a Hytale Biome JSON AST string via {@link JsonAstCompiler}, registers or
     * hot-swaps the biome inside {@link WorldStructure} and {@link StudioState}, and loads its
     * node graph directly into {@link NodeCanvasTab} so the user can inspect and edit it!
     */
    public static String importHytaleBiomeJson(String rawJson, NodeCanvasTab canvas) {
        if (rawJson == null || rawJson.isBlank()) {
            StudioState.get().setStatusMessage("Cannot import empty Hytale JSON.");
            return null;
        }
        try {
            JsonAstCompiler.registerBuiltInTypes();
            WorldStructure structure = getActiveWorldStructure();
            StudioState state = StudioState.get();

            JsonAstCompiler.CompiledBiome<BlockState> compiled = JsonAstCompiler.compileBiome(
                    DefaultBiomeCatalog.TEMPERATE_FOREST,
                    rawJson,
                    new JsonAstCompiler.CompileContext(1337L),
                    Map.of(),
                    id -> WorldGenV2ChunkGenerator.resolveBlockState(id, Blocks.STONE.defaultBlockState())
            );

            TileBiome compiledTile = compiled.tileBiome();
            String rawName = compiled.name() != null ? compiled.name() : "HytaleImportedBiome";
            String cleanName = rawName.contains(":") ? rawName.substring(rawName.indexOf(':') + 1) : rawName;

            // Update or bind to an existing TileBiome slot so it immediately generates in test chunks
            TileBiome targetSlot = structure.findTileBiomeByName(cleanName);
            if (targetSlot == null) {
                targetSlot = structure.findTileBiomeByName(state.getActiveBiome());
            }
            if (targetSlot == null) {
                targetSlot = structure.findTileBiome(DefaultBiomeCatalog.TEMPERATE_FOREST);
            }

            if (targetSlot != null && compiledTile != null) {
                TileBiome merged = new TileBiome(
                        targetSlot.id(),
                        cleanName,
                        compiledTile.weight(),
                        compiledTile.sizeModifier(),
                        compiledTile.defaultTransitionDistance(),
                        compiledTile.terrainDensity() != null ? compiledTile.terrainDensity() : targetSlot.terrainDensity(),
                        compiledTile.materialProvider() != null ? compiledTile.materialProvider() : targetSlot.materialProvider(),
                        compiledTile.props() != null && !compiledTile.props().isEmpty() ? compiledTile.props() : targetSlot.props(),
                        targetSlot.climate(),
                        compiledTile.defaultBlock() != null ? compiledTile.defaultBlock() : targetSlot.defaultBlock(),
                        compiledTile.fluidBlock() != null ? compiledTile.fluidBlock() : targetSlot.fluidBlock(),
                        compiledTile.seaLevel() != null ? compiledTile.seaLevel() : targetSlot.seaLevel()
                );
                structure.updateTileBiome(merged);
            }

            float w = compiledTile != null ? compiledTile.weight() : 1.5f;
            float sz = compiledTile != null ? compiledTile.sizeModifier() : 1.0f;
            float tr = compiledTile != null ? compiledTile.defaultTransitionDistance() : 32.0f;
            state.registerImportedHytaleBiome(state.getActiveDimension(), cleanName, w, sz, tr, rawJson);
            state.setActiveBiome(cleanName);

            if (canvas != null) {
                loadBiomeIntoCanvas(cleanName, canvas);
            }
            regenerateSelectedChunksLive();
            state.setStatusMessage("Imported Hytale Biome [" + cleanName + "] & loaded its AST into the Node Editor!");
            return cleanName;
        } catch (Exception e) {
            StudioState.get().setStatusMessage("Hytale JSON Import Error: " + e.getMessage());
            return null;
        }
    }

    public static void regenerateSelectedChunksLive() {
        Minecraft mc = Minecraft.getInstance();
        StudioState state = StudioState.get();
        if (mc != null && mc.level != null && mc.getSingleplayerServer() != null) {
            MinecraftServer server = mc.getSingleplayerServer();
            var dimKey = mc.level.dimension();
            List<ChunkPos> chunksToRegen = new ArrayList<>(state.getSelectedChunks());
            if (chunksToRegen.isEmpty() && mc.player != null) {
                var bp = mc.player.blockPosition();
                chunksToRegen.add(new ChunkPos(bp.getX() >> 4, bp.getZ() >> 4));
            }

            server.execute(() -> {
                ServerLevel serverLevel = server.getLevel(dimKey);
                if (serverLevel == null) serverLevel = server.overworld();
                if (serverLevel != null && serverLevel.getChunkSource().getGenerator() instanceof WorldGenV2ChunkGenerator v2Gen) {
                    for (ChunkPos pos : chunksToRegen) {
                        v2Gen.regenerateLiveChunk(serverLevel, pos);
                    }
                }
            });
        }
    }

    private static DensityFunction compileDensityNode(NodeCanvasTab canvas, NodeCard card, Set<String> visited) {
        if (card == null || !visited.add(card.id)) {
            return new VerticalGradientDensity(68.0f, 24.0f);
        }

        String type = card.schema.typeName();
        return switch (type) {
            case "VerticalGradientDensity" -> {
                float baseH = (float) canvas.getParamDouble(card, "baseHeight", 68.0);
                float scale = (float) Math.max(1.0, canvas.getParamDouble(card, "scale", 26.0));
                yield new VerticalGradientDensity(baseH, scale);
            }
            case "ConstantDensity" -> {
                float val = (float) canvas.getParamDouble(card, "value", 0.0);
                yield new ConstantDensity(val);
            }
            case "SimplexNoise2D", "PerlinNoise2D", "Noise2dDensity" -> {
                float freq = (float) canvas.getParamDouble(card, "frequency", 0.005);
                int oct = Math.clamp(canvas.getParamInt(card, "octaves", 3), 1, 8);
                float amp = (float) canvas.getParamDouble(card, "amplitude", 0.5);
                int seed = canvas.getParamInt(card, "seed", 101);
                String fractalStr = canvas.getParamValue(card, "fractalType", "FBm");
                FastNoiseLite fnl = new FastNoiseLite(seed);
                fnl.setNoiseType(type.contains("Perlin") ? FastNoiseLite.NoiseType.Perlin : FastNoiseLite.NoiseType.OpenSimplex2);
                fnl.setFrequency(freq);
                if ("Ridged".equalsIgnoreCase(fractalStr)) {
                    fnl.setFractalType(FastNoiseLite.FractalType.Ridged);
                    fnl.setFractalOctaves(oct);
                } else if (oct > 1) {
                    fnl.setFractalType(FastNoiseLite.FractalType.FBm);
                    fnl.setFractalOctaves(oct);
                } else {
                    fnl.setFractalType(FastNoiseLite.FractalType.None);
                }
                yield new Noise2dDensity(fnl, amp);
            }
            case "SimplexNoise3D", "PerlinNoise3D", "Noise3dDensity" -> {
                float freq = (float) canvas.getParamDouble(card, "frequency", 0.011);
                int oct = Math.clamp(canvas.getParamInt(card, "octaves", 3), 1, 8);
                float amp = (float) canvas.getParamDouble(card, "amplitude", 0.45);
                float xzScale = (float) canvas.getParamDouble(card, "xzScale", 1.0);
                float yScale = (float) canvas.getParamDouble(card, "yScale", 1.0);
                int seed = canvas.getParamInt(card, "seed", 202);
                FastNoiseLite fnl = new FastNoiseLite(seed);
                fnl.setNoiseType(type.contains("Perlin") ? FastNoiseLite.NoiseType.Perlin : FastNoiseLite.NoiseType.OpenSimplex2);
                fnl.setFrequency(freq);
                if (oct > 1) {
                    fnl.setFractalType(FastNoiseLite.FractalType.FBm);
                    fnl.setFractalOctaves(oct);
                }
                yield new Noise3dDensity(fnl, amp, xzScale, yScale);
            }
            case "CellNoise2D" -> {
                float freq = (float) canvas.getParamDouble(card, "frequency", 0.01);
                float jitter = (float) canvas.getParamDouble(card, "jitter", 0.35);
                float amp = (float) canvas.getParamDouble(card, "amplitude", 1.0);
                FastNoiseLite fnl = new FastNoiseLite(1337);
                fnl.setNoiseType(FastNoiseLite.NoiseType.Cellular);
                fnl.setFrequency(freq);
                fnl.setCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
                fnl.setCellularReturnType(FastNoiseLite.CellularReturnType.Distance);
                fnl.setCellularJitter(jitter);
                yield new CellNoise2dDensity(fnl, amp);
            }
            case "CellNoise3D", "CellWallDistance" -> {
                float freq = (float) canvas.getParamDouble(card, "frequency", 0.015);
                float jitter = (float) canvas.getParamDouble(card, "jitter", 0.35);
                float amp = (float) canvas.getParamDouble(card, "amplitude", 1.0);
                FastNoiseLite fnl = new FastNoiseLite(1337);
                fnl.setNoiseType(FastNoiseLite.NoiseType.Cellular);
                fnl.setFrequency(freq);
                fnl.setCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
                fnl.setCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
                fnl.setCellularJitter(jitter);
                yield new SumDensity(
                    new VerticalGradientDensity(70.0f, 24.0f),
                    new CellNoise3dDensity(fnl, amp)
                );
            }
            case "YSampledDensity" -> {
                int yInt = Math.clamp(canvas.getParamInt(card, "yInterval", 8), 1, 64);
                NodeCard inCard = findConnectedInputCard(canvas, card.id, "input");
                DensityFunction child = compileDensityNode(canvas, inCard, new HashSet<>(visited));
                yield new YSampledDensity(child, yInt);
            }
            case "SumDensity" -> {
                List<DensityFunction> parts = new ArrayList<>();
                for (String sock : new String[]{"in_a", "in_b", "in_c"}) {
                    NodeCard in = findConnectedInputCard(canvas, card.id, sock);
                    if (in != null) {
                        parts.add(compileDensityNode(canvas, in, new HashSet<>(visited)));
                    }
                }
                float bias = (float) canvas.getParamDouble(card, "offset", 0.0);
                if (Math.abs(bias) > 1e-4f) {
                    parts.add(new ConstantDensity(bias));
                }
                if (parts.isEmpty()) {
                    parts.add(new VerticalGradientDensity(68.0f, 24.0f));
                }
                yield new SumDensity(parts);
            }
            case "ProductDensity", "MultiplierDensity" -> {
                NodeCard a = findConnectedInputCard(canvas, card.id, "in_a");
                NodeCard b = findConnectedInputCard(canvas, card.id, "in_b");
                DensityFunction da = a != null ? compileDensityNode(canvas, a, new HashSet<>(visited)) : new ConstantDensity(1.0f);
                DensityFunction db = b != null ? compileDensityNode(canvas, b, new HashSet<>(visited)) : new ConstantDensity(1.0f);
                yield new ProductDensity(da, db);
            }
            case "MinDensity" -> {
                NodeCard a = findConnectedInputCard(canvas, card.id, "in_a");
                NodeCard b = findConnectedInputCard(canvas, card.id, "in_b");
                DensityFunction da = a != null ? compileDensityNode(canvas, a, new HashSet<>(visited)) : new ConstantDensity(0.0f);
                DensityFunction db = b != null ? compileDensityNode(canvas, b, new HashSet<>(visited)) : new ConstantDensity(0.0f);
                yield new MinDensity(da, db);
            }
            case "MaxDensity" -> {
                NodeCard a = findConnectedInputCard(canvas, card.id, "in_a");
                NodeCard b = findConnectedInputCard(canvas, card.id, "in_b");
                DensityFunction da = a != null ? compileDensityNode(canvas, a, new HashSet<>(visited)) : new ConstantDensity(0.0f);
                DensityFunction db = b != null ? compileDensityNode(canvas, b, new HashSet<>(visited)) : new ConstantDensity(0.0f);
                yield new MaxDensity(da, db);
            }
            case "ClampDensity", "SmoothClampDensity" -> {
                NodeCard in = findConnectedInputCard(canvas, card.id, "input");
                DensityFunction child = in != null ? compileDensityNode(canvas, in, new HashSet<>(visited)) : new VerticalGradientDensity(68.0f, 24.0f);
                float min = (float) canvas.getParamDouble(card, "min", -1.0);
                float max = (float) canvas.getParamDouble(card, "max", 1.0);
                yield new ClampDensity(child, min, max);
            }
            case "AbsDensity" -> {
                NodeCard in = findConnectedInputCard(canvas, card.id, "input");
                DensityFunction child = in != null ? compileDensityNode(canvas, in, new HashSet<>(visited)) : new ConstantDensity(0.0f);
                yield new AbsDensity(child);
            }
            case "InvertDensity", "InverterDensity" -> {
                NodeCard in = findConnectedInputCard(canvas, card.id, "input");
                DensityFunction child = in != null ? compileDensityNode(canvas, in, new HashSet<>(visited)) : new ConstantDensity(0.0f);
                yield new InvertDensity(child);
            }
            case "GradientWarpDensity", "FastGradientWarp" -> {
                NodeCard in = findConnectedInputCard(canvas, card.id, "input");
                NodeCard warpIn = findConnectedInputCard(canvas, card.id, "warpSource");
                DensityFunction base = in != null
                    ? compileDensityNode(canvas, in, new HashSet<>(visited))
                    : new VerticalGradientDensity(72.0f, 26.0f);
                DensityFunction warpField = warpIn != null
                    ? compileDensityNode(canvas, warpIn, new HashSet<>(visited))
                    : new Noise2dDensity(103, FastNoiseLite.NoiseType.OpenSimplex2, 0.008f, 2, 1.0f);
                float strength = (float) canvas.getParamDouble(card, "warpStrength", 14.0);
                yield new GradientWarpDensity(base, warpField, strength);
            }
            case "CurveMapperDensity" -> {
                NodeCard in = findConnectedInputCard(canvas, card.id, "input");
                DensityFunction base = in != null
                    ? compileDensityNode(canvas, in, new HashSet<>(visited))
                    : new Noise2dDensity(101, FastNoiseLite.NoiseType.OpenSimplex2, 0.005f, 3, 0.6f);
                Curve curve = new DistanceSCurve(1.0, 1.0, 1.0, 0.5, 0.25);
                yield new SumDensity(new VerticalGradientDensity(74.0f, 24.0f), new CurveMapperDensity(base, curve));
            }
            default -> {
                NodeCard in = findConnectedInputCard(canvas, card.id, "input");
                if (in != null) {
                    yield compileDensityNode(canvas, in, new HashSet<>(visited));
                }
                yield new VerticalGradientDensity(70.0f, 24.0f);
            }
        };
    }

    private static MaterialProvider<BlockState> compileMaterialNode(NodeCanvasTab canvas, NodeCard card) {
        if (card == null) {
            return DefaultWorldFactory.createDefaultMaterialProvider();
        }
        String type = card.schema.typeName();
        return switch (type) {
            case "ConstantMaterialProvider" -> {
                String blk = canvas.getParamValue(card, "block", "minecraft:stone");
                BlockState state = WorldGenV2ChunkGenerator.resolveBlockState(blk, Blocks.STONE.defaultBlockState());
                yield new ConstantMaterialProvider<>(state);
            }
            case "StrataMaterialProvider", "StripedMaterialProvider" -> {
                String upper = canvas.getParamValue(card, "upperBlock", "minecraft:red_sand");
                String lower = canvas.getParamValue(card, "lowerBlock", "minecraft:terracotta");
                BlockState upState = WorldGenV2ChunkGenerator.resolveBlockState(upper, Blocks.RED_SAND.defaultBlockState());
                BlockState lowState = WorldGenV2ChunkGenerator.resolveBlockState(lower, Blocks.TERRACOTTA.defaultBlockState());
                yield DefaultBiomeFactory.deepslateStrata(new SpaceAndDepthMaterialProvider<>(
                    List.of(
                        new SpaceAndDepthMaterialProvider.Layer<>(upState, 2),
                        new SpaceAndDepthMaterialProvider.Layer<>(lowState, 6),
                        new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
                    ),
                    Blocks.STONE.defaultBlockState(),
                    lowState,
                    48.0f,
                    2
                ));
            }
            default -> {
                // SpaceAndDepthMaterialProvider (and connected topMaterial/fillerMaterial overrides)
                String surfId = canvas.getParamValue(card, "surfaceBlock", "minecraft:grass_block");
                String subId = canvas.getParamValue(card, "subsoilBlock", "minecraft:dirt");
                String cliffId = canvas.getParamValue(card, "cliffBlock", "minecraft:stone");
                int subDepth = Math.clamp(canvas.getParamInt(card, "subsoilDepth", 3), 1, 16);
                float maxSlope = (float) canvas.getParamDouble(card, "maxSlope", 45.0);

                NodeCard topOverride = findConnectedInputCard(canvas, card.id, "topMaterial");
                if (topOverride != null) {
                    surfId = canvas.getParamValue(topOverride, "block", surfId);
                }
                NodeCard subOverride = findConnectedInputCard(canvas, card.id, "fillerMaterial");
                if (subOverride != null) {
                    subId = canvas.getParamValue(subOverride, "block", subId);
                }

                BlockState surfState = WorldGenV2ChunkGenerator.resolveBlockState(surfId, Blocks.GRASS_BLOCK.defaultBlockState());
                BlockState subState = WorldGenV2ChunkGenerator.resolveBlockState(subId, Blocks.DIRT.defaultBlockState());
                BlockState cliffState = WorldGenV2ChunkGenerator.resolveBlockState(cliffId, Blocks.STONE.defaultBlockState());

                yield DefaultBiomeFactory.deepslateStrata(new SpaceAndDepthMaterialProvider<>(
                    List.of(
                        new SpaceAndDepthMaterialProvider.Layer<>(surfState, 1),
                        new SpaceAndDepthMaterialProvider.Layer<>(subState, subDepth),
                        new SpaceAndDepthMaterialProvider.Layer<>(Blocks.STONE.defaultBlockState(), 999)
                    ),
                    Blocks.STONE.defaultBlockState(),
                    cliffState,
                    maxSlope,
                    2
                ));
            }
        };
    }

    private static NodeCard findConnectedInputCard(NodeCanvasTab canvas, String toNodeId, String toSocketId) {
        for (WireConnection w : canvas.getWires()) {
            if (w.toNodeId.equals(toNodeId) && w.toSocketId.equalsIgnoreCase(toSocketId)) {
                for (NodeCard n : canvas.getNodes()) {
                    if (n.id.equals(w.fromNodeId)) {
                        return n;
                    }
                }
            }
        }
        return null;
    }

    private static String fmt(float v) {
        if (Math.abs(v - Math.round(v)) < 0.001f) {
            return String.format(Locale.ROOT, "%.1f", v);
        }
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }

    private static String fmtFreq(float v) {
        return String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", ".0");
    }
}
