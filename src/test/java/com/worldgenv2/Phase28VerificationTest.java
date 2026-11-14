package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.prefab.Prefab;
import com.worldgenv2.props.Prop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Phase 28 verification: PondFiller, RandomRotator and Prefab folders (Hytale's prop semantics). */
public final class Phase28VerificationTest {

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

    private static Prop<String> prop(String json, JsonAstCompiler.CompileContext ctx) { return JsonAstCompiler.compileProp((Map<String, Object>) parse(json), ctx, id -> id); }

    private static void run(Prop<String> p, Space s, int x, int y, int z, long seed) { p.generate(new Prop.Context<>(x, y, z, 0f, 1f, 0f, s, seed)); }

    /** Flat stone up to y=10, with a bowl: columns within 3 of (20,20) have their floor at y=6. */
    private static Space bowl(boolean notch) {
        Space s = new Space();
        for (int x = 0; x < 41; x++) for (int z = 0; z < 41; z++) {
            int top = 10;
            if (Math.hypot(x - 20, z - 20) <= 3) top = 6;
            if (notch && x >= 24 && z == 20) top = 8;   // a low channel out of the bowl, to the edge
            for (int y = 0; y <= top; y++) s.setBlock(x, y, z, "stone");
        }
        return s;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 28 Verification Test Suite: PondFiller, RandomRotator, Prefab folders ===");
        JsonAstCompiler.registerBuiltInTypes();
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L);

        String pond = "{\"Type\":\"PondFiller\",\"Bounds\":{\"Min\":{\"X\":-15,\"Y\":-6,\"Z\":-15},\"Max\":{\"X\":15,\"Y\":6,\"Z\":15}},\"BarrierBlockSet\":[\"stone\"],\"FillMaterial\":{\"Type\":\"Constant\",\"Material\":\"water\"}}";
        Space s = bowl(false);
        run(prop(pond, ctx), s, 20, 10, 20, 1L);
        check("water".equals(s.getBlock(20, 7, 20)) && "water".equals(s.getBlock(20, 10, 20)) && "water".equals(s.getBlock(22, 9, 20)) && "air".equals(s.getBlock(20, 11, 20)), "PondFiller fills an existing depression up to its rim");
        check("stone".equals(s.getBlock(20, 6, 20)) && "stone".equals(s.getBlock(30, 10, 30)) && "air".equals(s.getBlock(30, 11, 30)), "walls and the flat ground around are left alone");
        s = bowl(true);
        run(prop(pond, ctx), s, 20, 10, 20, 1L);
        check("water".equals(s.getBlock(20, 8, 20)) && "air".equals(s.getBlock(20, 9, 20)), "water rests at the lowest rim (a channel at 8 keeps the pond at 8)");
        s = bowl(false);
        run(prop("{\"Type\":\"PondFiller\",\"Bounds\":{\"Min\":{\"X\":-2,\"Y\":-6,\"Z\":-2},\"Max\":{\"X\":2,\"Y\":6,\"Z\":2}},\"BarrierBlockSet\":[\"stone\"],\"FillMaterial\":{\"Type\":\"Constant\",\"Material\":\"water\"}}", ctx), s, 20, 10, 20, 1L);
        check("air".equals(s.getBlock(20, 8, 20)), "a depression larger than Bounds is not filled (it would spill over the box's edge)");

        // RandomRotator: Rotations list and HorizontalRotations
        String arm = "{\"Type\":\"Manual\",\"Blocks\":[{\"Offset\":{\"X\":2,\"Y\":0,\"Z\":0},\"Material\":\"arm\"}]}";
        Set<String> only = new TreeSet<>();
        for (int i = 0; i < 40; i++) {
            Space t = new Space();
            run(prop("{\"Type\":\"RandomRotator\",\"Rotations\":[0],\"Seed\":\"r\",\"Prop\":" + arm + "}", ctx), t, 10 + i, 5, 10, 7L + i);
            only.add(t.getBlock(12 + i, 5, 10));
        }
        check(only.equals(Set.of("arm")), "RandomRotator with only a 0 rotation never turns the child");
        Set<String> dirs = new TreeSet<>();
        for (int i = 0; i < 80; i++) {
            Space t = new Space();
            run(prop("{\"Type\":\"RandomRotator\",\"HorizontalRotations\":true,\"Seed\":\"r\",\"Prop\":" + arm + "}", ctx), t, 10, 5, 10, 100L + i * 31L);
            for (int[] d : new int[][]{{12, 10}, {8, 10}, {10, 12}, {10, 8}}) if ("arm".equals(t.getBlock(d[0], 5, d[1]))) dirs.add(d[0] + "," + d[1]);
        }
        check(dirs.size() == 4, "HorizontalRotations picks among all four turns");

        // Prefab folders: the weight picks the folder, then each prefab inside is equally likely
        Prefab a = new Prefab("a", 2, new Prefab.Vec3i(1, 1, 1), new Prefab.Vec3i(0, 0, 0), List.of(Prefab.PaletteEntry.of(0, "block_a", Map.of())), List.of(new Prefab.VoxelEntry(0, 0, 0, 0)), List.of(), List.of());
        Prefab b = new Prefab("b", 2, new Prefab.Vec3i(1, 1, 1), new Prefab.Vec3i(0, 0, 0), List.of(Prefab.PaletteEntry.of(0, "block_b", Map.of())), List.of(new Prefab.VoxelEntry(0, 0, 0, 0)), List.of(), List.of());
        JsonAstCompiler.CompileContext pctx = new JsonAstCompiler.CompileContext(1L, null, id -> id.equals("trees_a") ? a : id.equals("trees_b") ? b : null, null);
        pctx.setPrefabFolders(path -> path.equals("trees") ? List.of("trees_a", "trees_b") : List.of());
        Prop<String> forest = prop("{\"Type\":\"Prefab\",\"WeightedPrefabPaths\":[{\"Path\":\"trees\",\"Weight\":1}]}", pctx);
        int na = 0, nb = 0;
        for (int i = 0; i < 300; i++) {
            Space t = new Space();
            run(forest, t, 5, 5, 5, 1000L + i * 7919L);
            if (!"air".equals(t.getBlock(5, 5, 5)) || !"air".equals(t.getBlock(5, 6, 5))) { if (t.m.containsValue("block_a")) na++; if (t.m.containsValue("block_b")) nb++; }
        }
        check(na > 90 && nb > 90, "a Prefab path naming a folder picks one of its prefabs, equally likely (" + na + " / " + nb + ")");
        Prop<String> single = prop("{\"Type\":\"Prefab\",\"WeightedPrefabPaths\":[{\"Path\":\"trees_a\",\"Weight\":3},{\"Path\":\"trees_b\",\"Weight\":1}]}", pctx);
        int sa = 0;
        for (int i = 0; i < 400; i++) { Space t = new Space(); run(single, t, 5, 5, 5, 5000L + i * 104729L); if (t.m.containsValue("block_a")) sa++; }
        check(sa > 250 && sa < 350, "file paths are picked by weight (3:1 gives about 300 of 400: " + sa + ")");

        System.out.println("Phase 28 verification: ALL " + passed + " TESTS PASSED!");
    }
}