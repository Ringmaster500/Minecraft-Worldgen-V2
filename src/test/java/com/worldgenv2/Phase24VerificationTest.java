package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.curve.Curve;
import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.vector.VectorProvider;

import java.util.Map;

/** Phase 24 verification: Hytale Curve and Vector Provider nodes. */
public final class Phase24VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    @SuppressWarnings("unchecked")
    private static Object parse(String json) { return new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static Curve c(String json) { return JsonAstCompiler.compileCurve(parse(json), new JsonAstCompiler.CompileContext(1L)); }
    private static VectorProvider v(String json) { return JsonAstCompiler.compileVectorProvider(parse(json), new JsonAstCompiler.CompileContext(1L)); }

    private static String k(double x) { return "{\"Type\":\"Constant\",\"Value\":" + x + "}"; }
    private static String vc(double x, double y, double z) { return "{\"Type\":\"Constant\",\"Vector\":{\"X\":" + x + ",\"Y\":" + y + ",\"Z\":" + z + "}}"; }
    private static String dc(double x) { return "{\"Type\":\"Constant\",\"Value\":" + x + "}"; }

    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }
    private static boolean eq(Vec3d a, double x, double y, double z) { return near(a.x(), x) && near(a.y(), y) && near(a.z(), z); }

    public static void main(String[] args) {
        System.out.println("=== Phase 24 Verification Test Suite: Hytale curves and vector providers ===");
        JsonAstCompiler.registerBuiltInTypes();

        check(near(c(k(3)).evaluate(99), 3), "Curve Constant outputs its value");
        Curve man = c("{\"Type\":\"Manual\",\"Points\":[{\"X\":0,\"Y\":0,\"Z\":0},{\"X\":10,\"Y\":5,\"Z\":0},{\"X\":20,\"Y\":5,\"Z\":0}]}");
        check(near(man.evaluate(5), 2.5) && near(man.evaluate(-50), 0) && near(man.evaluate(500), 5), "Manual connects points with lines and is constant before the first and after the last");
        Curve de = c("{\"Type\":\"DistanceExponential\",\"Exponent\":2,\"Range\":10}");
        check(near(de.evaluate(0), 1) && near(de.evaluate(10), 0) && near(de.evaluate(50), 0) && de.evaluate(5) < 0.5, "DistanceExponential: 1 at 0, 0 at Range and beyond, easing between");
        Curve ds = c("{\"Type\":\"DistanceS\",\"ExponentA\":1,\"ExponentB\":1,\"Range\":10}");
        check(near(ds.evaluate(0), 1) && near(ds.evaluate(10), 0) && ds.evaluate(2) > ds.evaluate(8), "DistanceS: 1 at 0, 0 at Range, falling");
        check(near(c("{\"Type\":\"Ceiling\",\"Ceiling\":2,\"Curve\":" + k(5) + "}").evaluate(0), 2) && near(c("{\"Type\":\"Floor\",\"Floor\":2,\"Curve\":" + k(-5) + "}").evaluate(0), 2), "Ceiling and Floor limit the child");
        check(c("{\"Type\":\"SmoothCeiling\",\"Ceiling\":2,\"Range\":1,\"Curve\":" + k(50) + "}").evaluate(0) <= 2.0001, "SmoothCeiling never exceeds the ceiling");
        check(near(c("{\"Type\":\"Clamp\",\"WallA\":3,\"WallB\":1,\"Curve\":" + k(9) + "}").evaluate(0), 3) && near(c("{\"Type\":\"Clamp\",\"WallA\":3,\"WallB\":1,\"Curve\":" + k(-9) + "}").evaluate(0), 1), "Clamp takes its walls in either order");
        check(near(c("{\"Type\":\"Max\",\"Curves\":[" + k(1) + "," + k(4) + "," + k(2) + "]}").evaluate(0), 4) && near(c("{\"Type\":\"Min\",\"Curves\":[" + k(3) + "," + k(4) + "," + k(2) + "]}").evaluate(0), 2), "Max and Min over a list");
        check(near(c("{\"Type\":\"Sum\",\"Curves\":[" + k(1) + "," + k(4) + "]}").evaluate(0), 5) && near(c("{\"Type\":\"Multiplier\",\"Curves\":[" + k(2) + "," + k(4) + "]}").evaluate(0), 8), "Sum adds and Multiplier multiplies");
        check(near(c("{\"Type\":\"Inverter\",\"Curve\":" + k(3) + "}").evaluate(0), -3) && near(c("{\"Type\":\"Not\",\"Curve\":" + k(1) + "}").evaluate(0), 0) && near(c("{\"Type\":\"Not\",\"Curve\":" + k(0.25) + "}").evaluate(0), 0.75), "Inverter flips sign; Not outputs 1 minus the child");
        check(c("{\"Type\":\"SmoothMax\",\"Range\":2,\"CurveA\":" + k(1) + ",\"CurveB\":" + k(1) + "}").evaluate(0) >= 1 && near(c("{\"Type\":\"SmoothMin\",\"Range\":1,\"CurveA\":" + k(2) + ",\"CurveB\":" + k(9) + "}").evaluate(0), 2), "SmoothMax/SmoothMin follow the max/min away from the join");

        check(eq(v(vc(1, 2, 3)).getVector(0, 0, 0), 1, 2, 3), "Vector Constant");
        check(eq(v("{\"Type\":\"SetX\",\"Vector\":" + vc(1, 2, 3) + ",\"Value\":" + dc(9) + "}").getVector(0, 0, 0), 9, 2, 3) && eq(v("{\"Type\":\"SetY\",\"Vector\":" + vc(1, 2, 3) + ",\"Value\":" + dc(9) + "}").getVector(0, 0, 0), 1, 9, 3) && eq(v("{\"Type\":\"SetZ\",\"Vector\":" + vc(1, 2, 3) + ",\"Value\":" + dc(9) + "}").getVector(0, 0, 0), 1, 2, 9), "SetX/SetY/SetZ replace one component");
        check(eq(v("{\"Type\":\"Adder\",\"Vectors\":[" + vc(1, 2, 3) + "," + vc(10, 20, 30) + "]}").getVector(0, 0, 0), 11, 22, 33), "Adder adds component-wise");
        check(eq(v("{\"Type\":\"Subtracter\",\"Minuend\":" + vc(5, 5, 5) + ",\"Subtrahend\":" + vc(1, 2, 3) + "}").getVector(0, 0, 0), 4, 3, 2), "Subtracter is Minuend minus Subtrahend");
        check(eq(v("{\"Type\":\"Multiplier\",\"Vectors\":[" + vc(1, 2, 3) + "," + vc(2, 2, 2) + "]}").getVector(0, 0, 0), 2, 4, 6), "Multiplier multiplies component-wise");
        check(eq(v("{\"Type\":\"ScalarMultiplier\",\"Vector\":" + vc(1, 2, 3) + ",\"Scalar\":" + dc(-2) + "}").getVector(0, 0, 0), -2, -4, -6), "ScalarMultiplier scales (negative flips)");
        check(eq(v("{\"Type\":\"Normalizer\",\"Vector\":" + vc(3, 0, 4) + ",\"Magnitude\":" + dc(10) + "}").getVector(0, 0, 0), 6, 0, 8), "Normalizer sets the length to Magnitude keeping direction");
        check(eq(v("{\"Type\":\"Cross\",\"VectorA\":" + vc(1, 0, 0) + ",\"VectorB\":" + vc(0, 1, 0) + "}").getVector(0, 0, 0), 0, 0, 1), "Cross is A cross B");
        check(eq(v("{\"Type\":\"PlaneProjector\",\"Vector\":" + vc(1, 2, 3) + ",\"PlaneA\":" + vc(1, 0, 0) + ",\"PlaneB\":" + vc(0, 0, 1) + "}").getVector(0, 0, 0), 1, 0, 3), "PlaneProjector removes the component off the plane");
        check(eq(v("{\"Type\":\"VectorProjector\",\"Source\":" + vc(3, 4, 0) + ",\"Target\":" + vc(2, 0, 0) + "}").getVector(0, 0, 0), 3, 0, 0), "VectorProjector keeps the part along Target");
        Vec3d g = v("{\"Type\":\"DensityGradient\",\"SampleDistance\":1,\"Density\":{\"Type\":\"XValue\"}}").getVector(5, 5, 5);
        check(near(g.x(), 1) && near(g.y(), 0) && near(g.z(), 0), "DensityGradient of X is the unit X vector");
        Vec3d r = v("{\"Type\":\"Random\",\"Seed\":\"abc\"}").getVector(1, 2, 3);
        check(near(Math.sqrt(r.x() * r.x() + r.y() * r.y() + r.z() * r.z()), 1), "Random outputs a unit vector");
        check(eq(v("{\"Type\":\"Cache\",\"Vector\":" + vc(1, 2, 3) + "}").getVector(0, 0, 0), 1, 2, 3), "Cache passes the vector through");

        System.out.println("Phase 24 verification: ALL " + passed + " TESTS PASSED!");
    }
}