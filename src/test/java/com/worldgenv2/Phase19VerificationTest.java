package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.hytale.HPattern;
import com.worldgenv2.hytale.HProps;
import com.worldgenv2.hytale.HScanner;
import com.worldgenv2.props.Prop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Phase 19 verification: Hytale's Pattern, Scanner, Locator and Orienter semantics, including a log laid across a gap. */
public final class Phase19VerificationTest {

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
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) setBlock(x, y, z, b);
        }
    }

    private static HPattern<String> is(String b) { return HPattern.blockType(b, false); }

    private static HPattern<String> air() { return HPattern.blockType("air", true); }

    private static boolean t(HPattern<String> p, Space s, int x, int y, int z, int rot) { return p.test(new HPattern.Probe<>(s), x, y, z, rot); }

    public static void main(String[] args) {
        System.out.println("=== Phase 19 Verification Test Suite: Hytale patterns, scanners, Locator and Orienter ===");

        Space s = new Space();
        s.fill(0, 0, 0, 9, 4, 9, "dirt");      // ground up to y = 4
        s.fill(3, 5, 3, 3, 7, 3, "stone");      // a pillar

        // ---- patterns
        check(t(HPattern.constant(true), s, 0, 0, 0, 0) && !t(HPattern.constant(false), s, 0, 0, 0, 0), "Constant validates everywhere or nowhere");
        check(t(is("dirt"), s, 1, 1, 1, 0) && !t(is("dirt"), s, 1, 9, 1, 0), "BlockType checks the block's material");
        check(t(HPattern.blockSet(Set.of("dirt", "stone"), false), s, 3, 6, 3, 0), "BlockSet checks membership");
        check(t(HPattern.blockType("air", true), s, 1, 9, 1, 0), "an air BlockType means empty space");
        check(t(HPattern.floor(is("dirt"), air()), s, 1, 5, 1, 0) && !t(HPattern.floor(is("dirt"), air()), s, 1, 8, 1, 0), "Floor: origin validates the position, the Floor pattern the block right under it");
        check(t(HPattern.ceiling(is("stone"), air()), s, 3, 4, 3, 0) == false && t(HPattern.ceiling(is("dirt"), is("dirt")), s, 1, 3, 1, 0), "Ceiling: origin at the position, ceiling pattern right above it");
        check(t(HPattern.offset(is("stone"), 0, 1, 0), s, 3, 4, 3, 0) && !t(HPattern.offset(is("stone"), 0, 1, 0), s, 5, 4, 5, 0), "Offset moves the child pattern by a vector");
        check(t(HPattern.offset(is("stone"), 1, 0, 0), s, 2, 6, 3, 0) && !t(HPattern.offset(is("stone"), 1, 0, 0), s, 2, 6, 3, 1)
                && t(HPattern.offset(is("stone"), 1, 0, 0), s, 3, 6, 2, 1), "rotating a pattern turns its offsets (one quarter turn sends +X to +Z)");
        check(t(HPattern.rotator(HPattern.offset(is("stone"), 1, 0, 0), 1), s, 3, 6, 2, 0), "Rotator turns its child by a fixed rotation");
        check(t(HPattern.wall(is("stone"), air(), List.of(2), false), s, 2, 6, 3, 0) && !t(HPattern.wall(is("stone"), air(), List.of(3), false), s, 2, 6, 3, 0),
                "Wall: the wall pattern beside the origin, in the listed directions (E is +X)");
        check(t(HPattern.wall(is("stone"), air(), List.of(2, 3), false), s, 2, 6, 3, 0) && !t(HPattern.wall(is("stone"), air(), List.of(2, 3), true), s, 2, 6, 3, 0),
                "Wall: any listed direction by default, all of them with RequireAllDirections");
        check(t(HPattern.surface(is("dirt"), air(), 1.0, 1.0, 0, 0, List.of(4), false), s, 6, 5, 6, 0)
                && !t(HPattern.surface(is("dirt"), air(), 1.0, 1.0, 0, 0, List.of(4), false), s, 3, 5, 3, 0),
                "Surface: an up-facing surface has the medium at the origin and the surface below, over a disc");
        check(t(HPattern.surface(is("dirt"), air(), 0.0, 0.0, 1, 0, List.of(4), false), s, 6, 6, 6, 0)
                && !t(HPattern.surface(is("dirt"), air(), 0.0, 0.0, 0, 0, List.of(4), false), s, 6, 6, 6, 0), "Surface: a gap allows roughness between medium and surface");
        check(t(HPattern.cuboid(-1, 0, -1, 1, 1, 1, is("dirt")), s, 5, 2, 5, 0) && !t(HPattern.cuboid(-1, 0, -1, 1, 1, 1, is("dirt")), s, 5, 4, 5, 0),
                "Cuboid validates only if every position inside it validates");
        check(t(HPattern.and(List.of(is("dirt"), HPattern.constant(true))), s, 1, 1, 1, 0) && t(HPattern.or(List.of(is("stone"), is("dirt"))), s, 1, 1, 1, 0)
                && t(HPattern.not(is("stone")), s, 1, 1, 1, 0) && !t(HPattern.and(List.of(is("dirt"), is("stone"))), s, 1, 1, 1, 0), "And, Or and Not");
        check(t(HPattern.fieldFunction((x, y, z) -> y, new double[][]{{2, 5}}), s, 0, 4, 0, 0) && !t(HPattern.fieldFunction((x, y, z) -> y, new double[][]{{2, 5}}), s, 0, 5, 0, 0),
                "FieldFunction: min inclusive, max exclusive");

        // ---- scanners
        List<int[]> lin = HScanner.linear(1, -2, 2, null).scan(5, 10, 5, 0);
        check(lin.size() == 5 && lin.get(0)[1] == 8 && lin.get(4)[1] == 12, "Linear scans the range in order along its axis");
        List<int[]> rad = HScanner.radial(-2, -2, -2, 2, 2, 2, null).scan(0, 0, 0, 0);
        check(rad.size() == 125 && rad.get(0)[0] == 0 && rad.get(0)[1] == 0 && rad.get(0)[2] == 0, "Radial starts at the anchor and moves outward");
        boolean nearestFirst = true;
        for (int i = 1; i < rad.size(); i++) {
            int a = rad.get(i - 1)[0] * rad.get(i - 1)[0] + rad.get(i - 1)[1] * rad.get(i - 1)[1] + rad.get(i - 1)[2] * rad.get(i - 1)[2];
            int b = rad.get(i)[0] * rad.get(i)[0] + rad.get(i)[1] * rad.get(i)[1] + rad.get(i)[2] * rad.get(i)[2];
            if (b < a) nearestFirst = false;
        }
        check(nearestFirst, "Radial orders positions from closest to farthest");
        List<int[]> r1 = HScanner.random(1, -3, 3, 7, null).scan(0, 10, 0, 99), r2 = HScanner.random(1, -3, 3, 7, null).scan(0, 10, 0, 99);
        check(r1.size() == 7 && java.util.Arrays.deepEquals(r1.toArray(), r2.toArray()), "Random visits every block in an order fixed by the seed");
        check(HScanner.queue(List.of(HScanner.ORIGIN, HScanner.linear(0, 1, 2, null))).scan(0, 0, 0, 0).size() == 3, "Queue runs scanners one after another");
        check(HScanner.linear(1, 0, 1, HScanner.linear(0, 0, 1, null)).scan(0, 0, 0, 0).size() == 4, "a child scanner runs for every block the parent scans");

        // ---- Locator
        int[] hits = {0};
        Prop<String> mark = c -> { c.voxelSpace().setBlock(c.worldX(), c.worldY(), c.worldZ(), "marker"); hits[0]++; return true; };
        HPattern<String> ground = HPattern.floor(is("dirt"), air());
        Space w = new Space();
        w.fill(0, 0, 0, 9, 4, 9, "dirt");
        boolean placed = new HProps.Locator<>(mark, ground, HScanner.linear(1, -6, 6, null), 0).generate(new Prop.Context<>(4, 4, 4, 0, 1, 0, w, 1L));
        check(placed && "marker".equals(w.getBlock(4, 5, 4)), "Locator places its child where the pattern validates along the scan");
        Space w2 = new Space();
        w2.fill(0, 0, 0, 9, 4, 9, "dirt");
        hits[0] = 0;
        new HProps.Locator<>(mark, HPattern.constant(true), HScanner.linear(1, 0, 9, null), 3).generate(new Prop.Context<>(4, 0, 4, 0, 1, 0, w2, 1L));
        check(hits[0] == 3, "PlacementCap limits how many valid positions are used");
        check(!new HProps.Locator<>(mark, HPattern.constant(false), HScanner.ORIGIN, 0).generate(new Prop.Context<>(0, 0, 0, 0, 1, 0, w2, 1L)), "Locator generates nothing when no position validates");

        // ---- Orienter: a log of 9 laid across a 7-block gap, turned to whichever way the gap runs
        Prop<String> log = c -> { for (int dx = -4; dx <= 4; dx++) c.voxelSpace().setBlock(c.worldX() + dx, c.worldY(), c.worldZ(), "log"); return true; };
        HPattern<String> across = HPattern.and(List.of(
                HPattern.offset(is("dirt"), -4, -1, 0),                                       // bank at one end
                HPattern.offset(is("dirt"), 4, -1, 0),                                        // bank at the other
                HPattern.cuboid(-3, -1, 0, 3, -1, 0, is("water")),                            // water between
                HPattern.cuboid(-4, 0, 0, 4, 0, 0, air())));                                  // clear above the span
        // gap along X
        Space gx = new Space();
        gx.fill(0, 0, 0, 20, 8, 20, "dirt");
        gx.fill(7, 7, 0, 13, 8, 20, "water");
        gx.fill(7, 0, 0, 13, 6, 20, "dirt");
        boolean ok0 = new HProps.Orienter<>(log, List.of(0, 1), across, HScanner.ORIGIN, HProps.Selection.FIRST_VALID, 1L).generate(new Prop.Context<>(10, 9, 10, 0, 1, 0, gx, 5L));
        check(ok0 && "log".equals(gx.getBlock(6, 9, 10)) && "log".equals(gx.getBlock(14, 9, 10)) && "log".equals(gx.getBlock(10, 9, 10)), "Orienter lays the log along X across a gap that runs along Z");
        // gap along Z
        Space gz = new Space();
        gz.fill(0, 0, 0, 20, 8, 20, "dirt");
        gz.fill(0, 7, 7, 20, 8, 13, "water");
        gz.fill(0, 0, 7, 20, 6, 13, "dirt");
        boolean ok1 = new HProps.Orienter<>(log, List.of(0, 1), across, HScanner.ORIGIN, HProps.Selection.FIRST_VALID, 1L).generate(new Prop.Context<>(10, 9, 10, 0, 1, 0, gz, 5L));
        check(ok1 && "log".equals(gz.getBlock(10, 9, 6)) && "log".equals(gz.getBlock(10, 9, 14)) && !"log".equals(gz.getBlock(6, 9, 10)),
                "Orienter turns the pattern and the log together: the same prop now lies along Z across a gap that runs along X");
        // a gap that is too wide for the log: nothing placed
        Space wide = new Space();
        wide.fill(0, 0, 0, 30, 8, 20, "dirt");
        wide.fill(5, 7, 0, 25, 8, 20, "water");
        check(!new HProps.Orienter<>(log, List.of(0, 1), across, HScanner.ORIGIN, HProps.Selection.FIRST_VALID, 1L).generate(new Prop.Context<>(15, 9, 10, 0, 1, 0, wide, 5L)),
                "Orienter places nothing where no rotation fits");
        // selection modes on a symmetric spot: both 0 and 2 quarter turns validate
        Space sym = new Space();
        sym.fill(0, 0, 0, 20, 8, 20, "dirt");
        sym.fill(7, 7, 0, 13, 8, 20, "water");
        sym.fill(7, 0, 0, 13, 6, 20, "dirt");
        int[] turnsSeen = {0};
        Prop<String> countTurns = c -> { turnsSeen[0]++; return true; };
        new HProps.Orienter<>(countTurns, List.of(0, 2), across, HScanner.ORIGIN, HProps.Selection.ALL_VALID, 1L).generate(new Prop.Context<>(10, 9, 10, 0, 1, 0, sym, 5L));
        check(turnsSeen[0] == 2, "AllValid generates the child once for every valid rotation");
        turnsSeen[0] = 0;
        new HProps.Orienter<>(countTurns, List.of(0, 2), across, HScanner.ORIGIN, HProps.Selection.RANDOM_VALID, 1L).generate(new Prop.Context<>(10, 9, 10, 0, 1, 0, sym, 5L));
        check(turnsSeen[0] == 1, "RandomValid picks one of the valid rotations");

        System.out.println("Phase 19 verification: ALL " + passed + " TESTS PASSED!");
    }
}
