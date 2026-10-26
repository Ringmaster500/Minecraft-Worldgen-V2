package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.ConstantDensity;
import com.worldgenv2.density.EllipsoidDensity;
import com.worldgenv2.mask.*;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.props.*;
import com.worldgenv2.props.StaticRotatorProp.CardinalRotation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 11 Verification Test Suite: Block Mask System & Advanced Prop Operators.
 */
public final class Phase11VerificationTest {

    private static int testsPassed = 0;

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Phase 11 assertion failed: " + description);
        }
        System.out.println("[PASS] " + description);
        testsPassed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 11 Verification Test Suite ===");

        testBlockMaskBasics();
        testAdvancedBlockMask();
        testMaskedVoxelSpace();
        testPropOperators();
        testMaskPropIntegration();
        testJsonAstCompilation();

        System.out.println("Phase 11 verification: ALL " + testsPassed + " TESTS PASSED!");
    }

    private static void testBlockMaskBasics() {
        // Always & Never
        BlockMask<String> always = BlockMask.always();
        check(always.canPlace("stone", "air"), "BlockMask.always allows placement");
        check(always.canPlace("water", "dirt"), "BlockMask.always allows water on dirt");

        BlockMask<String> never = BlockMask.never();
        check(!never.canPlace("stone", "air"), "BlockMask.never rejects placement");

        // DontPlaceMask
        DontPlaceMask<String> dontPlace = new DontPlaceMask<>(Set.of("lava", "tnt"));
        check(!dontPlace.canPlace("lava", "dirt"), "DontPlaceMask rejects blacklisted source 'lava'");
        check(!dontPlace.canPlace("tnt", "air"), "DontPlaceMask rejects blacklisted source 'tnt'");
        check(dontPlace.canPlace("stone", "dirt"), "DontPlaceMask allows unlisted source 'stone'");

        // DontReplaceMask
        DontReplaceMask<String> dontReplace = new DontReplaceMask<>(Set.of("bedrock", "obsidian"));
        check(!dontReplace.canPlace("dirt", "bedrock"), "DontReplaceMask prevents overwriting 'bedrock'");
        check(!dontReplace.canPlace("stone", "obsidian"), "DontReplaceMask prevents overwriting 'obsidian'");
        check(dontReplace.canPlace("stone", "dirt"), "DontReplaceMask allows overwriting unlisted 'dirt'");
        check(dontReplace.canPlace("stone", "air"), "DontReplaceMask allows placing into 'air'");
    }

    private static void testAdvancedBlockMask() {
        // Define advanced mask:
        // dontPlace: lava
        // dontReplace: bedrock, water
        // advanced rule: 'bridge_wood' canReplace air, water
        AdvancedBlockMask<String> mask = new AdvancedBlockMask<>(
            Set.of("lava")::contains,
            Set.of("bedrock", "water")::contains,
            List.of(
                new AdvancedBlockMask.Rule<>(
                    SourceMaterial.of("bridge_wood"),
                    DestinationMaterial.of("air", "water")
                )
            )
        );

        // 1. Blacklisted source
        check(!mask.canPlace("lava", "air"), "AdvancedBlockMask: 'lava' in dontPlace is rejected on air");
        check(!mask.canPlace("lava", "water"), "AdvancedBlockMask: 'lava' in dontPlace is rejected on water");

        // 2. Default dontReplace
        check(!mask.canPlace("stone", "bedrock"), "AdvancedBlockMask: default dontReplace protects bedrock");
        check(!mask.canPlace("stone", "water"), "AdvancedBlockMask: default dontReplace protects water against stone");
        check(mask.canPlace("stone", "dirt"), "AdvancedBlockMask: stone can replace dirt (not in dontReplace)");

        // 3. Rule override: 'bridge_wood' CAN replace water
        check(mask.canPlace("bridge_wood", "water"), "AdvancedBlockMask rule override: bridge_wood CAN replace water");
        check(mask.canPlace("bridge_wood", "air"), "AdvancedBlockMask rule override: bridge_wood CAN replace air");
        check(!mask.canPlace("bridge_wood", "bedrock"), "AdvancedBlockMask rule override: bridge_wood cannot replace bedrock");
    }

    private static void testMaskedVoxelSpace() {
        MockVoxelSpace space = new MockVoxelSpace(-64, 320);
        space.setBlock(10, 60, 10, "bedrock");
        space.setBlock(11, 60, 10, "dirt");

        BlockMask<String> mask = new DontReplaceMask<>(Set.of("bedrock"));
        MaskedVoxelSpace<String> maskedSpace = new MaskedVoxelSpace<>(space, mask);

        // Attempt write over bedrock -> should be blocked
        maskedSpace.setBlock(10, 60, 10, "gold_block");
        check("bedrock".equals(space.getBlock(10, 60, 10)), "MaskedVoxelSpace blocked write to protected bedrock");

        // Attempt write over dirt -> should succeed
        maskedSpace.setBlock(11, 60, 10, "gold_block");
        check("gold_block".equals(space.getBlock(11, 60, 10)), "MaskedVoxelSpace allowed write to unshielded dirt");
    }

    private static void testPropOperators() {
        // 1. OffsetProp
        MockVoxelSpace space1 = new MockVoxelSpace(-64, 320);
        BlockProp<String> baseProp = new BlockProp<>("torch", 1, false);
        OffsetProp<String> offsetProp = new OffsetProp<>(baseProp, 3, 5, -2);
        Prop.Context<String> ctx1 = new Prop.Context<>(10, 20, 30, 0, 1, 0, space1, 12345L);
        boolean gen1 = offsetProp.generate(ctx1);
        check(gen1, "OffsetProp generates successfully");
        check("torch".equals(space1.getBlock(13, 25, 28)), "OffsetProp placed block at translated coordinates (13, 25, 28)");

        // 2. UnionProp
        MockVoxelSpace space2 = new MockVoxelSpace(-64, 320);
        BlockProp<String> propA = new BlockProp<>("oak_log", 2, false);
        OffsetProp<String> propB = new OffsetProp<>(new BlockProp<>("oak_leaves", 1, false), 0, 2, 0);
        UnionProp<String> union = new UnionProp<>(propA, propB);
        Prop.Context<String> ctx2 = new Prop.Context<>(0, 10, 0, 0, 1, 0, space2, 54321L);
        boolean gen2 = union.generate(ctx2);
        check(gen2, "UnionProp generates successfully");
        check("oak_log".equals(space2.getBlock(0, 10, 0)), "UnionProp placed first child log (0, 10, 0)");
        check("oak_log".equals(space2.getBlock(0, 11, 0)), "UnionProp placed second segment log (0, 11, 0)");
        check("oak_leaves".equals(space2.getBlock(0, 12, 0)), "UnionProp placed canopy leaf (0, 12, 0)");

        // 3. QueueProp
        MockVoxelSpace space3 = new MockVoxelSpace(-64, 320);
        space3.setBlock(5, 10, 5, "stone"); // target location is solid
        BlockProp<String> failProp = new BlockProp<>("flower", 1, true); // requires air target -> fails
        BlockProp<String> fallbackProp = new BlockProp<>("moss", 1, false); // requires no air -> overwrites
        QueueProp<String> queue = new QueueProp<>(failProp, fallbackProp);
        Prop.Context<String> ctx3 = new Prop.Context<>(5, 10, 5, 0, 1, 0, space3, 999L);
        boolean gen3 = queue.generate(ctx3);
        check(gen3, "QueueProp generates successfully using fallback");
        check("moss".equals(space3.getBlock(5, 10, 5)), "QueueProp placed fallback prop block");

        // 4. ManualProp
        MockVoxelSpace space4 = new MockVoxelSpace(-64, 320);
        ManualProp<String> manual = new ManualProp<>(List.of(
            new ManualProp.Entry<>(0, 0, 0, "chest"),
            new ManualProp.Entry<>(0, 1, 0, "lantern"),
            new ManualProp.Entry<>(1, 0, 0, "carpet")
        ));
        Prop.Context<String> ctx4 = new Prop.Context<>(100, 50, 100, 0, 1, 0, space4, 111L);
        boolean gen4 = manual.generate(ctx4);
        check(gen4, "ManualProp generates successfully");
        check("chest".equals(space4.getBlock(100, 50, 100)), "ManualProp placed chest");
        check("lantern".equals(space4.getBlock(100, 51, 100)), "ManualProp placed lantern");
        check("carpet".equals(space4.getBlock(101, 50, 100)), "ManualProp placed carpet");

        // 5. RandomRotatorProp
        MockVoxelSpace space5 = new MockVoxelSpace(-64, 320);
        OffsetProp<String> branch = new OffsetProp<>(new BlockProp<>("wood", 1, false), 2, 0, 0); // extends along +X
        RandomRotatorProp<String> rotator = new RandomRotatorProp<>(branch, true, "test_salt");
        Prop.Context<String> ctx5 = new Prop.Context<>(50, 60, 50, 0, 1, 0, space5, 7777L);
        boolean gen5 = rotator.generate(ctx5);
        check(gen5, "RandomRotatorProp generates successfully");
        // Due to cardinal rotation around (50, 60, 50), the block must be at one of the 4 cardinal offsets:
        // (+2, 0, 0) -> (52, 60, 50), (-2, 0, 0) -> (48, 60, 50), (0, 0, +2) -> (50, 60, 52), (0, 0, -2) -> (50, 60, 48)
        boolean placedCardinal = "wood".equals(space5.getBlock(52, 60, 50))
            || "wood".equals(space5.getBlock(48, 60, 50))
            || "wood".equals(space5.getBlock(50, 60, 52))
            || "wood".equals(space5.getBlock(50, 60, 48));
        check(placedCardinal, "RandomRotatorProp placed block at valid cardinal rotation");

        // 6. DensityProp
        MockVoxelSpace space6 = new MockVoxelSpace(-64, 320);
        // Ellipsoid density centered at (0, 0, 0) with radius (2, 2, 2)
        EllipsoidDensity sphereDensity = new EllipsoidDensity(2.0f, 2.0f, 2.0f);
        ConstantMaterialProvider<String> stoneProvider = new ConstantMaterialProvider<>("granite");
        DensityProp<String> densityProp = new DensityProp<>(sphereDensity, stoneProvider, 2, 2, 2, 0.0f);
        Prop.Context<String> ctx6 = new Prop.Context<>(0, 0, 0, 0, 1, 0, space6, 42L);
        boolean gen6 = densityProp.generate(ctx6);
        check(gen6, "DensityProp generates successfully");
        check("granite".equals(space6.getBlock(0, 0, 0)), "DensityProp placed core stone at center (0, 0, 0)");
        check("granite".equals(space6.getBlock(1, 0, 0)), "DensityProp placed stone inside radius (1, 0, 0)");
        check(space6.isAir(3, 0, 0), "DensityProp left space outside sphere bounds untouched as air (3, 0, 0)");
    }

    private static void testMaskPropIntegration() {
        MockVoxelSpace space = new MockVoxelSpace(-64, 320);
        space.setBlock(20, 69, 20, "grass_block"); // ground
        space.setBlock(20, 70, 20, "ancient_debris"); // protected block

        // Block mask protecting ancient_debris
        DontReplaceMask<String> mask = new DontReplaceMask<>(Set.of("ancient_debris"));
        BlockProp<String> child = new BlockProp<>("cobblestone", 1, false);
        MaskProp<String> maskProp = new MaskProp<>(
            Set.of("grass_block")::contains,
            0.0f,
            45.0f,
            mask,
            child
        );

        Prop.Context<String> ctx = new Prop.Context<>(20, 70, 20, 0, 1, 0, space, 123L);
        boolean gen = maskProp.generate(ctx);
        check(gen, "MaskProp passed ground substrate filter");
        check("ancient_debris".equals(space.getBlock(20, 70, 20)), "MaskProp prevented child from overwriting protected ancient_debris");

        // CuboidProp with BlockMask
        CuboidProp<String> cuboid = new CuboidProp<>("sandstone", 1, 1, 1, 0, false, mask);
        cuboid.generate(ctx);
        check("ancient_debris".equals(space.getBlock(20, 70, 20)), "CuboidProp respected block mask protecting ancient_debris");
    }

    private static void testJsonAstCompilation() {
        JsonAstCompiler compiler = new JsonAstCompiler();

        // 1. Compile DontPlaceMask
        String dpJson = """
            {
                "type": "DontPlaceMask",
                "dontPlace": ["minecraft:lava", "minecraft:fire"]
            }
            """;
        BlockMask<String> dpMask = compiler.compileBlockMaskJson(dpJson, 1L, s -> s);
        check(!dpMask.canPlace("minecraft:lava", "minecraft:air"), "Compiled DontPlaceMask blocks lava");
        check(dpMask.canPlace("minecraft:water", "minecraft:air"), "Compiled DontPlaceMask allows water");

        // 2. Compile AdvancedBlockMask
        String advJson = """
            {
                "type": "AdvancedBlockMask",
                "dontPlace": ["minecraft:lava"],
                "dontReplace": ["minecraft:water"],
                "advanced": [
                    {
                        "source": ["minecraft:lily_pad"],
                        "canReplace": ["minecraft:water"]
                    }
                ]
            }
            """;
        BlockMask<String> advMask = compiler.compileBlockMaskJson(advJson, 1L, s -> s);
        check(!advMask.canPlace("minecraft:stone", "minecraft:water"), "Compiled AdvancedBlockMask blocks stone on water");
        check(advMask.canPlace("minecraft:lily_pad", "minecraft:water"), "Compiled AdvancedBlockMask allows lily_pad on water via advanced rule");

        // 3. Compile OffsetProp
        String offsetJson = """
            {
                "type": "Offset",
                "offsetX": 2,
                "offsetY": 4,
                "offsetZ": 6,
                "child": {
                    "type": "BlockProp",
                    "block": "minecraft:lantern",
                    "requireAirTarget": false
                }
            }
            """;
        Prop<String> compiledOffset = compiler.compilePropJson(offsetJson, 100L, s -> s);
        MockVoxelSpace spaceA = new MockVoxelSpace(-64, 320);
        compiledOffset.generate(new Prop.Context<>(10, 10, 10, 0, 1, 0, spaceA, 100L));
        check("minecraft:lantern".equals(spaceA.getBlock(12, 14, 16)), "Compiled OffsetProp placed lantern at (12, 14, 16)");

        // 4. Compile ManualProp
        String manualJson = """
            {
                "type": "Manual",
                "blocks": [
                    { "x": 0, "y": 0, "z": 0, "block": "minecraft:anvil" },
                    { "x": 0, "y": 1, "z": 0, "block": "minecraft:flower_pot" }
                ]
            }
            """;
        Prop<String> compiledManual = compiler.compilePropJson(manualJson, 200L, s -> s);
        MockVoxelSpace spaceB = new MockVoxelSpace(-64, 320);
        compiledManual.generate(new Prop.Context<>(0, 64, 0, 0, 1, 0, spaceB, 200L));
        check("minecraft:anvil".equals(spaceB.getBlock(0, 64, 0)), "Compiled ManualProp placed anvil");
        check("minecraft:flower_pot".equals(spaceB.getBlock(0, 65, 0)), "Compiled ManualProp placed flower_pot");

        // 5. Compile DensityProp
        String densityPropJson = """
            {
                "type": "DensityProp",
                "threshold": 0.0,
                "radius": 1,
                "density": {
                    "type": "ConstantDensity",
                    "value": 1.0
                },
                "material": {
                    "type": "ConstantMaterialProvider",
                    "material": "minecraft:gold_block"
                }
            }
            """;
        Prop<String> compiledDensityProp = compiler.compilePropJson(densityPropJson, 300L, s -> s);
        MockVoxelSpace spaceC = new MockVoxelSpace(-64, 320);
        compiledDensityProp.generate(new Prop.Context<>(5, 70, 5, 0, 1, 0, spaceC, 300L));
        check("minecraft:gold_block".equals(spaceC.getBlock(5, 70, 5)), "Compiled DensityProp placed gold_block at center");
        check("minecraft:gold_block".equals(spaceC.getBlock(6, 70, 5)), "Compiled DensityProp placed gold_block at radius 1");
    }

    private static final class MockVoxelSpace implements VoxelSpace<String> {
        private final int minY;
        private final int maxY;
        private final Map<Long, String> blocks = new HashMap<>();

        MockVoxelSpace(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        private static long posKey(int x, int y, int z) {
            return (((long) x & 0x3FFFFFF) << 38) | (((long) y & 0xFFF) << 26) | ((long) z & 0x3FFFFFF);
        }

        @Override
        public String getBlock(int x, int y, int z) {
            return blocks.get(posKey(x, y, z));
        }

        @Override
        public void setBlock(int x, int y, int z, String block) {
            blocks.put(posKey(x, y, z), block);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return getBlock(x, y, z) == null;
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
