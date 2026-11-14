package com.worldgenv2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.worldgenv2.placement.Expr;
import com.worldgenv2.placement.Placement;
import com.worldgenv2.placement.Vars;

import java.util.List;
import java.util.Set;

/** Phase 16 verification: placement expressions and the finder / condition / action registry (the addon extension point). */
public final class Phase16VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.err.println("[FAIL] " + what);
            throw new AssertionError(what);
        }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static boolean throwsOnParse(String s) {
        try { Expr.parse(s); return false; } catch (IllegalArgumentException e) { return true; }
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 16 Verification Test Suite: placement expressions and registry ===");

        Vars v = new Vars().set("span.length", 9).set("prefab.length", 15).set("prefab.height", 24).set("log.length", 15);
        check(Expr.parse("1 + 2 * 3").eval(v) == 7, "arithmetic precedence");
        check(Expr.parse("(1 + 2) * 3").eval(v) == 9, "parentheses");
        check(Expr.parse("-span.length + 10").eval(v) == 1, "unary minus and dotted variables");
        check(Expr.parse("prefab.length >= span.length + 2 && prefab.length <= span.length + 16").test(v), "fit expression accepts a log that reaches both banks");
        check(!Expr.parse("prefab.length >= span.length + 12").test(v), "fit expression rejects a log that is too short");
        check(Expr.parse("prefab.height >= log.length").test(v), "tree-height requirement: a 24-block tree can make a 15-block log");
        check(!Expr.parse("prefab.height >= log.length * 2").test(v), "tree-height requirement scales: not tall enough for twice the length");
        check(Expr.parse("max(3, span.length, 5) == 9 and min(3, 4) == 3").test(v), "functions and the word operators");
        check(Expr.parse("not (1 > 2) or 0").test(v), "not / or");
        check(!Expr.parse("missing.value > 0").test(v), "a missing variable makes a comparison false instead of failing");
        check(!Expr.parse("missing.value == missing.value").test(v), "NaN never equals NaN");
        check(Expr.parse("floor(7 / 2) == 3 && ceil(7 / 2) == 4").test(v), "floor and ceil");
        check(Expr.parse("1 / 0").eval(v) != Expr.parse("1 / 0").eval(v), "division by zero is NaN, not an exception");
        check(throwsOnParse("1 +") && throwsOnParse("(1") && throwsOnParse("foo(1)") && throwsOnParse("1 $ 2"), "malformed expressions are rejected with a message");

        check(Placement.has("finder", "Span") && Placement.has("finder", "Point"), "built-in finders are registered");
        check(Placement.has("condition", "Biome") && Placement.has("condition", "Density") && Placement.has("condition", "Expression"), "built-in conditions are registered");
        check(Placement.has("action", "Prefab"), "the Prefab action is registered");
        check(!Placement.has("finder", "Nope"), "unknown types are not reported as registered");

        JsonObject schema = Placement.schemaJson();
        check(schema.getAsJsonObject("finder").has("Span") && schema.getAsJsonObject("action").has("Prefab"), "the registry produces node-schema JSON for the Studio");
        check(schema.getAsJsonObject("condition").getAsJsonObject("Density").getAsJsonArray("slots").size() == 1, "a condition can declare a child node socket (Density graph)");

        // An addon registers a brand-new condition with the same call the built-ins use.
        Placement.registerCondition("AlwaysEvenX", "Test: x must be even", List.of(Placement.param("flip", "bool", false, "Invert")),
                (cfg, ctx) -> {
                    boolean flip = cfg.has("flip") && cfg.get("flip").getAsBoolean();
                    return (env, vars) -> ((((int) vars.get("loc.x")) % 2 == 0) != flip);
                });
        check(Placement.has("condition", "AlwaysEvenX"), "an addon condition registers at runtime");
        check(Placement.schemaJson().getAsJsonObject("condition").has("AlwaysEvenX"), "an addon condition appears in the node schema automatically");
        Placement.Ctx ctx = new Placement.Ctx() {
            public com.worldgenv2.prefab.Prefab prefab(String id) { return null; }
            public Set<String> biomeNames(com.google.gson.JsonArray ids) { return Set.of(); }
            public com.worldgenv2.density.DensityFunction density(JsonObject graph) { return null; }
            public long seed() { return 1; }
            public void warn(String m) { }
        };
        var cond = Placement.createCondition(JsonParser.parseString("{\"type\":\"AlwaysEvenX\"}").getAsJsonObject(), ctx);
        check(cond.test(null, new Vars().set("loc.x", 4)) && !cond.test(null, new Vars().set("loc.x", 5)), "an addon condition built from JSON behaves as written");
        var inverted = Placement.createCondition(JsonParser.parseString("{\"type\":\"AlwaysEvenX\",\"flip\":true}").getAsJsonObject(), ctx);
        check(!inverted.test(null, new Vars().set("loc.x", 4)), "an addon condition reads its own parameters");

        boolean unknownThrows;
        try { Placement.createAction(JsonParser.parseString("{\"type\":\"Bogus\"}").getAsJsonObject(), ctx); unknownThrows = false; }
        catch (IllegalArgumentException e) { unknownThrows = true; }
        check(unknownThrows, "creating an unknown piece fails with a clear error (the compiler turns it into a warning)");

        System.out.println("Phase 16 verification: ALL " + passed + " TESTS PASSED!");
    }
}
