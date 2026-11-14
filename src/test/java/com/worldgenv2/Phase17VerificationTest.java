package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;

/** Phase 17: reading Hytale-style asset JSON (PascalCase keys) through the engine's compiler. */
public final class Phase17VerificationTest {
    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static double eval(String json) {
        JsonAstCompiler.registerBuiltInTypes();
        DensityFunction d = new JsonAstCompiler().compileDensityJson(json, 1L);
        return d.compute(3f, 70f, 5f);
    }

    private static boolean fails(String json) {
        try { eval(json); return false; } catch (RuntimeException e) { return true; }
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 17 Verification Test Suite: Hytale-style JSON ===");
        check(eval("{\"type\":\"Constant\",\"value\":2.5}") == 2.5, "engine-style keys still work");
        check(eval("{\"Type\":\"Constant\",\"Value\":2.5}") == 2.5, "Hytale-style PascalCase keys read the same parameters");
        check(eval("{\"TYPE\":\"constant\",\"VALUE\":2.5}") == 2.5, "keys and type names match without regard to case");
        check(eval("{\"Type\":\"Sum\",\"Inputs\":[{\"Type\":\"Constant\",\"Value\":2},{\"Type\":\"Constant\",\"Value\":3}]}") == 5.0,
                "Hytale's Sum node with an Inputs list");
        check(eval("{\"Type\":\"Max\",\"Inputs\":[{\"Type\":\"Constant\",\"Value\":2},{\"Type\":\"Constant\",\"Value\":3}]}") == 3.0, "Hytale's Max node");
        check(eval("{\"Type\":\"Abs\",\"Inputs\":[{\"Type\":\"Constant\",\"Value\":-4}]}") == 4.0, "Hytale's Abs node");
        System.out.println("Phase 17 verification: ALL " + passed + " TESTS PASSED!");
    }
}
