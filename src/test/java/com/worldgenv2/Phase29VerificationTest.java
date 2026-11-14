package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.graph.GraphBounds;
import com.worldgenv2.graph.GraphGenerator;
import com.worldgenv2.graph.GraphNode;
import com.worldgenv2.graph.ProceduralGraph;
import com.worldgenv2.positions.PositionsProvider;

import java.util.Map;

/** Phase 29 verification: features built as graph content (cave tunnels, roads, landmark points of interest). */
public final class Phase29VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static Object parse(String json) { return new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static JsonAstCompiler.CompileContext ctx() { return new JsonAstCompiler.CompileContext(1L); }

    private static String node(double x, double y, double z, String name, String conns) {
        return "{\"Position\":{\"X\":" + x + ",\"Y\":" + y + ",\"Z\":" + z + "},\"Name\":\"" + name + "\"" + (conns.isEmpty() ? "" : ",\"Connections\":[" + conns + "]") + "}";
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        System.out.println("=== Phase 29 Verification Test Suite: graph content (caves, roads, landmarks) ===");
        JsonAstCompiler.registerBuiltInTypes();

        // A cave system: two chambers joined by an edge; the edge is chopped into nodes that each carry a tunnel-sized density.
        String tunnel = "{\"DensityContent\":[{\"Density\":{\"Type\":\"Constant\",\"Value\":-1},\"Range\":9,\"ContentLayer\":\"caves\"}]}";
        String caves = "{\"Passes\":[{\"Type\":\"Manual\",\"Nodes\":[" + node(0, 30, 0, "a", "\"b\"") + "," + node(120, 30, 0, "b", "") + "]},"
                + "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Content\",\"Content\":{\"Type\":\"Constant\",\"Content\":" + tunnel + "}}},"
                + "{\"Type\":\"EdgeAction\",\"EdgeAction\":{\"Type\":\"Splitter\",\"NodeCount\":10,\"Content\":{\"Type\":\"Constant\",\"Content\":" + tunnel + "}}}]}";
        DensityFunction carve = JsonAstCompiler.compileDensity((Map<String, Object>) parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + caves + ",\"ContentLayer\":\"caves\",\"BackgroundValue\":1,\"TransitionSlope\":2.5,\"TransitionOffset\":0,\"TransitionSmooth\":0.2}"), ctx());
        check(carve.compute(0, 30, 0) < -0.9f && carve.compute(120, 30, 0) < -0.9f, "the chambers carve (density below 0 where the rock should go)");
        check(carve.compute(60, 30, 0) < 0f && carve.compute(30, 30, 0) < 0f && carve.compute(90, 30, 0) < 0f, "the tunnel along the edge carves between the chambers");
        check(carve.compute(60, 30, 60) == 1f && carve.compute(60, 80, 0) == 1f, "away from the tunnel the background rock is untouched");

        // A road network: points connected by proximity, capped to two roads each, with the roads as a layer of content
        String roads = "{\"CacheCellSize\":{\"X\":300,\"Y\":300,\"Z\":300},\"Passes\":[{\"Type\":\"Positions\",\"Positions\":{\"Type\":\"Scaler\",\"Scale\":{\"X\":60,\"Y\":60,\"Z\":60},\"Positions\":{\"Type\":\"SquareGrid2d\"}}},"
                + "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"ProximityConnector\",\"Range\":70,\"Cap\":2,\"Seed\":\"road\",\"NodeSelector\":{\"Type\":\"All\"}}}]}";
        GraphGenerator rg = JsonAstCompiler.compileGraphGenerator(parse(roads), ctx(), id -> id);
        ProceduralGraph cell = rg.getGraphForCell(1L, 0, 0, 0);
        check(cell.nodeCount() > 20 && cell.edgeCount() > 20 && cell.getNodes().stream().allMatch(n -> n.getConnectionCount() >= 1), "ProximityConnector with a Cap builds a connected-looking road network");

        // Landmarks: sparse nodes spawn a ring of points of interest around themselves, read back as Positions
        String landmarks = "{\"Passes\":[{\"Type\":\"Manual\",\"Nodes\":[" + node(0, 0, 0, "town", "") + "," + node(500, 0, 0, "ruin", "") + "]},"
                + "{\"Type\":\"NodeAction\",\"NodeAction\":{\"Type\":\"Spawner\",\"MaxOffsetExclusive\":50,\"CreateEdges\":true,\"Offset\":{\"Type\":\"Constant\",\"Vector\":{\"X\":0,\"Y\":0,\"Z\":0}},"
                + "\"ClusterBounds\":{\"Min\":{\"X\":-40,\"Y\":-1,\"Z\":-40},\"Max\":{\"X\":40,\"Y\":1,\"Z\":40}},\"Positions\":{\"Type\":\"List\",\"Positions\":[{\"X\":30,\"Y\":0,\"Z\":0},{\"X\":-30,\"Y\":0,\"Z\":0},{\"X\":0,\"Y\":0,\"Z\":30}]},"
                + "\"Content\":{\"Type\":\"Constant\",\"Content\":{\"PositionsContent\":[{\"Positions\":{\"Type\":\"Scaler\",\"Scale\":{\"X\":8,\"Y\":8,\"Z\":8},\"Positions\":{\"Type\":\"SquareGrid2d\"}},\"Range\":10,\"ContentLayer\":\"poi\"}]}}}}]}";
        PositionsProvider poi = JsonAstCompiler.compilePositionsProvider(parse("{\"Type\":\"Graph\",\"GraphGenerator\":" + landmarks + ",\"ContentLayer\":\"poi\"}"), ctx());
        var around = poi.getPositions(-100, -10, -100, 100, 10, 100);
        double[][] spawned = {{30, 0}, {-30, 0}, {0, 30}};
        boolean near = !around.isEmpty() && around.stream().allMatch(p -> { for (double[] s : spawned) if (Math.hypot(p.x() - s[0], p.z() - s[1]) <= 10.0) return true; return false; });
        check(near, "landmark nodes spawn a cluster of nodes whose content the Graph positions reader yields, only within each node's Range");
        check(!poi.getPositions(400, -10, -100, 600, 10, 100).isEmpty() && poi.getPositions(200, -10, -100, 300, 10, 100).isEmpty(), "every landmark spawns its own cluster, and nothing is yielded in the empty land between them");

        System.out.println("Phase 29 verification: ALL " + passed + " TESTS PASSED!");
    }
}