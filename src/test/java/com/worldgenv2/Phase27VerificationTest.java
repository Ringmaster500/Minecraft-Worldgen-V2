package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.HytaleDialect;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.graph.GraphBounds;
import com.worldgenv2.graph.GraphGenerator;
import com.worldgenv2.graph.ProceduralGraph;
import com.worldgenv2.hytale.HDistributions;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.positions.PositionsProvider;

import java.util.HashMap;
import java.util.Map;

/** Phase 27 verification: Hytale Graph Generator (passes, selectors, actions, content layers and the Graph readers). */
public final class Phase27VerificationTest {

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

    private static Object parse(String json) { return new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static JsonAstCompiler.CompileContext ctx() { return new JsonAstCompiler.CompileContext(1L); }

    private static GraphGenerator gen(String json) { return JsonAstCompiler.compileGraphGenerator(parse(json), ctx(), id -> id); }

    private static final GraphBounds BIG = new GraphBounds(-1000, -1000, -1000, 1000, 1000, 1000);

    private static String node(double x, double y, double z, String name, String conns, String content) {
        return "{\"Position\":{\"X\":" + x + ",\"Y\":" + y + ",\"Z\":" + z + "},\"Name\":\"" + name + "\"" + (conns.isEmpty() ? "" : ",\"Connections\":[" + conns + "]") + (content.isEmpty() ? "" : ",\"Content\":" + content) + "}";
    }

    private static String graph(String... passes) { return "{\"Passes\":[" + String.join(",", passes) + "]}"; }

    private static String manual(String... nodes) { return "{\"Type\":\"Manual\",\"Nodes\":[" + String.join(",", nodes) + "]}"; }

    private static String densityContent(double value, double range, String layer) {
        return "{\"DensityContent\":[{\"Density\":{\"Type\":\"Constant\",\"Value\":" + value + "},\"Range\":" + range + ",\"ContentLayer\":\"" + layer + "\"}],\"ContentTags\":[\"tag1\"]}";
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 27 Verification Test Suite: Graph Generator ===");
        JsonAstCompiler.registerBuiltInTypes();

        // ---- passes
        ProceduralGraph g = gen(graph(manual(node(0, 0, 0, "a", "\"b\",\"b\"", ""), node(100, 0, 0, "b", "\"a\"", ""), node(5000, 0, 0, "far", "", ""), node(10, 0, 0, "c", "\"far\",\"c\",\"nobody\"", "")))).generate(1L, BIG);
        check(g.nodeCount() == 3 && g.edgeCount() == 1, "Manual: nodes outside the region are skipped, a connection listed on both nodes makes one edge, self/unknown/skipped connections are dropped");
        g = gen(graph("{\"Type\":\"Positions\",\"Positions\":{\"Type\":\"List\",\"Positions\":[{\"X\":1,\"Y\":2,\"Z\":3},{\"X\":4,\"Y\":5,\"Z\":6}]}}")).generate(1L, BIG);
        check(g.nodeCount() == 2 && g.getNodes().stream().allMatch(n -> n.getContent().getDensityContent().isEmpty()), "Positions pass places an empty node for each position");
        check(gen(graph("{\"Type\":\"Empty\"}")).generate(1L, BIG).nodeCount() == 0 && gen(graph("{\"Skip\":true,\"Type\":\"Positions\",\"Positions\":{\"Type\":\"List\",\"Positions\":[{\"X\":1,\"Y\":2,\"Z\":3}]}}")).generate(1L, BIG).nodeCount() == 0, "Empty does nothing, and Skip turns any pass into nothing");

        // ---- node actions
        String line = manual(node(0, 0, 0, "a", "\"b\"", ""), node(30, 0, 0, "b", "\"c\"", ""), node(60, 0, 0, "c", "", ""), node(500, 0, 0, "d", "", ""));
        g = gen(graph(line, "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"ProximityConnector\",\"Range\":100,\"NodeSelector\":{\"Type\":\"All\"},\"Cap\":-1,\"Seed\":\"s\"}}")).generate(1L, BIG);
        check(g.hasEdge(0, 2) && g.nodeCount() == 4 && g.getNodes().stream().filter(n -> n.getConnectionCount() == 0).count() == 1, "ProximityConnector connects the nodes within Range and leaves distant ones alone");
        g = gen(graph(line, "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Selector\",\"NodeSelector\":{\"Type\":\"ConnectionCount\",\"Counts\":[0]},\"NodeAction\":{\"Type\":\"Delete\"}}}")).generate(1L, BIG);
        check(g.nodeCount() == 3, "Selector with ConnectionCount runs the action only on matching nodes (Delete removes the lone node)");
        g = gen(graph(line, "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Selector\",\"NodeSelector\":{\"Type\":\"Not\",\"NodeSelector\":{\"Type\":\"ConnectionCount\",\"Counts\":[1]}},\"NodeAction\":{\"Type\":\"Content\",\"Content\":{\"Type\":\"Constant\",\"Content\":" + densityContent(1, 50, "") + "}}}}")).generate(1L, BIG);
        check(g.getNodes().stream().filter(n -> n.getContent().hasTag("tag1")).count() == 2, "Content action attaches a ContentSupplier's content, Not inverts a selector");
        g = gen(graph(manual(node(0, 0, 0, "a", "", ""), node(10, 0, 0, "b", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Jitter2d\",\"Magnitude\":5,\"Seed\":\"j\"}}")).generate(1L, BIG);
        check(g.getNodes().stream().allMatch(n -> n.getPosition().y() == 0) && g.getNodes().stream().anyMatch(n -> n.getPosition().x() != 0 && n.getPosition().x() != 10), "Jitter2d moves nodes horizontally only");
        g = gen(graph(manual(node(0, 0, 0, "a", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Move\",\"MaxDistance\":100,\"Vector\":{\"Type\":\"Constant\",\"Vector\":{\"X\":0,\"Y\":7,\"Z\":0}}}}")).generate(1L, BIG);
        check(g.getNodes().iterator().next().getPosition().y() == 7, "Move shifts the node by the vector");
        g = gen(graph(manual(node(0, 0, 0, "a", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Move\",\"MaxDistance\":3,\"Vector\":{\"Type\":\"Constant\",\"Vector\":{\"X\":0,\"Y\":7,\"Z\":0}}}}")).generate(1L, BIG);
        check(g.getNodes().iterator().next().getPosition().y() == 0, "Move does not move when the vector reaches MaxDistance");
        g = gen(graph(manual(node(0, 0, 0, "a", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Spawner\",\"MaxOffsetExclusive\":50,\"CreateEdges\":true,\"Offset\":{\"Type\":\"Constant\",\"Vector\":{\"X\":0,\"Y\":0,\"Z\":0}},"
                + "\"ClusterBounds\":{\"Min\":{\"X\":-20,\"Y\":-1,\"Z\":-20},\"Max\":{\"X\":20,\"Y\":1,\"Z\":20}},\"Positions\":{\"Type\":\"List\",\"Positions\":[{\"X\":10,\"Y\":0,\"Z\":0},{\"X\":-10,\"Y\":0,\"Z\":0},{\"X\":30,\"Y\":0,\"Z\":0}]},\"Content\":{\"Type\":\"Constant\",\"Content\":{}}}}")).generate(1L, BIG);
        check(g.nodeCount() == 3 && g.edgeCount() == 2, "Spawner creates the cluster inside ClusterBounds (the position outside is dropped) and connects it to the node");

        // ---- edge actions
        String pair = manual(node(0, 0, 0, "a", "\"b\"", ""), node(90, 0, 0, "b", "", ""));
        g = gen(graph(pair, "{\"Type\":\"EdgeAction\",\"EdgeAction\":{\"Type\":\"Splitter\",\"NodeCount\":2,\"Content\":{\"Type\":\"Constant\",\"Content\":" + densityContent(1, 10, "") + "}}}")).generate(1L, BIG);
        check(g.nodeCount() == 4 && g.edgeCount() == 3 && g.getNodes().stream().filter(n -> n.getContent().hasTag("tag1")).count() == 2, "Splitter inserts evenly spaced nodes in a chain");
        g = gen(graph(pair, "{\"Type\":\"EdgeAction\",\"EdgeAction\":{\"Type\":\"Selector\",\"EdgeSelector\":{\"Type\":\"Length\",\"Delimiters\":[{\"Min\":0,\"Max\":50}]},\"EdgeAction\":{\"Type\":\"Delete\"}}}")).generate(1L, BIG);
        check(g.edgeCount() == 1, "Length selector skips edges outside its Delimiters");
        g = gen(graph(pair, "{\"Type\":\"EdgeAction\",\"EdgeAction\":{\"Type\":\"Selector\",\"EdgeSelector\":{\"Type\":\"Length\",\"Delimiters\":[{\"Min\":50,\"Max\":200}]},\"EdgeAction\":{\"Type\":\"Delete\"}}}")).generate(1L, BIG);
        check(g.edgeCount() == 0, "Selector + Delete removes the selected edges");
        g = gen(graph(pair, "{\"Type\":\"EdgeAction\",\"EdgeAction\":{\"Type\":\"Nodes\",\"NodeAction\":{\"Type\":\"Content\",\"Content\":{\"Type\":\"Constant\",\"Content\":" + densityContent(1, 10, "") + "}}}}")).generate(1L, BIG);
        check(g.getNodes().stream().allMatch(n -> n.getContent().hasTag("tag1")), "the Nodes edge action runs a node action on both ends of each edge");
        g = gen(graph(pair, "{\"Type\":\"EdgeAction\",\"EdgeAction\":{\"Type\":\"Proxy\",\"HostNodeSelector\":{\"Type\":\"All\"},\"Normal\":{\"Type\":\"Constant\",\"Vector\":{\"X\":0,\"Y\":1,\"Z\":0}},\"AngleToNormal\":{\"Type\":\"Constant\",\"Value\":90},\"SpinAngle\":{\"Type\":\"Constant\",\"Value\":0},\"ProxyDistance\":{\"Type\":\"Constant\",\"Value\":10},\"MaxProxyDistance\":20,\"Seed\":\"p\",\"ProxyContent\":{\"Type\":\"Constant\",\"Content\":{}}}}")).generate(1L, BIG);
        check(g.nodeCount() == 4, "Proxy inserts a proxy node for each host (both nodes selected: the edge splits in three)");

        // ---- selectors, predicates, suppliers
        g = gen(graph(line, "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Selector\",\"NodeSelector\":{\"Type\":\"Random\",\"Chance\":1.0,\"Seed\":\"r\"},\"NodeAction\":{\"Type\":\"Delete\"}}}")).generate(1L, BIG);
        check(g.nodeCount() == 0, "Random with Chance 1 selects every node");
        g = gen(graph(manual(node(0, 0, 0, "a", "", densityContent(1, 10, "")), node(50, 0, 0, "b", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Selector\",\"NodeSelector\":{\"Type\":\"Content\",\"ContentPredicate\":{\"Type\":\"Set\",\"ContentTags\":[\"tag1\"]}},\"NodeAction\":{\"Type\":\"Delete\"}}}")).generate(1L, BIG);
        check(g.nodeCount() == 1, "Content selector with a Set predicate matches nodes by ContentTags");
        g = gen(graph(manual(node(0, 0, 0, "a", "", ""), node(50, 0, 0, "b", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Selector\",\"NodeSelector\":{\"Type\":\"DensityDelimited\",\"Density\":{\"Type\":\"XValue\"},\"Delimiters\":[{\"Min\":40,\"Max\":60}]},\"NodeAction\":{\"Type\":\"Delete\"}}}")).generate(1L, BIG);
        check(g.nodeCount() == 1, "DensityDelimited selects nodes whose density value is in the Delimiters");
        g = gen(graph(manual(node(0, 0, 0, "a", "\"b\"", ""), node(50, 0, 0, "b", "\"c\"", ""), node(100, 0, 0, "c", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Selector\",\"NodeSelector\":{\"Type\":\"NeighborNodes\",\"SelectedNeighborsThreshold\":2,\"NodeSelector\":{\"Type\":\"All\"}},\"NodeAction\":{\"Type\":\"Delete\"}}}")).generate(1L, BIG);
        check(g.nodeCount() == 2, "NeighborNodes needs at least the threshold of selected neighbours");

        // ---- exports and imports by Name
        g = gen(graph(manual(node(0, 0, 0, "a", "", "")), "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Imported\",\"Name\":\"wipe\"}}", "{\"Type\":\"Empty\",\"ExportAs\":\"wipe-unused\"}",
                "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Delete\",\"ExportAs\":\"wipe\"},\"ExportAs\":\"pass1\"}")).generate(1L, BIG);
        check(g.nodeCount() == 0, "Imported pulls in an ExportAs node by Name, even when the export is written later");

        // ---- readers
        String layered = graph(manual(node(0, 0, 0, "a", "", "{\"DensityContent\":[{\"Density\":{\"Type\":\"Constant\",\"Value\":5},\"Range\":50,\"ContentLayer\":\"caves\"},{\"Density\":{\"Type\":\"Constant\",\"Value\":9},\"Range\":50,\"ContentLayer\":\"\"}],"
                + "\"MaterialContent\":[{\"MaterialProvider\":{\"Type\":\"Constant\",\"Material\":\"near\"},\"Range\":50,\"ContentLayer\":\"m\"}],"
                + "\"PositionsContent\":[{\"Positions\":{\"Type\":\"List\",\"Positions\":[{\"X\":3,\"Y\":0,\"Z\":0},{\"X\":300,\"Y\":0,\"Z\":0}]},\"Range\":50,\"ContentLayer\":\"p\"}],"
                + "\"PropDistributionContent\":[{\"PropDistribution\":{\"Type\":\"Constant\",\"Positions\":{\"Type\":\"List\",\"Positions\":[{\"X\":2,\"Y\":10,\"Z\":2},{\"X\":200,\"Y\":10,\"Z\":0}]},\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"tree\"}},\"Range\":50,\"ContentLayer\":\"d\"}]}"),
                node(400, 0, 0, "b", "", densityContent(100, 40, "caves"))));
        String gj = layered;
        DensityFunction caves = JsonAstCompiler.compileDensity((Map<String, Object>) parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + gj + ",\"ContentLayer\":\"caves\",\"BackgroundValue\":-1,\"TransitionSlope\":2.5,\"TransitionOffset\":0,\"TransitionSmooth\":0.2}"), ctx());
        check(Math.abs(caves.compute(0, 0, 0) - 5f) < 1e-3, "Graph density reads the layer named by ContentLayer at a node");
        check(caves.compute(200, 0, 0) == -1f, "Graph density returns BackgroundValue where no content is in range");
        float edge = caves.compute(49, 0, 0);
        check(edge < 5f && edge > -1f, "Graph density blends smoothly into the background towards the edge of Range");
        DensityFunction unnamed = JsonAstCompiler.compileDensity((Map<String, Object>) parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + gj + ",\"ContentLayer\":\"\",\"BackgroundValue\":-1}"), ctx());
        DensityFunction missing = JsonAstCompiler.compileDensity((Map<String, Object>) parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + gj + ",\"ContentLayer\":\"Caves\",\"BackgroundValue\":-1}"), ctx());
        check(Math.abs(unnamed.compute(0, 0, 0) - 9f) < 1e-3 && missing.compute(0, 0, 0) == -1f, "layers are matched exactly (the unnamed layer is its own; a differently-cased name finds nothing)");
        check(Math.abs(caves.compute(400, 0, 0) - 100f) < 1e-3, "a long-range content on a far node (found beyond the default search) is still read");

        MaterialProvider<String> mat = JsonAstCompiler.compileMaterialProvider((Map<String, Object>) parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + gj + ",\"ContentLayer\":\"m\"}"), ctx(), id -> id);
        check("near".equals(mat.getMaterial(10, 0, 0, 0, 5, 0f, new Space())) && mat.getMaterial(200, 0, 0, 0, 5, 0f, new Space()) == null, "Graph material provider uses the nearest node's material within Range");
        PositionsProvider pp = JsonAstCompiler.compilePositionsProvider(parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + gj + ",\"ContentLayer\":\"p\"}"), ctx());
        check(pp.getPositions(-10, -10, -10, 400, 10, 10).size() == 1, "Graph positions combines the positions of nodes in range (the one beyond Range is ignored)");
        HDistributions.Field<String> dist = HytaleDialect.compileDistribution(parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + gj + ",\"ContentLayer\":\"d\"}"), ctx(), id -> id);
        Space s = new Space();
        new HDistributions.FieldProp<>(dist).generateInRegion(0, 0, 16, 16, s, 1L, null, b -> true);
        check("tree".equals(s.getBlock(2, 10, 2)), "Graph prop distribution places the props its nodes carry");

        // ---- the graph is one infinite field: cells agree on nodes, jitter and random choices at their borders
        String field = "{\"CacheCellSize\":{\"X\":100,\"Y\":100,\"Z\":100},\"Passes\":[{\"Type\":\"Positions\",\"Positions\":{\"Type\":\"Scaler\",\"Scale\":{\"X\":30,\"Y\":30,\"Z\":30},\"Positions\":{\"Type\":\"SquareGrid2d\"}}},"
                + "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Jitter2d\",\"Magnitude\":12,\"Seed\":\"j\"}},"
                + "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Selector\",\"NodeSelector\":{\"Type\":\"Random\",\"Chance\":0.5,\"Seed\":\"r\"},\"NodeAction\":{\"Type\":\"Delete\"}}}]}";
        GraphGenerator a = gen(field), b2 = gen(field);
        var whole = a.getNodesNear(1L, new com.worldgenv2.vector.Vec3d(0, 0, 0), 150);
        var again = b2.getNodesNear(1L, new com.worldgenv2.vector.Vec3d(60, 0, 40), 100);   // different cells generated, other order
        java.util.Set<String> inWhole = new java.util.TreeSet<>(), inAgain = new java.util.TreeSet<>();
        for (var n : whole) if (n.getPosition().distance(new com.worldgenv2.vector.Vec3d(0, 0, 0)) <= 150 && n.getPosition().distance(new com.worldgenv2.vector.Vec3d(60, 0, 40)) <= 100) inWhole.add(n.getPosition().toString());
        for (var n : again) if (n.getPosition().distance(new com.worldgenv2.vector.Vec3d(0, 0, 0)) <= 150 && n.getPosition().distance(new com.worldgenv2.vector.Vec3d(60, 0, 40)) <= 100) inAgain.add(n.getPosition().toString());
        check(!inWhole.isEmpty() && inWhole.equals(inAgain) && whole.size() == new java.util.HashSet<>(whole.stream().map(n -> n.getPosition().toString()).toList()).size(),
                "nodes are the same however the cells are reached: no duplicates at cell borders, jitter and random deletes do not depend on the cell");
        System.out.println("Phase 27 verification: ALL " + passed + " TESTS PASSED!");
    }
}