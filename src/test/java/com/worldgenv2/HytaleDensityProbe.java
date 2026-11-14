package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dev probe: Hytale-format density nodes, evaluated at (3, 70, 5). Run with gradle probeHytaleDensity. */
public final class HytaleDensityProbe {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) { return (Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static String c(double v) { return "{\"Type\":\"Constant\",\"Value\":" + v + "}"; }

    private static String in(String type, String params, String... inputs) {
        StringBuilder sb = new StringBuilder("{\"Type\":\"" + type + "\"");
        if (!params.isEmpty()) sb.append(",").append(params);
        if (inputs.length > 0) sb.append(",\"Inputs\":[").append(String.join(",", inputs)).append("]");
        return sb.append("}").toString();
    }

    public static void main(String[] args) {
        JsonAstCompiler.registerBuiltInTypes();
        Map<String, String> n = new LinkedHashMap<>();
        n.put("Constant", c(2.5));
        n.put("SimplexNoise2D", in("SimplexNoise2D", "\"Lacunarity\":2.0,\"Persistence\":0.5,\"Scale\":50,\"Octaves\":4,\"Seed\":\"a\""));
        n.put("SimplexNoise3D", in("SimplexNoise3D", "\"Lacunarity\":2.0,\"Persistence\":0.5,\"ScaleXZ\":50,\"ScaleY\":30,\"Octaves\":4,\"Seed\":\"a\""));
        n.put("WhiteNoise", in("WhiteNoise", "\"Seed\":\"a\""));
        n.put("CellNoise2D", in("CellNoise2D", "\"Jitter\":1.0,\"CellType\":\"Distance\",\"ScaleX\":30,\"ScaleZ\":30,\"Octaves\":1,\"Seed\":\"a\""));
        n.put("CellNoise3D", in("CellNoise3D", "\"Jitter\":1.0,\"CellType\":\"Distance\",\"ScaleX\":30,\"ScaleY\":30,\"ScaleZ\":30,\"Octaves\":1,\"Seed\":\"a\""));
        n.put("Distance", in("Distance", "\"Curve\":{\"Type\":\"Linear\"}"));
        n.put("Ellipsoid", in("Ellipsoid", "\"Scale\":{\"X\":10,\"Y\":10,\"Z\":10},\"Curve\":{\"Type\":\"Linear\"}"));
        n.put("Cube", in("Cube", "\"Curve\":{\"Type\":\"Linear\"}"));
        n.put("Plane", in("Plane", "\"PlaneNormal\":{\"X\":0,\"Y\":1,\"Z\":0},\"Curve\":{\"Type\":\"Linear\"}"));
        n.put("Mix", in("Mix", "", c(1), c(3), c(0.25)));
        n.put("Sum", in("Sum", "", c(2), c(3)));
        n.put("Multiplier", in("Multiplier", "", c(2), c(3)));
        n.put("Max", in("Max", "", c(2), c(3)));
        n.put("Min", in("Min", "", c(2), c(3)));
        n.put("SmoothMax", in("SmoothMax", "\"Range\":1.0", c(2), c(3)));
        n.put("SmoothMin", in("SmoothMin", "\"Range\":1.0", c(2), c(3)));
        n.put("Clamp", in("Clamp", "\"WallA\":0,\"WallB\":1", c(5)));
        n.put("SmoothClamp", in("SmoothClamp", "\"WallA\":0,\"WallB\":1,\"Range\":0.5", c(5)));
        n.put("Comparator", in("Comparator", "", c(2), c(3)));
        n.put("Equal", in("Equal", "\"Epsilon\":0.001", c(2), c(2)));
        n.put("GreaterThan", in("GreaterThan", "", c(3), c(2)));
        n.put("GreaterOrEqual", in("GreaterOrEqual", "", c(3), c(3)));
        n.put("LessThan", in("LessThan", "", c(2), c(3)));
        n.put("LessOrEqual", in("LessOrEqual", "", c(3), c(3)));
        n.put("And", in("And", "", c(1), c(1)));
        n.put("Or", in("Or", "", c(0), c(1)));
        n.put("Not", in("Not", "", c(1)));
        n.put("Nor", in("Nor", "", c(0), c(0)));
        n.put("Xor", in("Xor", "", c(1), c(0)));
        n.put("Selector", in("Selector", "", c(1), c(7), c(9)));
        n.put("Abs", in("Abs", "", c(-4)));
        n.put("XValue", in("XValue", ""));
        n.put("YValue", in("YValue", ""));
        n.put("ZValue", in("ZValue", ""));
        n.put("XOverride", in("XOverride", "", c(1), c(9)));
        n.put("YOverride", in("YOverride", "", c(1), c(9)));
        n.put("ZOverride", in("ZOverride", "", c(1), c(9)));
        n.put("YSampled", in("YSampled", "\"SampleDistance\":4,\"SampleOffset\":0,\"Interpolate\":true", in("YValue", "")));
        n.put("Cache", in("Cache", "\"Capacity\":4", c(3)));
        n.put("Sqrt", in("Sqrt", "", c(16)));
        n.put("Inverter", in("Inverter", "", c(3)));
        n.put("Pow", in("Pow", "\"Exponent\":2", c(3)));
        n.put("Trig", in("Trig", "\"Function\":\"Sin\",\"InputScale\":1", c(0)));
        n.put("Scale", in("Scale", "\"X\":2,\"Y\":2,\"Z\":2", in("XValue", "")));
        n.put("Rotator", in("Rotator", "\"NewYAxis\":{\"X\":0,\"Y\":1,\"Z\":0},\"SpinAngle\":0", in("XValue", "")));
        n.put("Slider", in("Slider", "\"SlideX\":1,\"SlideY\":0,\"SlideZ\":0", in("XValue", "")));
        n.put("Anchor", in("Anchor", "\"Reverse\":false", c(3)));
        n.put("Normalizer", in("Normalizer", "\"FromMin\":0,\"FromMax\":10,\"ToMin\":0,\"ToMax\":1", c(5)));
        n.put("BaseHeight", in("BaseHeight", "\"BaseHeightName\":\"Base\",\"Distance\":false"));
        n.put("CurveMapper", in("CurveMapper", "\"Curve\":{\"Type\":\"Constant\",\"Value\":0.5}", c(5)));
        for (var e : n.entrySet()) {
            try {
                DensityFunction d = JsonAstCompiler.compileDensity(parse(e.getValue()), new JsonAstCompiler.CompileContext(1L));
                System.out.println("DENSITY " + e.getKey() + ": " + d.compute(3f, 70f, 5f));
            } catch (Throwable t) {
                System.out.println("DENSITY " + e.getKey() + ": FAIL " + t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage()).split("\n")[0].substring(0, Math.min(100, String.valueOf(t.getMessage()).split("\n")[0].length())));
            }
        }
    }
}
