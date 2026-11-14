package com.worldgenv2;

import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dev probe: which Hytale-format nodes load, and what a positions provider yields. Run with gradle probeHytale. */
public final class HytaleProbe {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        return (Map<String, Object>) new JsonAstCompiler.SimpleJsonParser(json).parseValue();
    }

    public static void main(String[] args) {
        JsonAstCompiler.registerBuiltInTypes();
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L);
        Map<String, String> props = new LinkedHashMap<>();
        String B = "{\"Type\":\"BlockProp\",\"Block\":\"x\"}";
        props.put("Cuboid", "{\"Type\":\"Cuboid\",\"Bounds\":{\"Min\":{\"X\":-1,\"Y\":0,\"Z\":-1},\"Max\":{\"X\":1,\"Y\":2,\"Z\":1}},\"Material\":{\"Type\":\"Constant\",\"Material\":\"stone\"}}");
        props.put("Manual", "{\"Type\":\"Manual\",\"Blocks\":[{\"Offset\":{\"X\":0,\"Y\":1,\"Z\":0},\"Material\":\"stone\"}]}");
        props.put("Queue", "{\"Type\":\"Queue\",\"Props\":[" + B + "," + B + "]}");
        props.put("Mask", "{\"Type\":\"Mask\",\"Prop\":" + B + ",\"Mask\":{\"DontPlace\":[\"air\"]}}");
        props.put("StaticRotator", "{\"Type\":\"StaticRotator\",\"Prop\":" + B + ",\"Rotation\":90}");
        props.put("RandomRotator", "{\"Type\":\"RandomRotator\",\"Prop\":" + B + ",\"HorizontalRotations\":true,\"Seed\":\"a\"}");
        props.put("DensitySelector", "{\"Type\":\"DensitySelector\",\"Density\":{\"Type\":\"Constant\",\"Value\":0.5},\"Delimiters\":[{\"Min\":0,\"Max\":1,\"Prop\":" + B + "}]}");
        props.put("Density", "{\"Type\":\"Density\",\"Bounds\":{\"Min\":{\"X\":-2,\"Y\":0,\"Z\":-2},\"Max\":{\"X\":2,\"Y\":4,\"Z\":2}},\"Density\":{\"Type\":\"Constant\",\"Value\":1},\"Material\":{\"Type\":\"Constant\",\"Material\":\"stone\"}}");
        props.put("Prefab", "{\"Type\":\"Prefab\",\"WeightedPrefabPaths\":[{\"Path\":\"trees/oak\",\"Weight\":1}]}");
        props.put("Union", "{\"Type\":\"Union\",\"Props\":[" + B + "," + B + "]}");
        props.put("Offset", "{\"Type\":\"Offset\",\"Offset\":{\"X\":0,\"Y\":1,\"Z\":0},\"Prop\":" + B + "}");
        props.put("Weighted", "{\"Type\":\"Weighted\",\"Seed\":\"a\",\"Entries\":[{\"Weight\":1,\"Prop\":" + B + "},{\"Weight\":2,\"Prop\":" + B + "}]}");
        props.put("PondFiller", "{\"Type\":\"PondFiller\",\"Bounds\":{\"Min\":{\"X\":-4,\"Y\":-3,\"Z\":-4},\"Max\":{\"X\":4,\"Y\":0,\"Z\":4}},\"BarrierBlockSet\":[\"stone\"],\"FillMaterial\":{\"Type\":\"Constant\",\"Material\":\"water\"}}");
        for (var e : props.entrySet()) {
            try { Prop<String> p = JsonAstCompiler.compileProp(parse(e.getValue()), ctx, id -> id); System.out.println("PROP    " + e.getKey() + ": OK " + p.getClass().getSimpleName()); }
            catch (Throwable t) { System.out.println("PROP    " + e.getKey() + ": FAIL " + t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage()).split("\n")[0]); }
        }

        Map<String, String> asg = new LinkedHashMap<>();
        asg.put("Constant", "{\"Type\":\"Constant\",\"Prop\":" + B + "}");
        asg.put("Weighted", "{\"Type\":\"Weighted\",\"Seed\":\"a\",\"SkipChance\":0.5,\"WeightedAssignments\":[{\"Weight\":1,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":" + B + "}}]}");
        asg.put("FieldFunction", "{\"Type\":\"FieldFunction\",\"FieldFunction\":{\"Type\":\"Constant\",\"Value\":0.5},\"Delimiters\":[{\"Min\":0,\"Max\":1,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":" + B + "}}]}");
        asg.put("Sandwich", "{\"Type\":\"Sandwich\",\"Delimiters\":[{\"MinY\":0,\"MaxY\":100,\"Assignments\":{\"Type\":\"Constant\",\"Prop\":" + B + "}}]}");
        for (var e : asg.entrySet()) {
            try {
                Assignments<String> a = JsonAstCompiler.compileAssignments(parse(e.getValue()), ctx, id -> id);
                Prop<String> chosen = a.assign(new Vec3d(1, 50, 1), 7L);
                System.out.println("ASSIGN  " + e.getKey() + ": OK -> " + (chosen == null ? "null (no prop)" : chosen.getClass().getSimpleName()));
            } catch (Throwable t) { System.out.println("ASSIGN  " + e.getKey() + ": FAIL " + t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage()).split("\n")[0]); }
        }

        Map<String, String> pos = new LinkedHashMap<>();
        String G = "{\"Type\":\"SquareGrid2d\"}";
        pos.put("TriangularGrid2d", "{\"Type\":\"TriangularGrid2d\"}");
        pos.put("SquareGrid2d", G);
        pos.put("SquareGrid3d", "{\"Type\":\"SquareGrid3d\"}");
        pos.put("Scaler", "{\"Type\":\"Scaler\",\"Scale\":4,\"Positions\":" + G + "}");
        pos.put("Jitter2d", "{\"Type\":\"Jitter2d\",\"Magnitude\":2,\"Seed\":\"a\",\"Positions\":" + G + "}");
        pos.put("Jitter3d", "{\"Type\":\"Jitter3d\",\"Magnitude\":2,\"Seed\":\"a\",\"Positions\":" + G + "}");
        pos.put("DirectionalJitter", "{\"Type\":\"DirectionalJitter\",\"Magnitude\":2,\"Direction\":{\"X\":0,\"Y\":1,\"Z\":0},\"IsBidirectional\":true,\"Seed\":\"a\",\"Positions\":" + G + "}");
        pos.put("List", "{\"Type\":\"List\",\"Positions\":[{\"X\":1,\"Y\":2,\"Z\":3}]}");
        pos.put("Anchor", "{\"Type\":\"Anchor\",\"Reversed\":false,\"Positions\":" + G + "}");
        pos.put("Bound", "{\"Type\":\"Bound\",\"Bounds\":{\"Min\":{\"X\":0,\"Y\":0,\"Z\":0},\"Max\":{\"X\":8,\"Y\":8,\"Z\":8}},\"Positions\":" + G + "}");
        pos.put("FieldFunction", "{\"Type\":\"FieldFunction\",\"Density\":{\"Type\":\"Constant\",\"Value\":0.5},\"Delimiters\":[{\"Min\":0,\"Max\":1}],\"Positions\":" + G + "}");
        pos.put("Occurrence", "{\"Type\":\"Occurrence\",\"FieldFunction\":{\"Type\":\"Constant\",\"Value\":0.5},\"Seed\":\"a\",\"Positions\":" + G + "}");
        pos.put("Offset", "{\"Type\":\"Offset\",\"Offset\":{\"X\":0,\"Y\":5,\"Z\":0},\"Positions\":" + G + "}");
        pos.put("Union", "{\"Type\":\"Union\",\"Positions\":[" + G + "," + G + "]}");
        pos.put("SimpleHorizontal", "{\"Type\":\"SimpleHorizontal\",\"RangeY\":{\"Min\":0,\"Max\":10},\"Positions\":" + G + "}");
        pos.put("Cache", "{\"Type\":\"Cache\",\"SectionSize\":32,\"CacheSize\":16,\"Positions\":" + G + "}");
        pos.put("BaseHeight", "{\"Type\":\"BaseHeight\",\"BaseHeightName\":\"Base\",\"Positions\":" + G + "}");
        for (var e : pos.entrySet()) {
            try {
                PositionsProvider p = JsonAstCompiler.compilePositionsProvider(parse(e.getValue()), ctx);
                List<Vec3d> pts = p.getPositions(0, 0, 0, 16, 16, 16);
                double ymin = 1e9, ymax = -1e9;
                for (Vec3d v : pts) { ymin = Math.min(ymin, v.y()); ymax = Math.max(ymax, v.y()); }
                System.out.println("POSITION " + e.getKey() + ": OK count=" + pts.size() + (pts.isEmpty() ? "" : " y[" + ymin + ".." + ymax + "]"));
            } catch (Throwable t) { System.out.println("POSITION " + e.getKey() + ": FAIL " + t.getClass().getSimpleName() + " " + String.valueOf(t.getMessage()).split("\n")[0]); }
        }
    }
}
