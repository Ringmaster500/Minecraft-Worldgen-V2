package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;

import java.util.Map;

/** Phase 22 verification: Hytale density nodes (math, remapping, coordinate and noise nodes). */
public final class Phase22VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    @SuppressWarnings("unchecked")
    private static DensityFunction d(String json, JsonAstCompiler.CompileContext ctx) {
        return JsonAstCompiler.compileDensity((Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(json).parseValue(), ctx);
    }

    private static DensityFunction d(String json) { return d(json, new JsonAstCompiler.CompileContext(1L)); }

    private static String c(double v) { return "{\"Type\":\"Constant\",\"Value\":" + v + "}"; }

    private static String n(String type, String params, String... in) {
        return "{\"Type\":\"" + type + "\"" + (params.isEmpty() ? "" : "," + params) + (in.length == 0 ? "" : ",\"Inputs\":[" + String.join(",", in) + "]") + "}";
    }

    private static float at(String json, float x, float y, float z) { return d(json).compute(x, y, z); }

    public static void main(String[] args) {
        System.out.println("=== Phase 22 Verification Test Suite: Hytale density nodes ===");
        JsonAstCompiler.registerBuiltInTypes();

        check(at(n("Multiplier", "", c(2), c(3), c(4)), 0, 0, 0) == 24f, "Multiplier multiplies all its inputs");
        check(at(n("Multiplier", "", c(0), n("Selector", "", c(1), c(1), c(1))), 0, 0, 0) == 0f, "Multiplier yields 0 as soon as an input is 0");
        check(at(n("Inverter", "", c(3)), 0, 0, 0) == -3f, "Inverter multiplies by -1");
        check(Math.abs(at(n("SmoothMax", "\"Range\":2", c(1), c(1)), 0, 0, 0) - 1.5f) < 1e-5 && at(n("SmoothMax", "\"Range\":1", c(2), c(5)), 0, 0, 0) == 5f,
                "SmoothMax is a maximum with a rounded join (equal inputs rise above the max, distant ones match it)");
        check(Math.abs(at(n("SmoothMin", "\"Range\":2", c(1), c(1)), 0, 0, 0) - 0.5f) < 1e-5 && at(n("SmoothMin", "\"Range\":1", c(2), c(5)), 0, 0, 0) == 2f, "SmoothMin mirrors it");
        check(at(n("SmoothClamp", "\"WallA\":0,\"WallB\":1,\"Range\":0.2", c(50)), 0, 0, 0) == 1f && at(n("SmoothClamp", "\"WallA\":0,\"WallB\":1,\"Range\":0.2", c(-50)), 0, 0, 0) == 0f
                && Math.abs(at(n("SmoothClamp", "\"WallA\":0,\"WallB\":1,\"Range\":0.2", c(0.5)), 0, 0, 0) - 0.5f) < 0.05f, "SmoothClamp keeps the range, approached smoothly");
        check(at(n("Normalizer", "\"FromMin\":0,\"FromMax\":10,\"ToMin\":-1,\"ToMax\":1", c(5)), 0, 0, 0) == 0f && at(n("Normalizer", "\"FromMin\":0,\"FromMax\":10,\"ToMin\":-1,\"ToMax\":1", c(10)), 0, 0, 0) == 1f,
                "Normalizer linearly remaps the input range");
        check(at(n("Scale", "\"X\":2,\"Y\":1,\"Z\":1", n("XValue", "")), 8, 0, 0) == 4f && at(n("Scale", "\"X\":-1,\"Y\":1,\"Z\":1", n("XValue", "")), 8, 0, 0) == -8f,
                "Scale stretches the field (above 1) and flips it (below 0)");
        check(at(n("Slider", "\"SlideX\":3,\"SlideY\":0,\"SlideZ\":0", n("XValue", "")), 10, 0, 0) == 7f, "Slider slides the field along its vector");
        check(at(n("Selector", "", c(7), c(9), c(1)), 0, 0, 0) == 7f && at(n("Selector", "", c(7), c(9), c(0)), 0, 0, 0) == 9f, "Selector: Then when the condition is non-zero, otherwise Else (inputs are Then, Else, Condition)");
        check(at(n("Selector", "", c(7), c(9)), 0, 0, 0) == 0f, "Selector with the wrong number of inputs outputs 0");

        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L);
        ctx.decimalConstants().put("Base", 100.0);
        check(d(n("BaseHeight", "\"BaseHeightName\":\"Base\",\"Distance\":false"), ctx).compute(0, 40, 0) == 100f, "BaseHeight outputs the named constant's Y");
        check(d(n("BaseHeight", "\"BaseHeightName\":\"Base\",\"Distance\":true"), ctx).compute(0, 120, 0) == 20f && d(n("BaseHeight", "\"BaseHeightName\":\"Base\",\"Distance\":true"), ctx).compute(0, 90, 0) == -10f,
                "BaseHeight with Distance on is positive above the height and negative below it");

        // seeds are strings
        String noiseA = n("SimplexNoise2D", "\"Lacunarity\":2,\"Persistence\":0.5,\"Scale\":50,\"Octaves\":3,\"Seed\":\"alpha\"");
        String noiseB = n("SimplexNoise2D", "\"Lacunarity\":2,\"Persistence\":0.5,\"Scale\":50,\"Octaves\":3,\"Seed\":\"beta\"");
        check(at(noiseA, 13.3f, 0, 7.1f) == at(noiseA, 13.3f, 0, 7.1f), "noise is deterministic for a given seed");
        boolean differs = false;
        for (int i = 0; i < 10; i++) if (at(noiseA, i * 9.7f, 0, i * 3.3f) != at(noiseB, i * 9.7f, 0, i * 3.3f)) differs = true;
        check(differs, "a different Seed string gives a different noise field");
        // Scale is the period in blocks: a larger scale varies more slowly
        String slow = n("SimplexNoise2D", "\"Lacunarity\":2,\"Persistence\":0.5,\"Scale\":400,\"Octaves\":1,\"Seed\":\"s\"");
        String fast = n("SimplexNoise2D", "\"Lacunarity\":2,\"Persistence\":0.5,\"Scale\":10,\"Octaves\":1,\"Seed\":\"s\"");
        double slowVar = 0, fastVar = 0;
        for (int i = 0; i < 100; i++) { slowVar += Math.abs(at(slow, i, 0, 5) - at(slow, i + 1, 0, 5)); fastVar += Math.abs(at(fast, i, 0, 5) - at(fast, i + 1, 0, 5)); }
        check(fastVar > slowVar * 5, "SimplexNoise2D's Scale is the period in blocks (Scale 10 varies much faster than Scale 400)");
        double mx = -9, mn = 9;
        for (int i = 0; i < 2000; i++) { float v = at(noiseA, i * 3.7f, 0, i * 1.9f); mx = Math.max(mx, v); mn = Math.min(mn, v); }
        check(mx <= 1.0001 && mn >= -1.0001 && mx > 0.2 && mn < -0.2, "noise stays within [-1, 1] and uses the range");
        check(at(n("SimplexNoise3D", "\"Lacunarity\":2,\"Persistence\":0.5,\"ScaleXZ\":50,\"ScaleY\":10,\"Octaves\":1,\"Seed\":\"s\""), 1, 2, 3) != 0f, "SimplexNoise3D loads with separate XZ and Y scales");

        check(Math.abs(at("{\"Type\":\"Angle\",\"Vector\":{\"X\":1,\"Y\":0,\"Z\":0},\"VectorProvider\":{\"Type\":\"Constant\",\"Vector\":{\"X\":0,\"Y\":1,\"Z\":0}}}", 0, 0, 0) - 90f) < 1e-3
                && Math.abs(at("{\"Type\":\"Angle\",\"Vector\":{\"X\":1,\"Y\":0,\"Z\":0},\"VectorProvider\":{\"Type\":\"Constant\",\"Vector\":{\"X\":-2,\"Y\":0,\"Z\":0}}}", 0, 0, 0) - 180f) < 1e-3, "Angle gives the degrees between a reference vector and a provided vector");
        JsonAstCompiler.CompileContext tctx = new JsonAstCompiler.CompileContext(1L);
        tctx.setTerrain((x, y, z) -> y * 2f);
        check(d("{\"Type\":\"Terrain\"}", tctx).compute(0, 10, 0) == 20f, "Terrain reads the world's terrain density (for material providers)");
        System.out.println("Phase 22 verification: ALL " + passed + " TESTS PASSED!");
    }
}
