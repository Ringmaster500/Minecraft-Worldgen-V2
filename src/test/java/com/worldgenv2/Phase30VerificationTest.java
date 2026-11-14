package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;

import java.util.Map;

/** Phase 30 verification: Hytale's shape densities, Rotator, Trig, warps and Switch (docs.hytale.com/hytale-generator/density). */
public final class Phase30VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static final String ID = "{\"Type\":\"Manual\",\"Points\":[{\"X\":-100,\"Y\":-100,\"Z\":0},{\"X\":100,\"Y\":100,\"Z\":0}]}";
    private static final String Y = "{\"X\":0,\"Y\":1,\"Z\":0}";

    @SuppressWarnings("unchecked")
    private static DensityFunction d(String j) { return JsonAstCompiler.compileDensity((Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(j).parseValue(), new JsonAstCompiler.CompileContext(1L)); }

    private static boolean near(float a, double b) { return Math.abs(a - b) < 1e-3; }

    public static void main(String[] args) {
        System.out.println("=== Phase 30 Verification Test Suite: shapes, rotation, trig, warps, switch ===");
        JsonAstCompiler.registerBuiltInTypes();

        DensityFunction dist = d("{\"Type\":\"Distance\",\"Curve\":" + ID + "}");
        check(near(dist.compute(3, 4, 0), 5), "Distance maps the distance from the origin through the Curve (in blocks)");
        DensityFunction ell = d("{\"Type\":\"Ellipsoid\",\"Curve\":" + ID + ",\"Scale\":{\"X\":2,\"Y\":1,\"Z\":1},\"NewYAxis\":" + Y + ",\"Spin\":0}");
        check(near(ell.compute(3, 0, 0), 1.5) && near(ell.compute(0, 4, 0), 4) && near(ell.compute(3, 4, 0), Math.sqrt(1.5 * 1.5 + 16)), "Ellipsoid: Scale stretches the sphere (twice as wide along X means half the distance)");
        DensityFunction tilted = d("{\"Type\":\"Ellipsoid\",\"Curve\":" + ID + ",\"Scale\":{\"X\":1,\"Y\":3,\"Z\":1},\"NewYAxis\":{\"X\":1,\"Y\":0,\"Z\":0},\"Spin\":0}");
        check(near(tilted.compute(6, 0, 0), 2) && near(tilted.compute(0, 6, 0), 6), "Ellipsoid: NewYAxis turns the stretched axis (stretch 3 along Y, Y now points along X)");
        DensityFunction cube = d("{\"Type\":\"Cube\",\"Curve\":" + ID + "}");
        check(near(cube.compute(3, 4, 0), 4) && near(cube.compute(10, 10, 10), 10), "Cube measures distance as the largest axis offset");
        DensityFunction cuboid = d("{\"Type\":\"Cuboid\",\"Curve\":" + ID + ",\"Scale\":{\"X\":2,\"Y\":1,\"Z\":1},\"NewYAxis\":" + Y + ",\"Spin\":0}");
        check(near(cuboid.compute(3, 0, 0), 1.5) && near(cuboid.compute(3, 4, 0), 4), "Cuboid: a Cube deformed by Scale");
        DensityFunction cyl = d("{\"Type\":\"Cylinder\",\"AxialCurve\":" + ID + ",\"RadialCurve\":{\"Type\":\"Constant\",\"Value\":1},\"Spin\":0,\"NewYAxis\":" + Y + "}");
        check(near(cyl.compute(5, 3, 5), 3) && near(cyl.compute(5, -3, 5), -3), "Cylinder: AxialCurve reads the signed height above the XZ plane");
        DensityFunction cyl2 = d("{\"Type\":\"Cylinder\",\"AxialCurve\":{\"Type\":\"Constant\",\"Value\":1},\"RadialCurve\":" + ID + ",\"Spin\":0,\"NewYAxis\":" + Y + "}");
        check(near(cyl2.compute(3, 9, 4), 5), "Cylinder: RadialCurve reads the distance from the Y axis");
        DensityFunction axis = d("{\"Type\":\"Axis\",\"Axis\":" + Y + ",\"Curve\":" + ID + ",\"IsAnchored\":false}");
        check(near(axis.compute(3, 50, 4), 5), "Axis reads the distance from a line through the origin");
        DensityFunction shell = d("{\"Type\":\"Shell\",\"Axis\":" + Y + ",\"Mirror\":false,\"AngleCurve\":{\"Type\":\"Constant\",\"Value\":1},\"DistanceCurve\":" + ID + "}");
        check(near(shell.compute(3, 4, 0), 5), "Shell multiplies the angle and distance curves");

        DensityFunction rot = d("{\"Type\":\"Rotator\",\"NewYAxis\":{\"X\":1,\"Y\":0,\"Z\":0},\"SpinAngle\":0,\"Inputs\":[{\"Type\":\"YValue\"}]}");
        check(near(rot.compute(3, 0, 0), 3) && near(rot.compute(0, 4, 0), 0), "Rotator aligns the field's Y axis with NewYAxis");
        DensityFunction spun = d("{\"Type\":\"Rotator\",\"NewYAxis\":" + Y + ",\"SpinAngle\":90,\"Inputs\":[{\"Type\":\"XValue\"}]}");
        check(Math.abs(Math.abs(spun.compute(3, 0, 0)) - 0) < 1e-3 && Math.abs(Math.abs(spun.compute(0, 0, 3)) - 3) < 1e-3, "Rotator spins the field around the new axis by SpinAngle degrees");

        DensityFunction trig = d("{\"Type\":\"Trig\",\"Function\":\"Sin\",\"InputScale\":0.5,\"Inputs\":[{\"Type\":\"XValue\"}]}");
        check(near(trig.compute(0, 0, 0), 0) && near(trig.compute(3.14159265f, 0, 0), 1), "Trig reads the input in radians after InputScale");
        check(near(d("{\"Type\":\"Trig\",\"Function\":\"Asin\",\"InputScale\":1,\"Inputs\":[{\"Type\":\"Constant\",\"Value\":5}]}").compute(0, 0, 0), Math.PI / 2) && Float.isFinite(d("{\"Type\":\"Trig\",\"Function\":\"Tan\",\"InputScale\":1,\"Inputs\":[{\"Type\":\"Constant\",\"Value\":1.5707964}]}").compute(0, 0, 0)),
                "Trig stays finite: Asin clamps its input, Tan clamps near asymptotes");

        DensityFunction vw = d("{\"Type\":\"VectorWarp\",\"WarpFactor\":2,\"WarpVector\":{\"X\":1,\"Y\":0,\"Z\":0},\"Inputs\":[{\"Type\":\"XValue\"},{\"Type\":\"Constant\",\"Value\":0.5}]}");
        check(near(vw.compute(3, 0, 0), 4), "VectorWarp shifts by WarpFactor times the warp field along WarpVector (2 x 0.5 = 1 block)");
        DensityFunction gw = d("{\"Type\":\"GradientWarp\",\"SampleRange\":1,\"WarpFactor\":2,\"2D\":true,\"YFor2D\":0,\"Inputs\":[{\"Type\":\"XValue\"},{\"Type\":\"XValue\"}]}");
        check(near(gw.compute(3, 7, 0), 5), "GradientWarp moves the sample along the warp source's gradient (gradient 1 x factor 2)");
        DensityFunction fw = d("{\"Type\":\"FastGradientWarp\",\"WarpScale\":30,\"WarpLacunarity\":2,\"WarpPersistence\":0.5,\"WarpOctaves\":2,\"WarpFactor\":5,\"Inputs\":[{\"Type\":\"XValue\"}]}");
        boolean bounded = true, moved = false;
        for (int i = 0; i < 200; i++) { float v = fw.compute(i, i * 0.3f, i * 0.7f); if (Math.abs(v - i) > 5.0 * 1.01) bounded = false; if (Math.abs(v - i) > 0.01) moved = true; }
        check(bounded && moved && fw.compute(10, 5, 5) == fw.compute(10, 5, 5), "FastGradientWarp warps by at most WarpFactor blocks, deterministically");

        String cases = "{\"Type\":\"Switch\",\"SwitchCases\":[{\"CaseState\":\"Default\",\"Density\":{\"Type\":\"Constant\",\"Value\":5}},{\"CaseState\":\"A\",\"Density\":{\"Type\":\"Constant\",\"Value\":8}}]}";
        check(near(d(cases).compute(0, 0, 0), 5), "Switch uses the Default case when no SwitchState is set");
        check(near(d("{\"Type\":\"SwitchState\",\"SwitchState\":\"A\",\"Inputs\":[" + cases + "]}").compute(0, 0, 0), 8), "SwitchState sets the state its branch is evaluated under");
        check(near(d("{\"Type\":\"Sum\",\"Inputs\":[{\"Type\":\"SwitchState\",\"SwitchState\":\"A\",\"Inputs\":[" + cases + "]}," + cases + "]}").compute(0, 0, 0), 13), "the state applies only downstream of the SwitchState node");

        DensityFunction cell = d("{\"Type\":\"CellNoise2D\",\"Jitter\":0.3,\"CellType\":\"CellValue\",\"ScaleX\":50,\"ScaleZ\":50,\"Octaves\":1,\"Seed\":\"a\"}");
        boolean in = true, varies = false;
        for (int i = 0; i < 500; i++) { float v = cell.compute(i * 7.3f, 0, i * 3.1f); if (v < -1.001 || v > 1.001) in = false; if (Math.abs(v - cell.compute(0, 0, 0)) > 0.01) varies = true; }
        check(in && varies && cell.compute(0, 0, 0) == cell.compute(1, 0, 1), "CellNoise2D CellValue stays within [-1, 1], varies between cells and is constant inside one");

        // ---- PositionsCellNoise: Hytale's ReturnType / DistanceFunction / MaxDistance form
        String two = "{\"Type\":\"List\",\"Positions\":[{\"X\":0,\"Y\":0,\"Z\":0},{\"X\":20,\"Y\":0,\"Z\":0}]}";
        String euclid = "\"DistanceFunction\":{\"Type\":\"Euclidean\"}";
        java.util.function.Function<String, DensityFunction> pcn = rt -> d("{\"Type\":\"PositionsCellNoise\",\"Positions\":" + two + ",\"ReturnType\":" + rt + "," + euclid + ",\"MaxDistance\":30}");
        DensityFunction pd = pcn.apply("{\"Type\":\"Distance\"}");
        check(near(pd.compute(0, 0, 0), -1) && near(pd.compute(5, 0, 0), 2 * 5.0 / 30 - 1) && pd.compute(10, 0, 0) > pd.compute(5, 0, 0), "PositionsCellNoise Distance: lowest at each Position, rising towards the cell edge");
        check(near(pcn.apply("{\"Type\":\"Distance2\"}").compute(5, 0, 0), 2 * 15.0 / 30 - 1) && near(pcn.apply("{\"Type\":\"Distance2Sub\"}").compute(5, 0, 0), 2 * (15.0 - 5) / 30 - 1), "Distance2 reads the second-nearest Position; Distance2Sub the difference");
        DensityFunction man = d("{\"Type\":\"PositionsCellNoise\",\"Positions\":" + two + ",\"ReturnType\":{\"Type\":\"Distance\"},\"DistanceFunction\":{\"Type\":\"Manhattan\"},\"MaxDistance\":30}");
        check(near(man.compute(5, 4, 3), 2 * 12.0 / 30 - 1) && near(pd.compute(5, 4, 3), 2 * Math.sqrt(50) / 30 - 1), "the DistanceFunction decides how distance is measured (Manhattan 12 vs Euclidean 7.07)");
        DensityFunction cv = pcn.apply("{\"Type\":\"CellValue\",\"Density\":{\"Type\":\"XValue\"},\"DefaultValue\":-7}");
        check(near(cv.compute(5, 0, 0), 0) && near(cv.compute(17, 0, 0), 20) && near(d("{\"Type\":\"PositionsCellNoise\",\"Positions\":" + two + ",\"ReturnType\":{\"Type\":\"CellValue\",\"Density\":{\"Type\":\"XValue\"},\"DefaultValue\":-7}," + euclid + ",\"MaxDistance\":12}").compute(40, 0, 0), -7),
                "CellValue: the Density sampled at the cell's core is constant in the cell; DefaultValue outside MaxDistance");
        check(near(pcn.apply("{\"Type\":\"Curve\",\"Curve\":" + ID + "}").compute(5, 0, 0), 5), "Curve: the curve of the distance in blocks to the nearest Position");
        String dens = "{\"Type\":\"Density\",\"ChoiceDensity\":{\"Type\":\"XValue\"},\"Delimiters\":[{\"Min\":-1,\"Max\":10,\"Density\":{\"Type\":\"Constant\",\"Value\":1}},{\"Min\":10,\"Max\":30,\"Density\":{\"Type\":\"Constant\",\"Value\":2}}],\"DefaultValue\":-9}";
        DensityFunction dd = pcn.apply(dens);
        check(near(dd.compute(5, 0, 0), 1) && near(dd.compute(17, 0, 0), 2), "Density: each cell takes the delimiter its ChoiceDensity (at the core) falls in");
        DensityFunction anch = pcn.apply("{\"Type\":\"Density\",\"ChoiceDensity\":{\"Type\":\"Constant\",\"Value\":0},\"Delimiters\":[{\"Min\":-1,\"Max\":1,\"Density\":{\"Type\":\"Anchor\",\"Reverse\":false,\"Inputs\":[{\"Type\":\"XValue\"}]}}],\"DefaultValue\":-9}");
        check(near(anch.compute(4, 0, 0), 4) && near(anch.compute(24, 0, 0), 4), "Anchor puts the child field's origin at the cell core");
        DensityFunction wall = pcn.apply("{\"Type\":\"Density\",\"ChoiceDensity\":{\"Type\":\"Constant\",\"Value\":0},\"Delimiters\":[{\"Min\":-1,\"Max\":1,\"Density\":{\"Type\":\"CellWallDistance\"}}],\"DefaultValue\":-9}");
        check(near(wall.compute(5, 0, 0), 5) && near(wall.compute(10, 0, 0), 0) && d("{\"Type\":\"CellWallDistance\"}").compute(0, 0, 0) > 1e30f, "CellWallDistance is the distance to the nearest cell wall inside a Density cell, and huge elsewhere");
        System.out.println("Phase 30 verification: ALL " + passed + " TESTS PASSED!");
    }
}