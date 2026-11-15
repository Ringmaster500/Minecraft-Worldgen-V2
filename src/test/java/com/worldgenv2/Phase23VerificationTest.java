package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.material.MaterialProvider;

import java.util.Map;

/** Phase 23 verification: Hytale material providers (Transparent, Queue, SimpleHorizontal, Striped, Weighted, FieldFunction, SpaceAndDepth). */
public final class Phase23VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static final VoxelSpace<String> SPACE = new VoxelSpace<>() {
        public String getBlock(int x, int y, int z) { return "air"; }
        public void setBlock(int x, int y, int z, String b) { }
        public boolean isAir(int x, int y, int z) { return true; }
        public int getMinY() { return 0; }
        public int getMaxY() { return 128; }
    };

    @SuppressWarnings("unchecked")
    private static MaterialProvider<String> p(String json) {
        return JsonAstCompiler.compileMaterialProvider((Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(json).parseValue(), new JsonAstCompiler.CompileContext(1L), id -> id);
    }

    private static String k(String m) { return "{\"Type\":\"Constant\",\"Material\":\"" + m + "\"}"; }

    private static String at(MaterialProvider<String> p, int y, int depth, int space) { return p.getMaterial(0, y, 0, depth, space, 0f, SPACE); }

    public static void main(String[] args) {
        System.out.println("=== Phase 23 Verification Test Suite: Hytale material providers ===");
        JsonAstCompiler.registerBuiltInTypes();

        check(at(p("{\"Type\":\"Transparent\"}"), 60, 0, 5) == null, "Transparent provides no material");
        check("second".equals(at(p("{\"Type\":\"Queue\",\"Queue\":[{\"Type\":\"Transparent\"}," + k("second") + "," + k("third") + "]}"), 60, 0, 5)), "Queue takes the first provider that gives a block");

        MaterialProvider<String> band = p("{\"Type\":\"SimpleHorizontal\",\"TopY\":70,\"BottomY\":60,\"Material\":" + k("band") + "}");
        check("band".equals(at(band, 60, 0, 5)) && "band".equals(at(band, 69, 0, 5)) && at(band, 70, 0, 5) == null && at(band, 59, 0, 5) == null, "SimpleHorizontal covers [BottomY, TopY): bottom inclusive, top exclusive");

        MaterialProvider<String> stripe = p("{\"Type\":\"Striped\",\"Stripes\":[{\"TopY\":65,\"BottomY\":63}],\"Material\":" + k("stripe") + "}");
        check("stripe".equals(at(stripe, 63, 0, 5)) && "stripe".equals(at(stripe, 65, 0, 5)) && at(stripe, 66, 0, 5) == null && at(stripe, 62, 0, 5) == null, "Striped covers stripes with both limits inclusive");

        MaterialProvider<String> w = p("{\"Type\":\"Weighted\",\"Seed\":\"a\",\"SkipChance\":0.0,\"WeightedMaterials\":[{\"Weight\":1,\"Material\":" + k("a") + "},{\"Weight\":1,\"Material\":" + k("b") + "}]}");
        int a = 0, b = 0;
        for (int i = 0; i < 400; i++) { String m = w.getMaterial(i, 64, i * 3, 0, 5, 0f, SPACE); if ("a".equals(m)) a++; else if ("b".equals(m)) b++; }
        check(a > 120 && b > 120 && a + b == 400, "Weighted splits blocks by weight");
        check(w.getMaterial(5, 64, 9, 0, 5, 0f, SPACE).equals(w.getMaterial(5, 64, 9, 0, 5, 0f, SPACE)), "Weighted is deterministic per position");
        MaterialProvider<String> skip = p("{\"Type\":\"Weighted\",\"Seed\":\"a\",\"SkipChance\":1.0,\"WeightedMaterials\":[{\"Weight\":1,\"Material\":" + k("a") + "}]}");
        check(skip.getMaterial(1, 1, 1, 0, 5, 0f, SPACE) == null, "SkipChance 1 provides nothing");

        MaterialProvider<String> ff = p("{\"Type\":\"FieldFunction\",\"FieldFunction\":{\"Type\":\"Constant\",\"Value\":0.5},\"Delimiters\":[{\"From\":0,\"To\":1,\"Material\":" + k("low") + "},{\"From\":0.4,\"To\":0.6,\"Material\":" + k("high") + "}]}");
        check("high".equals(at(ff, 60, 0, 5)), "FieldFunction: later delimiters have higher priority");

        MaterialProvider<String> sd = p("{\"Type\":\"SpaceAndDepth\",\"LayerContext\":\"DEPTH_INTO_FLOOR\",\"MaxExpectedDepth\":8,\"Condition\":{\"Type\":\"GreaterThanCondition\",\"ContextToCheck\":\"SPACE_ABOVE_FLOOR\",\"Threshold\":2},"
                + "\"Layers\":[{\"Type\":\"ConstantThickness\",\"Thickness\":1,\"Material\":" + k("grass") + "},{\"Type\":\"ConstantThickness\",\"Thickness\":2,\"Material\":" + k("dirt") + "},"
                + "{\"Type\":\"ConstantThickness\",\"Thickness\":99,\"Material\":" + k("stone") + "}]}");
        check("grass".equals(at(sd, 64, 0, 5)) && "dirt".equals(at(sd, 63, 1, 5)) && "dirt".equals(at(sd, 62, 2, 5)) && "stone".equals(at(sd, 61, 3, 5)), "SpaceAndDepth stacks layers by thickness from the surface down");
        check(at(sd, 64, 0, 2) == null, "SpaceAndDepth Condition (space above > 2) fails with too little room");

        MaterialProvider<String> legacy = p("{\"Type\":\"SpaceAndDepth\",\"layers\":[{\"material\":\"grass\",\"thickness\":1},{\"material\":\"stone\",\"thickness\":99}]}");
        check("grass".equals(at(legacy, 64, 0, 5)) && "stone".equals(at(legacy, 63, 1, 5)), "the older SpaceAndDepth shape still loads");

        MaterialProvider<String> ceiling = p("{\"Type\":\"SpaceAndDepth\",\"LayerContext\":\"DEPTH_INTO_CEILING\",\"MaxExpectedDepth\":8,\"Condition\":{\"Type\":\"GreaterThanCondition\",\"ContextToCheck\":\"SPACE_BELOW_CEILING\",\"Threshold\":2},"
                + "\"Layers\":[{\"Type\":\"ConstantThickness\",\"Thickness\":1,\"Material\":" + k("dripstone") + "},{\"Type\":\"ConstantThickness\",\"Thickness\":2,\"Material\":" + k("deepslate") + "}]}");
        check("dripstone".equals(ceiling.getMaterial(0, 40, 0, 10, 0, 0, 5, 0f, SPACE))
                && "deepslate".equals(ceiling.getMaterial(0, 41, 0, 9, 0, 1, 5, 0f, SPACE))
                && ceiling.getMaterial(0, 40, 0, 10, 0, 0, 2, 0f, SPACE) == null
                && ceiling.getMaterial(0, 40, 0, 10, 0, -1, 5, 0f, SPACE) == null,
                "SpaceAndDepth supports DEPTH_INTO_CEILING and SPACE_BELOW_CEILING");

        System.out.println("Phase 23 verification: ALL " + passed + " TESTS PASSED!");
    }
}