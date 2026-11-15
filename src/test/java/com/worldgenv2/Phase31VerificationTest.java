package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;

import java.util.Map;

/** Phase 31 verification: Hytale's math, comparison and logic densities with their documented edge cases. */
public final class Phase31VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    @SuppressWarnings("unchecked")
    private static float v(String type, String params, double... in) {
        StringBuilder sb = new StringBuilder("{\"Type\":\"" + type + "\"");
        if (!params.isEmpty()) sb.append(",").append(params);
        if (in.length > 0) {
            sb.append(",\"Inputs\":[");
            for (int i = 0; i < in.length; i++) sb.append(i > 0 ? "," : "").append("{\"Type\":\"Constant\",\"Value\":").append(in[i]).append("}");
            sb.append("]");
        }
        sb.append("}");
        return JsonAstCompiler.compileDensity((Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(sb.toString()).parseValue(), new JsonAstCompiler.CompileContext(1L)).compute(0, 0, 0);
    }

    private static boolean is(float a, double b) { return Math.abs(a - b) < 1e-4; }

    public static void main(String[] args) {
        System.out.println("=== Phase 31 Verification Test Suite: math, comparison and logic densities ===");
        JsonAstCompiler.registerBuiltInTypes();

        check(is(v("Sum", "", 1, 2, 3), 6) && is(v("Sum", ""), 0), "Sum adds all inputs; no inputs gives 0");
        check(is(v("Max", "", 1, 5, 3), 5) && is(v("Max", ""), 0) && is(v("Min", "", 4, -2, 3), -2) && is(v("Min", ""), 0), "Max and Min take the extreme input; no inputs gives 0");
        check(is(v("Abs", "", -4), 4), "Abs");
        float sq = v("Sqrt", "", -9);
        check(is(v("Sqrt", "", 9), 3) && Float.isFinite(sq), "Sqrt of a negative input is a finite, modified value");
        check(is(v("Clamp", "\"WallA\":5,\"WallB\":1", 9), 5) && is(v("Clamp", "\"WallA\":5,\"WallB\":1", -3), 1) && is(v("Clamp", "\"WallA\":5,\"WallB\":1", 3), 3), "Clamp: walls in either order; inside values are unchanged");
        check(is(v("Mix", "", 10, 20, -1), 10) && is(v("Mix", "", 10, 20, 2), 20) && is(v("Mix", "", 10, 20, 0.5), 15), "Mix: gauge <= 0 is all A, >= 1 is all B, between is proportional");

        check(is(v("Comparator", "", 1, 2), -1) && is(v("Comparator", "", 2, 2), 0) && is(v("Comparator", "", 3, 2), 1), "Comparator gives -1, 0 or 1");
        check(is(v("Equal", "\"Epsilon\":0.5", 1, 1.4), 1) && is(v("Equal", "\"Epsilon\":0.5", 1, 1.6), 0) && is(v("Equal", "\"Epsilon\":0", 2, 2), 1), "Equal counts inputs within Epsilon as equal");
        check(is(v("GreaterThan", "", 3, 2), 1) && is(v("GreaterThan", "", 2, 2), 0) && is(v("GreaterOrEqual", "", 2, 2), 1) && is(v("LessThan", "", 1, 2), 1) && is(v("LessThan", "", 2, 2), 0) && is(v("LessOrEqual", "", 2, 2), 1), "the ordered comparisons output 1 or 0");

        check(is(v("And", "", 1, 2, 3), 1) && is(v("And", "", 1, 0), 0) && is(v("And", ""), 0), "And: every input non-zero; no inputs is 0");
        check(is(v("Or", "", 0, 0, 2), 1) && is(v("Or", "", 0, 0), 0) && is(v("Or", ""), 0), "Or: any input non-zero; no inputs is 0");
        check(is(v("Not", "", 0), 1) && is(v("Not", "", 5), 0), "Not");
        check(is(v("Nor", "", 0, 0), 1) && is(v("Nor", "", 0, 1), 0) && is(v("Nor", ""), 1), "Nor: every input 0; no inputs is 1");
        check(is(v("Xor", "", 1, 0), 1) && is(v("Xor", "", 1, 1), 0) && is(v("Xor", "", 1, 1, 1), 1), "Xor: an odd number of non-zero inputs");

        System.out.println("Phase 31 verification: ALL " + passed + " TESTS PASSED!");
    }
}