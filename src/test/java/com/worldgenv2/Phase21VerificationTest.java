package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phase 21 verification: Hytale props, assignments and positions providers behave as the documentation says. */
public final class Phase21VerificationTest {

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
    private static Map<String, Object> parse(String json) { return (Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static JsonAstCompiler.CompileContext ctx() { return new JsonAstCompiler.CompileContext(1L); }

    private static Prop<String> prop(String json) { return JsonAstCompiler.compileProp(parse(json), ctx(), id -> id); }

    private static Assignments<String> asg(String json) { return JsonAstCompiler.compileAssignments(parse(json), ctx(), id -> id); }

    private static PositionsProvider pos(String json) { return JsonAstCompiler.compilePositionsProvider(parse(json), ctx()); }

    private static boolean run(Prop<String> p, Space s, int x, int y, int z) { return p.generate(new Prop.Context<>(x, y, z, 0, 1, 0, s, 3L)); }

    private static final String BLOCK = "{\"Type\":\"BlockProp\",\"Block\":\"x\"}";

    public static void main(String[] args) {
        System.out.println("=== Phase 21 Verification Test Suite: Hytale props, assignments and positions ===");
        JsonAstCompiler.registerBuiltInTypes();

        // ---------------- props
        Space s = new Space();
        run(prop("{\"Type\":\"Offset\",\"Offset\":{\"X\":2,\"Y\":3,\"Z\":-1},\"Prop\":" + BLOCK + "}"), s, 10, 10, 10);
        check("x".equals(s.getBlock(12, 13, 9)), "Offset moves the child by a 3D vector");

        s = new Space();
        run(prop("{\"Type\":\"Manual\",\"Blocks\":[{\"Offset\":{\"X\":1,\"Y\":0,\"Z\":0},\"Material\":\"a\"},{\"Offset\":{\"X\":0,\"Y\":2,\"Z\":0},\"Material\":\"b\"}]}"), s, 5, 5, 5);
        check("a".equals(s.getBlock(6, 5, 5)) && "b".equals(s.getBlock(5, 7, 5)), "Manual places blocks at offsets from the anchor");

        s = new Space();
        run(prop("{\"Type\":\"StaticRotator\",\"Rotation\":90,\"Prop\":{\"Type\":\"Manual\",\"Blocks\":[{\"Offset\":{\"X\":2,\"Y\":0,\"Z\":0},\"Material\":\"a\"}]}}"), s, 5, 5, 5);
        check("a".equals(s.getBlock(5, 5, 7)) && "air".equals(s.getBlock(7, 5, 5)), "StaticRotator turns the child (+X becomes +Z for a quarter turn)");

        s = new Space();
        run(prop("{\"Type\":\"Cuboid\",\"Bounds\":{\"Min\":{\"X\":-1,\"Y\":0,\"Z\":-1},\"Max\":{\"X\":1,\"Y\":1,\"Z\":1}},\"Material\":{\"Type\":\"Constant\",\"Material\":\"stone\"}}"), s, 5, 5, 5);
        int stones = 0;
        for (int x = 3; x <= 7; x++) for (int y = 4; y <= 7; y++) for (int z = 3; z <= 7; z++) if ("stone".equals(s.getBlock(x, y, z))) stones++;
        check(stones == 18, "Cuboid fills its bounds with the MaterialProvider's blocks (3 x 2 x 3 = 18)");

        s = new Space();
        run(prop("{\"Type\":\"Density\",\"Bounds\":{\"Min\":{\"X\":-2,\"Y\":0,\"Z\":-2},\"Max\":{\"X\":2,\"Y\":3,\"Z\":2}},\"Density\":{\"Type\":\"Constant\",\"Value\":1},\"Material\":{\"Type\":\"Constant\",\"Material\":\"rock\"}}"), s, 5, 5, 5);
        check("rock".equals(s.getBlock(5, 6, 5)), "Density generates blocks wherever its density field is solid");

        Prop<String> sel = prop("{\"Type\":\"DensitySelector\",\"Density\":{\"Type\":\"Constant\",\"Value\":0.5},\"Delimiters\":["
                + "{\"Min\":-1,\"Max\":0.2,\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"low\"}},"
                + "{\"Min\":0.2,\"Max\":1,\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"high\"}}]}");
        s = new Space();
        run(sel, s, 5, 5, 5);
        check("high".equals(s.getBlock(5, 5, 5)), "DensitySelector uses the first delimiter whose range contains the density value");

        Prop<String> mask = prop("{\"Type\":\"Mask\",\"Mask\":{\"DontPlace\":[\"forbidden\"],\"DontReplace\":[\"bedrock\",\"stone\"],"
                + "\"Advanced\":[{\"Source\":[\"pick\"],\"CanReplace\":[\"stone\"]}]},\"Prop\":{\"Type\":\"Manual\",\"Blocks\":["
                + "{\"Offset\":{\"X\":0,\"Y\":0,\"Z\":0},\"Material\":\"forbidden\"},{\"Offset\":{\"X\":1,\"Y\":0,\"Z\":0},\"Material\":\"plain\"},"
                + "{\"Offset\":{\"X\":2,\"Y\":0,\"Z\":0},\"Material\":\"plain\"},{\"Offset\":{\"X\":3,\"Y\":0,\"Z\":0},\"Material\":\"pick\"},{\"Offset\":{\"X\":4,\"Y\":0,\"Z\":0},\"Material\":\"plain\"}]}}");
        s = new Space();
        s.setBlock(1, 5, 5, "bedrock");
        s.setBlock(2, 5, 5, "stone");
        s.setBlock(3, 5, 5, "stone");
        run(mask, s, 0, 5, 5);
        check("air".equals(s.getBlock(0, 5, 5)), "Mask: blocks in DontPlace are never placed");
        check("bedrock".equals(s.getBlock(1, 5, 5)) && "stone".equals(s.getBlock(2, 5, 5)), "Mask: a placed block may not replace a block in DontReplace");
        check("pick".equals(s.getBlock(3, 5, 5)), "Mask: an Advanced rule lets its Source replace what its CanReplace names");
        check("plain".equals(s.getBlock(4, 5, 5)), "Mask: anything else is placed normally");

        Prop<String> queue = prop("{\"Type\":\"Queue\",\"Props\":[{\"Type\":\"Locator\",\"PlacementCap\":1,\"Scanner\":{\"Type\":\"Origin\"},\"Pattern\":{\"Type\":\"Constant\",\"Value\":false},\"Prop\":"
                + BLOCK + "},{\"Type\":\"BlockProp\",\"Block\":\"second\"},{\"Type\":\"BlockProp\",\"Block\":\"third\"}]}");
        s = new Space();
        check(run(queue, s, 1, 1, 1) && "second".equals(s.getBlock(1, 1, 1)), "Queue places the first prop that generates and skips the rest");
        s = new Space();
        run(prop("{\"Type\":\"Union\",\"Props\":[{\"Type\":\"BlockProp\",\"Block\":\"a\"},{\"Type\":\"Offset\",\"Offset\":{\"X\":1,\"Y\":0,\"Z\":0},\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"b\"}}]}"), s, 1, 1, 1);
        check("a".equals(s.getBlock(1, 1, 1)) && "b".equals(s.getBlock(2, 1, 1)), "Union places every prop at the same position, one after another");

        Prop<String> weighted = prop("{\"Type\":\"Weighted\",\"Seed\":\"w\",\"Entries\":[{\"Weight\":1,\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"rare\"}},{\"Weight\":9,\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"common\"}}]}");
        int common = 0;
        for (int i = 0; i < 400; i++) {
            Space w = new Space();
            weighted.generate(new Prop.Context<>(0, 0, 0, 0, 1, 0, w, i * 7919L));
            if ("common".equals(w.getBlock(0, 0, 0))) common++;
        }
        check(common > 300 && common < 400, "Weighted picks by weight (about 90% common here: " + common + " of 400)");

        // ---------------- assignments
        check(asg("{\"Type\":\"Constant\",\"Prop\":" + BLOCK + "}").assign(new Vec3d(1, 2, 3), 9L) != null, "Constant assigns the prop to every position");
        Assignments<String> skipAll = asg("{\"Type\":\"Weighted\",\"Seed\":\"s\",\"SkipChance\":1.0,\"WeightedAssignments\":[{\"Weight\":1,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":" + BLOCK + "}}]}");
        boolean any = false;
        for (int i = 0; i < 50; i++) any |= skipAll.assign(new Vec3d(i, 0, i), i) != null;
        check(!any, "Weighted with SkipChance 1 assigns nothing");
        Assignments<String> never = asg("{\"Type\":\"Weighted\",\"Seed\":\"s\",\"SkipChance\":0.0,\"WeightedAssignments\":[{\"Weight\":1,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":" + BLOCK + "}}]}");
        boolean all = true;
        for (int i = 0; i < 50; i++) all &= never.assign(new Vec3d(i, 0, i), i) != null;
        check(all, "Weighted with SkipChance 0 always assigns");
        Assignments<String> ff = asg("{\"Type\":\"FieldFunction\",\"FieldFunction\":{\"Type\":\"Constant\",\"Value\":0.5},\"Delimiters\":["
                + "{\"Min\":0,\"Max\":0.5,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"below\"}}},"
                + "{\"Min\":0.5,\"Max\":1,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"above\"}}}]}");
        Space fs = new Space();
        ff.assign(new Vec3d(0, 0, 0), 1L).generate(new Prop.Context<>(0, 0, 0, 0, 1, 0, fs, 1L));
        check("above".equals(fs.getBlock(0, 0, 0)), "FieldFunction picks by density; min is inclusive, max exclusive (0.5 falls in [0.5, 1))");
        Assignments<String> sand = asg("{\"Type\":\"Sandwich\",\"Delimiters\":[{\"MinY\":0,\"MaxY\":40,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"deep\"}}},"
                + "{\"MinY\":40,\"MaxY\":100,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":{\"Type\":\"BlockProp\",\"Block\":\"high\"}}}]}");
        Space ss = new Space();
        sand.assign(new Vec3d(0, 40, 0), 1L).generate(new Prop.Context<>(0, 0, 0, 0, 1, 0, ss, 1L));
        check("high".equals(ss.getBlock(0, 0, 0)) && sand.assign(new Vec3d(0, 100, 0), 1L) == null, "Sandwich picks by world Y: min inclusive, max exclusive");

        // ---------------- positions
        String G = "{\"Type\":\"SquareGrid2d\"}";
        check(pos(G).getPositions(0, 0, 0, 16, 16, 16).size() == 289, "SquareGrid2d has one block between positions (17 x 17 in a 16 block box)");
        check(pos("{\"Type\":\"Scaler\",\"Scale\":4,\"Positions\":" + G + "}").getPositions(0, 0, 0, 16, 16, 16).size() == 25, "Scaler scales the field (spacing 4 gives 5 x 5)");
        check(pos("{\"Type\":\"Scaler\",\"Scale\":{\"X\":2,\"Y\":1,\"Z\":8},\"Positions\":" + G + "}").getPositions(0, 0, 0, 16, 16, 16).size() == 9 * 3, "Scaler takes a 3D scale");
        check(pos("{\"Type\":\"Bound\",\"Bounds\":{\"Min\":{\"X\":0,\"Y\":0,\"Z\":0},\"Max\":{\"X\":4,\"Y\":4,\"Z\":4}},\"Positions\":" + G + "}").getPositions(-50, -50, -50, 50, 50, 50).size() == 25,
                "Bound limits an infinite field to a region");
        List<Vec3d> off = pos("{\"Type\":\"Offset\",\"Offset\":{\"X\":0.5,\"Y\":7,\"Z\":0},\"Positions\":" + G + "}").getPositions(0, 0, 0, 4, 10, 4);
        check(!off.isEmpty() && off.stream().allMatch(p -> p.y() == 7.0 && p.x() % 1.0 == 0.5), "Offset moves every position by the vector");
        List<Vec3d> jit = pos("{\"Type\":\"Jitter2d\",\"Magnitude\":1.5,\"Seed\":\"j\",\"Positions\":" + G + "}").getPositions(0, 0, 0, 10, 10, 10);
        boolean within = true, moved = false;
        for (Vec3d p : jit) { double dx = p.x() - Math.round(p.x()), dz = p.z() - Math.round(p.z()); if (p.y() != 0) within = false; if (Math.abs(dx) > 1e-9 || Math.abs(dz) > 1e-9) moved = true; }
        check(!jit.isEmpty() && within && moved, "Jitter2d moves positions in the x/z plane only");
        check(pos("{\"Type\":\"Jitter2d\",\"Magnitude\":1.5,\"Seed\":\"j\",\"Positions\":" + G + "}").getPositions(0, 0, 0, 10, 10, 10).size() == jit.size(), "jitter is deterministic for a seed");
        List<Vec3d> dj = pos("{\"Type\":\"DirectionalJitter\",\"Magnitude\":3,\"Direction\":{\"X\":0,\"Y\":1,\"Z\":0},\"IsBidirectional\":false,\"Seed\":\"d\",\"Positions\":" + G + "}").getPositions(0, 0, 0, 8, 10, 8);
        check(!dj.isEmpty() && dj.stream().allMatch(p -> p.y() >= 0 && p.y() <= 3 && Math.abs(p.x() - Math.round(p.x())) < 1e-9), "DirectionalJitter moves along the direction only (and only forward unless bidirectional)");
        int total = pos(G).getPositions(0, 0, 0, 30, 5, 30).size();
        int kept = pos("{\"Type\":\"Occurrence\",\"FieldFunction\":{\"Type\":\"Constant\",\"Value\":0.25},\"Seed\":\"o\",\"Positions\":" + G + "}").getPositions(0, 0, 0, 30, 5, 30).size();
        check(kept > total * 0.15 && kept < total * 0.35, "Occurrence keeps positions with the density's probability (about 25%: " + kept + " of " + total + ")");
        check(pos("{\"Type\":\"Occurrence\",\"FieldFunction\":{\"Type\":\"Constant\",\"Value\":0},\"Seed\":\"o\",\"Positions\":" + G + "}").getPositions(0, 0, 0, 30, 5, 30).isEmpty(), "Occurrence with density 0 keeps nothing");
        check(pos("{\"Type\":\"FieldFunction\",\"Density\":{\"Type\":\"Constant\",\"Value\":2},\"Delimiters\":[{\"Min\":0,\"Max\":1}],\"Positions\":" + G + "}").getPositions(0, 0, 0, 5, 5, 5).isEmpty(),
                "FieldFunction masks out positions outside its delimiters");
        check(pos("{\"Type\":\"Union\",\"Positions\":[" + G + ",{\"Type\":\"List\",\"Positions\":[{\"X\":1,\"Y\":1,\"Z\":1}]}]}").getPositions(0, 0, 0, 4, 4, 4).size() == 26, "Union combines fields; List gives static positions");
        check(pos("{\"Type\":\"SimpleHorizontal\",\"RangeY\":{\"Min\":2,\"Max\":3},\"Positions\":{\"Type\":\"SquareGrid3d\"}}").getPositions(0, 0, 0, 2, 10, 2).stream().allMatch(p -> p.y() >= 2 && p.y() <= 3),
                "SimpleHorizontal keeps only positions inside the Y range");
        check(pos("{\"Type\":\"Cache\",\"SectionSize\":16,\"CacheSize\":8,\"Positions\":" + G + "}").getPositions(0, 0, 0, 20, 5, 20).size() == pos(G).getPositions(0, 0, 0, 20, 5, 20).size(),
                "Cache returns exactly what the child would");
        // clusters: three anchors, each with its own small grid
        PositionsProvider clusters = pos("{\"Type\":\"Clusters\",\"ClusterBounds\":{\"Min\":{\"X\":-2,\"Y\":0,\"Z\":-2},\"Max\":{\"X\":2,\"Y\":0,\"Z\":2}},"
                + "\"Distributor\":{\"Type\":\"List\",\"Positions\":[{\"X\":10,\"Y\":0,\"Z\":10},{\"X\":30,\"Y\":0,\"Z\":10}]},"
                + "\"Cluster\":{\"Type\":\"Anchor\",\"Positions\":{\"Type\":\"Bound\",\"Bounds\":{\"Min\":{\"X\":-1,\"Y\":0,\"Z\":-1},\"Max\":{\"X\":1,\"Y\":0,\"Z\":1}},\"Positions\":" + G + "}}}");
        List<Vec3d> cl = clusters.getPositions(0, 0, 0, 40, 5, 40);
        boolean near = cl.stream().allMatch(p -> (Math.abs(p.x() - 10) <= 1 && Math.abs(p.z() - 10) <= 1) || (Math.abs(p.x() - 30) <= 1 && Math.abs(p.z() - 10) <= 1));
        check(cl.size() == 18 && near, "Clusters anchors each cluster's positions at the distributor's points (2 anchors x 9)");

        System.out.println("Phase 21 verification: ALL " + passed + " TESTS PASSED!");
    }
}
