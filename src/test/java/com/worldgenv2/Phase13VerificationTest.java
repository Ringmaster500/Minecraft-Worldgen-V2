package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.ConstantDensity;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.engine.chunkgenerator.ChunkDependencyEnvelope;
import com.worldgenv2.engine.chunkgenerator.ImmutableChunkSnapshot;
import com.worldgenv2.engine.chunkgenerator.MultiStageVoxelSpace;
import com.worldgenv2.engine.chunkgenerator.StageContextCache;
import com.worldgenv2.engine.chunkgenerator.StagedChunkGenerator;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.props.BlockProp;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.runtime.ContextPrecision;
import com.worldgenv2.props.runtime.PropRuntime;
import com.worldgenv2.props.runtime.PropRuntimePipeline;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 13 Verification Test Suite: Prop Runtime Context Precision & Dynamic Chunk Bounds.
 */
public final class Phase13VerificationTest {

    private static int testsPassed = 0;

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Phase 13 assertion failed: " + description);
        }
        System.out.println("[PASS] " + description);
        testsPassed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 13 Verification Test Suite: Prop Runtime & Context Precision ===");

        testContextPrecisionModes();
        testChunkDependencyEnvelopeCalculations();
        testImmutableChunkSnapshotIntegrity();
        testStageContextCacheMultiStageAndEviction();
        testMultiStageVoxelSpaceNeighborRead();
        testPropRuntimePipelineStagingAndSequencing();
        testZeroCascadingChunkGeneration();
        testJsonAstCompilationOfPropRuntime();

        System.out.println("Phase 13 verification: ALL " + testsPassed + " TESTS PASSED!");
    }

    private static void testContextPrecisionModes() {
        System.out.println("--- Testing ContextPrecision Modes ---");

        Prop<String> mockProp = new BlockProp<>("stone", 1, false);

        // 1. BIOME Precision Mode
        PropRuntime<String> biomeRuntime = new PropRuntime<>(
            1, ContextPrecision.BIOME, mockProp, 10, 5, 25
        );
        check(biomeRuntime.getMaxReach() == 25, "Biome precision maxReach is max(10, 5, 25) = 25");
        check(biomeRuntime.computeContextReach(0, 0, 12345L) == 25, "BIOME mode reach is uniform regardless of chunk");
        check(biomeRuntime.computeContextReach(100, -50, 12345L) == 25, "BIOME mode reach is uniform at distant coordinates");

        // 2. CHUNK Precision Mode with presence filter
        PropRuntime<String> chunkRuntime = new PropRuntime<>(
            1, ContextPrecision.CHUNK, mockProp, 8, 16, 4,
            (cx, cz) -> (cx == 0 && cz == 0) // only present at chunk (0, 0)
        );
        check(chunkRuntime.getMaxReach() == 16, "Chunk runtime maxReach is 16");
        check(chunkRuntime.computeContextReach(0, 0, 12345L) == 16, "CHUNK mode returns maxReach when chunk contains props");
        check(chunkRuntime.computeContextReach(1, 0, 12345L) == 0, "CHUNK mode returns 0 reach when chunk has no props");
        check(chunkRuntime.computeContextReach(0, 1, 12345L) == 0, "CHUNK mode returns 0 reach for unpopulated chunk");

        // 3. PROP Precision Mode
        PropRuntime<String> propRuntime = new PropRuntime<>(
            2, ContextPrecision.PROP, mockProp, 0, 0, 32,
            (cx, cz) -> (cx == 5 && cz == 5)
        );
        check(propRuntime.computeContextReach(5, 5, 12345L) == 32, "PROP mode returns exact prefab radius 32 when present");
        check(propRuntime.computeContextReach(0, 0, 12345L) == 0, "PROP mode returns 0 reach when not present in chunk");

        // 4. Default pseudo-random presence check
        PropRuntime<String> defaultFilterRuntime = new PropRuntime<>(
            1, ContextPrecision.CHUNK, mockProp, 10, 10, 10
        );
        boolean p1 = defaultFilterRuntime.containsPropsInChunk(12, 34, 9999L);
        boolean p2 = defaultFilterRuntime.containsPropsInChunk(12, 34, 9999L);
        check(p1 == p2, "Default chunk presence filter is deterministic for identical seed and chunk coords");
    }

    private static void testChunkDependencyEnvelopeCalculations() {
        System.out.println("--- Testing ChunkDependencyEnvelope Calculations ---");

        // Reach 0 -> radius 0 -> single chunk (0, 0)
        ChunkDependencyEnvelope env0 = new ChunkDependencyEnvelope(0, 0, 0);
        check(env0.haloChunkRadius() == 0, "Reach 0 gives chunk radius 0");
        check(env0.minChunkX() == 0 && env0.maxChunkX() == 0, "Reach 0 bounds X is [0, 0]");
        check(env0.minChunkZ() == 0 && env0.maxChunkZ() == 0, "Reach 0 bounds Z is [0, 0]");
        check(env0.containsChunk(0, 0), "Envelope contains chunk (0, 0)");
        check(!env0.containsChunk(1, 0), "Envelope does not contain neighbor chunk (1, 0)");

        // Reach 16 -> radius 1 -> 3x3 chunks
        ChunkDependencyEnvelope env16 = new ChunkDependencyEnvelope(5, 10, 16);
        check(env16.haloChunkRadius() == 1, "Reach 16 gives chunk radius ceil(16/16) = 1");
        check(env16.minChunkX() == 4 && env16.maxChunkX() == 6, "Min/max chunk X is [4, 6]");
        check(env16.minChunkZ() == 9 && env16.maxChunkZ() == 11, "Min/max chunk Z is [9, 11]");
        check(env16.containsChunk(4, 9), "Envelope contains corner chunk (4, 9)");
        check(env16.containsChunk(6, 11), "Envelope contains opposite corner chunk (6, 11)");
        check(!env16.containsChunk(3, 10), "Envelope does not contain chunk (3, 10)");

        // Reach 48 -> radius 3 -> 7x7 chunks
        ChunkDependencyEnvelope env48 = new ChunkDependencyEnvelope(0, 0, 48);
        check(env48.haloChunkRadius() == 3, "Reach 48 gives chunk radius ceil(48/16) = 3");
        check(env48.minChunkX() == -3 && env48.maxChunkX() == 3, "Min/max chunk X is [-3, 3]");

        // Union of two envelopes
        ChunkDependencyEnvelope unionEnv = env0.union(env16);
        check(unionEnv.containsChunk(0, 0) && unionEnv.containsChunk(5, 10), "Union contains both primary chunks");
    }

    private static void testImmutableChunkSnapshotIntegrity() {
        System.out.println("--- Testing ImmutableChunkSnapshot Integrity ---");

        TestChunkVoxelSpace space = new TestChunkVoxelSpace(0, 0, -64, 320, "air");
        space.setBlock(5, 64, 5, "stone");
        space.setBlock(10, 70, 12, "dirt");

        ImmutableChunkSnapshot<String> snapshot = ImmutableChunkSnapshot.capture(0, 0, space, "air");
        check(snapshot.getChunkX() == 0 && snapshot.getChunkZ() == 0, "Snapshot preserves chunk coords (0, 0)");
        check("stone".equals(snapshot.getBlock(5, 64, 5)), "Snapshot contains stone at (5, 64, 5)");
        check("dirt".equals(snapshot.getBlock(10, 70, 12)), "Snapshot contains dirt at (10, 70, 12)");
        check("air".equals(snapshot.getBlock(0, 64, 0)), "Snapshot contains air at empty coords");
        check(!snapshot.isAir(5, 64, 5), "isAir returns false for solid block");
        check(snapshot.isAir(0, 64, 0), "isAir returns true for air block");

        // Mutate original space
        space.setBlock(5, 64, 5, "lava");
        space.setBlock(0, 64, 0, "diamond");

        check("stone".equals(snapshot.getBlock(5, 64, 5)), "Snapshot remains stone after original space was mutated to lava (Immutability guarantee)");
        check("air".equals(snapshot.getBlock(0, 64, 0)), "Snapshot remains air after original space was mutated to diamond");
    }

    private static void testStageContextCacheMultiStageAndEviction() {
        System.out.println("--- Testing StageContextCache Multi-Stage and Eviction ---");

        StageContextCache<String> cache = new StageContextCache<>(16);

        TestChunkVoxelSpace space0 = new TestChunkVoxelSpace(1, 2, 0, 128, "air");
        space0.setBlock(16 + 2, 64, 32 + 2, "bedrock");
        ImmutableChunkSnapshot<String> snapStage0 = ImmutableChunkSnapshot.capture(1, 2, space0, "air");

        TestChunkVoxelSpace space1 = new TestChunkVoxelSpace(1, 2, 0, 128, "air");
        space1.setBlock(16 + 2, 64, 32 + 2, "bedrock");
        space1.setBlock(16 + 2, 65, 32 + 2, "grass");
        ImmutableChunkSnapshot<String> snapStage1 = ImmutableChunkSnapshot.capture(1, 2, space1, "air");

        cache.putSnapshot(1, 2, 0, snapStage0);
        cache.putSnapshot(1, 2, 1, snapStage1);

        check(cache.hasSnapshot(1, 2, 0), "Cache contains snapshot for (1, 2, stage 0)");
        check(cache.hasSnapshot(1, 2, 1), "Cache contains snapshot for (1, 2, stage 1)");
        check(!cache.hasSnapshot(1, 2, 2), "Cache returns false for ungenerated stage 2");
        check(!cache.hasSnapshot(0, 0, 0), "Cache returns false for uncached chunk (0, 0)");

        ImmutableChunkSnapshot<String> retrieved0 = cache.getSnapshot(1, 2, 0);
        ImmutableChunkSnapshot<String> retrieved1 = cache.getSnapshot(1, 2, 1);
        check("air".equals(retrieved0.getBlock(18, 65, 34)), "Stage 0 snapshot has air at y=65");
        check("grass".equals(retrieved1.getBlock(18, 65, 34)), "Stage 1 snapshot has grass at y=65");

        // Test LRU capacity
        for (int i = 0; i < 30; i++) {
            TestChunkVoxelSpace s = new TestChunkVoxelSpace(i, 0, 0, 64, "air");
            cache.putSnapshot(i, 0, 0, ImmutableChunkSnapshot.capture(i, 0, s, "air"));
        }
        check(cache.size() <= 16, "Cache capacity is capped at maxCapacity 16");
    }

    private static void testMultiStageVoxelSpaceNeighborRead() {
        System.out.println("--- Testing MultiStageVoxelSpace Cross-Chunk Neighbor Reads ---");

        StageContextCache<String> cache = new StageContextCache<>();

        // Neighbor chunk (1, 0) generated at Stage 0 (stone terrain)
        TestChunkVoxelSpace neighborSpace = new TestChunkVoxelSpace(1, 0, 0, 128, "air");
        neighborSpace.setBlock(16, 60, 5, "granite"); // exactly at border of neighbor chunk (x=16)
        ImmutableChunkSnapshot<String> neighborSnap = ImmutableChunkSnapshot.capture(1, 0, neighborSpace, "air");
        cache.putSnapshot(1, 0, 0, neighborSnap);

        // Current primary chunk (0, 0) executing Stage 1
        TestChunkVoxelSpace primarySpace = new TestChunkVoxelSpace(0, 0, 0, 128, "air");
        primarySpace.setBlock(15, 60, 5, "dirt"); // inside chunk (0, 0) at x=15

        MultiStageVoxelSpace<String> stageSpace = new MultiStageVoxelSpace<>(
            0, 0, 1, primarySpace, cache, "air"
        );

        // 1. Local reads pass through to primarySpace
        check("dirt".equals(stageSpace.getBlock(15, 60, 5)), "Local read at (15, 60, 5) returns dirt from primary space");

        // 2. Cross-chunk reads route seamlessly to neighbor chunk (1, 0) Stage 0 snapshot
        check("granite".equals(stageSpace.getBlock(16, 60, 5)), "Cross-boundary read at x=16 fetches granite from neighbor chunk Stage 0 snapshot!");
        check(stageSpace.getBlock(16, 60, 5) != null, "Neighbor block is non-null");

        // 3. Uncached neighbor reads gracefully return air without throwing or blocking
        check("air".equals(stageSpace.getBlock(-1, 60, 5)), "Uncached neighbor read at x=-1 returns default air block");

        // 4. Out-of-bounds write safety: writes to neighbor chunks are strictly prevented
        stageSpace.setBlock(16, 60, 5, "obsidian");
        check("granite".equals(neighborSnap.getBlock(16, 60, 5)), "Immutable neighbor snapshot was not altered by out-of-bounds write attempt");
    }

    private static void testPropRuntimePipelineStagingAndSequencing() {
        System.out.println("--- Testing PropRuntimePipeline Staging and Sequencing ---");

        StageContextCache<String> cache = new StageContextCache<>();

        // Stage 1: Grass block placed on solid terrain
        Prop<String> grassProp = (ctx) -> {
            ctx.voxelSpace().setBlock(ctx.worldX(), ctx.worldY(), ctx.worldZ(), "grass_block");
            return true;
        };
        PropRuntime<String> stage1Runtime = new PropRuntime<>(1, ContextPrecision.BIOME, grassProp, 0, 0, 0);

        // Stage 2: Tall flower placed on top of grass block
        Prop<String> flowerProp = (ctx) -> {
            // Only place if block beneath is grass_block
            if ("grass_block".equals(ctx.voxelSpace().getBlock(ctx.worldX(), ctx.worldY() - 1, ctx.worldZ()))) {
                ctx.voxelSpace().setBlock(ctx.worldX(), ctx.worldY(), ctx.worldZ(), "sunflower");
                return true;
            }
            return false;
        };
        PropRuntime<String> stage2Runtime = new PropRuntime<>(2, ContextPrecision.BIOME, flowerProp, 0, 0, 0);

        PropRuntimePipeline<String> pipeline = new PropRuntimePipeline<>(
            List.of(stage1Runtime, stage2Runtime), cache, "air"
        );

        check(pipeline.getStageIndices().equals(List.of(1, 2)), "Pipeline registered ordered stages [1, 2]");

        // Prepare chunk (0, 0) with Stage 0 solid bedrock floor at y=60
        TestChunkVoxelSpace voxelSpace = new TestChunkVoxelSpace(0, 0, 0, 128, "air");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                voxelSpace.setBlock(x, 60, z, "stone");
            }
        }
        // Cache Stage 0 snapshot
        cache.putSnapshot(0, 0, 0, ImmutableChunkSnapshot.capture(0, 0, voxelSpace, "air"));

        // Execute Stage 1
        pipeline.executeStage(1, 0, 0, voxelSpace, 42L, null);

        check("grass_block".equals(voxelSpace.getBlock(0, 61, 0)), "Stage 1 placed grass_block on top of stone at y=61");
        check(cache.hasSnapshot(0, 0, 1), "Stage 1 automatically captured immutable snapshot in context cache");

        // Execute Stage 2
        pipeline.executeStage(2, 0, 0, voxelSpace, 42L, null);

        check("sunflower".equals(voxelSpace.getBlock(0, 62, 0)), "Stage 2 placed sunflower on top of Stage 1 grass_block at y=62");
        check(cache.hasSnapshot(0, 0, 2), "Stage 2 captured immutable snapshot in context cache");
    }

    private static void testZeroCascadingChunkGeneration() {
        System.out.println("--- Testing Zero Cascading Chunk Generation Guarantee ---");

        StageContextCache<String> cache = new StageContextCache<>();

        // Prop with wide reach (32 blocks, cross-chunk envelope +/- 2 chunks)
        Prop<String> wideProp = (ctx) -> {
            // Read cross-chunk block 20 blocks east (worldX + 20)
            String neighborVal = ctx.voxelSpace().getBlock(ctx.worldX() + 20, 60, ctx.worldZ());
            if ("granite".equals(neighborVal)) {
                ctx.voxelSpace().setBlock(ctx.worldX(), ctx.worldY(), ctx.worldZ(), "gold_block");
                return true;
            }
            return false;
        };

        PropRuntime<String> wideRuntime = new PropRuntime<>(
            1, ContextPrecision.PROP, wideProp, 0, 0, 32,
            (cx, cz) -> true
        );

        PropRuntimePipeline<String> pipeline = new PropRuntimePipeline<>(
            List.of(wideRuntime), cache, "air"
        );

        // Compute envelope
        ChunkDependencyEnvelope envelope = pipeline.computeStageEnvelope(0, 0, 1, 12345L);
        check(envelope.haloChunkRadius() == 2, "Radius 32 blocks yields chunk radius 2 (bounds [-2, 2])");

        // Seed neighbor chunk (1, 0) with Stage 0 stone
        TestChunkVoxelSpace neighborSpace = new TestChunkVoxelSpace(1, 0, 0, 128, "air");
        neighborSpace.setBlock(25, 60, 5, "granite"); // at x=25 (which is x=9 in chunk (1, 0))
        cache.putSnapshot(1, 0, 0, ImmutableChunkSnapshot.capture(1, 0, neighborSpace, "air"));

        TestChunkVoxelSpace primarySpace = new TestChunkVoxelSpace(0, 0, 0, 128, "air");
        primarySpace.setBlock(5, 60, 5, "stone"); // top surface at y=60

        // Execute stage 1 for primary chunk (0, 0).
        // Since (5 + 20, 60, 5) = (25, 60, 5), it reads the cached stage 0 snapshot from (1, 0)
        pipeline.executeStage(1, 0, 0, primarySpace, 999L, null);

        check("gold_block".equals(primarySpace.getBlock(5, 61, 5)), "Wide prop read Stage 0 neighbor block and placed gold_block at (5, 61, 5)");
        // Crucial guarantee: Chunk (1, 0) was NOT generated for Stage 1!
        check(!cache.hasSnapshot(1, 0, 1), "Zero Cascading Guarantee: Neighbor chunk (1, 0) was NOT triggered to generate Stage 1!");
    }

    private static void testJsonAstCompilationOfPropRuntime() {
        System.out.println("--- Testing JSON AST Compilation of PropRuntime ---");

        JsonAstCompiler compiler = new JsonAstCompiler();

        // 1. Full explicit PropRuntime JSON
        String json1 = """
            {
                "type": "PropRuntime",
                "stage": 2,
                "precision": "PROP",
                "scannerReach": 14,
                "patternOffset": 8,
                "prefabRadius": 28,
                "prop": {
                    "type": "BlockProp",
                    "block": "minecraft:moss_block",
                    "height": 2
                }
            }
            """;

        PropRuntime<String> runtime1 = compiler.compilePropRuntimeJson(json1, 555L, s -> s);
        check(runtime1.getRuntimeIndex() == 2, "Parsed runtime index is 2");
        check(runtime1.getPrecision() == ContextPrecision.PROP, "Parsed precision is ContextPrecision.PROP");
        check(runtime1.getScannerReach() == 14, "Parsed scannerReach is 14");
        check(runtime1.getPatternOffset() == 8, "Parsed patternOffset is 8");
        check(runtime1.getPrefabRadius() == 28, "Parsed prefabRadius is 28");
        check(runtime1.getMaxReach() == 28, "maxReach is max(14, 8, 28) = 28");

        // 2. Shorthand "Runtime" alias with CHUNK precision
        String json2 = """
            {
                "type": "Runtime",
                "stage": 3,
                "precision": "CHUNK",
                "radius": 18,
                "prop": {
                    "type": "BlockProp",
                    "block": "minecraft:oak_sapling"
                }
            }
            """;

        PropRuntime<String> runtime2 = compiler.compilePropRuntimeJson(json2, 777L, s -> s);
        check(runtime2.getRuntimeIndex() == 3, "Parsed runtime index is 3");
        check(runtime2.getPrecision() == ContextPrecision.CHUNK, "Parsed precision is ContextPrecision.CHUNK");
        check(runtime2.getMaxReach() == 18, "Parsed radius 18 converted to maxReach 18");

        // 3. Raw BlockProp fallback wrapped into default PropRuntime stage 1
        String json3 = """
            {
                "type": "BlockProp",
                "block": "minecraft:dandelion"
            }
            """;

        PropRuntime<String> runtime3 = compiler.compilePropRuntimeJson(json3, 888L, s -> s);
        check(runtime3.getRuntimeIndex() == 1, "Fallback raw prop defaults to stage 1");
        check(runtime3.getPrecision() == ContextPrecision.BIOME, "Fallback raw prop defaults to BIOME precision");
    }

    /**
     * Minimal in-memory chunk voxel space implementation for testing.
     */
    private static final class TestChunkVoxelSpace implements VoxelSpace<String> {
        private final int chunkX;
        private final int chunkZ;
        private final int minY;
        private final int maxY;
        private final String defaultAir;
        private final Map<Long, String> blocks = new HashMap<>();

        public TestChunkVoxelSpace(int chunkX, int chunkZ, int minY, int maxY, String defaultAir) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.minY = minY;
            this.maxY = maxY;
            this.defaultAir = defaultAir;
        }

        private long key(int x, int y, int z) {
            return (((long) x & 0x3FFFFFF) << 38) | (((long) y & 0xFFF) << 26) | ((long) z & 0x3FFFFFF);
        }

        @Override
        public String getBlock(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), defaultAir);
        }

        @Override
        public void setBlock(int x, int y, int z, String block) {
            if (defaultAir.equals(block)) {
                blocks.remove(key(x, y, z));
            } else {
                blocks.put(key(x, y, z), block);
            }
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return defaultAir.equals(getBlock(x, y, z));
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
}
