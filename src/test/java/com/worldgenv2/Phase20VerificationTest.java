package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.runtime.PropRuntime;
import com.worldgenv2.worldstructure.TileBiome;

import java.util.HashMap;
import java.util.Map;

/** Phase 20 verification: Hytale-format JSON (Locator, Orienter, patterns, scanners, biome Props entries) through the compiler. */
public final class Phase20VerificationTest {

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

    @SuppressWarnings("unchecked")
    private static Prop<String> prop(String json) {
        JsonAstCompiler.registerBuiltInTypes();
        Object parsed = new JsonAstCompiler.SimpleJsonParser(json).parseValue();
        return JsonAstCompiler.compileProp((Map<String, Object>) parsed, new JsonAstCompiler.CompileContext(1L), id -> id);
    }

    private static final String BRIDGE = """
        { "Type": "Orienter", "Rotations": [0, 90], "SelectionMode": "FirstValid",
          "Scanner": { "Type": "Origin" },
          "Pattern": { "Type": "And", "Patterns": [
            { "Type": "Offset", "Offset": { "X": -4, "Y": -1, "Z": 0 }, "Pattern": { "Type": "BlockType", "Material": "dirt" } },
            { "Type": "Offset", "Offset": { "X": 4, "Y": -1, "Z": 0 }, "Pattern": { "Type": "BlockType", "Material": "dirt" } },
            { "Type": "Cuboid", "Min": { "X": -3, "Y": -1, "Z": 0 }, "Max": { "X": 3, "Y": -1, "Z": 0 },
              "SubPattern": { "Type": "BlockType", "Material": "water" } },
            { "Type": "Cuboid", "Min": { "X": -4, "Y": 0, "Z": 0 }, "Max": { "X": 4, "Y": 0, "Z": 0 },
              "SubPattern": { "Type": "BlockType", "Material": "Empty" } }
          ] },
          "Prop": { "Type": "BlockProp", "Block": "log" } }
        """;

    public static void main(String[] args) {
        System.out.println("=== Phase 20 Verification Test Suite: Hytale-format props and patterns ===");

        Prop<String> bridge = prop(BRIDGE);
        Space gx = new Space();
        gx.fill(0, 0, 0, 20, 8, 20, "dirt");
        gx.fill(7, 7, 0, 13, 8, 20, "water");
        gx.fill(7, 0, 0, 13, 6, 20, "dirt");
        check(bridge.generate(new Prop.Context<>(10, 9, 10, 0, 1, 0, gx, 5L)) && "log".equals(gx.getBlock(10, 9, 10)),
                "a Hytale-format Orienter with a nested Pattern loads from JSON and validates a gap running along Z");
        Space gz = new Space();
        gz.fill(0, 0, 0, 20, 8, 20, "dirt");
        gz.fill(0, 7, 7, 20, 8, 13, "water");
        gz.fill(0, 0, 7, 20, 6, 13, "dirt");
        check(bridge.generate(new Prop.Context<>(10, 9, 10, 0, 1, 0, gz, 5L)), "the same JSON tries the 90 degree rotation and validates a gap running along X");
        Space dry = new Space();
        dry.fill(0, 0, 0, 20, 8, 20, "dirt");
        check(!bridge.generate(new Prop.Context<>(10, 9, 10, 0, 1, 0, dry, 5L)), "and places nothing where there is no gap");

        Prop<String> loc = prop("""
            { "Type": "Locator", "PlacementCap": 1,
              "Scanner": { "Type": "Linear", "Axis": "Y", "Range": { "Min": -8, "Max": 8 } },
              "Pattern": { "Type": "Floor", "Floor": { "Type": "BlockSet", "BlockSet": ["dirt", "stone"] }, "Origin": { "Type": "BlockType", "Material": "Empty" } },
              "Prop": { "Type": "BlockProp", "Block": "flower" } }
            """);
        Space g = new Space();
        g.fill(0, 0, 0, 9, 4, 9, "dirt");
        check(loc.generate(new Prop.Context<>(4, 4, 4, 0, 1, 0, g, 1L)) && "flower".equals(g.getBlock(4, 5, 4)),
                "a Hytale-format Locator with a Linear scanner, Floor pattern and BlockSet finds the ground");

        Prop<String> union = prop("{ \"Type\": \"Union\", \"Props\": [ { \"Type\": \"BlockProp\", \"Block\": \"a\" }, { \"Type\": \"BlockProp\", \"Block\": \"b\" } ] }");
        check(union != null, "other nodes still load in the same file format");

        // a biome whose Props are written the way Hytale writes them
        JsonAstCompiler.registerBuiltInTypes();
        String biomeJson = """
            { "Name": "Test", "Type": "TileBiome", "Weight": 1,
              "Terrain": { "Type": "Constant", "Value": 1 },
              "Props": [
                { "Runtime": 0, "Skip": true,
                  "Positions": { "Type": "SquareGrid2d" },
                  "Assignments": { "Type": "Constant", "Prop": { "Type": "BlockProp", "Block": "skipped" } } },
                { "Runtime": 2,
                  "Positions": { "Type": "SquareGrid2d" },
                  "Assignments": { "Type": "Constant", "Prop": { "Type": "BlockProp", "Block": "shown" } } } ] }
            """;
        var cb = JsonAstCompiler.<String>compileBiome(0, biomeJson, new JsonAstCompiler.CompileContext(1L), new HashMap<>(), id -> id);
        TileBiome tb = cb.tileBiome();
        check(tb != null && tb.props().size() == 1, "a Props entry with Skip true is left out");
        Prop<?> p = tb.props().get(0);
        check(p instanceof PropRuntime<?> r && r.getRuntimeIndex() == 3, "a Hytale Runtime number becomes a stage (Hytale counts from 0, the engine from 1)");

        System.out.println("Phase 20 verification: ALL " + passed + " TESTS PASSED!");
    }
}
