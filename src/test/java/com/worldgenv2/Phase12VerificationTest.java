package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.ConstantDensity;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.density.GraphDensity;
import com.worldgenv2.graph.*;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.material.GraphMaterialProvider;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.positions.GraphPositionsProvider;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.positions.SquareGrid2dPositionsProvider;
import com.worldgenv2.props.BlockProp;
import com.worldgenv2.props.GraphProp;
import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.ConstantVectorProvider;
import com.worldgenv2.vector.Vec3d;
import com.worldgenv2.worldstructure.RiverCarver;
import com.worldgenv2.worldstructure.SplineRoadEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 12 Verification Test Suite: Procedural Graph Generator Subsystem.
 */
public final class Phase12VerificationTest {

    private static int testsPassed = 0;

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Phase 12 assertion failed: " + description);
        }
        System.out.println("[PASS] " + description);
        testsPassed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 12 Verification Test Suite: Procedural Graph Engine ===");

        testProceduralGraphBasicTopology();
        testPositionsGraphPass();
        testProximityConnectorAction();
        testSplitterEdgeAction();
        testNodeAndEdgeSelectors();
        testContentSupplierAndPredicates();
        testGraphGeneratorCellCaching();
        testGraphDensityEvaluationAndBlending();
        testGraphMaterialProviderAndProp();
        testSplineRoadEngineAndRiverCarverBridges();
        testJsonAstCompilationOfGraph();

        System.out.println("Phase 12 verification: ALL " + testsPassed + " TESTS PASSED!");
    }

    private static void testProceduralGraphBasicTopology() {
        ProceduralGraph graph = new ProceduralGraph();
        check(graph.nodeCount() == 0, "Initial graph nodeCount == 0");
        check(graph.edgeCount() == 0, "Initial graph edgeCount == 0");

        GraphNode n1 = graph.addNode(new Vec3d(0, 64, 0), GraphContent.EMPTY);
        GraphNode n2 = graph.addNode(new Vec3d(50, 64, 0), GraphContent.EMPTY);
        GraphNode n3 = graph.addNode(new Vec3d(100, 64, 0), GraphContent.EMPTY);

        check(graph.nodeCount() == 3, "Added 3 nodes");
        check(graph.hasNode(n1.getId()), "Graph contains node 1");
        check(graph.hasNode(n2.getId()), "Graph contains node 2");
        check(graph.hasNode(n3.getId()), "Graph contains node 3");

        GraphEdge e1 = graph.addEdge(n1.getId(), n2.getId());
        check(e1 != null, "Added edge between node 1 and 2");
        check(graph.edgeCount() == 1, "Graph edgeCount == 1");
        check(graph.hasEdge(n1.getId(), n2.getId()), "Graph hasEdge(n1, n2)");
        check(graph.hasEdge(n2.getId(), n1.getId()), "Graph hasEdge(n2, n1) [undirected]");
        check(n1.isConnectedTo(n2.getId()), "Node 1 is connected to node 2");
        check(n2.isConnectedTo(n1.getId()), "Node 2 is connected to node 1");

        GraphEdge e2 = graph.addEdge(n2.getId(), n3.getId());
        check(e2 != null, "Added edge between node 2 and 3");
        check(graph.edgeCount() == 2, "Graph edgeCount == 2");

        check(n1.getConnectionCount() == 1, "Node 1 degree == 1");
        check(n2.getConnectionCount() == 2, "Node 2 degree == 2");
        check(n3.getConnectionCount() == 1, "Node 3 degree == 1");

        // Spatial radius query
        List<GraphNode> inRadius = graph.getNodesInRadius(new Vec3d(0, 64, 0), 60.0);
        check(inRadius.size() == 2, "Radius 60 from (0,64,0) contains exactly 2 nodes (n1 and n2)");

        // Remove node 2
        check(graph.removeNode(n2.getId()), "Removed node 2");
        check(graph.nodeCount() == 2, "Node count is now 2");
        check(graph.edgeCount() == 0, "Incident edges removed (edge count == 0)");
        check(n1.getConnectionCount() == 0, "Node 1 degree is now 0");
        check(n3.getConnectionCount() == 0, "Node 3 degree is now 0");
    }

    private static void testPositionsGraphPass() {
        ProceduralGraph graph = new ProceduralGraph();
        PositionsProvider grid = new SquareGrid2dPositionsProvider(50.0);
        ContentSupplier supplier = (n, s) -> new GraphContent.Builder().addTag("grid_node").build();

        PositionsGraphPass pass = new PositionsGraphPass(grid, supplier);
        GraphBounds bounds = new GraphBounds(0, 0, 0, 100, 100, 100);

        pass.execute(graph, 12345L, bounds);

        check(graph.nodeCount() == 9, "PositionsGraphPass populated exactly 9 lattice nodes in [0, 100] bounds");
        boolean allTagged = true;
        for (GraphNode node : graph.getNodes()) {
            if (!node.hasTag("grid_node") || !bounds.contains(node.getPosition())) {
                allTagged = false;
                break;
            }
        }
        check(allTagged, "All generated nodes have content tag 'grid_node' and lie within bounds");
    }

    private static void testProximityConnectorAction() {
        ProceduralGraph graph = new ProceduralGraph();
        GraphNode n1 = graph.addNode(new Vec3d(0, 0, 0), GraphContent.EMPTY);
        GraphNode n2 = graph.addNode(new Vec3d(30, 0, 0), GraphContent.EMPTY); // dist 30
        GraphNode n3 = graph.addNode(new Vec3d(50, 0, 0), GraphContent.EMPTY); // dist 50
        GraphNode n4 = graph.addNode(new Vec3d(200, 0, 0), GraphContent.EMPTY); // dist 200

        ProximityConnectorNodeAction action = new ProximityConnectorNodeAction(
            60.0, AllNodeSelector.INSTANCE, -1, 42L
        );

        action.apply(n1, graph, 42L);

        check(graph.hasEdge(n1.getId(), n2.getId()), "ProximityConnector linked n1 to n2 (distance 30 <= 60)");
        check(graph.hasEdge(n1.getId(), n3.getId()), "ProximityConnector linked n1 to n3 (distance 50 <= 60)");
        check(!graph.hasEdge(n1.getId(), n4.getId()), "ProximityConnector excluded n4 (distance 200 > 60)");
        check(graph.edgeCount() == 2, "ProximityConnector created exactly 2 connections");

        // Test connection cap = 1
        ProceduralGraph graphCap = new ProceduralGraph();
        GraphNode c1 = graphCap.addNode(new Vec3d(0, 0, 0), GraphContent.EMPTY);
        GraphNode c2 = graphCap.addNode(new Vec3d(10, 0, 0), GraphContent.EMPTY);
        GraphNode c3 = graphCap.addNode(new Vec3d(20, 0, 0), GraphContent.EMPTY);
        ProximityConnectorNodeAction actionCapped = new ProximityConnectorNodeAction(
            50.0, AllNodeSelector.INSTANCE, 1, 42L
        );
        actionCapped.apply(c1, graphCap, 42L);
        check(graphCap.edgeCount() == 1, "ProximityConnector cap=1 created at most 1 connection");
        check(graphCap.hasEdge(c1.getId(), c2.getId()), "ProximityConnector connected to closest node c2");
    }

    private static void testSplitterEdgeAction() {
        ProceduralGraph graph = new ProceduralGraph();
        GraphNode a = graph.addNode(new Vec3d(0, 0, 0), GraphContent.EMPTY);
        GraphNode b = graph.addNode(new Vec3d(100, 0, 0), GraphContent.EMPTY);
        GraphEdge edge = graph.addEdge(a.getId(), b.getId());
        check(edge != null, "Created edge for splitting");

        ContentSupplier supplier = (n, s) -> new GraphContent.Builder().addTag("intermediate").build();
        SplitterEdgeAction splitter = new SplitterEdgeAction(3, supplier);

        splitter.apply(edge, graph, 999L);

        check(graph.nodeCount() == 5, "Splitter with nodeCount=3 resulted in 5 total nodes");
        check(graph.edgeCount() == 4, "Splitter created a chained path of 4 edges");
        check(!graph.hasEdge(a.getId(), b.getId()), "Original edge was removed");

        boolean has25 = false, has50 = false, has75 = false;
        for (GraphNode node : graph.getNodes()) {
            if (Math.abs(node.getPosition().x() - 25.0) < 1e-4) has25 = true;
            if (Math.abs(node.getPosition().x() - 50.0) < 1e-4) has50 = true;
            if (Math.abs(node.getPosition().x() - 75.0) < 1e-4) has75 = true;
        }
        check(has25 && has50 && has75, "Intermediate nodes placed at exact 25%, 50%, and 75% arc positions");
    }

    private static void testNodeAndEdgeSelectors() {
        ProceduralGraph graph = new ProceduralGraph();
        GraphNode a = graph.addNode(new Vec3d(0, 0, 0), GraphContent.EMPTY);
        GraphNode b = graph.addNode(new Vec3d(10, 0, 0), GraphContent.EMPTY);
        GraphNode c = graph.addNode(new Vec3d(20, 0, 0), GraphContent.EMPTY);
        GraphEdge e1 = graph.addEdge(a.getId(), b.getId());
        GraphEdge e2 = graph.addEdge(b.getId(), c.getId());

        // Node degree: a=1, b=2, c=1
        ConnectionCountNodeSelector deg2 = new ConnectionCountNodeSelector(Set.of(2));
        check(!deg2.select(a, graph), "ConnectionCountNodeSelector rejects degree 1");
        check(deg2.select(b, graph), "ConnectionCountNodeSelector selects degree 2");

        // Edge length: e1 len=10
        LengthEdgeSelector lenSel = new LengthEdgeSelector(List.of(new DoubleRange(5.0, 15.0)));
        check(lenSel.select(e1, graph), "LengthEdgeSelector selects edge length 10 in [5, 15]");

        // Angle edge selector: reference along X
        AngleEdgeSelector angleSel = new AngleEdgeSelector(
            new ConstantVectorProvider(1, 0, 0),
            List.of(new DoubleRange(0.0, 5.0))
        );
        check(angleSel.select(e1, graph), "AngleEdgeSelector selects collinear edge along X axis");

        // Nodes edge selector
        ConnectionCountNodeSelector deg1or2 = new ConnectionCountNodeSelector(Set.of(1, 2));
        NodesEdgeSelector bothSelected = new NodesEdgeSelector(deg1or2, NodesEdgeSelector.Operator.AND);
        check(bothSelected.select(e1, graph), "NodesEdgeSelector Operator.AND selects edge when both endpoints pass");

        // Delimited density selector
        DensityFunction constDensity = new ConstantDensity(0.75f);
        DensityDelimitedNodeSelector densitySel = new DensityDelimitedNodeSelector(
            constDensity, List.of(new DoubleRange(0.5, 1.0))
        );
        check(densitySel.select(a, graph), "DensityDelimitedNodeSelector selects node in range [0.5, 1.0]");
    }

    private static void testContentSupplierAndPredicates() {
        GraphContent content = new GraphContent.Builder()
            .addDensity(new ConstantDensity(0.8f), 20.0, "caves")
            .addTag("waterfall")
            .addTag("river")
            .build();

        SetContentPredicate pred1 = new SetContentPredicate(List.of("river"));
        SetContentPredicate pred2 = new SetContentPredicate(List.of("volcano"));
        check(pred1.test(content), "SetContentPredicate passes matching tag 'river'");
        check(!pred2.test(content), "SetContentPredicate rejects non-matching tag 'volcano'");

        NotContentPredicate notPred = new NotContentPredicate(pred2);
        check(notPred.test(content), "NotContentPredicate inverts negative match");

        AndContentPredicate andPred = new AndContentPredicate(List.of(pred1, notPred));
        check(andPred.test(content), "AndContentPredicate passes when all children pass");
    }

    private static void testGraphGeneratorCellCaching() {
        PositionsGraphPass pass = new PositionsGraphPass(
            new SquareGrid2dPositionsProvider(100.0),
            (n, s) -> GraphContent.EMPTY
        );
        GraphGenerator gen = new GraphGenerator(List.of(pass), new Vec3d(320, 320, 320), 10);

        ProceduralGraph g1 = gen.getGraphForCell(1001L, 0, 0, 0);
        ProceduralGraph g2 = gen.getGraphForCell(1001L, 0, 0, 0);
        check(g1 == g2, "GraphGenerator cell cache returned memoized graph instance on repeated query");

        List<GraphNode> nodesNear = gen.getNodesNear(1001L, new Vec3d(150, 150, 150), 200.0);
        check(!nodesNear.isEmpty(), "GraphGenerator getNodesNear retrieved nodes across overlapping cell boundaries");
    }

    private static void testGraphDensityEvaluationAndBlending() {
        ProceduralGraph graph = new ProceduralGraph();
        GraphContent c1 = new GraphContent.Builder()
            .addDensity(new ConstantDensity(1.0f), 50.0, "main")
            .build();
        GraphContent c2 = new GraphContent.Builder()
            .addDensity(new ConstantDensity(-1.0f), 50.0, "main")
            .build();

        graph.addNode(new Vec3d(0, 0, 0), c1);
        graph.addNode(new Vec3d(100, 0, 0), c2);

        GraphGenerator gen = new GraphGenerator(List.of(
            (g, s, b) -> {
                for (GraphNode n : graph.getNodes()) {
                    g.addNode(n.getId(), n.getPosition(), n.getContent());
                }
            }
        ));

        GraphDensity graphDensity = new GraphDensity(gen, "main", 0.0f, 123L);

        float dAt0 = graphDensity.compute(0.0f, 0.0f, 0.0f);
        check(Math.abs(dAt0 - 1.0f) < 0.01f, "GraphDensity at node 1 evaluates exactly to child density 1.0");

        float dAt100 = graphDensity.compute(100.0f, 0.0f, 0.0f);
        check(Math.abs(dAt100 - (-1.0f)) < 0.01f, "GraphDensity at node 2 evaluates exactly to child density -1.0");

        float dFar = graphDensity.compute(500.0f, 0.0f, 0.0f);
        check(Math.abs(dFar - 0.0f) < 0.01f, "GraphDensity out of range evaluates to backgroundValue 0.0");

        float[] col = new float[16];
        graphDensity.computeColumn(0.0f, 0.0f, 0, 16, col);
        check(Math.abs(col[0] - 1.0f) < 0.01f && Math.abs(col[15] - 1.0f) < 0.01f, "GraphDensity computeColumn matches point evaluation");
    }

    private static void testGraphMaterialProviderAndProp() {
        ProceduralGraph graph = new ProceduralGraph();
        GraphContent cStone = new GraphContent.Builder()
            .addMaterial(new ConstantMaterialProvider<>("stone"), 30.0, "terrain")
            .addProp(new BlockProp<>("poppy"), 30.0, "flora")
            .build();
        GraphContent cDirt = new GraphContent.Builder()
            .addMaterial(new ConstantMaterialProvider<>("dirt"), 30.0, "terrain")
            .build();

        graph.addNode(new Vec3d(0, 64, 0), cStone);
        graph.addNode(new Vec3d(100, 64, 0), cDirt);

        GraphGenerator gen = new GraphGenerator(List.of(
            (g, s, b) -> {
                for (GraphNode n : graph.getNodes()) {
                    g.addNode(n.getId(), n.getPosition(), n.getContent());
                }
            }
        ));

        GraphMaterialProvider<String> matProv = new GraphMaterialProvider<>(
            gen, "terrain", new ConstantMaterialProvider<>("bedrock"), 42L
        );

        MockVoxelSpace space = new MockVoxelSpace(-64, 320);
        check("stone".equals(matProv.getMaterial(5, 64, 0, 0, 5, 0.0f, space)), "GraphMaterialProvider resolves stone near node 1");
        check("dirt".equals(matProv.getMaterial(95, 64, 0, 0, 5, 0.0f, space)), "GraphMaterialProvider resolves dirt near node 2");
        check("bedrock".equals(matProv.getMaterial(500, 64, 0, 0, 5, 0.0f, space)), "GraphMaterialProvider falls back to bedrock out of range");

        GraphProp<String> graphProp = new GraphProp<>(gen, "flora");
        Prop.Context<String> ctx = new Prop.Context<>(0, 65, 0, 0, 1, 0, space, 42L);
        boolean placed = graphProp.generate(ctx);
        check(placed, "GraphProp generated successfully from node content in range");
        check("poppy".equals(space.getBlock(0, 65, 0)), "GraphProp stamped block state 'poppy'");
    }

    private static void testSplineRoadEngineAndRiverCarverBridges() {
        ProceduralGraph graph = new ProceduralGraph();
        GraphNode n1 = graph.addNode(new Vec3d(0, 64, 0), GraphContent.EMPTY);
        GraphNode n2 = graph.addNode(new Vec3d(100, 64, 0), GraphContent.EMPTY);
        graph.addEdge(n1.getId(), n2.getId());

        // SplineRoadEngine bridge
        SplineRoadEngine roadEngine = new SplineRoadEngine();
        roadEngine.buildFromProceduralGraph(graph, 8.0f, 4.0f);
        check(roadEngine.getRoadCount() == 1, "SplineRoadEngine built 1 road spline from ProceduralGraph edges");

        SplineRoadEngine.RoadQueryResult roadQuery = roadEngine.queryRoad(50.0f, 0.0f);
        check(roadQuery.active(), "Road query active along ProceduralGraph edge");
        check(roadQuery.isRoadDeck(), "Road query detects road deck");
        check(Math.abs(roadQuery.roadElevation() - 64.0f) < 0.5f, "Road elevation matches node elevation 64.0");

        // RiverCarver bridge
        RiverCarver riverCarver = new RiverCarver(12345L, 63, 6);
        riverCarver.setRiverGraph(graph, 40.0f);
        check(riverCarver.getRiverGraph() == graph, "RiverCarver registered river ProceduralGraph");

        float distCenter = riverCarver.riverDistance(50.0f, 0.0f);
        check(distCenter < 0.001f, "RiverCarver riverDistance is 0.0 on graph river centerline");
        check(riverCarver.isChannel(50.0f, 0.0f), "RiverCarver confirms point lies inside river channel");

        float distFar = riverCarver.riverDistance(50.0f, 1000.0f);
        check(distFar > 0.0f, "RiverCarver riverDistance > 0 away from river centerline");
    }

    private static void testJsonAstCompilationOfGraph() {
        String json = """
        {
            "type": "GraphDensity",
            "contentLayer": "surface",
            "backgroundValue": 0.5,
            "graphGenerator": {
                "cacheCapacity": 20,
                "cacheCellSize": {"X": 256, "Y": 256, "Z": 256},
                "passes": [
                    {
                        "type": "Manual",
                        "nodes": [
                            {
                                "position": {"X": 10, "Y": 20, "Z": 30},
                                "name": "nodeA",
                                "content": {
                                    "densityContent": [
                                        {
                                            "density": {"type": "ConstantDensity", "value": 0.9},
                                            "range": 40.0,
                                            "contentLayer": "surface"
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                ]
            }
        }
        """;

        JsonAstCompiler compiler = new JsonAstCompiler();
        DensityFunction density = compiler.compileDensityJson(json, 777L);
        check(density != null, "AST compiled GraphDensity JSON without error");
        check(density instanceof GraphDensity, "Compiled node is instance of GraphDensity");

        float valNear = density.compute(10.0f, 20.0f, 30.0f);
        check(Math.abs(valNear - 0.9f) < 0.05f, "Compiled GraphDensity evaluates to 0.9 near node");

        float valFar = density.compute(1000.0f, 20.0f, 30.0f);
        check(Math.abs(valFar - 0.5f) < 0.01f, "Compiled GraphDensity evaluates to backgroundValue 0.5 far away");
    }

    private static final class MockVoxelSpace implements VoxelSpace<String> {
        private final int minY;
        private final int maxY;
        private final Map<Long, String> blocks = new HashMap<>();

        MockVoxelSpace(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        private static long posKey(int x, int y, int z) {
            return (((long) x & 0x3FFFFFF) << 38) | (((long) y & 0xFFF) << 26) | ((long) z & 0x3FFFFFF);
        }

        @Override
        public String getBlock(int x, int y, int z) {
            return blocks.get(posKey(x, y, z));
        }

        @Override
        public void setBlock(int x, int y, int z, String block) {
            blocks.put(posKey(x, y, z), block);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return getBlock(x, y, z) == null;
        }

        @Override
        public int getMinY() { return minY; }

        @Override
        public int getMaxY() { return maxY; }
    }
}
