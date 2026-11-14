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
 * Places prefabs that <em>span</em> a gap in the terrain, for example a fallen tree lying across a river.
 *
 * <p>The decision is a pure function of the seed and the terrain, so it does not depend on which chunk is generated
 * first. The world is cut into cells; each cell may host one candidate per rule. A candidate scans a straight line
 * for a run of water, then only accepts it when every prerequisite holds:
 * <ul>
 *   <li>the run is between {@code minGap} and {@code maxGap} blocks long (water = below the local river or sea level),</li>
 *   <li>both ends are solid land at least two blocks wide, with heights within {@code maxBankDiff} of each other and at
 *       most {@code maxDrop} above the water,</li>
 *   <li>the river is straight across the prefab's width (so it does not lie across a diagonal bend),</li>
 *   <li>the biome matches (when the rule names biomes),</li>
 *   <li>a prefab in the rule's list is long enough to reach both banks but not far past them,</li>
 *   <li>when the rule has companion trees: at least one is tall enough that it could have made the span (height at least
 *       {@code minCompanionRatio} times the span's length); otherwise nothing is placed.</li>
 * </ul>
 * The span prefab is turned so its long side crosses the gap. Companions stand on the bank beside the prefab's
 * {@code root} socket (the end the tree fell from).
 */
public final class SpanPlacer {

    public record Rule(String id, List<Prefab> prefabs, int minGap, int maxGap, int maxBankDiff, int cell,
                       float chance, int yOffset, Set<String> biomes, int maxDrop,
                       List<Prefab> companions, float minCompanionRatio, int companionCount) {}

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
            for (Prefab p : r.companions()) m = Math.max(m, Math.max(p.size().x(), p.size().z()) + 24);
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
        long variety = rng.nextLong();
        for (int a = 0; a < 2; a++) {
            boolean axisX = a == 0 ? firstX : !firstX;
            List<StructurePlacement> p = trySpan(ws, rule, px, pz, axisX, flip, pick, variety);
            if (p != null) return p;
        }
        return List.of();
    }

    private List<StructurePlacement> trySpan(WorldStructure ws, Rule rule, int px, int pz, boolean axisX, int flip, int pick, long variety) {
        Map<Long, Integer> memo = new HashMap<>();
        int dx = axisX ? 1 : 0, dz = axisX ? 0 : 1;
        int reach = rule.maxGap() + 8;
        // coarse scan: find runs of water along the line through (px, pz)
        int bestStart = Integer.MIN_VALUE, bestEnd = 0;
        long bestScore = Long.MAX_VALUE;
        int runStart = Integer.MIN_VALUE;
        for (int t = -reach; t <= reach + 2; t += 2) {
            boolean wet = t <= reach && wet(ws, memo, px + dx * t, pz + dz * t);
            if (wet && runStart == Integer.MIN_VALUE) runStart = t;
            if (!wet && runStart != Integer.MIN_VALUE) {
                int s = runStart, e = t - 2;
                runStart = Integer.MIN_VALUE;
                if (wet(ws, memo, px + dx * (s - 1), pz + dz * (s - 1))) s--;      // refine to exact block edges
                if (wet(ws, memo, px + dx * (e + 1), pz + dz * (e + 1))) e++;
                if (s <= -reach || e >= reach) continue;
                int len = e - s + 1;
                if (len < rule.minGap() || len > rule.maxGap()) continue;
                long score = Math.abs((long) (s + e) / 2);
                if (score < bestScore) { bestScore = score; bestStart = s; bestEnd = e; }
            }
        }
        if (bestStart == Integer.MIN_VALUE) return null;
        int s = bestStart, e = bestEnd, len = e - s + 1;
        int mid = (s + e) / 2;
        int gx = px + dx * mid, gz = pz + dz * mid;
        int level = level(ws, gx, gz);
        // banks: solid land two blocks deep on both sides, at similar heights
        int hA = surf(ws, memo, px + dx * (s - 1), pz + dz * (s - 1));
        int hB = surf(ws, memo, px + dx * (e + 1), pz + dz * (e + 1));
        if (wet(ws, memo, px + dx * (s - 1), pz + dz * (s - 1)) || wet(ws, memo, px + dx * (e + 1), pz + dz * (e + 1))) return null;
        if (wet(ws, memo, px + dx * (s - 2), pz + dz * (s - 2)) || wet(ws, memo, px + dx * (e + 2), pz + dz * (e + 2))) return null;
        if (Math.abs(hA - hB) > rule.maxBankDiff()) return null;
        // the banks must lie close to the water, so the span rests near the surface instead of bridging a canyon
        if (hA > level + rule.maxDrop() || hB > level + rule.maxDrop()) return null;
        // the river must be straight across the prefab's width, not a diagonal bend
        for (int lat : new int[]{-3, 3}) {
            int lx = px + (axisX ? 0 : lat), lz = pz + (axisX ? lat : 0);
            if (!wet(ws, memo, lx + dx * mid, lz + dz * mid)) return null;
            if (wet(ws, memo, lx + dx * (s - 1), lz + dz * (s - 1))) return null;
            if (wet(ws, memo, lx + dx * (e + 1), lz + dz * (e + 1))) return null;
        }
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
            List<StructurePlacement> out = new ArrayList<>();
            out.add(new StructurePlacement(rule.id() + "_" + gx + "_" + gz, turned,
                    baseX + turned.anchor().x(), y, baseZ + turned.anchor().z()));
            if (!rule.companions().isEmpty()) {
                List<StructurePlacement> trees = companions(ws, rule, turned, baseX, baseZ, gx, gz, axisX, length, variety, memo);
                if (trees == null) return null;       // no tree tall enough could have made this span
                out.addAll(trees);
            }
            return out;
        }
        return null;
    }

    /**
     * Standing trees beside the root end of the fallen tree. Only trees at least {@code minCompanionRatio} times as tall as
     * the span is long are used; returns null when none qualifies or none finds solid ground.
     */
    private List<StructurePlacement> companions(WorldStructure ws, Rule rule, Prefab turned, int baseX, int baseZ,
                                                 int gx, int gz, boolean axisX, int length, long variety, Map<Long, Integer> memo) {
        List<Prefab> tall = new ArrayList<>();
        int need = (int) Math.ceil(length * rule.minCompanionRatio());
        for (Prefab t : rule.companions()) if (t.size().y() >= need) tall.add(t);
        if (tall.isEmpty()) return null;

        int rootX = gx, rootZ = gz;
        for (Prefab.PrefabSocket sk : turned.sockets()) {
            if (sk.name().equals("root")) { rootX = baseX + sk.position().x(); rootZ = baseZ + sk.position().z(); break; }
        }
        // which way is "outward" from the middle of the span to its root end
        int ox = axisX ? Integer.signum(rootX - gx) : 0, oz = axisX ? 0 : Integer.signum(rootZ - gz);
        if (ox == 0 && oz == 0) { ox = axisX ? 1 : 0; oz = axisX ? 0 : 1; }
        int lx = -oz, lz = ox;                         // sideways direction
        SplittableRandom rng = new SplittableRandom(variety);
        List<StructurePlacement> out = new ArrayList<>();
        for (int k = 0, tries = 0; k < rule.companionCount() && tries < rule.companionCount() * 4; tries++) {
            int along = 3 + rng.nextInt(6) + k * 2;
            int side = (rng.nextBoolean() ? 1 : -1) * (3 + rng.nextInt(5));
            int tx = rootX + ox * along + lx * side, tz = rootZ + oz * along + lz * side;
            if (wet(ws, memo, tx, tz)) continue;
            Prefab tree = tall.get(rng.nextInt(tall.size()));
            int ty = surf(ws, memo, tx, tz);
            out.add(new StructurePlacement("tree_" + tx + "_" + tz, tree, tx, ty, tz));
            k++;
        }
        return out.isEmpty() ? null : out;
    }

    /** Water level at a column: the river's own level inside a river channel, otherwise sea level. */
    private int level(WorldStructure ws, int x, int z) {
        int rl = ws.riverWaterLevel(x, z);
        return rl == Integer.MIN_VALUE ? seaLevel : rl;
    }

    private boolean wet(WorldStructure ws, Map<Long, Integer> memo, int x, int z) {
        return surf(ws, memo, x, z) < level(ws, x, z);
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
