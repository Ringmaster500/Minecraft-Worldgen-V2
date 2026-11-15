package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.graph.GraphGenerator;
import com.worldgenv2.worldstructure.GraphCaveCarver;
import com.worldgenv2.worldstructure.RiverCarver;

import java.util.Map;

/** Phase 32 verification: rivers and caves built as Hytale graphs (edge distance, graph rivers, graph caves, node-anchored content). */
public final class Phase32VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static Object parse(String json) { return new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static GraphGenerator gen(String json) { return JsonAstCompiler.compileGraphGenerator(parse(json), new JsonAstCompiler.CompileContext(5L), id -> id); }

    private static final String LINE = "{\"CacheCellSize\":{\"X\":100,\"Y\":100,\"Z\":100},\"Passes\":[{\"Type\":\"Manual\",\"Nodes\":[{\"Position\":{\"X\":-250,\"Y\":0,\"Z\":0},\"Name\":\"a\",\"Connections\":[\"b\"]},{\"Position\":{\"X\":250,\"Y\":0,\"Z\":0},\"Name\":\"b\"}]}]}";

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        System.out.println("=== Phase 32 Verification Test Suite: graph rivers and caves ===");
        JsonAstCompiler.registerBuiltInTypes();

        // an edge much longer than a cell is still found from any cell it crosses
        GraphGenerator g = gen(LINE);
        double[] n = g.nearestEdge(5L, 0, 0, 30, 100, false, 0);
        check(n != null && Math.abs(n[0] - 30) < 1e-6 && Math.abs(n[1]) < 1e-6, "nearestEdge finds an edge that crosses many cells, with the distance and the nearest point");
        check(g.nearestEdge(5L, 0, 0, 200, 100, false, 0) == null, "nothing is returned beyond the search radius");
        double[] flat = g.nearestEdge(5L, 120, 40, 0, 100, true, 0);
        check(flat != null && Math.abs(flat[0]) < 1e-6, "a horizontal measurement ignores height");

        DensityFunction dte = JsonAstCompiler.compileDensity((Map<String, Object>) parse("{\"Type\":\"DistanceToGraphEdge\",\"GraphGenerator\":" + LINE + ",\"MaxDistance\":80}"), new JsonAstCompiler.CompileContext(5L));
        check(Math.abs(dte.compute(0, 0, 30) - 30f) < 1e-3 && dte.compute(0, 0, 500) == 80f, "DistanceToGraphEdge reads the distance in blocks, capped at MaxDistance");

        // a river that follows the graph
        RiverCarver.Settings st = new RiverCarver.Settings(1000f, 20f, 60f, 3, 0f, 0f);
        RiverCarver river = new RiverCarver(5L, 63, st).useGraph(g, 5L, st);
        river.setHeightSampler((x, z) -> 70f);
        check(river.usesGraph() && river.isChannel(0, 3) && !river.isChannel(0, 25) && river.riverDistance(0, 100) == 1.0f, "a graph river has its channel along the graph's edge and nothing elsewhere");
        check(river.levelAt(0, 3) == river.levelAt(120, -2) && river.levelAt(0, 3) >= 63, "the water is flat along a flat valley and never below sea level");
        float[] col = new float[100];
        for (int i = 0; i < 100; i++) col[i] = (70 - (i - 20)) * 0.12f;   // ground at y=70 for minY -20... positive below
        float[] before = col.clone();
        river.carveColumn(0, 2, -20, 80, col);
        float[] far = before.clone();
        river.carveColumn(0, 400, -20, 80, far);
        check(java.util.Arrays.equals(far, before) && !java.util.Arrays.equals(col, before), "the valley is carved at the river and nothing changes far from it");

        // caves: tunnel nodes carry density, the carver subtracts it
        String tunnel = "{\"Type\":\"Constant\",\"Content\":{\"DensityContent\":[{\"Range\":9,\"ContentLayer\":\"caves\",\"Density\":{\"Type\":\"Anchor\",\"Reverse\":false,\"Inputs\":[{\"Type\":\"Ellipsoid\",\"Scale\":{\"X\":5,\"Y\":4,\"Z\":5},\"NewYAxis\":{\"X\":0,\"Y\":1,\"Z\":0},\"Spin\":0,\"Curve\":{\"Type\":\"Manual\",\"Points\":[{\"X\":0,\"Y\":-1,\"Z\":0},{\"X\":1,\"Y\":0,\"Z\":0},{\"X\":1.6,\"Y\":1,\"Z\":0}]}}]}}]}}";
        String caves = "{\"CacheCellSize\":{\"X\":200,\"Y\":200,\"Z\":200},\"Passes\":[{\"Type\":\"Manual\",\"Nodes\":[{\"Position\":{\"X\":-80,\"Y\":40,\"Z\":0},\"Name\":\"a\",\"Connections\":[\"b\"]},{\"Position\":{\"X\":80,\"Y\":40,\"Z\":0},\"Name\":\"b\"}]},"
                + "{\"Type\":\"EdgeAction\",\"EdgeAction\":{\"Type\":\"Splitter\",\"NodeCount\":30,\"Content\":" + tunnel + "}},{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Content\",\"Content\":" + tunnel + "}}]}";
        GraphCaveCarver cc = new GraphCaveCarver(gen(caves), "caves", 5L, 7);
        float[] solid = new float[200];
        java.util.Arrays.fill(solid, 1f);
        cc.carveColumn(0, 0, 0, 200, solid);
        check(solid[40] < 0f && solid[38] < 0f && solid[42] < 0f && solid[60] == 1f && solid[20] == 1f, "a tunnel is carved around the graph's edge (about its radius) and the rock above and below stays");
        float[] aside = new float[200];
        java.util.Arrays.fill(aside, 1f);
        cc.carveColumn(0, 40, 0, 200, aside);
        boolean untouched = true;
        for (float v : aside) if (v != 1f) untouched = false;
        check(untouched, "a column away from every tunnel is not touched");
        float[] deep = new float[200];
        java.util.Arrays.fill(deep, 1f);
        GraphCaveCarver low = new GraphCaveCarver(gen(caves.replace("\"Y\":40", "\"Y\":3")), "caves", 5L, 7);
        low.carveColumn(0, 0, 0, 200, deep);
        check(deep[0] == 1f && deep[3] == 1f && deep[6] == 1f, "the bedrock shell at the bottom is left alone");

        // content is read relative to its node: the Anchor node puts the node at the origin
        String reader = "{\"Type\":\"Graph\",\"ContentLayer\":\"c\",\"BackgroundValue\":-99,\"GraphGenerator\":{\"Passes\":[{\"Type\":\"Manual\",\"Nodes\":[{\"Position\":{\"X\":100,\"Y\":0,\"Z\":0},\"Name\":\"n\",\"Content\":{\"DensityContent\":[{\"Range\":30,\"ContentLayer\":\"c\",\"Density\":{\"Type\":\"Anchor\",\"Reverse\":false,\"Inputs\":[{\"Type\":\"XValue\"}]}}]}}]}]}}";
        DensityFunction rel = JsonAstCompiler.compileDensity((Map<String, Object>) parse(reader), new JsonAstCompiler.CompileContext(5L));
        check(Math.abs(rel.compute(110, 0, 0) - 10f) < 1e-3, "node content wrapped in Anchor is evaluated relative to its node (x - 100 = 10)");

        System.out.println("Phase 32 verification: ALL " + passed + " TESTS PASSED!");
    }
}