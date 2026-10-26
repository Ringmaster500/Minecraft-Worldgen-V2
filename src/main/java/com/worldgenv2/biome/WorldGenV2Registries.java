package com.worldgenv2.biome;

import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.curve.Curve;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.mask.BlockMask;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.material.patterns.Pattern;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.props.Prop;
import com.worldgenv2.props.runtime.PropRuntime;
import com.worldgenv2.scanner.Scanner;
import com.worldgenv2.vector.VectorProvider;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Open, thread-safe extensibility registries for Worldgen V2 node-graph AST compilation.
 * Allows both internal systems and third-party mods/plugins to register custom:
 * <ul>
 *   <li>{@link #DENSITY_NODE_TYPE} — Volumetric 3D/2D scalar field nodes</li>
 *   <li>{@link #CURVE_TYPE} — 1D scalar curve transfer functions</li>
 *   <li>{@link #VECTOR_PROVIDER_TYPE} — 3D vector fields and domain warpers</li>
 *   <li>{@link #POSITIONS_PROVIDER_TYPE} — Infinite point field generators</li>
 *   <li>{@link #SCANNER_TYPE} — Voxel raycast and anchor surface scanners</li>
 *   <li>{@link #ASSIGNMENTS_TYPE} — Decoupled prop assignment operators</li>
 *   <li>{@link #MATERIAL_PROVIDER_TYPE} — Procedural geological material providers</li>
 *   <li>{@link #PATTERN_TYPE} — Structural and geometric voxel pattern matchers</li>
 *   <li>{@link #PROP_TYPE} — Surface, ceiling, and prefab prop generators</li>
 * </ul>
 */
public final class WorldGenV2Registries {

    public static final String DEFAULT_NAMESPACE = "worldgenv2";

    @FunctionalInterface
    public interface DensityNodeDeserializer {
        DensityFunction deserialize(Map<String, Object> json, JsonAstCompiler.CompileContext context);
    }

    @FunctionalInterface
    public interface CurveDeserializer {
        Curve deserialize(Map<String, Object> json, JsonAstCompiler.CompileContext context);
    }

    @FunctionalInterface
    public interface VectorProviderDeserializer {
        VectorProvider deserialize(Map<String, Object> json, JsonAstCompiler.CompileContext context);
    }

    @FunctionalInterface
    public interface PositionsProviderDeserializer {
        PositionsProvider deserialize(Map<String, Object> json, JsonAstCompiler.CompileContext context);
    }

    @FunctionalInterface
    public interface ScannerDeserializer {
        Scanner deserialize(Map<String, Object> json, JsonAstCompiler.CompileContext context);
    }

    @FunctionalInterface
    public interface AssignmentsDeserializer {
        <T> Assignments<T> deserialize(
            Map<String, Object> json,
            JsonAstCompiler.CompileContext context,
            Function<String, T> blockResolver
        );
    }

    @FunctionalInterface
    public interface MaterialProviderDeserializer {
        <T> MaterialProvider<T> deserialize(
            Map<String, Object> json,
            JsonAstCompiler.CompileContext context,
            Function<String, T> blockResolver
        );
    }

    @FunctionalInterface
    public interface PatternDeserializer {
        <T> Pattern<T> deserialize(
            Map<String, Object> json,
            JsonAstCompiler.CompileContext context,
            Function<String, T> blockResolver
        );
    }

    @FunctionalInterface
    public interface BlockMaskDeserializer {
        <T> BlockMask<T> deserialize(
            Map<String, Object> json,
            JsonAstCompiler.CompileContext context,
            Function<String, T> blockResolver
        );
    }

    @FunctionalInterface
    public interface PropRuntimeDeserializer {
        <T> PropRuntime<T> deserialize(
            Map<String, Object> json,
            JsonAstCompiler.CompileContext context,
            Function<String, T> blockResolver
        );
    }

    @FunctionalInterface
    public interface PropDeserializer {
        <T> Prop<T> deserialize(
            Map<String, Object> json,
            JsonAstCompiler.CompileContext context,
            Function<String, T> blockResolver
        );
    }

    /**
     * Thread-safe registry mapping type identifiers (both short PascalCase names like {@code "SumDensity"}
     * and namespaced IDs like {@code "worldgenv2:sum_density"} or {@code "mymod:custom_node"}) to deserializers.
     */
    public static final class Registry<D> {
        private final String registryName;
        private final Map<String, D> entries = new ConcurrentHashMap<>();

        public Registry(String registryName) {
            this.registryName = Objects.requireNonNull(registryName, "registryName");
        }

        public D register(String id, D deserializer) {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(deserializer, "deserializer");

            String trimmed = id.trim();
            entries.put(trimmed, deserializer);
            entries.put(trimmed.toLowerCase(Locale.ROOT), deserializer);

            if (!trimmed.contains(":")) {
                String snake = toSnakeCase(trimmed);
                entries.put(snake, deserializer);
                entries.put(DEFAULT_NAMESPACE + ":" + snake, deserializer);
                entries.put(DEFAULT_NAMESPACE + ":" + trimmed.toLowerCase(Locale.ROOT), deserializer);
            } else {
                int colon = trimmed.indexOf(':');
                String ns = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
                String path = trimmed.substring(colon + 1);
                entries.put(ns + ":" + toSnakeCase(path), deserializer);
                if (DEFAULT_NAMESPACE.equals(ns)) {
                    entries.put(path, deserializer);
                    entries.put(path.toLowerCase(Locale.ROOT), deserializer);
                }
            }
            return deserializer;
        }

        public D get(String id) {
            if (id == null) {
                return null;
            }
            String trimmed = id.trim();
            D found = entries.get(trimmed);
            if (found != null) {
                return found;
            }
            found = entries.get(trimmed.toLowerCase(Locale.ROOT));
            if (found != null) {
                return found;
            }
            return entries.get(toSnakeCase(trimmed));
        }

        public D getOrThrow(String id) {
            D found = get(id);
            if (found == null) {
                throw new IllegalArgumentException(
                    "Unknown " + registryName + " type '" + id + "'. Registered keys: " + entries.keySet()
                );
            }
            return found;
        }

        public boolean contains(String id) {
            return get(id) != null;
        }

        public Set<String> keys() {
            return Collections.unmodifiableSet(entries.keySet());
        }

        private static String toSnakeCase(String input) {
            StringBuilder sb = new StringBuilder(input.length() + 4);
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                if (Character.isUpperCase(c)) {
                    if (i > 0 && input.charAt(i - 1) != '_' && input.charAt(i - 1) != ':'
                        && !Character.isUpperCase(input.charAt(i - 1))) {
                        sb.append('_');
                    }
                    sb.append(Character.toLowerCase(c));
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    public static final Registry<DensityNodeDeserializer> DENSITY_NODE_TYPE =
        new Registry<>("DensityFunction");

    public static final Registry<CurveDeserializer> CURVE_TYPE =
        new Registry<>("Curve");

    public static final Registry<VectorProviderDeserializer> VECTOR_PROVIDER_TYPE =
        new Registry<>("VectorProvider");

    public static final Registry<PositionsProviderDeserializer> POSITIONS_PROVIDER_TYPE =
        new Registry<>("PositionsProvider");

    public static final Registry<ScannerDeserializer> SCANNER_TYPE =
        new Registry<>("Scanner");

    public static final Registry<AssignmentsDeserializer> ASSIGNMENTS_TYPE =
        new Registry<>("Assignments");

    public static final Registry<MaterialProviderDeserializer> MATERIAL_PROVIDER_TYPE =
        new Registry<>("MaterialProvider");

    public static final Registry<PatternDeserializer> PATTERN_TYPE =
        new Registry<>("Pattern");

    public static final Registry<PropDeserializer> PROP_TYPE =
        new Registry<>("Prop");

    public static final Registry<BlockMaskDeserializer> BLOCK_MASK_TYPE =
        new Registry<>("BlockMask");

    public static final Registry<PropRuntimeDeserializer> PROP_RUNTIME_TYPE =
        new Registry<>("PropRuntime");

    static {
        JsonAstCompiler.registerBuiltInTypes();
    }

    private WorldGenV2Registries() {
    }
}
