package com.worldgenv2.placement;

import com.worldgenv2.placement.Placement.Action;
import com.worldgenv2.placement.Placement.Condition;
import com.worldgenv2.placement.Placement.Env;
import com.worldgenv2.placement.Placement.Finder;
import com.worldgenv2.worldstructure.StructurePlacement;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Runs placement rules over an unbounded world.
 *
 * <p>The world is cut into square cells per rule. Each cell, with probability {@code chance}, gets one candidate point;
 * the rule's finder, conditions and actions run there. The outcome depends only on the seed, the rule, the cell and the
 * terrain, so it is identical whichever chunk is generated first, and each chunk stamps only the part of each placement
 * that falls inside it.
 */
public final class PlacementEngine {

    public record Rule(String id, int cell, float chance, Finder finder, List<Condition> conditions, List<Action> actions) {
        int reach() {
            int r = finder.reach();
            for (Action a : actions) r += a.reach();
            return r;
        }
    }

    private record Key(int rule, int cx, int cz) {}

    private final long seed;
    private final int minY, maxY, seaLevel;
    private final List<Rule> rules;
    private final int margin;
    private final Map<Key, List<StructurePlacement>> cache = new ConcurrentHashMap<>();

    public PlacementEngine(long seed, int minY, int maxY, int seaLevel, List<Rule> rules) {
        this.seed = seed;
        this.minY = minY;
        this.maxY = maxY;
        this.seaLevel = seaLevel;
        this.rules = List.copyOf(rules);
        int m = 16;
        for (Rule r : rules) m = Math.max(m, r.reach());
        this.margin = m;
    }

    public boolean isEmpty() { return rules.isEmpty(); }

    public List<Rule> rules() { return rules; }

    /** Every placement of every rule that can touch the chunk. */
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
        SplittableRandom rng = new SplittableRandom(seed * 0x9E3779B97F4A7C15L ^ ((long) ri << 48) ^ ((long) cx * 73856093L) ^ ((long) cz * 19349663L));
        if (rng.nextDouble() >= rule.chance()) return List.of();
        int cell = Math.max(16, rule.cell());
        int px = cx * cell + rng.nextInt(cell), pz = cz * cell + rng.nextInt(cell);
        try {
            Env env = new Env(ws, minY, maxY, seaLevel);
            Vars vars = rule.finder().find(env, px, pz, rng);
            if (vars == null) return List.of();
            for (Condition c : rule.conditions()) if (!c.test(env, vars)) return List.of();
            List<StructurePlacement> out = new ArrayList<>();
            for (Action a : rule.actions()) if (!a.run(env, vars, rng, out)) return List.of();
            return out.isEmpty() ? List.of() : List.copyOf(out);
        } catch (RuntimeException ex) {
            return List.of();      // a faulty addon piece must not take chunk generation down with it
        }
    }
}
