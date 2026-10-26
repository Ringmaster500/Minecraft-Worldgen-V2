package com.worldgenv2;

import com.worldgenv2.density.ConstantDensity;
import com.worldgenv2.density.VerticalGradientDensity;
import com.worldgenv2.worldstructure.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verification for Phase 7: climate-driven biomes (oceans/land), volumetric caves, rivers, and 3D underground biomes.
 * Run via {@code gradlew verifyPhase7}.
 */
public final class Phase7VerificationTest {

    private static int failures = 0;

    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "[PASS] " : "[FAIL] ") + msg);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        long seed = 1337L;

        // --- Climate-driven BiCarta: oceans + land + highlands all appear, spawn is land ---
        TileBiome deep = new TileBiome(0, "Deep", 10, 1.5f, 60, new ConstantDensity(0f)).withClimate(new ClimateTarget(-0.9f, 0f, 0f));
        TileBiome ocean = new TileBiome(1, "Ocean", 10, 1.5f, 60, new ConstantDensity(0f)).withClimate(new ClimateTarget(-0.5f, 0f, 0f));
        TileBiome plains = new TileBiome(2, "Plains", 10, 1.5f, 60, new ConstantDensity(0f)).withClimate(new ClimateTarget(0.1f, 0f, 0f));
        TileBiome desert = new TileBiome(3, "Desert", 10, 1.5f, 60, new ConstantDensity(0f)).withClimate(new ClimateTarget(0.1f, 0.9f, -0.8f));
        TileBiome peaks = new TileBiome(4, "Peaks", 10, 1.5f, 60, new ConstantDensity(0f)).withClimate(new ClimateTarget(0.9f, -0.3f, 0f));
        WorldStructure ws = new WorldStructure(List.of(deep, ocean, plains, desert, peaks), List.of(), 288, seed);
        Set<Integer> seen = new HashSet<>();
        for (int x = -12000; x <= 12000; x += 160) {
            for (int z = -12000; z <= 12000; z += 160) {
                seen.add(ws.getBiomeId(x, z));
            }
        }
        check(seen.size() == 5, "climate selection yields every biome across a 24km map (saw " + seen.size() + ")");
        int spawnId = ws.getBiomeId(0, 0);
        check(spawnId == 2 || spawnId == 3, "world origin is on land (id " + spawnId + ")");

        // --- Caves: carve negative density deep underground, keep bedrock shell & sky untouched ---
        CaveCarver caves = new CaveCarver(seed);
        int minY = -64;
        int maxY = 320;
        VerticalGradientDensity gradient = new VerticalGradientDensity(72f, 26f);
        int carvedColumns = 0;
        int carvedVoxels = 0;
        boolean bedrockShellIntact = true;
        for (int x = 0; x < 160; x += 3) {
            for (int z = 0; z < 160; z += 3) {
                float[] col = new float[maxY - minY];
                gradient.computeColumn(x, z, minY, maxY, col);
                float[] original = col.clone();
                caves.carveColumn(x, z, minY, maxY, col);
                boolean any = false;
                for (int i = 0; i < col.length; i++) {
                    int y = minY + i;
                    if (original[i] > 0 && col[i] <= 0) {
                        any = true;
                        carvedVoxels++;
                        if (y < minY + 7) bedrockShellIntact = false;
                        if (y > 71) bedrockShellIntact = false;
                    }
                }
                if (any) carvedColumns++;
            }
        }
        check(carvedVoxels > 5000, "caves carve a meaningful volume (" + carvedVoxels + " voxels)");
        check(carvedColumns > 100, "caves appear in many columns (" + carvedColumns + ")");
        check(bedrockShellIntact, "caves never touch the bedrock shell or rise above the surface");

        // --- Rivers: strength forms channels and lowers terrain toward sea level ---
        RiverCarver rivers = new RiverCarver(seed, 63, 6);
        int channelColumns = 0;
        int loweredColumns = 0;
        int total = 0;
        for (int x = -4000; x < 4000; x += 16) {
            for (int z = -4000; z < 4000; z += 16) {
                total++;
                if (rivers.isChannel(x, z)) {
                    channelColumns++;
                    float[] col = new float[maxY - minY];
                    new VerticalGradientDensity(76f, 26f).computeColumn(x, z, minY, maxY, col);
                    rivers.carveColumn(x, z, minY, maxY, col);
                    if (col[64 - minY] <= 0) loweredColumns++;
                }
            }
        }
        float channelFraction = channelColumns / (float) total;
        check(channelFraction > 0.01f && channelFraction < 0.20f, "river channels cover a sensible fraction (" + channelFraction + ")");
        check(loweredColumns > channelColumns * 0.9f, "river channels lower terrain below sea level (" + loweredColumns + "/" + channelColumns + ")");

        // --- Underground 3D biomes: several distinct volumes, varying with Y ---
        UndergroundBiome a = new UndergroundBiome(10, "A", -40, 50, 1f, Set.of(), null, 0f, null, 0f);
        UndergroundBiome b = new UndergroundBiome(11, "B", -40, 50, 1f, Set.of(), null, 0f, null, 0f);
        UndergroundBiome c = new UndergroundBiome(12, "C", -60, -30, 1f, Set.of(), null, 0f, null, 0f);
        UndergroundBiomeMap map = new UndergroundBiomeMap(List.of(a, b, c), seed);
        Set<Integer> undergroundSeen = new HashSet<>();
        int none = 0;
        for (int x = -600; x < 600; x += 12) {
            for (int z = -600; z < 600; z += 12) {
                for (int y = -60; y <= 50; y += 10) {
                    UndergroundBiome ub = map.query(x, y, z, () -> 0);
                    if (ub == null) none++; else undergroundSeen.add(ub.id());
                }
            }
        }
        check(undergroundSeen.size() == 3, "all underground volumes are placed (" + undergroundSeen + ")");
        check(none > 0, "plain rock remains between underground volumes");
        check(map.query(0, 80, 0, () -> 0) == null, "no underground biome above the configured band");

        System.out.println(failures == 0 ? "Phase 7 verification: ALL PASSED" : "Phase 7 verification: " + failures + " FAILURE(S)");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
