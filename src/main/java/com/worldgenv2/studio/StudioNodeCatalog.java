package com.worldgenv2.studio;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.biome.WorldGenV2Registries;

import java.util.*;

/**
 * Exhaustive catalog and schema registry of all ~295 WorldGen V2 & Hytale Generator AST node types
 * across all 24 official categories.
 * <p>
 * Provides typed input/output socket schemas, interactive parameter definitions (sliders, numbers,
 * enums, booleans, block pickers), mathematical formulas, and semantic search tags for the Studio
 * Node Editor and Built-In Wiki.
 */
public final class StudioNodeCatalog {

    public enum SocketDomain {
        DENSITY("Density", StudioTheme.COLOR_DENSITY),
        CURVE("Curve", StudioTheme.COLOR_CURVE),
        VECTOR("Vector", StudioTheme.COLOR_VECTOR),
        MATERIAL("Material", StudioTheme.COLOR_MATERIAL),
        PATTERN("Pattern", 0xFFCC66FF),
        MASK("Mask", 0xFFFF66B2),
        POSITIONS("Positions", StudioTheme.COLOR_POSITIONS),
        SCANNER("Scanner", 0xFF33CCFF),
        ASSIGNMENTS("Assign", 0xFFFFCC33),
        PROP("Prop", StudioTheme.COLOR_PROP),
        GRAPH("Graph", StudioTheme.COLOR_GRAPH),
        BIOME("Biome", 0xFFFFAA00),
        ANY("Any", 0xFFCCCCCC);

        public final String label;
        public final int color;

        SocketDomain(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public boolean isCompatibleWith(SocketDomain other) {
            if (this == ANY || other == ANY) return true;
            if (this == other) return true;
            // Allow Positions/Scanner and Prop/Assignments interoperability where appropriate
            if ((this == POSITIONS && other == SCANNER) || (this == SCANNER && other == POSITIONS)) return true;
            if ((this == PROP && other == ASSIGNMENTS) || (this == ASSIGNMENTS && other == PROP)) return true;
            if ((this == MATERIAL && other == BIOME) || (this == DENSITY && other == BIOME)) return true;
            return false;
        }
    }

    public enum ParamType {
        FLOAT,
        INT,
        BOOLEAN,
        ENUM,
        STRING,
        BLOCK,
        STRUCTURE
    }

    public record SocketDef(String id, String label, SocketDomain domain, boolean multiInput) {
        public SocketDef(String id, String label, SocketDomain domain) {
            this(id, label, domain, false);
        }
    }

    public record ParamDef(
        String key,
        String label,
        ParamType type,
        String defaultValue,
        double min,
        double max,
        double step,
        String[] enumOptions
    ) {
        public static ParamDef flt(String key, String label, double def, double min, double max, double step) {
            return new ParamDef(key, label, ParamType.FLOAT, String.valueOf(def), min, max, step, null);
        }

        public static ParamDef integer(String key, String label, int def, int min, int max, int step) {
            return new ParamDef(key, label, ParamType.INT, String.valueOf(def), min, max, step, null);
        }

        public static ParamDef bool(String key, String label, boolean def) {
            return new ParamDef(key, label, ParamType.BOOLEAN, String.valueOf(def), 0, 1, 1, null);
        }

        public static ParamDef enm(String key, String label, String def, String... options) {
            return new ParamDef(key, label, ParamType.ENUM, def, 0, options.length - 1, 1, options);
        }

        public static ParamDef str(String key, String label, String def) {
            return new ParamDef(key, label, ParamType.STRING, def, 0, 0, 0, null);
        }

        public static ParamDef block(String key, String label, String defBlockId) {
            return new ParamDef(key, label, ParamType.BLOCK, defBlockId, 0, 0, 0, null);
        }

        public static ParamDef structure(String key, String label, String defStructId) {
            return new ParamDef(key, label, ParamType.STRUCTURE, defStructId, 0, 0, 0, null);
        }
    }

    public record NodeSchema(
        String id,
        String typeName,
        String displayName,
        String category,
        int headerColor,
        List<SocketDef> inputs,
        List<SocketDef> outputs,
        List<ParamDef> params,
        String formula,
        String description,
        String[] tags
    ) {}

    public static final String[] CATEGORIES = new String[] {
        "All",
        "Density",
        "Curve",
        "Vector",
        "Material",
        "Pattern",
        "Mask",
        "Positions",
        "Scanner",
        "Assign",
        "Prop",
        "Graph",
        "World"
    };

    private static final List<NodeSchema> ALL_SCHEMAS = new ArrayList<>();
    private static final Map<String, NodeSchema> BY_ID = new LinkedHashMap<>();
    private static boolean initialized = false;

    public static synchronized List<NodeSchema> getAllSchemas() {
        ensureInitialized();
        return Collections.unmodifiableList(ALL_SCHEMAS);
    }

    public static synchronized NodeSchema getSchema(String idOrType) {
        ensureInitialized();
        NodeSchema found = BY_ID.get(idOrType);
        if (found != null) return found;
        for (NodeSchema s : ALL_SCHEMAS) {
            if (s.typeName().equalsIgnoreCase(idOrType) || s.displayName().equalsIgnoreCase(idOrType)) {
                return s;
            }
        }
        return ALL_SCHEMAS.getFirst();
    }

    private static void reg(NodeSchema schema) {
        if (!BY_ID.containsKey(schema.id())) {
            ALL_SCHEMAS.add(schema);
            BY_ID.put(schema.id(), schema);
            BY_ID.putIfAbsent(schema.typeName(), schema);
        }
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        JsonAstCompiler.registerBuiltInTypes();

        // ====================================================================
        // 1. DENSITY NODES (Volumetric 3D/2D Scalar Fields & Math)
        // ====================================================================
        int cDen = 0xFF286E34; // Vanilla emerald/forest green header
        SocketDef outDen = new SocketDef("out", "Out", SocketDomain.DENSITY);
        SocketDef inDen = new SocketDef("input", "In", SocketDomain.DENSITY);
        SocketDef inDenA = new SocketDef("in_a", "A", SocketDomain.DENSITY);
        SocketDef inDenB = new SocketDef("in_b", "B", SocketDomain.DENSITY);

        reg(new NodeSchema("Density:ConstantDensity", "ConstantDensity", "ConstantDensity", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(ParamDef.flt("value", "Value", 0.0, -1000.0, 1000.0, 0.1)),
            "D(x,y,z) = c", "Outputs a constant scalar value everywhere in 3D space.",
            new String[]{"constant", "number", "scalar", "value", "math"}));

        reg(new NodeSchema("Density:VerticalGradientDensity", "VerticalGradientDensity", "VerticalGradient", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("baseHeight", "Base Y", 64.0, -64.0, 512.0, 1.0),
                ParamDef.flt("scale", "Scale", 32.0, 1.0, 512.0, 1.0)
            ),
            "D(y) = (baseHeight - y) / scale", "Gravitational vertical gradient: positive (solid) below Base Y, negative (air) above.",
            new String[]{"gravity", "height", "vertical", "gradient", "elevation", "y"}));

        reg(new NodeSchema("Density:YSampledDensity", "YSampledDensity", "YSampledDensity", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(ParamDef.integer("yInterval", "Y Step", 8, 1, 64, 1)),
            "D(x,y,z) = Lerp(D(y0), D(y1))", "Samples child 3D density every Y Step blocks vertically and linearly interpolates in-between for massive SIMD speedups.",
            new String[]{"ysampled", "interpolate", "cache", "height", "gravity"}));

        reg(new NodeSchema("Density:SimplexNoise2D", "SimplexNoise2D", "SimplexNoise2D", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("frequency", "Freq", 0.005, 0.0001, 1.0, 0.001),
                ParamDef.integer("octaves", "Octaves", 3, 1, 8, 1),
                ParamDef.flt("amplitude", "Amp", 1.0, 0.0, 64.0, 0.1),
                ParamDef.integer("seed", "Seed", 101, 0, 99999, 1)
            ),
            "D(x,z) =Amp * FBM_Simplex2D(x*f, z*f)", "2D multi-octave OpenSimplex2 fractal noise for continental heightmaps and hills.",
            new String[]{"noise", "simplex", "2d", "heightmap", "hills", "mountain"}));

        reg(new NodeSchema("Density:SimplexNoise3D", "SimplexNoise3D", "SimplexNoise3D", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("frequency", "Freq", 0.01, 0.0001, 1.0, 0.001),
                ParamDef.integer("octaves", "Octaves", 3, 1, 8, 1),
                ParamDef.flt("amplitude", "Amp", 1.0, 0.0, 64.0, 0.1),
                ParamDef.flt("xzScale", "XZ Scale", 1.0, 0.05, 16.0, 0.1),
                ParamDef.flt("yScale", "Y Scale", 1.0, 0.05, 16.0, 0.1),
                ParamDef.integer("seed", "Seed", 202, 0, 99999, 1)
            ),
            "D(x,y,z) = Amp * FBM_Simplex3D(x,y,z)", "3D volumetric multi-octave OpenSimplex2 noise for overhangs, floating islands, and 3D cliffs.",
            new String[]{"noise", "simplex", "3d", "overhang", "arch", "volumetric"}));

        reg(new NodeSchema("Density:Noise2dDensity", "Noise2dDensity", "Noise2dDensity", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.enm("noiseType", "Type", "OpenSimplex2", "OpenSimplex2", "Perlin", "Cellular", "Value"),
                ParamDef.flt("frequency", "Freq", 0.005, 0.0001, 1.0, 0.001),
                ParamDef.integer("octaves", "Octaves", 3, 1, 8, 1),
                ParamDef.flt("amplitude", "Amp", 1.0, 0.0, 64.0, 0.1),
                ParamDef.enm("fractalType", "Fractal", "FBm", "FBm", "Ridged", "PingPong", "None")
            ),
            "D(x,z) = FastNoise2D(x, z)", "Configurable 2D FastNoiseLite generator supporting OpenSimplex2, Perlin, Cellular, and Ridged fractals.",
            new String[]{"noise", "fastnoiselite", "2d", "perlin", "ridged"}));

        reg(new NodeSchema("Density:Noise3dDensity", "Noise3dDensity", "Noise3dDensity", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.enm("noiseType", "Type", "OpenSimplex2", "OpenSimplex2", "Perlin", "Cellular", "Value"),
                ParamDef.flt("frequency", "Freq", 0.01, 0.0001, 1.0, 0.001),
                ParamDef.integer("octaves", "Octaves", 3, 1, 8, 1),
                ParamDef.flt("amplitude", "Amp", 1.0, 0.0, 64.0, 0.1),
                ParamDef.flt("xzScale", "XZ Scale", 1.0, 0.05, 16.0, 0.1),
                ParamDef.flt("yScale", "Y Scale", 1.0, 0.05, 16.0, 0.1)
            ),
            "D(x,y,z) = FastNoise3D(x, y, z)", "Configurable 3D FastNoiseLite volumetric noise generator.",
            new String[]{"noise", "fastnoiselite", "3d", "perlin", "volumetric"}));

        reg(new NodeSchema("Density:WhiteNoise", "WhiteNoise", "WhiteNoise", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(ParamDef.integer("seed", "Seed", 1337, 0, 99999, 1)),
            "D(x,y,z) = Hash(x,y,z) in [-1, 1]", "Deterministic 3D stochastic white noise for dithering and rough surface breakup.",
            new String[]{"white", "noise", "random", "dither", "hash"}));

        reg(new NodeSchema("Density:CellNoise2D", "CellNoise2D", "CellNoise2D", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("frequency", "Freq", 0.01, 0.0005, 0.5, 0.001),
                ParamDef.flt("jitter", "Jitter", 0.3, 0.0, 1.0, 0.05),
                ParamDef.flt("amplitude", "Amp", 1.0, 0.0, 32.0, 0.1),
                ParamDef.enm("cellType", "Return", "Distance", "Distance", "Distance2", "Distance2Sub", "CellValue")
            ),
            "D(x,z) = Voronoi2D(x, z)", "2D Cellular Voronoi noise for cracked plateaus, canyons, and polygonal cells.",
            new String[]{"cell", "voronoi", "worley", "2d", "cracks", "mesa"}));

        reg(new NodeSchema("Density:CellNoise3D", "CellNoise3D", "CellNoise3D", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("frequency", "Freq", 0.015, 0.0005, 0.5, 0.001),
                ParamDef.flt("jitter", "Jitter", 0.35, 0.0, 1.0, 0.05),
                ParamDef.flt("amplitude", "Amp", 1.0, 0.0, 32.0, 0.1),
                ParamDef.enm("cellType", "Return", "Distance2Sub", "Distance", "Distance2", "Distance2Sub", "CellValue")
            ),
            "D(x,y,z) = Voronoi3D(x, y, z)", "3D Cellular Worley noise for Swiss-cheese caverns, tunnels, and crystal pockets.",
            new String[]{"cell", "voronoi", "worley", "3d", "cave", "caverns"}));

        reg(new NodeSchema("Density:CellWallDistance", "CellWallDistance", "CellWallDistance", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("frequency", "Freq", 0.012, 0.0005, 0.5, 0.001),
                ParamDef.flt("jitter", "Jitter", 0.4, 0.0, 1.0, 0.05),
                ParamDef.flt("wallThickness", "Wall", 0.15, 0.01, 1.0, 0.02)
            ),
            "D = d2 - d1 - wallThickness", "Computes distance to 3D Voronoi cell walls; ideal for interconnected cave walls and ridges.",
            new String[]{"cave", "wall", "worley", "voronoi", "ridge", "tunnels"}));

        reg(new NodeSchema("Density:PositionsCellNoise", "PositionsCellNoise", "PositionsCellNoise", "Density", cDen,
            List.of(new SocketDef("positions", "Points", SocketDomain.POSITIONS)), List.of(outDen),
            List.of(
                ParamDef.flt("maxDistance", "MaxDist", 64.0, 1.0, 512.0, 1.0),
                ParamDef.flt("radius", "Radius", 32.0, 1.0, 256.0, 1.0),
                ParamDef.enm("returnType", "Return", "Distance", "Distance", "Distance2", "Distance2Sub", "CellValue")
            ),
            "D(x) = DistToNearestPoint(Positions)", "Evaluates Voronoi cellular distance around an arbitrary PositionsProvider point field.",
            new String[]{"positions", "cell", "voronoi", "points"}));

        reg(new NodeSchema("Density:SumDensity", "SumDensity", "SumDensity", "Density", cDen,
            List.of(inDenA, inDenB, new SocketDef("in_c", "C (Opt)", SocketDomain.DENSITY)), List.of(outDen),
            List.of(ParamDef.flt("offset", "Bias", 0.0, -100.0, 100.0, 0.5)),
            "D = A + B (+ C) + Bias", "Adds multiple density fields together (e.g., Gravity Gradient + Mountain Noise).",
            new String[]{"sum", "add", "plus", "combine", "math"}));

        reg(new NodeSchema("Density:ProductDensity", "ProductDensity", "ProductDensity", "Density", cDen,
            List.of(inDenA, inDenB), List.of(outDen),
            List.of(ParamDef.flt("multiplier", "Scale", 1.0, -100.0, 100.0, 0.1)),
            "D = A * B * Scale", "Multiplies two density fields together; used for masking and amplitude modulation.",
            new String[]{"product", "multiply", "mul", "mask", "math"}));

        reg(new NodeSchema("Density:MinDensity", "MinDensity", "MinDensity", "Density", cDen,
            List.of(inDenA, inDenB), List.of(outDen),
            List.of(),
            "D = min(A, B)", "Outputs the minimum of two density fields (CSG intersection / cave carving).",
            new String[]{"min", "minimum", "intersect", "carve", "math"}));

        reg(new NodeSchema("Density:MaxDensity", "MaxDensity", "MaxDensity", "Density", cDen,
            List.of(inDenA, inDenB), List.of(outDen),
            List.of(),
            "D = max(A, B)", "Outputs the maximum of two density fields (CSG union of solid shapes).",
            new String[]{"max", "maximum", "union", "combine", "math"}));

        reg(new NodeSchema("Density:SmoothMin", "SmoothMin", "SmoothMin", "Density", cDen,
            List.of(inDenA, inDenB), List.of(outDen),
            List.of(ParamDef.flt("smoothness", "Smooth k", 0.25, 0.01, 10.0, 0.05)),
            "D = smin(A, B, k)", "Polynomial smooth minimum that blends CSG intersections without sharp creases.",
            new String[]{"smooth", "min", "blend", "organic", "sdf"}));

        reg(new NodeSchema("Density:SmoothMax", "SmoothMax", "SmoothMax", "Density", cDen,
            List.of(inDenA, inDenB), List.of(outDen),
            List.of(ParamDef.flt("smoothness", "Smooth k", 0.25, 0.01, 10.0, 0.05)),
            "D = smax(A, B, k)", "Polynomial smooth maximum that organically merges 3D terrain shapes.",
            new String[]{"smooth", "max", "blend", "organic", "sdf"}));

        reg(new NodeSchema("Density:ClampDensity", "ClampDensity", "ClampDensity", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.flt("min", "Min", -1.0, -1000.0, 1000.0, 0.1),
                ParamDef.flt("max", "Max", 1.0, -1000.0, 1000.0, 0.1)
            ),
            "D = clamp(In, min, max)", "Restricts a scalar density value to the closed interval [min, max].",
            new String[]{"clamp", "limit", "bound", "range"}));

        reg(new NodeSchema("Density:SmoothClamp", "SmoothClamp", "SmoothClamp", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.flt("min", "Min", -1.0, -100.0, 100.0, 0.1),
                ParamDef.flt("max", "Max", 1.0, -100.0, 100.0, 0.1),
                ParamDef.flt("smoothness", "Smooth", 0.2, 0.01, 5.0, 0.05)
            ),
            "D = sclamp(In, min, max, k)", "C1-continuous smooth clamping function for soft plateau tops.",
            new String[]{"smooth", "clamp", "plateau"}));

        reg(new NodeSchema("Density:NormalizerDensity", "NormalizerDensity", "NormalizerDensity", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.flt("fromMin", "In Min", -1.0, -500.0, 500.0, 0.5),
                ParamDef.flt("fromMax", "In Max", 1.0, -500.0, 500.0, 0.5),
                ParamDef.flt("toMin", "Out Min", 0.0, -500.0, 500.0, 0.5),
                ParamDef.flt("toMax", "Out Max", 1.0, -500.0, 500.0, 0.5)
            ),
            "D = remap(In, [fromMin,fromMax] -> [toMin,toMax])", "Linearly remaps an input range into a target output range.",
            new String[]{"normalizer", "remap", "range", "scale"}));

        reg(new NodeSchema("Density:Mix", "Mix", "Mix (Lerp)", "Density", cDen,
            List.of(inDenA, inDenB, new SocketDef("factor", "Factor", SocketDomain.DENSITY)), List.of(outDen),
            List.of(ParamDef.flt("alpha", "Default t", 0.5, 0.0, 1.0, 0.05)),
            "D = A * (1 - t) + B * t", "Linearly interpolates between density A and density B by factor t.",
            new String[]{"mix", "lerp", "blend", "interpolate"}));

        reg(new NodeSchema("Density:MultiMix", "MultiMix", "MultiMix", "Density", cDen,
            List.of(inDenA, inDenB, new SocketDef("in_c", "C", SocketDomain.DENSITY), new SocketDef("selector", "Index", SocketDomain.DENSITY)),
            List.of(outDen),
            List.of(),
            "D = PiecewiseLerp(Inputs[], Index)", "Blends across an array of density inputs driven by a continuous selector field.",
            new String[]{"multimix", "array", "blend", "lerp"}));

        reg(new NodeSchema("Density:AbsDensity", "AbsDensity", "AbsDensity", "Density", cDen,
            List.of(inDen), List.of(outDen), List.of(),
            "D = |In|", "Absolute value |D|; turns zero-crossings into sharp creases and ridged mountains.",
            new String[]{"abs", "absolute", "ridge", "crease", "math"}));

        reg(new NodeSchema("Density:InvertDensity", "InvertDensity", "InvertDensity", "Density", cDen,
            List.of(inDen), List.of(outDen), List.of(),
            "D = -In", "Negates the density sign (-D), swapping solid terrain and empty air.",
            new String[]{"invert", "negate", "flip", "minus"}));

        reg(new NodeSchema("Density:Sqrt", "Sqrt", "SqrtDensity", "Density", cDen,
            List.of(inDen), List.of(outDen), List.of(),
            "D = sqrt(max(0, In))", "Square root operator (clamped at 0 to prevent NaN).",
            new String[]{"sqrt", "root", "math"}));

        reg(new NodeSchema("Density:Pow", "Pow", "PowDensity", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(ParamDef.flt("exponent", "Exponent", 2.0, 0.1, 10.0, 0.1)),
            "D = sgn(In) * |In|^exponent", "Sign-preserving power function for steepening or flattening slopes.",
            new String[]{"pow", "power", "exponent", "math"}));

        reg(new NodeSchema("Density:Trig", "Trig", "TrigDensity", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.enm("function", "Func", "SIN", "SIN", "COS", "TAN"),
                ParamDef.flt("frequency", "Freq", 1.0, 0.01, 50.0, 0.1),
                ParamDef.flt("amplitude", "Amp", 1.0, 0.0, 100.0, 0.1),
                ParamDef.flt("phase", "Phase", 0.0, -6.28, 6.28, 0.1)
            ),
            "D = Amp * trig(In * Freq + Phase)", "Trigonometric wave shaper (SIN, COS, TAN) for periodic ripples and terraces.",
            new String[]{"trig", "sin", "cos", "tan", "wave", "math"}));

        reg(new NodeSchema("Density:CurveMapperDensity", "CurveMapperDensity", "CurveMapper", "Density", cDen,
            List.of(inDen, new SocketDef("curve", "Curve", SocketDomain.CURVE)), List.of(outDen),
            List.of(),
            "D = Curve.eval(In)", "Passes a 3D density scalar through a 1D Transfer Curve (e.g., ManualCurve spline).",
            new String[]{"curve", "mapper", "spline", "shaper", "transfer"}));

        reg(new NodeSchema("Density:GradientWarpDensity", "GradientWarpDensity", "GradientWarp", "Density", cDen,
            List.of(inDen, new SocketDef("warpSource", "WarpField", SocketDomain.DENSITY)), List.of(outDen),
            List.of(ParamDef.flt("warpStrength", "Strength", 12.0, 0.0, 128.0, 1.0)),
            "D(x) = In(x + strength * grad(Warp))", "Displaces coordinates along the 3D gradient of a secondary field for organic overhangs.",
            new String[]{"warp", "gradient", "domain", "overhang", "distort"}));

        reg(new NodeSchema("Density:FastGradientWarp", "FastGradientWarp", "FastGradientWarp", "Density", cDen,
            List.of(inDen, new SocketDef("warpSource", "WarpField", SocketDomain.DENSITY)), List.of(outDen),
            List.of(
                ParamDef.flt("warpStrength", "Strength", 16.0, 0.0, 128.0, 1.0),
                ParamDef.flt("stepSize", "Epsilon", 1.0, 0.1, 8.0, 0.1)
            ),
            "D(x) = In(x + strength * FastGrad(Warp))", "High-speed forward-difference 3D domain warp node.",
            new String[]{"fast", "warp", "gradient", "overhang"}));

        reg(new NodeSchema("Density:VectorWarpDensity", "VectorWarpDensity", "VectorWarp", "Density", cDen,
            List.of(inDen, new SocketDef("vectorProvider", "Vector", SocketDomain.VECTOR)), List.of(outDen),
            List.of(ParamDef.flt("scale", "WarpScale", 1.0, 0.0, 64.0, 0.5)),
            "D(x) = In(x + Vector(x))", "Warps sampling coordinates using any 3D VectorProvider field.",
            new String[]{"vector", "warp", "displace", "domain"}));

        reg(new NodeSchema("Density:OffsetDensity", "OffsetDensity", "Offset (Slider)", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.flt("offsetX", "Offset X", 0.0, -512.0, 512.0, 1.0),
                ParamDef.flt("offsetY", "Offset Y", 0.0, -512.0, 512.0, 1.0),
                ParamDef.flt("offsetZ", "Offset Z", 0.0, -512.0, 512.0, 1.0)
            ),
            "D(x,y,z) = In(x - dx, y - dy, z - dz)", "Translates / slides a 3D density field in world space.",
            new String[]{"offset", "slider", "translate", "move", "shift"}));

        reg(new NodeSchema("Density:ScaleDensity", "ScaleDensity", "ScaleDensity", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.flt("scaleX", "Scale X", 1.0, 0.01, 100.0, 0.1),
                ParamDef.flt("scaleY", "Scale Y", 1.0, 0.01, 100.0, 0.1),
                ParamDef.flt("scaleZ", "Scale Z", 1.0, 0.01, 100.0, 0.1)
            ),
            "D(x,y,z) = In(x/sx, y/sy, z/sz)", "Stretches or compresses the spatial coordinates of a child density node.",
            new String[]{"scale", "stretch", "frequency", "transform"}));

        reg(new NodeSchema("Density:Rotator", "Rotator", "RotatorDensity", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.enm("axis", "Axis", "Y", "X", "Y", "Z"),
                ParamDef.flt("angle", "Angle Deg", 45.0, -180.0, 180.0, 5.0)
            ),
            "D(x) = In(Rotate(x, Axis, Angle))", "Rotates the 3D coordinate system around X, Y, or Z by Angle degrees for tilted strata.",
            new String[]{"rotate", "rotator", "tilt", "angle", "transform"}));

        // 3D SDF Primitives
        reg(new NodeSchema("Density:EllipsoidDensity", "EllipsoidDensity", "Ellipsoid SDF", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("radiusX", "Radius X", 24.0, 1.0, 256.0, 1.0),
                ParamDef.flt("radiusY", "Radius Y", 16.0, 1.0, 256.0, 1.0),
                ParamDef.flt("radiusZ", "Radius Z", 24.0, 1.0, 256.0, 1.0)
            ),
            "D = 1 - sqrt((x/rx)^2 + (y/ry)^2 + (z/rz)^2)", "3D Ellipsoid / Sphere signed distance field for floating islands, domes, and caverns.",
            new String[]{"ellipsoid", "sphere", "ball", "sdf", "shape", "island"}));

        reg(new NodeSchema("Density:CuboidDensity", "CuboidDensity", "Cuboid SDF", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("halfX", "Half X", 16.0, 1.0, 256.0, 1.0),
                ParamDef.flt("halfY", "Half Y", 16.0, 1.0, 256.0, 1.0),
                ParamDef.flt("halfZ", "Half Z", 16.0, 1.0, 256.0, 1.0)
            ),
            "D = BoxSDF(x, y, z, hx, hy, hz)", "3D Rectangular Box / Cube signed distance field.",
            new String[]{"cuboid", "cube", "box", "sdf", "shape"}));

        reg(new NodeSchema("Density:CylinderDensity", "CylinderDensity", "Cylinder SDF", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.enm("axis", "Axis", "Y", "X", "Y", "Z"),
                ParamDef.flt("radius", "Radius", 16.0, 1.0, 256.0, 1.0),
                ParamDef.flt("halfLength", "Half Len", 32.0, 1.0, 512.0, 1.0)
            ),
            "D = CylinderSDF(Axis, r, h)", "3D Cylinder signed distance field for pillars, shafts, and tunnels.",
            new String[]{"cylinder", "pillar", "column", "tube", "sdf"}));

        reg(new NodeSchema("Density:PlaneDensity", "PlaneDensity", "Plane SDF", "Density", cDen,
            List.of(), List.of(outDen),
            List.of(
                ParamDef.flt("normalX", "Normal X", 0.0, -1.0, 1.0, 0.1),
                ParamDef.flt("normalY", "Normal Y", -1.0, -1.0, 1.0, 0.1),
                ParamDef.flt("normalZ", "Normal Z", 0.0, -1.0, 1.0, 0.1),
                ParamDef.flt("distance", "Offset d", 64.0, -256.0, 512.0, 1.0)
            ),
            "D = -(nx*x + ny*y + nz*z) + d", "Infinite 3D half-space plane defined by normal vector and distance.",
            new String[]{"plane", "flat", "halfspace", "sdf"}));

        reg(new NodeSchema("Density:ShellDensity", "ShellDensity", "Shell SDF", "Density", cDen,
            List.of(inDen), List.of(outDen),
            List.of(
                ParamDef.flt("innerRadius", "Inner", 12.0, 0.0, 256.0, 1.0),
                ParamDef.flt("outerRadius", "Outer", 16.0, 0.5, 256.0, 1.0)
            ),
            "D = HollowShell(In, r_in, r_out)", "Converts a solid SDF shape into a hollow spherical/tubular shell with configurable wall thickness.",
            new String[]{"shell", "hollow", "dome", "geode", "sdf"}));

        // Logic, Comparators, Coordinates & Caching
        String[][] logicDensityNodes = {
            {"DistanceToBiomeEdgeDensity", "DistanceToBiomeEdge", "D = distToEdge / maxDist", "Normalized distance [0, 1] to the nearest Voronoi biome boundary for seamless edge blending."},
            {"ComparatorDensity", "ComparatorDensity", "D = compare(A, B)", "Compares two density inputs and outputs signed difference."},
            {"EqualDensity", "EqualDensity", "D = |A - B| <= eps ? 1 : -1", "Outputs +1 where A and B are equal within tolerance."},
            {"GreaterThanDensity", "GreaterThan", "D = (A > B) ? 1 : -1", "Boolean relational operator A > B."},
            {"GreaterOrEqualDensity", "GreaterOrEqual", "D = (A >= B) ? 1 : -1", "Boolean relational operator A >= B."},
            {"LessThanDensity", "LessThan", "D = (A < B) ? 1 : -1", "Boolean relational operator A < B."},
            {"LessOrEqualDensity", "LessOrEqual", "D = (A <= B) ? 1 : -1", "Boolean relational operator A <= B."},
            {"AndDensity", "AndDensity", "D = (A > 0 && B > 0) ? 1 : -1", "Logical AND gate on volumetric density sign."},
            {"OrDensity", "OrDensity", "D = (A > 0 || B > 0) ? 1 : -1", "Logical OR gate on volumetric density sign."},
            {"NotDensity", "NotDensity", "D = (In <= 0) ? 1 : -1", "Logical NOT gate on volumetric density sign."},
            {"NorDensity", "NorDensity", "D = !(A > 0 || B > 0)", "Logical NOR gate on volumetric density sign."},
            {"XorDensity", "XorDensity", "D = (A > 0) ^ (B > 0)", "Logical XOR gate on volumetric density sign."},
            {"SelectorDensity", "SelectorDensity", "D = (Cond >= min && Cond <= max) ? TrueIn : FalseIn", "Selects between two density branches based on a condition range."},
            {"SwitchDensity", "SwitchDensity", "D = CaseBranch(Selector)", "Multi-branch switch selector across density subgraphs."},
            {"SwitchState", "SwitchStateDensity", "D = StateMap[ActiveState]", "Evaluates different density subgraphs depending on named contextual state."},
            {"XValueDensity", "XValue", "D(x,y,z) = x", "Outputs the raw world X coordinate."},
            {"YValueDensity", "YValue", "D(x,y,z) = y", "Outputs the raw world Y coordinate (elevation)."},
            {"ZValueDensity", "ZValue", "D(x,y,z) = z", "Outputs the raw world Z coordinate."},
            {"XOverrideDensity", "XOverride", "D(x,y,z) = In(NewX, y, z)", "Overrides the X coordinate before evaluating Input using a second density node."},
            {"YOverrideDensity", "YOverride", "D(x,y,z) = In(x, NewY, z)", "Overrides the Y coordinate before evaluating Input (e.g. flattening Y to 2D)."},
            {"ZOverrideDensity", "ZOverride", "D(x,y,z) = In(x, y, NewZ)", "Overrides the Z coordinate before evaluating Input."},
            {"CacheDensity", "CacheDensity", "D = ThreadLocalCache(In)", "Caches costly 2D/3D subgraph evaluations per column or voxel."},
            {"SingleInstanceDensity", "SingleInstance", "D = SharedSubgraph(In)", "Ensures an exported subgraph shares a single cache instance across all consumers."},
            {"PositionsPinch", "PositionsPinch", "D = PinchAroundPoints(In, Points)", "Pinches/compresses coordinates radially toward nearby PositionsProvider points."},
            {"PositionsTwist", "PositionsTwist", "D = TwistAroundPoints(In, Points)", "Swirls/twists coordinates in a vortex around nearby PositionsProvider points."},
            {"Anchor", "AnchorDensity", "D = In(x - Anchor)", "Evaluates child density in local coordinates relative to the active Prop/Structure anchor."},
            {"BaseHeight", "BaseHeightDensity", "D = BaseHeightMap(x, z)", "Reads the world structure macro base elevation."},
            {"Terrain", "TerrainDensity", "D = ActiveTerrainField(x,y,z)", "References the current biome's primary terrain density field."},
            {"Distance", "DistanceDensity", "D = ||x - origin||", "Radial Euclidean distance from origin."},
            {"Angle", "AngleDensity", "D = atan2(z, x)", "Polar angle around the Y axis."},
            {"GraphDensity", "GraphDensity", "D = GraphCarver(x,y,z)", "Reads volumetric density carved or deposited by the Procedural Graph Generator."},
            {"Imported", "ImportedDensity", "D = ExternalAsset(path)", "Imports a reusable Density subgraph JSON asset."},
            {"Exported", "ExportedDensity", "D = ExportPin(In)", "Marks a Density subgraph for external reuse."}
        };
        for (String[] row : logicDensityNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("Edge")) p.add(ParamDef.flt("maxDistance", "MaxDist", 64.0, 1.0, 512.0, 1.0));
            else if (row[0].contains("Selector")) {
                p.add(ParamDef.flt("min", "Min", 0.0, -100.0, 100.0, 0.1));
                p.add(ParamDef.flt("max", "Max", 1.0, -100.0, 100.0, 0.1));
            } else if (row[0].contains("Imported")) {
                p.add(ParamDef.str("asset", "Asset Path", "shared/noise.json"));
            } else if (row[0].contains("Pinch") || row[0].contains("Twist")) {
                p.add(ParamDef.flt("radius", "Radius", 24.0, 1.0, 128.0, 1.0));
                p.add(ParamDef.flt("strength", "Strength", 1.5, -10.0, 10.0, 0.1));
            }
            List<SocketDef> ins = row[0].contains("Value") || row[0].contains("Edge") || row[0].contains("BaseHeight") || row[0].contains("Imported")
                ? List.of()
                : (row[0].contains("Equal") || row[0].contains("Greater") || row[0].contains("Less") || row[0].contains("And") || row[0].contains("Or") || row[0].contains("Nor") || row[0].contains("Xor") || row[0].contains("Comparator") || row[0].contains("Override")
                    ? List.of(inDenA, inDenB)
                    : List.of(inDen));
            reg(new NodeSchema("Density:" + row[0], row[0], row[1], "Density", cDen, ins, List.of(outDen), p, row[2], row[3],
                new String[]{row[1].toLowerCase(Locale.ROOT), "density", "logic"}));
        }

        // ====================================================================
        // 2. CURVE NODES (1D Transfer Functions y = f(x))
        // ====================================================================
        int cCrv = 0xFF1F6E8C; // Deep Cyan/Blue header
        SocketDef outCrv = new SocketDef("out", "Curve", SocketDomain.CURVE);
        SocketDef inCrv = new SocketDef("input", "In", SocketDomain.CURVE);
        SocketDef inCrvA = new SocketDef("in_a", "A", SocketDomain.CURVE);
        SocketDef inCrvB = new SocketDef("in_b", "B", SocketDomain.CURVE);

        reg(new NodeSchema("Curve:ManualCurve", "ManualCurve", "ManualCurve (Spline)", "Curve", cCrv,
            List.of(), List.of(outCrv),
            List.of(
                ParamDef.enm("mode", "Interp", "SMOOTH", "SMOOTH", "LINEAR", "STEP"),
                ParamDef.flt("y0", "Y at -1", -0.8, -2.0, 2.0, 0.05),
                ParamDef.flt("yMid", "Y at 0", 0.1, -2.0, 2.0, 0.05),
                ParamDef.flt("y1", "Y at +1", 1.0, -2.0, 2.0, 0.05)
            ),
            "y = HermiteSpline(x, Keyframes)", "Piecewise Hermite/Catmull-Rom spline curve for sculpting continental shelves, terraces, and peaks.",
            new String[]{"curve", "manual", "spline", "hermite", "terrace", "continental"}));

        reg(new NodeSchema("Curve:DistanceSCurve", "DistanceSCurve", "DistanceSCurve", "Curve", cCrv,
            List.of(), List.of(outCrv),
            List.of(
                ParamDef.flt("range", "Range", 64.0, 1.0, 512.0, 1.0),
                ParamDef.flt("steepness", "Steepness", 2.0, 0.1, 10.0, 0.1)
            ),
            "y = 3t^2 - 2t^3", "Smooth S-shaped sigmoidal curve for feathering biome transitions and shorelines.",
            new String[]{"scurve", "sigmoid", "smoothstep", "distance", "curve"}));

        reg(new NodeSchema("Curve:DistanceExponentialCurve", "DistanceExponentialCurve", "DistanceExponential", "Curve", cCrv,
            List.of(), List.of(outCrv),
            List.of(
                ParamDef.flt("range", "Range", 64.0, 1.0, 512.0, 1.0),
                ParamDef.flt("exponent", "Exponent", 2.5, 0.1, 10.0, 0.1)
            ),
            "y = (1 - clamp(x/range, 0, 1))^exp", "Exponential falloff curve over distance.",
            new String[]{"exponential", "falloff", "distance", "curve"}));

        String[][] curveNodes = {
            {"ConstantCurve", "ConstantCurve", "y = c", "Constant 1D transfer function.", "value", "0.5"},
            {"Linear", "LinearCurve", "y = m*x + b", "Linear transfer function with slope and offset.", "slope", "1.0"},
            {"CeilingCurve", "CeilingCurve", "y = min(In(x), ceiling)", "Hard upper ceiling cap on a curve.", "ceiling", "1.0"},
            {"FloorCurve", "FloorCurve", "y = max(In(x), floor)", "Hard lower floor bound on a curve.", "floor", "0.0"},
            {"SmoothCeilingCurve", "SmoothCeiling", "y = smin(In(x), ceiling, k)", "C1 smooth upper ceiling cap.", "ceiling", "1.0"},
            {"SmoothFloorCurve", "SmoothFloor", "y = smax(In(x), floor, k)", "C1 smooth lower floor cap.", "floor", "0.0"},
            {"ClampCurve", "ClampCurve", "y = clamp(In(x), min, max)", "Clamps curve output between min and max.", "max", "1.0"},
            {"SmoothClampCurve", "SmoothClampCurve", "y = sclamp(In(x), min, max)", "Smoothly clamps curve output.", "smoothness", "0.2"},
            {"MinCurve", "MinCurve", "y = min(A(x), B(x))", "Pointwise minimum of two curves.", "", ""},
            {"MaxCurve", "MaxCurve", "y = max(A(x), B(x))", "Pointwise maximum of two curves.", "", ""},
            {"SmoothMinCurve", "SmoothMinCurve", "y = smin(A(x), B(x), k)", "Pointwise smooth minimum of two curves.", "smoothness", "0.2"},
            {"SmoothMaxCurve", "SmoothMaxCurve", "y = smax(A(x), B(x), k)", "Pointwise smooth maximum of two curves.", "smoothness", "0.2"},
            {"InverterCurve", "InverterCurve", "y = -In(x)", "Negates the curve output.", "", ""},
            {"NotCurve", "NotCurve", "y = 1.0 - In(x)", "Complements a [0, 1] curve (1 - y).", "", ""},
            {"MultiplierCurve", "MultiplierCurve", "y = A(x) * B(x)", "Multiplies two curves together.", "factor", "1.0"},
            {"SumCurve", "SumCurve", "y = A(x) + B(x)", "Adds two curves together.", "bias", "0.0"},
            {"Normalizer", "NormalizerCurve", "y = remap(x)", "Normalizes curve input/output domain.", "toMax", "1.0"},
            {"Cache", "CacheCurve", "y = LUT(In(x))", "Precomputes a fast lookup table for a complex curve.", "samples", "256"},
            {"Imported", "ImportedCurve", "y = AssetCurve(x)", "Imports an external Curve JSON asset.", "asset", "shared/curve.json"}
        };
        for (String[] row : curveNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (!row[4].isEmpty()) {
                if (row[4].equals("asset")) p.add(ParamDef.str("asset", "Asset", row[5]));
                else p.add(ParamDef.flt(row[4], row[4], Double.parseDouble(row[5]), -100.0, 100.0, 0.1));
            }
            List<SocketDef> ins = row[0].contains("Min") || row[0].contains("Max") || row[0].contains("Multi") || row[0].contains("Sum")
                ? List.of(inCrvA, inCrvB)
                : (row[0].contains("Constant") || row[0].contains("Linear") || row[0].contains("Imported") ? List.of() : List.of(inCrv));
            reg(new NodeSchema("Curve:" + row[0], row[0], row[1], "Curve", cCrv, ins, List.of(outCrv), p, row[2], row[3],
                new String[]{"curve", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 3. VECTOR PROVIDER NODES (3D Vector Fields & Domain Warpers)
        // ====================================================================
        int cVec = 0xFF9E5818; // Warm Amber/Orange header
        SocketDef outVec = new SocketDef("out", "Vector", SocketDomain.VECTOR);
        SocketDef inVec = new SocketDef("input", "Vec", SocketDomain.VECTOR);
        SocketDef inVecA = new SocketDef("vec_a", "Vec A", SocketDomain.VECTOR);
        SocketDef inVecB = new SocketDef("vec_b", "Vec B", SocketDomain.VECTOR);

        reg(new NodeSchema("Vector:ConstantVectorProvider", "ConstantVectorProvider", "ConstantVector", "Vector", cVec,
            List.of(), List.of(outVec),
            List.of(
                ParamDef.flt("x", "Vec X", 0.0, -100.0, 100.0, 0.5),
                ParamDef.flt("y", "Vec Y", 1.0, -100.0, 100.0, 0.5),
                ParamDef.flt("z", "Vec Z", 0.0, -100.0, 100.0, 0.5)
            ),
            "V = (x, y, z)", "Constant 3D directional vector.",
            new String[]{"vector", "constant", "direction", "xyz"}));

        reg(new NodeSchema("Vector:DensityGradientVectorProvider", "DensityGradientVectorProvider", "DensityGradient", "Vector", cVec,
            List.of(new SocketDef("density", "Density", SocketDomain.DENSITY)), List.of(outVec),
            List.of(ParamDef.flt("step", "Step h", 1.0, 0.1, 16.0, 0.1)),
            "V = grad(D) = (dD/dx, dD/dy, dD/dz)", "Computes the 3D spatial gradient vector of a scalar Density field (surface normal direction).",
            new String[]{"gradient", "normal", "slope", "vector", "density"}));

        reg(new NodeSchema("Vector:SimplexNoise", "SimplexNoise", "SimplexNoiseVector", "Vector", cVec,
            List.of(), List.of(outVec),
            List.of(
                ParamDef.flt("frequency", "Freq", 0.01, 0.0005, 1.0, 0.001),
                ParamDef.flt("amplitude", "Strength", 16.0, 0.0, 128.0, 1.0),
                ParamDef.integer("octaves", "Octaves", 2, 1, 6, 1)
            ),
            "V(x) = (NoiseX, NoiseY, NoiseZ) * Strength", "3-channel decorrelated Simplex vector field for 3D domain warping.",
            new String[]{"simplex", "noise", "vector", "warp", "turbulence"}));

        String[][] vecNodes = {
            {"SetXVectorProvider", "SetXVector", "V = (NewX, V.y, V.z)", "Replaces the X component of a 3D vector using a scalar Density input."},
            {"SetYVectorProvider", "SetYVector", "V = (V.x, NewY, V.z)", "Replaces the Y component of a 3D vector using a scalar Density input."},
            {"SetZVectorProvider", "SetZVector", "V = (V.x, V.y, NewZ)", "Replaces the Z component of a 3D vector using a scalar Density input."},
            {"AdderVectorProvider", "AdderVector", "V = A + B", "Component-wise 3D vector addition."},
            {"SubtracterVectorProvider", "SubtracterVector", "V = A - B", "Component-wise 3D vector subtraction."},
            {"MultiplierVectorProvider", "MultiplierVector", "V = A * B", "Component-wise Hadamard 3D vector product."},
            {"ScalarMultiplierVectorProvider", "ScalarMultiplier", "V = Vec * Scalar", "Scales a 3D vector by a scalar Density field."},
            {"NormalizerVectorProvider", "NormalizerVector", "V = Vec / ||Vec||", "Normalizes a 3D vector to unit length 1.0."},
            {"CrossVectorProvider", "CrossVector", "V = A x B", "3D vector cross product orthogonal to A and B."},
            {"PlaneProjectorVectorProvider", "PlaneProjector", "V = Vec - (Vec . N) * N", "Projects a 3D vector onto a plane defined by normal N."},
            {"VectorProjectorVectorProvider", "VectorProjector", "V = ((A . B) / ||B||^2) * B", "Projects vector A onto vector B."},
            {"RandomVectorProvider", "RandomVector", "V = RandomUnitVec(x,y,z)", "Stochastic 3D jitter vector field."},
            {"CacheVectorProvider", "CacheVector", "V = Cache(Vec)", "Caches 3D vector evaluations per voxel."},
            {"Rotate", "RotateVector", "V = Rot(Vec, Axis, Deg)", "Rotates a 3D vector around X, Y, or Z."},
            {"Scale", "ScaleVector", "V = (Vx*sx, Vy*sy, Vz*sz)", "Scales individual components of a 3D vector."},
            {"Interpolate", "InterpolateVector", "V = lerp(A, B, t)", "Linearly interpolates between two 3D vectors."},
            {"Combine", "CombineVector", "V = (Dx, Dy, Dz)", "Combines three scalar Density fields into a single 3D Vector."},
            {"Split", "SplitVector", "D = V.component", "Extracts X, Y, or Z component from a 3D Vector."},
            {"DotProduct", "DotProductVector", "D = A . B", "Computes scalar dot product of two 3D vectors."},
            {"Angle", "AngleVector", "D = acos((A.B)/(|A||B|))", "Angle in radians/degrees between two 3D vectors."},
            {"Imported", "ImportedVector", "V = Asset(path)", "Imports an external VectorProvider JSON asset."},
            {"Exported", "ExportedVector", "V = Export(Vec)", "Exports a VectorProvider subgraph."}
        };
        for (String[] row : vecNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("Scalar") || row[0].contains("Scale")) p.add(ParamDef.flt("scale", "Scale", 1.0, -64.0, 64.0, 0.5));
            else if (row[0].contains("Rotate")) p.add(ParamDef.flt("angle", "Angle", 45.0, -180.0, 180.0, 5.0));
            List<SocketDef> ins = row[0].contains("Adder") || row[0].contains("Sub") || row[0].contains("Multi") || row[0].contains("Cross") || row[0].contains("Projector") || row[0].contains("Interp") || row[0].contains("Dot") || row[0].contains("Angle")
                ? List.of(inVecA, inVecB)
                : (row[0].contains("Set") ? List.of(inVec, new SocketDef("scalar", "Value", SocketDomain.DENSITY)) : List.of(inVec));
            reg(new NodeSchema("Vector:" + row[0], row[0], row[1], "Vector", cVec, ins, List.of(outVec), p, row[2], row[3],
                new String[]{"vector", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 4. MATERIAL & STRATA NODES (Geological Rules & Surface Blocks)
        // ====================================================================
        int cMat = 0xFF6B2D84; // Rich Purple header
        SocketDef outMat = new SocketDef("out", "Material", SocketDomain.MATERIAL);
        SocketDef inMat = new SocketDef("fallback", "Fallback", SocketDomain.MATERIAL);

        reg(new NodeSchema("Material:ConstantMaterialProvider", "ConstantMaterialProvider", "ConstantMaterial", "Material", cMat,
            List.of(), List.of(outMat),
            List.of(ParamDef.block("block", "Block", "minecraft:stone")),
            "Block = ConstantBlock", "Assigns a single solid or fluid block (supports all vanilla and modded blocks).",
            new String[]{"material", "block", "constant", "stone"}));

        reg(new NodeSchema("Material:SpaceAndDepthMaterialProvider", "SpaceAndDepthMaterialProvider", "SpaceAndDepth", "Material", cMat,
            List.of(
                new SocketDef("in_terrain", "Terrain", SocketDomain.DENSITY),
                new SocketDef("topMaterial", "Topsoil", SocketDomain.MATERIAL),
                new SocketDef("fillerMaterial", "Subsoil", SocketDomain.MATERIAL),
                new SocketDef("deepMaterial", "Bedrock", SocketDomain.MATERIAL)
            ),
            List.of(outMat),
            List.of(
                ParamDef.block("surfaceBlock", "Top Block", "minecraft:grass_block"),
                ParamDef.block("subsoilBlock", "Sub Block", "minecraft:dirt"),
                ParamDef.block("cliffBlock", "Cliff Block", "minecraft:stone"),
                ParamDef.integer("subsoilDepth", "Sub Depth", 3, 1, 16, 1),
                ParamDef.integer("maxSlope", "Max Slope", 45, 0, 90, 5)
            ),
            "Block = MatchStrata(depth, slopeAngle, airClearance)", "Core geological strata engine: places Topsoil on gentle slopes, Subsoil below, and bare Cliff Block on steep cliff faces.",
            new String[]{"strata", "spaceanddepth", "layers", "grass", "dirt", "cliff", "surface", "material"}));

        reg(new NodeSchema("Material:StrataMaterialProvider", "StrataMaterialProvider", "StrataMaterial", "Material", cMat,
            List.of(inMat), List.of(outMat),
            List.of(
                ParamDef.block("upperBlock", "Upper", "minecraft:stone"),
                ParamDef.block("lowerBlock", "Lower", "minecraft:deepslate"),
                ParamDef.integer("splitY", "Split Y", 0, -64, 320, 4)
            ),
            "Block = StrataByElevation(y)", "Assigns geological rock strata based on Y elevation bands.",
            new String[]{"strata", "elevation", "deepslate", "layers", "material"}));

        reg(new NodeSchema("Material:StripedMaterialProvider", "StripedMaterialProvider", "StripedMaterial", "Material", cMat,
            List.of(), List.of(outMat),
            List.of(
                ParamDef.block("stripeA", "Band A", "minecraft:terracotta"),
                ParamDef.block("stripeB", "Band B", "minecraft:orange_terracotta"),
                ParamDef.integer("thickness", "Band H", 4, 1, 32, 1)
            ),
            "Block = Stripes[(y / thickness) % N]", "Repeating horizontal terracotta/sedimentary bands for Badlands and canyons.",
            new String[]{"striped", "bands", "terracotta", "badlands", "mesa", "material"}));

        reg(new NodeSchema("Material:QueueMaterialProvider", "QueueMaterialProvider", "QueueMaterial", "Material", cMat,
            List.of(
                new SocketDef("first", "1st Rule", SocketDomain.MATERIAL),
                new SocketDef("second", "2nd Rule", SocketDomain.MATERIAL),
                inMat
            ),
            List.of(outMat), List.of(),
            "Block = FirstNonNull(Rule1, Rule2, ...)", "Evaluates an ordered queue of material providers and returns the first match.",
            new String[]{"queue", "priority", "list", "material"}));

        reg(new NodeSchema("Material:WeightedMaterialProvider", "WeightedMaterialProvider", "WeightedMaterial", "Material", cMat,
            List.of(new SocketDef("matA", "Mat A", SocketDomain.MATERIAL), new SocketDef("matB", "Mat B", SocketDomain.MATERIAL)),
            List.of(outMat),
            List.of(
                ParamDef.block("blockA", "Block A", "minecraft:stone"),
                ParamDef.block("blockB", "Block B", "minecraft:cobblestone"),
                ParamDef.flt("weightA", "Weight A", 0.7, 0.0, 1.0, 0.05)
            ),
            "Block = WeightedChoice(A, B, Hash(x,y,z))", "Randomly mixes blocks according to probability weights for textured surfaces.",
            new String[]{"weighted", "random", "mix", "palette", "material"}));

        String[][] matNodes = {
            {"SolidityMaterialProvider", "SolidityMaterial", "Block = (D >= threshold) ? Solid : Fluid", "Switches between solid and fluid material providers based on a Density threshold."},
            {"LayerMaterialProvider", "LayerMaterial", "Block = (y in [minY, maxY]) ? Inside : Outside", "Applies a material within a specific [minY, maxY] altitude layer."},
            {"SimpleHorizontalMaterialProvider", "SimpleHorizontalMat", "Block = HorizontalBand(y)", "Applies material across a horizontal elevation span."},
            {"FieldFunctionMaterialProvider", "FieldFunctionMat", "Block = BandLookup(Density(x,y,z))", "Selects materials based on continuous 3D noise/density intervals."},
            {"ConditionMaterialProvider", "ConditionMaterial", "Block = Pattern.matches(x,y,z) ? TrueMat : FalseMat", "Applies a material only where a structural Pattern matches (e.g., Floor, Ceiling, Slope)."},
            {"TransparentMaterialProvider", "TransparentMaterial", "Block = null (Skip)", "Returns null/transparent so downstream queue providers can handle the voxel."},
            {"SurfaceMaterialProvider", "SurfaceMaterial", "Block = SurfaceRule(depth)", "Top-surface material rule with configurable depth."},
            {"StackedMaterialProvider", "StackedMaterial", "Block = VerticalStack(depth)", "Stacks multiple material layers downward from the surface."},
            {"TerrainMaterialProvider", "TerrainMaterial", "Block = DefaultTerrainBlock", "Outputs the dimension or biome default stone block."},
            {"MaskMaterialProvider", "MaskMaterial", "Block = Masked(Material, BlockMask)", "Filters material placement through a BlockMask."},
            {"GraphMaterialProvider", "GraphMaterial", "Block = GraphContentMaterial(x,y,z)", "Reads road, riverbed, or dungeon materials from the Procedural Graph Generator."},
            {"Material", "MaterialLiteral", "Block + Rotation(Yaw,Pitch,Roll)", "Explicit solid + fluid material pair with 90-degree 3D rotation metadata."},
            {"Imported", "ImportedMaterial", "Block = Asset(path)", "Imports an external MaterialProvider JSON asset."},
            {"Exported", "ExportedMaterial", "Block = Export(Mat)", "Exports a MaterialProvider subgraph."}
        };
        for (String[] row : matNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("Layer") || row[0].contains("Horizontal")) {
                p.add(ParamDef.integer("minY", "Min Y", 50, -64, 512, 1));
                p.add(ParamDef.integer("maxY", "Max Y", 70, -64, 512, 1));
            } else if (row[0].equals("Material")) {
                p.add(ParamDef.block("solid", "Solid", "minecraft:stone"));
                p.add(ParamDef.enm("yaw", "Yaw", "0", "0", "90", "180", "270"));
            }
            List<SocketDef> ins = row[0].contains("Condition")
                ? List.of(new SocketDef("pattern", "Pattern", SocketDomain.PATTERN), new SocketDef("trueMat", "Then", SocketDomain.MATERIAL), inMat)
                : List.of(inMat);
            reg(new NodeSchema("Material:" + row[0], row[0], row[1], "Material", cMat, ins, List.of(outMat), p, row[2], row[3],
                new String[]{"material", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 5. PATTERN NODES (Voxel Neighborhood & Surface Validation)
        // ====================================================================
        int cPat = 0xFF7B3F9E;
        SocketDef outPat = new SocketDef("out", "Pattern", SocketDomain.PATTERN);
        SocketDef inPat = new SocketDef("input", "Pat", SocketDomain.PATTERN);

        reg(new NodeSchema("Pattern:SurfacePattern", "SurfacePattern", "SurfacePattern", "Pattern", cPat,
            List.of(), List.of(outPat),
            List.of(ParamDef.integer("airClearance", "Air Above", 1, 1, 16, 1)),
            "Match = IsSolid(x,y,z) && IsAir(x, y+1..k, z)", "Matches solid top surface blocks with required open air clearance above.",
            new String[]{"surface", "floor", "ground", "top", "pattern"}));

        reg(new NodeSchema("Pattern:SlopePattern", "SlopePattern", "SlopePattern", "Pattern", cPat,
            List.of(), List.of(outPat),
            List.of(
                ParamDef.flt("minSlope", "Min Deg", 0.0, 0.0, 90.0, 5.0),
                ParamDef.flt("maxSlope", "Max Deg", 45.0, 0.0, 90.0, 5.0)
            ),
            "Match = slopeAngle in [minSlope, maxSlope]", "Matches terrain surfaces whose steepness angle in degrees falls within [min, max].",
            new String[]{"slope", "angle", "cliff", "steep", "pattern"}));

        reg(new NodeSchema("Pattern:BlockTypePattern", "BlockType", "BlockTypePattern", "Pattern", cPat,
            List.of(), List.of(outPat),
            List.of(ParamDef.block("block", "Match Block", "minecraft:grass_block")),
            "Match = (WorldBlock(x,y,z) == Block)", "Matches when the existing voxel in the chunk matches the selected Block.",
            new String[]{"blocktype", "block", "match", "filter", "pattern"}));

        String[][] patNodes = {
            {"CeilingPattern", "CeilingPattern", "Match = IsSolid(y) && IsAir(y-1)", "Matches subterranean cave ceiling undersides."},
            {"WallPattern", "WallPattern", "Match = IsSolid(y) && HasAdjacentAirXZ()", "Matches vertical cliff walls and cave walls."},
            {"Floor", "FloorPattern", "Match = IsAir(y) && IsSolid(y-1)", "Matches air voxel immediately resting on a solid floor."},
            {"DepthPattern", "DepthPattern", "Match = depthBelowSurface in [min, max]", "Matches voxels within a specific depth range below the terrain surface."},
            {"SpaceCheckPattern", "SpaceCheckPattern", "Match = BoxIsClear(hx, hy, hz)", "Verifies that a 3D bounding volume is unobstructed before placing structures."},
            {"GapPattern", "GapPattern", "Match = VerticalSpanInRange(min, max)", "Matches cave or canyon gaps of a specific vertical height."},
            {"CuboidPattern", "CuboidPattern", "Match = AllVoxelsInBoxMatch(SubPattern)", "Checks that every voxel in a 3D cuboid satisfies a sub-pattern."},
            {"BlockSet", "BlockSetPattern", "Match = Block in AllowedSet", "Matches if the voxel belongs to a set/tag of blocks."},
            {"OffsetPattern", "OffsetPattern", "Match = Pat(x+dx, y+dy, z+dz)", "Evaluates a child pattern at a relative (dx, dy, dz) offset."},
            {"Rotator", "RotatorPattern", "Match = Pat(Rotate90(x,y,z))", "Rotates a child pattern in 90-degree increments."},
            {"AndPattern", "AndPattern", "Match = A && B", "Logical AND of multiple voxel patterns."},
            {"OrPattern", "OrPattern", "Match = A || B", "Logical OR of multiple voxel patterns."},
            {"NotPattern", "NotPattern", "Match = !Pat", "Logical NOT inversion of a voxel pattern."},
            {"FieldFunction", "FieldFunctionPattern", "Match = Density(x,y,z) in [min, max]", "Matches where a 3D Density field falls within a target range."},
            {"Constant", "ConstantPattern", "Match = true / false", "Always-true or always-false constant pattern."},
            {"BlockMask", "BlockMaskPattern", "Match = Mask.canReplace()", "Adapts a BlockMask into a Pattern."},
            {"Imported", "ImportedPattern", "Match = Asset(path)", "Imports an external Pattern JSON asset."},
            {"Exported", "ExportedPattern", "Match = Export(Pat)", "Exports a Pattern subgraph."}
        };
        for (String[] row : patNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("Depth") || row[0].contains("Gap")) {
                p.add(ParamDef.integer("min", "Min", 1, 0, 128, 1));
                p.add(ParamDef.integer("max", "Max", 5, 1, 128, 1));
            } else if (row[0].contains("Offset")) {
                p.add(ParamDef.integer("dy", "Offset Y", -1, -32, 32, 1));
            }
            List<SocketDef> ins = row[0].contains("And") || row[0].contains("Or")
                ? List.of(new SocketDef("a", "Pat A", SocketDomain.PATTERN), new SocketDef("b", "Pat B", SocketDomain.PATTERN))
                : (row[0].contains("Not") || row[0].contains("Offset") || row[0].contains("Rotator") ? List.of(inPat) : List.of());
            reg(new NodeSchema("Pattern:" + row[0], row[0], row[1], "Pattern", cPat, ins, List.of(outPat), p, row[2], row[3],
                new String[]{"pattern", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 6. BLOCK MASK NODES (Source vs Destination Replacement Rules)
        // ====================================================================
        int cMsk = 0xFF9E2B68;
        SocketDef outMsk = new SocketDef("out", "Mask", SocketDomain.MASK);

        reg(new NodeSchema("Mask:DontReplaceMask", "DontReplaceMask", "DontReplaceMask", "Mask", cMsk,
            List.of(), List.of(outMsk),
            List.of(ParamDef.block("protectedBlock", "Protect", "minecraft:bedrock")),
            "Allow = (Dest != ProtectedBlock)", "Prevents props or carvers from overwriting protected existing blocks (e.g., bedrock).",
            new String[]{"mask", "dontreplace", "protect", "bedrock"}));

        reg(new NodeSchema("Mask:DontPlaceMask", "DontPlaceMask", "DontPlaceMask", "Mask", cMsk,
            List.of(), List.of(outMsk),
            List.of(ParamDef.block("excludedBlock", "Exclude", "minecraft:air")),
            "Allow = (Source != ExcludedBlock)", "Prevents placing specific source blocks (e.g., skipping structure air pockets).",
            new String[]{"mask", "dontplace", "air", "skip"}));

        reg(new NodeSchema("Mask:AdvancedBlockMask", "AdvancedBlockMask", "AdvancedBlockMask", "Mask", cMsk,
            List.of(), List.of(outMsk),
            List.of(
                ParamDef.block("sourceBlock", "Source", "minecraft:moss_block"),
                ParamDef.block("destBlock", "Target", "minecraft:stone")
            ),
            "Allow = MatrixMatch(Source, Dest)", "N x M source-to-destination replacement matrix rule.",
            new String[]{"mask", "advanced", "matrix", "replace"}));

        reg(new NodeSchema("Mask:SourceMaterial", "SourceMaterial", "SourceMaterialRule", "Mask", cMsk,
            List.of(), List.of(outMsk),
            List.of(ParamDef.block("block", "Source", "minecraft:stone")),
            "Source == Block", "Specifies the incoming Source Material in a BlockMask rule.",
            new String[]{"source", "mask", "material"}));

        reg(new NodeSchema("Mask:DestinationMaterial", "DestinationMaterial", "DestinationMaterialRule", "Mask", cMsk,
            List.of(), List.of(outMsk),
            List.of(ParamDef.block("block", "Dest", "minecraft:stone")),
            "Dest == Block", "Specifies the existing world Destination Material in a BlockMask rule.",
            new String[]{"destination", "dest", "mask", "material"}));

        reg(new NodeSchema("Mask:Imported", "Imported", "ImportedBlockMask", "Mask", cMsk,
            List.of(), List.of(outMsk),
            List.of(ParamDef.str("asset", "Asset", "shared/mask.json")),
            "Mask = Asset(path)", "Imports an external BlockMask JSON asset.",
            new String[]{"imported", "mask"}));

        reg(new NodeSchema("Mask:Exported", "Exported", "ExportedBlockMask", "Mask", cMsk,
            List.of(new SocketDef("in", "Mask", SocketDomain.MASK)), List.of(outMsk), List.of(),
            "Mask = Export(In)", "Exports a BlockMask subgraph.",
            new String[]{"exported", "mask"}));

        // ====================================================================
        // 7. POSITIONS PROVIDER NODES (Point Fields, Grids & Blue Noise)
        // ====================================================================
        int cPos = 0xFF147A7A; // Teal/Cyan header
        SocketDef outPos = new SocketDef("out", "Positions", SocketDomain.POSITIONS);
        SocketDef inPos = new SocketDef("input", "Points", SocketDomain.POSITIONS);

        reg(new NodeSchema("Positions:PoissonDisk", "PoissonDisk", "PoissonDisk (BlueNoise)", "Positions", cPos,
            List.of(), List.of(outPos),
            List.of(
                ParamDef.flt("spacing", "Min Dist", 12.0, 2.0, 128.0, 1.0),
                ParamDef.integer("seed", "Seed", 777, 0, 99999, 1)
            ),
            "Pts = PoissonDisk2D(minDistance)", "Natural blue-noise point distribution with guaranteed minimum spacing; ideal for trees and boulders.",
            new String[]{"poisson", "bluenoise", "scatter", "trees", "positions"}));

        reg(new NodeSchema("Positions:TriangularGrid2dPositionsProvider", "TriangularGrid2dPositionsProvider", "TriangularGrid2d", "Positions", cPos,
            List.of(), List.of(outPos),
            List.of(ParamDef.flt("spacing", "Spacing", 16.0, 1.0, 256.0, 1.0)),
            "Pts = HexTriangularLattice(spacing)", "Equilateral triangular / hexagonal 2D point tessellation.",
            new String[]{"triangular", "hex", "grid", "lattice", "positions"}));

        reg(new NodeSchema("Positions:SquareGrid2dPositionsProvider", "SquareGrid2dPositionsProvider", "SquareGrid2d", "Positions", cPos,
            List.of(), List.of(outPos),
            List.of(ParamDef.flt("spacing", "Spacing", 16.0, 1.0, 256.0, 1.0)),
            "Pts = SquareGrid2D(spacing)", "Regular 2D Cartesian grid of candidate column coordinates.",
            new String[]{"square", "grid", "2d", "positions"}));

        reg(new NodeSchema("Positions:SquareGrid3dPositionsProvider", "SquareGrid3dPositionsProvider", "SquareGrid3d", "Positions", cPos,
            List.of(), List.of(outPos),
            List.of(
                ParamDef.flt("spacingXZ", "Step XZ", 16.0, 1.0, 256.0, 1.0),
                ParamDef.flt("spacingY", "Step Y", 16.0, 1.0, 256.0, 1.0)
            ),
            "Pts = Grid3D(dx, dy, dz)", "3D volumetric lattice of candidate points for cave features and floating props.",
            new String[]{"square", "grid", "3d", "volumetric", "positions"}));

        reg(new NodeSchema("Positions:Jitter2dPositionsProvider", "Jitter2dPositionsProvider", "Jitter2d", "Positions", cPos,
            List.of(inPos), List.of(outPos),
            List.of(ParamDef.flt("magnitude", "Jitter XZ", 4.0, 0.0, 64.0, 0.5)),
            "Pt' = Pt + RandomOffsetXZ(mag)", "Randomly perturbs input points horizontally in X and Z to break up grid regularity.",
            new String[]{"jitter", "random", "2d", "perturb", "positions"}));

        reg(new NodeSchema("Positions:Jitter3dPositionsProvider", "Jitter3dPositionsProvider", "Jitter3d", "Positions", cPos,
            List.of(inPos), List.of(outPos),
            List.of(ParamDef.flt("magnitude", "Jitter XYZ", 4.0, 0.0, 64.0, 0.5)),
            "Pt' = Pt + RandomOffsetXYZ(mag)", "Randomly perturbs input points in all three dimensions (X, Y, Z).",
            new String[]{"jitter", "random", "3d", "positions"}));

        String[][] posNodes = {
            {"DirectionalJitterPositionsProvider", "DirectionalJitter", "Pt' = Pt + Dir * Rand()", "Perturbs points along a specific 3D direction vector."},
            {"ScalerPositionsProvider", "ScalerPositions", "Pt' = Pt * (sx, sy, sz)", "Scales point coordinates by (sx, sy, sz)."},
            {"ClustersPositionsProvider", "ClustersPositions", "Pts = ExpandCluster(SeedPts, count, radius)", "Spawns tight organic clusters of child points around each parent point (e.g., flower patches, ore veins)."},
            {"OccurrencePositionsProvider", "OccurrencePositions", "Pts = FilterByChance(In, probability)", "Randomly keeps or culls each input point based on a probability [0, 1] or Density field."},
            {"FieldFunctionPositionsProvider", "FieldFunctionPositions", "Pts = FilterByDensity(In, min, max)", "Retains only points where a 3D Density field falls within [min, max] (e.g., lush patches)."},
            {"OffsetPositionsProvider", "OffsetPositions", "Pt' = Pt + (dx, dy, dz)", "Translates all candidate points by a constant (dx, dy, dz) offset."},
            {"VectorOffsetPositionsProvider", "VectorOffsetPositions", "Pt' = Pt + Vector(Pt)", "Displaces candidate points using a 3D VectorProvider field."},
            {"UnionPositionsProvider", "UnionPositions", "Pts = A U B", "Merges multiple point streams into a single unified PositionsProvider."},
            {"SimpleHorizontalPositionsProvider", "SimpleHorizontalPos", "Pts =GridXZ at fixed Y", "Generates a horizontal plane of points at a target Y elevation."},
            {"AnchorPositionsProvider", "AnchorPositions", "Pt = LocalAnchor(x, y, z)", "Emits a single local anchor point."},
            {"ListPositionsProvider", "ListPositions", "Pts = ExplicitCoordinateList", "Emits an explicit list of fixed (x, y, z) coordinates."},
            {"BoundPositionsProvider", "BoundPositions", "Pts = CropToAABB(In, Box)", "Clips a point stream to a 3D bounding box."},
            {"BaseHeight", "BaseHeightPositions", "Pt.y = BaseHeight(Pt.x, Pt.z)", "Projects 2D points onto the world structure macro base height."},
            {"Framework", "FrameworkPositions", "Pts = GlobalFrameworkPoints(Name)", "Reads global world-structure framework points."},
            {"Cache", "CachePositions", "Pts = Cached(In)", "Caches generated point coordinates per chunk."},
            {"GraphPositionsProvider", "GraphPositions", "Pts = GraphNodeAndEdgePoints", "Samples positions along Procedural Graph nodes and edges."},
            {"Imported", "ImportedPositions", "Pts = Asset(path)", "Imports an external PositionsProvider JSON asset."},
            {"Exported", "ExportedPositions", "Pts = Export(In)", "Exports a PositionsProvider subgraph."}
        };
        for (String[] row : posNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("Clusters")) {
                p.add(ParamDef.integer("count", "Count", 5, 1, 32, 1));
                p.add(ParamDef.flt("radius", "Radius", 6.0, 1.0, 64.0, 0.5));
            } else if (row[0].contains("Occurrence")) {
                p.add(ParamDef.flt("chance", "Chance", 0.5, 0.0, 1.0, 0.05));
            } else if (row[0].contains("SimpleHorizontal")) {
                p.add(ParamDef.flt("spacing", "Spacing", 16.0, 1.0, 128.0, 1.0));
                p.add(ParamDef.flt("y", "Plane Y", 64.0, -64.0, 512.0, 1.0));
            }
            reg(new NodeSchema("Positions:" + row[0], row[0], row[1], "Positions", cPos, List.of(inPos), List.of(outPos), p, row[2], row[3],
                new String[]{"positions", "points", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 8. SCANNER NODES (Voxel Raycasting & Surface Probing)
        // ====================================================================
        int cScn = 0xFF1A6885;
        SocketDef outScn = new SocketDef("out", "Scanner", SocketDomain.SCANNER);
        SocketDef inPatScn = new SocketDef("pattern", "ValidPat", SocketDomain.PATTERN);

        reg(new NodeSchema("Scanner:LinearScanner", "LinearScanner", "LinearScanner", "Scanner", cScn,
            List.of(inPatScn), List.of(outScn),
            List.of(
                ParamDef.enm("axis", "Axis", "Y", "X", "Y", "Z"),
                ParamDef.integer("range", "Range", 64, 1, 384, 4),
                ParamDef.bool("topDown", "Top-Down", true),
                ParamDef.integer("maxResults", "Max Hits", 1, 1, 16, 1)
            ),
            "Hits = Raycast(Axis, Range, Pattern)", "Casts a linear voxel probe along X, Y, or Z (e.g. top-down sky raycast) to find surfaces matching Pattern.",
            new String[]{"scanner", "linear", "raycast", "surface", "ground", "ceiling"}));

        reg(new NodeSchema("Scanner:RadialScanner", "RadialScanner", "RadialScanner", "Scanner", cScn,
            List.of(inPatScn), List.of(outScn),
            List.of(
                ParamDef.integer("radius", "Radius", 8, 1, 64, 1),
                ParamDef.integer("maxResults", "Max Hits", 4, 1, 32, 1)
            ),
            "Hits = SphereSearch(Origin, Radius, Pattern)", "Probes outward in a 3D spherical shell to locate valid anchor voxels.",
            new String[]{"scanner", "radial", "sphere", "probe"}));

        reg(new NodeSchema("Scanner:RandomScanner", "RandomScanner", "RandomScanner", "Scanner", cScn,
            List.of(inPatScn), List.of(outScn),
            List.of(
                ParamDef.integer("radius", "Radius", 8, 1, 64, 1),
                ParamDef.integer("attempts", "Attempts", 16, 1, 128, 1)
            ),
            "Hits = MonteCarloProbe(Radius, Attempts)", "Stochastically samples candidate offsets around the origin until Pattern matches.",
            new String[]{"scanner", "random", "montecarlo"}));

        reg(new NodeSchema("Scanner:OriginScanner", "OriginScanner", "OriginScanner", "Scanner", cScn,
            List.of(inPatScn), List.of(outScn), List.of(),
            "Hits = Pattern(Origin) ? [Origin] : []", "Tests the exact input coordinate without raycasting.",
            new String[]{"scanner", "origin", "point"}));

        reg(new NodeSchema("Scanner:QueueScanner", "QueueScanner", "QueueScanner", "Scanner", cScn,
            List.of(new SocketDef("first", "Scan 1", SocketDomain.SCANNER), new SocketDef("second", "Scan 2", SocketDomain.SCANNER)),
            List.of(outScn), List.of(),
            "Hits = FirstNonEmpty(Scanner1, Scanner2)", "Tries a sequence of Scanners in order until valid placement anchors are found.",
            new String[]{"scanner", "queue", "fallback"}));

        reg(new NodeSchema("Scanner:Imported", "Imported", "ImportedScanner", "Scanner", cScn,
            List.of(), List.of(outScn), List.of(ParamDef.str("asset", "Asset", "shared/scanner.json")),
            "Scanner = Asset(path)", "Imports an external Scanner JSON asset.", new String[]{"scanner", "imported"}));

        reg(new NodeSchema("Scanner:Exported", "Exported", "ExportedScanner", "Scanner", cScn,
            List.of(new SocketDef("in", "Scanner", SocketDomain.SCANNER)), List.of(outScn), List.of(),
            "Scanner = Export(In)", "Exports a Scanner subgraph.", new String[]{"scanner", "exported"}));

        // ====================================================================
        // 9. ASSIGNMENTS NODES (Decoupled Prop-to-Position Mapping)
        // ====================================================================
        int cAsg = 0xFF9E7B1C;
        SocketDef outAsg = new SocketDef("out", "Assign", SocketDomain.ASSIGNMENTS);
        SocketDef inPropAsg = new SocketDef("prop", "Prop", SocketDomain.PROP);

        reg(new NodeSchema("Assign:ConstantAssignment", "ConstantAssignment", "ConstantAssignment", "Assign", cAsg,
            List.of(inPropAsg), List.of(outAsg), List.of(),
            "Assign(pt) = Prop", "Always assigns the connected Prop to every candidate point.",
            new String[]{"assignment", "constant", "prop"}));

        reg(new NodeSchema("Assign:WeightedAssignment", "WeightedAssignment", "WeightedAssignment", "Assign", cAsg,
            List.of(new SocketDef("propA", "Prop A", SocketDomain.PROP), new SocketDef("propB", "Prop B", SocketDomain.PROP)),
            List.of(outAsg),
            List.of(
                ParamDef.flt("weightA", "Weight A", 0.7, 0.0, 10.0, 0.1),
                ParamDef.flt("weightB", "Weight B", 0.3, 0.0, 10.0, 0.1)
            ),
            "Assign(pt) = WeightedChoice(PropA, PropB)", "Randomly selects between multiple Props (e.g., Oak vs Birch tree variants) by weight.",
            new String[]{"assignment", "weighted", "random", "variant"}));

        reg(new NodeSchema("Assign:FieldFunctionAssignment", "FieldFunctionAssignment", "FieldFunctionAssign", "Assign", cAsg,
            List.of(new SocketDef("field", "Density", SocketDomain.DENSITY), new SocketDef("propA", "Low Prop", SocketDomain.PROP), new SocketDef("propB", "High Prop", SocketDomain.PROP)),
            List.of(outAsg),
            List.of(ParamDef.flt("threshold", "Split", 0.0, -10.0, 10.0, 0.1)),
            "Assign(pt) = LookupByDensity(D(pt))", "Selects which Prop to place based on a continuous 3D Density field (e.g., tall trees in core, shrubs on edge).",
            new String[]{"assignment", "field", "density", "zone"}));

        reg(new NodeSchema("Assign:SandwichAssignment", "SandwichAssignment", "SandwichAssignment", "Assign", cAsg,
            List.of(new SocketDef("bottom", "Low Y", SocketDomain.PROP), new SocketDef("middle", "Mid Y", SocketDomain.PROP), new SocketDef("top", "High Y", SocketDomain.PROP)),
            List.of(outAsg),
            List.of(
                ParamDef.integer("minY", "Mid MinY", 64, -64, 512, 4),
                ParamDef.integer("maxY", "Mid MaxY", 140, -64, 512, 4)
            ),
            "Assign(pt) = ElevationBand(pt.y)", "Assigns different Props across vertical altitude strata (e.g., valley oaks -> mid pines -> alpine shrubs).",
            new String[]{"assignment", "sandwich", "altitude", "elevation"}));

        reg(new NodeSchema("Assign:Imported", "Imported", "ImportedAssignment", "Assign", cAsg,
            List.of(), List.of(outAsg), List.of(ParamDef.str("asset", "Asset", "shared/assign.json")),
            "Assign = Asset(path)", "Imports an external Assignments JSON asset.", new String[]{"assignment", "imported"}));

        reg(new NodeSchema("Assign:Exported", "Exported", "ExportedAssignment", "Assign", cAsg,
            List.of(new SocketDef("in", "Assign", SocketDomain.ASSIGNMENTS)), List.of(outAsg), List.of(),
            "Assign = Export(In)", "Exports an Assignments subgraph.", new String[]{"assignment", "exported"}));

        // ====================================================================
        // 10. PROP, PROP DISTRIBUTION & PROP RUNTIME NODES
        // ====================================================================
        int cPrp = 0xFF8A7A1F;
        SocketDef outPrp = new SocketDef("out", "Prop", SocketDomain.PROP);
        SocketDef inPrp = new SocketDef("prop", "Child", SocketDomain.PROP);

        reg(new NodeSchema("Prop:BlockProp", "BlockProp", "BlockProp", "Prop", cPrp,
            List.of(), List.of(outPrp),
            List.of(ParamDef.block("block", "Block", "minecraft:short_grass")),
            "PlaceBlock(Anchor, Block)", "Places a single block (flower, grass, ore, or modded block) at each scanned anchor.",
            new String[]{"prop", "block", "flower", "grass", "foliage"}));

        reg(new NodeSchema("Prop:CuboidProp", "CuboidProp", "CuboidProp", "Prop", cPrp,
            List.of(new SocketDef("material", "Mat (Opt)", SocketDomain.MATERIAL)), List.of(outPrp),
            List.of(
                ParamDef.block("block", "Block", "minecraft:mossy_cobblestone"),
                ParamDef.integer("sizeX", "Size X", 3, 1, 32, 1),
                ParamDef.integer("sizeY", "Size Y", 3, 1, 32, 1),
                ParamDef.integer("sizeZ", "Size Z", 3, 1, 32, 1)
            ),
            "PlaceBox(sizeX, sizeY, sizeZ, Material)", "Places a 3D cuboid volume of blocks or MaterialProvider at the anchor.",
            new String[]{"prop", "cuboid", "box", "boulder", "pillar"}));

        reg(new NodeSchema("Prop:PrefabProp", "PrefabProp", "PrefabProp", "Prop", cPrp,
            List.of(new SocketDef("mask", "Mask (Opt)", SocketDomain.MASK)), List.of(outPrp),
            List.of(
                ParamDef.str("prefab", "Prefab ID", "trees/oak_large"),
                ParamDef.bool("adaptFoundation", "Plinths", true),
                ParamDef.integer("maxPlinthDepth", "Max Plinth", 8, 0, 32, 1)
            ),
            "PlacePrefab(ID, Rotation, FoundationPlinths)", "Places a 3D `.prefab.json` structure with optional adaptive foundation plinths and socket alignment.",
            new String[]{"prop", "prefab", "structure", "tree", "house", "dungeon", "plinth"}));

        reg(new NodeSchema("Prop:DensityProp", "DensityProp", "DensityProp (3D Blob)", "Prop", cPrp,
            List.of(
                new SocketDef("density", "Shape SDF", SocketDomain.DENSITY),
                new SocketDef("material", "Material", SocketDomain.MATERIAL)
            ),
            List.of(outPrp),
            List.of(ParamDef.integer("radius", "Bound R", 8, 1, 64, 1)),
            "PlaceWhere(D_local(x,y,z) >= 0)", "Evaluates a local 3D Density field around each anchor to sculpt organic boulders, stalactites, or bushes.",
            new String[]{"prop", "density", "blob", "boulder", "stalactite", "spike"}));

        reg(new NodeSchema("Prop:PondFillerProp", "PondFillerProp", "PondFillerProp", "Prop", cPrp,
            List.of(), List.of(outPrp),
            List.of(
                ParamDef.block("fluidBlock", "Fluid", "minecraft:water"),
                ParamDef.block("basinBlock", "Basin", "minecraft:clay"),
                ParamDef.integer("maxRadius", "Max Rad", 6, 2, 24, 1)
            ),
            "FillClosedBasin(Origin, Fluid, Basin)", "Flood-fills closed terrain depressions with water/lava Only if the rim is sealed against spilling.",
            new String[]{"prop", "pond", "lake", "water", "pool", "filler"}));

        reg(new NodeSchema("Prop:LocatorProp", "LocatorProp", "LocatorProp", "Prop", cPrp,
            List.of(
                new SocketDef("scanner", "Scanner", SocketDomain.SCANNER),
                new SocketDef("pattern", "Pattern", SocketDomain.PATTERN),
                inPrp
            ),
            List.of(outPrp), List.of(),
            "ForEachHit(Scanner, Pattern) -> Place(Child)", "Runs a Scanner and Pattern check around the candidate point and places Child Prop at valid surface hits.",
            new String[]{"prop", "locator", "scanner", "anchor"}));

        reg(new NodeSchema("Prop:AssignmentsProp", "AssignmentsProp", "AssignmentsProp", "Prop", cPrp,
            List.of(
                new SocketDef("positions", "Positions", SocketDomain.POSITIONS),
                new SocketDef("scanner", "Scanner", SocketDomain.SCANNER),
                new SocketDef("assignments", "Assign", SocketDomain.ASSIGNMENTS)
            ),
            List.of(outPrp), List.of(),
            "Distribute(Positions -> Scanner -> Assignments)", "Full decoupled prop distribution pipeline connecting PositionsProvider, Scanner, and Assignments.",
            new String[]{"prop", "assignments", "distribution", "pipeline"}));

        String[][] propNodes = {
            {"MaskProp", "MaskProp", "PlaceWithMask(Child, BlockMask)", "Wraps a child Prop with a BlockMask replacement filter."},
            {"RandomRotatorProp", "RandomRotatorProp", "RotateY(Child, Random90())", "Randomly rotates child Prop by 0, 90, 180, or 270 degrees."},
            {"StaticRotatorProp", "StaticRotatorProp", "RotateY(Child, FixedDeg)", "Rotates child Prop by a fixed 90-degree increment."},
            {"OrienterProp", "OrienterProp", "OrientToSurfaceNormal(Child, Pattern)", "Automatically rotates child Prop to face adjacent walls, ceilings, or slopes."},
            {"OffsetProp", "OffsetProp", "Place(Child, Anchor + Offset)", "Shifts child Prop placement by (dx, dy, dz) blocks."},
            {"WeightedProp", "WeightedProp", "RandomWeightedChoice(Props[])", "Selects one child Prop from a weighted probability list."},
            {"QueueProp", "QueueProp", "PlaceFirstSucceeded(Props[])", "Attempts child Props in priority order until one succeeds."},
            {"UnionProp", "UnionProp", "PlaceAll(PropA, PropB, ...)", "Executes all connected child Props at the same anchor (e.g., tree trunk + vines + roots)."},
            {"DensitySelectorProp", "DensitySelectorProp", "SelectPropByDensity(D(anchor))", "Picks a child Prop based on a 3D Density field value."},
            {"ManualProp", "ManualProp", "PlaceExplicitBlockMap(Offsets[])", "Places an explicit inline list of relative (dx, dy, dz, block) voxels."},
            {"GraphProp", "GraphProp", "PlaceGraphContentProps()", "Places props generated by the Procedural Graph Generator."},
            {"PropRuntime", "PropRuntimeStage", "ExecuteInStage(StageIdx, Precision, Prop)", "Assigns a Prop pipeline to a discrete generation Stage (0..N) with Context Precision (BIOME/CHUNK/PROP)."},
            {"ContextPrecision", "ContextPrecision", "Precision = BIOME | CHUNK | PROP", "Configures halo context precision mode for staged prop execution."},
            {"ConstantPropDist", "ConstantPropDist", "PropDist = Constant(Prop)", "Constant prop distribution field."},
            {"PositionsPropDist", "PositionsPropDist", "PropDist = AtPositions(Positions, Prop)", "Binds a Prop directly to a PositionsProvider field."},
            {"AssignedPropDist", "AssignedPropDist", "PropDist = Assigned(Positions, Assign)", "Binds an Assignments table to a PositionsProvider field."},
            {"UnionPropDist", "UnionPropDist", "PropDist = Union(DistA, DistB)", "Combines multiple PropDistribution fields."},
            {"AnchorPropDist", "AnchorPropDist", "PropDist = RelativeToAnchor(Dist)", "Evaluates PropDistribution relative to a parent structure anchor."},
            {"Imported", "ImportedProp", "Prop = Asset(path)", "Imports an external Prop JSON asset."},
            {"Exported", "ExportedProp", "Prop = Export(In)", "Exports a Prop subgraph."}
        };
        for (String[] row : propNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("StaticRotator")) p.add(ParamDef.enm("degrees", "Degrees", "90", "0", "90", "180", "270"));
            else if (row[0].contains("Offset")) p.add(ParamDef.integer("dy", "Offset Y", 1, -32, 32, 1));
            else if (row[0].contains("Runtime") || row[0].contains("Precision")) {
                p.add(ParamDef.integer("stage", "Stage", 0, 0, 8, 1));
                p.add(ParamDef.enm("precision", "Precision", "CHUNK", "BIOME", "CHUNK", "PROP"));
            }
            List<SocketDef> ins = row[0].contains("Mask")
                ? List.of(inPrp, new SocketDef("mask", "Mask", SocketDomain.MASK))
                : (row[0].contains("Union") || row[0].contains("Queue") || row[0].contains("Weighted")
                    ? List.of(new SocketDef("a", "Prop A", SocketDomain.PROP), new SocketDef("b", "Prop B", SocketDomain.PROP))
                    : List.of(inPrp));
            reg(new NodeSchema("Prop:" + row[0], row[0], row[1], "Prop", cPrp, ins, List.of(outPrp), p, row[2], row[3],
                new String[]{"prop", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 11. PROCEDURAL GRAPH GENERATOR NODES (Passes, Selectors, Actions)
        // ====================================================================
        int cGrp = 0xFF8C2D2D; // Crimson Red header
        SocketDef outGrp = new SocketDef("out", "Graph", SocketDomain.GRAPH);
        SocketDef inGrp = new SocketDef("in", "Graph", SocketDomain.GRAPH);

        String[][] graphNodes = {
            // Core & Passes
            {"GraphGenerator", "GraphGenerator", "Graph = RunPasses(Passes[], CellSize)", "Macro topological network solver for rivers, cave labyrinths, and road networks."},
            {"PositionsGraphPass", "PositionsGraphPass", "V = V U SamplePositions(PositionsProvider)", "Populates graph vertices (V) from a PositionsProvider point field."},
            {"NodeActionPass", "NodeActionPass", "ForEach(SelectedNodes) -> Apply(NodeAction)", "Executes a NodeAction on all graph vertices matching a NodeSelector."},
            {"EdgeActionPass", "EdgeActionPass", "ForEach(SelectedEdges) -> Apply(EdgeAction)", "Executes an EdgeAction on all graph edges matching an EdgeSelector."},
            {"ManualGraphPass", "ManualGraphPass", "Graph += ExplicitNodesAndEdges", "Injects explicit hand-authored vertices and edges into the topological graph."},
            {"EmptyGraphPass", "EmptyGraphPass", "No-op pass", "Placeholder pass in a GraphGenerator pipeline."},
            // Graph Content
            {"GraphContent", "GraphContent", "BindContentToGraph(Tags, Readers)", "Attaches density carvers, materials, and props to graph vertices and edges."},
            {"DensityContent", "DensityContent", "D_graph = RadialEdgeFalloff(Radius, Density)", "Sweeps a volumetric 3D density field along graph edges and vertices (e.g., cave tunnels, canyons)."},
            {"MaterialContent", "MaterialContent", "Mat_graph = PaintAlongEdges(Radius, Material)", "Paints road cobblestones, river gravel, or tunnel linings along graph edges."},
            {"PropDistributionContent", "PropDistributionContent", "PlacePropsAlongGraph(PropDist)", "Distributes lanterns, bridges, or dungeon props along graph nodes/edges."},
            {"PositionsContent", "PositionsContent", "EmitGraphPositions()", "Exposes graph vertices and edge samples as a PositionsProvider."},
            // Node Selectors
            {"AllNodeSelector", "AllNodeSelector", "Select(v) = true", "Selects every vertex in the graph."},
            {"ConnectionCountNodeSelector", "ConnectionCountNodeSel", "Select(v) = deg(v) in [min, max]", "Selects vertices by edge degree (e.g., dead-ends deg=1, crossroads deg>=3)."},
            {"ContentNodeSelector", "ContentNodeSelector", "Select(v) = ContentPred(v)", "Selects vertices whose attached content tags match a ContentPredicate."},
            {"DensityDelimitedNodeSelector", "DensityDelimitedNodeSel", "Select(v) = D(v.pos) in [min, max]", "Selects vertices where a 3D Density field falls within [min, max]."},
            {"NeighborNodesNodeSelector", "NeighborNodesSelector", "Select(v) = AnyNeighborMatches(Sel)", "Selects vertices adjacent to another selected vertex set."},
            {"NeighborEdgesNodeSelector", "NeighborEdgesSelector", "Select(v) = AnyIncidentEdgeMatches(EdgeSel)", "Selects vertices incident to selected edges."},
            {"RandomNodeSelector", "RandomNodeSelector", "Select(v) = Rand() < probability", "Stochastically selects a subset of graph vertices."},
            {"AndNodeSelector", "AndNodeSelector", "Select(v) = SelA(v) && SelB(v)", "Intersection of two NodeSelectors."},
            {"OrNodeSelector", "OrNodeSelector", "Select(v) = SelA(v) || SelB(v)", "Union of two NodeSelectors."},
            {"NotNodeSelector", "NotNodeSelector", "Select(v) = !Sel(v)", "Inverts a NodeSelector."},
            // Node Actions
            {"ProximityConnectorNodeAction", "ProximityConnector", "Connect(u, v) if ||u - v|| <= maxDist", "Links nearby vertices with edges (Delaunay / Relative Neighborhood / k-NN road & cave networks)."},
            {"Jitter2dNodeAction", "Jitter2dNodeAction", "v.xz += RandXZ(mag)", "Perturbs selected graph vertices horizontally."},
            {"Jitter3dNodeAction", "Jitter3dNodeAction", "v.xyz += RandXYZ(mag)", "Perturbs selected graph vertices in 3D."},
            {"MoveNodeAction", "MoveNodeAction", "v.xyz += Vector(v.pos)", "Displaces selected vertices along a 3D VectorProvider."},
            {"SpawnerNodeAction", "SpawnerNodeAction", "SpawnChildNodes(v, count, radius)", "Spawns satellite vertices and branches around selected parent vertices."},
            {"ContentNodeAction", "ContentNodeAction", "v.content += ContentSupplier()", "Assigns content tags/payloads to selected vertices."},
            {"ContentCopyNodeAction", "ContentCopyNodeAction", "CopyContentToNeighbors(v)", "Propagates vertex content tags to connected neighbors."},
            {"ConnectedNodesNodeAction", "ConnectedNodesAction", "RunActionOnNeighbors(v, Action)", "Executes a child NodeAction on all neighbors of v."},
            {"ConnectedEdgesNodeAction", "ConnectedEdgesAction", "RunEdgeActionOnIncident(v, EdgeAction)", "Executes an EdgeAction on all edges incident to v."},
            {"DeleteNodeAction", "DeleteNodeAction", "RemoveVertex(v)", "Deletes selected vertices and their incident edges from the graph."},
            {"EmptyNodeAction", "EmptyNodeAction", "No-op", "Empty vertex action."},
            {"SelectorNodeAction", "SelectorNodeAction", "If(NodeSelector(v)) -> Action(v)", "Conditionally executes a NodeAction."},
            {"WeightedNodeAction", "WeightedNodeAction", "WeightedChoice(Actions[])", "Randomly chooses one NodeAction by weight."},
            // Edge Selectors
            {"AllEdgeSelector", "AllEdgeSelector", "Select(e) = true", "Selects all edges in the graph."},
            {"LengthEdgeSelector", "LengthEdgeSelector", "Select(e) = ||e|| in [minLen, maxLen]", "Selects edges whose Euclidean length falls within [min, max]."},
            {"AngleEdgeSelector", "AngleEdgeSelector", "Select(e) = SlopeAngle(e) in [min, max]", "Selects edges by 3D inclination or azimuth angle."},
            {"NodesEdgeSelector", "NodesEdgeSelector", "Select(e=(u,v)) = NodeSel(u) && NodeSel(v)", "Selects edges whose endpoints match a NodeSelector."},
            {"RandomEdgeSelector", "RandomEdgeSelector", "Select(e) = Rand() < probability", "Stochastically selects a fraction of edges."},
            {"AndEdgeSelector", "AndEdgeSelector", "Select(e) = A(e) && B(e)", "Logical AND of two EdgeSelectors."},
            {"OrEdgeSelector", "OrEdgeSelector", "Select(e) = A(e) || B(e)", "Logical OR of two EdgeSelectors."},
            {"NotEdgeSelector", "NotEdgeSelector", "Select(e) = !Sel(e)", "Inverts an EdgeSelector."},
            // Edge Actions
            {"SplitterEdgeAction", "SplitterEdgeAction", "Subdivide(e=(u,v) -> u - m - v)", "Subdivides edges by inserting midpoint vertices (used with Jitter for winding rivers and caves)."},
            {"NodesEdgeAction", "NodesEdgeAction", "ApplyNodeActionToEndpoints(e, NodeAction)", "Runs a NodeAction on both endpoints of selected edges."},
            {"ProxyEdgeAction", "ProxyEdgeAction", "DelegateEdgeAction(e)", "Delegates execution to a referenced EdgeAction."},
            {"SelectorEdgeAction", "SelectorEdgeAction", "If(EdgeSelector(e)) -> EdgeAction(e)", "Conditionally executes an EdgeAction."},
            {"DeleteEdgeAction", "DeleteEdgeAction", "RemoveEdge(e)", "Removes selected edges (e.g., pruning overly steep road segments)."},
            {"EmptyEdgeAction", "EmptyEdgeAction", "No-op", "Empty edge action."},
            // Content Predicates & Suppliers
            {"ConstantContentPredicate", "ConstantContentPred", "Match = HasTag(tag)", "Checks whether a graph element carries a specific content tag."},
            {"SetContentPredicate", "SetContentPredicate", "Match = Tag in AllowedSet", "Checks whether a graph element matches a set of content tags."},
            {"AndContentPredicate", "AndContentPredicate", "Match = A && B", "Logical AND of ContentPredicates."},
            {"OrContentPredicate", "OrContentPredicate", "Match = A || B", "Logical OR of ContentPredicates."},
            {"NotContentPredicate", "NotContentPredicate", "Match = !Pred", "Logical NOT of a ContentPredicate."},
            {"ConstantContentSupplier", "ConstantContentSupplier", "Supply() = ContentTag", "Supplies a constant content tag/radius payload to graph elements."},
            {"WeightedContentSupplier", "WeightedContentSupplier", "Supply() = WeightedChoice(Tags[])", "Supplies a weighted random content payload."}
        };
        for (String[] row : graphNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("Proximity")) {
                p.add(ParamDef.flt("maxDistance", "Max Dist", 48.0, 4.0, 256.0, 4.0));
                p.add(ParamDef.integer("maxConnections", "Max Deg", 4, 1, 12, 1));
            } else if (row[0].contains("Splitter")) {
                p.add(ParamDef.integer("subdivisions", "Splits", 2, 1, 8, 1));
            } else if (row[0].contains("Content")) {
                p.add(ParamDef.str("tag", "Tag", "cave_tunnel"));
                p.add(ParamDef.flt("radius", "Radius", 6.0, 1.0, 64.0, 0.5));
            } else if (row[0].contains("Length")) {
                p.add(ParamDef.flt("min", "Min Len", 8.0, 0.0, 256.0, 1.0));
                p.add(ParamDef.flt("max", "Max Len", 64.0, 1.0, 512.0, 1.0));
            }
            reg(new NodeSchema("Graph:" + row[0], row[0], row[1], "Graph", cGrp, List.of(inGrp), List.of(outGrp), p, row[2], row[3],
                new String[]{"graph", "network", "river", "cave", "road", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 12. WORLD STRUCTURE & BIOME ROOT NODES
        // ====================================================================
        int cWld = 0xFF8C5A1E;
        SocketDef outBio = new SocketDef("out", "Biome", SocketDomain.BIOME);

        reg(new NodeSchema("World:BiomeRoot", "BiomeRoot", "Biome Root Output", "World", cWld,
            List.of(
                new SocketDef("terrain", "Terrain", SocketDomain.DENSITY),
                new SocketDef("material", "Material", SocketDomain.MATERIAL),
                new SocketDef("props", "Props", SocketDomain.PROP),
                new SocketDef("graph", "Graph (Opt)", SocketDomain.GRAPH)
            ),
            List.of(),
            List.of(
                ParamDef.integer("seaLevel", "Sea Level", 63, -64, 320, 1),
                ParamDef.block("defaultBlock", "Base Block", "minecraft:stone"),
                ParamDef.block("fluidBlock", "Fluid", "minecraft:water")
            ),
            "Biome = Compile(Terrain, Material, Props)", "Master output node for a Biome definition. Connect your final Density, MaterialProvider, and Props here.",
            new String[]{"biome", "root", "output", "world", "master"}));

        // Macro Dimension Layout & Outside-Bounds Fallback Nodes
        reg(new NodeSchema("World:DimensionOutput", "DimensionOutput", "Dimension Output & Fallback", "World", 0xFF9E5A14,
            List.of(
                new SocketDef("zones", "Zones / Rings", SocketDomain.ANY),
                new SocketDef("landmarks", "Landmarks", SocketDomain.GRAPH),
                new SocketDef("biome_pool", "Biome Pool", SocketDomain.BIOME)
            ),
            List.of(),
            List.of(
                ParamDef.enm("outsideFallback", "Outside", "DEFAULT_BIOMES", "DEFAULT_BIOMES", "VOID", "OCEAN", "VANILLA_DEFAULT"),
                ParamDef.flt("boundsRadius", "Bounds R", 640.0, 0.0, 30000.0, 32.0),
                ParamDef.flt("blendFalloff", "Falloff", 64.0, 8.0, 512.0, 8.0),
                ParamDef.integer("seaLevel", "Sea Level", 63, -64, 320, 1),
                ParamDef.block("defaultBlock", "Base Block", "minecraft:stone"),
                ParamDef.block("fluidBlock", "Fluid", "minecraft:water")
            ),
            "Dimension = MacroLayout(Zones, Biomes, Landmarks, Fallback)",
            "Master Dimension Output node. Controls concentric zones, biome pools, landmarks, bounding radius (0=Infinite), and Outside-Bounds Fallback (DEFAULT_BIOMES, VOID, OCEAN, VANILLA_DEFAULT).",
            new String[]{"dimension", "output", "fallback", "void", "bounds", "end", "preset", "world"}));

        reg(new NodeSchema("World:RadialZone", "RadialZone", "Radial Zone / Ring", "World", 0xFF7A489E,
            List.of(
                new SocketDef("in_zone", "Prev Zone", SocketDomain.ANY)
            ),
            List.of(new SocketDef("out_zone", "Zone Out", SocketDomain.ANY)),
            List.of(
                ParamDef.enm("zoneMode", "Mode", "ISLAND_PLATEAU", "ISLAND_PLATEAU", "VOID_MOAT", "OUTER_ISLANDS", "BIOME_OVERRIDE"),
                ParamDef.flt("innerRadius", "Inner R", 0.0, 0.0, 30000.0, 16.0),
                ParamDef.flt("outerRadius", "Outer R", 200.0, 16.0, 30000.0, 16.0),
                ParamDef.flt("falloffWidth", "Falloff", 36.0, 4.0, 256.0, 4.0),
                ParamDef.flt("targetHeight", "Height Y", 64.0, -32.0, 256.0, 2.0),
                ParamDef.bool("floating", "Floating", false),
                ParamDef.enm("biomeOverride", "Biome", "TemperateForest",
                    "TemperateForest", "SunlitPlains", "AridDunes", "AlpinePeaks", "StonyPeaks", "WindsweptHills",
                    "Ocean", "DeepOcean", "CherryGrove", "Badlands", "Jungle", "Savanna", "Taiga")
            ),
            "Zone(r) = SmoothRing(innerR, outerR, Mode)",
            "Concentric radial zone or ring (e.g. Central End Island 0..190, Void Moat 190..960, Outer Archipelago Islands 960+).",
            new String[]{"radial", "ring", "zone", "end", "island", "void", "moat", "bounds"}));

        reg(new NodeSchema("World:RingLandmarks", "RingLandmarks", "Ring Towers / Pillars", "World", 0xFF8C2D56,
            List.of(new SocketDef("in_zone", "In Zone", SocketDomain.ANY)),
            List.of(new SocketDef("out_zone", "Ring Out", SocketDomain.ANY)),
            List.of(
                ParamDef.flt("ringRadius", "Ring R", 43.0, 8.0, 512.0, 1.0),
                ParamDef.integer("count", "Count", 10, 1, 32, 1),
                ParamDef.integer("pillarRadius", "Pillar R", 3, 1, 6, 1),
                ParamDef.integer("minHeight", "Min TopY", 76, 16, 240, 2),
                ParamDef.integer("maxHeight", "Max TopY", 103, 16, 256, 2),
                ParamDef.block("pillarBlock", "Pillar", "minecraft:obsidian"),
                ParamDef.bool("bedrockCap", "Bedrock Cap", true)
            ),
            "Pillars[i] = Polar(ringRadius, 2*pi*i/count)",
            "Places a circular ring of procedural pillars or towers (e.g. 10 End Obsidian Pillars around (0,0)).",
            new String[]{"ring", "obsidian", "pillar", "tower", "end", "landmark", "circle"}));

        reg(new NodeSchema("World:SpawnIslandAnchor", "SpawnIslandAnchor", "Spawn Island Anchor", "World", 0xFF287A5E,
            List.of(),
            List.of(
                new SocketDef("out_zone", "Island Zone", SocketDomain.ANY),
                new SocketDef("anchor", "Hub Anchor", SocketDomain.GRAPH)
            ),
            List.of(
                ParamDef.flt("islandRadius", "Island R", 220.0, 32.0, 2000.0, 16.0),
                ParamDef.flt("coastFalloff", "Falloff", 48.0, 8.0, 256.0, 8.0),
                ParamDef.flt("moatOuterRadius", "Moat R", 380.0, 32.0, 3000.0, 16.0),
                ParamDef.flt("plateauHeight", "Height Y", 72.0, 32.0, 180.0, 2.0),
                ParamDef.enm("biome", "Biome", "TemperateForest",
                    "TemperateForest", "SunlitPlains", "CherryGrove", "Meadow", "Jungle", "Savanna", "Taiga", "AridDunes"),
                ParamDef.structure("centerStructure", "Structure", "worldgenv2:spawn_shrine")
            ),
            "SpawnIsland = RadialIsland(0, 0, islandRadius) + HubAnchor",
            "Sculpts a starter island at (0,0) with an optional center structure and exports a Hub Anchor for connecting procedural landmarks.",
            new String[]{"spawn", "island", "starter", "anchor", "hub", "landmark"}));

        reg(new NodeSchema("World:RelationalLandmark", "RelationalLandmark", "Structure Landmark (A=>B)", "World", 0xFF8C3B2B,
            List.of(new SocketDef("parent_anchor", "Parent Hub", SocketDomain.GRAPH)),
            List.of(
                new SocketDef("out_zone", "Landmark Out", SocketDomain.GRAPH),
                new SocketDef("anchor", "Sub-Anchor", SocketDomain.GRAPH)
            ),
            List.of(
                ParamDef.str("landmarkId", "Name", "northern_watchtower"),
                ParamDef.structure("prefabId", "Structure", "worldgenv2:watchtower_01"),
                ParamDef.integer("count", "Count", 1, 1, 12, 1),
                ParamDef.flt("minDistance", "Min Dist", 110.0, 24.0, 2000.0, 10.0),
                ParamDef.flt("maxDistance", "Max Dist", 190.0, 32.0, 3000.0, 10.0),
                ParamDef.bool("connectBridge", "Bridge", true),
                ParamDef.flt("bridgeHalfWidth", "Bridge W", 3.5, 1.5, 10.0, 0.5),
                ParamDef.block("bridgeBlock", "Bridge Blk", "minecraft:stone_bricks")
            ),
            "LandmarkB = PlaceStructure(ParentA, [minDist, maxDist], Structure) + SplineBridge",
            "Procedurally places 1 or more instances of a chosen Structure within [minDist, maxDist] of its Parent Hub and connects them with a bridge or road.",
            new String[]{"landmark", "structure", "relational", "connected", "bridge", "road", "watchtower", "shrine"}));

        reg(new NodeSchema("World:SubIslandCluster", "SubIslandCluster", "Satellite Islands Cluster", "World", 0xFF2B688C,
            List.of(new SocketDef("parent_anchor", "Parent Hub", SocketDomain.GRAPH)),
            List.of(new SocketDef("out_zone", "Islands Out", SocketDomain.ANY)),
            List.of(
                ParamDef.integer("count", "Islands", 4, 1, 16, 1),
                ParamDef.flt("minDistance", "Min Dist", 240.0, 48.0, 3000.0, 16.0),
                ParamDef.flt("maxDistance", "Max Dist", 480.0, 64.0, 5000.0, 16.0),
                ParamDef.flt("islandRadius", "Sub-Island R", 64.0, 16.0, 400.0, 8.0),
                ParamDef.enm("biome", "Biome", "CherryGrove",
                    "CherryGrove", "TemperateForest", "SunlitPlains", "AridDunes", "StonyPeaks", "MushroomIsland"),
                ParamDef.structure("structure", "Structure", "worldgenv2:ancient_shrine")
            ),
            "SubIslands = ScatterIslands(Parent, count, [minDist, maxDist])",
            "Procedurally scatters satellite islands around a parent hub with an optional landmark structure on each island.",
            new String[]{"island", "satellite", "cluster", "archipelago", "structure"}));

        reg(new NodeSchema("World:BiomeWeightEntry", "BiomeWeightEntry", "Biome Weight & Pool Entry", "World", cWld,
            List.of(new SocketDef("in_pool", "Prev Biome", SocketDomain.BIOME)),
            List.of(new SocketDef("out_pool", "Pool Out", SocketDomain.BIOME)),
            List.of(
                ParamDef.enm("biome", "Biome", "TemperateForest",
                    "TemperateForest", "SunlitPlains", "AridDunes", "AlpinePeaks", "OldGrowthGrove",
                    "CherryGrove", "Badlands", "Jungle", "Savanna", "Taiga", "SnowyPlains", "Ocean", "DeepOcean", "StonyPeaks"),
                ParamDef.flt("weight", "Weight", 2.0, 0.1, 20.0, 0.2),
                ParamDef.flt("sizeMultiplier", "Size Mod", 1.0, 0.2, 3.0, 0.1),
                ParamDef.flt("transitionDistance", "Blend Dist", 36.0, 8.0, 256.0, 4.0)
            ),
            "Pool += Biome(weight, sizeMod, transitionDist)",
            "Assigns a biome to the active dimension with configurable weight, cell size multiplier, and boundary blend width. Double-click header to edit this biome's terrain graph!",
            new String[]{"biome", "weight", "pool", "dimension", "voronoi", "rarity"}));

        String[][] worldNodes = {
            {"WorldStructure", "WorldStructure", "World = BiCarta(Biomes, TransitionDist)", "Top-level dimension WorldStructure configuring global Voronoi biomes and transition blending."},
            {"BiCarta", "BiCartaVoronoi", "Biome(x,z) = Voronoi5x5(Seed, Climate)", "5x5 cellular Voronoi biome layout with domain warping and smooth neighbor transition weights."},
            {"TileBiome", "TileBiome", "SurfaceBiome(Terrain, Strata, Props)", "Primary surface biome definition with climate coordinates and weight."},
            {"CustomBiome", "CustomBiome", "OverlayBiome(Condition, ParentBiomes)", "Contextual sub-biome (e.g., Riparian Shoreline, Oasis, Canyon Rim) that triggers inside parent biomes."},
            {"UndergroundBiome", "UndergroundBiome", "CaveBiome(DepthRange, Climate)", "3D subterranean biome overriding materials and props inside deep cavern layers."},
            {"ClimateTarget", "ClimateTarget", "Climate = (Temp, Humidity, Cont, Erosion, Weird)", "5D climate target vector slotting a biome into the dimension's climate distribution."},
            {"CaveCarver", "CaveCarver", "Carve(SwissCheese, Spaghetti, Noodle)", "Volumetric 3D cave carver combining Worley chambers and spaghetti tunnels."},
            {"RiverCarver", "RiverCarver", "Carve(DownhillSpline, BankWidth, Depth)", "Hydrological river channel carver with riparian shoreline blending."},
            {"SplineRoadEngine", "SplineRoadEngine", "Road = AStarSpline(Landmarks, MaxGrade)", "Slope-constrained Catmull-Rom road network generator with bridges and tunnels."},
            {"MacroFeatureGraph", "MacroFeatureGraph", "MacroGraph(Landmarks, Rivers, Roads)", "Region-level relational landmark and road/river solver."},
            {"ConditionalSpawnProvider", "ConditionalSpawnProvider", "Spawn = EnvironmentalCheck(Mob, Rules)", "Contextual entity spawn rules driven by elevation, slope, and sky visibility."},
            {"DefaultBiome", "DefaultBiome", "FallbackBiome = BiomeID", "Sets the dimension's default fallback biome."},
            {"DefaultTransitionDistance", "DefaultTransitionDist", "TransitionWidth = Blocks", "Global Voronoi biome boundary blending distance in blocks."},
            {"MaxBiomeEdgeDistance", "MaxBiomeEdgeDistance", "MaxEdgeSearch = Blocks", "Maximum search distance for DistanceToBiomeEdgeDensity."},
            {"SpawnPositions", "SpawnPositions", "PlayerSpawn = Locator(Pattern)", "Defines valid player world spawn search positions."},
            {"FrameworkPositionsDef", "FrameworkPositionsDef", "GlobalPoints[Name] = Positions", "Registers named global point fields in the WorldStructure framework."},
            {"FrameworkDecimalConstants", "FrameworkDecimals", "GlobalConst[Name] = Value", "Registers named global numeric constants in the WorldStructure framework."}
        };
        for (String[] row : worldNodes) {
            List<ParamDef> p = new ArrayList<>();
            if (row[0].contains("Transition") || row[0].contains("Edge")) {
                p.add(ParamDef.flt("distance", "Dist", 32.0, 4.0, 256.0, 4.0));
            } else if (row[0].contains("Climate")) {
                p.add(ParamDef.flt("temperature", "Temp", 0.5, -2.0, 2.0, 0.1));
                p.add(ParamDef.flt("humidity", "Humid", 0.5, -2.0, 2.0, 0.1));
            }
            reg(new NodeSchema("World:" + row[0], row[0], row[1], "World", cWld,
                List.of(new SocketDef("in", "Input", SocketDomain.ANY)), List.of(outBio), p, row[2], row[3],
                new String[]{"world", "biome", row[1].toLowerCase(Locale.ROOT)}));
        }

        // ====================================================================
        // 13. DYNAMIC REGISTRY DISCOVERY (Ensures 100% of registered keys + addons are present)
        // ====================================================================
        discoverRegistryKeys(WorldGenV2Registries.DENSITY_NODE_TYPE.keys(), "Density", cDen, SocketDomain.DENSITY);
        discoverRegistryKeys(WorldGenV2Registries.CURVE_TYPE.keys(), "Curve", cCrv, SocketDomain.CURVE);
        discoverRegistryKeys(WorldGenV2Registries.VECTOR_PROVIDER_TYPE.keys(), "Vector", cVec, SocketDomain.VECTOR);
        discoverRegistryKeys(WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.keys(), "Material", cMat, SocketDomain.MATERIAL);
        discoverRegistryKeys(WorldGenV2Registries.PATTERN_TYPE.keys(), "Pattern", cPat, SocketDomain.PATTERN);
        discoverRegistryKeys(WorldGenV2Registries.BLOCK_MASK_TYPE.keys(), "Mask", cMsk, SocketDomain.MASK);
        discoverRegistryKeys(WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.keys(), "Positions", cPos, SocketDomain.POSITIONS);
        discoverRegistryKeys(WorldGenV2Registries.SCANNER_TYPE.keys(), "Scanner", cScn, SocketDomain.SCANNER);
        discoverRegistryKeys(WorldGenV2Registries.ASSIGNMENTS_TYPE.keys(), "Assign", cAsg, SocketDomain.ASSIGNMENTS);
        discoverRegistryKeys(WorldGenV2Registries.PROP_TYPE.keys(), "Prop", cPrp, SocketDomain.PROP);
        discoverRegistryKeys(WorldGenV2Registries.PROP_RUNTIME_TYPE.keys(), "Prop", cPrp, SocketDomain.PROP);
    }

    private static void discoverRegistryKeys(Set<String> keys, String category, int color, SocketDomain domain) {
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        for (String key : sorted) {
            // Only include canonical PascalCase keys or custom namespaced keys (skip auto-generated lowercase/snake_case duplicates)
            if (key.contains(":") || key.contains("_")) continue;
            if (key.isEmpty() || !Character.isUpperCase(key.charAt(0))) continue;

            String fullId = category + ":" + key;
            if (BY_ID.containsKey(fullId)) continue;

            // Register this node variant/alias so all ~295 registered engine keys are searchable!
            reg(new NodeSchema(
                fullId,
                key,
                key + " (" + category + ")",
                category,
                color,
                List.of(new SocketDef("input", "In", domain)),
                List.of(new SocketDef("out", "Out", domain)),
                List.of(ParamDef.flt("value", "Param", 1.0, -100.0, 100.0, 0.1)),
                category + " Node: " + key,
                "Registered " + category + " AST node in WorldGenV2Registries.",
                new String[]{key.toLowerCase(Locale.ROOT), category.toLowerCase(Locale.ROOT)}
            ));
        }
    }

    private StudioNodeCatalog() {}
}
