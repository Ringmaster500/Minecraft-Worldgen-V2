package com.worldgenv2;

import com.worldgenv2.biome.HytaleDialect;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.hytale.HDistributions;

import java.util.HashMap;
import java.util.Map;

/** Phase 26 verification: Hytale Prop Distribution nodes (Constant, Positions, Assigned, Union, Anchor, Imported). */
public final class Phase26VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static final class Space implements VoxelSpace<String> {
        final Map<Long, String> m = new HashMap<>();
        static long k(int x, int y, int z) { return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL); }
        public String getBlock(int x, int y, int z) { return m.getOrDefault(k(x, y, z), "air"); }
        public void setBlock(int x, int y, int z, String b) { if ("air".equals(b)) m.remove(k(x, y, z)); else m.put(k(x, y, z), b); }
        public boolean isAir(int x, int y, int z) { return "air".equals(getBlock(x, y, z)); }
        public int getMinY() { return 0; }
        public int getMaxY() { return 64; }
    }

    @SuppressWarnings("unchecked")
    private static Object parse(String json) { return new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static int run(String json, Space s) {
        HDistributions.Field<String> f = HytaleDialect.compileDistribution(parse(json), new JsonAstCompiler.CompileContext(1L), id -> id);
        return new HDistributions.FieldProp<>(f).generateInRegion(0, 0, 16, 16, s, 7L, null, b -> !"air".equals(b));
    }

    private static String pts(int... xz) {
        StringBuilder sb = new StringBuilder("{\"Type\":\"List\",\"Positions\":[");
        for (int i = 0; i < xz.length; i += 2) sb.append(i > 0 ? "," : "").append("{\"X\":").append(xz[i]).append(",\"Y\":10,\"Z\":").append(xz[i + 1]).append("}");
        return sb.append("]}").toString();
    }

    private static String block(String b) { return "{\"Type\":\"BlockProp\",\"Block\":\"" + b + "\"}"; }

    private static String constAsg(String b) { return "{\"Type\":\"Constant\",\"Prop\":" + block(b) + "}"; }

    public static void main(String[] args) {
        System.out.println("=== Phase 26 Verification Test Suite: Prop Distribution nodes ===");
        JsonAstCompiler.registerBuiltInTypes();

        Space s = new Space();
        check(run("{\"Type\":\"Constant\",\"Positions\":" + pts(2, 2, 5, 5) + ",\"Prop\":" + block("a") + "}", s) == 2 && "a".equals(s.getBlock(2, 10, 2)) && "a".equals(s.getBlock(5, 10, 5)), "Constant pairs the prop with every position");

        s = new Space();
        check(run("{\"Type\":\"Positions\",\"Positions\":" + pts(2, 2, 5, 5) + "}", s) == 0 && s.m.isEmpty(), "Positions makes empty positions that place nothing");

        s = new Space();
        check(run("{\"Type\":\"Assigned\",\"PropDistribution\":{\"Type\":\"Positions\",\"Positions\":" + pts(2, 2) + "},\"Assignments\":" + constAsg("b") + "}", s) == 1 && "b".equals(s.getBlock(2, 10, 2)), "Assigned fills empty positions from the Assignments");

        s = new Space();
        String kept = "{\"Type\":\"Constant\",\"Positions\":" + pts(2, 2) + ",\"Prop\":" + block("old") + "}";
        run("{\"Type\":\"Assigned\",\"OverrideAllProps\":false,\"PropDistribution\":" + kept + ",\"Assignments\":" + constAsg("new") + "}", s);
        check("old".equals(s.getBlock(2, 10, 2)), "Assigned with OverrideAllProps off leaves already-assigned positions alone");
        s = new Space();
        run("{\"Type\":\"Assigned\",\"OverrideAllProps\":true,\"PropDistribution\":" + kept + ",\"Assignments\":" + constAsg("new") + "}", s);
        check("new".equals(s.getBlock(2, 10, 2)), "OverrideAllProps replaces them");

        s = new Space();
        check(run("{\"Type\":\"Union\",\"PropDistributions\":[{\"Type\":\"Constant\",\"Positions\":" + pts(1, 1) + ",\"Prop\":" + block("u1") + "},{\"Type\":\"Constant\",\"Positions\":" + pts(9, 9) + ",\"Prop\":" + block("u2") + "}]}", s) == 2
                && "u1".equals(s.getBlock(1, 10, 1)) && "u2".equals(s.getBlock(9, 10, 9)), "Union keeps every child's positioned props");

        s = new Space();
        run("{\"Type\":\"Anchor\",\"PropDistribution\":{\"Type\":\"Constant\",\"Positions\":" + pts(3, 3) + ",\"Prop\":" + block("an") + "}}", s);
        check("an".equals(s.getBlock(3, 10, 3)), "Anchor without a contextual anchor leaves the field where it is");

        // anchored inside a Clusters positions node: Anchor shifts the child field to the cluster's anchor
        s = new Space();
        String clusters = "{\"Type\":\"Clusters\",\"Seed\":\"c\",\"ClusterBounds\":{\"Min\":{\"X\":-8,\"Y\":-20,\"Z\":-8},\"Max\":{\"X\":8,\"Y\":20,\"Z\":8}},\"Distributor\":" + pts(8, 8) + ",\"Cluster\":{\"Type\":\"Anchor\",\"Positions\":" + pts(1, 1) + "}}";
        int n = run("{\"Type\":\"Constant\",\"Positions\":" + clusters + ",\"Prop\":" + block("cl") + "}", s);
        check(n >= 1 && "cl".equals(s.getBlock(9, 20, 9)), "a Clusters distributor with an Anchor places its contents around the cluster anchor");

        // Imported
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L, null, null, path -> path.equals("shared") ? "{\"Type\":\"Constant\",\"Positions\":" + pts(4, 4) + ",\"Prop\":" + block("imp") + "}" : null);
        s = new Space();
        HDistributions.Field<String> f = HytaleDialect.compileDistribution(parse("{\"Type\":\"Imported\",\"Name\":\"shared\"}"), ctx, id -> id);
        new HDistributions.FieldProp<>(f).generateInRegion(0, 0, 16, 16, s, 1L, null, b -> true);
        check("imp".equals(s.getBlock(4, 10, 4)), "Imported pulls in an exported distribution by Name");

        // a biome Props entry may use a PropDistribution pin or the Positions + Assignments form
        s = new Space();
        var rt = HytaleDialect.compileBiomeProp((Map<String, Object>) parse("{\"Runtime\":0,\"PropDistribution\":{\"Type\":\"Constant\",\"Positions\":" + pts(6, 6) + ",\"Prop\":" + block("bp") + "}}"), new JsonAstCompiler.CompileContext(1L), id -> id);
        check(rt != null, "a biome Props entry accepts a PropDistribution");

        System.out.println("Phase 26 verification: ALL " + passed + " TESTS PASSED!");
    }
}