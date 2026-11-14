package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.assignments.*;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.ConstantDensity;
import com.worldgenv2.density.VerticalGradientDensity;
import com.worldgenv2.positions.*;
import com.worldgenv2.props.AssignmentsProp;
import com.worldgenv2.props.BlockProp;
import com.worldgenv2.props.Prop;
import com.worldgenv2.scanner.*;
import com.worldgenv2.vector.ConstantVectorProvider;
import com.worldgenv2.vector.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 10 Verification Test Suite: Positions Providers, Scanners & Assignments.
 */
public final class Phase10VerificationTest {

    private static int testsPassed = 0;

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("[FAIL] " + description);
            throw new AssertionError("Phase 10 assertion failed: " + description);
        }
        System.out.println("[PASS] " + description);
        testsPassed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 10 Verification Test Suite ===");

        testPositionsProviders();
        testScanners();
        testAssignments();
        testAssignmentsPropPipeline();
        testJsonAstCompilation();

        System.out.println("Phase 10 verification: ALL " + testsPassed + " TESTS PASSED!");
    }

    private static void testPositionsProviders() {
        // SquareGrid2d
        SquareGrid2dPositionsProvider sq2d = new SquareGrid2dPositionsProvider(2.0);
        List<Vec3d> pts2d = sq2d.getPositions(0, -1, 0, 4, 1, 4);
        // (0,0), (0,2), (0,4), (2,0), (2,2), (2,4), (4,0), (4,2), (4,4) -> 9 points
        check(pts2d.size() == 9, "SquareGrid2d generates 9 lattice points in [0..4]x[0..4] with spacing 2.0");

        // TriangularGrid2d
        TriangularGrid2dPositionsProvider tri = new TriangularGrid2dPositionsProvider(2.0);
        List<Vec3d> triPts = tri.getPositions(0, -1, 0, 10, 1, 10);
        check(!triPts.isEmpty(), "TriangularGrid2d generates non-empty hexagonal mesh");

        // Scaler
        ScalerPositionsProvider scaler = new ScalerPositionsProvider(sq2d, new Vec3d(2.0, 1.0, 2.0));
        List<Vec3d> scaledPts = scaler.getPositions(0, -1, 0, 8, 1, 8);
        check(scaledPts.size() == 9, "ScalerPositionsProvider scales coordinates by factor 2.0");

        // Jitter2d
        Jitter2dPositionsProvider jitter = new Jitter2dPositionsProvider(sq2d, 0.5, 12345L);
        List<Vec3d> jPts = jitter.getPositions(0, -1, 0, 4, 1, 4);
        check(!jPts.isEmpty(), "Jitter2d generates non-empty points within bounds");
        boolean displaced = false;
        for (Vec3d p : jPts) {
            if (p.x() % 2.0 != 0.0 || p.z() % 2.0 != 0.0) {
                displaced = true;
                break;
            }
        }
        check(displaced, "Jitter2d displaces points off regular grid");

        // Directional Jitter along X
        DirectionalJitterPositionsProvider dirJitter = new DirectionalJitterPositionsProvider(
            new ListPositionsProvider(new Vec3d(10, 0, 10)),
            new Vec3d(1, 0, 0),
            5.0,
            false,
            42L
        );
        List<Vec3d> dPts = dirJitter.getPositions(0, -10, 0, 20, 10, 20);
        check(dPts.size() == 1, "DirectionalJitter produces 1 point");
        Vec3d displacedPoint = dPts.get(0);
        check(displacedPoint.x() >= 10.0 && displacedPoint.y() == 0.0 && displacedPoint.z() == 10.0,
            "DirectionalJitter strictly displaces along X axis");

        // Occurrence thinning (50% density)
        OccurrencePositionsProvider occ = new OccurrencePositionsProvider(
            new SquareGrid2dPositionsProvider(1.0),
            new ConstantDensity(0.5f),
            999L
        );
        List<Vec3d> occPts = occ.getPositions(0, -1, 0, 19, 1, 19); // 400 candidate points
        check(occPts.size() > 150 && occPts.size() < 250, "OccurrencePositionsProvider statistically thins by ~50%");

        // FieldFunction filtering (delimiters [10..20])
        FieldFunctionPositionsProvider ffPos = new FieldFunctionPositionsProvider(
            new ListPositionsProvider(new Vec3d(5, 5, 5), new Vec3d(15, 15, 15), new Vec3d(25, 25, 25)),
            (x, y, z) -> x, // density equals X
            10.0, 20.0
        );
        List<Vec3d> ffPts = ffPos.getPositions(0, 0, 0, 30, 30, 30);
        check(ffPts.size() == 1 && ffPts.get(0).x() == 15.0, "FieldFunctionPositionsProvider filters points by density range");
    }

    private static void testScanners() {
        MockVoxelSpace space = new MockVoxelSpace(-64, 320);
        // Fill terrain up to Y=63 with "stone", Y >= 64 is air
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = 50; y <= 63; y++) {
                    space.setBlock(x, y, z, "stone");
                }
            }
        }

        // LinearScanner along Y from 50 to 80
        LinearScanner linear = new LinearScanner(LinearScanner.Axis.Y, -10, 20);
        List<Vec3d> surfaces = linear.scan(new Vec3d(0, 60, 0), space, "stone"::equals);
        check(surfaces.size() == 1, "LinearScanner locates exactly one surface");
        check(surfaces.get(0).y() == 64.0, "LinearScanner finds surface block at Y=64 (air above stone at 63)");

        // OriginScanner
        OriginScanner origin = OriginScanner.INSTANCE;
        check(origin.scan(new Vec3d(1, 2, 3), space, "stone"::equals).get(0).equals(new Vec3d(1, 2, 3)), "OriginScanner returns anchor");

        // QueueScanner
        QueueScanner queue = new QueueScanner(linear, origin);
        List<Vec3d> qResult = queue.scan(new Vec3d(0, 60, 0), space, "stone"::equals);
        check(qResult.size() == 1 && qResult.get(0).y() == 64.0, "QueueScanner runs priority chain");
    }

    private static void testAssignments() {
        BlockProp<String> oak = new BlockProp<>("oak_sapling");
        BlockProp<String> birch = new BlockProp<>("birch_sapling");
        BlockProp<String> spruce = new BlockProp<>("spruce_sapling");

        ConstantAssignment<String> constAssign = new ConstantAssignment<>(oak);
        check(constAssign.assign(new Vec3d(0, 0, 0), 1L) == oak, "ConstantAssignment assigns prop");

        // Sandwich assignment: Y < 50: oak, Y in [50, 100): birch, Y >= 100: spruce
        SandwichAssignment<String> sandwich = new SandwichAssignment<>(List.of(
            new SandwichAssignment.HeightDelimiter<>(Double.NEGATIVE_INFINITY, 50.0, new ConstantAssignment<>(oak)),
            new SandwichAssignment.HeightDelimiter<>(50.0, 100.0, new ConstantAssignment<>(birch)),
            new SandwichAssignment.HeightDelimiter<>(100.0, Double.POSITIVE_INFINITY, new ConstantAssignment<>(spruce))
        ));

        check(sandwich.assign(new Vec3d(0, 30, 0), 1L) == oak, "Sandwich assigns oak below Y=50");
        check(sandwich.assign(new Vec3d(0, 75, 0), 1L) == birch, "Sandwich assigns birch at Y=75");
        check(sandwich.assign(new Vec3d(0, 120, 0), 1L) == spruce, "Sandwich assigns spruce above Y=100");

        // Weighted assignment
        WeightedAssignment<String> weighted = new WeightedAssignment<>(List.of(
            new WeightedAssignment.Entry<>(new ConstantAssignment<>(oak), 10.0),
            new WeightedAssignment.Entry<>(new ConstantAssignment<>(spruce), 0.0) // 0 weight
        ), 0.0, 42L);
        check(weighted.assign(new Vec3d(5, 5, 5), 1L) == oak, "WeightedAssignment picks positive weight");
    }

    private static void testAssignmentsPropPipeline() {
        MockVoxelSpace space = new MockVoxelSpace(0, 256);
        // Create ground at Y=10
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                space.setBlock(x, 10, z, "dirt");
            }
        }

        PositionsProvider grid = new SquareGrid2dPositionsProvider(4.0);
        Scanner scanner = new LinearScanner(LinearScanner.Axis.Y, 0, 20);
        Assignments<String> assign = new ConstantAssignment<>(new BlockProp<>("flower"));

        AssignmentsProp<String> propPipeline = new AssignmentsProp<>(
            grid,
            scanner,
            assign,
            "dirt"::equals
        );

        Prop.Context<String> ctx = new Prop.Context<>(
            0, 10, 0,
            0.0f, 1.0f, 0.0f,
            space,
            12345L
        );

        boolean placed = propPipeline.generate(ctx);
        check(placed, "AssignmentsProp successfully executed pipeline");
        // Verify a flower was placed at (0, 11, 0)
        check("flower".equals(space.getBlock(0, 11, 0)), "AssignmentsProp placed flower block at (0, 11, 0) on dirt surface");
    }

    private static void testJsonAstCompilation() {
        JsonAstCompiler compiler = new JsonAstCompiler();

        String posJson = """
            {
                "type": "Jitter2d",
                "magnitude": 2.5,
                "seed": 100,
                "positions": {
                    "type": "SquareGrid2d",
                    "spacing": 4.0
                }
            }
            """;
        Object parsedPos = new JsonAstCompiler.SimpleJsonParser(posJson).parseValue();
        PositionsProvider pos = JsonAstCompiler.compilePositionsProvider(parsedPos, new JsonAstCompiler.CompileContext(123L));
        check(pos != null && !pos.getPositions(0, 0, 0, 16, 0, 16).isEmpty(), "JSON AST compiles a jittered positions field (Hytale Jitter2d over a spaced grid)");

        String scannerJson = """
            {
                "type": "Linear",
                "axis": "Y",
                "minOffset": -10,
                "maxOffset": 10
            }
            """;
        Object parsedScanner = new JsonAstCompiler.SimpleJsonParser(scannerJson).parseValue();
        Scanner sc = JsonAstCompiler.compileScanner(parsedScanner, new JsonAstCompiler.CompileContext(123L));
        check(sc instanceof LinearScanner, "JSON AST compiles LinearScanner");
    }

    // In-memory test voxel space
    private static final class MockVoxelSpace implements VoxelSpace<String> {
        private final int minY;
        private final int maxY;
        private final Map<Long, String> voxels = new HashMap<>();

        MockVoxelSpace(int minY, int maxY) {
            this.minY = minY;
            this.maxY = maxY;
        }

        @Override
        public String getBlock(int x, int y, int z) {
            return voxels.get(posKey(x, y, z));
        }

        @Override
        public void setBlock(int x, int y, int z, String state) {
            voxels.put(posKey(x, y, z), state);
        }

        @Override
        public boolean isAir(int x, int y, int z) {
            return getBlock(x, y, z) == null;
        }

        @Override
        public int getMinY() {
            return minY;
        }

        @Override
        public int getMaxY() {
            return maxY;
        }

        private static long posKey(int x, int y, int z) {
            return ((long) x & 0x3FFFFFF) | (((long) z & 0x3FFFFFF) << 26) | (((long) y & 0xFFF) << 52);
        }
    }
}
