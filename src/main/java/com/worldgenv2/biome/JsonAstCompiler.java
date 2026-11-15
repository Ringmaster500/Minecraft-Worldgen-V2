package com.worldgenv2.biome;

import com.worldgenv2.assignments.*;
import com.worldgenv2.curve.*;
import com.worldgenv2.density.SharedColumnDensity;
import com.worldgenv2.density.*;
import com.worldgenv2.graph.*;
import com.worldgenv2.mask.*;
import com.worldgenv2.material.*;
import com.worldgenv2.material.patterns.*;
import com.worldgenv2.positions.*;
import com.worldgenv2.prefab.Prefab;
import com.worldgenv2.prefab.PrefabLoader;
import com.worldgenv2.prefab.PrefabProp;
import com.worldgenv2.props.*;
import com.worldgenv2.props.runtime.*;
import com.worldgenv2.scanner.*;
import com.worldgenv2.vector.*;
import com.worldgenv2.worldstructure.BiCarta;
import com.worldgenv2.worldstructure.CustomBiome;
import com.worldgenv2.worldstructure.TileBiome;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import com.worldgenv2.scanner.Scanner;

/**
 * Recursive JSON Node-Graph AST Compiler and Optimizer for Worldgen V2.
 * <p>
 * Compiles both:
 * <ol>
 *   <li>Full declarative node-graph DAGs ({@code terrain}, {@code materialProvider}, {@code props})
 *       following the Doc 07 specification, and</li>
 *   <li>Shorthand biome presets ({@code terrainSettings} + {@code layers} + {@code CustomBiome} fields).</li>
 * </ol>
 * Performs an automatic AST optimization pass including constant folding, zero-product pruning,
 * double-inversion elimination, and consecutive affine transform ({@link OffsetDensity}/{@link ScaleDensity})
 * collapsing.
 */
public final class JsonAstCompiler {

    private static volatile boolean builtInsRegistered = false;

    /**
     * Context passed down during recursive AST compilation.
     */
    public static final class CompileContext {
        private final long worldSeed;
        private final BiCarta<TileBiome> biCarta;
        private final Function<String, Prefab> prefabResolver;
        private final Function<String, String> assetResolver;
        private final Map<String, Object> assetCache = new java.util.concurrent.ConcurrentHashMap<>();
        private final AtomicInteger saltCounter = new AtomicInteger(100);
        private final Map<String, Object> exports = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, List<Object>> allExports = new java.util.concurrent.ConcurrentHashMap<>();

        /** Every export under a name (different families may reuse one name). */
        public void addExport(String name, Object node) {
            exports.putIfAbsent(name, node);
            allExports.computeIfAbsent(name, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(node);
        }

        /** The export under this name that fits {@code family} (density, pattern, assignments, ...); the first one when none is recognised. */
        public Object resolveAsset(String assetPath, String family) {
            List<Object> all = assetPath == null ? null : allExports.get(assetPath);
            if (all != null && all.size() > 1) {
                for (Object o : all) if (o instanceof Map<?, ?> m && family.equals(familyGuess(m))) return o;
                for (Object o : all) if (o instanceof Map<?, ?> m && familyGuess(m) == null) return o;
            }
            return resolveAsset(assetPath);
        }

        /** Named exports (ExportAs) seen so far; Imported nodes read them before looking for an asset file. */
        public Map<String, Object> exports() { return exports; }

        private final Map<String, Double> decimalConstants = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, PositionsProvider> frameworkPositions = new java.util.concurrent.ConcurrentHashMap<>();

        private volatile java.util.function.DoubleBinaryOperator edgeDistance = (x, z) -> Double.MAX_VALUE;
        private volatile int maxBiomeEdgeDistance = 0;
        private volatile DensityFunction terrain = (x, y, z) -> 0f;

        /** The world's blended terrain density, for the Terrain node (biome material providers read it). */
        public void setTerrain(DensityFunction terrain) { this.terrain = terrain; }
        public DensityFunction terrain() { return terrain; }

        /** Installed once the World Structure exists; DistanceFromBiomeEdge reads it. */
        public void setBiomeEdge(java.util.function.DoubleBinaryOperator distance, int maxDistance) { this.edgeDistance = distance; this.maxBiomeEdgeDistance = maxDistance; }
        public double biomeEdgeDistance(double x, double z) { return edgeDistance.applyAsDouble(x, z); }
        public int maxBiomeEdgeDistance() { return maxBiomeEdgeDistance; }

        /** Named decimal constants of the World Structure's Framework (BaseHeight reads them). */
        public Map<String, Double> decimalConstants() { return decimalConstants; }

        /** Named positions fields of the World Structure's Framework (the Framework positions node reads them). */
        public Map<String, PositionsProvider> frameworkPositions() { return frameworkPositions; }

        public CompileContext(long worldSeed) {
            this(worldSeed, null, null, null);
        }

        public CompileContext(
            long worldSeed,
            BiCarta<TileBiome> biCarta,
            Function<String, Prefab> prefabResolver
        ) {
            this(worldSeed, biCarta, prefabResolver, null);
        }

        public CompileContext(
            long worldSeed,
            BiCarta<TileBiome> biCarta,
            Function<String, Prefab> prefabResolver,
            Function<String, String> assetResolver
        ) {
            this.worldSeed = worldSeed;
            this.biCarta = biCarta;
            this.prefabResolver = prefabResolver;
            this.assetResolver = assetResolver;
        }

        public CompileContext withAssetResolver(Function<String, String> resolver) {
            return new CompileContext(worldSeed, biCarta, prefabResolver, resolver);
        }

        public long worldSeed() {
            return worldSeed;
        }

        /** How many seeds have been drawn from the compile-order counter (a node that draws one is not the same function twice). */
        public int saltDraws() { return saltCounter.get(); }

        public int nextSeedSalt(int explicitSalt) {
            int base = (int) (worldSeed ^ (worldSeed >>> 32));
            return base + (explicitSalt != 0 ? explicitSalt : saltCounter.getAndAdd(37));
        }

        public BiCarta<TileBiome> biCarta() {
            return biCarta;
        }

        private volatile Function<String, List<String>> prefabFolders;

        /** How a Prefab path that names a folder is expanded into the prefab ids inside it (null: no folders). */
        public void setPrefabFolders(Function<String, List<String>> folders) { this.prefabFolders = folders; }

        /** The prefab ids inside a folder path, or empty when the path is not a folder. */
        public List<String> prefabFolder(String path) {
            Function<String, List<String>> f = prefabFolders;
            return f == null ? List.of() : f.apply(path);
        }

        public Prefab resolvePrefab(String prefabId) {
            if (prefabResolver != null) {
                Prefab resolved = prefabResolver.apply(prefabId);
                if (resolved != null) {
                    return resolved;
                }
            }
            return loadClasspathPrefab(prefabId);
        }

        public Object resolveAsset(String assetPath) {
            if (assetPath == null || assetPath.isBlank()) {
                return null;
            }
            Object exported = exports.get(assetPath);
            if (exported != null) return exported;
            return assetCache.computeIfAbsent(assetPath, path -> {
                if (assetResolver != null) {
                    String json = assetResolver.apply(path);
                    if (json != null) {
                        try {
                            return new SimpleJsonParser(json).parseValue();
                        } catch (Exception ignored) {}
                    }
                }
                try (InputStream in = JsonAstCompiler.class.getResourceAsStream(path.startsWith("/") ? path : "/" + path)) {
                    if (in != null) {
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        return new SimpleJsonParser(json).parseValue();
                    }
                } catch (Exception ignored) {}
                return null;
            });
        }
    }

    /**
     * Result of compiling a biome JSON asset.
     */
    public record CompiledBiome<T>(
        int id,
        String name,
        boolean isCustomBiome,
        TileBiome tileBiome,
        CustomBiome customBiome,
        BiomeDefinition definition
    ) {}

    public JsonAstCompiler() {
        registerBuiltInTypes();
    }

    /**
     * Registers all built-in Worldgen V2 density nodes, material providers, pattern matchers,
     * and props into {@link WorldGenV2Registries}.
     */
    public static synchronized void registerBuiltInTypes() {
        if (builtInsRegistered) {
            return;
        }
        builtInsRegistered = true;

        // =====================================================================
        // 1. Built-In DensityFunction Node Types
        // =====================================================================
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ConstantDensity", (json, ctx) ->
            new ConstantDensity(getFloat(json, "value", 0.0f))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Constant", (json, ctx) ->
            new ConstantDensity(getFloat(json, "value", 0.0f))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("VerticalGradientDensity", (json, ctx) ->
            new VerticalGradientDensity(
                getFloat(json, "baseHeight", 64.0f),
                getFloat(json, "scale", getFloat(json, "heightScale", 32.0f))
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("VerticalGradient", (json, ctx) ->
            new VerticalGradientDensity(
                getFloat(json, "baseHeight", 64.0f),
                getFloat(json, "scale", getFloat(json, "heightScale", 32.0f))
            )
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Noise2dDensity", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            FastNoiseLite.NoiseType noiseType = parseNoiseType(getString(json, "noiseType", "OpenSimplex2"));
            float frequency = getFloat(json, "frequency", 0.005f);
            int octaves = getInt(json, "octaves", 3);
            float amplitude = getFloat(json, "amplitude", 1.0f);
            String fractalStr = getString(json, "fractalType", octaves > 1 ? "FBm" : "None");

            FastNoiseLite noise = new FastNoiseLite(seed);
            noise.setNoiseType(noiseType);
            noise.setFrequency(frequency);
            noise.setFractalType(parseFractalType(fractalStr));
            noise.setFractalOctaves(Math.max(1, octaves));
            return new Noise2dDensity(noise, amplitude);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Noise3dDensity", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            FastNoiseLite.NoiseType noiseType = parseNoiseType(getString(json, "noiseType", "OpenSimplex2"));
            float frequency = getFloat(json, "frequency", 0.01f);
            int octaves = getInt(json, "octaves", 3);
            float amplitude = getFloat(json, "amplitude", 1.0f);
            float xzScale = getFloat(json, "xzScale", 1.0f);
            float yScale = getFloat(json, "yScale", 1.0f);
            String fractalStr = getString(json, "fractalType", octaves > 1 ? "FBm" : "None");

            FastNoiseLite noise = new FastNoiseLite(seed);
            noise.setNoiseType(noiseType);
            noise.setFrequency(frequency);
            noise.setFractalType(parseFractalType(fractalStr));
            noise.setFractalOctaves(Math.max(1, octaves));
            return new Noise3dDensity(noise, amplitude, xzScale, yScale);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("YSampledDensity", (json, ctx) -> {
            int yInterval = getInt(json, "yInterval", 8);
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            return new YSampledDensity(input, yInterval);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SumDensity", (json, ctx) ->
            new SumDensity(compileDensityList(json, "inputs", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ProductDensity", (json, ctx) ->
            new ProductDensity(compileDensityList(json, "inputs", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("MinDensity", (json, ctx) ->
            new MinDensity(compileDensityList(json, "inputs", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("MaxDensity", (json, ctx) ->
            new MaxDensity(compileDensityList(json, "inputs", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("GradientWarpDensity", (json, ctx) -> {
            DensityFunction source = compileChildDensity(json, "input", "source", ctx);
            DensityFunction warpSource = compileChildDensity(json, "warpSource", "warp", ctx);
            float warpStrength = getFloat(json, "warpStrength", 12.0f);
            return new GradientWarpDensity(source, warpSource, warpStrength);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("DistanceToBiomeEdgeDensity", (json, ctx) -> {
            float maxDist = getFloat(json, "maxBiomeEdgeDistance", getFloat(json, "maxDistance", 64.0f));
            if (ctx.biCarta() == null) {
                return new ConstantDensity(1.0f);
            }
            return new DistanceToBiomeEdgeDensity(ctx.biCarta(), maxDist);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("AbsDensity", (json, ctx) ->
            new AbsDensity(compileChildDensity(json, "input", "source", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("InvertDensity", (json, ctx) ->
            new InvertDensity(compileChildDensity(json, "input", "source", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ClampDensity", (json, ctx) ->
            new ClampDensity(
                compileChildDensity(json, "input", "source", ctx),
                getFloat(json, "min", -1.0f),
                getFloat(json, "max", 1.0f)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("NormalizerDensity", (json, ctx) ->
            new NormalizerDensity(
                compileChildDensity(json, "input", "source", ctx),
                getFloat(json, "fromMin", -1.0f),
                getFloat(json, "fromMax", 1.0f),
                getFloat(json, "toMin", -1.0f),
                getFloat(json, "toMax", 1.0f)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("OffsetDensity", (json, ctx) ->
            new OffsetDensity(
                compileChildDensity(json, "input", "source", ctx),
                getFloat(json, "offsetX", getFloat(json, "dx", 0.0f)),
                getFloat(json, "offsetY", getFloat(json, "dy", 0.0f)),
                getFloat(json, "offsetZ", getFloat(json, "dz", 0.0f))
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ScaleDensity", (json, ctx) ->
            new ScaleDensity(
                compileChildDensity(json, "input", "source", ctx),
                getFloat(json, "scaleX", getFloat(json, "sx", 1.0f)),
                getFloat(json, "scaleY", getFloat(json, "sy", 1.0f)),
                getFloat(json, "scaleZ", getFloat(json, "sz", 1.0f))
            )
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CurveMapperDensity", (json, ctx) ->
            new CurveMapperDensity(
                compileChildDensity(json, "input", "source", ctx),
                compileChildCurve(json, "curve", "Curve", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CurveMapper", (json, ctx) ->
            new CurveMapperDensity(
                compileChildDensity(json, "input", "source", ctx),
                compileChildCurve(json, "curve", "Curve", ctx)
            )
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("VectorWarpDensity", (json, ctx) ->
            new VectorWarpDensity(
                compileChildDensity(json, "input", "source", ctx),
                compileChildVectorProvider(json, "vectorProvider", "vector", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("VectorWarp", (json, ctx) ->
            new VectorWarpDensity(
                compileChildDensity(json, "input", "source", ctx),
                compileChildVectorProvider(json, "vectorProvider", "vector", ctx)
            )
        );

        // --- Cellular Voronoi Nodes ---
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CellNoise2dDensity", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            float freq = getFloat(json, "frequency", getFloat(json, "scale", 0.01f));
            float jitter = getFloat(json, "jitter", 0.3f);
            float amp = getFloat(json, "amplitude", 1.0f);
            String returnTypeStr = getString(json, "cellType", getString(json, "returnType", "Distance"));

            FastNoiseLite noise = new FastNoiseLite(seed);
            noise.setNoiseType(FastNoiseLite.NoiseType.Cellular);
            noise.setFrequency(freq);
            noise.setCellularJitter(jitter);
            noise.setCellularReturnType(parseCellularReturnType(returnTypeStr));
            return new CellNoise2dDensity(noise, amp);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CellNoise2D", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("CellNoise2dDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CellNoise3dDensity", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            float freq = getFloat(json, "frequency", getFloat(json, "scale", 0.01f));
            float jitter = getFloat(json, "jitter", 0.3f);
            float amp = getFloat(json, "amplitude", 1.0f);
            String returnTypeStr = getString(json, "cellType", getString(json, "returnType", "Distance"));

            FastNoiseLite noise = new FastNoiseLite(seed);
            noise.setNoiseType(FastNoiseLite.NoiseType.Cellular);
            noise.setFrequency(freq);
            noise.setCellularJitter(jitter);
            noise.setCellularReturnType(parseCellularReturnType(returnTypeStr));
            return new CellNoise3dDensity(noise, amp);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CellNoise3D", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("CellNoise3dDensity").deserialize(json, ctx)
        );

        // --- Volumetric 3D Geometric SDF Primitives ---
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("EllipsoidDensity", (json, ctx) -> {
            float rx = getFloat(json, "radiusX", getFloat(json, "rx", getFloat(json, "radius", 50.0f)));
            float ry = getFloat(json, "radiusY", getFloat(json, "ry", getFloat(json, "radius", 50.0f)));
            float rz = getFloat(json, "radiusZ", getFloat(json, "rz", getFloat(json, "radius", 50.0f)));
            Curve curve = json.containsKey("curve") || json.containsKey("Curve")
                ? compileChildCurve(json, "curve", "Curve", ctx)
                : null;
            return new EllipsoidDensity(rx, ry, rz, curve);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Ellipsoid", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("EllipsoidDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CuboidDensity", (json, ctx) -> {
            float hx = getFloat(json, "halfX", getFloat(json, "hx", getFloat(json, "size", 50.0f)));
            float hy = getFloat(json, "halfY", getFloat(json, "hy", getFloat(json, "size", 50.0f)));
            float hz = getFloat(json, "halfZ", getFloat(json, "hz", getFloat(json, "size", 50.0f)));
            Curve curve = json.containsKey("curve") || json.containsKey("Curve")
                ? compileChildCurve(json, "curve", "Curve", ctx)
                : null;
            return new CuboidDensity(hx, hy, hz, curve);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Cuboid", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("CuboidDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Cube", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("CuboidDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CylinderDensity", (json, ctx) -> {
            float rad = getFloat(json, "radius", 50.0f);
            String axisStr = getString(json, "axis", "Y");
            CylinderDensity.Axis axis = switch (axisStr.toUpperCase(Locale.ROOT)) {
                case "X" -> CylinderDensity.Axis.X;
                case "Z" -> CylinderDensity.Axis.Z;
                default -> CylinderDensity.Axis.Y;
            };
            Curve curve = json.containsKey("curve") || json.containsKey("Curve")
                ? compileChildCurve(json, "curve", "Curve", ctx)
                : null;
            return new CylinderDensity(rad, axis, curve);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Cylinder", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("CylinderDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("PlaneDensity", (json, ctx) -> {
            Vec3d norm = json.containsKey("normal") || json.containsKey("planeNormal")
                ? compileVectorProvider(json.getOrDefault("normal", json.get("planeNormal")), ctx).getVector(0, 0, 0)
                : Vec3d.UNIT_Y;
            float offset = getFloat(json, "offset", 0.0f);
            Curve curve = json.containsKey("curve") || json.containsKey("Curve")
                ? compileChildCurve(json, "curve", "Curve", ctx)
                : null;
            return new PlaneDensity(norm, offset, curve);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Plane", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("PlaneDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ShellDensity", (json, ctx) -> {
            Vec3d axis = json.containsKey("axis") || json.containsKey("Axis")
                ? compileVectorProvider(json.getOrDefault("axis", json.get("Axis")), ctx).getVector(0, 0, 0)
                : Vec3d.UNIT_Y;
            boolean mirror = getBoolean(json, "mirror", getBoolean(json, "Mirror", false));
            Curve angleCurve = json.containsKey("angleCurve") || json.containsKey("AngleCurve")
                ? compileChildCurve(json, "angleCurve", "AngleCurve", ctx)
                : null;
            Curve distCurve = json.containsKey("distanceCurve") || json.containsKey("DistanceCurve")
                ? compileChildCurve(json, "distanceCurve", "DistanceCurve", ctx)
                : null;
            return new ShellDensity(axis, mirror, angleCurve, distCurve);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Shell", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("ShellDensity").deserialize(json, ctx)
        );

        // --- Logic, Comparison & Conditional Nodes ---
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ComparatorDensity", (json, ctx) ->
            new ComparatorDensity(
                compileChildDensity(json, "inputA", "a", ctx),
                compileChildDensity(json, "inputB", "b", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Comparator", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("ComparatorDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("EqualDensity", (json, ctx) ->
            new EqualDensity(
                compileChildDensity(json, "inputA", "a", ctx),
                compileChildDensity(json, "inputB", "b", ctx),
                getFloat(json, "epsilon", 0.0f)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Equal", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("EqualDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("GreaterThanDensity", (json, ctx) ->
            new GreaterThanDensity(
                compileChildDensity(json, "inputA", "a", ctx),
                compileChildDensity(json, "inputB", "b", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("GreaterThan", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("GreaterThanDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("GreaterOrEqualDensity", (json, ctx) ->
            new GreaterOrEqualDensity(
                compileChildDensity(json, "inputA", "a", ctx),
                compileChildDensity(json, "inputB", "b", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("GreaterOrEqual", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("GreaterOrEqualDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("LessThanDensity", (json, ctx) ->
            new LessThanDensity(
                compileChildDensity(json, "inputA", "a", ctx),
                compileChildDensity(json, "inputB", "b", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("LessThan", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("LessThanDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("LessOrEqualDensity", (json, ctx) ->
            new LessOrEqualDensity(
                compileChildDensity(json, "inputA", "a", ctx),
                compileChildDensity(json, "inputB", "b", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("LessOrEqual", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("LessOrEqualDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("AndDensity", (json, ctx) ->
            new AndDensity(compileDensityList(json, "inputs", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("And", (json, ctx) ->
            new AndDensity(compileDensityList(json, "inputs", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("OrDensity", (json, ctx) ->
            new OrDensity(compileDensityList(json, "inputs", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Or", (json, ctx) ->
            new OrDensity(compileDensityList(json, "inputs", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("NotDensity", (json, ctx) ->
            new NotDensity(compileChildDensity(json, "input", "source", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Not", (json, ctx) ->
            new NotDensity(compileChildDensity(json, "input", "source", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("NorDensity", (json, ctx) ->
            new NorDensity(compileDensityList(json, "inputs", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Nor", (json, ctx) ->
            new NorDensity(compileDensityList(json, "inputs", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("XorDensity", (json, ctx) ->
            new XorDensity(compileDensityList(json, "inputs", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Xor", (json, ctx) ->
            new XorDensity(compileDensityList(json, "inputs", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SelectorDensity", (json, ctx) ->
            new SelectorDensity(
                compileChildDensity(json, "condition", "Condition", ctx),
                compileChildDensity(json, "then", "Then", ctx),
                compileChildDensity(json, "else", "Else", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Selector", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("SelectorDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SwitchDensity", (json, ctx) ->
            new SwitchDensity(
                compileChildDensity(json, "index", "selector", ctx),
                compileDensityList(json, "branches", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Switch", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("SwitchDensity").deserialize(json, ctx)
        );

        // --- Spatial Coordinates, Override & Cache Nodes ---
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("XValueDensity", (json, ctx) -> XValueDensity.INSTANCE);
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("XValue", (json, ctx) -> XValueDensity.INSTANCE);

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("YValueDensity", (json, ctx) -> YValueDensity.INSTANCE);
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("YValue", (json, ctx) -> YValueDensity.INSTANCE);

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ZValueDensity", (json, ctx) -> ZValueDensity.INSTANCE);
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ZValue", (json, ctx) -> ZValueDensity.INSTANCE);

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("XOverrideDensity", (json, ctx) ->
            new XOverrideDensity(
                compileChildDensity(json, "source", "input", ctx),
                compileChildDensity(json, "override", "x", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("XOverride", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("XOverrideDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("YOverrideDensity", (json, ctx) ->
            new YOverrideDensity(
                compileChildDensity(json, "source", "input", ctx),
                compileChildDensity(json, "override", "y", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("YOverride", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("YOverrideDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ZOverrideDensity", (json, ctx) ->
            new ZOverrideDensity(
                compileChildDensity(json, "source", "input", ctx),
                compileChildDensity(json, "override", "z", ctx)
            )
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("ZOverride", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("ZOverrideDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("CacheDensity", (json, ctx) ->
            new CacheDensity(compileChildDensity(json, "input", "source", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Cache", (json, ctx) ->
            new CacheDensity(compileChildDensity(json, "input", "source", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SingleInstanceDensity", (json, ctx) ->
            new SingleInstanceDensity(compileChildDensity(json, "input", "source", ctx))
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SingleInstance", (json, ctx) ->
            new SingleInstanceDensity(compileChildDensity(json, "input", "source", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("GraphDensity", (json, ctx) -> {
            GraphGenerator gen = compileGraphGenerator(json.get("graphGenerator"), ctx, s -> s);
            String layer = getString(json, "contentLayer", getString(json, "ContentLayer", ""));
            float bg = getFloat(json, "backgroundValue", getFloat(json, "BackgroundValue", 0.0f));
            return new GraphDensity(gen, layer, bg, ctx.worldSeed()).withTransition(getDouble(json, "transitionSlope", 2.5), getDouble(json, "transitionOffset", 0.0), getDouble(json, "transitionSmooth", 0.2));
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Graph", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("GraphDensity").deserialize(json, ctx)
        );

        // New Official Density Node Types & Aliases
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SimplexNoise2D", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            float lacunarity = getFloat(json, "lacunarity", getFloat(json, "Lacunarity", 2.0f));
            float persistence = getFloat(json, "persistence", getFloat(json, "Persistence", 0.5f));
            float scale = getFloat(json, "scale", getFloat(json, "Scale", 50.0f));
            int octaves = getInt(json, "octaves", getInt(json, "Octaves", 4));

            FastNoiseLite noise = new FastNoiseLite(seed);
            noise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
            noise.setFrequency(1.0f / Math.max(0.001f, scale));
            noise.setFractalType(octaves > 1 ? FastNoiseLite.FractalType.FBm : FastNoiseLite.FractalType.None);
            noise.setFractalOctaves(Math.max(1, octaves));
            noise.setFractalLacunarity(lacunarity);
            noise.setFractalGain(persistence);
            return new Noise2dDensity(noise, 1.0f);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SimplexNoise3D", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            float lacunarity = getFloat(json, "lacunarity", getFloat(json, "Lacunarity", 2.0f));
            float persistence = getFloat(json, "persistence", getFloat(json, "Persistence", 0.5f));
            float scaleXZ = getFloat(json, "scaleXZ", getFloat(json, "ScaleXZ", getFloat(json, "scale", 50.0f)));
            float scaleY = getFloat(json, "scaleY", getFloat(json, "ScaleY", getFloat(json, "scale", 50.0f)));
            int octaves = getInt(json, "octaves", getInt(json, "Octaves", 4));

            FastNoiseLite noise = new FastNoiseLite(seed);
            noise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
            noise.setFrequency(1.0f / Math.max(0.001f, scaleXZ));
            noise.setFractalType(octaves > 1 ? FastNoiseLite.FractalType.FBm : FastNoiseLite.FractalType.None);
            noise.setFractalOctaves(Math.max(1, octaves));
            noise.setFractalLacunarity(lacunarity);
            noise.setFractalGain(persistence);
            float yScale = scaleXZ / Math.max(0.001f, scaleY);
            return new Noise3dDensity(noise, 1.0f, 1.0f, yScale);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("WhiteNoise", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            return new WhiteNoiseDensity(seed);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Mix", (json, ctx) -> {
            DensityFunction a = compileChildDensity(json, "a", "A", ctx);
            DensityFunction b = compileChildDensity(json, "b", "B", ctx);
            DensityFunction factor = compileChildDensity(json, "factor", "Factor", ctx);
            return new MixDensity(a, b, factor);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Lerp", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("Mix").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("MultiMix", (json, ctx) -> {
            List<DensityFunction> inputs = compileDensityList(json, "inputs", ctx);
            DensityFunction selector = compileChildDensity(json, "selector", "Factor", ctx);
            return new MultiMixDensity(inputs, selector);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Sqrt", (json, ctx) ->
            new SqrtDensity(compileChildDensity(json, "input", "source", ctx))
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Pow", (json, ctx) -> {
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            float exp = getFloat(json, "exponent", getFloat(json, "power", 2.0f));
            return new PowDensity(input, exp);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Trig", (json, ctx) -> {
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            String fnStr = getString(json, "function", getString(json, "trigType", "SIN")).toUpperCase(Locale.ROOT);
            TrigDensity.Function fn = switch (fnStr) {
                case "COS" -> TrigDensity.Function.COS;
                case "TAN" -> TrigDensity.Function.TAN;
                default -> TrigDensity.Function.SIN;
            };
            float freq = getFloat(json, "frequency", 1.0f);
            float amp = getFloat(json, "amplitude", 1.0f);
            float phase = getFloat(json, "phase", 0.0f);
            return new TrigDensity(input, fn, freq, amp, phase);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Rotator", (json, ctx) -> {
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            String axisStr = getString(json, "axis", "Y").toUpperCase(Locale.ROOT);
            RotatorDensity.Axis axis = switch (axisStr) {
                case "X" -> RotatorDensity.Axis.X;
                case "Z" -> RotatorDensity.Axis.Z;
                default -> RotatorDensity.Axis.Y;
            };
            float angle = getFloat(json, "angle", getFloat(json, "degrees", 0.0f));
            return new RotatorDensity(input, axis, angle);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Slider", (json, ctx) -> {
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            float dx = getFloat(json, "offsetX", getFloat(json, "x", 0.0f));
            float dy = getFloat(json, "offsetY", getFloat(json, "y", 0.0f));
            float dz = getFloat(json, "offsetZ", getFloat(json, "z", 0.0f));
            return new SliderDensity(input, dx, dy, dz);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("FastGradientWarp", (json, ctx) -> {
            DensityFunction source = compileChildDensity(json, "source", "input", ctx);
            DensityFunction warpField = compileChildDensity(json, "warpField", "warpSource", ctx);
            float factor = getFloat(json, "factor", getFloat(json, "warpStrength", 10.0f));
            return new FastGradientWarpDensity(source, warpField, factor);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("PositionsCellNoise", (json, ctx) -> {
            PositionsProvider prov = compilePositionsProvider(json.get("positions"), ctx);
            float radius = getFloat(json, "searchRadius", 32.0f);
            float maxDist = getFloat(json, "maxDistance", radius);
            return new PositionsCellNoiseDensity(prov, radius, maxDist);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("PositionsPinch", (json, ctx) -> {
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            float strength = getFloat(json, "strength", 1.0f);
            return new ScaleDensity(input, strength, strength, strength);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("PositionsTwist", (json, ctx) -> {
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            float angle = getFloat(json, "angle", 45.0f);
            return new RotatorDensity(input, RotatorDensity.Axis.Y, angle);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Anchor", (json, ctx) -> {
            DensityFunction input = compileChildDensity(json, "input", "source", ctx);
            float x = getFloat(json, "x", 0.0f);
            float y = getFloat(json, "y", 0.0f);
            float z = getFloat(json, "z", 0.0f);
            return new SliderDensity(input, x, y, z);
        });

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("SwitchState", (json, ctx) -> {
            String stateKey = getString(json, "stateKey", "default");
            Map<String, DensityFunction> branches = new HashMap<>();
            Object rawBranches = json.get("branches");
            if (rawBranches instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getValue() instanceof Map<?, ?> bm) {
                        branches.put(e.getKey().toString(), compileDensity((Map<String, Object>) bm, ctx));
                    }
                }
            }
            DensityFunction defBranch = json.containsKey("default")
                ? compileDensity((Map<String, Object>) json.get("default"), ctx)
                : new ConstantDensity(0.0f);
            return new SwitchStateDensity(() -> stateKey, branches, defBranch);
        });

        // Short aliases for all density nodes
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Abs", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("AbsDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Sum", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("SumDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Product", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("ProductDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Min", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("MinDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Max", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("MaxDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Invert", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("InvertDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Clamp", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("ClampDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("YSampled", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("YSampledDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("GradientWarp", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("GradientWarpDensity").deserialize(json, ctx)
        );
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("DistanceToBiomeEdge", (json, ctx) ->
            WorldGenV2Registries.DENSITY_NODE_TYPE.get("DistanceToBiomeEdgeDensity").deserialize(json, ctx)
        );

        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Imported", (json, ctx) -> {
            String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
            Object loaded = ctx.resolveAsset(asset, "density");
            if (loaded instanceof Map<?, ?> map) {
                return compileDensity((Map<String, Object>) map, ctx);
            }
            return new ConstantDensity(0.0f);
        });
        WorldGenV2Registries.DENSITY_NODE_TYPE.register("Exported", (json, ctx) ->
            compileChildDensity(json, "node", "child", ctx)
        );

        // =====================================================================
        // 2. Built-In Curve Node Types
        // =====================================================================
        WorldGenV2Registries.CURVE_TYPE.register("ConstantCurve", (json, ctx) ->
            new ConstantCurve(getDouble(json, "value", getDouble(json, "Value", 0.0)))
        );
        WorldGenV2Registries.CURVE_TYPE.register("Constant", (json, ctx) ->
            new ConstantCurve(getDouble(json, "value", getDouble(json, "Value", 0.0)))
        );

        WorldGenV2Registries.CURVE_TYPE.register("ManualCurve", (json, ctx) -> {
            List<ManualCurve.Point> pts = new ArrayList<>();
            Object rawPts = json.getOrDefault("points", json.get("Points"));
            if (rawPts instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof List<?> l && l.size() >= 2) {
                        double px = ((Number) l.get(0)).doubleValue();
                        double py = ((Number) l.get(1)).doubleValue();
                        pts.add(new ManualCurve.Point(px, py));
                    } else if (item instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) m;
                        // Hytale's own files write a point as { In, Out }; the older docs write { X, Y }
                        double px = map.containsKey("in") ? getDouble(map, "in", 0.0) : getDouble(map, "x", getDouble(map, "X", 0.0));
                        double py = map.containsKey("out") ? getDouble(map, "out", 0.0) : getDouble(map, "y", getDouble(map, "Y", 0.0));
                        pts.add(new ManualCurve.Point(px, py));
                    }
                }
            }
            String modeStr = getString(json, "mode", getString(json, "interpolation", "linear"));
            ManualCurve.InterpolationMode mode = switch (modeStr.toLowerCase(Locale.ROOT)) {
                case "smooth", "hermite", "catmullrom" -> ManualCurve.InterpolationMode.SMOOTH;
                case "step" -> ManualCurve.InterpolationMode.STEP;
                default -> ManualCurve.InterpolationMode.LINEAR;
            };
            return new ManualCurve(pts, mode);
        });
        WorldGenV2Registries.CURVE_TYPE.register("Manual", (json, ctx) ->
            WorldGenV2Registries.CURVE_TYPE.get("ManualCurve").deserialize(json, ctx)
        );

        WorldGenV2Registries.CURVE_TYPE.register("DistanceExponentialCurve", (json, ctx) ->
            new DistanceExponentialCurve(
                getDouble(json, "exponent", getDouble(json, "Exponent", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 1.0))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("DistanceExponential", (json, ctx) ->
            new DistanceExponentialCurve(
                getDouble(json, "exponent", getDouble(json, "Exponent", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 1.0))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("DistanceSCurve", (json, ctx) ->
            new DistanceSCurve(
                getDouble(json, "exponentA", getDouble(json, "ExponentA", 1.0)),
                getDouble(json, "exponentB", getDouble(json, "ExponentB", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 1.0)),
                getDouble(json, "transition", getDouble(json, "Transition", 1.0)),
                getDouble(json, "transitionSmooth", getDouble(json, "TransitionSmooth", 1.0))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("DistanceS", (json, ctx) ->
            new DistanceSCurve(
                getDouble(json, "exponentA", getDouble(json, "ExponentA", 1.0)),
                getDouble(json, "exponentB", getDouble(json, "ExponentB", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 1.0)),
                getDouble(json, "transition", getDouble(json, "Transition", 1.0)),
                getDouble(json, "transitionSmooth", getDouble(json, "TransitionSmooth", 1.0))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("CeilingCurve", (json, ctx) ->
            new CeilingCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "ceiling", getDouble(json, "Ceiling", 1.0))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("Ceiling", (json, ctx) ->
            new CeilingCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "ceiling", getDouble(json, "Ceiling", 1.0))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("FloorCurve", (json, ctx) ->
            new FloorCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "floor", getDouble(json, "Floor", 0.0))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("Floor", (json, ctx) ->
            new FloorCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "floor", getDouble(json, "Floor", 0.0))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("SmoothCeilingCurve", (json, ctx) ->
            new SmoothCeilingCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "ceiling", getDouble(json, "Ceiling", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("SmoothCeiling", (json, ctx) ->
            new SmoothCeilingCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "ceiling", getDouble(json, "Ceiling", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("SmoothFloorCurve", (json, ctx) ->
            new SmoothFloorCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "floor", getDouble(json, "Floor", 0.0)),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("SmoothFloor", (json, ctx) ->
            new SmoothFloorCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "floor", getDouble(json, "Floor", 0.0)),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("ClampCurve", (json, ctx) ->
            new ClampCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "wallA", getDouble(json, "WallA", 0.0)),
                getDouble(json, "wallB", getDouble(json, "WallB", 1.0))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("Clamp", (json, ctx) ->
            new ClampCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "wallA", getDouble(json, "WallA", 0.0)),
                getDouble(json, "wallB", getDouble(json, "WallB", 1.0))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("SmoothClampCurve", (json, ctx) ->
            new SmoothClampCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "wallA", getDouble(json, "WallA", 0.0)),
                getDouble(json, "wallB", getDouble(json, "WallB", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("SmoothClamp", (json, ctx) ->
            new SmoothClampCurve(
                compileChildCurve(json, "curve", "Curve", ctx),
                getDouble(json, "wallA", getDouble(json, "WallA", 0.0)),
                getDouble(json, "wallB", getDouble(json, "WallB", 1.0)),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("MinCurve", (json, ctx) ->
            new MinCurve(compileCurveList(json, "curves", ctx))
        );
        WorldGenV2Registries.CURVE_TYPE.register("Min", (json, ctx) ->
            new MinCurve(compileCurveList(json, "curves", ctx))
        );

        WorldGenV2Registries.CURVE_TYPE.register("MaxCurve", (json, ctx) ->
            new MaxCurve(compileCurveList(json, "curves", ctx))
        );
        WorldGenV2Registries.CURVE_TYPE.register("Max", (json, ctx) ->
            new MaxCurve(compileCurveList(json, "curves", ctx))
        );

        WorldGenV2Registries.CURVE_TYPE.register("SmoothMinCurve", (json, ctx) ->
            new SmoothMinCurve(
                compileChildCurve(json, "curveA", "CurveA", ctx),
                compileChildCurve(json, "curveB", "CurveB", ctx),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("SmoothMin", (json, ctx) ->
            new SmoothMinCurve(
                compileChildCurve(json, "curveA", "CurveA", ctx),
                compileChildCurve(json, "curveB", "CurveB", ctx),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("SmoothMaxCurve", (json, ctx) ->
            new SmoothMaxCurve(
                compileChildCurve(json, "curveA", "CurveA", ctx),
                compileChildCurve(json, "curveB", "CurveB", ctx),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );
        WorldGenV2Registries.CURVE_TYPE.register("SmoothMax", (json, ctx) ->
            new SmoothMaxCurve(
                compileChildCurve(json, "curveA", "CurveA", ctx),
                compileChildCurve(json, "curveB", "CurveB", ctx),
                getDouble(json, "range", getDouble(json, "Range", 0.25))
            )
        );

        WorldGenV2Registries.CURVE_TYPE.register("InverterCurve", (json, ctx) ->
            new InverterCurve(compileChildCurve(json, "curve", "Curve", ctx))
        );
        WorldGenV2Registries.CURVE_TYPE.register("Inverter", (json, ctx) ->
            new InverterCurve(compileChildCurve(json, "curve", "Curve", ctx))
        );

        WorldGenV2Registries.CURVE_TYPE.register("NotCurve", (json, ctx) ->
            new NotCurve(compileChildCurve(json, "curve", "Curve", ctx))
        );
        WorldGenV2Registries.CURVE_TYPE.register("Not", (json, ctx) ->
            new NotCurve(compileChildCurve(json, "curve", "Curve", ctx))
        );

        WorldGenV2Registries.CURVE_TYPE.register("MultiplierCurve", (json, ctx) ->
            new MultiplierCurve(compileCurveList(json, "curves", ctx))
        );
        WorldGenV2Registries.CURVE_TYPE.register("Multiplier", (json, ctx) ->
            new MultiplierCurve(compileCurveList(json, "curves", ctx))
        );

        WorldGenV2Registries.CURVE_TYPE.register("SumCurve", (json, ctx) ->
            new SumCurve(compileCurveList(json, "curves", ctx))
        );
        WorldGenV2Registries.CURVE_TYPE.register("Sum", (json, ctx) ->
            new SumCurve(compileCurveList(json, "curves", ctx))
        );

        WorldGenV2Registries.CURVE_TYPE.register("Linear", (json, ctx) ->
            new ManualCurve(List.of(new ManualCurve.Point(0.0, 0.0), new ManualCurve.Point(1.0, 1.0)), ManualCurve.InterpolationMode.LINEAR)
        );
        WorldGenV2Registries.CURVE_TYPE.register("Normalizer", (json, ctx) ->
            new ClampCurve(compileChildCurve(json, "curve", "Curve", ctx), 0.0, 1.0)
        );
        WorldGenV2Registries.CURVE_TYPE.register("Cache", (json, ctx) ->
            compileChildCurve(json, "curve", "Curve", ctx)
        );
        WorldGenV2Registries.CURVE_TYPE.register("Imported", (json, ctx) -> {
            String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
            Object loaded = ctx.resolveAsset(asset, "curve");
            if (loaded != null) {
                return compileCurve(loaded, ctx);
            }
            return new ConstantCurve(0.0);
        });
        WorldGenV2Registries.CURVE_TYPE.register("Exported", (json, ctx) ->
            compileChildCurve(json, "node", "child", ctx)
        );

        // =====================================================================
        // 3. Built-In VectorProvider Node Types
        // =====================================================================
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("ConstantVectorProvider", (json, ctx) -> {
            Object rawVec = json.getOrDefault("vector", json.get("Vector"));
            if (rawVec instanceof List<?> l && l.size() >= 3) {
                return new ConstantVectorProvider(
                    ((Number) l.get(0)).doubleValue(),
                    ((Number) l.get(1)).doubleValue(),
                    ((Number) l.get(2)).doubleValue()
                );
            } else if (rawVec instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) m;
                return new ConstantVectorProvider(
                    getDouble(map, "x", getDouble(map, "X", 0.0)),
                    getDouble(map, "y", getDouble(map, "Y", 0.0)),
                    getDouble(map, "z", getDouble(map, "Z", 0.0))
                );
            }
            return new ConstantVectorProvider(
                getDouble(json, "x", getDouble(json, "X", 0.0)),
                getDouble(json, "y", getDouble(json, "Y", 0.0)),
                getDouble(json, "z", getDouble(json, "Z", 0.0))
            );
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Constant", (json, ctx) ->
            WorldGenV2Registries.VECTOR_PROVIDER_TYPE.get("ConstantVectorProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SetXVectorProvider", (json, ctx) ->
            new SetXVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "value", "Value", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SetX", (json, ctx) ->
            new SetXVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "value", "Value", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SetYVectorProvider", (json, ctx) ->
            new SetYVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "value", "Value", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SetY", (json, ctx) ->
            new SetYVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "value", "Value", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SetZVectorProvider", (json, ctx) ->
            new SetZVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "value", "Value", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SetZ", (json, ctx) ->
            new SetZVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "value", "Value", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("AdderVectorProvider", (json, ctx) ->
            new AdderVectorProvider(compileVectorProviderList(json, "vectors", ctx))
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Adder", (json, ctx) ->
            new AdderVectorProvider(compileVectorProviderList(json, "vectors", ctx))
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SubtracterVectorProvider", (json, ctx) ->
            new SubtracterVectorProvider(
                compileChildVectorProvider(json, "minuend", "vectorA", ctx),
                compileChildVectorProvider(json, "subtrahend", "vectorB", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Subtracter", (json, ctx) ->
            new SubtracterVectorProvider(
                compileChildVectorProvider(json, "minuend", "vectorA", ctx),
                compileChildVectorProvider(json, "subtrahend", "vectorB", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("MultiplierVectorProvider", (json, ctx) ->
            new MultiplierVectorProvider(compileVectorProviderList(json, "vectors", ctx))
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Multiplier", (json, ctx) ->
            new MultiplierVectorProvider(compileVectorProviderList(json, "vectors", ctx))
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("ScalarMultiplierVectorProvider", (json, ctx) ->
            new ScalarMultiplierVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "scalar", "Scalar", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("ScalarMultiplier", (json, ctx) ->
            new ScalarMultiplierVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildDensity(json, "scalar", "Scalar", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("NormalizerVectorProvider", (json, ctx) -> {
            DensityFunction mag = json.containsKey("magnitude") || json.containsKey("Magnitude")
                ? compileChildDensity(json, "magnitude", "Magnitude", ctx)
                : new ConstantDensity(1.0f);
            return new NormalizerVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                mag
            );
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Normalizer", (json, ctx) ->
            WorldGenV2Registries.VECTOR_PROVIDER_TYPE.get("NormalizerVectorProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("CrossVectorProvider", (json, ctx) ->
            new CrossVectorProvider(
                compileChildVectorProvider(json, "vectorA", "VectorA", ctx),
                compileChildVectorProvider(json, "vectorB", "VectorB", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Cross", (json, ctx) ->
            new CrossVectorProvider(
                compileChildVectorProvider(json, "vectorA", "VectorA", ctx),
                compileChildVectorProvider(json, "vectorB", "VectorB", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("PlaneProjectorVectorProvider", (json, ctx) ->
            new PlaneProjectorVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildVectorProvider(json, "planeA", "PlaneA", ctx),
                compileChildVectorProvider(json, "planeB", "PlaneB", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("PlaneProjector", (json, ctx) ->
            new PlaneProjectorVectorProvider(
                compileChildVectorProvider(json, "vector", "Vector", ctx),
                compileChildVectorProvider(json, "planeA", "PlaneA", ctx),
                compileChildVectorProvider(json, "planeB", "PlaneB", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("VectorProjectorVectorProvider", (json, ctx) ->
            new VectorProjectorVectorProvider(
                compileChildVectorProvider(json, "source", "Source", ctx),
                compileChildVectorProvider(json, "target", "Target", ctx)
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("VectorProjector", (json, ctx) ->
            new VectorProjectorVectorProvider(
                compileChildVectorProvider(json, "source", "Source", ctx),
                compileChildVectorProvider(json, "target", "Target", ctx)
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("DensityGradientVectorProvider", (json, ctx) ->
            new DensityGradientVectorProvider(
                compileChildDensity(json, "density", "Density", ctx),
                getDouble(json, "sampleDistance", getDouble(json, "SampleDistance", 1.0))
            )
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("DensityGradient", (json, ctx) ->
            new DensityGradientVectorProvider(
                compileChildDensity(json, "density", "Density", ctx),
                getDouble(json, "sampleDistance", getDouble(json, "SampleDistance", 1.0))
            )
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("RandomVectorProvider", (json, ctx) -> {
            long seed = ctx.nextSeedSalt(getInt(json, "seed", getInt(json, "Seed", 0)));
            return new RandomVectorProvider(seed);
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Random", (json, ctx) -> {
            long seed = ctx.nextSeedSalt(getInt(json, "seed", getInt(json, "Seed", 0)));
            return new RandomVectorProvider(seed);
        });

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("CacheVectorProvider", (json, ctx) ->
            new CacheVectorProvider(compileChildVectorProvider(json, "vector", "Vector", ctx))
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Cache", (json, ctx) ->
            new CacheVectorProvider(compileChildVectorProvider(json, "vector", "Vector", ctx))
        );

        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Sum", (json, ctx) ->
            WorldGenV2Registries.VECTOR_PROVIDER_TYPE.get("Adder").deserialize(json, ctx)
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Product", (json, ctx) ->
            WorldGenV2Registries.VECTOR_PROVIDER_TYPE.get("Multiplier").deserialize(json, ctx)
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("ScalarMultiply", (json, ctx) ->
            WorldGenV2Registries.VECTOR_PROVIDER_TYPE.get("ScalarMultiplier").deserialize(json, ctx)
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("CrossProduct", (json, ctx) ->
            WorldGenV2Registries.VECTOR_PROVIDER_TYPE.get("Cross").deserialize(json, ctx)
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Normalized", (json, ctx) ->
            WorldGenV2Registries.VECTOR_PROVIDER_TYPE.get("Normalizer").deserialize(json, ctx)
        );
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("DotProduct", (json, ctx) -> {
            VectorProvider a = compileChildVectorProvider(json, "vectorA", "a", ctx);
            VectorProvider b = compileChildVectorProvider(json, "vectorB", "b", ctx);
            return (x, y, z) -> {
                Vec3d va = a.getVector(x, y, z);
                Vec3d vb = b.getVector(x, y, z);
                double dot = va.x() * vb.x() + va.y() * vb.y() + va.z() * vb.z();
                return new Vec3d(dot, dot, dot);
            };
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Angle", (json, ctx) -> {
            VectorProvider a = compileChildVectorProvider(json, "vectorA", "a", ctx);
            VectorProvider b = compileChildVectorProvider(json, "vectorB", "b", ctx);
            return (x, y, z) -> {
                Vec3d va = a.getVector(x, y, z);
                Vec3d vb = b.getVector(x, y, z);
                double lenA = Math.sqrt(va.x() * va.x() + va.y() * va.y() + va.z() * va.z());
                double lenB = Math.sqrt(vb.x() * vb.x() + vb.y() * vb.y() + vb.z() * vb.z());
                if (lenA < 1e-6 || lenB < 1e-6) return new Vec3d(0, 0, 0);
                double cos = (va.x() * vb.x() + va.y() * vb.y() + va.z() * vb.z()) / (lenA * lenB);
                double angle = Math.acos(Math.clamp(cos, -1.0, 1.0));
                return new Vec3d(angle, angle, angle);
            };
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Interpolate", (json, ctx) -> {
            VectorProvider a = compileChildVectorProvider(json, "vectorA", "a", ctx);
            VectorProvider b = compileChildVectorProvider(json, "vectorB", "b", ctx);
            DensityFunction factor = compileChildDensity(json, "factor", "t", ctx);
            return (x, y, z) -> {
                Vec3d va = a.getVector(x, y, z);
                Vec3d vb = b.getVector(x, y, z);
                double t = factor.compute((float) x, (float) y, (float) z);
                return new Vec3d(
                    va.x() + t * (vb.x() - va.x()),
                    va.y() + t * (vb.y() - va.y()),
                    va.z() + t * (vb.z() - va.z())
                );
            };
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("SimplexNoise", (json, ctx) -> {
            int seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            return new RandomVectorProvider(seed);
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Rotate", (json, ctx) -> {
            VectorProvider vec = compileChildVectorProvider(json, "vector", "input", ctx);
            float angleDeg = getFloat(json, "angle", 0.0f);
            double rad = Math.toRadians(angleDeg);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            return (x, y, z) -> {
                Vec3d v = vec.getVector(x, y, z);
                return new Vec3d(v.x() * cos - v.z() * sin, v.y(), v.x() * sin + v.z() * cos);
            };
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Scale", (json, ctx) -> {
            VectorProvider vec = compileChildVectorProvider(json, "vector", "input", ctx);
            double sx = getDouble(json, "scaleX", getDouble(json, "scale", 1.0));
            double sy = getDouble(json, "scaleY", getDouble(json, "scale", 1.0));
            double sz = getDouble(json, "scaleZ", getDouble(json, "scale", 1.0));
            return (x, y, z) -> {
                Vec3d v = vec.getVector(x, y, z);
                return new Vec3d(v.x() * sx, v.y() * sy, v.z() * sz);
            };
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Combine", (json, ctx) -> {
            DensityFunction dx = compileChildDensity(json, "x", "X", ctx);
            DensityFunction dy = compileChildDensity(json, "y", "Y", ctx);
            DensityFunction dz = compileChildDensity(json, "z", "Z", ctx);
            return (x, y, z) -> new Vec3d(
                dx.compute((float) x, (float) y, (float) z),
                dy.compute((float) x, (float) y, (float) z),
                dz.compute((float) x, (float) y, (float) z)
            );
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Split", (json, ctx) -> {
            VectorProvider vec = compileChildVectorProvider(json, "vector", "input", ctx);
            String comp = getString(json, "component", "x").toLowerCase(Locale.ROOT);
            return (x, y, z) -> {
                Vec3d v = vec.getVector(x, y, z);
                double val = switch (comp) {
                    case "y" -> v.y();
                    case "z" -> v.z();
                    default -> v.x();
                };
                return new Vec3d(val, val, val);
            };
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Imported", (json, ctx) -> {
            String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
            Object loaded = ctx.resolveAsset(asset, "vector");
            if (loaded != null) {
                return compileVectorProvider(loaded, ctx);
            }
            return new ConstantVectorProvider(0, 0, 0);
        });
        WorldGenV2Registries.VECTOR_PROVIDER_TYPE.register("Exported", (json, ctx) ->
            compileChildVectorProvider(json, "node", "child", ctx)
        );

        // =====================================================================
        // 2. Built-In MaterialProvider Node Types
        // =====================================================================
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("ConstantMaterialProvider", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String mat = getString(json, "material", getString(json, "block", "minecraft:stone"));
                return new ConstantMaterialProvider<>(blockResolver.apply(mat));
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("SpaceAndDepthMaterialProvider", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return deserializeSpaceAndDepth(json, blockResolver);
            }
        });
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("SpaceAndDepth", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return deserializeSpaceAndDepth(json, blockResolver);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("WeightedMaterialProvider", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<WeightedMaterialProvider.Entry<T>> entries = new ArrayList<>();
                Object rawWeights = json.getOrDefault("weights", json.get("entries"));
                if (rawWeights instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, Object> entryMap = (Map<String, Object>) m;
                            float weight = getFloat(entryMap, "weight", 1.0f);
                            if (entryMap.get("provider") instanceof Map<?, ?> childProvMap) {
                                MaterialProvider<T> childProv = compileMaterialProvider((Map<String, Object>) childProvMap, context, blockResolver);
                                entries.add(new WeightedMaterialProvider.Entry<>(childProv, weight));
                            } else {
                                String mat = getString(entryMap, "material", "minecraft:stone");
                                entries.add(WeightedMaterialProvider.Entry.ofMaterial(blockResolver.apply(mat), weight));
                            }
                        }
                    }
                }
                long salt = getInt(json, "seed", 0);
                return new WeightedMaterialProvider<>(entries, context.worldSeed() ^ salt);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("QueueMaterialProvider", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<QueueMaterialProvider.ConditionalRule<T>> rules = new ArrayList<>();
                Object rawProviders = json.getOrDefault("providers", json.get("rules"));
                if (rawProviders instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, Object> ruleMap = (Map<String, Object>) m;
                            Map<String, Object> patternJson = (Map<String, Object>) ruleMap.get("pattern");
                            Map<String, Object> providerJson = (Map<String, Object>) ruleMap.get("provider");
                            Pattern<T> pattern = patternJson != null
                                ? compilePattern(patternJson, context, blockResolver)
                                : new SurfacePattern<>();
                            MaterialProvider<T> provider = compileMaterialProvider(providerJson, context, blockResolver);
                            rules.add(new QueueMaterialProvider.ConditionalRule<>(pattern, provider));
                        }
                    }
                }
                MaterialProvider<T> fallback;
                Object rawFallback = json.get("fallback");
                if (rawFallback instanceof Map<?, ?> fbMap) {
                    fallback = compileMaterialProvider((Map<String, Object>) fbMap, context, blockResolver);
                } else if (rawFallback instanceof String fbStr) {
                    fallback = new ConstantMaterialProvider<>(blockResolver.apply(fbStr));
                } else {
                    fallback = new ConstantMaterialProvider<>(blockResolver.apply("minecraft:stone"));
                }
                return new QueueMaterialProvider<>(rules, fallback);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("GraphMaterialProvider", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                GraphGenerator gen = compileGraphGenerator(json.get("graphGenerator"), context, blockResolver);
                String layer = getString(json, "contentLayer", getString(json, "ContentLayer", ""));
                MaterialProvider<T> fallback = json.containsKey("fallback")
                    ? compileMaterialProvider((Map<String, Object>) json.get("fallback"), context, blockResolver)
                    : null;   // Hytale: no content in range means no material (a Queue moves on)
                return new GraphMaterialProvider<>(gen, layer, fallback, context.worldSeed());
            }
        });
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Graph", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get("GraphMaterialProvider").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Strata", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<StrataMaterialProvider.Stratum<T>> list = new ArrayList<>();
                Object rawStrata = json.getOrDefault("strata", json.getOrDefault("Strata", json.getOrDefault("layers", json.get("Layers"))));
                if (rawStrata instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> sm) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sMap = (Map<String, Object>) sm;
                            int minY = getInt(sMap, "minY", getInt(sMap, "min", -64));
                            int maxY = getInt(sMap, "maxY", getInt(sMap, "max", 320));
                            Object pObj = sMap.containsKey("provider") ? sMap.get("provider") : sMap.get("material");
                            MaterialProvider<T> p = compileMaterialProvider(pObj, context, blockResolver);
                            list.add(new StrataMaterialProvider.Stratum<>(minY, maxY, p));
                        }
                    }
                }
                MaterialProvider<T> fallback = json.containsKey("fallback")
                    ? compileMaterialProvider(json.get("fallback"), context, blockResolver)
                    : new ConstantMaterialProvider<>(blockResolver.apply("minecraft:stone"));
                return new StrataMaterialProvider<>(list, fallback);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Striped", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<StripedMaterialProvider.Stripe<T>> list = new ArrayList<>();
                Object rawStripes = json.getOrDefault("stripes", json.get("Stripes"));
                if (rawStripes instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> sm) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> sMap = (Map<String, Object>) sm;
                            int thickness = getInt(sMap, "thickness", 4);
                            Object pObj = sMap.containsKey("provider") ? sMap.get("provider") : sMap.get("material");
                            MaterialProvider<T> p = compileMaterialProvider(pObj, context, blockResolver);
                            list.add(new StripedMaterialProvider.Stripe<>(thickness, p));
                        }
                    }
                }
                if (list.isEmpty()) {
                    list.add(new StripedMaterialProvider.Stripe<>(4, new ConstantMaterialProvider<>(blockResolver.apply("minecraft:stone"))));
                }
                return new StripedMaterialProvider<>(list);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Layer", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                int minY = getInt(json, "minY", getInt(json, "min", 0));
                int maxY = getInt(json, "maxY", getInt(json, "max", 64));
                Object lObj = json.containsKey("layerMaterial") ? json.get("layerMaterial") : json.get("material");
                MaterialProvider<T> layerMat = compileMaterialProvider(lObj, context, blockResolver);
                MaterialProvider<T> fallback = json.containsKey("fallback")
                    ? compileMaterialProvider(json.get("fallback"), context, blockResolver)
                    : new ConstantMaterialProvider<>(blockResolver.apply("minecraft:stone"));
                return new LayerMaterialProvider<>(minY, maxY, layerMat, fallback);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Solidity", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                DensityFunction density = compileChildDensity(json, "density", "Density", context);
                float threshold = getFloat(json, "threshold", 0.0f);
                MaterialProvider<T> solid = compileMaterialProvider(json.get("solid"), context, blockResolver);
                MaterialProvider<T> fluid = json.containsKey("fluid")
                    ? compileMaterialProvider(json.get("fluid"), context, blockResolver)
                    : new ConstantMaterialProvider<>(blockResolver.apply("minecraft:water"));
                return new SolidityMaterialProvider<>(density, threshold, solid, fluid);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("SimpleHorizontal", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get("Layer").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Transparent", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compileMaterialProvider((Map<String, Object>) json.getOrDefault("material", json.get("provider")), context, blockResolver);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Surface", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get("SpaceAndDepth").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Stacked", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get("SpaceAndDepth").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Terrain", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get("SpaceAndDepth").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Mask", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.get("SpaceAndDepth").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Imported", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
                Object loaded = context.resolveAsset(asset, "material");
                if (loaded instanceof Map<?, ?> map) {
                    return compileMaterialProvider((Map<String, Object>) map, context, blockResolver);
                }
                return new ConstantMaterialProvider<>(blockResolver.apply("minecraft:stone"));
            }
        });
        WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.register("Exported", new WorldGenV2Registries.MaterialProviderDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> MaterialProvider<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compileMaterialProvider((Map<String, Object>) json.getOrDefault("node", json.get("provider")), context, blockResolver);
            }
        });

        // =====================================================================
        // 3. Built-In Pattern Matcher Node Types
        // =====================================================================
        WorldGenV2Registries.PATTERN_TYPE.register("SurfacePattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return new SurfacePattern<>();
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("WallPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                float minSlope = getFloat(json, "minSlopeDegrees", getFloat(json, "minAngleDegrees", 60.0f));
                float maxSlope = getFloat(json, "maxSlopeDegrees", getFloat(json, "maxAngleDegrees", 120.0f));
                return new WallPattern<>(minSlope, maxSlope);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("CeilingPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                float minSlope = getFloat(json, "minAngleDegrees", 115.0f);
                return new CeilingPattern<>(minSlope);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("SlopePattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                float minDeg = getFloat(json, "minAngleDegrees", getFloat(json, "minSlope", 0.0f));
                float maxDeg = getFloat(json, "maxAngleDegrees", getFloat(json, "maxSlope", 180.0f));
                return new SlopePattern<>(minDeg, maxDeg);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("SpaceCheckPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                int minSpace = getInt(json, "minAirAbove", getInt(json, "minSpace", 1));
                int maxSpace = getInt(json, "maxAirAbove", getInt(json, "maxSpace", Integer.MAX_VALUE));
                return new SpaceCheckPattern<>(minSpace, maxSpace);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("DepthPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                int minDepth = getInt(json, "minDepth", 0);
                int maxDepth = getInt(json, "maxDepth", Integer.MAX_VALUE);
                return new DepthPattern<>(minDepth, maxDepth);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("GapPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                int minGap = getInt(json, "minGap", 1);
                int maxGap = getInt(json, "maxGap", 16);
                boolean horizontal = getBoolean(json, "checkHorizontal", false);
                return new GapPattern<>(minGap, maxGap, horizontal);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("CuboidPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                int minX = getInt(json, "minX", Integer.MIN_VALUE);
                int minY = getInt(json, "minY", Integer.MIN_VALUE);
                int minZ = getInt(json, "minZ", Integer.MIN_VALUE);
                int maxX = getInt(json, "maxX", Integer.MAX_VALUE);
                int maxY = getInt(json, "maxY", Integer.MAX_VALUE);
                int maxZ = getInt(json, "maxZ", Integer.MAX_VALUE);
                return new CuboidPattern<>(minX, minY, minZ, maxX, maxY, maxZ);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("OffsetPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Map<String, Object> childMap = (Map<String, Object>) json.getOrDefault("child", json.get("pattern"));
                Pattern<T> child = compilePattern(childMap, context, blockResolver);
                return new OffsetPattern<>(
                    child,
                    getInt(json, "dx", 0),
                    getInt(json, "dy", 0),
                    getInt(json, "dz", 0)
                );
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("AndPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return new AndPattern<>(compilePatternList(json, "patterns", context, blockResolver));
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("OrPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return new OrPattern<>(compilePatternList(json, "patterns", context, blockResolver));
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("NotPattern", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Map<String, Object> childMap = (Map<String, Object>) json.getOrDefault("child", json.get("pattern"));
                return new NotPattern<>(compilePattern(childMap, context, blockResolver));
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("BlockType", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String blockId = getString(json, "block", getString(json, "material", "minecraft:stone"));
                return new BlockTypePattern<>(blockResolver.apply(blockId));
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("BlockSet", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Set<T> set = new LinkedHashSet<>();
                Object raw = json.getOrDefault("blocks", json.get("materials"));
                if (raw instanceof List<?> list) {
                    for (Object o : list) {
                        if (o != null) set.add(blockResolver.apply(o.toString()));
                    }
                }
                return new BlockSetPattern<>(set);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("Rotator", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Pattern<T> child = compilePattern((Map<String, Object>) json.get("child"), context, blockResolver);
                int turns = getInt(json, "turns", getInt(json, "quarterTurns", 1));
                return new RotatorPattern<>(child, turns);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("Surface", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PATTERN_TYPE.get("SurfacePattern").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("BlockMask", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return new SurfacePattern<>();
            }
        });

        WorldGenV2Registries.PATTERN_TYPE.register("Imported", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
                Object loaded = context.resolveAsset(asset, "pattern");
                if (loaded instanceof Map<?, ?> map) {
                    return compilePattern((Map<String, Object>) map, context, blockResolver);
                }
                return new SurfacePattern<>();
            }
        });
        WorldGenV2Registries.PATTERN_TYPE.register("Exported", new WorldGenV2Registries.PatternDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Pattern<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compilePattern((Map<String, Object>) json.getOrDefault("node", json.get("pattern")), context, blockResolver);
            }
        });

        // =====================================================================
        // 4. Built-In Prop Node Types
        // =====================================================================
        WorldGenV2Registries.PROP_TYPE.register("BlockProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String blockId = getString(json, "block", getString(json, "material", "minecraft:short_grass"));
                int height = getInt(json, "height", 1);
                boolean requireAir = getBoolean(json, "requireAirTarget", true);
                BlockMask<T> mask = (json.containsKey("mask") || json.containsKey("blockMask"))
                    ? compileBlockMask(json.getOrDefault("mask", json.get("blockMask")), context, blockResolver)
                    : null;
                return new BlockProp<>(blockResolver.apply(blockId), height, requireAir, mask);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("CuboidProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String mat = getString(json, "material", getString(json, "block", "minecraft:stone"));
                int rx = getInt(json, "radiusX", 1);
                int height = getInt(json, "height", 3);
                int rz = getInt(json, "radiusZ", 1);
                int yOffset = getInt(json, "yOffset", 0);
                boolean bevel = getBoolean(json, "bevelEdges", false);
                BlockMask<T> mask = (json.containsKey("mask") || json.containsKey("blockMask"))
                    ? compileBlockMask(json.getOrDefault("mask", json.get("blockMask")), context, blockResolver)
                    : null;
                return new CuboidProp<>(blockResolver.apply(mat), rx, height, rz, yOffset, bevel, mask);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("WeightedProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<WeightedProp.Entry<T>> entries = new ArrayList<>();
                Object rawProps = json.getOrDefault("props", json.get("entries"));
                if (rawProps instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, Object> propMap = (Map<String, Object>) m;
                            float weight = getFloat(propMap, "weight", 1.0f);
                            Prop<T> child;
                            if (propMap.get("prop") instanceof Map<?, ?> nestedProp) {
                                child = compileProp((Map<String, Object>) nestedProp, context, blockResolver);
                            } else {
                                child = compileProp(propMap, context, blockResolver);
                            }
                            entries.add(new WeightedProp.Entry<>(child, weight));
                        }
                    }
                }
                return new WeightedProp<>(entries);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("DensitySelectorProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                float threshold = getFloat(json, "threshold", 0.5f);
                float multiplier = getFloat(json, "biomeMultiplier", 1.0f);
                boolean probabilistic = getBoolean(json, "probabilistic", false);
                Map<String, Object> densityMap = (Map<String, Object>) json.getOrDefault("densitySource", json.get("density"));
                DensityFunction density = densityMap != null
                    ? compileDensity(densityMap, context)
                    : new ConstantDensity(1.0f);
                Map<String, Object> childMap = (Map<String, Object>) json.get("child");
                Prop<T> child = compileProp(childMap, context, blockResolver);
                return new DensitySelectorProp<>(density, threshold, multiplier, probabilistic, child);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("MaskProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Set<T> allowed = new LinkedHashSet<>();
                if (json.get("requiredBlocks") instanceof List<?> blockList) {
                    for (Object b : blockList) {
                        allowed.add(blockResolver.apply(String.valueOf(b)));
                    }
                } else if (json.get("requiredBlock") != null) {
                    allowed.add(blockResolver.apply(String.valueOf(json.get("requiredBlock"))));
                }
                float minSlope = getFloat(json, "minSlopeDegrees", 0.0f);
                float maxSlope = getFloat(json, "maxSlopeDegrees", 45.0f);
                BlockMask<T> blockMask = (json.containsKey("mask") || json.containsKey("blockMask"))
                    ? compileBlockMask(json.getOrDefault("mask", json.get("blockMask")), context, blockResolver)
                    : null;
                Map<String, Object> childMap = (Map<String, Object>) json.getOrDefault("child", json.get("prop"));
                Prop<T> child = compileProp(childMap, context, blockResolver);
                Predicate<T> filter = allowed.isEmpty() && !json.containsKey("requiredBlocks") && !json.containsKey("requiredBlock")
                    ? null
                    : (allowed.isEmpty() ? (b -> true) : allowed::contains);
                return new MaskProp<>(filter, minSlope, maxSlope, blockMask, child);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Mask", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("MaskProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("StructureNearProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Map<String, Object> childMap = (Map<String, Object>) json.getOrDefault("child", json.get("prop"));
                Prop<T> child = compileProp(childMap, context, blockResolver);
                return new com.worldgenv2.props.StructureNearProp<>(
                    com.worldgenv2.props.StructureNearProp.current(),
                    getString(json, "structure", ""),
                    getInt(json, "radius", 8),
                    getInt(json, "yMin", -64),
                    getInt(json, "yMax", 64),
                    getBoolean(json, "invert", false),
                    child);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("RandomRotatorProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Map<String, Object> childMap = (Map<String, Object>) json.getOrDefault("child", json.get("prop"));
                Prop<T> child = compileProp(childMap, context, blockResolver);
                boolean horizontal = getBoolean(json, "horizontalRotations", true);
                String seed = getString(json, "seed", "rot");
                return new RandomRotatorProp<>(child, horizontal, seed);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("RandomRotator", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("RandomRotatorProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("OffsetProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Map<String, Object> childMap = (Map<String, Object>) json.getOrDefault("child", json.get("prop"));
                Prop<T> child = compileProp(childMap, context, blockResolver);
                int ox = getInt(json, "offsetX", getInt(json, "x", 0));
                int oy = getInt(json, "offsetY", getInt(json, "y", 0));
                int oz = getInt(json, "offsetZ", getInt(json, "z", 0));
                if (json.get("offset") instanceof List<?> list && list.size() >= 3) {
                    ox = ((Number) list.get(0)).intValue();
                    oy = ((Number) list.get(1)).intValue();
                    oz = ((Number) list.get(2)).intValue();
                }
                return new OffsetProp<>(child, ox, oy, oz);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Offset", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("OffsetProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("UnionProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<Prop<T>> props = new ArrayList<>();
                Object rawProps = json.getOrDefault("props", json.get("children"));
                if (rawProps instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            props.add(compileProp((Map<String, Object>) m, context, blockResolver));
                        }
                    }
                }
                return new UnionProp<>(props);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Union", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("UnionProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("QueueProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<Prop<T>> props = new ArrayList<>();
                Object rawProps = json.getOrDefault("props", json.get("children"));
                if (rawProps instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            props.add(compileProp((Map<String, Object>) m, context, blockResolver));
                        }
                    }
                }
                return new QueueProp<>(props);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Queue", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("QueueProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("ManualProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<ManualProp.Entry<T>> entries = new ArrayList<>();
                Object rawBlocks = json.getOrDefault("blocks", json.get("entries"));
                if (rawBlocks instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            Map<String, Object> blockMap = (Map<String, Object>) m;
                            int ox = getInt(blockMap, "x", getInt(blockMap, "offsetX", 0));
                            int oy = getInt(blockMap, "y", getInt(blockMap, "offsetY", 0));
                            int oz = getInt(blockMap, "z", getInt(blockMap, "offsetZ", 0));
                            String blockId = getString(blockMap, "block", getString(blockMap, "material", "minecraft:stone"));
                            T b = blockResolver.apply(blockId);
                            if (b != null) {
                                entries.add(new ManualProp.Entry<>(ox, oy, oz, b));
                            }
                        }
                    }
                }
                return new ManualProp<>(entries);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Manual", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("ManualProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("DensityProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                DensityFunction density = compileDensity(
                    (Map<String, Object>) json.getOrDefault("density", json.get("densitySource")),
                    context
                );
                MaterialProvider<T> material = compileMaterialProvider(
                    (Map<String, Object>) json.getOrDefault("material", json.get("materialProvider")),
                    context,
                    blockResolver
                );
                float threshold = getFloat(json, "threshold", 0.0f);

                int minX = -4, minY = -4, minZ = -4;
                int maxX = 4, maxY = 4, maxZ = 4;
                if (json.get("bounds") instanceof Map<?, ?> boundsMap) {
                    Map<String, Object> bm = (Map<String, Object>) boundsMap;
                    minX = getInt(bm, "minX", -4);
                    minY = getInt(bm, "minY", -4);
                    minZ = getInt(bm, "minZ", -4);
                    maxX = getInt(bm, "maxX", 4);
                    maxY = getInt(bm, "maxY", 4);
                    maxZ = getInt(bm, "maxZ", 4);
                } else {
                    int rx = getInt(json, "radiusX", getInt(json, "radius", 4));
                    int ry = getInt(json, "radiusY", getInt(json, "radius", 4));
                    int rz = getInt(json, "radiusZ", getInt(json, "radius", 4));
                    minX = -rx; maxX = rx;
                    minY = -ry; maxY = ry;
                    minZ = -rz; maxZ = rz;
                }
                return new DensityProp<>(density, material, minX, minY, minZ, maxX, maxY, maxZ, threshold);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Density", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("DensityProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Assigned", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("AssignmentsProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("OrienterProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                float tilt = getFloat(json, "tiltStrength", 1.0f);
                Map<String, Object> childMap = (Map<String, Object>) json.get("child");
                return new OrienterProp<>(compileProp(childMap, context, blockResolver), tilt);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("StaticRotatorProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Map<String, Object> childMap = (Map<String, Object>) json.get("child");
                Prop<T> child = compileProp(childMap, context, blockResolver);
                if (json.containsKey("rotation")) {
                    int deg = getInt(json, "rotation", 0);
                    return new StaticRotatorProp<>(child, StaticRotatorProp.CardinalRotation.fromQuarterTurns(deg / 90));
                }
                return new StaticRotatorProp<>(child);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("LocatorProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                int minOff = getInt(json, "minYOffset", -8);
                int maxOff = getInt(json, "maxYOffset", 8);
                String surfaceStr = getString(json, "targetSurface", "FLOOR").toUpperCase(Locale.ROOT);
                LocatorProp.TargetSurface target = "CEILING".equals(surfaceStr)
                    ? LocatorProp.TargetSurface.CEILING
                    : LocatorProp.TargetSurface.FLOOR;
                Map<String, Object> childMap = (Map<String, Object>) json.get("child");
                return new LocatorProp<>(minOff, maxOff, target, compileProp(childMap, context, blockResolver));
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("PondFillerProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                T fluid = blockResolver.apply(getString(json, "fluidMaterial", "minecraft:water"));
                T basin = blockResolver.apply(getString(json, "basinMaterial", "minecraft:clay"));
                T air = blockResolver.apply(getString(json, "airMaterial", "minecraft:air"));
                int radius = getInt(json, "radius", 3);
                int depth = getInt(json, "depth", 2);
                return new PondFillerProp<>(fluid, basin, air, radius, depth);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("PrefabProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String prefabId = getString(json, "prefab", "worldgenv2:structures/watchtower_01");
                Prefab prefab = context.resolvePrefab(prefabId);
                boolean includeAir = getBoolean(json, "includeAir", false);
                boolean randomRot = getBoolean(json, "randomCardinalRotation", true);
                int maxDepth = getInt(json, "maxSocketDepth", 1);
                BlockMask<T> mask = (json.containsKey("mask") || json.containsKey("blockMask"))
                    ? compileBlockMask(json.getOrDefault("mask", json.get("blockMask")), context, blockResolver)
                    : null;

                List<Prefab> children = new ArrayList<>();
                if (json.get("socketPool") instanceof List<?> poolList) {
                    for (Object item : poolList) {
                        Prefab childPrefab = context.resolvePrefab(String.valueOf(item));
                        if (childPrefab != null) {
                            children.add(childPrefab);
                        }
                    }
                }
                return new PrefabProp<>(prefab, blockResolver, includeAir, randomRot, children, maxDepth, mask);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("AssignmentsProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                PositionsProvider posProv = compilePositionsProvider(
                    json.getOrDefault("positions", json.get("positionsProvider")),
                    context
                );
                Scanner scanner = compileScanner(json.get("scanner"), context);
                Assignments<T> assignments = compileAssignments(
                    json.getOrDefault("assignments", json.get("assignment")),
                    context,
                    blockResolver
                );
                return new AssignmentsProp<>(posProv, scanner, assignments, Objects::nonNull);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Assignments", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("AssignmentsProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("GraphProp", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                GraphGenerator gen = compileGraphGenerator(json.get("graphGenerator"), context, blockResolver);
                String layer = getString(json, "contentLayer", getString(json, "ContentLayer", ""));
                return new GraphProp<>(gen, layer);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Graph", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("GraphProp").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.PROP_TYPE.register("Block", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("BlockProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Cuboid", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("CuboidProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Weighted", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("WeightedProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("DensitySelector", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("DensitySelectorProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Mask", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("MaskProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("RandomRotator", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("RandomRotatorProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("StaticRotator", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("StaticRotatorProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Offset", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("OffsetProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Union", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("UnionProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Queue", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("QueueProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Manual", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("ManualProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Prefab", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("PrefabProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Locator", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("LocatorProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Orienter", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("OrienterProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("PondFiller", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("PondFillerProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Density", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("DensityProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Assigned", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_TYPE.get("AssignmentsProp").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Imported", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
                Object loaded = context.resolveAsset(asset, "prop");
                if (loaded instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) map;
                    return compileProp(m, context, blockResolver);
                }
                return new BlockProp<>(blockResolver.apply("minecraft:short_grass"), 1, true);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Exported", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) json.getOrDefault("node", json.get("prop"));
                return compileProp(m, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("PropRuntime", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compilePropRuntime(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_TYPE.register("Runtime", new WorldGenV2Registries.PropDeserializer() {
            @Override
            public <T> Prop<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compilePropRuntime(json, context, blockResolver);
            }
        });

        // =====================================================================
        // 5. Built-In PositionsProvider Node Types
        // =====================================================================
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("SquareGrid2dPositionsProvider", (json, ctx) ->
            new SquareGrid2dPositionsProvider(getDouble(json, "spacing", getDouble(json, "scale", 1.0)))
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("SquareGrid2d", (json, ctx) ->
            new SquareGrid2dPositionsProvider(getDouble(json, "spacing", getDouble(json, "scale", 1.0)))
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("SquareGrid3dPositionsProvider", (json, ctx) ->
            new SquareGrid3dPositionsProvider(getDouble(json, "spacing", getDouble(json, "scale", 1.0)))
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("SquareGrid3d", (json, ctx) ->
            new SquareGrid3dPositionsProvider(getDouble(json, "spacing", getDouble(json, "scale", 1.0)))
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("TriangularGrid2dPositionsProvider", (json, ctx) ->
            new TriangularGrid2dPositionsProvider(getDouble(json, "spacing", getDouble(json, "scale", 1.0)))
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("TriangularGrid2d", (json, ctx) ->
            new TriangularGrid2dPositionsProvider(getDouble(json, "spacing", getDouble(json, "scale", 1.0)))
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("ScalerPositionsProvider", (json, ctx) -> {
            Vec3d scale = json.containsKey("scale")
                ? compileVectorProvider(json.get("scale"), ctx).getVector(0, 0, 0)
                : new Vec3d(1, 1, 1);
            return new ScalerPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                scale
            );
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Scaler", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("ScalerPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Jitter2dPositionsProvider", (json, ctx) ->
            new Jitter2dPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                getDouble(json, "magnitude", 1.0),
                ctx.nextSeedSalt(getInt(json, "seed", 0))
            )
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Jitter2d", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("Jitter2dPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Jitter3dPositionsProvider", (json, ctx) ->
            new Jitter3dPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                getDouble(json, "magnitude", 1.0),
                ctx.nextSeedSalt(getInt(json, "seed", 0))
            )
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Jitter3d", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("Jitter3dPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("DirectionalJitterPositionsProvider", (json, ctx) -> {
            Vec3d dir = json.containsKey("direction")
                ? compileVectorProvider(json.get("direction"), ctx).getVector(0, 0, 0)
                : Vec3d.UNIT_Y;
            return new DirectionalJitterPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                dir,
                getDouble(json, "magnitude", 1.0),
                getBoolean(json, "isBidirectional", getBoolean(json, "bidirectional", false)),
                ctx.nextSeedSalt(getInt(json, "seed", 0))
            );
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("DirectionalJitter", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("DirectionalJitterPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("OccurrencePositionsProvider", (json, ctx) ->
            new OccurrencePositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                compileChildDensity(json, "fieldFunction", "density", ctx),
                ctx.nextSeedSalt(getInt(json, "seed", 0))
            )
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Occurrence", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("OccurrencePositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("FieldFunctionPositionsProvider", (json, ctx) -> {
            List<FieldFunctionPositionsProvider.Delimiter> delimiters = new ArrayList<>();
            if (json.get("delimiters") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dm = (Map<String, Object>) m;
                        delimiters.add(new FieldFunctionPositionsProvider.Delimiter(
                            getDouble(dm, "min", Double.NEGATIVE_INFINITY),
                            getDouble(dm, "max", Double.POSITIVE_INFINITY)
                        ));
                    }
                }
            } else {
                delimiters.add(new FieldFunctionPositionsProvider.Delimiter(
                    getDouble(json, "min", 0.0),
                    getDouble(json, "max", 1.0)
                ));
            }
            return new FieldFunctionPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                compileChildDensity(json, "density", "fieldFunction", ctx),
                delimiters
            );
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("FieldFunction", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("FieldFunctionPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("OffsetPositionsProvider", (json, ctx) -> {
            Vec3d offset = json.containsKey("offset")
                ? compileVectorProvider(json.get("offset"), ctx).getVector(0, 0, 0)
                : new Vec3d(getDouble(json, "dx", 0.0), getDouble(json, "dy", 0.0), getDouble(json, "dz", 0.0));
            return new OffsetPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                offset
            );
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Offset", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("OffsetPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("VectorOffsetPositionsProvider", (json, ctx) ->
            new VectorOffsetPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                compileChildVectorProvider(json, "vectorProvider", "vector", ctx),
                getDouble(json, "maxOffsetDistance", 32.0)
            )
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("VectorOffset", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("VectorOffsetPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("UnionPositionsProvider", (json, ctx) -> {
            List<PositionsProvider> list = new ArrayList<>();
            if (json.get("positions") instanceof List<?> plist) {
                for (Object item : plist) {
                    list.add(compilePositionsProvider(item, ctx));
                }
            }
            return new UnionPositionsProvider(list);
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Union", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("UnionPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("ListPositionsProvider", (json, ctx) -> {
            List<Vec3d> pts = new ArrayList<>();
            if (json.get("positions") instanceof List<?> plist) {
                for (Object item : plist) {
                    if (item instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) m;
                        pts.add(new Vec3d(
                            getDouble(map, "x", getDouble(map, "X", 0.0)),
                            getDouble(map, "y", getDouble(map, "Y", 0.0)),
                            getDouble(map, "z", getDouble(map, "Z", 0.0))
                        ));
                    }
                }
            }
            return new ListPositionsProvider(pts);
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("List", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("ListPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("BoundPositionsProvider", (json, ctx) ->
            new BoundPositionsProvider(
                compilePositionsProvider(json.get("positions"), ctx),
                getDouble(json, "minX", Double.NEGATIVE_INFINITY),
                getDouble(json, "minY", Double.NEGATIVE_INFINITY),
                getDouble(json, "minZ", Double.NEGATIVE_INFINITY),
                getDouble(json, "maxX", Double.POSITIVE_INFINITY),
                getDouble(json, "maxY", Double.POSITIVE_INFINITY),
                getDouble(json, "maxZ", Double.POSITIVE_INFINITY)
            )
        );
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Bound", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("BoundPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("GraphPositionsProvider", (json, ctx) -> {
            GraphGenerator gen = compileGraphGenerator(json.get("graphGenerator"), ctx, s -> s);
            String layer = getString(json, "contentLayer", getString(json, "ContentLayer", ""));
            return new GraphPositionsProvider(gen, layer, ctx.worldSeed());
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Graph", (json, ctx) ->
            WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.get("GraphPositionsProvider").deserialize(json, ctx)
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Clusters", (json, ctx) -> {
            PositionsProvider parent = compilePositionsProvider(json.get("parent"), ctx);
            int count = getInt(json, "pointsPerCluster", getInt(json, "count", 5));
            double radius = getDouble(json, "clusterRadius", getDouble(json, "radius", 8.0));
            long seed = ctx.nextSeedSalt(getInt(json, "seed", 0));
            return new ClustersPositionsProvider(parent, count, radius, seed);
        });

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Anchor", (json, ctx) -> {
            double x = getDouble(json, "x", 0.0);
            double y = getDouble(json, "y", 64.0);
            double z = getDouble(json, "z", 0.0);
            return new AnchorPositionsProvider(x, y, z);
        });

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("SimpleHorizontal", (json, ctx) -> {
            double spacing = getDouble(json, "spacing", 16.0);
            double yLevel = getDouble(json, "yLevel", getDouble(json, "y", 64.0));
            boolean alongX = getBoolean(json, "alongX", true);
            return new SimpleHorizontalPositionsProvider(spacing, yLevel, alongX);
        });

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("PoissonDisk", (json, ctx) ->
            new TriangularGrid2dPositionsProvider(getDouble(json, "radius", getDouble(json, "spacing", 4.0)))
        );

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Framework", (json, ctx) -> {
            String name = getString(json, "name", getString(json, "Name", ""));
            Object loaded = ctx.resolveAsset(name);
            if (loaded != null) {
                return compilePositionsProvider(loaded, ctx);
            }
            return new SquareGrid2dPositionsProvider(16.0);
        });

        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Imported", (json, ctx) -> {
            String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
            Object loaded = ctx.resolveAsset(asset, "positions");
            if (loaded != null) {
                return compilePositionsProvider(loaded, ctx);
            }
            return new SquareGrid2dPositionsProvider(16.0);
        });
        WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.register("Exported", (json, ctx) ->
            compilePositionsProvider(json.getOrDefault("node", json.get("positions")), ctx)
        );

        // =====================================================================
        // 6. Built-In Scanner Node Types
        // =====================================================================
        WorldGenV2Registries.SCANNER_TYPE.register("OriginScanner", (json, ctx) -> OriginScanner.INSTANCE);
        WorldGenV2Registries.SCANNER_TYPE.register("Origin", (json, ctx) -> OriginScanner.INSTANCE);

        WorldGenV2Registries.SCANNER_TYPE.register("LinearScanner", (json, ctx) -> {
            String axisStr = getString(json, "axis", "Y");
            LinearScanner.Axis axis = switch (axisStr.toUpperCase(Locale.ROOT)) {
                case "X" -> LinearScanner.Axis.X;
                case "Z" -> LinearScanner.Axis.Z;
                default -> LinearScanner.Axis.Y;
            };
            Scanner child = json.containsKey("scanner") ? compileScanner(json.get("scanner"), ctx) : null;
            return new LinearScanner(axis, getInt(json, "minOffset", -16), getInt(json, "maxOffset", 16), child);
        });
        WorldGenV2Registries.SCANNER_TYPE.register("Linear", (json, ctx) ->
            WorldGenV2Registries.SCANNER_TYPE.get("LinearScanner").deserialize(json, ctx)
        );

        WorldGenV2Registries.SCANNER_TYPE.register("RandomScanner", (json, ctx) -> {
            String axisStr = getString(json, "axis", "Y");
            LinearScanner.Axis axis = switch (axisStr.toUpperCase(Locale.ROOT)) {
                case "X" -> LinearScanner.Axis.X;
                case "Z" -> LinearScanner.Axis.Z;
                default -> LinearScanner.Axis.Y;
            };
            Scanner child = json.containsKey("scanner") ? compileScanner(json.get("scanner"), ctx) : null;
            return new RandomScanner(
                axis,
                getInt(json, "minOffset", -16),
                getInt(json, "maxOffset", 16),
                ctx.nextSeedSalt(getInt(json, "seed", 0)),
                child
            );
        });
        WorldGenV2Registries.SCANNER_TYPE.register("Random", (json, ctx) ->
            WorldGenV2Registries.SCANNER_TYPE.get("RandomScanner").deserialize(json, ctx)
        );

        WorldGenV2Registries.SCANNER_TYPE.register("RadialScanner", (json, ctx) -> {
            Scanner child = json.containsKey("scanner") ? compileScanner(json.get("scanner"), ctx) : null;
            return new RadialScanner(getInt(json, "maxReach", getInt(json, "reach", 8)), child);
        });
        WorldGenV2Registries.SCANNER_TYPE.register("Radial", (json, ctx) ->
            WorldGenV2Registries.SCANNER_TYPE.get("RadialScanner").deserialize(json, ctx)
        );

        WorldGenV2Registries.SCANNER_TYPE.register("QueueScanner", (json, ctx) -> {
            List<Scanner> scanners = new ArrayList<>();
            if (json.get("scanners") instanceof List<?> list) {
                for (Object item : list) {
                    scanners.add(compileScanner(item, ctx));
                }
            }
            return new QueueScanner(scanners);
        });
        WorldGenV2Registries.SCANNER_TYPE.register("Queue", (json, ctx) ->
            WorldGenV2Registries.SCANNER_TYPE.get("QueueScanner").deserialize(json, ctx)
        );
        WorldGenV2Registries.SCANNER_TYPE.register("Imported", (json, ctx) -> {
            String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
            Object loaded = ctx.resolveAsset(asset, "scanner");
            if (loaded != null) {
                return compileScanner(loaded, ctx);
            }
            return new LinearScanner(LinearScanner.Axis.Y, -64, 320);
        });
        WorldGenV2Registries.SCANNER_TYPE.register("Exported", (json, ctx) ->
            compileScanner(json.getOrDefault("node", json.get("scanner")), ctx)
        );

        // =====================================================================
        // 7. Built-In Assignments Node Types
        // =====================================================================
        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("ConstantAssignment", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Prop<T> prop = compileProp((Map<String, Object>) json.get("prop"), context, blockResolver);
                return new ConstantAssignment<>(prop);
            }
        });
        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("Constant", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.ASSIGNMENTS_TYPE.get("ConstantAssignment").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("FieldFunctionAssignment", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                DensityFunction field = compileChildDensity(json, "fieldFunction", "density", context);
                List<FieldFunctionAssignment.Delimiter<T>> delimiters = new ArrayList<>();
                if (json.get("delimiters") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> dm = (Map<String, Object>) m;
                            Assignments<T> childAssign = compileAssignments(dm.get("assignments"), context, blockResolver);
                            delimiters.add(new FieldFunctionAssignment.Delimiter<>(
                                getDouble(dm, "min", Double.NEGATIVE_INFINITY),
                                getDouble(dm, "max", Double.POSITIVE_INFINITY),
                                childAssign
                            ));
                        }
                    }
                }
                return new FieldFunctionAssignment<>(field, delimiters);
            }
        });
        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("FieldFunction", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.ASSIGNMENTS_TYPE.get("FieldFunctionAssignment").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("SandwichAssignment", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<SandwichAssignment.HeightDelimiter<T>> delimiters = new ArrayList<>();
                if (json.get("delimiters") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> dm = (Map<String, Object>) m;
                            Assignments<T> childAssign = compileAssignments(dm.get("assignments"), context, blockResolver);
                            delimiters.add(new SandwichAssignment.HeightDelimiter<>(
                                getDouble(dm, "minY", Double.NEGATIVE_INFINITY),
                                getDouble(dm, "maxY", Double.POSITIVE_INFINITY),
                                childAssign
                            ));
                        }
                    }
                }
                return new SandwichAssignment<>(delimiters);
            }
        });
        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("Sandwich", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.ASSIGNMENTS_TYPE.get("SandwichAssignment").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("WeightedAssignment", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                List<WeightedAssignment.Entry<T>> entries = new ArrayList<>();
                if (json.get("weightedAssignments") instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> em = (Map<String, Object>) m;
                            Assignments<T> childAssign = compileAssignments(em.get("assignments"), context, blockResolver);
                            entries.add(new WeightedAssignment.Entry<>(childAssign, getDouble(em, "weight", 1.0)));
                        }
                    }
                }
                double skip = getDouble(json, "skipChance", 0.0);
                long seed = context.nextSeedSalt(getInt(json, "seed", 0));
                return new WeightedAssignment<>(entries, skip, seed);
            }
        });
        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("Weighted", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.ASSIGNMENTS_TYPE.get("WeightedAssignment").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("Imported", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
                Object loaded = context.resolveAsset(asset, "assignments");
                if (loaded != null) {
                    return compileAssignments(loaded, context, blockResolver);
                }
                return new ConstantAssignment<>(new BlockProp<>(blockResolver.apply("minecraft:short_grass"), 1, true));
            }
        });
        WorldGenV2Registries.ASSIGNMENTS_TYPE.register("Exported", new WorldGenV2Registries.AssignmentsDeserializer() {
            @Override
            public <T> Assignments<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compileAssignments(json.getOrDefault("node", json.get("assignments")), context, blockResolver);
            }
        });

        // =====================================================================
        // 8. Built-In BlockMask Node Types
        // =====================================================================
        WorldGenV2Registries.BLOCK_MASK_TYPE.register("DontPlaceMask", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Set<T> dontPlace = new LinkedHashSet<>();
                Object raw = json.getOrDefault("dontPlace", json.get("materials"));
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        dontPlace.add(blockResolver.apply(String.valueOf(item)));
                    }
                } else if (raw != null) {
                    dontPlace.add(blockResolver.apply(String.valueOf(raw)));
                }
                return new DontPlaceMask<>(dontPlace);
            }
        });
        WorldGenV2Registries.BLOCK_MASK_TYPE.register("DontPlace", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.BLOCK_MASK_TYPE.get("DontPlaceMask").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.BLOCK_MASK_TYPE.register("DontReplaceMask", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Set<T> dontReplace = new LinkedHashSet<>();
                Object raw = json.getOrDefault("dontReplace", json.get("materials"));
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        dontReplace.add(blockResolver.apply(String.valueOf(item)));
                    }
                } else if (raw != null) {
                    dontReplace.add(blockResolver.apply(String.valueOf(raw)));
                }
                return new DontReplaceMask<>(dontReplace);
            }
        });
        WorldGenV2Registries.BLOCK_MASK_TYPE.register("DontReplace", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.BLOCK_MASK_TYPE.get("DontReplaceMask").deserialize(json, context, blockResolver);
            }
        });

        WorldGenV2Registries.BLOCK_MASK_TYPE.register("AdvancedBlockMask", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                Set<T> dontPlace = new LinkedHashSet<>();
                Object rawDP = json.get("dontPlace");
                if (rawDP instanceof List<?> list) {
                    for (Object item : list) dontPlace.add(blockResolver.apply(String.valueOf(item)));
                } else if (rawDP != null) {
                    dontPlace.add(blockResolver.apply(String.valueOf(rawDP)));
                }

                Set<T> dontReplace = new LinkedHashSet<>();
                Object rawDR = json.get("dontReplace");
                if (rawDR instanceof List<?> list) {
                    for (Object item : list) dontReplace.add(blockResolver.apply(String.valueOf(item)));
                } else if (rawDR != null) {
                    dontReplace.add(blockResolver.apply(String.valueOf(rawDR)));
                }

                List<AdvancedBlockMask.Rule<T>> rules = new ArrayList<>();
                Object rawAdvanced = json.getOrDefault("advanced", json.get("rules"));
                if (rawAdvanced instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> rawMap) {
                            Map<String, Object> ruleMap = (Map<String, Object>) rawMap;
                            Set<T> srcSet = new LinkedHashSet<>();
                            Object rawSrc = ruleMap.getOrDefault("source", ruleMap.get("Source"));
                            if (rawSrc instanceof List<?> sl) {
                                for (Object s : sl) srcSet.add(blockResolver.apply(String.valueOf(s)));
                            } else if (rawSrc != null) {
                                srcSet.add(blockResolver.apply(String.valueOf(rawSrc)));
                            }

                            Set<T> canReplaceSet = new LinkedHashSet<>();
                            Object rawCan = ruleMap.getOrDefault("canReplace", ruleMap.getOrDefault("CanReplace", ruleMap.get("destination")));
                            if (rawCan instanceof List<?> dl) {
                                for (Object d : dl) canReplaceSet.add(blockResolver.apply(String.valueOf(d)));
                            } else if (rawCan != null) {
                                canReplaceSet.add(blockResolver.apply(String.valueOf(rawCan)));
                            }

                            rules.add(new AdvancedBlockMask.Rule<>(
                                SourceMaterial.of(srcSet),
                                DestinationMaterial.of(canReplaceSet)
                            ));
                        }
                    }
                }

                return new AdvancedBlockMask<>(
                    dontPlace.isEmpty() ? m -> false : dontPlace::contains,
                    dontReplace.isEmpty() ? m -> false : dontReplace::contains,
                    rules
                );
            }
        });
        WorldGenV2Registries.BLOCK_MASK_TYPE.register("BlockMask", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.BLOCK_MASK_TYPE.get("AdvancedBlockMask").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.BLOCK_MASK_TYPE.register("Advanced", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.BLOCK_MASK_TYPE.get("AdvancedBlockMask").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.BLOCK_MASK_TYPE.register("Imported", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
                Object loaded = context.resolveAsset(asset, "blockmask");
                if (loaded instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) map;
                    return compileBlockMask(m, context, blockResolver);
                }
                return BlockMask.always();
            }
        });
        WorldGenV2Registries.BLOCK_MASK_TYPE.register("Exported", new WorldGenV2Registries.BlockMaskDeserializer() {
            @Override
            public <T> BlockMask<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compileBlockMask(json.getOrDefault("node", json.get("mask")), context, blockResolver);
            }
        });

        // =====================================================================
        // 9. Built-In PropRuntime Node Types
        // =====================================================================
        WorldGenV2Registries.PROP_RUNTIME_TYPE.register("PropRuntime", new WorldGenV2Registries.PropRuntimeDeserializer() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> PropRuntime<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                int runtimeIndex = getInt(json, "stage", getInt(json, "runtimeIndex", 1));
                String precisionStr = getString(json, "precision", getString(json, "contextPrecision", "BIOME")).toUpperCase(Locale.ROOT);
                ContextPrecision precision = switch (precisionStr) {
                    case "CHUNK" -> ContextPrecision.CHUNK;
                    case "PROP" -> ContextPrecision.PROP;
                    default -> ContextPrecision.BIOME;
                };
                Object propObj = json.getOrDefault("prop", json.get("Prop"));
                Prop<T> prop;
                if (propObj instanceof Map<?, ?> m) {
                    prop = compileProp((Map<String, Object>) m, context, blockResolver);
                } else {
                    prop = compileProp(json, context, blockResolver);
                }
                int scannerReach = getInt(json, "scannerReach", 0);
                int patternOffset = getInt(json, "patternOffset", 0);
                int prefabRadius = getInt(json, "prefabRadius", getInt(json, "radius", 0));
                return new PropRuntime<>(runtimeIndex, precision, prop, scannerReach, patternOffset, prefabRadius);
            }
        });
        WorldGenV2Registries.PROP_RUNTIME_TYPE.register("Runtime", new WorldGenV2Registries.PropRuntimeDeserializer() {
            @Override
            public <T> PropRuntime<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_RUNTIME_TYPE.get("PropRuntime").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_RUNTIME_TYPE.register("ContextPrecision", new WorldGenV2Registries.PropRuntimeDeserializer() {
            @Override
            public <T> PropRuntime<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return WorldGenV2Registries.PROP_RUNTIME_TYPE.get("PropRuntime").deserialize(json, context, blockResolver);
            }
        });
        WorldGenV2Registries.PROP_RUNTIME_TYPE.register("Imported", new WorldGenV2Registries.PropRuntimeDeserializer() {
            @Override
            public <T> PropRuntime<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                String asset = getString(json, "name", getString(json, "asset", getString(json, "path", getString(json, "id", ""))));
                Object loaded = context.resolveAsset(asset);
                if (loaded != null) {
                    return compilePropRuntime(loaded, context, blockResolver);
                }
                return PropRuntime.of(1, new BlockProp<>(blockResolver.apply("minecraft:short_grass"), 1, true));
            }
        });
        WorldGenV2Registries.PROP_RUNTIME_TYPE.register("Exported", new WorldGenV2Registries.PropRuntimeDeserializer() {
            @Override
            public <T> PropRuntime<T> deserialize(Map<String, Object> json, CompileContext context, Function<String, T> blockResolver) {
                return compilePropRuntime(json.getOrDefault("node", json.get("runtime")), context, blockResolver);
            }
        });

        // Hytale's own props, patterns and scanners go in last so a Hytale name always means what Hytale says it means.
        HytaleDialect.register();
    }

    // =========================================================================
    // Public Compilation API
    // =========================================================================

    /**
     * Compiles a JSON string into a {@link DensityFunction} DAG and applies the AST optimizer pass.
     */
    @SuppressWarnings("unchecked")
    public DensityFunction compileDensityJson(String jsonContent, long worldSeed) {
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected JSON object for DensityFunction AST");
        }
        return compileDensity((Map<String, Object>) map, new CompileContext(worldSeed));
    }

    /**
     * Compiles a JSON string into a {@link Curve}.
     */
    public Curve compileCurveJson(String jsonContent, long worldSeed) {
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        return compileCurve(parsed, new CompileContext(worldSeed));
    }

    /**
     * Compiles a JSON string into a {@link VectorProvider}.
     */
    public VectorProvider compileVectorProviderJson(String jsonContent, long worldSeed) {
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        return compileVectorProvider(parsed, new CompileContext(worldSeed));
    }

    /**
     * Compiles a JSON string into a {@link BlockMask}.
     */
    public <T> BlockMask<T> compileBlockMaskJson(String jsonContent, long worldSeed, Function<String, T> blockResolver) {
        registerBuiltInTypes();
        CompileContext context = new CompileContext(worldSeed);
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        return compileBlockMask(parsed, context, blockResolver);
    }

    /**
     * Compiles a JSON string into a {@link Prop}.
     */
    @SuppressWarnings("unchecked")
    public <T> Prop<T> compilePropJson(String jsonContent, long worldSeed, Function<String, T> blockResolver) {
        registerBuiltInTypes();
        CompileContext context = new CompileContext(worldSeed);
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        if (parsed instanceof Map<?, ?> map) {
            return compileProp((Map<String, Object>) map, context, blockResolver);
        }
        throw new IllegalArgumentException("Expected JSON object for Prop definition");
    }

    /**
     * Compiles a JSON string into a {@link PropRuntime}.
     */
    @SuppressWarnings("unchecked")
    public <T> PropRuntime<T> compilePropRuntimeJson(String jsonContent, long worldSeed, Function<String, T> blockResolver) {
        registerBuiltInTypes();
        CompileContext context = new CompileContext(worldSeed);
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        return compilePropRuntime(parsed, context, blockResolver);
    }

    /**
     * Compiles a parsed JSON object map into an optimized {@link DensityFunction} DAG.
     */
    public static DensityFunction compileDensity(Map<String, Object> json, CompileContext context) {
        if (json == null) {
            return new ConstantDensity(0.0f);
        }
        String type = getString(json, "type", "ConstantDensity");
        HytaleDialect.adaptDensityInputs(type, json);
        WorldGenV2Registries.DensityNodeDeserializer deserializer =
            WorldGenV2Registries.DENSITY_NODE_TYPE.getOrThrow(type);
        boolean exported = false;
        for (String k : json.keySet()) if (k.equalsIgnoreCase("exportas") || k.equalsIgnoreCase("exportname")) { exported = true; break; }
        int drawsBefore = context.saltDraws();
        DensityFunction rawNode = deserializer.deserialize(json, context);
        DensityFunction node = optimizeDensity(rawNode);
        // Every copy of an exported node (inline, and each Imported) shares recent columns, when the copies are the same function.
        if (exported && !NO_SHARE && context.saltDraws() == drawsBefore) return new SharedColumnDensity(new SharedKey(context, json), node);
        return node;
    }

    /** Same compile context (world seed, constants) and equal JSON: the same function. */
    private record SharedKey(CompileContext context, Object json) {
        @Override public boolean equals(Object o) { return o instanceof SharedKey k && k.context == context && k.json.equals(json); }
        @Override public int hashCode() { return System.identityHashCode(context) * 31 + json.hashCode(); }
    }

    /** Column sharing between copies of an exported density is off unless WG2_SHARE=1: the copies the gradient and the terrain use do not meet yet. */
    private static final boolean NO_SHARE = System.getenv("WG2_SHARE") == null;

    /**
     * Compiles a parsed JSON object map into a {@link MaterialProvider}.
     */
    @SuppressWarnings("unchecked")
    public static <T> MaterialProvider<T> compileMaterialProvider(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) {
            return new ConstantMaterialProvider<>(blockResolver.apply("minecraft:stone"));
        }
        if (raw instanceof String str) {
            return new ConstantMaterialProvider<>(blockResolver.apply(str));
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", "SpaceAndDepth");
            WorldGenV2Registries.MaterialProviderDeserializer deserializer =
                WorldGenV2Registries.MATERIAL_PROVIDER_TYPE.getOrThrow(type);
            return deserializer.deserialize(json, context, blockResolver);
        }
        return new ConstantMaterialProvider<>(blockResolver.apply("minecraft:stone"));
    }

    /**
     * Compiles a parsed JSON object map into a {@link Pattern}.
     */
    public static <T> Pattern<T> compilePattern(
        Map<String, Object> json,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (json == null) {
            return new SurfacePattern<>();
        }
        String type = getString(json, "type", "SurfacePattern");
        WorldGenV2Registries.PatternDeserializer deserializer =
            WorldGenV2Registries.PATTERN_TYPE.getOrThrow(type);
        return deserializer.deserialize(json, context, blockResolver);
    }

    /**
     * Compiles a parsed JSON object map into a {@link Prop}.
     */
    public static <T> Prop<T> compileProp(
        Map<String, Object> json,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (json == null) {
            throw new IllegalArgumentException("Prop JSON definition cannot be null");
        }
        String type = getString(json, "type", "BlockProp");
        if (WorldGenV2Registries.PROP_RUNTIME_TYPE.contains(type)) {
            return compilePropRuntime(json, context, blockResolver);
        }
        WorldGenV2Registries.PropDeserializer deserializer =
            WorldGenV2Registries.PROP_TYPE.getOrThrow(type);
        return deserializer.deserialize(json, context, blockResolver);
    }

    /**
     * Compiles a parsed JSON object map or child prop into a {@link PropRuntime}.
     */
    @SuppressWarnings("unchecked")
    public static <T> PropRuntime<T> compilePropRuntime(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) {
            throw new IllegalArgumentException("PropRuntime JSON definition cannot be null");
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "PropRuntime"));
            if (WorldGenV2Registries.PROP_RUNTIME_TYPE.contains(type)) {
                return WorldGenV2Registries.PROP_RUNTIME_TYPE.get(type).deserialize(json, context, blockResolver);
            }
            Prop<T> prop = compileProp(json, context, blockResolver);
            return PropRuntime.of(1, prop);
        }
        throw new IllegalArgumentException("Expected JSON object for PropRuntime definition: " + raw);
    }

    /**
     * Compiles a parsed JSON object into a {@link Curve}.
     */
    @SuppressWarnings("unchecked")
    public static Curve compileCurve(Object raw, CompileContext context) {
        if (raw == null) {
            return new ConstantCurve(0.0);
        }
        if (raw instanceof Number n) {
            return new ConstantCurve(n.doubleValue());
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "ConstantCurve"));
            WorldGenV2Registries.CurveDeserializer deserializer =
                WorldGenV2Registries.CURVE_TYPE.getOrThrow(type);
            return deserializer.deserialize(json, context);
        }
        return new ConstantCurve(0.0);
    }

    /**
     * Compiles a parsed JSON object into a {@link VectorProvider}.
     */
    @SuppressWarnings("unchecked")
    public static VectorProvider compileVectorProvider(Object raw, CompileContext context) {
        if (raw == null) {
            return new ConstantVectorProvider(Vec3d.ZERO);
        }
        if (raw instanceof List<?> l && l.size() >= 3) {
            return new ConstantVectorProvider(
                ((Number) l.get(0)).doubleValue(),
                ((Number) l.get(1)).doubleValue(),
                ((Number) l.get(2)).doubleValue()
            );
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "ConstantVectorProvider"));
            WorldGenV2Registries.VectorProviderDeserializer deserializer =
                WorldGenV2Registries.VECTOR_PROVIDER_TYPE.getOrThrow(type);
            return deserializer.deserialize(json, context);
        }
        return new ConstantVectorProvider(Vec3d.ZERO);
    }

    /**
     * Compiles a parsed JSON object into a {@link PositionsProvider}.
     */
    @SuppressWarnings("unchecked")
    public static PositionsProvider compilePositionsProvider(Object raw, CompileContext context) {
        if (raw == null) {
            return new SquareGrid2dPositionsProvider(1.0);
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "SquareGrid2dPositionsProvider"));
            WorldGenV2Registries.PositionsProviderDeserializer deserializer =
                WorldGenV2Registries.POSITIONS_PROVIDER_TYPE.getOrThrow(type);
            return deserializer.deserialize(json, context);
        }
        return new SquareGrid2dPositionsProvider(1.0);
    }

    /**
     * Compiles a parsed JSON object into a {@link Scanner}.
     */
    @SuppressWarnings("unchecked")
    public static Scanner compileScanner(Object raw, CompileContext context) {
        if (raw == null) {
            return OriginScanner.INSTANCE;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "OriginScanner"));
            WorldGenV2Registries.ScannerDeserializer deserializer =
                WorldGenV2Registries.SCANNER_TYPE.getOrThrow(type);
            return deserializer.deserialize(json, context);
        }
        return OriginScanner.INSTANCE;
    }

    /**
     * Compiles a parsed JSON object into an {@link Assignments}.
     */
    @SuppressWarnings("unchecked")
    public static <T> Assignments<T> compileAssignments(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) {
            throw new IllegalArgumentException("Assignments JSON definition cannot be null");
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "ConstantAssignment"));
            WorldGenV2Registries.AssignmentsDeserializer deserializer =
                WorldGenV2Registries.ASSIGNMENTS_TYPE.getOrThrow(type);
            return deserializer.deserialize(json, context, blockResolver);
        }
        throw new IllegalArgumentException("Expected JSON object for Assignments definition");
    }

    /**
     * Compiles a parsed JSON object into a {@link BlockMask}.
     */
    @SuppressWarnings("unchecked")
    public static <T> BlockMask<T> compileBlockMask(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) {
            return BlockMask.always();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "AdvancedBlockMask"));
            WorldGenV2Registries.BlockMaskDeserializer deserializer =
                WorldGenV2Registries.BLOCK_MASK_TYPE.getOrThrow(type);
            return deserializer.deserialize(json, context, blockResolver);
        }
        return BlockMask.always();
    }

    /** A Manual pass's edges are as long as its nodes are far apart, so a cell must see that far to find both ends. */
    private static double longestManualEdge(Map<?, ?> pass) {
        Object nodes = null;
        for (Map.Entry<?, ?> e : pass.entrySet()) if (String.valueOf(e.getKey()).equalsIgnoreCase("nodes")) nodes = e.getValue();
        if (!(nodes instanceof List<?> list)) return 0.0;
        Map<String, double[]> at = new java.util.HashMap<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> n)) continue;
            Object name = null, pos = null;
            for (Map.Entry<?, ?> e : n.entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                if (k.equals("name")) name = e.getValue(); else if (k.equals("position")) pos = e.getValue();
            }
            if (name != null && pos instanceof Map<?, ?> pm) {
                double[] v = new double[3];
                for (Map.Entry<?, ?> e : pm.entrySet()) {
                    String k = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                    if (e.getValue() instanceof Number num) { if (k.equals("x")) v[0] = num.doubleValue(); else if (k.equals("y")) v[1] = num.doubleValue(); else if (k.equals("z")) v[2] = num.doubleValue(); }
                }
                at.put(String.valueOf(name), v);
            }
        }
        double max = 0.0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> n)) continue;
            Object name = null, conns = null;
            for (Map.Entry<?, ?> e : n.entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                if (k.equals("name")) name = e.getValue(); else if (k.equals("connections")) conns = e.getValue();
            }
            double[] a = name == null ? null : at.get(String.valueOf(name));
            if (a != null && conns instanceof List<?> cl) for (Object c : cl) {
                double[] b = at.get(String.valueOf(c));
                if (b != null) max = Math.max(max, Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])));
            }
        }
        return max;
    }
    // ---------------------------------------------------------------- exports shared across families

    /**
     * Hytale lets a density, a pattern and an assignments asset share one export name, because each family has its own namespace.
     * The node that asks says which family it wants; of the exports with that name this picks the one that fits.
     */
    static String familyGuess(Map<?, ?> m) {
        String type = null;
        java.util.Set<String> k = new java.util.HashSet<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            k.add(key);
            if (key.equals("type")) type = String.valueOf(e.getValue());
        }
        if (type == null) return null;
        switch (type) {
            case "Constant":
                if (k.contains("prop") && k.contains("positions")) return "propdistribution";
                if (k.contains("prop")) return "assignments";
                if (k.contains("material")) return "material";
                if (k.contains("value")) return "density";
                return null;
            case "Weighted":
                if (k.contains("weightedassignments")) return "assignments";
                if (k.contains("weightedprops")) return "prop";
                if (k.contains("weightedmaterials")) return "material";
                return null;
            case "FieldFunction": {
                if (m.get("Delimiters") instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> d) {
                    for (Object dk : d.keySet()) {
                        String s = String.valueOf(dk).toLowerCase(Locale.ROOT);
                        if (s.equals("assignments")) return "assignments";
                        if (s.equals("material")) return "material";
                    }
                }
                return k.contains("positions") ? "positions" : "pattern";
            }
            case "Floor": case "Ceiling": case "Wall": case "Surface": case "BlockSet": case "BlockType": return "pattern";
            case "ColumnLinear": case "ColumnRandom": case "Area": case "Direct": case "Origin": case "Radial": return "scanner";
            case "Column": case "Cluster": case "Box": case "Locator": case "Orienter": case "Prefab": case "StaticRotator": case "RandomRotator": return "prop";
            case "Mesh2D": case "Mesh3D": case "Scaler": case "Jitter2d": case "Jitter3d": case "List": case "SquareGrid2d": case "SquareGrid3d": case "TriangularGrid2d": return "positions";
            default: break;
        }
        if (k.contains("curves")) return "curve";
        if (k.contains("vectors")) return "vector";
        if (k.contains("props")) return "prop";
        if (k.contains("patterns")) return "pattern";
        if (k.contains("inputs")) return "density";
        return null;
    }
    /** The sum of every cap (Range, Magnitude, MaxDistance, MaxOffsetExclusive, MaxProxyDistance, ClusterBounds) in a graph: how far a pass can reach. */
    static double contextMarginOf(Object raw) {
        double sum = 0.0;
        if (raw instanceof Map<?, ?> m) {
            sum += longestManualEdge(m);
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
                if (e.getValue() instanceof Number n && (k.equals("range") || k.equals("magnitude") || k.equals("maxdistance") || k.equals("maxoffsetexclusive") || k.equals("maxproxydistance"))) {
                    if (!k.equals("range") || m.containsKey("Cap") || m.containsKey("cap") || m.containsKey("NodeSelector") || m.containsKey("nodeSelector")) sum += Math.abs(n.doubleValue());
                } else if (k.equals("clusterbounds")) {
                    double[][] b = HytaleDialect.boundsD(e.getValue(), 0.0);
                    for (int i = 0; i < 3; i++) sum += Math.max(Math.abs(b[0][i]), Math.abs(b[1][i]));
                } else {
                    sum += contextMarginOf(e.getValue());
                }
            }
        } else if (raw instanceof List<?> l) {
            for (Object o : l) sum += contextMarginOf(o);
        }
        return sum;
    }

    /** Hytale's common {@code Skip} flag: the node does nothing and falls back to its neutral default. */
    static boolean isSkipped(Map<?, ?> json) {
        for (Map.Entry<?, ?> e : json.entrySet()) {
            if (String.valueOf(e.getKey()).equalsIgnoreCase("skip")) return Boolean.TRUE.equals(e.getValue()) || "true".equalsIgnoreCase(String.valueOf(e.getValue()));
        }
        return false;
    }

    /** Records every node that carries {@code ExportAs} (or ExportName) so an Imported node can find it by Name. */
    @SuppressWarnings("unchecked")
    public static void collectExports(Object raw, CompileContext context) {
        if (raw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                if ((k.equalsIgnoreCase("exportas") || k.equalsIgnoreCase("exportname")) && e.getValue() instanceof String s && !s.isEmpty()) {
                    context.addExport(s, raw);
                }
                collectExports(e.getValue(), context);
            }
        } else if (raw instanceof List<?> l) {
            for (Object o : l) collectExports(o, context);
        }
    }
    /**
     * Compiles a parsed JSON object into a {@link GraphGenerator}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GraphGenerator compileGraphGenerator(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) {
            return new GraphGenerator(List.of());
        }
        collectExports(raw, context);
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String importName = getString(json, "importName", getString(json, "ImportName", ""));
            if (!importName.isEmpty()) {
                Object imported = context.resolveAsset(importName);
                if (imported != null) {
                    return compileGraphGenerator(imported, context, blockResolver);
                }
            }
            boolean printStats = getBoolean(json, "printStats", getBoolean(json, "PrintStats", false));
            String statsLabel = getString(json, "statsLabel", getString(json, "StatsLabel", ""));
            int statsInterval = getInt(json, "statsPrintInterval", getInt(json, "StatsPrintInterval", 0));
            String exportName = getString(json, "exportName", getString(json, "ExportName", ""));

            List<GraphPass> passes = new ArrayList<>();
            Object rawPasses = json.getOrDefault("passes", json.get("Passes"));
            if (rawPasses instanceof List<?> list) {
                for (Object item : list) {
                    passes.add(compileGraphPass(item, context, blockResolver));
                }
            }
            Vec3d cellSize = GraphGenerator.DEFAULT_CELL_SIZE;
            Object rawCell = json.getOrDefault("cacheCellSize", json.get("CacheCellSize"));
            if (rawCell instanceof Map<?, ?> cMap) {
                cellSize = new Vec3d(
                    getDouble((Map<String, Object>) cMap, "x", getDouble((Map<String, Object>) cMap, "X", 320.0)),
                    getDouble((Map<String, Object>) cMap, "y", getDouble((Map<String, Object>) cMap, "Y", 320.0)),
                    getDouble((Map<String, Object>) cMap, "z", getDouble((Map<String, Object>) cMap, "Z", 320.0))
                );
            }
            int capacity = getInt(json, "cacheCapacity", getInt(json, "CacheCapacity", GraphGenerator.DEFAULT_CAPACITY));
            return new GraphGenerator(passes, cellSize, capacity).withContextMargin(contextMarginOf(json));
        }
        return new GraphGenerator(List.of());
    }

    /**
     * Compiles a parsed JSON object into a {@link GraphPass}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GraphPass compileGraphPass(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) return EmptyGraphPass.INSTANCE;
        if (raw instanceof Map<?, ?> skipCheck && isSkipped(skipCheck)) return EmptyGraphPass.INSTANCE;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "Empty"));
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "positions" -> new PositionsGraphPass(
                    compilePositionsProvider(json.getOrDefault("positions", json.get("Positions")), context),
                    compileContentSupplier(json.getOrDefault("content", json.get("Content")), context, blockResolver)
                );
                case "nodeaction" -> new NodeActionPass(
                    compileNodeAction(json.getOrDefault("nodeAction", json.get("NodeAction")), context, blockResolver),
                    json.containsKey("nodeSelector") || json.containsKey("NodeSelector")
                        ? compileNodeSelector(json.getOrDefault("nodeSelector", json.get("NodeSelector")), context)
                        : AllNodeSelector.INSTANCE
                );
                case "edgeaction" -> new EdgeActionPass(
                    compileEdgeAction(json.getOrDefault("edgeAction", json.get("EdgeAction")), context, blockResolver),
                    json.containsKey("edgeSelector") || json.containsKey("EdgeSelector")
                        ? compileEdgeSelector(json.getOrDefault("edgeSelector", json.get("EdgeSelector")), context)
                        : AllEdgeSelector.INSTANCE
                );
                case "manual" -> {
                    List<ManualGraphPass.ManualNode> mNodes = new ArrayList<>();
                    Object rawNodes = json.getOrDefault("nodes", json.get("Nodes"));
                    if (rawNodes instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof Map<?, ?> nm) {
                                Map<String, Object> nJson = (Map<String, Object>) nm;
                                Vec3d pos = Vec3d.ZERO;
                                Object rawPos = nJson.getOrDefault("position", nJson.get("Position"));
                                if (rawPos instanceof Map<?, ?> pm) {
                                    pos = new Vec3d(
                                        getDouble((Map<String, Object>) pm, "x", getDouble((Map<String, Object>) pm, "X", 0.0)),
                                        getDouble((Map<String, Object>) pm, "y", getDouble((Map<String, Object>) pm, "Y", 0.0)),
                                        getDouble((Map<String, Object>) pm, "z", getDouble((Map<String, Object>) pm, "Z", 0.0))
                                    );
                                }
                                GraphContent content = compileGraphContent(nJson.getOrDefault("content", nJson.get("Content")), context, blockResolver);
                                String name = getString(nJson, "name", getString(nJson, "Name", ""));
                                List<String> conns = new ArrayList<>();
                                Object rawConns = nJson.getOrDefault("connections", nJson.get("Connections"));
                                if (rawConns instanceof List<?> cList) {
                                    for (Object c : cList) {
                                        if (c != null) conns.add(c.toString());
                                    }
                                }
                                mNodes.add(new ManualGraphPass.ManualNode(pos, content, name, conns));
                            }
                        }
                    }
                    yield new ManualGraphPass(mNodes);
                }
                case "imported" -> compileGraphPass(context.resolveAsset(getString(json, "name", getString(json, "asset", ""))), context, blockResolver);
                default -> EmptyGraphPass.INSTANCE;
            };
        }
        return EmptyGraphPass.INSTANCE;
    }

    /**
     * Compiles a parsed JSON object into a {@link GraphContent}.
     */
    @SuppressWarnings("unchecked")
    public static <T> GraphContent compileGraphContent(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) return GraphContent.EMPTY;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String importName = getString(json, "importName", getString(json, "ImportName", ""));
            if (!importName.isEmpty()) {
                Object imported = context.resolveAsset(importName);
                if (imported != null) {
                    return compileGraphContent(imported, context, blockResolver);
                }
            }
            GraphContent.Builder builder = new GraphContent.Builder();
            String exportName = getString(json, "exportName", getString(json, "ExportName", ""));
            if (!exportName.isEmpty()) {
                builder.addTag("export:" + exportName);
            }

            Object dList = json.getOrDefault("densityContent", json.get("DensityContent"));
            if (dList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> entry = (Map<String, Object>) m;
                        DensityFunction df = compileDensity((Map<String, Object>) entry.getOrDefault("density", entry.get("Density")), context);
                        double range = getDouble(entry, "range", getDouble(entry, "Range", 10.0));
                        String layer = getString(entry, "contentLayer", getString(entry, "ContentLayer", ""));
                        builder.addDensity(df, range, layer);
                    }
                }
            }

            Object mList = json.getOrDefault("materialContent", json.get("MaterialContent"));
            if (mList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> entry = (Map<String, Object>) m;
                        MaterialProvider<T> mp = compileMaterialProvider((Map<String, Object>) entry.getOrDefault("materialProvider", entry.get("MaterialProvider")), context, blockResolver);
                        double range = getDouble(entry, "range", getDouble(entry, "Range", 10.0));
                        String layer = getString(entry, "contentLayer", getString(entry, "ContentLayer", ""));
                        builder.addMaterial(mp, range, layer);
                    }
                }
            }

            Object pList = json.getOrDefault("propDistributionContent", json.get("PropDistributionContent"));
            if (pList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> entry = (Map<String, Object>) m;
                        Prop<T> p = new com.worldgenv2.hytale.HDistributions.FieldProp<>(HytaleDialect.compileDistribution(entry.getOrDefault("propDistribution", entry.get("PropDistribution")), context, blockResolver));
                        double range = getDouble(entry, "range", getDouble(entry, "Range", 10.0));
                        String layer = getString(entry, "contentLayer", getString(entry, "ContentLayer", ""));
                        builder.addProp(p, range, layer);
                    }
                }
            }

            Object posList = json.getOrDefault("positionsContent", json.get("PositionsContent"));
            if (posList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        Map<String, Object> entry = (Map<String, Object>) m;
                        PositionsProvider pp = compilePositionsProvider(entry.getOrDefault("positions", entry.get("Positions")), context);
                        double range = getDouble(entry, "range", getDouble(entry, "Range", 10.0));
                        String layer = getString(entry, "contentLayer", getString(entry, "ContentLayer", ""));
                        builder.addPositions(pp, range, layer);
                    }
                }
            }

            Object tags = json.getOrDefault("contentTags", json.get("ContentTags"));
            if (tags instanceof List<?> tList) {
                for (Object t : tList) {
                    if (t != null) builder.addTag(t.toString());
                }
            }
            return builder.build();
        }
        return GraphContent.EMPTY;
    }

    /**
     * Compiles a parsed JSON object into a {@link NodeSelector}.
     */
    @SuppressWarnings("unchecked")
    public static NodeSelector compileNodeSelector(Object raw, CompileContext context) {
        if (raw == null) return AllNodeSelector.INSTANCE;
        if (raw instanceof Map<?, ?> skipCheck && isSkipped(skipCheck)) return AllNodeSelector.INSTANCE;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "All"));
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "all" -> AllNodeSelector.INSTANCE;
                case "connectioncount" -> {
                    Set<Integer> counts = new LinkedHashSet<>();
                    Object rawCounts = json.getOrDefault("counts", json.get("Counts"));
                    if (rawCounts instanceof List<?> list) {
                        for (Object c : list) {
                            if (c instanceof Number n) counts.add(n.intValue());
                        }
                    }
                    yield new ConnectionCountNodeSelector(counts);
                }
                case "content" -> new ContentNodeSelector(
                    compileContentPredicate(json.getOrDefault("contentPredicate", json.get("ContentPredicate")), context)
                );
                case "densitydelimited" -> {
                    DensityFunction df = compileDensity((Map<String, Object>) json.getOrDefault("density", json.get("Density")), context);
                    List<DoubleRange> delimiters = compileDoubleRanges(json.getOrDefault("delimiters", json.get("Delimiters")));
                    yield new DensityDelimitedNodeSelector(df, delimiters);
                }
                case "neighbornodes" -> new NeighborNodesNodeSelector(
                    compileNodeSelector(json.getOrDefault("nodeSelector", json.get("NodeSelector")), context),
                    getInt(json, "selectedNeighborsThreshold", getInt(json, "SelectedNeighborsThreshold", -1))
                );
                case "neighboredges" -> new NeighborEdgesNodeSelector(
                    compileEdgeSelector(json.getOrDefault("edgeSelector", json.get("EdgeSelector")), context),
                    getInt(json, "selectedNeighborsThreshold", getInt(json, "SelectedNeighborsThreshold", -1))
                );
                case "random" -> new RandomNodeSelector(
                    getDouble(json, "chance", getDouble(json, "Chance", 0.5)),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "not" -> new NotNodeSelector(
                    compileNodeSelector(json.getOrDefault("nodeSelector", json.get("NodeSelector")), context)
                );
                case "and" -> {
                    List<NodeSelector> children = new ArrayList<>();
                    Object rawList = json.getOrDefault("nodeSelectors", json.get("NodeSelectors"));
                    if (rawList instanceof List<?> list) {
                        for (Object o : list) children.add(compileNodeSelector(o, context));
                    }
                    yield new AndNodeSelector(children);
                }
                case "or" -> {
                    List<NodeSelector> children = new ArrayList<>();
                    Object rawList = json.getOrDefault("nodeSelectors", json.get("NodeSelectors"));
                    if (rawList instanceof List<?> list) {
                        for (Object o : list) children.add(compileNodeSelector(o, context));
                    }
                    yield new OrNodeSelector(children);
                }
                case "imported" -> compileNodeSelector(context.resolveAsset(getString(json, "name", getString(json, "asset", ""))), context);
                default -> AllNodeSelector.INSTANCE;
            };
        }
        return AllNodeSelector.INSTANCE;
    }

    /**
     * Compiles a parsed JSON object into an {@link EdgeSelector}.
     */
    @SuppressWarnings("unchecked")
    public static EdgeSelector compileEdgeSelector(Object raw, CompileContext context) {
        if (raw == null) return AllEdgeSelector.INSTANCE;
        if (raw instanceof Map<?, ?> skipCheck && isSkipped(skipCheck)) return AllEdgeSelector.INSTANCE;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "All"));
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "all" -> AllEdgeSelector.INSTANCE;
                case "angle" -> {
                    VectorProvider ref = compileVectorProvider(json.getOrDefault("reference", json.get("Reference")), context);
                    List<DoubleRange> delimiters = compileDoubleRanges(json.getOrDefault("delimiters", json.get("Delimiters")));
                    yield new AngleEdgeSelector(ref, delimiters);
                }
                case "length" -> {
                    List<DoubleRange> delimiters = compileDoubleRanges(json.getOrDefault("delimiters", json.get("Delimiters")));
                    yield new LengthEdgeSelector(delimiters);
                }
                case "nodes" -> {
                    NodeSelector ns = compileNodeSelector(json.getOrDefault("nodeSelector", json.get("NodeSelector")), context);
                    String op = getString(json, "operator", getString(json, "Operator", "AND"));
                    NodesEdgeSelector.Operator operator = "OR".equalsIgnoreCase(op) ? NodesEdgeSelector.Operator.OR : NodesEdgeSelector.Operator.AND;
                    yield new NodesEdgeSelector(ns, operator);
                }
                case "random" -> new RandomEdgeSelector(
                    getDouble(json, "chance", getDouble(json, "Chance", 0.5)),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "not" -> new NotEdgeSelector(
                    compileEdgeSelector(json.getOrDefault("edgeSelector", json.get("EdgeSelector")), context)
                );
                case "and" -> {
                    List<EdgeSelector> children = new ArrayList<>();
                    Object rawList = json.getOrDefault("edgeSelectors", json.get("EdgeSelectors"));
                    if (rawList instanceof List<?> list) {
                        for (Object o : list) children.add(compileEdgeSelector(o, context));
                    }
                    yield new AndEdgeSelector(children);
                }
                case "or" -> {
                    List<EdgeSelector> children = new ArrayList<>();
                    Object rawList = json.getOrDefault("edgeSelectors", json.get("EdgeSelectors"));
                    if (rawList instanceof List<?> list) {
                        for (Object o : list) children.add(compileEdgeSelector(o, context));
                    }
                    yield new OrEdgeSelector(children);
                }
                case "imported" -> compileEdgeSelector(context.resolveAsset(getString(json, "name", getString(json, "asset", ""))), context);
                default -> AllEdgeSelector.INSTANCE;
            };
        }
        return AllEdgeSelector.INSTANCE;
    }

    /**
     * Compiles a delimiter range array into a list of {@link DoubleRange}.
     */
    @SuppressWarnings("unchecked")
    public static List<DoubleRange> compileDoubleRanges(Object raw) {
        List<DoubleRange> ranges = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> map = (Map<String, Object>) m;
                    double min = getDouble(map, "min", getDouble(map, "Min", Double.NEGATIVE_INFINITY));
                    double max = getDouble(map, "max", getDouble(map, "Max", Double.POSITIVE_INFINITY));
                    ranges.add(DoubleRange.of(min, max));
                } else if (item instanceof Number n) {
                    ranges.add(DoubleRange.of(n.doubleValue(), n.doubleValue()));
                }
            }
        }
        return ranges.isEmpty() ? List.of(DoubleRange.all()) : ranges;
    }

    /**
     * Compiles a parsed JSON object into a {@link ContentSupplier}.
     */
    @SuppressWarnings("unchecked")
    public static <T> ContentSupplier compileContentSupplier(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) return (n, s) -> GraphContent.EMPTY;
        if (raw instanceof Map<?, ?> skipCheck && isSkipped(skipCheck)) return (n, s) -> GraphContent.EMPTY;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "Constant"));
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "constant" -> new ConstantContentSupplier(
                    compileGraphContent(json.getOrDefault("content", json.get("Content")), context, blockResolver)
                );
                case "weighted" -> {
                    List<WeightedContentSupplier.Entry> entries = new ArrayList<>();
                    Object rawEntries = json.getOrDefault("entries", json.get("Entries"));
                    if (rawEntries instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof Map<?, ?> em) {
                                Map<String, Object> eMap = (Map<String, Object>) em;
                                double weight = getDouble(eMap, "weight", getDouble(eMap, "Weight", 1.0));
                                ContentSupplier cs = compileContentSupplier(eMap.getOrDefault("contentSupplier", eMap.get("ContentSupplier")), context, blockResolver);
                                entries.add(new WeightedContentSupplier.Entry(weight, cs));
                            }
                        }
                    }
                    yield new WeightedContentSupplier(entries.isEmpty() ? List.of(new WeightedContentSupplier.Entry(1.0, (n, s) -> GraphContent.EMPTY)) : entries);
                }
                case "imported" -> compileContentSupplier(context.resolveAsset(getString(json, "name", getString(json, "asset", ""))), context, blockResolver);
                default -> new ConstantContentSupplier(compileGraphContent(json, context, blockResolver));
            };
        }
        return (n, s) -> GraphContent.EMPTY;
    }

    /**
     * Compiles a parsed JSON object into a {@link ContentPredicate}.
     */
    @SuppressWarnings("unchecked")
    public static ContentPredicate compileContentPredicate(Object raw, CompileContext context) {
        if (raw == null) return (c) -> true;
        if (raw instanceof Map<?, ?> skipCheck && isSkipped(skipCheck)) return (c) -> true;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "Constant"));
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "constant" -> new ConstantContentPredicate(
                    getBoolean(json, "value", getBoolean(json, "Value", true))
                );
                case "set" -> {
                    List<String> tags = new ArrayList<>();
                    Object rawTags = json.getOrDefault("contentTags", json.get("ContentTags"));
                    if (rawTags instanceof List<?> list) {
                        for (Object t : list) {
                            if (t != null) tags.add(t.toString());
                        }
                    }
                    yield new SetContentPredicate(tags);
                }
                case "not" -> new NotContentPredicate(
                    compileContentPredicate(json.getOrDefault("contentPredicate", json.get("ContentPredicate")), context)
                );
                case "and" -> {
                    List<ContentPredicate> children = new ArrayList<>();
                    Object rawList = json.getOrDefault("contentPredicates", json.get("ContentPredicates"));
                    if (rawList instanceof List<?> list) {
                        for (Object o : list) children.add(compileContentPredicate(o, context));
                    }
                    yield new AndContentPredicate(children);
                }
                case "or" -> {
                    List<ContentPredicate> children = new ArrayList<>();
                    Object rawList = json.getOrDefault("contentPredicates", json.get("ContentPredicates"));
                    if (rawList instanceof List<?> list) {
                        for (Object o : list) children.add(compileContentPredicate(o, context));
                    }
                    yield new OrContentPredicate(children);
                }
                case "imported" -> compileContentPredicate(context.resolveAsset(getString(json, "name", getString(json, "asset", ""))), context);
                default -> (c) -> true;
            };
        }
        return (c) -> true;
    }

    /**
     * Compiles a parsed JSON object into a {@link NodeAction}.
     */
    @SuppressWarnings("unchecked")
    public static <T> NodeAction compileNodeAction(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) return EmptyNodeAction.INSTANCE;
        if (raw instanceof Map<?, ?> skipCheck && isSkipped(skipCheck)) return EmptyNodeAction.INSTANCE;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "Empty"));
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "content" -> new ContentNodeAction(
                    compileContentSupplier(json.getOrDefault("content", json.get("Content")), context, blockResolver)
                );
                case "contentcopy" -> new ContentCopyNodeAction(
                    compileContentPredicate(json.getOrDefault("contentPredicate", json.get("ContentPredicate")), context),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "delete" -> DeleteNodeAction.INSTANCE;
                case "empty" -> EmptyNodeAction.INSTANCE;
                case "jitter2d" -> new Jitter2dNodeAction(
                    getDouble(json, "magnitude", getDouble(json, "Magnitude", 5.0)),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "jitter3d" -> new Jitter3dNodeAction(
                    getDouble(json, "magnitude", getDouble(json, "Magnitude", 5.0)),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "move" -> new MoveNodeAction(
                    compileVectorProvider(json.getOrDefault("vector", json.get("Vector")), context),
                    getDouble(json, "maxDistance", getDouble(json, "MaxDistance", 50.0))
                );
                case "proximityconnector" -> new ProximityConnectorNodeAction(
                    getDouble(json, "range", getDouble(json, "Range", 50.0)),
                    compileNodeSelector(json.getOrDefault("nodeSelector", json.get("NodeSelector")), context),
                    getInt(json, "cap", getInt(json, "Cap", -1)),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "connectednodes" -> new ConnectedNodesNodeAction(
                    compileNodeAction(json.getOrDefault("nodeAction", json.get("NodeAction")), context, blockResolver),
                    getDouble(json, "ratio", getDouble(json, "Ratio", 1.0)),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "connectededges" -> new ConnectedEdgesNodeAction(
                    compileEdgeAction(json.getOrDefault("edgeAction", json.get("EdgeAction")), context, blockResolver),
                    getDouble(json, "ratio", getDouble(json, "Ratio", 1.0)),
                    context.nextSeedSalt(getInt(json, "seed", 0))
                );
                case "selector" -> new SelectorNodeAction(
                    compileNodeSelector(json.getOrDefault("nodeSelector", json.get("NodeSelector")), context),
                    compileNodeAction(json.getOrDefault("nodeAction", json.get("NodeAction")), context, blockResolver)
                );
                case "spawner" -> {
                    VectorProvider off = compileVectorProvider(json.getOrDefault("offset", json.get("Offset")), context);
                    PositionsProvider pos = compilePositionsProvider(json.getOrDefault("positions", json.get("Positions")), context);
                    ContentSupplier cs = compileContentSupplier(json.getOrDefault("content", json.get("Content")), context, blockResolver);
                    double maxOff = getDouble(json, "maxOffsetExclusive", getDouble(json, "MaxOffsetExclusive", 100.0));
                    boolean createEdges = getBoolean(json, "createEdges", getBoolean(json, "CreateEdges", true));
                    double[][] cb = HytaleDialect.boundsD(json.getOrDefault("clusterBounds", json.get("ClusterBounds")), 50.0);
                    yield new SpawnerNodeAction(off, pos, cs, new GraphBounds(cb[0][0], cb[0][1], cb[0][2], cb[1][0], cb[1][1], cb[1][2]), maxOff, createEdges);
                }
                case "weighted" -> {
                    List<WeightedNodeAction.Entry> entries = new ArrayList<>();
                    Object rawEntries = json.getOrDefault("entries", json.get("Entries"));
                    if (rawEntries instanceof List<?> list) {
                        for (Object o : list) {
                            if (o instanceof Map<?, ?> em) {
                                Map<String, Object> eMap = (Map<String, Object>) em;
                                double weight = getDouble(eMap, "weight", getDouble(eMap, "Weight", 1.0));
                                NodeAction na = compileNodeAction(eMap.getOrDefault("nodeAction", eMap.get("NodeAction")), context, blockResolver);
                                entries.add(new WeightedNodeAction.Entry(weight, na));
                            }
                        }
                    }
                    yield new WeightedNodeAction(entries.isEmpty() ? List.of(new WeightedNodeAction.Entry(1.0, EmptyNodeAction.INSTANCE)) : entries, context.nextSeedSalt(getInt(json, "seed", 0)));
                }
                case "imported" -> compileNodeAction(context.resolveAsset(getString(json, "name", getString(json, "asset", ""))), context, blockResolver);
                default -> EmptyNodeAction.INSTANCE;
            };
        }
        return EmptyNodeAction.INSTANCE;
    }

    /**
     * Compiles a parsed JSON object into an {@link EdgeAction}.
     */
    @SuppressWarnings("unchecked")
    public static <T> EdgeAction compileEdgeAction(
        Object raw,
        CompileContext context,
        Function<String, T> blockResolver
    ) {
        if (raw == null) return EmptyEdgeAction.INSTANCE;
        if (raw instanceof Map<?, ?> skipCheck && isSkipped(skipCheck)) return EmptyEdgeAction.INSTANCE;
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> json = (Map<String, Object>) map;
            String type = getString(json, "type", getString(json, "Type", "Empty"));
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "delete" -> DeleteEdgeAction.INSTANCE;
                case "empty" -> EmptyEdgeAction.INSTANCE;
                case "nodes" -> new NodesEdgeAction(
                    compileNodeAction(json.getOrDefault("nodeAction", json.get("NodeAction")), context, blockResolver)
                );
                case "selector" -> new SelectorEdgeAction(
                    compileEdgeSelector(json.getOrDefault("edgeSelector", json.get("EdgeSelector")), context),
                    compileEdgeAction(json.getOrDefault("edgeAction", json.get("EdgeAction")), context, blockResolver)
                );
                case "splitter" -> new SplitterEdgeAction(
                    getInt(json, "nodeCount", getInt(json, "NodeCount", 1)),
                    compileContentSupplier(json.getOrDefault("content", json.get("Content")), context, blockResolver)
                );
                case "proxy" -> {
                    NodeSelector host = compileNodeSelector(json.getOrDefault("hostNodeSelector", json.get("HostNodeSelector")), context);
                    VectorProvider norm = compileVectorProvider(json.getOrDefault("normal", json.get("Normal")), context);
                    DensityFunction a2n = compileDensity((Map<String, Object>) json.getOrDefault("angleToNormal", json.get("AngleToNormal")), context);
                    DensityFunction spin = compileDensity((Map<String, Object>) json.getOrDefault("spinAngle", json.get("SpinAngle")), context);
                    DensityFunction pDist = compileDensity((Map<String, Object>) json.getOrDefault("proxyDistance", json.get("ProxyDistance")), context);
                    double maxDist = getDouble(json, "maxProxyDistance", getDouble(json, "MaxProxyDistance", 50.0));
                    ContentSupplier pContent = compileContentSupplier(json.getOrDefault("proxyContent", json.get("ProxyContent")), context, blockResolver);
                    yield new ProxyEdgeAction(host, norm, a2n, spin, pDist, maxDist, pContent, context.nextSeedSalt(getInt(json, "seed", 0)));
                }
                case "imported" -> compileEdgeAction(context.resolveAsset(getString(json, "name", getString(json, "asset", ""))), context, blockResolver);
                default -> EmptyEdgeAction.INSTANCE;
            };
        }
        return EmptyEdgeAction.INSTANCE;
    }

    /**
     * Compiles a complete Biome JSON asset (supporting both full Doc 07 node-graph syntax and
     * shorthand {@code terrainSettings} + {@code layers} + {@code CustomBiome} syntax).
     */
    @SuppressWarnings("unchecked")
    public static <T> CompiledBiome<T> compileBiome(
        int numericId,
        String jsonContent,
        CompileContext context,
        Map<String, Integer> biomeNameToId,
        Function<String, T> blockResolver
    ) {
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        if (!(parsed instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("Expected JSON object at root of biome asset");
        }
        return compileBiome(numericId, (Map<String, Object>) rootMap, context, biomeNameToId, blockResolver);
    }

    /** Compiles a biome that is already parsed (for example one read from Hytale's own files). */
    @SuppressWarnings("unchecked")
    public static <T> CompiledBiome<T> compileBiome(
        int numericId,
        Map<String, Object> root,
        CompileContext context,
        Map<String, Integer> biomeNameToId,
        Function<String, T> blockResolver
    ) {
        if (HytaleImport.isHytaleBiome(root)) root = HytaleImport.biomeToEngineForm(root);

        String name = getString(root, "name", "worldgenv2:unnamed_biome");
        String category = getString(root, "category", getString(root, "zone", "default"));
        String type = getString(root, "type", "TileBiome");
        float weight = getFloat(root, "weight", 10.0f);
        float sizeModifier = getFloat(root, "sizeModifier", 1.0f);
        float defaultTransitionDistance = getFloat(root, "defaultTransitionDistance", 48.0f);
        float maxSlopeForCover = getFloat(root, "maxSlopeForCover", 45.0f);
        int minSpaceForCover = getInt(root, "minSpaceForCover", 3);
        String fallbackMaterial = getString(root, "fallbackMaterial", "minecraft:stone");
        String cliffMaterial = getString(root, "cliffMaterial", fallbackMaterial);

        List<BiomeDefinition.LayerDefinition> layerDefs = new ArrayList<>();
        if (root.get("layers") instanceof List<?> rawLayers) {
            for (Object item : rawLayers) {
                if (item instanceof Map<?, ?> layerMap) {
                    Map<String, Object> lm = (Map<String, Object>) layerMap;
                    layerDefs.add(new BiomeDefinition.LayerDefinition(
                        getString(lm, "material", "minecraft:stone"),
                        getInt(lm, "thickness", 1)
                    ));
                }
            }
        }

        String defaultBlock = getString(root, "defaultBlock", fallbackMaterial);
        String fluidBlock = getString(root, "fluidBlock", null);
        Integer seaLevel = root.containsKey("seaLevel") ? getInt(root, "seaLevel", 63) : null;

        BiomeDefinition definition = new BiomeDefinition(
            name,
            category,
            type,
            weight,
            sizeModifier,
            defaultTransitionDistance,
            layerDefs,
            fallbackMaterial,
            cliffMaterial,
            maxSlopeForCover,
            minSpaceForCover,
            defaultBlock,
            fluidBlock,
            seaLevel
        );

        // 1. Compile Terrain Density DAG (either declarative "terrain" AST or shorthand "terrainSettings")
        DensityFunction terrainDensity;
        if (root.get("terrain") instanceof Map<?, ?> terrainAst) {
            terrainDensity = compileDensity((Map<String, Object>) terrainAst, context);
        } else if (root.get("terrainSettings") instanceof Map<?, ?> settingsMap) {
            terrainDensity = compileShorthandTerrain((Map<String, Object>) settingsMap, numericId, context);
        } else {
            terrainDensity = new VerticalGradientDensity(64.0f, 32.0f);
        }

        // 2. Compile MaterialProvider (either declarative "materialProvider" AST or shorthand "layers")
        MaterialProvider<T> materialProvider;
        if (root.get("materialProvider") instanceof Map<?, ?> matAst) {
            materialProvider = compileMaterialProvider((Map<String, Object>) matAst, context, blockResolver);
        } else if (!layerDefs.isEmpty()) {
            materialProvider = new BiomeFactory().createMaterialProvider(definition, blockResolver);
        } else {
            materialProvider = new ConstantMaterialProvider<>(blockResolver.apply(fallbackMaterial));
        }

        // 3. Compile Props list
        List<Prop<T>> props = new ArrayList<>();
        if (root.get("props") instanceof List<?> rawProps) {
            for (Object item : rawProps) {
                if (item instanceof Map<?, ?> propMap) {
                    Map<String, Object> pm = (Map<String, Object>) propMap;
                    if (HytaleDialect.isHytaleBiomeProp(pm)) {
                        Prop<T> hp = HytaleDialect.compileBiomeProp(pm, context, blockResolver);
                        if (hp != null) props.add(hp);
                    } else {
                        props.add(compileProp(pm, context, blockResolver));
                    }
                }
            }
        }

        // 4. Construct either CustomBiome or TileBiome
        if ("CustomBiome".equalsIgnoreCase(type)) {
            int priority = getInt(root, "priority", 10);
            float minThreshold = getFloat(root, "noiseThreshold", getFloat(root, "minNoiseThreshold", 0.25f));
            float maxThreshold = getFloat(root, "maxNoiseThreshold", 1.0f);
            float fadeMargin = getFloat(root, "fadeMargin", 0.10f);

            Set<Integer> parentIds = new LinkedHashSet<>();
            if (root.get("parentBiomes") instanceof List<?> parentList) {
                for (Object p : parentList) {
                    if (p instanceof Number n) {
                        parentIds.add(n.intValue());
                    } else if (p != null && biomeNameToId != null) {
                        String pName = String.valueOf(p);
                        Integer resolvedId = biomeNameToId.get(pName);
                        if (resolvedId == null && pName.contains(":")) {
                            resolvedId = biomeNameToId.get(pName.substring(pName.indexOf(':') + 1));
                        }
                        if (resolvedId != null) {
                            parentIds.add(resolvedId);
                        }
                    }
                }
            }

            DensityFunction placementNoise;
            if (root.get("placementNoise") instanceof Map<?, ?> pnMap) {
                placementNoise = compileDensity((Map<String, Object>) pnMap, context);
            } else {
                placementNoise = new Noise2dDensity(
                    context.nextSeedSalt(400 + numericId),
                    FastNoiseLite.NoiseType.OpenSimplex2,
                    getFloat(root, "placementFrequency", 0.003f),
                    2,
                    1.0f
                );
            }

            CustomBiome customBiome = new CustomBiome(
                numericId,
                name,
                priority,
                parentIds,
                minThreshold,
                maxThreshold,
                fadeMargin,
                placementNoise,
                terrainDensity,
                materialProvider,
                props
            );
            return new CompiledBiome<>(numericId, name, true, null, customBiome, definition);
        }

        TileBiome tileBiome = new TileBiome(
            numericId,
            name,
            weight,
            sizeModifier,
            defaultTransitionDistance,
            terrainDensity,
            materialProvider,
            props,
            null,
            defaultBlock,
            fluidBlock,
            seaLevel
        );
        return new CompiledBiome<>(numericId, name, false, tileBiome, null, definition);
    }

    /**
     * Convenience method to compile a {@link TileBiome} directly from JSON content.
     */
    public static <T> TileBiome compileTileBiome(
        int numericId,
        String jsonContent,
        long worldSeed,
        Function<String, T> blockResolver
    ) {
        CompiledBiome<T> compiled = compileBiome(
            numericId,
            jsonContent,
            new CompileContext(worldSeed),
            Map.of(),
            blockResolver
        );
        if (compiled.tileBiome() == null) {
            throw new IllegalArgumentException("Expected TileBiome JSON but got CustomBiome: " + compiled.name());
        }
        return compiled.tileBiome();
    }

    /**
     * Compiles a WorldStructure JSON configuration with BiCarta, Biomes, DefaultBiome, and SpawnPositions.
     */
    @SuppressWarnings("unchecked")
    public <T> com.worldgenv2.worldstructure.WorldStructure compileWorldStructure(
        String jsonContent,
        long worldSeed,
        Function<String, T> blockResolver
    ) {
        registerBuiltInTypes();
        CompileContext context = new CompileContext(worldSeed);
        Object parsed = new SimpleJsonParser(jsonContent).parseValue();
        if (!(parsed instanceof Map<?, ?> rootMap)) {
            throw new IllegalArgumentException("Expected JSON object for WorldStructure definition");
        }
        Map<String, Object> root = (Map<String, Object>) rootMap;

        // Extract DefaultBiome
        String defaultBiomeName = getString(root, "defaultBiome", getString(root, "DefaultBiome", "worldgenv2:plains"));
        TileBiome defaultBiome = new TileBiome(1, defaultBiomeName, 1.0f, 1.0f, 32.0f, new ConstantDensity(0.0f));

        // Extract Biomes
        List<TileBiome> tileBiomes = new ArrayList<>();
        tileBiomes.add(defaultBiome);
        Object rawBiomes = root.getOrDefault("biomes", root.get("Biomes"));
        if (rawBiomes instanceof List<?> list) {
            int id = 2;
            for (Object item : list) {
                if (item instanceof Map<?, ?> bm) {
                    Map<String, Object> bMap = (Map<String, Object>) bm;
                    String bName = getString(bMap, "name", "biome_" + id);
                    DensityFunction df = bMap.containsKey("terrain") || bMap.containsKey("density")
                        ? compileDensity((Map<String, Object>) bMap.getOrDefault("terrain", bMap.get("density")), context)
                        : new ConstantDensity(0.0f);
                    tileBiomes.add(new TileBiome(id++, bName, 1.0f, 1.0f, 32.0f, df));
                } else if (item != null) {
                    tileBiomes.add(new TileBiome(id++, item.toString(), 1.0f, 1.0f, 32.0f, new ConstantDensity(0.0f)));
                }
            }
        }

        return new com.worldgenv2.worldstructure.WorldStructure(
            tileBiomes,
            List.of(),
            1024,
            worldSeed
        );
    }

    // =========================================================================
    // AST Optimization Pass: Constant Folding & Affine Transform Collapsing
    // =========================================================================

    /**
     * Recursively optimizes a {@link DensityFunction} AST:
     * <ul>
     *   <li>Flattens nested {@link SumDensity} and {@link ProductDensity} nodes</li>
     *   <li>Folds constant inputs and prunes zero-multipliers ({@code Product(X, 0) -> 0})</li>
     *   <li>Eliminates identity constants ({@code Sum(X, 0) -> X}, {@code Product(X, 1) -> X})</li>
     *   <li>Collapses consecutive {@link OffsetDensity} nodes into a single translation vector</li>
     *   <li>Collapses consecutive {@link ScaleDensity} nodes into a single scale factor</li>
     *   <li>Eliminates double negations ({@code Invert(Invert(X)) -> X}) and redundant clamps</li>
     * </ul>
     */
    public static DensityFunction optimizeDensity(DensityFunction node) {
        if (node == null) {
            return new ConstantDensity(0.0f);
        }

        if (node instanceof OffsetDensity offset) {
            DensityFunction inner = optimizeDensity(offset.input());
            float dx = offset.offsetX();
            float dy = offset.offsetY();
            float dz = offset.offsetZ();

            if (inner instanceof ConstantDensity) {
                return inner;
            }
            if (inner instanceof OffsetDensity childOffset) {
                dx += childOffset.offsetX();
                dy += childOffset.offsetY();
                dz += childOffset.offsetZ();
                inner = childOffset.input();
            }
            if (Math.abs(dx) < 1e-6f && Math.abs(dy) < 1e-6f && Math.abs(dz) < 1e-6f) {
                return inner;
            }
            return new OffsetDensity(inner, dx, dy, dz);
        }

        if (node instanceof ScaleDensity scale) {
            DensityFunction inner = optimizeDensity(scale.input());
            float sx = scale.scaleX();
            float sy = scale.scaleY();
            float sz = scale.scaleZ();

            if (inner instanceof ConstantDensity) {
                return inner;
            }
            if (inner instanceof ScaleDensity childScale) {
                sx *= childScale.scaleX();
                sy *= childScale.scaleY();
                sz *= childScale.scaleZ();
                inner = childScale.input();
            }
            if (Math.abs(sx - 1.0f) < 1e-6f && Math.abs(sy - 1.0f) < 1e-6f && Math.abs(sz - 1.0f) < 1e-6f) {
                return inner;
            }
            return new ScaleDensity(inner, sx, sy, sz);
        }

        if (node instanceof InvertDensity inv) {
            DensityFunction inner = optimizeDensity(inv.input());
            if (inner instanceof ConstantDensity c) {
                return new ConstantDensity(-c.getValue());
            }
            if (inner instanceof InvertDensity doubleInv) {
                return doubleInv.input();
            }
            return new InvertDensity(inner);
        }

        if (node instanceof AbsDensity abs) {
            DensityFunction inner = optimizeDensity(abs.input());
            if (inner instanceof ConstantDensity c) {
                return new ConstantDensity(Math.abs(c.getValue()));
            }
            if (inner instanceof AbsDensity) {
                return inner;
            }
            return new AbsDensity(inner);
        }

        if (node instanceof ClampDensity clamp) {
            DensityFunction inner = optimizeDensity(clamp.input());
            if (inner instanceof ConstantDensity c) {
                return new ConstantDensity(Math.clamp(c.getValue(), clamp.min(), clamp.max()));
            }
            if (inner instanceof ClampDensity childClamp) {
                float mergedMin = Math.max(clamp.min(), childClamp.min());
                float mergedMax = Math.min(clamp.max(), childClamp.max());
                if (mergedMin <= mergedMax) {
                    return new ClampDensity(childClamp.input(), mergedMin, mergedMax);
                }
            }
            return new ClampDensity(inner, clamp.min(), clamp.max());
        }

        if (node instanceof NormalizerDensity norm) {
            DensityFunction inner = optimizeDensity(norm.input());
            if (inner instanceof ConstantDensity c) {
                NormalizerDensity folded = new NormalizerDensity(
                    c, norm.fromMin(), norm.fromMax(), norm.toMin(), norm.toMax()
                );
                return new ConstantDensity(folded.compute(0.0f, 0.0f, 0.0f));
            }
            return new NormalizerDensity(inner, norm.fromMin(), norm.fromMax(), norm.toMin(), norm.toMax());
        }

        if (node instanceof YSampledDensity ySampled) {
            DensityFunction inner = optimizeDensity(ySampled.getSource());
            if (inner instanceof ConstantDensity || inner instanceof Noise2dDensity) {
                return inner;
            }
            return new YSampledDensity(inner, ySampled.getYInterval());
        }

        if (node instanceof GradientWarpDensity warp) {
            DensityFunction source = optimizeDensity(warp.getSource());
            DensityFunction warpSource = optimizeDensity(warp.getWarpSource());
            if (Math.abs(warp.getWarpStrength()) < 1e-6f
                || warpSource instanceof ConstantDensity
                || source instanceof ConstantDensity) {
                return source;
            }
            return new GradientWarpDensity(source, warpSource, warp.getWarpStrength());
        }

        if (node instanceof SumDensity sum) {
            List<DensityFunction> nonConstant = new ArrayList<>();
            float constSum = 0.0f;

            for (DensityFunction rawChild : sum.getInputs()) {
                DensityFunction opt = optimizeDensity(rawChild);
                if (opt instanceof ConstantDensity c) {
                    constSum += c.getValue();
                } else if (opt instanceof SumDensity nestedSum) {
                    for (DensityFunction nestedChild : nestedSum.getInputs()) {
                        if (nestedChild instanceof ConstantDensity nc) {
                            constSum += nc.getValue();
                        } else {
                            nonConstant.add(nestedChild);
                        }
                    }
                } else {
                    nonConstant.add(opt);
                }
            }

            if (nonConstant.isEmpty()) {
                return new ConstantDensity(constSum);
            }
            if (Math.abs(constSum) > 1e-6f) {
                nonConstant.add(new ConstantDensity(constSum));
            }
            if (nonConstant.size() == 1) {
                return nonConstant.getFirst();
            }
            return new SumDensity(nonConstant);
        }

        if (node instanceof ProductDensity prod) {
            List<DensityFunction> nonConstant = new ArrayList<>();
            float constProd = 1.0f;

            for (DensityFunction rawChild : prod.getInputs()) {
                DensityFunction opt = optimizeDensity(rawChild);
                if (opt instanceof ConstantDensity c) {
                    constProd *= c.getValue();
                    if (Math.abs(constProd) < 1e-7f) {
                        // Multiply by zero: prune the entire product branch immediately
                        return new ConstantDensity(0.0f);
                    }
                } else if (opt instanceof ProductDensity nestedProd) {
                    for (DensityFunction nestedChild : nestedProd.getInputs()) {
                        if (nestedChild instanceof ConstantDensity nc) {
                            constProd *= nc.getValue();
                            if (Math.abs(constProd) < 1e-7f) {
                                return new ConstantDensity(0.0f);
                            }
                        } else {
                            nonConstant.add(nestedChild);
                        }
                    }
                } else {
                    nonConstant.add(opt);
                }
            }

            if (nonConstant.isEmpty()) {
                return new ConstantDensity(constProd);
            }
            if (Math.abs(constProd - 1.0f) > 1e-6f) {
                nonConstant.add(new ConstantDensity(constProd));
            }
            if (nonConstant.size() == 1) {
                return nonConstant.getFirst();
            }
            return new ProductDensity(nonConstant);
        }

        if (node instanceof MinDensity minNode) {
            List<DensityFunction> nonConstant = new ArrayList<>();
            float minConst = Float.POSITIVE_INFINITY;
            boolean hasConst = false;

            for (DensityFunction rawChild : minNode.getInputs()) {
                DensityFunction opt = optimizeDensity(rawChild);
                if (opt instanceof ConstantDensity c) {
                    minConst = Math.min(minConst, c.getValue());
                    hasConst = true;
                } else {
                    nonConstant.add(opt);
                }
            }
            if (nonConstant.isEmpty()) {
                return new ConstantDensity(hasConst ? minConst : 0.0f);
            }
            if (hasConst) {
                nonConstant.add(new ConstantDensity(minConst));
            }
            if (nonConstant.size() == 1) {
                return nonConstant.getFirst();
            }
            return new MinDensity(nonConstant);
        }

        if (node instanceof MaxDensity maxNode) {
            List<DensityFunction> nonConstant = new ArrayList<>();
            float maxConst = Float.NEGATIVE_INFINITY;
            boolean hasConst = false;

            for (DensityFunction rawChild : maxNode.getInputs()) {
                DensityFunction opt = optimizeDensity(rawChild);
                if (opt instanceof ConstantDensity c) {
                    maxConst = Math.max(maxConst, c.getValue());
                    hasConst = true;
                } else {
                    nonConstant.add(opt);
                }
            }
            if (nonConstant.isEmpty()) {
                return new ConstantDensity(hasConst ? maxConst : 0.0f);
            }
            if (hasConst) {
                nonConstant.add(new ConstantDensity(maxConst));
            }
            if (nonConstant.size() == 1) {
                return nonConstant.getFirst();
            }
            return new MaxDensity(nonConstant);
        }

        if (node instanceof SqrtDensity sqrt) {
            DensityFunction inner = optimizeDensity(sqrt.input());
            if (inner instanceof ConstantDensity c) {
                return new ConstantDensity((float) Math.sqrt(Math.max(0.0f, c.getValue())));
            }
            return new SqrtDensity(inner);
        }

        if (node instanceof PowDensity pow) {
            DensityFunction inner = optimizeDensity(pow.input());
            if (inner instanceof ConstantDensity c) {
                float v = c.getValue();
                return new ConstantDensity(v == 0.0f ? 0.0f : Math.signum(v) * (float) Math.pow(Math.abs(v), pow.exponent()));
            }
            return new PowDensity(inner, pow.exponent());
        }

        if (node instanceof MixDensity mix) {
            DensityFunction a = optimizeDensity(mix.a());
            DensityFunction b = optimizeDensity(mix.b());
            DensityFunction factor = optimizeDensity(mix.factor());
            if (factor instanceof ConstantDensity fc) {
                if (fc.getValue() <= 0.0f) return a;
                if (fc.getValue() >= 1.0f) return b;
            }
            if (a instanceof ConstantDensity ca && b instanceof ConstantDensity cb && factor instanceof ConstantDensity cf) {
                float t = Math.clamp(cf.getValue(), 0.0f, 1.0f);
                return new ConstantDensity(ca.getValue() + t * (cb.getValue() - ca.getValue()));
            }
            return new MixDensity(a, b, factor);
        }

        if (node instanceof SliderDensity slider) {
            DensityFunction inner = optimizeDensity(slider.input());
            if (inner instanceof ConstantDensity) {
                return inner;
            }
            if (inner instanceof SliderDensity childSlider) {
                return new SliderDensity(
                    childSlider.input(),
                    slider.offsetX() + childSlider.offsetX(),
                    slider.offsetY() + childSlider.offsetY(),
                    slider.offsetZ() + childSlider.offsetZ()
                );
            }
            return new SliderDensity(inner, slider.offsetX(), slider.offsetY(), slider.offsetZ());
        }

        if (node instanceof TrigDensity trig) {
            DensityFunction inner = optimizeDensity(trig.input());
            if (inner instanceof ConstantDensity c) {
                float val = c.getValue();
                float arg = trig.frequency() * val + trig.phase();
                float res = switch (trig.function()) {
                    case SIN -> (float) Math.sin(arg);
                    case COS -> (float) Math.cos(arg);
                    case TAN -> (float) Math.tan(arg);
                };
                return new ConstantDensity(trig.amplitude() * res);
            }
            return new TrigDensity(inner, trig.function(), trig.frequency(), trig.amplitude(), trig.phase());
        }

        if (node instanceof RotatorDensity rot) {
            DensityFunction inner = optimizeDensity(rot.input());
            if (inner instanceof ConstantDensity) {
                return inner;
            }
            if (Math.abs(rot.angleDegrees()) < 1e-6f) {
                return inner;
            }
            return new RotatorDensity(inner, rot.axis(), rot.angleDegrees());
        }

        if (node instanceof FastGradientWarpDensity fgw) {
            DensityFunction source = optimizeDensity(fgw.source());
            DensityFunction warpField = optimizeDensity(fgw.warpField());
            if (source instanceof ConstantDensity || Math.abs(fgw.factor()) < 1e-6f) {
                return source;
            }
            return new FastGradientWarpDensity(source, warpField, fgw.factor());
        }

        return node;
    }

    // =========================================================================
    // Internal Helpers
    // =========================================================================

    private static DensityFunction compileShorthandTerrain(
        Map<String, Object> settings,
        int biomeId,
        CompileContext context
    ) {
        float baseHeight = getFloat(settings, "baseHeight", 68.0f);
        float heightScale = getFloat(settings, "heightScale", 28.0f);
        float warpStrength = getFloat(settings, "warpStrength", 14.0f);
        int yInterval = getInt(settings, "yInterval", 8);
        float frequency2D = getFloat(settings, "frequency2D", 0.0045f);
        float amplitude2D = getFloat(settings, "amplitude2D", 0.55f);
        float frequency3D = getFloat(settings, "frequency3D", 0.011f);
        float amplitude3D = getFloat(settings, "amplitude3D", 0.52f);

        int seedBase = context.nextSeedSalt(biomeId * 100 + 1);
        DensityFunction baseGrad = new VerticalGradientDensity(baseHeight, heightScale);
        DensityFunction hills2D = new Noise2dDensity(
            seedBase + 1, FastNoiseLite.NoiseType.OpenSimplex2, frequency2D, 3, amplitude2D
        );
        DensityFunction overhangs3D = new YSampledDensity(
            new Noise3dDensity(seedBase + 2, FastNoiseLite.NoiseType.OpenSimplex2, frequency3D, 3, amplitude3D),
            Math.max(1, yInterval)
        );
        DensityFunction sum = new SumDensity(baseGrad, hills2D, overhangs3D);

        if (warpStrength > 0.0f) {
            DensityFunction warpNoise = new Noise2dDensity(
                seedBase + 3, FastNoiseLite.NoiseType.OpenSimplex2, 0.008f, 2, 1.0f
            );
            return optimizeDensity(new GradientWarpDensity(sum, warpNoise, warpStrength));
        }
        return optimizeDensity(sum);
    }

    @SuppressWarnings("unchecked")
    private static <T> MaterialProvider<T> deserializeSpaceAndDepth(
        Map<String, Object> json,
        Function<String, T> blockResolver
    ) {
        List<SpaceAndDepthMaterialProvider.Layer<T>> layers = new ArrayList<>();
        if (json.get("layers") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> lm = (Map<String, Object>) m;
                    String mat = getString(lm, "material", "minecraft:stone");
                    int thickness = getInt(lm, "thickness", 1);
                    layers.add(new SpaceAndDepthMaterialProvider.Layer<>(blockResolver.apply(mat), thickness));
                }
            }
        }
        T fallback = blockResolver.apply(getString(json, "fallbackMaterial", getString(json, "fallback", "minecraft:stone")));
        T cliff = blockResolver.apply(getString(json, "cliffMaterial", getString(json, "cliff", getString(json, "fallbackMaterial", "minecraft:stone"))));
        float maxSlope = getFloat(json, "maxSlopeForCover", 45.0f);
        int minSpace = getInt(json, "minSpaceForCover", 3);
        return new SpaceAndDepthMaterialProvider<>(layers, fallback, cliff, maxSlope, minSpace);
    }

    @SuppressWarnings("unchecked")
    private static DensityFunction compileChildDensity(
        Map<String, Object> parent,
        String primaryKey,
        String aliasKey,
        CompileContext ctx
    ) {
        Object raw = parent.get(primaryKey);
        if (raw == null && aliasKey != null) {
            raw = parent.get(aliasKey);
        }
        if (raw instanceof Map<?, ?> childMap) {
            return compileDensity((Map<String, Object>) childMap, ctx);
        }
        if (raw instanceof Number n) {
            return new ConstantDensity(n.floatValue());
        }
        return new ConstantDensity(0.0f);
    }

    @SuppressWarnings("unchecked")
    private static List<DensityFunction> compileDensityList(
        Map<String, Object> parent,
        String key,
        CompileContext ctx
    ) {
        List<DensityFunction> result = new ArrayList<>();
        Object raw = parent.get(key);
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> childMap) {
                    result.add(compileDensity((Map<String, Object>) childMap, ctx));
                } else if (item instanceof Number n) {
                    result.add(new ConstantDensity(n.floatValue()));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<Pattern<T>> compilePatternList(
        Map<String, Object> parent,
        String key,
        CompileContext ctx,
        Function<String, T> blockResolver
    ) {
        List<Pattern<T>> result = new ArrayList<>();
        Object raw = parent.get(key);
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> childMap) {
                    result.add(compilePattern((Map<String, Object>) childMap, ctx, blockResolver));
                }
            }
        }
        return result;
    }

    private static FastNoiseLite.NoiseType parseNoiseType(String s) {
        if (s == null) return FastNoiseLite.NoiseType.OpenSimplex2;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "opensimplex2s", "simplex2s" -> FastNoiseLite.NoiseType.OpenSimplex2S;
            case "cellular", "worley", "voronoi" -> FastNoiseLite.NoiseType.Cellular;
            case "perlin" -> FastNoiseLite.NoiseType.Perlin;
            case "value", "valuecubic" -> FastNoiseLite.NoiseType.Value;
            default -> FastNoiseLite.NoiseType.OpenSimplex2;
        };
    }

    private static FastNoiseLite.FractalType parseFractalType(String s) {
        if (s == null) return FastNoiseLite.FractalType.FBm;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "none" -> FastNoiseLite.FractalType.None;
            case "ridged", "ridgemulti" -> FastNoiseLite.FractalType.Ridged;
            case "pingpong" -> FastNoiseLite.FractalType.PingPong;
            default -> FastNoiseLite.FractalType.FBm;
        };
    }

    private static FastNoiseLite.CellularReturnType parseCellularReturnType(String s) {
        if (s == null) return FastNoiseLite.CellularReturnType.Distance;
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "cellvalue" -> FastNoiseLite.CellularReturnType.CellValue;
            case "distance", "distancetopoint" -> FastNoiseLite.CellularReturnType.Distance;
            case "distance2", "distancetopoint2" -> FastNoiseLite.CellularReturnType.Distance2;
            case "distance2add" -> FastNoiseLite.CellularReturnType.Distance2Add;
            case "distance2sub", "cellwalldistance", "wall" -> FastNoiseLite.CellularReturnType.Distance2Sub;
            case "distance2mul" -> FastNoiseLite.CellularReturnType.Distance2Mul;
            case "distance2div" -> FastNoiseLite.CellularReturnType.Distance2Div;
            default -> FastNoiseLite.CellularReturnType.Distance;
        };
    }

    private static Prefab loadClasspathPrefab(String prefabId) {
        String pathName = prefabId.contains(":") ? prefabId.substring(prefabId.indexOf(':') + 1) : prefabId;
        if (pathName.startsWith("structures/")) {
            pathName = pathName.substring("structures/".length());
        }
        if (pathName.startsWith("prefabs/")) {
            pathName = pathName.substring("prefabs/".length());
        }
        String resourcePath = "/data/worldgenv2/prefabs/" + pathName + (pathName.endsWith(".prefab.json") ? "" : ".prefab.json");
        try (InputStream in = JsonAstCompiler.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return new PrefabLoader().parseJson(json);
            }
        } catch (IOException ignored) {
        }
        return new Prefab(
            prefabId,
            2,
            new Prefab.Vec3i(1, 1, 1),
            new Prefab.Vec3i(0, 0, 0),
            List.of(Prefab.PaletteEntry.of(0, "minecraft:stone_bricks", Map.of())),
            List.of(new Prefab.VoxelEntry(0, 0, 0, 0)),
            List.of(),
            List.of()
        );
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // Hytale's Seed is a string: any text is a seed, and different text gives a different seed
                if (key.equalsIgnoreCase("seed") && !s.isBlank()) return s.trim().hashCode();
            }
        }
        return defaultValue;
    }

    private static float getFloat(Map<String, Object> map, String key, float defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.floatValue();
        }
        if (val instanceof String s) {
            try {
                return Float.parseFloat(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static Curve compileChildCurve(
        Map<String, Object> parent,
        String primaryKey,
        String aliasKey,
        CompileContext ctx
    ) {
        Object raw = parent.get(primaryKey);
        if (raw == null && aliasKey != null) {
            raw = parent.get(aliasKey);
        }
        return compileCurve(raw, ctx);
    }

    @SuppressWarnings("unchecked")
    private static List<Curve> compileCurveList(
        Map<String, Object> parent,
        String key,
        CompileContext ctx
    ) {
        List<Curve> result = new ArrayList<>();
        Object raw = parent.get(key);
        if (raw == null) {
            raw = parent.get(Character.toUpperCase(key.charAt(0)) + key.substring(1));
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                result.add(compileCurve(item, ctx));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static VectorProvider compileChildVectorProvider(
        Map<String, Object> parent,
        String primaryKey,
        String aliasKey,
        CompileContext ctx
    ) {
        Object raw = parent.get(primaryKey);
        if (raw == null && aliasKey != null) {
            raw = parent.get(aliasKey);
        }
        return compileVectorProvider(raw, ctx);
    }

    @SuppressWarnings("unchecked")
    private static List<VectorProvider> compileVectorProviderList(
        Map<String, Object> parent,
        String key,
        CompileContext ctx
    ) {
        List<VectorProvider> result = new ArrayList<>();
        Object raw = parent.get(key);
        if (raw == null) {
            raw = parent.get(Character.toUpperCase(key.charAt(0)) + key.substring(1));
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                result.add(compileVectorProvider(item, ctx));
            }
        }
        return result;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return defaultValue;
    }

    /**
     * A JSON object whose keys are matched without regard to case, in insertion order. Hytale's assets use PascalCase keys
     * ({@code "Type"}, {@code "Inputs"}) while ours use camelCase; either spelling reads the same parameter.
     */
    public static final class CaseInsensitiveMap extends LinkedHashMap<String, Object> {
        private final Map<String, String> actual = new java.util.HashMap<>();
        /** Dev aid: when on, every key a compiler asks for is remembered, so keys nobody reads can be reported. */
        public static volatile boolean trackReads = false;
        private java.util.Set<String> read;

        private void note(Object key) {
            if (!trackReads) return;
            if (read == null) read = new java.util.HashSet<>();
            read.add(lc(key));
        }

        /** The keys of this object that were never asked for since tracking began (dev aid). */
        public java.util.List<String> unreadKeys() {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String k : keySet()) if (read == null || !read.contains(lc(k))) out.add(k);
            return out;
        }

        private static String lc(Object k) { return k == null ? "" : k.toString().toLowerCase(java.util.Locale.ROOT); }

        @Override public Object put(String key, Object value) {
            String l = lc(key);
            String prev = actual.get(l);
            if (prev != null && !prev.equals(key)) super.remove(prev);
            actual.put(l, key);
            return super.put(key, value);
        }

        @Override public Object get(Object key) {
            note(key);
            Object v = super.get(key);
            if (v != null || super.containsKey(key)) return v;
            String a = actual.get(lc(key));
            return a == null ? null : super.get(a);
        }

        @Override public boolean containsKey(Object key) { note(key); return super.containsKey(key) || actual.containsKey(lc(key)); }

        @Override public Object getOrDefault(Object key, Object def) { return containsKey(key) ? get(key) : def; }
    }

    /**
     * Lightweight, zero-dependency recursive JSON parser.
     */
    public static final class SimpleJsonParser {
        private final String src;
        private int pos;

        public SimpleJsonParser(String src) {
            this.src = src;
            this.pos = 0;
        }

        public Object parseValue() {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            char c = src.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new CaseInsensitiveMap();
            pos++;
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < src.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' in JSON object at position " + pos);
                }
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < src.length()) {
                list.add(parseValue());
                skipWhitespace();
                if (pos < src.length() && src.charAt(pos) == ',') {
                    pos++;
                } else if (pos < src.length() && src.charAt(pos) == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' in JSON array at position " + pos);
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && pos < src.length()) {
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = src.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Number parseNumber() {
            int start = pos;
            boolean isFloat = false;
            if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) {
                pos++;
            }
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloat = true;
                    pos++;
                } else {
                    break;
                }
            }
            String token = src.substring(start, pos);
            return isFloat ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Boolean parseBoolean() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean token at position " + pos);
        }

        private Object parseNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null token at position " + pos);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
