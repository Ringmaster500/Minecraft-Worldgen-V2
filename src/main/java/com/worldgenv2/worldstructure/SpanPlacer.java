package com.worldgenv2.worldstructure;

import com.worldgenv2.prefab.Prefab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places prefabs that <em>span</em> a gap in the terrain, for example a fallen log lying across a river.
 *
 * <p>The decision is a pure function of the seed and the terrain, so it does not depend on which chunk is generated
 * first. The world is cut into cells; each cell may host one candidate per rule. A candidate scans a straight line
 * for a run of water, then only accepts it when every prerequisite holds:
 * <ul>
 *   <li>the run is between {@code minGap} and {@code maxGap} blocks long,</li>
 *   <li>both ends are solid land at least two blocks wide, with heights within {@code maxBankDiff} of each other,</li>
 *   <li>the river is straight across the prefab's width (so it does not lie across a diagonal bend),</li>
 *   <li>the biome matches (when the rule names biomes),</li>
 *   <li>a prefab in the rule's list is long enough to reach both banks but not far past them.</li>
 * </ul>
 * The prefab is turned so its long side crosses the gap.
 */
public final class SpanPlacer {

    public record Rule(String id, List<Prefab> prefabs, int minGap, int maxGap, int maxBankDiff, int cell,
                       float chance, int yOffset, Set<String> biomes) {}

    private record Key(int rule, int cx, int cz) {}

    private final long seed;
    private final int minY, maxY, seaLevel;
    private final List<Rule> rules;
    private final int margin;
    private final Map<Key, List<StructurePlacement>> cache = new ConcurrentHashMap<>();

    public SpanPlacer(long seed, int minY, int maxY, int seaLevel, List<Rule> rules) {
        this.seed = seed;
        this.minY = minY;
        this.maxY = maxY;
        this.seaLevel = seaLevel;
        this.rules = List.copyOf(rules);
        int m = 16;
        for (Rule r : rules) {
            for (Prefab p : r.prefabs()) m = Math.max(m, Math.max(p.size().x(), p.size().z()) + 8);
            m = Math.max(m, r.maxGap() + 16);
        }
        this.margin = m;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** Every span placement that can touch the chunk. */
    public List<StructurePlacement> forChunk(WorldStructure ws, int chunkX, int chunkZ) {
        List<StructurePlacement> out = new ArrayList<>();
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        for (int ri = 0; ri < rules.size(); ri++) {
            Rule rule = rules.get(ri);
            int cell = Math.max(16, rule.cell());
            for (int cx = Math.floorDiv(x0 - margin, cell); cx <= Math.floorDiv(x0 + 15 + margin, cell); cx++) {
                for (int cz = Math.floorDiv(z0 - margin, cell); cz <= Math.floorDiv(z0 + 15 + margin, cell); cz++) {
                    final int fri = ri, fcx = cx, fcz = cz;
                    List<StructurePlacement> here = cache.computeIfAbsent(new Key(ri, cx, cz), k -> evaluate(ws, fri, fcx, fcz));
                    for (StructurePlacement p : here) if (p.touchesChunk(chunkX, chunkZ)) out.add(p);
                }
            }
        }
        return out;
    }

    private List<StructurePlacement> evaluate(WorldStructure ws, int ri, int cx, int cz) {
        Rule rule = rules.get(ri);
        if (rule.prefabs().isEmpty()) return List.of();
        SplittableRandom rng = new SplittableRandom(seed * 0x9E3779B97F4A7C15L ^ ((long) ri << 48) ^ ((long) cx * 73856093L) ^ ((long) cz * 19349663L));
        if (rng.nextDouble() >= rule.chance()) return List.of();
        int cell = Math.max(16, rule.cell());
        int px = cx * cell + rng.nextInt(cell), pz = cz * cell + rng.nextInt(cell);
        boolean firstX = rng.nextBoolean();
        int flip = rng.nextInt(2) * 2;
        int pick = rng.nextInt(rule.prefabs().size());
        for (int a = 0; a < 2; a++) {
            boolean axisX = a == 0 ? firstX : !firstX;
            StructurePlacement p = trySpan(ws, rule, ri, px, pz, axisX, flip, pick);
            if (p != null) return List.of(p);
        }
        return List.of();
    }

    private StructurePlacement trySpan(WorldStructure ws, Rule rule, int ri, int px, int pz, boolean axisX, int flip, int pick) {
        Map<Long, Integer> memo = new HashMap<>();
        int dx = axisX ? 1 : 0, dz = axisX ? 0 : 1;
        int reach = rule.maxGap() + 8;
        // coarse scan: find runs of water along the line through (px, pz)
        int bestStart = Integer.MIN_VALUE, bestEnd = 0;
        long bestScore = Long.MAX_VALUE;
        int runStart = Integer.MIN_VALUE;
        for (int t = -reach; t <= reach + 2; t += 2) {
            boolean wet = t <= reach && surf(ws, memo, px + dx * t, pz + dz * t) < seaLevel;
            if (wet && runStart == Integer.MIN_VALUE) runStart = t;
            if (!wet && runStart != Integer.MIN_VALUE) {
                int s = runStart, e = t - 2;
                runStart = Integer.MIN_VALUE;
                // refine to exact block edges
                if (surf(ws, memo, px + dx * (s - 1), pz + dz * (s - 1)) < seaLevel) s--;
                if (surf(ws, memo, px + dx * (e + 1), pz + dz * (e + 1)) < seaLevel) e++;
                if (s <= -reach || e >= reach) continue;
                int len = e - s + 1;
                if (len < rule.minGap() || len > rule.maxGap()) continue;
                long score = Math.abs((long) (s + e) / 2);
                if (score < bestScore) { bestScore = score; bestStart = s; bestEnd = e; }
            }
        }
        if (bestStart == Integer.MIN_VALUE) return null;
        int s = bestStart, e = bestEnd, len = e - s + 1;
        // banks: solid land two blocks deep on both sides, at similar heights
        int hA = surf(ws, memo, px + dx * (s - 1), pz + dz * (s - 1));
        int hB = surf(ws, memo, px + dx * (e + 1), pz + dz * (e + 1));
        if (hA < seaLevel || hB < seaLevel) return null;
        if (surf(ws, memo, px + dx * (s - 2), pz + dz * (s - 2)) < seaLevel) return null;
        if (surf(ws, memo, px + dx * (e + 2), pz + dz * (e + 2)) < seaLevel) return null;
        if (Math.abs(hA - hB) > rule.maxBankDiff()) return null;
        // the river must be straight across the prefab's width, not a diagonal bend
        int mid = (s + e) / 2;
        for (int lat : new int[]{-3, 3}) {
            int lx = px + (axisX ? 0 : lat), lz = pz + (axisX ? lat : 0);
            if (surf(ws, memo, lx + dx * mid, lz + dz * mid) >= seaLevel) return null;
            if (surf(ws, memo, lx + dx * (s - 1), lz + dz * (s - 1)) < seaLevel) return null;
            if (surf(ws, memo, lx + dx * (e + 1), lz + dz * (e + 1)) < seaLevel) return null;
        }
        int gx = px + dx * mid, gz = pz + dz * mid;
        if (!rule.biomes().isEmpty()) {
            String biome = ws.queryBiome(gx, gz).primaryTileBiome().name();
            if (!rule.biomes().contains(biome)) return null;
        }
        // choose a prefab long enough to reach both banks without sailing far past them
        int n = rule.prefabs().size();
        for (int i = 0; i < n; i++) {
            Prefab base = rule.prefabs().get((pick + i) % n);
            boolean longIsX = base.size().x() >= base.size().z();
            int length = Math.max(base.size().x(), base.size().z());
            if (length < len + 2 || length > len + 16) continue;
            int turns = (longIsX == axisX ? 0 : 1) + flip;
            Prefab turned = base.rotated(turns);
            int baseX = gx - turned.size().x() / 2, baseZ = gz - turned.size().z() / 2;
            int y = Math.max(hA, hB) + 1 + rule.yOffset() + turned.anchor().y();
            return new StructurePlacement(rule.id() + "_" + gx + "_" + gz, turned,
                    baseX + turned.anchor().x(), y, baseZ + turned.anchor().z());
        }
        return null;
    }

    private int surf(WorldStructure ws, Map<Long, Integer> memo, int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Integer v = memo.get(k);
        if (v == null) {
            v = ws.sampleSurfaceY(x, z, minY, maxY);
            memo.put(k, v);
        }
        return v;
    }
}
