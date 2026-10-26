package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.biome.WorldGenV2Registries;
import com.worldgenv2.curve.*;
import com.worldgenv2.density.*;
import com.worldgenv2.vector.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 8 Verification Test Suite: Curves & Vector Provider Framework.
 * Verifies mathematical precision, continuity, domain warping, and JSON AST compilation.
 */
public final class Phase8VerificationTest {

    private static int testsPassed = 0;

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Phase 8 assertion failed: " + description);
        }
        System.out.println("[PASS] " + description);
        testsPassed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 8 Verification Test Suite ===");

        testConstantAndManualCurve();
        testDistanceExponentialAndSCurves();
        testExtremaAndSmoothExtremaCurves();
        testMathCombinatorCurves();
        testCurveMapperDensity();
        testVec3dOperations();
        testVectorProviders();
        testDensityGradientAndVectorWarp();
        testJsonAstCompilation();

        System.out.println("Phase 8 verification: ALL " + testsPassed + " TESTS PASSED!");
    }

    private static void testConstantAndManualCurve() {
        ConstantCurve constant = new ConstantCurve(42.5);
        check(constant.evaluate(-100.0) == 42.5, "ConstantCurve returns 42.5 at negative input");
        check(constant.evaluate(100.0) == 42.5, "ConstantCurve returns 42.5 at positive input");

        // Manual curve with keyframes: (0, 0), (10, 100), (20, 50)
        List<ManualCurve.Point> points = List.of(
            new ManualCurve.Point(0.0, 0.0),
            new ManualCurve.Point(10.0, 100.0),
            new ManualCurve.Point(20.0, 50.0)
        );
        ManualCurve linear = new ManualCurve(points, ManualCurve.InterpolationMode.LINEAR);
        check(linear.evaluate(-5.0) == 0.0, "ManualCurve clamps constant before first point");
        check(linear.evaluate(0.0) == 0.0, "ManualCurve exact at first point");
        check(Math.abs(linear.evaluate(5.0) - 50.0) < 1e-6, "ManualCurve linear interpolation at midpoint");
        check(linear.evaluate(10.0) == 100.0, "ManualCurve exact at intermediate point");
        check(Math.abs(linear.evaluate(15.0) - 75.0) < 1e-6, "ManualCurve linear interpolation second interval");
        check(linear.evaluate(25.0) == 50.0, "ManualCurve clamps constant after last point");

        ManualCurve smooth = new ManualCurve(points, ManualCurve.InterpolationMode.SMOOTH);
        check(smooth.evaluate(0.0) == 0.0, "ManualCurve smooth exact at first point");
        check(smooth.evaluate(10.0) == 100.0, "ManualCurve smooth exact at second point");
        // Hermite smoothstep at t=0.5 is exactly 0.5 * (100 - 0) = 50.0
        check(Math.abs(smooth.evaluate(5.0) - 50.0) < 1e-6, "ManualCurve smoothstep inflection at midpoint");

        ManualCurve step = new ManualCurve(points, ManualCurve.InterpolationMode.STEP);
        check(step.evaluate(4.99) == 0.0, "ManualCurve step holds previous point");
        check(step.evaluate(10.0) == 100.0, "ManualCurve step transitions at control point");
    }

    private static void testDistanceExponentialAndSCurves() {
        DistanceExponentialCurve expCurve = new DistanceExponentialCurve(2.0, 50.0);
        check(expCurve.evaluate(-5.0) == 1.0, "DistanceExponential is 1.0 before 0.0");
        check(expCurve.evaluate(0.0) == 1.0, "DistanceExponential is exactly 1.0 at 0.0");
        check(expCurve.evaluate(50.0) == 0.0, "DistanceExponential is exactly 0.0 at range");
        check(expCurve.evaluate(60.0) == 0.0, "DistanceExponential is 0.0 beyond range");
        // At t = 25 / 50 = 0.5: (1 - 0.5)^2 = 0.25
        check(Math.abs(expCurve.evaluate(25.0) - 0.25) < 1e-6, "DistanceExponential quadratic curve value at half range");

        DistanceSCurve sCurve = new DistanceSCurve(2.0, 2.0, 100.0);
        check(sCurve.evaluate(-10.0) == 1.0, "DistanceSCurve is 1.0 before 0.0");
        check(sCurve.evaluate(0.0) == 1.0, "DistanceSCurve is 1.0 at 0.0");
        check(sCurve.evaluate(100.0) == 0.0, "DistanceSCurve is 0.0 at range");
        check(sCurve.evaluate(120.0) == 0.0, "DistanceSCurve is 0.0 beyond range");
        // S-curve inflection point at range / 2: value should be 0.5
        check(Math.abs(sCurve.evaluate(50.0) - 0.5) < 1e-6, "DistanceSCurve reaches 0.5 at midpoint");
    }

    private static void testExtremaAndSmoothExtremaCurves() {
        Curve base = x -> x;

        CeilingCurve ceil = new CeilingCurve(base, 10.0);
        check(ceil.evaluate(5.0) == 5.0, "CeilingCurve does not modify values below ceiling");
        check(ceil.evaluate(15.0) == 10.0, "CeilingCurve caps values above ceiling");

        FloorCurve floor = new FloorCurve(base, -5.0);
        check(floor.evaluate(0.0) == 0.0, "FloorCurve does not modify values above floor");
        check(floor.evaluate(-10.0) == -5.0, "FloorCurve limits values below floor");

        SmoothCeilingCurve smoothCeil = new SmoothCeilingCurve(base, 10.0, 2.0);
        check(smoothCeil.evaluate(0.0) == 0.0, "SmoothCeilingCurve equals linear well below ceiling");
        check(smoothCeil.evaluate(20.0) == 10.0, "SmoothCeilingCurve clamps to ceiling well above");
        // C1 smoothness: at input = ceiling (10.0), output should be ceiling - 0.25 * range = 9.5
        check(Math.abs(smoothCeil.evaluate(10.0) - 9.5) < 1e-6, "SmoothCeilingCurve smooth minimum curvature");

        SmoothFloorCurve smoothFloor = new SmoothFloorCurve(base, -5.0, 2.0);
        check(smoothFloor.evaluate(10.0) == 10.0, "SmoothFloorCurve equals linear well above floor");
        check(smoothFloor.evaluate(-20.0) == -5.0, "SmoothFloorCurve clamps to floor well below");
        check(Math.abs(smoothFloor.evaluate(-5.0) - (-4.5)) < 1e-6, "SmoothFloorCurve smooth maximum curvature");

        ClampCurve clamp = new ClampCurve(base, -2.0, 8.0);
        check(clamp.evaluate(-10.0) == -2.0, "ClampCurve min clamp");
        check(clamp.evaluate(3.0) == 3.0, "ClampCurve passthrough");
        check(clamp.evaluate(15.0) == 8.0, "ClampCurve max clamp");

        SmoothMinCurve smin = new SmoothMinCurve(x -> x, x -> 10.0 - x, 4.0);
        check(Math.abs(smin.evaluate(5.0) - 4.0) < 1e-6, "SmoothMinCurve smoothly rounds intersection of curves");

        SmoothMaxCurve smax = new SmoothMaxCurve(x -> x, x -> 10.0 - x, 4.0);
        check(Math.abs(smax.evaluate(5.0) - 6.0) < 1e-6, "SmoothMaxCurve smoothly rounds intersection of curves");
    }

    private static void testMathCombinatorCurves() {
        Curve linear = x -> x;
        InverterCurve inverter = new InverterCurve(linear);
        check(inverter.evaluate(7.5) == -7.5, "InverterCurve inverts sign");

        NotCurve not = new NotCurve(new ConstantCurve(0.2));
        check(Math.abs(not.evaluate(0.0) - 0.8) < 1e-6, "NotCurve 1 - x inversion");

        MultiplierCurve mul = new MultiplierCurve(x -> 2.0, x -> 3.0, x -> x);
        check(mul.evaluate(4.0) == 24.0, "MultiplierCurve calculates product of child curves");

        SumCurve sum = new SumCurve(x -> 10.0, x -> 20.0, x -> x);
        check(sum.evaluate(5.0) == 35.0, "SumCurve calculates sum of child curves");
    }

    private static void testCurveMapperDensity() {
        DensityFunction base = new VerticalGradientDensity(64.0f, 32.0f);
        Curve plateau = new SmoothClampCurve(x -> x, -0.5, 0.5, 0.1);

        CurveMapperDensity mapper = new CurveMapperDensity(base, plateau);
        float dBelow = mapper.compute(0, 0, 0);
        float dAbove = mapper.compute(0, 128, 0);
        check(dBelow <= 0.55f && dBelow >= 0.45f, "CurveMapperDensity clamps positive gradient to plateau");
        check(dAbove >= -0.55f && dAbove <= -0.45f, "CurveMapperDensity clamps negative gradient to floor");

        // Verify column batching matches single evaluations
        float[] col = new float[64];
        mapper.computeColumn(10, 20, 32, 96, col);
        boolean columnMatches = true;
        for (int i = 0; i < 64; i++) {
            float single = mapper.compute(10, 32 + i, 20);
            if (Math.abs(col[i] - single) > 1e-6) {
                columnMatches = false;
                break;
            }
        }
        check(columnMatches, "CurveMapperDensity computeColumn matches individual compute samples");
    }

    private static void testVec3dOperations() {
        Vec3d v1 = new Vec3d(1.0, 2.0, 3.0);
        Vec3d v2 = new Vec3d(4.0, 5.0, 6.0);

        check(v1.add(v2).equals(new Vec3d(5.0, 7.0, 9.0)), "Vec3d addition");
        check(v1.subtract(v2).equals(new Vec3d(-3.0, -3.0, -3.0)), "Vec3d subtraction");
        check(v1.multiply(v2).equals(new Vec3d(4.0, 10.0, 18.0)), "Vec3d component multiplication");
        check(v1.scale(2.0).equals(new Vec3d(2.0, 4.0, 6.0)), "Vec3d scaling");
        check(v1.dot(v2) == 32.0, "Vec3d dot product (4 + 10 + 18 = 32)");

        // Cross product of Unit X and Unit Y is Unit Z
        Vec3d cross = Vec3d.UNIT_X.cross(Vec3d.UNIT_Y);
        check(cross.equals(Vec3d.UNIT_Z), "Vec3d cross product: X x Y = Z");

        // Normalization
        Vec3d vNorm = new Vec3d(0.0, 3.0, 4.0).normalize();
        check(Math.abs(vNorm.length() - 1.0) < 1e-6, "Vec3d normalize unit length");
        check(Math.abs(vNorm.y() - 0.6) < 1e-6 && Math.abs(vNorm.z() - 0.8) < 1e-6, "Vec3d normalize components");
    }

    private static void testVectorProviders() {
        ConstantVectorProvider cVec = new ConstantVectorProvider(10.0, 20.0, 30.0);
        check(cVec.getVector(0, 0, 0).equals(new Vec3d(10.0, 20.0, 30.0)), "ConstantVectorProvider invariant");

        SetXVectorProvider setX = new SetXVectorProvider(cVec, new ConstantDensity(99.0f));
        check(setX.getVector(0, 0, 0).equals(new Vec3d(99.0, 20.0, 30.0)), "SetXVectorProvider overrides X");

        SetYVectorProvider setY = new SetYVectorProvider(cVec, new ConstantDensity(88.0f));
        check(setY.getVector(0, 0, 0).equals(new Vec3d(10.0, 88.0, 30.0)), "SetYVectorProvider overrides Y");

        SetZVectorProvider setZ = new SetZVectorProvider(cVec, new ConstantDensity(77.0f));
        check(setZ.getVector(0, 0, 0).equals(new Vec3d(10.0, 20.0, 77.0)), "SetZVectorProvider overrides Z");

        // Adder & Subtracter
        AdderVectorProvider adder = new AdderVectorProvider(
            new ConstantVectorProvider(1, 2, 3),
            new ConstantVectorProvider(10, 20, 30)
        );
        check(adder.getVector(0, 0, 0).equals(new Vec3d(11, 22, 33)), "AdderVectorProvider sums vectors");

        SubtracterVectorProvider sub = new SubtracterVectorProvider(
            new ConstantVectorProvider(10, 20, 30),
            new ConstantVectorProvider(3, 5, 7)
        );
        check(sub.getVector(0, 0, 0).equals(new Vec3d(7, 15, 23)), "SubtracterVectorProvider vector difference");

        // Normalizer with magnitude
        NormalizerVectorProvider norm = new NormalizerVectorProvider(
            new ConstantVectorProvider(0, 10, 0),
            new ConstantDensity(5.0f)
        );
        check(norm.getVector(0, 0, 0).equals(new Vec3d(0, 5, 0)), "NormalizerVectorProvider scales to magnitude");

        // Plane Projector: projecting (1, 2, 3) onto XY plane (normal along Z) should yield (1, 2, 0)
        PlaneProjectorVectorProvider planeProj = new PlaneProjectorVectorProvider(
            new ConstantVectorProvider(1.0, 2.0, 3.0),
            new ConstantVectorProvider(1.0, 0.0, 0.0), // Plane vector A
            new ConstantVectorProvider(0.0, 1.0, 0.0)  // Plane vector B
        );
        Vec3d proj = planeProj.getVector(0, 0, 0);
        check(Math.abs(proj.x() - 1.0) < 1e-6 && Math.abs(proj.y() - 2.0) < 1e-6 && Math.abs(proj.z()) < 1e-6,
            "PlaneProjectorVectorProvider projects onto XY plane");

        // Vector Projector: projecting (3, 4, 0) onto (1, 0, 0) should yield (3, 0, 0)
        VectorProjectorVectorProvider vecProj = new VectorProjectorVectorProvider(
            new ConstantVectorProvider(3.0, 4.0, 0.0),
            new ConstantVectorProvider(1.0, 0.0, 0.0)
        );
        Vec3d vProjResult = vecProj.getVector(0, 0, 0);
        check(Math.abs(vProjResult.x() - 3.0) < 1e-6 && Math.abs(vProjResult.y()) < 1e-6,
            "VectorProjectorVectorProvider projects onto target vector");

        // CacheVectorProvider
        CacheVectorProvider cache = new CacheVectorProvider(cVec);
        Vec3d c1 = cache.getVector(5, 5, 5);
        Vec3d c2 = cache.getVector(5, 5, 5);
        check(c1 == c2, "CacheVectorProvider memoizes identical coordinate query");
    }

    private static void testDensityGradientAndVectorWarp() {
        // Density function D(x, y, z) = 2x - 3y + 4z
        DensityFunction linearDensity = (x, y, z) -> 2.0f * x - 3.0f * y + 4.0f * z;

        DensityGradientVectorProvider gradProvider = new DensityGradientVectorProvider(linearDensity, 1.0);
        Vec3d grad = gradProvider.getVector(10, 20, 30);
        check(Math.abs(grad.x() - 2.0) < 1e-5, "DensityGradient dD/dx = 2.0");
        check(Math.abs(grad.y() - (-3.0)) < 1e-5, "DensityGradient dD/dy = -3.0");
        check(Math.abs(grad.z() - 4.0) < 1e-5, "DensityGradient dD/dz = 4.0");

        // Vector Warp: offset coordinates by (10, 0, 5)
        VectorProvider offsetProvider = new ConstantVectorProvider(10.0, 0.0, 5.0);
        VectorWarpDensity warp = new VectorWarpDensity((x, y, z) -> x + z, offsetProvider);
        // compute at (0, 0, 0) should evaluate (0 + 10) + (0 + 5) = 15
        check(warp.compute(0, 0, 0) == 15.0f, "VectorWarpDensity applies 3D coordinate displacement");
    }

    private static void testJsonAstCompilation() {
        // Test compiling Curve from JSON
        String curveJson = """
            {
                "type": "SmoothClampCurve",
                "wallA": -10.0,
                "wallB": 10.0,
                "range": 2.0,
                "curve": {
                    "type": "DistanceExponential",
                    "exponent": 1.5,
                    "range": 50.0
                }
            }
            """;
        Object parsedCurve = new JsonAstCompiler.SimpleJsonParser(curveJson).parseValue();
        Curve compiledCurve = JsonAstCompiler.compileCurve(parsedCurve, new JsonAstCompiler.CompileContext(12345L));
        check(compiledCurve instanceof SmoothClampCurve, "JSON AST compiles SmoothClampCurve");
        check(compiledCurve.evaluate(0.0) == 1.0, "JSON AST compiled curve evaluates correctly");

        // Test compiling VectorProvider from JSON
        String vectorJson = """
            {
                "type": "Cross",
                "vectorA": {
                    "type": "Constant",
                    "vector": [1.0, 0.0, 0.0]
                },
                "vectorB": {
                    "type": "Constant",
                    "vector": [0.0, 1.0, 0.0]
                }
            }
            """;
        Object parsedVector = new JsonAstCompiler.SimpleJsonParser(vectorJson).parseValue();
        VectorProvider compiledVector = JsonAstCompiler.compileVectorProvider(parsedVector, new JsonAstCompiler.CompileContext(12345L));
        check(compiledVector instanceof CrossVectorProvider, "JSON AST compiles CrossVectorProvider");
        Vec3d vecResult = compiledVector.getVector(0, 0, 0);
        check(vecResult.equals(Vec3d.UNIT_Z), "JSON AST compiled CrossVectorProvider evaluates X x Y = Z");

        // Test compiling CurveMapperDensity from JSON
        String densityJson = """
            {
                "type": "CurveMapper",
                "source": {
                    "type": "Constant",
                    "value": 5.0
                },
                "curve": {
                    "type": "Inverter",
                    "curve": {
                        "type": "Constant",
                        "value": 5.0
                    }
                }
            }
            """;
        JsonAstCompiler compiler = new JsonAstCompiler();
        DensityFunction compiledDensity = compiler.compileDensityJson(densityJson, 12345L);
        check(compiledDensity.compute(0, 0, 0) == -5.0f, "JSON AST compiles CurveMapperDensity and evaluates correctly");
    }
}
