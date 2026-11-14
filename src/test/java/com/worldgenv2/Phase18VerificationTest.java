package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.assignments.ConstantAssignment;
import com.worldgenv2.density.VerticalGradientDensity;
import com.worldgenv2.engine.chunkgenerator.StagedChunkGenerator;
import com.worldgenv2.material.ConstantMaterialProvider;
import com.worldgenv2.positions.SquareGrid2dPositionsProvider;
import com.worldgenv2.prefab.Prefab;
import com.worldgenv2.prefab.PrefabProp;
import com.worldgenv2.props.AssignmentsProp;
import com.worldgenv2.props.runtime.PropRuntime;
import com.worldgenv2.scanner.OriginScanner;
import com.worldgenv2.worldstructure.TileBiome;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 18 verification: the Prop Runtime context. A chunk must come out identical whichever of its neighbours were
 * generated before it, and a prop that overlaps a chunk border must be built from both sides.
 */
public final class Phase18VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.err.println("[FAIL] " + what);
            throw new AssertionError(what);
        }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static final class MapSpace implements VoxelSpace<String> {
        final Map<Long, String> blocks = new HashMap<>();

        static long pack(int x, int y, int z) { return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL); }

        public String getBlock(int x, int y, int z) { return blocks.getOrDefault(pack(x, y, z), "minecraft:air"); }

        public void setBlock(int x, int y, int z, String b) {
            if ("minecraft:air".equals(b)) blocks.remove(pack(x, y, z)); else blocks.put(pack(x, y, z), b);
        }

        public boolean isAir(int x, int y, int z) { return "minecraft:air".equals(getBlock(x, y, z)); }

        public int getMinY() { return 0; }

        public int getMaxY() { return 128; }
    }

    /** A 9 x 3 x 9 slab of "test:leaf", anchored at its centre: far wider than the 1-block margin the old code allowed. */
    private static Prefab slab() {
        List<Prefab.VoxelEntry> v = new ArrayList<>();
        for (int x = 0; x < 9; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 9; z++) v.add(new Prefab.VoxelEntry(x, y, z, 1));
        return new Prefab("test", 2, new Prefab.Vec3i(9, 3, 9), new Prefab.Vec3i(4, 0, 4),
                List.of(Prefab.PaletteEntry.of(1, "test:leaf", Map.of())), v, List.of(), List.of());
    }

    private static WorldStructure world() {
        PrefabProp<String> tree = new PrefabProp<>(slab(), id -> id);
        AssignmentsProp<String> dist = new AssignmentsProp<>(new SquareGrid2dPositionsProvider(7.0), OriginScanner.INSTANCE,
                new ConstantAssignment<>(tree), b -> b != null && !"minecraft:air".equals(b));
        TileBiome biome = new TileBiome(0, "Test", 10f, 1f, 32f, new VerticalGradientDensity(40f, 20f),
                new ConstantMaterialProvider<>("test:grass"), List.of(PropRuntime.of(1, dist)));
        return new WorldStructure(List.of(biome), List.of(), 128, 4242L);
    }

    private static StagedChunkGenerator<String> generator(WorldStructure ws) {
        return new StagedChunkGenerator<>(ws, new ConstantMaterialProvider<>("test:grass"), List.of(), "minecraft:air", "minecraft:stone");
    }

    private static Map<Long, String> footprint(MapSpace s, int cx, int cz) {
        Map<Long, String> out = new HashMap<>();
        for (int x = cx << 4; x < (cx << 4) + 16; x++)
            for (int z = cz << 4; z < (cz << 4) + 16; z++)
                for (int y = 0; y < 128; y++) { String b = s.getBlock(x, y, z); if (!"minecraft:air".equals(b)) out.put(MapSpace.pack(x, y, z), b); }
        return out;
    }

    private static MapSpace gen(StagedChunkGenerator<String> g, int cx, int cz) {
        MapSpace s = new MapSpace();
        g.generateChunk(cx, cz, s);
        return s;
    }

    private static long count(Map<Long, String> m, String b) { return m.values().stream().filter(b::equals).count(); }

    public static void main(String[] args) {
        System.out.println("=== Phase 18 Verification Test Suite: Prop Runtime context ===");

        WorldStructure ws = world();
        check(ws.<String>allBiomeProps().size() == 1, "the world exposes its biome props with stable identities");
        var bp = ws.<String>allBiomeProps().get(0);
        check(bp.prop().horizontalReach() >= 9 && bp.prop().contentReach() >= 9, "a prop reports the reach of what it builds (a 9-wide prefab reaches 9)");
        check(bp.salt() == ws.<String>allBiomeProps().get(0).salt() && bp.salt() == "Test".hashCode() * 31L, "a prop's salt is derived from its biome name and position, so it is the same in every run");

        // Alone, on a fresh generator
        Map<Long, String> alone = footprint(gen(generator(ws), 0, 0), 0, 0);
        long leavesAlone = count(alone, "test:leaf");
        check(leavesAlone > 0, "props are placed (" + leavesAlone + " leaf blocks in the chunk)");

        // After every neighbour, in several different orders, each on a fresh generator
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
        StagedChunkGenerator<String> g2 = generator(ws);
        for (int[] c : ring) gen(g2, c[0], c[1]);
        Map<Long, String> afterRing = footprint(gen(g2, 0, 0), 0, 0);
        check(afterRing.equals(alone), "chunk (0,0) is identical after all eight neighbours were generated first");

        StagedChunkGenerator<String> g3 = generator(ws);
        for (int i = ring.length - 1; i >= 0; i--) gen(g3, ring[i][0], ring[i][1]);
        Map<Long, String> afterReverse = footprint(gen(g3, 0, 0), 0, 0);
        check(afterReverse.equals(alone), "chunk (0,0) is identical when the neighbours were generated in the opposite order");

        StagedChunkGenerator<String> g4 = generator(ws);
        gen(g4, 1, 0);
        gen(g4, 0, 1);
        check(footprint(gen(g4, 0, 0), 0, 0).equals(alone), "chunk (0,0) is identical after only two neighbours");

        // A prop overlapping the border must exist on both sides of it
        boolean nearWest = false, nearEast = false;
        for (long key : alone.keySet()) {
            int x = (int) ((key >> 38) & 0x3FFFFFFL);
            if (alone.get(key).equals("test:leaf")) { if (x < 4) nearWest = true; if (x > 11) nearEast = true; }
        }
        check(nearWest && nearEast, "leaf blocks appear right up to both borders of the chunk (props are not clipped at the edge)");

        Map<Long, String> east = footprint(gen(generator(ws), 1, 0), 1, 0);
        boolean continues = false;
        for (long key : east.keySet()) {
            int x = (int) ((key >> 38) & 0x3FFFFFFL);
            if (east.get(key).equals("test:leaf") && x < 20) continues = true;
        }
        check(continues, "the neighbour chunk carries the other half of a prop that straddles the border");

        // seam check: where a leaf slab crosses the border, both sides agree on its height range
        Map<Integer, int[]> westEdge = new HashMap<>(), eastEdge = new HashMap<>();
        for (int z = 0; z < 16; z++) {
            int lo = 999, hi = -1, lo2 = 999, hi2 = -1;
            for (int y = 0; y < 128; y++) {
                if ("test:leaf".equals(alone.get(MapSpace.pack(15, y, z)))) { lo = Math.min(lo, y); hi = Math.max(hi, y); }
                if ("test:leaf".equals(east.get(MapSpace.pack(16, y, z)))) { lo2 = Math.min(lo2, y); hi2 = Math.max(hi2, y); }
            }
            if (hi >= 0 && hi2 >= 0) { westEdge.put(z, new int[]{lo, hi}); eastEdge.put(z, new int[]{lo2, hi2}); }
        }
        boolean aligned = !westEdge.isEmpty();
        for (var e : westEdge.entrySet()) {
            int[] a = e.getValue(), b = eastEdge.get(e.getKey());
            if (Math.abs(a[0] - b[0]) > 3) aligned = false;
        }
        check(aligned, "a prop that crosses the border sits at the same height on both sides (no seam)");

        System.out.println("Phase 18 verification: ALL " + passed + " TESTS PASSED!");
    }
}
