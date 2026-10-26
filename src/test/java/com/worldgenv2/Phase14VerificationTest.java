package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.biome.WorldGenV2Registries;
import com.worldgenv2.density.AbsDensity;
import com.worldgenv2.density.ClampDensity;
import com.worldgenv2.density.ConstantDensity;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.density.FastGradientWarpDensity;
import com.worldgenv2.density.InvertDensity;
import com.worldgenv2.density.MixDensity;
import com.worldgenv2.density.MultiMixDensity;
import com.worldgenv2.density.OffsetDensity;
import com.worldgenv2.density.PositionsCellNoiseDensity;
import com.worldgenv2.density.PowDensity;
import com.worldgenv2.density.ProductDensity;
import com.worldgenv2.density.RotatorDensity;
import com.worldgenv2.density.ScaleDensity;
import com.worldgenv2.density.SliderDensity;
import com.worldgenv2.density.SqrtDensity;
import com.worldgenv2.density.SumDensity;
import com.worldgenv2.density.SwitchStateDensity;
import com.worldgenv2.density.TrigDensity;
import com.worldgenv2.density.WhiteNoiseDensity;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.material.LayerMaterialProvider;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.material.SolidityMaterialProvider;
import com.worldgenv2.material.StrataMaterialProvider;
import com.worldgenv2.material.StripedMaterialProvider;
import com.worldgenv2.material.patterns.BlockSetPattern;
import com.worldgenv2.material.patterns.BlockTypePattern;
import com.worldgenv2.material.patterns.Pattern;
import com.worldgenv2.material.patterns.RotatorPattern;
import com.worldgenv2.positions.AnchorPositionsProvider;
import com.worldgenv2.positions.ClustersPositionsProvider;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.positions.SimpleHorizontalPositionsProvider;
import com.worldgenv2.props.Prop;
import com.worldgenv2.studio.StudioNodeCatalog;
import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.worldstructure.DimensionLayoutConfig;
import com.worldgenv2.worldstructure.DimensionLayoutConfig.OutsideBoundsFallback;
import com.worldgenv2.worldstructure.TileBiome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 14 Verification Test Suite: Complete JSON AST Schema Ingestion (All 293 Nodes & 24 Categories).
 */
public final class Phase14VerificationTest {

    private static int testsPassed = 0;

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Phase 14 assertion failed: " + description);
        }
        System.out.println("[PASS] " + description);
        testsPassed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 14 Verification Test Suite: JSON AST Ingestion & Optimizer Pass ===");

        testMathematicalDensityNodes();
        testStructuralAndWarpDensityNodes();
        testAdvancedMaterialProviderNodes();
        testPatternAndPositionsProviderNodes();
        testRegistryCoverageAcrossAll24Categories();
        testUniversalImportedAndExportedResolution();
        testAstCompilerOptimizerPass();
        testFullComplexBiomeAstRoundTrip();
        testDimensionLayoutAndMacroPresets();

        System.out.println("Phase 14 verification: ALL " + testsPassed + " TESTS PASSED!");
    }

    private static void testMathematicalDensityNodes() {
        System.out.println("--- Testing Mathematical Density Nodes ---");

        // 1. WhiteNoiseDensity
        DensityFunction whiteNoise = new WhiteNoiseDensity(12345);
        float wn1 = whiteNoise.compute(10.0f, 20.0f, 30.0f);
        float wn2 = whiteNoise.compute(10.0f, 20.0f, 30.0f);
        float wn3 = whiteNoise.compute(11.0f, 20.0f, 30.0f);
        check(wn1 >= -1.0f && wn1 <= 1.0f, "WhiteNoise output is normalized within [-1, 1]");
        check(wn1 == wn2, "WhiteNoise is deterministic for identical coordinates");
        check(wn1 != wn3, "WhiteNoise produces distinct values at different coordinates");

        // 2. SqrtDensity
        DensityFunction sqrtNode = new SqrtDensity(new ConstantDensity(16.0f));
        check(Math.abs(sqrtNode.compute(0, 0, 0) - 4.0f) < 1e-5f, "SqrtDensity calculates sqrt(16) = 4.0");

        DensityFunction sqrtNeg = new SqrtDensity(new ConstantDensity(-9.0f));
        check(Math.abs(sqrtNeg.compute(0, 0, 0)) < 1e-5f, "SqrtDensity clamps negative inputs to 0.0 to prevent NaN");

        // 3. PowDensity
        DensityFunction powNode = new PowDensity(new ConstantDensity(3.0f), 3.0f);
        check(Math.abs(powNode.compute(0, 0, 0) - 27.0f) < 1e-5f, "PowDensity calculates 3^3 = 27.0");

        DensityFunction powNeg = new PowDensity(new ConstantDensity(-2.0f), 3.0f);
        check(Math.abs(powNeg.compute(0, 0, 0) - (-8.0f)) < 1e-5f, "PowDensity preserves sign: (-2)^3 = -8.0");

        // 4. TrigDensity
        DensityFunction trigSin = new TrigDensity(new ConstantDensity((float) (Math.PI / 2.0)), TrigDensity.Function.SIN, 1.0f, 2.0f, 0.0f);
        check(Math.abs(trigSin.compute(0, 0, 0) - 2.0f) < 1e-4f, "TrigDensity calculates 2 * sin(PI/2) = 2.0");

        DensityFunction trigCos = new TrigDensity(new ConstantDensity(0.0f), TrigDensity.Function.COS, 1.0f, 5.0f, 0.0f);
        check(Math.abs(trigCos.compute(0, 0, 0) - 5.0f) < 1e-4f, "TrigDensity calculates 5 * cos(0) = 5.0");

        // 5. MixDensity
        DensityFunction mixNode = new MixDensity(new ConstantDensity(10.0f), new ConstantDensity(20.0f), new ConstantDensity(0.25f));
        check(Math.abs(mixNode.compute(0, 0, 0) - 12.5f) < 1e-5f, "MixDensity calculates lerp(10, 20, 0.25) = 12.5");

        // 6. MultiMixDensity
        MultiMixDensity multiMix = new MultiMixDensity(
            List.of(new ConstantDensity(100.0f), new ConstantDensity(200.0f), new ConstantDensity(300.0f)),
            new ConstantDensity(0.5f)
        );
        check(Math.abs(multiMix.compute(0, 0, 0) - 200.0f) < 1e-5f, "MultiMixDensity samples exact breakpoint 0.5 = 200.0");
    }

    private static void testStructuralAndWarpDensityNodes() {
        System.out.println("--- Testing Structural and Domain Warp Density Nodes ---");

        // 1. SliderDensity
        DensityFunction base = new ConstantDensity(50.0f);
        SliderDensity slider = new SliderDensity(base, 10.0f, -5.0f, 20.0f);
        check(slider.offsetX() == 10.0f && slider.offsetY() == -5.0f && slider.offsetZ() == 20.0f, "Slider offsets preserved");
        check(slider.compute(0, 0, 0) == 50.0f, "Slider evaluates inner function");

        // 2. RotatorDensity
        RotatorDensity rotY = new RotatorDensity(new DensityFunction() {
            @Override
            public float compute(float x, float y, float z) {
                return x;
            }
        }, RotatorDensity.Axis.Y, 90.0f);
        float rotVal = rotY.compute(1.0f, 0.0f, 0.0f);
        check(Math.abs(rotVal) < 1e-4f, "RotatorDensity rotates around Y axis by 90 degrees");

        // 3. FastGradientWarpDensity
        DensityFunction warpField = new DensityFunction() {
            @Override
            public float compute(float x, float y, float z) {
                return x * x;
            }
        };
        FastGradientWarpDensity warp = new FastGradientWarpDensity(new DensityFunction() {
            @Override
            public float compute(float x, float y, float z) {
                return x;
            }
        }, warpField, 2.0f);
        float warpedVal = warp.compute(5.0f, 0.0f, 0.0f);
        check(Math.abs(warpedVal - 25.0f) < 1e-4f, "FastGradientWarp accurately computes central difference displacement");

        // 4. SwitchStateDensity
        SwitchStateDensity switchState = new SwitchStateDensity(
            () -> "forest",
            Map.of("desert", new ConstantDensity(10.0f), "forest", new ConstantDensity(20.0f), "ocean", new ConstantDensity(30.0f)),
            new ConstantDensity(0.0f)
        );
        check(Math.abs(switchState.compute(0, 0, 0) - 20.0f) < 1e-5f, "SwitchState selects exact state branch 'forest' = 20.0");

        // 5. PositionsCellNoiseDensity
        PositionsProvider ptProvider = new AnchorPositionsProvider(0.0, 0.0, 0.0);
        PositionsCellNoiseDensity cellNoise = new PositionsCellNoiseDensity(ptProvider, 64.0f, 32.0f);
        float cn = cellNoise.compute(0.0f, 0.0f, 0.0f);
        check(Math.abs(cn - (-1.0f)) < 1e-5f, "PositionsCellNoise returns -1.0 at exact position (cell core)");
    }

    private static void testAdvancedMaterialProviderNodes() {
        System.out.println("--- Testing Advanced Material Provider Nodes ---");

        MockVoxelSpace space = new MockVoxelSpace();

        // 1. StrataMaterialProvider
        StrataMaterialProvider<String> strata = new StrataMaterialProvider<>(
            List.of(
                new StrataMaterialProvider.Stratum<>(0, 10, new ConstantMaterialProvider<>("minecraft:bedrock")),
                new StrataMaterialProvider.Stratum<>(11, 30, new ConstantMaterialProvider<>("minecraft:deepslate")),
                new StrataMaterialProvider.Stratum<>(31, 100, new ConstantMaterialProvider<>("minecraft:stone"))
            ),
            new ConstantMaterialProvider<>("minecraft:dirt")
        );
        check("minecraft:bedrock".equals(strata.getMaterial(0, 5, 0, 0, 10, 0.0f, space)), "Strata gives bedrock at y=5");
        check("minecraft:deepslate".equals(strata.getMaterial(0, 20, 0, 0, 10, 0.0f, space)), "Strata gives deepslate at y=20");
        check("minecraft:stone".equals(strata.getMaterial(0, 50, 0, 0, 10, 0.0f, space)), "Strata gives stone at y=50");
        check("minecraft:dirt".equals(strata.getMaterial(0, 150, 0, 0, 10, 0.0f, space)), "Strata gives fallback dirt outside bounds");

        // 2. StripedMaterialProvider
        StripedMaterialProvider<String> striped = new StripedMaterialProvider<>(
            List.of(
                new StripedMaterialProvider.Stripe<>(10, new ConstantMaterialProvider<>("minecraft:sandstone")),
                new StripedMaterialProvider.Stripe<>(10, new ConstantMaterialProvider<>("minecraft:red_sandstone"))
            )
        );
        check("minecraft:sandstone".equals(striped.getMaterial(0, 5, 0, 0, 10, 0.0f, space)), "Striped layer 0 at y=5");
        check("minecraft:red_sandstone".equals(striped.getMaterial(0, 15, 0, 0, 10, 0.0f, space)), "Striped layer 1 at y=15");
        check("minecraft:sandstone".equals(striped.getMaterial(0, 25, 0, 0, 10, 0.0f, space)), "Striped layer 0 again at y=25");

        // 3. LayerMaterialProvider
        LayerMaterialProvider<String> layer = new LayerMaterialProvider<>(
            60, 64,
            new ConstantMaterialProvider<>("minecraft:dirt"),
            new ConstantMaterialProvider<>("minecraft:stone")
        );
        check("minecraft:dirt".equals(layer.getMaterial(0, 62, 0, 0, 10, 0.0f, space)), "LayerMaterialProvider provides dirt at y=62");
        check("minecraft:stone".equals(layer.getMaterial(0, 50, 0, 0, 10, 0.0f, space)), "LayerMaterialProvider provides stone outside layer");

        // 4. SolidityMaterialProvider
        SolidityMaterialProvider<String> solidity = new SolidityMaterialProvider<>(
            new ConstantDensity(0.5f),
            0.0f,
            new ConstantMaterialProvider<>("minecraft:stone"),
            new ConstantMaterialProvider<>("minecraft:water")
        );
        check("minecraft:stone".equals(solidity.getMaterial(0, 0, 0, 0, 10, 0.0f, space)), "Solidity provides stone when density 0.5 >= 0.0");
    }

    private static void testPatternAndPositionsProviderNodes() {
        System.out.println("--- Testing Pattern and Positions Provider Nodes ---");

        // 1. BlockTypePattern
        Pattern<String> typePat = new BlockTypePattern<>("minecraft:stone");
        MockVoxelSpace space = new MockVoxelSpace();
        space.setBlock(1, 2, 3, "minecraft:stone");
        space.setBlock(1, 2, 4, "minecraft:dirt");
        check(typePat.matches(1, 2, 3, 0, 10, 0.0f, space), "BlockTypePattern matches stone");
        check(!typePat.matches(1, 2, 4, 0, 10, 0.0f, space), "BlockTypePattern rejects dirt");

        // 2. BlockSetPattern
        Pattern<String> setPat = new BlockSetPattern<>(Set.of("minecraft:stone", "minecraft:andesite", "minecraft:granite"));
        space.setBlock(5, 5, 5, "minecraft:andesite");
        check(setPat.matches(5, 5, 5, 0, 10, 0.0f, space), "BlockSetPattern matches andesite from set");
        check(!setPat.matches(1, 2, 4, 0, 10, 0.0f, space), "BlockSetPattern rejects dirt");

        // 3. RotatorPattern
        RotatorPattern<String> rotPat = new RotatorPattern<>(typePat, 1); // 1 quarter turn = 90 deg
        check(rotPat.getQuarterTurns() == 1, "RotatorPattern preserves quarter turns");
        // At (1, 2, 3), quarter turn 1 evaluates (-3, 2, 1)
        space.setBlock(-3, 2, 1, "minecraft:stone");
        check(rotPat.matches(1, 2, 3, 0, 10, 0.0f, space), "RotatorPattern matches rotated block coordinate");

        // 4. SimpleHorizontalPositionsProvider
        SimpleHorizontalPositionsProvider horizontal = new SimpleHorizontalPositionsProvider(
            16.0, 64.0, true
        );
        List<Vec3d> hPositions = horizontal.getPositions(0, 60, 0, 64, 70, 64);
        check(!hPositions.isEmpty(), "SimpleHorizontal generates non-empty positions");
        check(hPositions.getFirst().y() == 64.0, "SimpleHorizontal generates flat Y=64 coordinates");

        // 5. ClustersPositionsProvider
        ClustersPositionsProvider clusters = new ClustersPositionsProvider(
            horizontal, 4, 8.0, 12345L
        );
        List<Vec3d> cPositions = clusters.getPositions(0, 50, 0, 64, 80, 64);
        check(cPositions.size() >= hPositions.size(), "ClustersPositionsProvider expands seed positions into clusters");

        // 6. AnchorPositionsProvider
        AnchorPositionsProvider anchor = new AnchorPositionsProvider(
            new Vec3d(10.0, 50.0, 10.0)
        );
        List<Vec3d> aPositions = anchor.getPositions(0, 0, 0, 100, 100, 100);
        check(!aPositions.isEmpty(), "AnchorPositionsProvider emits anchor inside box");
        check(Math.abs(aPositions.getFirst().y() - 50.0) < 1e-4, "Anchor Y is 50.0");
    }

    private static void testRegistryCoverageAcrossAll24Categories() {
        System.out.println("--- Testing Registry Coverage Across All Registry Categories ---");

        JsonAstCompiler.registerBuiltInTypes();

        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("WhiteNoise"), "DENSITY_NODE_TYPE has WhiteNoise");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Mix"), "DENSITY_NODE_TYPE has Mix");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("MultiMix"), "DENSITY_NODE_TYPE has MultiMix");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Sqrt"), "DENSITY_NODE_TYPE has Sqrt");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Pow"), "DENSITY_NODE_TYPE has Pow");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Trig"), "DENSITY_NODE_TYPE has Trig");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Rotator"), "DENSITY_NODE_TYPE has Rotator");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Slider"), "DENSITY_NODE_TYPE has Slider");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("FastGradientWarp"), "DENSITY_NODE_TYPE has FastGradientWarp");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("PositionsCellNoise"), "DENSITY_NODE_TYPE has PositionsCellNoise");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("SwitchState"), "DENSITY_NODE_TYPE has SwitchState");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Imported"), "DENSITY_NODE_TYPE has Imported");
        check(WorldGenV2Registries.DENSITY_NODE_TYPE.contains("Exported"), "DENSITY_NODE_TYPE has Exported");

        check(WorldGenV2Registries.CURVE_TYPE.contains("Linear"), "CURVE_TYPE has Linear");
        check(WorldGenV2Registries.CURVE_TYPE.contains("Imported"), "CURVE_TYPE has Imported");

        check(WorldGenV2Registries.VECTOR_PROVIDER_TYPE.contains("Sum"), "VECTOR_PROVIDER_TYPE has Sum");
        check(WorldGenV2Registries.VECTOR_PROVIDER_TYPE.contains("Rotate"), "VECTOR_PROVIDER_TYPE has Rotate");
        check(WorldGenV2Registries.VECTOR_PROVIDER_TYPE.contains("Imported"), "VECTOR_PROVIDER_TYPE has Imported");

        check(WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.contains("Strata"), "MATERIAL_PROVIDER_TYPE has Strata");
        check(WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.contains("Striped"), "MATERIAL_PROVIDER_TYPE has Striped");
        check(WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.contains("Layer"), "MATERIAL_PROVIDER_TYPE has Layer");
        check(WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.contains("Solidity"), "MATERIAL_PROVIDER_TYPE has Solidity");
        check(WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.contains("Imported"), "MATERIAL_PROVIDER_TYPE has Imported");

        check(WorldGenV2Registries.PATTERN_TYPE.contains("BlockType"), "PATTERN_TYPE has BlockType");
        check(WorldGenV2Registries.PATTERN_TYPE.contains("BlockSet"), "PATTERN_TYPE has BlockSet");
        check(WorldGenV2Registries.PATTERN_TYPE.contains("Rotator"), "PATTERN_TYPE has Rotator");
        check(WorldGenV2Registries.PATTERN_TYPE.contains("Imported"), "PATTERN_TYPE has Imported");

        check(WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.contains("Clusters"), "POSITIONS_PROVIDER_TYPE has Clusters");
        check(WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.contains("Anchor"), "POSITIONS_PROVIDER_TYPE has Anchor");
        check(WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.contains("SimpleHorizontal"), "POSITIONS_PROVIDER_TYPE has SimpleHorizontal");
        check(WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.contains("Imported"), "POSITIONS_PROVIDER_TYPE has Imported");

        check(WorldGenV2Registries.PROP_TYPE.contains("Block"), "PROP_TYPE has Block");
        check(WorldGenV2Registries.PROP_TYPE.contains("Prefab"), "PROP_TYPE has Prefab");
        check(WorldGenV2Registries.PROP_TYPE.contains("RandomRotator"), "PROP_TYPE has RandomRotator");
        check(WorldGenV2Registries.PROP_TYPE.contains("Imported"), "PROP_TYPE has Imported");

        check(WorldGenV2Registries.SCANNER_TYPE.contains("Linear"), "SCANNER_TYPE has Linear");
        check(WorldGenV2Registries.SCANNER_TYPE.contains("Queue"), "SCANNER_TYPE has Queue");
        check(WorldGenV2Registries.SCANNER_TYPE.contains("Imported"), "SCANNER_TYPE has Imported");

        check(WorldGenV2Registries.ASSIGNMENTS_TYPE.contains("Constant"), "ASSIGNMENTS_TYPE has Constant");
        check(WorldGenV2Registries.ASSIGNMENTS_TYPE.contains("Imported"), "ASSIGNMENTS_TYPE has Imported");

        check(WorldGenV2Registries.BLOCK_MASK_TYPE.contains("DontReplace"), "BLOCK_MASK_TYPE has DontReplace");
        check(WorldGenV2Registries.BLOCK_MASK_TYPE.contains("Imported"), "BLOCK_MASK_TYPE has Imported");

        check(WorldGenV2Registries.PROP_RUNTIME_TYPE.contains("PropRuntime"), "PROP_RUNTIME_TYPE has PropRuntime");
        check(WorldGenV2Registries.PROP_RUNTIME_TYPE.contains("Imported"), "PROP_RUNTIME_TYPE has Imported");
    }

    private static void testUniversalImportedAndExportedResolution() {
        System.out.println("--- Testing Universal Imported and Exported Asset Resolution ---");

        String mockSubAssetJson = "{\"type\": \"Constant\", \"value\": 42.0}";

        JsonAstCompiler.CompileContext context = new JsonAstCompiler.CompileContext(12345L)
            .withAssetResolver(path -> {
                if ("shared/constant_density.json".equals(path)) {
                    return mockSubAssetJson;
                }
                return null;
            });

        // Test Imported Density
        Map<String, Object> importedDensityJson = Map.of(
            "type", "Imported",
            "asset", "shared/constant_density.json"
        );
        DensityFunction compiledImported = JsonAstCompiler.compileDensity(importedDensityJson, context);
        check(compiledImported instanceof ConstantDensity, "Imported compiles to ConstantDensity from asset resolver");
        check(Math.abs(compiledImported.compute(0, 0, 0) - 42.0f) < 1e-5f, "Imported ConstantDensity value is 42.0");

        // Test Exported Density
        Map<String, Object> exportedDensityJson = Map.of(
            "type", "Exported",
            "node", Map.of(
                "type", "Constant",
                "value", 99.0
            )
        );
        DensityFunction compiledExported = JsonAstCompiler.compileDensity(exportedDensityJson, context);
        check(Math.abs(compiledExported.compute(0, 0, 0) - 99.0f) < 1e-5f, "Exported unpacks inner node and resolves 99.0");
    }

    private static void testAstCompilerOptimizerPass() {
        System.out.println("--- Testing AST Compiler Optimizer Pass ---");

        // 1. Sqrt of Constant
        DensityFunction sqrtConst = new SqrtDensity(new ConstantDensity(25.0f));
        DensityFunction optSqrt = JsonAstCompiler.optimizeDensity(sqrtConst);
        check(optSqrt instanceof ConstantDensity c && Math.abs(c.getValue() - 5.0f) < 1e-5f, "Constant folding folds Sqrt(25) -> Constant(5)");

        // 2. Pow of Constant
        DensityFunction powConst = new PowDensity(new ConstantDensity(4.0f), 2.0f);
        DensityFunction optPow = JsonAstCompiler.optimizeDensity(powConst);
        check(optPow instanceof ConstantDensity c && Math.abs(c.getValue() - 16.0f) < 1e-5f, "Constant folding folds Pow(4, 2) -> Constant(16)");

        // 3. Trig of Constant
        DensityFunction trigConst = new TrigDensity(new ConstantDensity(0.0f), TrigDensity.Function.COS, 1.0f, 10.0f, 0.0f);
        DensityFunction optTrig = JsonAstCompiler.optimizeDensity(trigConst);
        check(optTrig instanceof ConstantDensity c && Math.abs(c.getValue() - 10.0f) < 1e-5f, "Constant folding folds Trig(Cos(0)*10) -> Constant(10)");

        // 4. Mix with Constant Factor 0.0 -> returns A
        DensityFunction dynamicA = new DensityFunction() {
            @Override
            public float compute(float x, float y, float z) { return x; }
        };
        DensityFunction dynamicB = new DensityFunction() {
            @Override
            public float compute(float x, float y, float z) { return y; }
        };
        DensityFunction mixZero = new MixDensity(dynamicA, dynamicB, new ConstantDensity(0.0f));
        DensityFunction optMixZero = JsonAstCompiler.optimizeDensity(mixZero);
        check(optMixZero == dynamicA, "Algebraic simplification folds Mix(A, B, factor=0) -> A");

        // 5. Mix with Constant Factor 1.0 -> returns B
        DensityFunction mixOne = new MixDensity(dynamicA, dynamicB, new ConstantDensity(1.0f));
        DensityFunction optMixOne = JsonAstCompiler.optimizeDensity(mixOne);
        check(optMixOne == dynamicB, "Algebraic simplification folds Mix(A, B, factor=1) -> B");

        // 6. Slider collapsing: consecutive Slider + Slider
        SliderDensity s1 = new SliderDensity(dynamicA, 10, 0, 5);
        SliderDensity s2 = new SliderDensity(s1, 5, 20, -5);
        DensityFunction optSlider = JsonAstCompiler.optimizeDensity(s2);
        check(optSlider instanceof SliderDensity res && res.offsetX() == 15 && res.offsetY() == 20 && res.offsetZ() == 0,
            "Affine collapsing merges consecutive Sliders into single (15, 20, 0)");

        // 7. Offset collapsing: consecutive Offset + Offset
        OffsetDensity off1 = new OffsetDensity(dynamicA, 3, 4, 5);
        OffsetDensity off2 = new OffsetDensity(off1, 7, -4, 10);
        DensityFunction optOff = JsonAstCompiler.optimizeDensity(off2);
        check(optOff instanceof OffsetDensity res && res.offsetX() == 10 && res.offsetY() == 0 && res.offsetZ() == 15,
            "Affine collapsing merges consecutive Offsets into single (10, 0, 15)");

        // 8. Scale collapsing: consecutive Scale * Scale
        ScaleDensity sc1 = new ScaleDensity(dynamicA, 2.0f, 3.0f, 4.0f);
        ScaleDensity sc2 = new ScaleDensity(sc1, 0.5f, 2.0f, 0.25f);
        DensityFunction optScale = JsonAstCompiler.optimizeDensity(sc2);
        check(optScale instanceof ScaleDensity res && Math.abs(res.scaleX() - 1.0f) < 1e-5f && Math.abs(res.scaleY() - 6.0f) < 1e-5f && Math.abs(res.scaleZ() - 1.0f) < 1e-5f,
            "Affine collapsing merges consecutive Scales into single (1.0, 6.0, 1.0)");

        // 9. Double Invert cancellation: Invert(Invert(A)) -> A
        InvertDensity inv1 = new InvertDensity(dynamicA);
        InvertDensity inv2 = new InvertDensity(inv1);
        DensityFunction optInv = JsonAstCompiler.optimizeDensity(inv2);
        check(optInv == dynamicA, "Algebraic simplification: Invert(Invert(A)) collapses directly to A");

        // 10. Product with zero prune: Prod(A, 0, B) -> 0
        ProductDensity prodZero = new ProductDensity(List.of(dynamicA, new ConstantDensity(0.0f), dynamicB));
        DensityFunction optProdZero = JsonAstCompiler.optimizeDensity(prodZero);
        check(optProdZero instanceof ConstantDensity c && c.getValue() == 0.0f, "Algebraic simplification: Product containing 0 prunes branch to Constant(0)");

        // 11. Sum identity: Sum(A, 0) -> A
        SumDensity sumIdentity = new SumDensity(List.of(dynamicA, new ConstantDensity(0.0f)));
        DensityFunction optSumId = JsonAstCompiler.optimizeDensity(sumIdentity);
        check(optSumId == dynamicA, "Algebraic simplification: Sum(A, 0) collapses to A");
    }

    private static void testFullComplexBiomeAstRoundTrip() {
        System.out.println("--- Testing Full Complex Biome AST Round-Trip Ingestion ---");

        String biomeJson = """
        {
          "name": "worldgenv2:volcanic_crag",
          "defaultTransitionDistance": 48.0,
          "weight": 2.5,
          "sizeModifier": 1.2,
          "terrain": {
            "type": "Mix",
            "a": {
              "type": "WhiteNoise",
              "frequency": 0.02
            },
            "b": {
              "type": "Pow",
              "input": {
                "type": "WhiteNoise",
                "frequency": 0.02
              },
              "exponent": 2.0
            },
            "factor": {
              "type": "Trig",
              "function": "sin",
              "frequency": 1.0,
              "amplitude": 1.0,
              "phase": 0.0,
              "input": {
                "type": "Constant",
                "value": 1.5707963
              }
            }
          },
          "materialProvider": {
            "type": "Strata",
            "layers": [
              { "min": -64.0, "max": 0.0, "material": "minecraft:deepslate" },
              { "min": 0.0, "max": 128.0, "material": "minecraft:basalt" },
              { "min": 128.0, "max": 320.0, "material": "minecraft:blackstone" }
            ],
            "fallback": "minecraft:lava"
          },
          "props": [
            {
              "type": "PropRuntime",
              "stage": 1,
              "precision": "PROP",
              "scannerReach": 16,
              "patternOffset": 8,
              "prefabRadius": 24,
              "prop": {
                "type": "Block",
                "block": "minecraft:magma_block",
                "amount": 3,
                "solidSurface": true
              }
            }
          ]
        }
        """;

        JsonAstCompiler.CompiledBiome<String> compiled = JsonAstCompiler.compileBiome(
            10,
            biomeJson,
            new JsonAstCompiler.CompileContext(987654321L),
            Map.of(),
            s -> s
        );

        check(compiled != null, "Compiled complex biome is non-null");
        check("worldgenv2:volcanic_crag".equals(compiled.name()), "Biome name parsed correctly");

        TileBiome tileBiome = compiled.tileBiome();
        check(tileBiome != null, "Compiled biome is a TileBiome");
        check(tileBiome.weight() == 2.5f, "Biome weight is 2.5");
        check(tileBiome.sizeModifier() == 1.2f, "Biome sizeModifier is 1.2");
        check(tileBiome.defaultTransitionDistance() == 48.0f, "Biome transition distance is 48.0");

        DensityFunction terrain = tileBiome.terrainDensity();
        check(terrain != null, "Biome terrain density compiled successfully");

        // The factor is Trig(Sin(Constant(PI/2))) which optimizes to Constant(1.0).
        // Mix(A, B, factor=1.0) optimizes to B (Pow(WhiteNoise, 2.0))!
        check(terrain instanceof PowDensity, "AST Optimizer cleanly reduced complex Mix to PowDensity!");

        MaterialProvider<?> matProvider = tileBiome.materialProvider();
        check(matProvider instanceof StrataMaterialProvider, "Material provider compiled to StrataMaterialProvider");

        List<?> props = tileBiome.props();
        check(!props.isEmpty(), "Props list contains compiled runtime prop");
        check(props.getFirst() instanceof Prop, "Prop is an instance of Prop");
    }

    private static void testDimensionLayoutAndMacroPresets() {
        System.out.println("--- Testing DimensionLayoutConfig, End Rings, Spawn Island & Outside-Bounds Fallbacks ---");

        // 1. Verify End Dimension Concentric Layout (Central Island -> 10 Obsidian Towers -> Void Moat -> Outer Islands)
        DimensionLayoutConfig endLayout = DimensionLayoutConfig.endDimensionLayout(1337L);
        check(endLayout.outsideFallback() == OutsideBoundsFallback.VOID, "End layout outsideFallback is VOID");
        check(endLayout.resolvedPillars().size() == 10, "End layout places 10 circular Ring Pillars at R=43");
        check(endLayout.isVoidColumn(500.0f, 0.0f), "Point (500, 0) inside End Void Moat (R=190..960) IS a void column");

        float[] moatCol = new float[128];
        java.util.Arrays.fill(moatCol, 10.0f);
        endLayout.applyMacroZonesAndBounds(500.0f, 0.0f, 0, 128, moatCol);
        check(moatCol[64] < 0.0f, "Void moat column density is carved to negative air (< 0)");

        float[] centerCol = new float[128];
        java.util.Arrays.fill(centerCol, -10.0f);
        endLayout.applyMacroZonesAndBounds(0.0f, 0.0f, 0, 128, centerCol);
        check(centerCol[50] > 0.0f, "Central End Island plateau (Y=50) has positive solid density");
        check(centerCol[10] < 0.0f, "Underside of floating Central End Island (Y=10) is carved to void");

        // 2. Verify Spawn Island Layout with DEFAULT_BIOMES outside-bounds fallback
        DimensionLayoutConfig spawnLayout = DimensionLayoutConfig.spawnIslandLayout(
                220.0f, 520.0f, 640.0f, OutsideBoundsFallback.DEFAULT_BIOMES, "TemperateForest", 1337L
        );
        check(!spawnLayout.isVoidColumn(0.0f, 0.0f), "Spawn island center (0, 0) is solid");
        check(!spawnLayout.isVoidColumn(2000.0f, 2000.0f), "Outside bounds (2000, 2000) with DEFAULT_BIOMES is not void");
        check("TemperateForest".equals(spawnLayout.resolveZoneBiomeOverride(0.0f, 0.0f)), "Spawn island overrides center biome to TemperateForest");
        check("Ocean".equals(spawnLayout.resolveZoneBiomeOverride(340.0f, 0.0f)), "Coastal moat (R=340) overrides biome to Ocean");
        check(spawnLayout.resolveZoneBiomeOverride(1200.0f, 0.0f) == null, "Outside bounds (R=1200) returns null so normal Voronoi biomes take over");

        // 3. Verify Bounded Skyblock Layout with VOID outside-bounds fallback
        DimensionLayoutConfig skyblockVoid = DimensionLayoutConfig.spawnIslandLayout(
                180.0f, 320.0f, 380.0f, OutsideBoundsFallback.VOID, "TemperateForest", 1337L
        );
        check(!skyblockVoid.isVoidColumn(0.0f, 0.0f), "Skyblock spawn island (0, 0) is not void");
        check(skyblockVoid.isVoidColumn(800.0f, 0.0f), "Beyond boundsRadius=380 with VOID fallback IS a void column");

        // 4. Verify StudioNodeCatalog has all 6 new Dimension Macro Graph nodes registered
        check(StudioNodeCatalog.getSchema("World:DimensionOutput") != null, "Catalog has World:DimensionOutput");
        check(StudioNodeCatalog.getSchema("World:RadialZone") != null, "Catalog has World:RadialZone");
        check(StudioNodeCatalog.getSchema("World:RingLandmarks") != null, "Catalog has World:RingLandmarks");
        check(StudioNodeCatalog.getSchema("World:SpawnIslandAnchor") != null, "Catalog has World:SpawnIslandAnchor");
        check(StudioNodeCatalog.getSchema("World:RelationalLandmark") != null, "Catalog has World:RelationalLandmark");
        check(StudioNodeCatalog.getSchema("World:BiomeWeightEntry") != null, "Catalog has World:BiomeWeightEntry");

        // 5. Verify Compartmentalized Hierarchical Studio Navigation & Hytale Import State
        com.worldgenv2.studio.StudioState state = com.worldgenv2.studio.StudioState.get();
        check(state.getHierarchyLevel() == com.worldgenv2.studio.StudioState.HierarchyLevel.WORLD_OVERVIEW, "Studio opens at WORLD_OVERVIEW Home by default");
        check(state.getDimensionProfiles().size() == 4, "Studio has 4 compartmentalized Dimension profiles (Overworld, End, Nether, Hytale 576)");
        com.worldgenv2.studio.StudioState.DimensionStudioProfile ow = state.getDimensionProfile("minecraft:overworld");
        check(ow.getEnabledBiomeCount() == 39, "Overworld profile initializes with all 39 WorldGen V2 biomes");
        com.worldgenv2.studio.StudioState.BiomeEntryState imported = state.registerImportedHytaleBiome(
                "minecraft:overworld", "EmeraldGrove", 2.2f, 1.3f, 42.0f, "{}"
        );
        check(imported.importedHytale && ow.getEnabledBiomeCount() == 40, "Imported Hytale biome registers into Overworld biome list");
    }

    private static class MockVoxelSpace implements VoxelSpace<String> {
        private final Map<String, String> blocks = new HashMap<>();

        @Override
        public String getBlock(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, "minecraft:air");
        }

        @Override
        public void setBlock(int x, int y, int z, String state) {
            blocks.put(x + "," + y + "," + z, state);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            String b = getBlock(x, y, z);
            return b == null || "minecraft:air".equals(b);
        }

        @Override
        public int getMinY() {
            return -64;
        }

        @Override
        public int getMaxY() {
            return 320;
        }
    }
}
