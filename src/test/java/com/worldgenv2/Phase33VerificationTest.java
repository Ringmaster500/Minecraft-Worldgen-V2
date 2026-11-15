package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.hytale.HytaleBlocks;
import com.worldgenv2.props.Prop;

import java.util.HashMap;
import java.util.Map;

/** Phase 33 verification: the shapes Hytale's own V2 asset files use (release schema): In/Out curve points, ranges, overrides, Column / Cluster / Box props. */
public final class Phase33VerificationTest {

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
        public int getMaxY() { return 128; }
    }

    private static Object parse(String json) { return new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    @SuppressWarnings("unchecked")
    private static DensityFunction d(String json, JsonAstCompiler.CompileContext ctx) { return JsonAstCompiler.compileDensity((Map<String, Object>) parse(json), ctx); }

    @SuppressWarnings("unchecked")
    private static Prop<String> prop(String json, JsonAstCompiler.CompileContext ctx) { return JsonAstCompiler.compileProp((Map<String, Object>) parse(json), ctx, id -> id); }

    private static void run(Prop<String> p, Space s, int x, int y, int z) { p.generate(new Prop.Context<>(x, y, z, 0f, 1f, 0f, s, 99L)); }

    private static boolean near(float a, double b) { return Math.abs(a - b) < 1e-3; }

    public static void main(String[] args) {
        System.out.println("=== Phase 33 Verification Test Suite: Hytale's own asset shapes ===");
        JsonAstCompiler.registerBuiltInTypes();
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L);
        ctx.decimalConstants().put("Base", 50.0);

        // curves: Points are { In, Out }
        DensityFunction cm = d("{\"Type\":\"CurveMapper\",\"Curve\":{\"Type\":\"Manual\",\"Points\":[{\"In\":0,\"Out\":1},{\"In\":50,\"Out\":-1}]},\"Inputs\":[{\"Type\":\"YValue\"}]}", ctx);
        check(near(cm.compute(0, 0, 0), 1) && near(cm.compute(0, 25, 0), 0) && near(cm.compute(0, 80, 0), -1), "a Manual curve reads its points as { In, Out }");

        // overrides: YOverride has a Value, and a column of voxels costs one evaluation
        DensityFunction yo = d("{\"Type\":\"YOverride\",\"Value\":7,\"Inputs\":[{\"Type\":\"YValue\"}]}", ctx);
        check(near(yo.compute(3, 99, 4), 7) && near(d("{\"Type\":\"XOverride\",\"Value\":5,\"Inputs\":[{\"Type\":\"XValue\"}]}", ctx).compute(1, 2, 3), 5), "YOverride and XOverride replace the coordinate the input sees with Value");
        float[] col = new float[10];
        yo.computeColumn(0, 0, 0, 10, col);
        check(col[0] == 7f && col[9] == 7f, "a column through YOverride is constant");
        check(near(d("{\"Type\":\"Cache2D\",\"Y\":4,\"CacheSize\":16,\"Inputs\":[{\"Type\":\"YValue\"}]}", ctx).compute(0, 99, 0), 4), "Cache2D reads its input at Y");

        // legacy constants and amplitude nodes
        check(near(d("{\"Type\":\"OffsetConstant\",\"Value\":2,\"Inputs\":[{\"Type\":\"Constant\",\"Value\":3}]}", ctx).compute(0, 0, 0), 5) && near(d("{\"Type\":\"AmplitudeConstant\",\"Value\":2,\"Inputs\":[{\"Type\":\"Constant\",\"Value\":3}]}", ctx).compute(0, 0, 0), 6), "OffsetConstant adds and AmplitudeConstant multiplies");
        DensityFunction amp = d("{\"Type\":\"Amplitude\",\"FunctionForY\":{\"Points\":[{\"Y\":0,\"Out\":0},{\"Y\":100,\"Out\":2}]},\"Inputs\":[{\"Type\":\"Constant\",\"Value\":1}]}", ctx);
        check(near(amp.compute(0, 50, 0), 1) && near(amp.compute(0, 200, 0), 2), "Amplitude multiplies by a function of Y");
        DensityFunction pipe = d("{\"Type\":\"Pipeline\",\"Pipeline\":[{\"Type\":\"Inverter\"},{\"Type\":\"Pow\",\"Exponent\":2}],\"Inputs\":[{\"Type\":\"Constant\",\"Value\":3}]}", ctx);
        check(near(pipe.compute(0, 0, 0), -9), "Pipeline applies its stages to the input in order (Inverter, then the sign-preserving Pow)");
        DensityFunction ys = d("{\"Type\":\"YSampled\",\"SampleDistance\":10,\"SampleOffset\":0,\"Interpolate\":true,\"Inputs\":[{\"Type\":\"YValue\"}]}", ctx);
        float[] yc = new float[40];
        ys.computeColumn(0, 0, 5, 45, yc);
        check(near(yc[0], 5) && near(yc[7], 12) && near(ys.compute(0, 23, 0), 23), "YSampled interpolates between samples taken every SampleDistance blocks");
        check(near(d("{\"Type\":\"BaseHeight\",\"BaseHeightName\":\"Base\",\"Distance\":true}", ctx).compute(0, 60, 0), 10), "BaseHeight reads the world structure's constant");

        // scanners and the release props
        Space s = new Space();
        for (int x = 0; x < 20; x++) for (int z = 0; z < 20; z++) for (int y = 0; y <= 30; y++) s.setBlock(x, y, z, "stone");
        String floorGrass = "{\"Type\":\"Floor\",\"Origin\":{\"Type\":\"BlockSet\",\"BlockSet\":{\"Inclusive\":true,\"Materials\":[{\"Solid\":\"Empty\"}]}},\"Floor\":{\"Type\":\"BlockSet\",\"BlockSet\":{\"Inclusive\":true,\"Materials\":[{\"Solid\":\"stone\"}]}}}";
        String scan = "{\"Type\":\"ColumnLinear\",\"MinY\":0,\"MaxY\":60,\"RelativeToPosition\":false,\"BaseHeightName\":\"\",\"TopDownOrder\":true,\"ResultCap\":1}";
        run(prop("{\"Type\":\"Column\",\"ColumnBlocks\":[{\"Y\":0,\"Material\":{\"Solid\":\"bush\"}},{\"Y\":1,\"Material\":{\"Solid\":\"bush\"}}],\"Directionality\":{\"Type\":\"Static\",\"Rotation\":0,\"Pattern\":" + floorGrass + "},\"Scanner\":" + scan + "}", ctx), s, 5, 0, 5);
        check("bush".equals(s.getBlock(5, 31, 5)) && "bush".equals(s.getBlock(5, 32, 5)) && "air".equals(s.getBlock(5, 33, 5)), "Column with a ColumnLinear scanner finds the surface top-down and stacks its blocks there (ResultCap 1)");

        Space s2 = new Space();
        for (int x = 0; x < 30; x++) for (int z = 0; z < 30; z++) for (int y = 0; y <= 30; y++) s2.setBlock(x, y, z, "stone");
        String col1 = "{\"Type\":\"Column\",\"ColumnBlocks\":[{\"Y\":0,\"Material\":{\"Solid\":\"rubble\"}}],\"Directionality\":{\"Type\":\"Static\",\"Rotation\":0,\"Pattern\":" + floorGrass + "},\"Scanner\":{\"Type\":\"ColumnLinear\",\"MinY\":-5,\"MaxY\":5,\"RelativeToPosition\":true,\"BaseHeightName\":\"\",\"TopDownOrder\":false,\"ResultCap\":1}}";
        run(prop("{\"Type\":\"Cluster\",\"Range\":6,\"Seed\":\"c\",\"DistanceCurve\":{\"Type\":\"Manual\",\"Points\":[{\"In\":0,\"Out\":1},{\"In\":6,\"Out\":0}]},\"WeightedProps\":[{\"Weight\":1,\"ColumnProp\":" + col1 + "}],\"Pattern\":" + floorGrass + ",\"Scanner\":" + scan + "}", ctx), s2, 15, 0, 15);
        int rub = 0, far = 0;
        for (int x = 0; x < 30; x++) for (int z = 0; z < 30; z++) if ("rubble".equals(s2.getBlock(x, 31, z))) { rub++; if (Math.hypot(x - 15, z - 15) > 6.5) far++; }
        check(rub > 3 && far == 0, "Cluster scatters its props within Range of the anchor, thinning out with the DistanceCurve (" + rub + " placed)");

        Space s3 = new Space();
        for (int x = 0; x < 30; x++) for (int z = 0; z < 30; z++) for (int y = 0; y <= 30; y++) s3.setBlock(x, y, z, "stone");
        run(prop("{\"Type\":\"Box\",\"Range\":{\"X\":3,\"Y\":2,\"Z\":3},\"Material\":{\"Solid\":\"dirt\"},\"Pattern\":" + floorGrass + ",\"Scanner\":" + scan + "}", ctx), s3, 15, 0, 15);
        check("dirt".equals(s3.getBlock(15, 31, 15)) && "dirt".equals(s3.getBlock(18, 31, 12)) && "air".equals(s3.getBlock(19, 31, 15)), "Box fills a box of Material around the position the Pattern accepts");

        // Range written as MinInclusive / MaxExclusive
        Space s4 = new Space();
        run(prop("{\"Type\":\"Locator\",\"PlacementCap\":1,\"Prop\":{\"Type\":\"Manual\",\"Blocks\":[{\"Position\":{\"X\":0,\"Y\":0,\"Z\":0},\"Material\":{\"Solid\":\"mark\"}}]},\"Pattern\":{\"Type\":\"Constant\",\"Value\":true},\"Scanner\":{\"Type\":\"Linear\",\"Axis\":\"Y\",\"Range\":{\"MinInclusive\":2,\"MaxExclusive\":5}}}", ctx), s4, 0, 10, 0);
        check("mark".equals(s4.getBlock(0, 12, 0)), "a Linear scanner's Range is [MinInclusive, MaxExclusive)");

        // Imported finds the export of the right family when names are shared
        JsonAstCompiler.CompileContext shared = new JsonAstCompiler.CompileContext(1L);
        JsonAstCompiler.collectExports(parse("{\"Type\":\"Constant\",\"ExportAs\":\"Same\",\"Prop\":{\"Type\":\"Manual\",\"Blocks\":[]}}"), shared);
        JsonAstCompiler.collectExports(parse("{\"Type\":\"Constant\",\"ExportAs\":\"Same\",\"Value\":4.5}"), shared);
        check(near(d("{\"Type\":\"Imported\",\"Name\":\"Same\"}", shared).compute(0, 0, 0), 4.5), "Imported picks the export of its own family when an assignments asset shares the name");

        // block names
        check("minecraft:grass_block".equals(HytaleBlocks.toMinecraft("Soil_Grass_Deep")) && "minecraft:stone".equals(HytaleBlocks.toMinecraft("Rock_Stone")) && "minecraft:water".equals(HytaleBlocks.toMinecraft("Water_Source"))
                && "minecraft:air".equals(HytaleBlocks.toMinecraft("Empty")) && "minecraft:poppy".equals(HytaleBlocks.toMinecraft("Plant_Flower_Common_Red2")) && HytaleBlocks.toMinecraft("minecraft:stone") == null,
                "Hytale block names map to Minecraft blocks (exact names, then the longest prefix); Minecraft ids are left alone");

        System.out.println("Phase 33 verification: ALL " + passed + " TESTS PASSED!");
    }
}