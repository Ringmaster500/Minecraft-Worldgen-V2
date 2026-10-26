package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.biome.WorldGenV2Registries;
import com.worldgenv2.density.*;
import com.worldgenv2.engine.chunkgenerator.StagedChunkGenerator;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.worldstructure.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Verification suite for:
 * 1. BiCarta 5x5 + 2D Domain Warp + Multi-Biome (Primary/Secondary/Tertiary) Seamless Blending
 * 2. Phase 3: WorldGenV2Registries, JsonAstCompiler (Doc 07 + Shorthand + CustomBiome), & AST Optimizer
 * 3. Phase 4: ConditionalSpawnProvider, MacroFeatureGraph (A => B), & SplineRoadEngine (Grade/Bridge/Tunnel)
 */
public final class Phase3And4VerificationTest {

    private static final class MapVoxelSpace implements VoxelSpace<String> {
        private final Map<Long, String> blocks = new HashMap<>();
        private final int minY;
        private final int maxY;

        MapVoxelSpace(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        private static long pack(int x, int y, int z) {
            return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
        }

        @Override
        public String getBlock(int x, int y, int z) {
            return blocks.getOrDefault(pack(x, y, z), "minecraft:air");
        }

        @Override
        public void setBlock(int x, int y, int z, String block) {
            if ("minecraft:air".equals(block)) {
                blocks.remove(pack(x, y, z));
            } else {
                blocks.put(pack(x, y, z), block);
            }
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return "minecraft:air".equals(getBlock(x, y, z));
        }

        @Override
        public int getMinY() {
            return minY;
        }

        @Override
        public int getMaxY() {
            return maxY;
        }
    }

    public static void main(String[] args) throws Exception {
        testBiCartaSeamlessMultiBiomeBlending();
        testPhase3RegistriesAndAstOptimizer();
        testPhase3BiomeJsonCompilation();
        testPhase4ConditionalSpawnAndIslandMask();
        testPhase4MacroGraphAndSplineRoads();
        System.out.println("ALL BI-CARTA, PHASE 3 & PHASE 4 VERIFICATION TESTS PASSED!");
    }

    private static void testBiCartaSeamlessMultiBiomeBlending() {
        // Three biomes with deliberately asymmetric sizeModifiers (0.6, 1.3, 1.8),
        // different transition distances (40, 56, 64), and distinct constant densities (0.0, 50.0, 100.0)
        // so any discontinuity in Voronoi blending immediately manifests as a sharp step.
        TileBiome b0 = new TileBiome(0, "LowPlains", 10.0f, 0.6f, 40.0f, new ConstantDensity(0.0f));
        TileBiome b1 = new TileBiome(1, "MidForest", 10.0f, 1.3f, 56.0f, new ConstantDensity(50.0f));
        TileBiome b2 = new TileBiome(2, "HighPeaks", 10.0f, 1.8f, 64.0f, new ConstantDensity(100.0f));

        WorldStructure ws = new WorldStructure(List.of(b0, b1, b2), List.of(), 128, 987654321L);

        // Sweep a 2D grid across multiple cell boundaries and triple junctions at 1-block resolution
        float maxStepDelta = 0.0f;
        for (int z = -256; z <= 256; z += 16) {
            float prevVal = ws.queryBiome(-256, z).computeBlendedPoint(-256, 64, z);
            for (int x = -255; x <= 256; x++) {
                float currVal = ws.queryBiome(x, z).computeBlendedPoint(x, 64, z);
                float delta = Math.abs(currVal - prevVal);
                if (delta > maxStepDelta) {
                    maxStepDelta = delta;
                }
                prevVal = currVal;
            }
        }

        // Across a transition corridor of 40-64 blocks between values 0 and 100, a 1-block step
        // should never jump by 20-50 units (which happens if sizeModifier or triple-junction pops occur).
        assertTrue(
            maxStepDelta < 6.5f,
            "BiCarta blended density must be C1 continuous without border or triple-junction pops (maxDelta=" + maxStepDelta + ")"
        );

        // Also verify CustomBiome overlay restricted to biome 0 fades smoothly at Voronoi borders
        CustomBiome overlay = new CustomBiome(
            10,
            "HighPlateauOverlay",
            10,
            Set.of(0),
            -1.0f,
            1.0f,
            0.1f,
            new ConstantDensity(0.5f),
            new ConstantDensity(120.0f),
            null,
            List.of()
        );
        WorldStructure wsWithCustom = new WorldStructure(List.of(b0, b1, b2), List.of(overlay), 128, 987654321L);
        float maxCustomStepDelta = 0.0f;
        for (int z = -128; z <= 128; z += 32) {
            float prevVal = wsWithCustom.queryBiome(-128, z).computeBlendedPoint(-128, 64, z);
            for (int x = -127; x <= 128; x++) {
                float currVal = wsWithCustom.queryBiome(x, z).computeBlendedPoint(x, 64, z);
                float delta = Math.abs(currVal - prevVal);
                if (delta > maxCustomStepDelta) {
                    maxCustomStepDelta = delta;
                }
                prevVal = currVal;
            }
        }
        assertTrue(
            maxCustomStepDelta < 8.5f,
            "CustomBiome must fade to 0 before Voronoi border so it never forms a vertical cliff wall (maxDelta=" + maxCustomStepDelta + ")"
        );

        // Verify climatic zone coherence: when b0 (Hot/Arid) and b2 (Cold/Alpine) are separated by a broad
        // middle buffer biome b1 (Temperate), b0 and b2 should never directly border each other with significant blend weight
        TileBiome arid = new TileBiome(0, "AridDunes", 12.0f, 1.5f, 92.0f, new ConstantDensity(0.0f));
        TileBiome temperate = new TileBiome(1, "TemperateForest", 22.0f, 1.4f, 88.0f, new ConstantDensity(50.0f));
        TileBiome alpine = new TileBiome(2, "AlpinePeaks", 12.0f, 1.5f, 96.0f, new ConstantDensity(100.0f));
        BiCarta<TileBiome> climaticCarta = new BiCarta<>(List.of(arid, temperate, alpine), 288, 1337L);

        for (int x = -2048; x <= 2048; x += 64) {
            for (int z = -2048; z <= 2048; z += 64) {
                BiCarta.CellQueryResult<TileBiome> res = climaticCarta.query(x, z);
                if (res.blendFactor() > 0.05f) {
                    boolean hotTouchingCold = (res.primary().id() == 0 && res.secondary().id() == 2)
                        || (res.primary().id() == 2 && res.secondary().id() == 0);
                    assertTrue(
                        !hotTouchingCold,
                        "Hot AridDunes (0) must never directly border Cold AlpinePeaks (2) at (" + x + ", " + z + ")"
                    );
                }
            }
        }
    }

    private static void testPhase3RegistriesAndAstOptimizer() {
        // 1. Register a custom third-party DensityFunction node in WorldGenV2Registries
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("mymod:mana_wave", (json, ctx) -> {
            float boost = ((Number) json.getOrDefault("boost", 0.5f)).floatValue();
            return new ConstantDensity(boost);
        });
        assertTrue(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("mymod:mana_wave"), "Custom density type registered");

        JsonAstCompiler compiler = new JsonAstCompiler();

        // 2. Test zero-product pruning: Product(Noise3d, Constant(0.0)) -> Constant(0.0)
        String zeroProductJson = """
            {
              "type": "ProductDensity",
              "inputs": [
                { "type": "Noise3dDensity", "frequency": 0.02, "amplitude": 5.0 },
                { "type": "ConstantDensity", "value": 0.0 }
              ]
            }
            """;
        DensityFunction pruned = compiler.compileDensityJson(zeroProductJson, 42L);
        assertTrue(pruned instanceof ConstantDensity, "Product by 0.0 must be folded to ConstantDensity");
        assertApprox(0.0f, pruned.compute(12, 34, 56), 1e-6f, "Pruned zero product value");

        // 3. Test consecutive OffsetDensity collapsing and double InvertDensity elimination
        DensityFunction base = new VerticalGradientDensity(64.0f, 32.0f);
        DensityFunction unoptimized = new InvertDensity(new InvertDensity(
            new OffsetDensity(new OffsetDensity(base, 3.0f, 4.0f, -2.0f), 7.0f, -1.0f, 5.0f)
        ));
        DensityFunction optimized = JsonAstCompiler.optimizeDensity(unoptimized);
        assertTrue(optimized instanceof OffsetDensity, "Nested OffsetDensity + double InvertDensity should collapse to single OffsetDensity");
        OffsetDensity collapsedOffset = (OffsetDensity) optimized;
        assertApprox(10.0f, collapsedOffset.offsetX(), 1e-5f, "Collapsed offsetX");
        assertApprox(3.0f, collapsedOffset.offsetY(), 1e-5f, "Collapsed offsetY");
        assertApprox(3.0f, collapsedOffset.offsetZ(), 1e-5f, "Collapsed offsetZ");

        // 4. Test consecutive ScaleDensity collapsing
        DensityFunction scaled = JsonAstCompiler.optimizeDensity(
            new ScaleDensity(new ScaleDensity(base, 2.0f, 0.5f, 3.0f), 0.5f, 4.0f, 2.0f)
        );
        assertTrue(scaled instanceof ScaleDensity, "Nested ScaleDensity should collapse to single ScaleDensity");
        ScaleDensity collapsedScale = (ScaleDensity) scaled;
        assertApprox(1.0f, collapsedScale.scaleX(), 1e-5f, "Collapsed scaleX");
        assertApprox(2.0f, collapsedScale.scaleY(), 1e-5f, "Collapsed scaleY");
        assertApprox(6.0f, collapsedScale.scaleZ(), 1e-5f, "Collapsed scaleZ");
    }

    private static void testPhase3BiomeJsonCompilation() throws Exception {
        JsonAstCompiler compiler = new JsonAstCompiler();
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1337L);

        String forestJson = Files.readString(Path.of("src/main/resources/data/worldgenv2/biomes/temperate_forest.json"));
        String alpineJson = Files.readString(Path.of("src/main/resources/data/worldgenv2/biomes/alpine_peaks.json"));
        String dunesJson = Files.readString(Path.of("src/main/resources/data/worldgenv2/biomes/arid_dunes.json"));
        String groveJson = Files.readString(Path.of("src/main/resources/data/worldgenv2/biomes/old_growth_grove.json"));

        Map<String, Integer> nameToId = Map.of(
            "minecraft:temperate_forest", 0,
            "TemperateForest", 0,
            "minecraft:alpine_peaks", 1,
            "minecraft:arid_dunes", 2
        );

        JsonAstCompiler.CompiledBiome<String> forest = compiler.compileBiome(0, forestJson, ctx, nameToId, Function.identity());
        JsonAstCompiler.CompiledBiome<String> alpine = compiler.compileBiome(1, alpineJson, ctx, nameToId, Function.identity());
        JsonAstCompiler.CompiledBiome<String> dunes = compiler.compileBiome(2, dunesJson, ctx, nameToId, Function.identity());
        JsonAstCompiler.CompiledBiome<String> grove = compiler.compileBiome(3, groveJson, ctx, nameToId, Function.identity());

        assertTrue(!forest.isCustomBiome() && forest.tileBiome() != null, "Temperate forest compiled as TileBiome");
        assertTrue(!alpine.isCustomBiome() && alpine.tileBiome() != null, "Alpine peaks compiled as TileBiome");
        assertTrue(!dunes.isCustomBiome() && dunes.tileBiome() != null, "Arid dunes (full Doc 07 AST) compiled as TileBiome");
        assertTrue(grove.isCustomBiome() && grove.customBiome() != null, "Old growth grove compiled as CustomBiome");
        assertTrue(grove.customBiome().parentBiomeIds().contains(0), "Old growth grove parent biome ID 0 resolved");

        // Verify compiled material provider & props on Arid Dunes
        MapVoxelSpace space = new MapVoxelSpace(0, 128);
        MaterialProvider<String> dunesMat = dunes.tileBiome().typedMaterialProvider();
        assertEquals("minecraft:sand", dunesMat.getMaterial(0, 70, 0, 0, 10, 10.0f, space), "Arid dunes surface material");
    }

    private static void testPhase4ConditionalSpawnAndIslandMask() {
        ConditionalSpawnProvider islandSpawn = ConditionalSpawnProvider.islandStart(
            120.0f,
            80.0f,
            Set.of("TemperateForest"),
            64,
            110
        );

        // Verify radial smoothstep falloff mask M(r)
        assertApprox(1.0f, islandSpawn.computeIslandMask(0.0f, 0.0f), 1e-5f, "Island center mask == 1.0");
        assertApprox(1.0f, islandSpawn.computeIslandMask(100.0f, 0.0f), 1e-5f, "Within island radius mask == 1.0");
        float midBufferMask = islandSpawn.computeIslandMask(160.0f, 0.0f);
        assertTrue(midBufferMask > 0.1f && midBufferMask < 0.9f, "Mid ocean buffer mask should smoothly transition");
        assertApprox(0.0f, islandSpawn.computeIslandMask(220.0f, 0.0f), 1e-5f, "Outside ocean buffer mask == 0.0");

        // Wrap a constant 68-height gradient in the island mask and verify ocean ring depression
        DensityFunction islandTerrain = islandSpawn.wrapDensityWithIslandMask(
            new VerticalGradientDensity(68.0f, 24.0f),
            0.9f
        );
        TileBiome islandBiome = new TileBiome(0, "TemperateForest", 10.0f, 1.0f, 32.0f, islandTerrain);
        WorldStructure ws = new WorldStructure(List.of(islandBiome), List.of(), 128, 42L);

        ConditionalSpawnProvider.SpawnPoint spawn = islandSpawn.resolveSpawnPoint(ws, 0, 128, 63);
        assertTrue(spawn.y() > 63, "Spawn point must be above sea level (Y=" + spawn.y() + ")");
        assertTrue(spawn.islandMaskActive(), "Island mask flag active");

        // At r = 240 (in the ocean ring), surface at Y=64 must be depressed below sea level (D < 0)
        assertTrue(islandTerrain.compute(240.0f, 64.0f, 0.0f) < 0.0f, "Ocean ring outside island perimeter must be below sea level");
    }

    private static void testPhase4MacroGraphAndSplineRoads() {
        TileBiome flatPlains = new TileBiome(
            0,
            "TemperateForest",
            10.0f,
            1.0f,
            32.0f,
            new VerticalGradientDensity(70.0f, 20.0f),
            new ConstantMaterialProvider<>("minecraft:grass_block"),
            List.of()
        );
        WorldStructure ws = new WorldStructure(List.of(flatPlains), List.of(), 128, 2026L);

        // 1. Build MacroFeatureGraph with Primary Landmark A => Relational Landmark B
        MacroFeatureGraph graph = new MacroFeatureGraph()
            .addPrimaryLandmark(new MacroFeatureGraph.PrimaryLandmarkRule(
                "sun_temple",
                "worldgenv2:structures/watchtower_01",
                Set.of("TemperateForest"),
                32.0f,
                64.0f,
                List.of(new MacroFeatureGraph.MacroAnchorDef("north_gate", "ROAD_OUTLET", 0.0f, -1.0f))
            ))
            .addRelationalLandmark(new MacroFeatureGraph.RelationalLandmarkRule(
                "guardian_watchtower",
                "sun_temple",
                "ROAD_OUTLET",
                "worldgenv2:structures/watchtower_01",
                Set.of("TemperateForest"),
                80.0f,
                140.0f,
                true,
                true,
                6.0f,
                3.0f
            ));

        graph.buildGraph(ws, 0, 128, 63, 2026L);

        MacroFeatureGraph.PlacedLandmark temple = graph.getLandmark("sun_temple");
        MacroFeatureGraph.PlacedLandmark tower = graph.getLandmark("guardian_watchtower");
        assertTrue(temple != null, "Primary landmark 'sun_temple' placed");
        assertTrue(tower != null, "Relational landmark 'guardian_watchtower' placed");
        assertEquals("sun_temple", tower.parentLandmarkId(), "Relational landmark parent ID");
        assertTrue(temple.anchors().getFirst().isClaimed(), "Primary landmark MacroAnchor claimed by child");
        assertEquals(1, graph.getRoadEdges().size(), "Topological RoadEdge created between landmarks");

        // 2. Build SplineRoadEngine and test Surface Road, Bridge over Ravine, and Mountain Tunnel modes
        SplineRoadEngine roadEngine = new SplineRoadEngine();
        roadEngine.addRoadSpline(
            "test_highway",
            List.of(
                new SplineRoadEngine.ControlPoint(0.0f, 70.0f, 0.0f),
                new SplineRoadEngine.ControlPoint(32.0f, 70.0f, 0.0f),
                new SplineRoadEngine.ControlPoint(64.0f, 70.0f, 0.0f)
            ),
            6.0f,
            3.0f
        );

        SplineRoadEngine.RoadQueryResult centerQuery = roadEngine.queryRoad(32.0f, 0.0f);
        assertTrue(centerQuery.active() && centerQuery.isRoadDeck(), "Point (32, 0) lies on road spine");
        assertApprox(70.0f, centerQuery.roadElevation(), 0.5f, "Road elevation at (32, 0)");

        // Test Bridge Mode when natural column is open ravine air (D = -1.0)
        float[] ravineCol = new float[128];
        java.util.Arrays.fill(ravineCol, -1.0f);
        SplineRoadEngine.RoadSegmentMode bridgeMode = roadEngine.applyRoadToColumn(32.0f, 0.0f, 0, 128, ravineCol);
        assertEquals(SplineRoadEngine.RoadSegmentMode.BRIDGE, bridgeMode, "Ravine column should trigger BRIDGE mode");
        assertTrue(ravineCol[70] > 0.0f, "Bridge deck at Y=70 must be solid");

        // Test Tunnel Mode when natural column is deep inside a tall mountain (D = +1.0 up to Y=100)
        float[] mountainCol = new float[128];
        for (int y = 0; y < 100; y++) {
            mountainCol[y] = 1.0f;
        }
        SplineRoadEngine.RoadSegmentMode tunnelMode = roadEngine.applyRoadToColumn(32.0f, 0.0f, 0, 128, mountainCol);
        assertEquals(SplineRoadEngine.RoadSegmentMode.TUNNEL, tunnelMode, "Mountain column should trigger TUNNEL mode");
        assertTrue(mountainCol[70] > 0.0f, "Tunnel floor at Y=70 must remain solid");
        assertTrue(mountainCol[72] < 0.0f && mountainCol[73] < 0.0f, "Tunnel interior at Y=72..73 must be carved to air");

        // Test end-to-end StagedChunkGenerator with SplineRoadEngine attached
        ws.withSplineRoadEngine(roadEngine);
        StagedChunkGenerator<String> chunkGen = new StagedChunkGenerator<>(
            ws,
            new ConstantMaterialProvider<>("minecraft:grass_block"),
            List.of(),
            "minecraft:air",
            "minecraft:stone",
            "minecraft:stone_bricks",
            "minecraft:cobblestone"
        );
        MapVoxelSpace chunkSpace = new MapVoxelSpace(0, 128);
        chunkGen.generateChunk(1, 0, chunkSpace); // Chunk [16..31, 0..15] along the road spine at Z=0
        assertEquals(
            "minecraft:stone_bricks",
            chunkSpace.getBlock(24, 70, 0),
            "Road spine surface at (24, 70, 0) should be paved with stone_bricks"
        );

        // Verify that on a mountain column above a tunnel (mountain top at Y=95, road tunnel at Y=70),
        // the mountain roof at Y=95 is NOT paved with stone_bricks
        assertEquals(
            "minecraft:grass_block",
            roadEngine.resolveRoadMaterial(32, 95, 0, 0, "minecraft:grass_block", "minecraft:stone_bricks", "minecraft:cobblestone"),
            "Mountain top at Y=95 above a Y=70 tunnel must keep its natural surface material"
        );
        assertEquals(
            "minecraft:stone_bricks",
            roadEngine.resolveRoadMaterial(32, 70, 0, 0, "minecraft:grass_block", "minecraft:stone_bricks", "minecraft:cobblestone"),
            "Tunnel floor at Y=70 must be paved with stone_bricks"
        );
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " | Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertApprox(float expected, float actual, float eps, String message) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError(message + " | Expected: " + expected + ", Actual: " + actual);
        }
    }
}
