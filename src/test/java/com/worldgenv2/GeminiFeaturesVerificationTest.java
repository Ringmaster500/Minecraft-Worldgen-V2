package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.prefab.Prefab;
import com.worldgenv2.prefab.PrefabLoader;
import com.worldgenv2.prefab.PrefabProp;
import com.worldgenv2.props.Prop;
import com.worldgenv2.worldstructure.SpawnAnchorManager;
import com.worldgenv2.worldstructure.StructurePlacement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Verification suite for Gemini additions: scorch blending, container loot tables, and prefab loot persistence. */
public final class GeminiFeaturesVerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.err.println("[FAIL] " + what);
            throw new AssertionError(what);
        }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static final class TestVoxelSpace implements VoxelSpace<String> {
        final Map<String, String> blocks = new HashMap<>();
        final Map<String, String> loots = new HashMap<>();

        private static String k(int x, int y, int z) { return x + "," + y + "," + z; }

        @Override public String getBlock(int x, int y, int z) { return blocks.getOrDefault(k(x, y, z), "minecraft:air"); }
        @Override public void setBlock(int x, int y, int z, String block) { blocks.put(k(x, y, z), block); }
        @Override public boolean isAir(int x, int y, int z) { return "minecraft:air".equals(getBlock(x, y, z)); }
        @Override public int getMinY() { return -64; }
        @Override public int getMaxY() { return 320; }
        @Override public void setBlockLoot(int x, int y, int z, String lootTableId, long seed) {
            loots.put(k(x, y, z), lootTableId);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Gemini Features Verification Suite ===");

        // 1. SpawnAnchorManager scorch block blending
        var rule = new SpawnAnchorManager.SpawnAnchorRule(
            "crash", "ship", 0, 0, 20f, 8f, 3f, true, 0, 1, 0,
            "minecraft:blackstone", "minecraft:basalt"
        );
        SpawnAnchorManager mgr = new SpawnAnchorManager(rule);
        check("minecraft:blackstone".equals(mgr.getScorchBlockId(0, 0, 0)), "crater center surface uses primary scorch block");
        check(mgr.getScorchBlockId(18, 0, 1) != null, "outer scorch perimeter depth=1 uses scorch block");
        check(mgr.getScorchBlockId(0, 0, 4) == null, "deep underground below crater is not scorched");
        check(mgr.getScorchBlockId(50, 50, 0) == null, "outside crater radius is not scorched");

        // 2. PrefabProp container loot + lootTableOverride + rotation preservation
        Prefab prefab = new Prefab(
            "test:loot_shack", 2,
            new Prefab.Vec3i(3, 2, 3), new Prefab.Vec3i(0, 0, 0),
            List.of(
                Prefab.PaletteEntry.of(0, "minecraft:stone", Map.of()),
                Prefab.PaletteEntry.of(1, "minecraft:chest", Map.of("facing", "north"))
            ),
            List.of(
                new Prefab.VoxelEntry(0, 0, 0, 0),
                new Prefab.VoxelEntry(1, 0, 0, 1),
                new Prefab.VoxelEntry(2, 0, 0, 1, "minecraft:chests/simple_dungeon")
            ),
            List.of(), List.of()
        );

        Prefab rotated = prefab.rotated(1);
        check("minecraft:chests/simple_dungeon".equals(rotated.blocks().get(2).lootTable()),
            "prefab rotation preserves voxel lootTable");

        TestVoxelSpace space = new TestVoxelSpace();
        new PrefabProp<>(prefab, s -> s, false, false, List.of(), 0, null, "modpack:chests/outpost")
            .generate(new Prop.Context<>(10, 64, 20, 0, 1, 0, space, 42L));

        check(space.loots.get("10,64,20") == null, "non-container stone block receives no loot");
        check("modpack:chests/outpost".equals(space.loots.get("11,64,20")),
            "chest without explicit loot receives StructurePlacement lootTableOverride");
        check("minecraft:chests/simple_dungeon".equals(space.loots.get("12,64,20")),
            "chest with explicit voxel loot keeps its own loot table");

        // 3. PrefabLoader parses lootTable from .prefab.json
        String json = """
            {
              "name": "worldgenv2:desert_cache",
              "version": 2,
              "size": {"x": 2, "y": 1, "z": 1},
              "anchor": {"x": 0, "y": 0, "z": 0},
              "palette": [
                {"id": 0, "name": "minecraft:barrel", "properties": {"facing": "up"}}
              ],
              "blocks": [
                {"x": 0, "y": 0, "z": 0, "paletteIndex": 0, "lootTable": "minecraft:chests/desert_pyramid"}
              ]
            }
            """;
        Prefab parsed = new PrefabLoader().parseJson(json);
        check("minecraft:chests/desert_pyramid".equals(parsed.blocks().get(0).lootTable()),
            "PrefabLoader parses lootTable field on voxel entries");

        // 4. StructurePlacement carries lootTableOverride
        StructurePlacement sp = new StructurePlacement("outpost_0", parsed, 100, 64, 100, "modpack:chests/tier1");
        check("modpack:chests/tier1".equals(sp.lootTableOverride()), "StructurePlacement stores lootTableOverride");

        System.out.println("=== All " + passed + " Gemini verification checks PASSED! ===");
    }
}
