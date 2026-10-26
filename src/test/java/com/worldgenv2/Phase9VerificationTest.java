package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.curve.ConstantCurve;
import com.worldgenv2.density.*;
import com.worldgenv2.vector.Vec3d;

import java.util.List;

/**
 * Phase 9 Verification Test Suite: Advanced Density Nodes & Caching Infrastructure.
 */
public final class Phase9VerificationTest {

    private static int testsPassed = 0;

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Phase 9 assertion failed: " + description);
        }
        System.out.println("[PASS] " + description);
        testsPassed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 9 Verification Test Suite ===");

        testCellularNoise();
        testGeometricPrimitives();
        testLogicAndComparisonNodes();
        testSpatialCoordinatesAndOverrides();
        testCacheAndSingleInstance();
        testJsonAstCompilation();

        System.out.println("Phase 9 verification: ALL " + testsPassed + " TESTS PASSED!");
    }

    private static void testCellularNoise() {
        FastNoiseLite noise = new FastNoiseLite(12345);
        noise.setNoiseType(FastNoiseLite.NoiseType.Cellular);
        noise.setFrequency(0.05f);
        noise.setCellularReturnType(FastNoiseLite.CellularReturnType.CellWallDistance);

        CellNoise2dDensity cell2d = new CellNoise2dDensity(noise, 1.0f);
        float v1 = cell2d.compute(10, 0, 20);
        float v2 = cell2d.compute(10, 100, 20);
        check(v1 == v2, "CellNoise2dDensity is column-constant across Y");

        // Verify computeColumn optimization matches compute
        float[] col = new float[32];
        cell2d.computeColumn(10, 20, 0, 32, col);
        check(col[0] == v1 && col[31] == v1, "CellNoise2dDensity computeColumn matches");

        CellNoise3dDensity cell3d = new CellNoise3dDensity(noise, 1.0f);
        float v3d_1 = cell3d.compute(10, 0, 20);
        float v3d_2 = cell3d.compute(10, 50, 20);
        check(v3d_1 != v3d_2, "CellNoise3dDensity varies across volumetric Y space");
    }

    private static void testGeometricPrimitives() {
        // Ellipsoid centered at (0, 0, 0) with radii (10, 20, 30)
        EllipsoidDensity ellipsoid = new EllipsoidDensity(10.0f, 20.0f, 30.0f);
        check(ellipsoid.compute(0, 0, 0) == 1.0f, "Ellipsoid center is maximum density (1.0)");
        check(ellipsoid.compute(5, 0, 0) > 0.0f, "Ellipsoid interior is positive solid matter");
        check(Math.abs(ellipsoid.compute(10, 0, 0)) < 1e-6, "Ellipsoid boundary at X=radiusX is isosurface (0.0)");
        check(Math.abs(ellipsoid.compute(0, 20, 0)) < 1e-6, "Ellipsoid boundary at Y=radiusY is isosurface (0.0)");
        check(Math.abs(ellipsoid.compute(0, 0, 30)) < 1e-6, "Ellipsoid boundary at Z=radiusZ is isosurface (0.0)");
        check(ellipsoid.compute(15, 0, 0) < 0.0f, "Ellipsoid exterior is negative air space");

        // Cuboid with half-extents (5, 10, 15)
        CuboidDensity cuboid = new CuboidDensity(5.0f, 10.0f, 15.0f);
        check(cuboid.compute(0, 0, 0) == 1.0f, "Cuboid center is maximum density (1.0)");
        check(cuboid.compute(2, 4, 6) > 0.0f, "Cuboid interior is positive");
        check(Math.abs(cuboid.compute(5, 0, 0)) < 1e-6, "Cuboid X face is 0.0 isosurface");
        check(Math.abs(cuboid.compute(0, 10, 0)) < 1e-6, "Cuboid Y face is 0.0 isosurface");
        check(cuboid.compute(10, 0, 0) < 0.0f, "Cuboid exterior is negative");

        // Cylinder along Y axis with radius 20
        CylinderDensity cylinder = new CylinderDensity(20.0f, CylinderDensity.Axis.Y, null);
        check(cylinder.compute(0, 50, 0) == 1.0f, "Cylinder axis line is 1.0 everywhere along Y");
        check(Math.abs(cylinder.compute(20, 50, 0)) < 1e-6, "Cylinder surface at radial distance is 0.0");
        check(cylinder.compute(30, 50, 0) < 0.0f, "Cylinder exterior is negative");

        // Plane with normal UNIT_Y and offset 64.0
        PlaneDensity plane = new PlaneDensity(Vec3d.UNIT_Y, 64.0f);
        check(plane.compute(0, 64, 0) == 0.0f, "Plane isosurface at Y=64");
        check(plane.compute(0, 50, 0) > 0.0f, "Plane solid below Y=64");
        check(plane.compute(0, 80, 0) < 0.0f, "Plane air above Y=64");

        // Spherical Shell between radius 30 and 50
        ShellDensity shell = new ShellDensity(30.0f, 50.0f);
        check(shell.compute(0, 40, 0) == 1.0f, "Shell midpoint is maximum (1.0)");
        check(shell.compute(0, 20, 0) < 0.0f, "Shell interior cavity is air");
        check(shell.compute(0, 60, 0) < 0.0f, "Shell exterior is air");
    }

    private static void testLogicAndComparisonNodes() {
        DensityFunction c10 = new ConstantDensity(10.0f);
        DensityFunction c20 = new ConstantDensity(20.0f);

        ComparatorDensity comp = new ComparatorDensity(c10, c20);
        check(comp.compute(0, 0, 0) == -1.0f, "Comparator returns -1 for A < B");

        EqualDensity eq = new EqualDensity(c10, new ConstantDensity(10.05f), 0.1f);
        check(eq.compute(0, 0, 0) == 1.0f, "Equal with epsilon returns 1.0");

        GreaterThanDensity gt = new GreaterThanDensity(c20, c10);
        check(gt.compute(0, 0, 0) == 1.0f, "GreaterThan returns 1.0");

        GreaterOrEqualDensity ge = new GreaterOrEqualDensity(c10, c10);
        check(ge.compute(0, 0, 0) == 1.0f, "GreaterOrEqual returns 1.0 for equal inputs");

        LessThanDensity lt = new LessThanDensity(c10, c20);
        check(lt.compute(0, 0, 0) == 1.0f, "LessThan returns 1.0");

        LessOrEqualDensity le = new LessOrEqualDensity(c10, c10);
        check(le.compute(0, 0, 0) == 1.0f, "LessOrEqual returns 1.0 for equal inputs");

        AndDensity and = new AndDensity(new ConstantDensity(1.0f), new ConstantDensity(2.0f));
        check(and.compute(0, 0, 0) == 1.0f, "And returns 1.0 when all non-zero");

        OrDensity or = new OrDensity(new ConstantDensity(0.0f), new ConstantDensity(5.0f));
        check(or.compute(0, 0, 0) == 1.0f, "Or returns 1.0 when one non-zero");

        NotDensity not = new NotDensity(new ConstantDensity(0.0f));
        check(not.compute(0, 0, 0) == 1.0f, "Not returns 1.0 for zero input");

        NorDensity nor = new NorDensity(new ConstantDensity(0.0f), new ConstantDensity(0.0f));
        check(nor.compute(0, 0, 0) == 1.0f, "Nor returns 1.0 when all inputs are 0");

        XorDensity xor = new XorDensity(new ConstantDensity(1.0f), new ConstantDensity(0.0f));
        check(xor.compute(0, 0, 0) == 1.0f, "Xor returns 1.0 for single non-zero");

        // Selector lazy branch evaluation
        SelectorDensity sel = new SelectorDensity(
            new ConstantDensity(1.0f), // Condition = 1.0 (true)
            new ConstantDensity(100.0f),
            new ConstantDensity(200.0f)
        );
        check(sel.compute(0, 0, 0) == 100.0f, "Selector routes to then branch");

        // Switch routing
        SwitchDensity sw = new SwitchDensity(
            new ConstantDensity(1.0f), // index 1
            List.of(new ConstantDensity(10.0f), new ConstantDensity(20.0f), new ConstantDensity(30.0f))
        );
        check(sw.compute(0, 0, 0) == 20.0f, "Switch routes to branch index 1");
    }

    private static void testSpatialCoordinatesAndOverrides() {
        check(XValueDensity.INSTANCE.compute(15.5f, 20.0f, 30.0f) == 15.5f, "XValueDensity returns X coordinate");
        check(YValueDensity.INSTANCE.compute(15.5f, 64.0f, 30.0f) == 64.0f, "YValueDensity returns Y coordinate");
        check(ZValueDensity.INSTANCE.compute(15.5f, 20.0f, -42.0f) == -42.0f, "ZValueDensity returns Z coordinate");

        // Override: source evaluates X, override sets X to 999
        XOverrideDensity xOverride = new XOverrideDensity(XValueDensity.INSTANCE, new ConstantDensity(999.0f));
        check(xOverride.compute(0, 0, 0) == 999.0f, "XOverrideDensity replaces X coordinate");

        YOverrideDensity yOverride = new YOverrideDensity(YValueDensity.INSTANCE, new ConstantDensity(888.0f));
        check(yOverride.compute(0, 0, 0) == 888.0f, "YOverrideDensity replaces Y coordinate");

        ZOverrideDensity zOverride = new ZOverrideDensity(ZValueDensity.INSTANCE, new ConstantDensity(777.0f));
        check(zOverride.compute(0, 0, 0) == 777.0f, "ZOverrideDensity replaces Z coordinate");
    }

    private static void testCacheAndSingleInstance() {
        final int[] evalCount = new int[1];
        DensityFunction countingSource = (x, y, z) -> {
            evalCount[0]++;
            return x + y + z;
        };

        CacheDensity cached = new CacheDensity(countingSource);
        evalCount[0] = 0;
        float v1 = cached.compute(5, 10, 15);
        float v2 = cached.compute(5, 10, 15);
        check(v1 == 30.0f && v2 == 30.0f, "CacheDensity computes correct value");
        check(evalCount[0] == 1, "CacheDensity eliminates redundant evaluation for identical coordinates");

        SingleInstanceDensity single = new SingleInstanceDensity(cached);
        check(single.compute(5, 10, 15) == 30.0f, "SingleInstanceDensity delegates correctly");
        check(evalCount[0] == 1, "SingleInstanceDensity retains memoized state");
    }

    private static void testJsonAstCompilation() {
        JsonAstCompiler compiler = new JsonAstCompiler();

        String json = """
            {
                "type": "Selector",
                "condition": {
                    "type": "GreaterThan",
                    "inputA": { "type": "YValue" },
                    "inputB": { "type": "Constant", "value": 64.0 }
                },
                "then": {
                    "type": "Ellipsoid",
                    "radiusX": 25.0,
                    "radiusY": 50.0,
                    "radiusZ": 25.0
                },
                "else": {
                    "type": "Cuboid",
                    "halfX": 30.0,
                    "halfY": 10.0,
                    "halfZ": 30.0
                }
            }
            """;
        DensityFunction compiled = compiler.compileDensityJson(json, 42L);
        check(compiled instanceof SelectorDensity, "JSON AST compiles Selector with GreaterThan and Geometric SDFs");

        // At Y = 80 (> 64), uses Ellipsoid: center (0, 80, 0) is not (0,0,0) of ellipsoid,
        // compute at (0, 80, 0) -> Ellipsoid evaluates at y=80 with radiusY=50 -> 1.0 - 80/50 = -0.6 (air)
        float dAbove = compiled.compute(0, 80, 0);
        check(dAbove < 0.0f, "Above Y=64 routes to Ellipsoid branch");

        // At Y = 0 (<= 64), uses Cuboid: center (0, 0, 0) of cuboid is 1.0
        float dBelow = compiled.compute(0, 0, 0);
        check(dBelow == 1.0f, "Below Y=64 routes to Cuboid branch");
    }
}
