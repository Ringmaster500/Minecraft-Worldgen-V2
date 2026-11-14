package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;

import java.util.Map;

public final class HytaleShapeProbe {
    static final String ID = "{\"Type\":\"Manual\",\"Points\":[{\"X\":-100,\"Y\":-100,\"Z\":0},{\"X\":100,\"Y\":100,\"Z\":0}]}";
    static DensityFunction d(String j) { return JsonAstCompiler.compileDensity((Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(j).parseValue(), new JsonAstCompiler.CompileContext(1L)); }
    static void show(String name, String json, float[][] pts) {
        try {
            DensityFunction f = d(json);
            StringBuilder sb = new StringBuilder();
            for (float[] p : pts) sb.append(String.format("(%.0f,%.0f,%.0f)=%.3f ", p[0], p[1], p[2], f.compute(p[0], p[1], p[2])));
            System.out.println("SHAPE " + name + ": " + sb);
        } catch (Throwable t) { System.out.println("SHAPE " + name + ": FAIL " + t); }
    }
    public static void main(String[] a) {
        JsonAstCompiler.registerBuiltInTypes();
        float[][] pts = {{3, 0, 0}, {0, 4, 0}, {0, 0, 5}, {3, 4, 0}, {-3, 0, 0}, {10, 10, 10}};
        show("Distance", "{\"Type\":\"Distance\",\"Curve\":" + ID + "}", pts);
        show("Ellipsoid scale2x", "{\"Type\":\"Ellipsoid\",\"Curve\":" + ID + ",\"Scale\":{\"X\":2,\"Y\":1,\"Z\":1},\"NewYAxis\":{\"X\":0,\"Y\":1,\"Z\":0},\"Spin\":0}", pts);
        show("Cube", "{\"Type\":\"Cube\",\"Curve\":" + ID + "}", pts);
        show("Cuboid scale2x", "{\"Type\":\"Cuboid\",\"Curve\":" + ID + ",\"Scale\":{\"X\":2,\"Y\":1,\"Z\":1},\"NewYAxis\":{\"X\":0,\"Y\":1,\"Z\":0},\"Spin\":0}", pts);
        show("Cylinder", "{\"Type\":\"Cylinder\",\"AxialCurve\":" + ID + ",\"RadialCurve\":" + ID + ",\"Spin\":0,\"NewYAxis\":{\"X\":0,\"Y\":1,\"Z\":0}}", pts);
        show("Axis Y", "{\"Type\":\"Axis\",\"Axis\":{\"X\":0,\"Y\":1,\"Z\":0},\"Curve\":" + ID + ",\"IsAnchored\":false}", pts);
        show("Plane up", "{\"Type\":\"Plane\",\"PlaneNormal\":{\"X\":0,\"Y\":1,\"Z\":0},\"Curve\":" + ID + "}", pts);
        show("Shell", "{\"Type\":\"Shell\",\"Axis\":{\"X\":0,\"Y\":1,\"Z\":0},\"Mirror\":false,\"AngleCurve\":{\"Type\":\"Constant\",\"Value\":1},\"DistanceCurve\":" + ID + "}", pts);
        show("CellNoise2D", "{\"Type\":\"CellNoise2D\",\"Jitter\":0.3,\"CellType\":\"CellValue\",\"ScaleX\":50,\"ScaleZ\":50,\"Octaves\":1,\"Seed\":\"a\"}", new float[][]{{0, 0, 0}, {10, 0, 10}, {200, 0, 5}, {777, 0, 123}});
        show("CellNoise2D dist", "{\"Type\":\"CellNoise2D\",\"Jitter\":0.3,\"CellType\":\"Distance\",\"ScaleX\":50,\"ScaleZ\":50,\"Octaves\":1,\"Seed\":\"a\"}", new float[][]{{0, 0, 0}, {10, 0, 10}, {200, 0, 5}, {777, 0, 123}});
        show("CellNoise3D", "{\"Type\":\"CellNoise3D\",\"Jitter\":0.3,\"CellType\":\"CellValue\",\"ScaleX\":50,\"ScaleY\":50,\"ScaleZ\":50,\"Octaves\":1,\"Seed\":\"a\"}", new float[][]{{0, 0, 0}, {10, 5, 10}, {200, 30, 5}});
        String pts2 = "{\"Type\":\"List\",\"Positions\":[{\"X\":0,\"Y\":0,\"Z\":0},{\"X\":20,\"Y\":0,\"Z\":0}]}";
        float[][] cp = {{0, 0, 0}, {5, 0, 0}, {10, 0, 0}, {15, 0, 0}, {20, 0, 0}, {40, 0, 0}, {5, 4, 3}};
        show("PCN Distance", "{\"Type\":\"PositionsCellNoise\",\"Positions\":" + pts2 + ",\"ReturnType\":{\"Type\":\"Distance\"},\"DistanceFunction\":{\"Type\":\"Euclidean\"},\"MaxDistance\":30}", cp);
        show("PCN Distance2", "{\"Type\":\"PositionsCellNoise\",\"Positions\":" + pts2 + ",\"ReturnType\":{\"Type\":\"Distance2\"},\"DistanceFunction\":{\"Type\":\"Euclidean\"},\"MaxDistance\":30}", cp);
        show("PCN Manhattan", "{\"Type\":\"PositionsCellNoise\",\"Positions\":" + pts2 + ",\"ReturnType\":{\"Type\":\"Distance\"},\"DistanceFunction\":{\"Type\":\"Manhattan\"},\"MaxDistance\":30}", cp);
        show("PCN CellValue", "{\"Type\":\"PositionsCellNoise\",\"Positions\":" + pts2 + ",\"ReturnType\":{\"Type\":\"CellValue\",\"Density\":{\"Type\":\"XValue\"},\"DefaultValue\":-7},\"DistanceFunction\":{\"Type\":\"Euclidean\"},\"MaxDistance\":12}", cp);
        show("PCN Curve", "{\"Type\":\"PositionsCellNoise\",\"Positions\":" + pts2 + ",\"ReturnType\":{\"Type\":\"Curve\",\"Curve\":" + ID + "},\"DistanceFunction\":{\"Type\":\"Euclidean\"},\"MaxDistance\":30}", cp);
        show("PCN Density", "{\"Type\":\"PositionsCellNoise\",\"Positions\":" + pts2 + ",\"ReturnType\":{\"Type\":\"Density\",\"ChoiceDensity\":{\"Type\":\"XValue\"},\"Delimiters\":[{\"Min\":-1,\"Max\":10,\"Density\":{\"Type\":\"Constant\",\"Value\":1}},{\"Min\":10,\"Max\":30,\"Density\":{\"Type\":\"Constant\",\"Value\":2}}],\"DefaultValue\":-9},\"DistanceFunction\":{\"Type\":\"Euclidean\"},\"MaxDistance\":30}", cp);        show("Rotator X->Y", "{\"Type\":\"Rotator\",\"NewYAxis\":{\"X\":1,\"Y\":0,\"Z\":0},\"SpinAngle\":0,\"Inputs\":[{\"Type\":\"YValue\"}]}", pts);
        show("Trig", "{\"Type\":\"Trig\",\"Function\":\"Sin\",\"InputScale\":0.5,\"Inputs\":[{\"Type\":\"XValue\"}]}", new float[][]{{0, 0, 0}, {3.14159f, 0, 0}});
        show("VectorWarp", "{\"Type\":\"VectorWarp\",\"WarpFactor\":2,\"WarpVector\":{\"X\":1,\"Y\":0,\"Z\":0},\"Inputs\":[{\"Type\":\"XValue\"},{\"Type\":\"Constant\",\"Value\":0.5}]}", pts);
        show("GradientWarp", "{\"Type\":\"GradientWarp\",\"SampleRange\":1,\"WarpFactor\":2,\"2D\":true,\"YFor2D\":0,\"Inputs\":[{\"Type\":\"XValue\"},{\"Type\":\"XValue\"}]}", pts);
        show("Switch Default", "{\"Type\":\"Switch\",\"SwitchCases\":[{\"CaseState\":\"Default\",\"Density\":{\"Type\":\"Constant\",\"Value\":5}},{\"CaseState\":\"A\",\"Density\":{\"Type\":\"Constant\",\"Value\":8}}]}", new float[][]{{0, 0, 0}});
        show("SwitchState A", "{\"Type\":\"SwitchState\",\"SwitchState\":\"A\",\"Inputs\":[{\"Type\":\"Switch\",\"SwitchCases\":[{\"CaseState\":\"Default\",\"Density\":{\"Type\":\"Constant\",\"Value\":5}},{\"CaseState\":\"A\",\"Density\":{\"Type\":\"Constant\",\"Value\":8}}]}]}", new float[][]{{0, 0, 0}});
        show("FastGradientWarp", "{\"Type\":\"FastGradientWarp\",\"WarpScale\":30,\"WarpLacunarity\":2,\"WarpPersistence\":0.5,\"WarpOctaves\":2,\"WarpFactor\":5,\"Inputs\":[{\"Type\":\"XValue\"}]}", pts);    }
}