package com.worldgenv2;

import com.worldgenv2.prefab.Prefab;
import com.worldgenv2.prefab.Prefab.*;

import java.util.List;
import java.util.Map;

/** Phase 15 verification: prefab rotation (voxels, anchor, sockets, block-state properties). */
public final class Phase15VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.err.println("[FAIL] " + what);
            throw new AssertionError(what);
        }
        System.out.println("[PASS] " + what);
        passed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Phase 15 Verification Test Suite: Prefab rotation ===");
        // 4 long (x) by 2 wide (z); a stair facing east at the +x end, a log along x
        Prefab p = new Prefab("t", 2, new Vec3i(4, 3, 2), new Vec3i(1, 0, 0),
                List.of(PaletteEntry.of(1, "minecraft:oak_stairs", Map.of("facing", "east")),
                        PaletteEntry.of(2, "minecraft:oak_log", Map.of("axis", "x"))),
                List.of(new VoxelEntry(3, 0, 0, 1), new VoxelEntry(0, 0, 1, 2)),
                List.of(new PrefabSocket("s", "T", "ANY", new Vec3i(3, 0, 0), Facing.EAST, List.of())),
                List.of(new AdaptiveFoundation(0, 0, 3, 1, "minecraft:stone", 4)));

        check(p.rotated(0) == p && p.rotated(4) == p, "rotating by 0 or 4 quarter turns returns the same prefab");

        Prefab r = p.rotated(1);
        check(r.size().x() == 2 && r.size().z() == 4 && r.size().y() == 3, "one turn swaps the footprint (4x2 -> 2x4)");
        // (x,z) -> (sizeZ-1-z, x): (3,0) -> (1,3) ; (0,1) -> (0,0)
        check(r.blocks().get(0).x() == 1 && r.blocks().get(0).z() == 3, "voxel at far +x end moves to the far +z end");
        check(r.blocks().get(1).x() == 0 && r.blocks().get(1).z() == 0, "second voxel lands at the origin corner");
        check(r.resolvePalette(1).properties().get("facing").equals("south"), "east-facing stair now faces south");
        check(r.resolvePalette(2).properties().get("axis").equals("z"), "log axis x becomes z");
        check(r.sockets().get(0).facing() == Facing.SOUTH && r.sockets().get(0).position().z() == 3, "socket position and facing turn together");
        check(r.anchor().x() == 1 && r.anchor().z() == 1, "anchor turns with the prefab ((1,0) -> (1,1))");
        AdaptiveFoundation f = r.adaptiveFoundations().get(0);
        check(f.minX() == 0 && f.maxX() == 1 && f.minZ() == 0 && f.maxZ() == 3, "foundation rectangle turns with the prefab");

        Prefab back = p.rotated(1).rotated(1).rotated(1).rotated(1);
        check(back.size().equals(p.size()) && back.blocks().equals(p.blocks()) && back.anchor().equals(p.anchor()),
                "four single turns return the original voxels, size and anchor");
        check(p.rotated(-1).blocks().equals(p.rotated(3).blocks()), "negative turns wrap (-1 == 3)");

        System.out.println("Phase 15 verification: ALL " + passed + " TESTS PASSED!");
    }
}
