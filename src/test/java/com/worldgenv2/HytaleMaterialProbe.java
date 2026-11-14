package com.worldgenv2;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.material.MaterialProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dev probe: Hytale-format Material Providers, sampled by depth into the floor and space above it. */
public final class HytaleMaterialProbe {

    private static final VoxelSpace<String> SPACE = new VoxelSpace<>() {
        public String getBlock(int x, int y, int z) { return "air"; }
        public void setBlock(int x, int y, int z, String b) { }
        public boolean isAir(int x, int y, int z) { return true; }
        public int getMinY() { return 0; }
        public int getMaxY() { return 128; }
    };

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) { return (Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(json).parseValue(); }

    private static String k(String m) { return "{\"Type\":\"Constant\",\"Material\":\"" + m + "\"}"; }

    public static void main(String[] args) {
        JsonAstCompiler.registerBuiltInTypes();
        Map<String, String> n = new LinkedHashMap<>();
        n.put("Constant", k("stone"));
        n.put("Transparent", "{\"Type\":\"Transparent\"}");
        n.put("Solidity", "{\"Type\":\"Solidity\",\"Solid\":" + k("stone") + ",\"Empty\":" + k("water") + "}");
        n.put("Queue", "{\"Type\":\"Queue\",\"Queue\":[{\"Type\":\"Transparent\"}," + k("second") + "," + k("third") + "]}");
        n.put("SimpleHorizontal", "{\"Type\":\"SimpleHorizontal\",\"TopY\":70,\"BottomY\":60,\"Material\":" + k("band") + "}");
        n.put("Striped", "{\"Type\":\"Striped\",\"Stripes\":[{\"TopY\":65,\"BottomY\":63}],\"Material\":" + k("stripe") + "}");
        n.put("Weighted", "{\"Type\":\"Weighted\",\"Seed\":\"a\",\"SkipChance\":0.0,\"WeightedMaterials\":[{\"Weight\":1,\"Material\":" + k("a") + "},{\"Weight\":1,\"Material\":" + k("b") + "}]}");
        n.put("FieldFunction", "{\"Type\":\"FieldFunction\",\"FieldFunction\":{\"Type\":\"Constant\",\"Value\":0.5},\"Delimiters\":[{\"From\":0,\"To\":1,\"Material\":" + k("hit") + "}]}");
        n.put("SpaceAndDepth", "{\"Type\":\"SpaceAndDepth\",\"LayerContext\":\"DEPTH_INTO_FLOOR\",\"MaxExpectedDepth\":8,\"Condition\":{\"Type\":\"GreaterThanCondition\",\"ContextToCheck\":\"SPACE_ABOVE_FLOOR\",\"Threshold\":2},"
                + "\"Layers\":[{\"Type\":\"ConstantThickness\",\"Thickness\":1,\"Material\":" + k("grass") + "},{\"Type\":\"RangeThickness\",\"RangeMin\":2,\"RangeMax\":3,\"Seed\":\"d\",\"Material\":" + k("dirt") + "},"
                + "{\"Type\":\"ConstantThickness\",\"Thickness\":99,\"Material\":" + k("stone") + "}]}");
        for (var e : n.entrySet()) {
            try {
                MaterialProvider<String> p = JsonAstCompiler.compileMaterialProvider(parse(e.getValue()), new JsonAstCompiler.CompileContext(1L), id -> id);
                StringBuilder sb = new StringBuilder();
                for (int depth = 0; depth <= 6; depth++) sb.append(p.getMaterial(0, 64 - depth, 0, depth, 5, 0f, SPACE)).append(' ');
                System.out.println("MATERIAL " + e.getKey() + ": " + sb);
            } catch (Throwable t) {
                String m = String.valueOf(t.getMessage()).split("\n")[0];
                System.out.println("MATERIAL " + e.getKey() + ": FAIL " + t.getClass().getSimpleName() + " " + m.substring(0, Math.min(90, m.length())));
            }
        }
    }
}
