package com.worldgenv2.placement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.placement.Placement.Action;
import com.worldgenv2.placement.Placement.Condition;
import com.worldgenv2.placement.Placement.Env;
import com.worldgenv2.placement.Placement.Finder;
import com.worldgenv2.prefab.Prefab;
import com.worldgenv2.worldstructure.StructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static com.worldgenv2.placement.Placement.param;

/**
 * The built-in finders, conditions and actions. They are registered exactly the way an addon registers its own.
 *
 * <p>Variables published (read them in expressions):
 * <ul>
 *   <li>every finder: {@code point.x/z} (the candidate), {@code loc.x/z} (where the find is, used by conditions)</li>
 *   <li>Span finder: {@code span.length, span.axisX (1 = along X, 0 = along Z), span.centerX/Z, span.hA, span.hB,
 *       span.bankTop, span.level}</li>
 *   <li>Prefab action named N: {@code N.x/y/z, N.centerX/Z, N.length, N.height, N.width, N.count,
 *       N.socket.<socket>.x/z} (world position of each named socket of the placed prefab)</li>
 *   <li>while choosing a prefab: {@code prefab.length, prefab.height, prefab.width, prefab.sizeX/Y/Z}; while choosing the
 *       height: {@code ground} (surface height at the target column)</li>
 * </ul>
 */
final class BuiltinPlacement {
    private BuiltinPlacement() {}

    static void register() {
        // ------------------------------------------------------------ finders
        Placement.registerFinder("Point", "The candidate point itself, on the ground.", List.of(), (c, ctx) -> new Finder() {
            @Override public Vars find(Env env, int px, int pz, SplittableRandom rng) {
                return new Vars().set("point.x", px).set("point.z", pz).set("loc.x", px).set("loc.z", pz).set("ground", env.surface(px, pz));
            }
            @Override public int reach() { return 4; }
        });

        Placement.registerFinder("Span", "A stretch of water between two solid banks that something could lie across.", List.of(
                param("axis", "string", "any", "any, x or z: which way to look across the water"),
                param("minGap", "int", 4, "Shortest stretch of water to cross"),
                param("maxGap", "int", 12, "Longest stretch of water to cross"),
                param("maxBankDiff", "int", 3, "Largest height difference between the two banks"),
                param("maxBankHeight", "int", 3, "Banks may rise at most this far above the water")),
                (c, ctx) -> spanFinder(c));

        // ------------------------------------------------------------ conditions
        Placement.registerCondition("Biome", "Only in these biomes.", List.of(param("biomes", "strings", "", "Biome ids; empty = any")),
                (c, ctx) -> {
                    Set<String> names = ctx.biomeNames(array(c, "biomes"));
                    return (env, v) -> names.isEmpty() || names.contains(env.ws.queryBiome((float) v.get("loc.x"), (float) v.get("loc.z")).primaryTileBiome().name());
                });

        Placement.registerCondition("Zone", "Only inside these zones.", List.of(param("zones", "strings", "", "Zone ids; empty = any")),
                (c, ctx) -> {
                    List<String> ids = strings(c, "zones");
                    return (env, v) -> {
                        if (ids.isEmpty()) return true;
                        var zl = env.ws.getZoneLayout();
                        var z = zl == null ? null : zl.zoneAt((float) v.get("loc.x"), (float) v.get("loc.z"));
                        return z != null && ids.contains(z.id);
                    };
                });

        Placement.registerCondition("Surface", "The ground height at the find lies in a range.", List.of(
                param("minY", "int", -2048, "Lowest ground height"), param("maxY", "int", 4096, "Highest ground height")),
                (c, ctx) -> {
                    int lo = integer(c, "minY", -2048), hi = integer(c, "maxY", 4096);
                    return (env, v) -> {
                        int y = env.surface((int) v.get("loc.x"), (int) v.get("loc.z"));
                        return y >= lo && y <= hi;
                    };
                });

        Placement.registerCondition("Wet", "The find is (or is not) under water.", List.of(param("wet", "bool", false, "true = must be under water")),
                (c, ctx) -> {
                    boolean want = bool(c, "wet", false);
                    return (env, v) -> env.wet((int) v.get("loc.x"), (int) v.get("loc.z")) == want;
                });

        Placement.registerCondition("Density", "A density node graph, sampled at the find, lies in a range. Build any shape or pattern with nodes.",
                List.of(param("min", "number", 0.0, "Accept when the value is at least this"), param("max", "number", 1000.0, "...and at most this")),
                List.of(Placement.slot("graph", "density")),
                (c, ctx) -> {
                    JsonObject g = c.has("graph") && c.get("graph").isJsonObject() ? c.getAsJsonObject("graph") : null;
                    if (g == null) { ctx.warn("A Density condition has no graph, so it accepts everything."); return (env, v) -> true; }
                    DensityFunction df = ctx.density(g);
                    double lo = number(c, "min", 0.0), hi = number(c, "max", 1000.0);
                    return (env, v) -> {
                        double d = df.compute((float) v.get("loc.x"), env.seaLevel, (float) v.get("loc.z"));
                        return d >= lo && d <= hi;
                    };
                });

        Placement.registerCondition("StructureNear", "A placed structure (spawn, landmarks, points of interest) is near the find, or with invert, none is.",
                List.of(param("structure", "string", "", "Placement id contains this; empty = any"), param("radius", "int", 32, "Blocks around the find"),
                        param("invert", "bool", false, "true = no such structure may be near")),
                (c, ctx) -> {
                    String filter = string(c, "structure", "");
                    int radius = integer(c, "radius", 32);
                    boolean invert = bool(c, "invert", false);
                    return (env, v) -> {
                        int x = (int) v.get("loc.x"), z = (int) v.get("loc.z");
                        boolean near = false;
                        for (StructurePlacement p : env.ws.getStructurePlacements()) {
                            if (!filter.isEmpty() && !p.id().contains(filter)) continue;
                            var a = p.prefab().anchor();
                            var s = p.prefab().size();
                            int bx = p.x() - a.x(), bz = p.z() - a.z();
                            if (x >= bx - radius && x <= bx + s.x() + radius && z >= bz - radius && z <= bz + s.z() + radius) { near = true; break; }
                        }
                        return near != invert;
                    };
                });

        Placement.registerCondition("Expression", "Any expression over the find's variables must be true, e.g. span.length >= 6 && span.level > 70.",
                List.of(param("expr", "string", "1", "Expression")),
                (c, ctx) -> {
                    Expr e = Expr.parse(string(c, "expr", "1"));
                    return (env, v) -> e.test(v);
                });

        // ------------------------------------------------------------ actions
        Placement.registerAction("Prefab", "Place a prefab: chosen from a list by an expression, turned, offset and set on the ground or on a computed height.",
                List.of(
                        param("from", "strings", "", "Prefab ids to choose from"),
                        param("name", "string", "placed", "Name for the variables this records (name.x, name.length, name.socket.root.x ...)"),
                        param("require", "string", "1", "Expression a prefab must satisfy to be chosen, e.g. prefab.height >= log.length"),
                        param("rotate", "string", "none", "none, random, alignSpan, or 0/90/180/270"),
                        param("flip", "bool", false, "With alignSpan: randomly turn it end for end"),
                        param("at", "string", "point", "point, span.center, <name>.center or <name>.socket.<socket>"),
                        param("alongMin", "int", 0, "Offset along the line away from the reference (min)"),
                        param("alongMax", "int", 0, "...(max)"),
                        param("sideMin", "int", 0, "Offset sideways, random side (min)"),
                        param("sideMax", "int", 0, "...(max)"),
                        param("count", "int", 1, "How many to place"),
                        param("height", "string", "ground", "Expression for the height of the anchor's block; ground = surface at the spot"),
                        param("yOffset", "int", 0, "Added to the height"),
                        param("align", "string", "anchor", "anchor = prefab anchor at the spot; center = prefab centred on it"),
                        param("onLand", "bool", true, "Skip spots that are under water"),
                        param("optional", "bool", false, "When it cannot be placed, carry on instead of cancelling the rule")),
                (c, ctx) -> prefabAction(c, ctx));
    }

    // ================================================================== Span finder

    private static Finder spanFinder(JsonObject c) {
        String axisCfg = string(c, "axis", "any");
        int minGap = integer(c, "minGap", 4), maxGap = Math.max(minGap, integer(c, "maxGap", 12));
        int maxDiff = integer(c, "maxBankDiff", 3), maxBank = integer(c, "maxBankHeight", 3);
        return new Finder() {
            @Override public int reach() { return maxGap + 16; }

            @Override public Vars find(Env env, int px, int pz, SplittableRandom rng) {
                boolean firstX = axisCfg.equals("x") || (!axisCfg.equals("z") && rng.nextBoolean());
                int tries = axisCfg.equals("any") ? 2 : 1;
                for (int a = 0; a < tries; a++) {
                    Vars v = tryAxis(env, px, pz, a == 0 ? firstX : !firstX);
                    if (v != null) return v;
                }
                return null;
            }

            private Vars tryAxis(Env env, int px, int pz, boolean axisX) {
                int dx = axisX ? 1 : 0, dz = axisX ? 0 : 1;
                int reach = maxGap + 8;
                int bestStart = Integer.MIN_VALUE, bestEnd = 0;
                long bestScore = Long.MAX_VALUE;
                int runStart = Integer.MIN_VALUE;
                for (int t = -reach; t <= reach + 2; t += 2) {
                    boolean wet = t <= reach && env.wet(px + dx * t, pz + dz * t);
                    if (wet && runStart == Integer.MIN_VALUE) runStart = t;
                    if (!wet && runStart != Integer.MIN_VALUE) {
                        int s = runStart, e = t - 2;
                        runStart = Integer.MIN_VALUE;
                        if (env.wet(px + dx * (s - 1), pz + dz * (s - 1))) s--;
                        if (env.wet(px + dx * (e + 1), pz + dz * (e + 1))) e++;
                        if (s <= -reach || e >= reach) continue;
                        int len = e - s + 1;
                        if (len < minGap || len > maxGap) continue;
                        long score = Math.abs((long) (s + e) / 2);
                        if (score < bestScore) { bestScore = score; bestStart = s; bestEnd = e; }
                    }
                }
                if (bestStart == Integer.MIN_VALUE) return null;
                int s = bestStart, e = bestEnd, len = e - s + 1, mid = (s + e) / 2;
                int gx = px + dx * mid, gz = pz + dz * mid;
                int level = env.waterLevel(gx, gz);
                int ax = px + dx * (s - 1), az = pz + dz * (s - 1), bx = px + dx * (e + 1), bz = pz + dz * (e + 1);
                if (env.wet(ax, az) || env.wet(bx, bz) || env.wet(px + dx * (s - 2), pz + dz * (s - 2)) || env.wet(px + dx * (e + 2), pz + dz * (e + 2))) return null;
                int hA = env.surface(ax, az), hB = env.surface(bx, bz);
                if (Math.abs(hA - hB) > maxDiff || hA > level + maxBank || hB > level + maxBank) return null;
                for (int lat : new int[]{-3, 3}) {          // straight across, not a diagonal bend
                    int lx = px + (axisX ? 0 : lat), lz = pz + (axisX ? lat : 0);
                    if (!env.wet(lx + dx * mid, lz + dz * mid)) return null;
                    if (env.wet(lx + dx * (s - 1), lz + dz * (s - 1)) || env.wet(lx + dx * (e + 1), lz + dz * (e + 1))) return null;
                }
                return new Vars().set("point.x", px).set("point.z", pz).set("loc.x", gx).set("loc.z", gz)
                        .set("span.length", len).set("span.axisX", axisX ? 1 : 0).set("span.centerX", gx).set("span.centerZ", gz)
                        .set("span.hA", hA).set("span.hB", hB).set("span.bankTop", Math.max(hA, hB)).set("span.level", level)
                        .set("ground", env.surface(gx, gz));
            }
        };
    }

    // ================================================================== Prefab action

    private static Action prefabAction(JsonObject c, Placement.Ctx ctx) {
        List<Prefab> from = new ArrayList<>();
        for (String id : strings(c, "from")) {
            Prefab p = ctx.prefab(id);
            if (p != null) from.add(p);
        }
        final String name = string(c, "name", "placed");
        final Expr require = Expr.parse(string(c, "require", "1"));
        final Expr height = Expr.parse(string(c, "height", "ground"));
        final String rotate = string(c, "rotate", "none");
        final boolean flip = bool(c, "flip", false), onLand = bool(c, "onLand", true), optional = bool(c, "optional", false);
        final boolean center = string(c, "align", "anchor").equals("center");
        final String at = string(c, "at", "point");
        final int alongMin = integer(c, "alongMin", 0), alongMax = Math.max(alongMin, integer(c, "alongMax", 0));
        final int sideMin = integer(c, "sideMin", 0), sideMax = Math.max(sideMin, integer(c, "sideMax", 0));
        final int count = Math.max(1, integer(c, "count", 1)), yOffset = integer(c, "yOffset", 0);
        int dim = 0;
        for (Prefab p : from) dim = Math.max(dim, Math.max(p.size().x(), Math.max(p.size().y(), p.size().z())));
        final int reach = dim + alongMax + sideMax + 16;

        return new Action() {
            @Override public int reach() { return reach; }

            @Override public boolean run(Env env, Vars vars, SplittableRandom rng, List<StructurePlacement> out) {
                if (from.isEmpty()) return optional;
                int placed = 0;
                for (int attempt = 0; attempt < count * 4 && placed < count; attempt++) {
                    List<Prefab> ok = new ArrayList<>();
                    for (Prefab p : from) if (require.test(withPrefab(vars, p))) ok.add(p);
                    if (ok.isEmpty()) return optional;
                    Prefab base = ok.get(rng.nextInt(ok.size()));

                    int turns = turnsFor(rotate, flip, base, vars, rng);
                    Prefab turned = base.rotated(turns);
                    double[] ref = resolve(at, vars);
                    if (ref == null) return optional;
                    int along = alongMin + (alongMax > alongMin ? rng.nextInt(alongMax - alongMin + 1) : 0);
                    int side = sideMin + (sideMax > sideMin ? rng.nextInt(sideMax - sideMin + 1) : 0);
                    if (rng.nextBoolean()) side = -side;
                    int tx = (int) Math.round(ref[0] + ref[2] * along - ref[3] * side);
                    int tz = (int) Math.round(ref[1] + ref[3] * along + ref[2] * side);
                    if (onLand && env.wet(tx, tz)) continue;

                    Vars local = vars.copy().set("ground", env.surface(tx, tz)).set("target.x", tx).set("target.z", tz);
                    int y = (int) Math.round(height.eval(local)) + yOffset + turned.anchor().y();
                    int baseX = center ? tx - turned.size().x() / 2 : tx - turned.anchor().x();
                    int baseZ = center ? tz - turned.size().z() / 2 : tz - turned.anchor().z();
                    int ax = baseX + turned.anchor().x(), az = baseZ + turned.anchor().z();
                    out.add(new StructurePlacement(name + "_" + tx + "_" + tz, turned, ax, y, az));
                    if (placed == 0) record(vars, name, turned, ax, y, az, baseX, baseZ);
                    placed++;
                }
                vars.set(name + ".count", placed);
                return placed > 0 || optional;
            }
        };
    }

    private static Vars withPrefab(Vars v, Prefab p) {
        int l = Math.max(p.size().x(), p.size().z());
        return v.copy().set("prefab.length", l).set("prefab.width", Math.min(p.size().x(), p.size().z())).set("prefab.height", p.size().y())
                .set("prefab.sizeX", p.size().x()).set("prefab.sizeY", p.size().y()).set("prefab.sizeZ", p.size().z());
    }

    private static int turnsFor(String rotate, boolean flip, Prefab base, Vars v, SplittableRandom rng) {
        switch (rotate) {
            case "random": return rng.nextInt(4);
            case "alignSpan": {
                boolean longIsX = base.size().x() >= base.size().z();
                boolean axisX = v.get("span.axisX") != 0;
                return (longIsX == axisX ? 0 : 1) + (flip && rng.nextBoolean() ? 2 : 0);
            }
            case "none": return 0;
            default:
                try { return Integer.parseInt(rotate.trim()) / 90; } catch (NumberFormatException e) { return 0; }
        }
    }

    /** Reference point and outward direction: {x, z, dirX, dirZ}. */
    private static double[] resolve(String at, Vars v) {
        double dx = Double.isNaN(v.get("span.axisX")) || v.get("span.axisX") != 0 ? 1 : 0;
        double dz = 1 - dx;
        if (at.equals("point")) return new double[]{v.get("loc.x"), v.get("loc.z"), dx, dz};
        if (at.equals("span.center")) return new double[]{v.get("span.centerX"), v.get("span.centerZ"), dx, dz};
        int i = at.indexOf(".socket.");
        if (i > 0) {
            String n = at.substring(0, i), sock = at.substring(i + 8);
            double sx = v.get(n + ".socket." + sock + ".x"), sz = v.get(n + ".socket." + sock + ".z");
            double cx = v.get(n + ".centerX"), cz = v.get(n + ".centerZ");
            if (Double.isNaN(sx) || Double.isNaN(cx)) return null;
            double ox = sx - cx, oz = sz - cz;
            if (Math.abs(ox) >= Math.abs(oz)) return new double[]{sx, sz, Math.signum(ox) == 0 ? 1 : Math.signum(ox), 0};
            return new double[]{sx, sz, 0, Math.signum(oz) == 0 ? 1 : Math.signum(oz)};
        }
        if (at.endsWith(".center")) {
            String n = at.substring(0, at.length() - 7);
            double cx = v.get(n + ".centerX"), cz = v.get(n + ".centerZ");
            return Double.isNaN(cx) ? null : new double[]{cx, cz, dx, dz};
        }
        return null;
    }

    private static void record(Vars v, String n, Prefab turned, int ax, int y, int az, int baseX, int baseZ) {
        v.set(n + ".x", ax).set(n + ".y", y).set(n + ".z", az)
                .set(n + ".centerX", baseX + turned.size().x() / 2.0).set(n + ".centerZ", baseZ + turned.size().z() / 2.0)
                .set(n + ".length", Math.max(turned.size().x(), turned.size().z())).set(n + ".width", Math.min(turned.size().x(), turned.size().z()))
                .set(n + ".height", turned.size().y());
        for (Prefab.PrefabSocket s : turned.sockets()) {
            v.set(n + ".socket." + s.name() + ".x", baseX + s.position().x()).set(n + ".socket." + s.name() + ".z", baseZ + s.position().z());
        }
    }

    // ================================================================== config helpers

    static String string(JsonObject c, String k, String def) { return c.has(k) && c.get(k).isJsonPrimitive() ? c.get(k).getAsString() : def; }

    static int integer(JsonObject c, String k, int def) {
        try { return c.has(k) && c.get(k).isJsonPrimitive() ? (int) Math.round(c.get(k).getAsDouble()) : def; } catch (NumberFormatException e) { return def; }
    }

    static double number(JsonObject c, String k, double def) {
        try { return c.has(k) && c.get(k).isJsonPrimitive() ? c.get(k).getAsDouble() : def; } catch (NumberFormatException e) { return def; }
    }

    static boolean bool(JsonObject c, String k, boolean def) { return c.has(k) && c.get(k).isJsonPrimitive() ? c.get(k).getAsBoolean() : def; }

    /** A list of strings given as a JSON array or as one comma-separated string. */
    static List<String> strings(JsonObject c, String k) {
        List<String> out = new ArrayList<>();
        JsonElement e = c.get(k);
        if (e == null) return out;
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive() && !x.getAsString().isBlank()) out.add(x.getAsString().trim());
        } else if (e.isJsonPrimitive()) {
            for (String s : e.getAsString().split(",")) if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    static JsonArray array(JsonObject c, String k) {
        JsonArray a = new JsonArray();
        for (String s : strings(c, k)) a.add(s);
        return a;
    }
}
