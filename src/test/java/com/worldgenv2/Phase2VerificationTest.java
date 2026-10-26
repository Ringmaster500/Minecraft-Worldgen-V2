package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.density.*;
import com.worldgenv2.engine.chunkgenerator.StagedChunkGenerator;
import com.worldgenv2.material.*;
import com.worldgenv2.material.patterns.*;
import com.worldgenv2.prefab.*;
import com.worldgenv2.props.*;
import com.worldgenv2.worldstructure.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * End-to-end verification suite for Phase 1 (complete) and Phase 2 (Material Providers,
 * Pattern Matchers, Composable Props, and Prefab & Socket Blueprint Engine).
 */
public final class Phase2VerificationTest {

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
        testPhase1TransformNodes();
        testMaterialProvidersAndPatterns();
        testComposableProps();
        testPrefabEngineAndSockets();
        testFullPipelineIntegration();
        System.out.println("ALL PHASE 1 & PHASE 2 VERIFICATION TESTS PASSED!");
    }

    private static void testPhase1TransformNodes() {
        DensityFunction base = new VerticalGradientDensity(64.0f, 32.0f);
        DensityFunction chain = base
            .offset(0.0f, 4.0f, 0.0f)
            .scale(1.0f, 0.5f, 1.0f)
            .abs()
            .invert()
            .normalize(-2.0f, 0.0f)
            .clamp(-0.8f, 0.8f);

        int minY = 0;
        int maxY = 128;
        float[] col = new float[maxY - minY];
        chain.computeColumn(10.0f, 20.0f, minY, maxY, col);

        for (int i = 0; i < col.length; i++) {
            float expected = chain.compute(10.0f, minY + i, 20.0f);
            assertApprox(expected, col[i], 1e-5f, "Transform chain column mismatch at Y=" + (minY + i));
            assertTrue(col[i] >= -0.8f && col[i] <= 0.8f, "ClampDensity bounds violated");
        }
    }

    private static void testMaterialProvidersAndPatterns() {
        MapVoxelSpace space = new MapVoxelSpace(0, 128);

        Pattern<String> gentleSurface = new SurfacePattern<String>()
            .and(new SlopePattern<>(0.0f, 35.0f))
            .and(new SpaceCheckPattern<>(3));
        Pattern<String> cliffWall = new WallPattern<>(60.0f, 110.0f);
        Pattern<String> caveCeiling = new CeilingPattern<>(120.0f);
        Pattern<String> deepBand = new DepthPattern<String>(4, 100)
            .and(CuboidPattern.elevationBand(0, 40));

        WeightedMaterialProvider<String> deepMix = new WeightedMaterialProvider<>(List.of(
            WeightedMaterialProvider.Entry.ofMaterial("minecraft:deepslate", 80.0f),
            WeightedMaterialProvider.Entry.ofMaterial("minecraft:tuff", 20.0f)
        ));

        QueueMaterialProvider<String> queue = new QueueMaterialProvider<>(
            List.of(
                new QueueMaterialProvider.ConditionalRule<>(caveCeiling, new ConstantMaterialProvider<>("minecraft:glow_lichen")),
                new QueueMaterialProvider.ConditionalRule<>(cliffWall, new ConstantMaterialProvider<>("minecraft:basalt")),
                new QueueMaterialProvider.ConditionalRule<>(gentleSurface, new ConstantMaterialProvider<>("minecraft:grass_block")),
                new QueueMaterialProvider.ConditionalRule<>(deepBand, deepMix)
            ),
            new ConstantMaterialProvider<>("minecraft:stone")
        );

        assertEquals("minecraft:grass_block", queue.getMaterial(0, 64, 0, 0, 10, 12.0f, space), "Gentle surface should be grass");
        assertEquals("minecraft:stone", queue.getMaterial(0, 64, 0, 0, 1, 12.0f, space), "Low clearance should skip grass");
        assertEquals("minecraft:basalt", queue.getMaterial(0, 64, 0, 1, 10, 75.0f, space), "Steep slope should match WallPattern");
        assertEquals("minecraft:glow_lichen", queue.getMaterial(0, 50, 0, 2, 5, 150.0f, space), "Downward normal should match CeilingPattern");

        String deepMat = queue.getMaterial(5, 20, 5, 10, 8, 0.0f, space);
        assertTrue(
            "minecraft:deepslate".equals(deepMat) || "minecraft:tuff".equals(deepMat),
            "Deep band should resolve to weighted deepslate/tuff palette"
        );
    }

    private static void testComposableProps() {
        MapVoxelSpace space = new MapVoxelSpace(0, 128);
        // Build a flat grass floor at Y=63
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                for (int y = 50; y <= 63; y++) {
                    space.setBlock(x, y, z, y == 63 ? "minecraft:grass_block" : "minecraft:dirt");
                }
            }
        }

        // 1. Test MaskProp + DensitySelectorProp + BlockProp
        Prop<String> tallFlora = new MaskProp<>(
            Set.of("minecraft:grass_block"),
            35.0f,
            new DensitySelectorProp<>(
                new ConstantDensity(0.75f),
                0.5f,
                new BlockProp<>("minecraft:fern", 2, true)
            )
        );
        Prop.Context<String> ctx = new Prop.Context<>(0, 64, 0, 0.0f, 1.0f, 0.0f, space, 42L);
        assertTrue(tallFlora.generate(ctx), "Flora prop should place on flat grass");
        assertEquals("minecraft:fern", space.getBlock(0, 64, 0), "Bottom fern block");
        assertEquals("minecraft:fern", space.getBlock(0, 65, 0), "Top fern block");

        // 2. Test OrienterProp tilting a 4-block pillar along a horizontal normal (+X)
        Prop<String> orientedSpire = new OrienterProp<>(new BlockProp<>("minecraft:amethyst_block", 4, false), 1.0f);
        Prop.Context<String> wallCtx = new Prop.Context<>(5, 70, 5, 1.0f, 0.0f, 0.0f, space, 99L);
        assertTrue(orientedSpire.generate(wallCtx), "Oriented spire should generate along +X normal");
        for (int dx = 0; dx < 4; dx++) {
            assertEquals("minecraft:amethyst_block", space.getBlock(5 + dx, 70, 5), "Spire rotated along +X at dx=" + dx);
        }

        // 3. Test PondFillerProp carving a water basin at (0, 64, 5)
        space.setBlock(0, 64, 0, "minecraft:air");
        space.setBlock(0, 65, 0, "minecraft:air");
        PondFillerProp<String> pond = new PondFillerProp<>("minecraft:water", "minecraft:clay", "minecraft:air", 3, 2);
        Prop.Context<String> pondCtx = new Prop.Context<>(0, 64, 0, 0.0f, 1.0f, 0.0f, space, 123L);
        assertTrue(pond.generate(pondCtx), "PondFillerProp should carve enclosed basin");
        assertEquals("minecraft:water", space.getBlock(0, 63, 0), "Pond center should be water");
        assertEquals("minecraft:clay", space.getBlock(0, 61, 0), "Pond bed should be lined with clay");
    }

    private static void testPrefabEngineAndSockets() throws Exception {
        PrefabLoader loader = new PrefabLoader();

        String watchtowerJson = Files.readString(Path.of("src/main/resources/data/worldgenv2/prefabs/watchtower_01.prefab.json"));
        String walkwayJson = Files.readString(Path.of("src/main/resources/data/worldgenv2/prefabs/stone_walkway_01.prefab.json"));

        Prefab watchtower = loader.parseJson(watchtowerJson);
        Prefab walkway = loader.parseJson(walkwayJson);

        assertEquals("worldgenv2:structures/watchtower_01", watchtower.name(), "Prefab name");
        assertEquals(1, watchtower.sockets().size(), "Watchtower socket count");
        assertEquals(1, watchtower.adaptiveFoundations().size(), "Watchtower adaptive foundation count");

        // Create uneven terrain where Z < 0 drops off by 4 blocks to Y=59 while Z >= 0 is at Y=63
        MapVoxelSpace space = new MapVoxelSpace(0, 128);
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                int topY = (z < 0) ? 59 : 63;
                for (int y = 50; y <= topY; y++) {
                    space.setBlock(x, y, z, "minecraft:stone");
                }
            }
        }

        PrefabProp<String> prefabProp = new PrefabProp<>(
            watchtower,
            Function.identity(),
            false,
            true,
            List.of(walkway),
            1
        );

        Prop.Context<String> ctx = new Prop.Context<>(0, 64, 0, 0.0f, 1.0f, 0.0f, space, 777L);
        assertTrue(prefabProp.generate(ctx), "PrefabProp should stamp watchtower and connected walkway");

        // Verify watchtower roof stairs with formatted blockstate properties at anchor (0, 64+3, 0)
        assertEquals(
            "minecraft:oak_stairs[facing=north,half=bottom]",
            space.getBlock(0, 67, 0),
            "Formatted stair block state at watchtower peak"
        );

        // Verify Adaptive Foundation Plinth marched down from Y=63 to Y=60 over the cliff drop-off at Z = -2
        for (int y = 60; y <= 63; y++) {
            assertEquals(
                "minecraft:cobblestone",
                space.getBlock(0, y, -2),
                "Adaptive foundation plinth should fill air gap at Y=" + y
            );
        }

        // Verify connected stone_walkway_01 stamped northward via socket doorway_front (Z = -3, -4, -5)
        assertEquals(
            "minecraft:stone_bricks",
            space.getBlock(0, 64, -3),
            "Connected walkway socket block at Z=-3"
        );
        assertEquals(
            "minecraft:stone_bricks",
            space.getBlock(0, 64, -4),
            "Connected walkway center block at Z=-4"
        );
    }

    private static void testFullPipelineIntegration() {
        MaterialProvider<String> plainsMaterial = new SpaceAndDepthMaterialProvider<>(
            List.of(
                new SpaceAndDepthMaterialProvider.Layer<>("minecraft:grass_block", 1),
                new SpaceAndDepthMaterialProvider.Layer<>("minecraft:dirt", 3)
            ),
            "minecraft:stone",
            "minecraft:stone",
            45.0f,
            3
        );
        TileBiome plains = new TileBiome(
            1,
            "minecraft:plains",
            1.0f,
            1.0f,
            16.0f,
            new VerticalGradientDensity(64.0f, 16.0f),
            plainsMaterial,
            List.of(
                new MaskProp<>(
                    Set.of("minecraft:grass_block"),
                    30.0f,
                    new BlockProp<>("minecraft:short_grass")
                )
            )
        );

        WorldStructure worldStructure = new WorldStructure(List.of(plains), List.of(), 128, 12345L);
        StagedChunkGenerator<String> generator = new StagedChunkGenerator<>(
            worldStructure,
            plainsMaterial,
            List.of(),
            "minecraft:air",
            "minecraft:stone"
        );

        MapVoxelSpace chunkSpace = new MapVoxelSpace(0, 128);
        generator.generateChunk(0, 0, chunkSpace);

        assertEquals("minecraft:grass_block", chunkSpace.getBlock(8, 63, 8), "Surface should be grass_block");
        assertEquals("minecraft:short_grass", chunkSpace.getBlock(8, 64, 8), "PropStage should place short_grass at Y=64");
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
