package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.worldstructure.BiCarta;
import com.worldgenv2.worldstructure.DensityBiomeMap;
import com.worldgenv2.worldstructure.TileBiome;

import java.util.List;
import java.util.Map;

/** Phase 25 verification: Hytale World Structure biome distribution (Density plus Biome delimiters, transitions, edge distance). */
public final class Phase25VerificationTest {

    private static int passed = 0;

    private static void check(boolean ok, String what) {
        if (!ok) { System.err.println("[FAIL] " + what); throw new AssertionError(what); }
        System.out.println("[PASS] " + what);
        passed++;
    }

    private static TileBiome biome(int id, String name) { return new TileBiome(id, name, 1f, 1f, 32f, (x, y, z) -> 0f); }

    public static void main(String[] args) {
        System.out.println("=== Phase 25 Verification Test Suite: World Structure biome map ===");
        JsonAstCompiler.registerBuiltInTypes();

        TileBiome a = biome(2, "default"), b = biome(3, "b"), c = biome(4, "c");
        Map<Integer, TileBiome> byId = Map.of(2, a, 3, b, 4, c);
        DensityFunction x = (px, py, pz) -> px;
        DensityBiomeMap map = new DensityBiomeMap(x, List.of(new DensityBiomeMap.Delimiter(0, 100, 3), new DensityBiomeMap.Delimiter(100, 200, 4)), 2, 32f, 64, byId::get);

        check(map.query(-50, 0).primary() == a, "outside every delimiter the default biome is used");
        check(map.query(50, 0).primary() == b && map.query(150, 0).primary() == c, "a delimiter gives its biome where the density is within its bounds");
        check(map.query(500, 0).primary() == a, "beyond the last delimiter it is the default biome again");

        BiCarta.CellQueryResult<TileBiome> inner = map.query(50, 0);
        check(inner.blendFactor() == 0f, "farther than DefaultTransitionDistance from an edge there is no blending");
        BiCarta.CellQueryResult<TileBiome> near = map.query(90, 0);
        check(near.secondary() == c && Math.abs(near.distanceToEdge() - 10f) < 0.5f, "near an edge the neighbouring biome is the secondary, with the distance to the edge in blocks");
        check(near.blendFactor() > 0f && near.blendFactor() < 0.5f, "blend weight rises towards the edge");
        BiCarta.CellQueryResult<TileBiome> onEdge = map.query(-0.01f, 0);
        check(onEdge.primary() == a && onEdge.secondary() == b && Math.abs(onEdge.blendFactor() - 0.5f) < 0.02f, "exactly on an edge both biomes weigh half");
        check(map.query(-20, 0).secondary() == b, "the default region blends towards the delimiter it borders");

        // the field can be any density: a slower field makes transitions wider in blocks
        DensityBiomeMap slow = new DensityBiomeMap((px, py, pz) -> px * 0.1f, List.of(new DensityBiomeMap.Delimiter(0, 10, 3)), 2, 32f, 64, byId::get);
        check(Math.abs(slow.query(50, 0).distanceToEdge() - 50f) < 1f, "distance to the edge follows how fast the field changes (slope 0.1: value gap 5 is 50 blocks)");

        // DistanceFromBiomeEdge: blocks to the nearest edge, -1 beyond MaxBiomeEdgeDistance
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L);
        ctx.setBiomeEdge((px, pz) -> map.query((float) px, (float) pz).distanceToEdge(), 30);
        @SuppressWarnings("unchecked")
        DensityFunction edge = JsonAstCompiler.compileDensity((Map<String, Object>) new JsonAstCompiler.SimpleJsonParser("{\"Type\":\"DistanceFromBiomeEdge\"}").parseValue(), ctx);
        check(Math.abs(edge.compute(90, 0, 0) - 10f) < 0.5f && edge.compute(50, 0, 0) == -1f, "DistanceFromBiomeEdge reads the edge distance, -1 beyond MaxBiomeEdgeDistance");

        System.out.println("Phase 25 verification: ALL " + passed + " TESTS PASSED!");
    }
}