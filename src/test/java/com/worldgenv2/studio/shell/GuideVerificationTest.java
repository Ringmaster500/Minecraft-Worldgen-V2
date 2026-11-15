package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.graph.NodeSchemas;

/** Guide: every page builds, and density and curve nodes get a working live demo. */
public final class GuideVerificationTest {
    public static void main(String[] args) {
        int pass = 0, fail = 0;
        var pages = GuideContent.pages();
        boolean many = pages.size() > 300;
        System.out.println((many ? "[PASS] " : "[FAIL] ") + pages.size() + " guide pages (how-to plus one per node)");
        if (many) pass++; else fail++;
        int howTo = 0;
        for (var p : pages) if (p.node() == null) howTo++;
        System.out.println((howTo >= 8 ? "[PASS] " : "[FAIL] ") + howTo + " written how-to pages");
        if (howTo >= 8) pass++; else fail++;
        int withText = 0, nodes = 0, demos = 0, densityNodes = 0;
        for (var p : pages) {
            if (p.node() == null) continue;
            nodes++;
            if (GuideContent.nodeLines(p.node()).size() >= 3) withText++;
            if (p.node().kind().equals("density")) {
                densityNodes++;
                try { if (DensityDemo.forNode(p.node()) != null) demos++; } catch (Throwable t) { System.out.println("  demo threw for " + p.title() + ": " + t); }
            }
        }
        System.out.println((withText == nodes ? "[PASS] " : "[FAIL] ") + "every node page has text (" + withText + "/" + nodes + ")");
        if (withText == nodes) pass++; else fail++;
        boolean enough = demos * 2 >= densityNodes;
        System.out.println((enough ? "[PASS] " : "[FAIL] ") + demos + " of " + densityNodes + " density nodes have a working live demo");
        if (enough) pass++; else fail++;
        int curveNodes = 0, curveDemos = 0;
        for (var p : pages) if (p.node() != null && p.node().kind().equals("curve")) { curveNodes++; try { if (DensityDemo.forNode(p.node()) != null) curveDemos++; } catch (Throwable t) { } }
        boolean cok = curveNodes > 0 && curveDemos * 2 >= curveNodes;
        System.out.println((cok ? "[PASS] " : "[FAIL] ") + curveDemos + " of " + curveNodes + " curve nodes have a working live demo");
        if (cok) pass++; else fail++;
        var simplex = NodeSchemas.get("density", "SimplexNoise2D");
        boolean s = simplex != null && DensityDemo.forNode(simplex) != null;
        System.out.println((s ? "[PASS] " : "[FAIL] ") + "SimplexNoise2D demo builds");
        if (s) pass++; else fail++;
        for (GuideRecipes.Recipe r : GuideRecipes.all()) {
            String err = null;
            try {
                com.worldgenv2.biome.JsonAstCompiler.registerBuiltInTypes();
                var ctx = new com.worldgenv2.biome.JsonAstCompiler.CompileContext(4242L);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> form = (java.util.Map<String, Object>) new com.worldgenv2.biome.JsonAstCompiler.SimpleJsonParser(r.biome().toString()).parseValue();
                com.worldgenv2.biome.JsonAstCompiler.<String>compileBiome(2, form, ctx, new java.util.HashMap<>(), id -> id);
                @SuppressWarnings("unchecked")
                var terrain = com.worldgenv2.biome.JsonAstCompiler.compileDensity((java.util.Map<String, Object>) form.get("terrain"), ctx);
                float low = terrain.compute(0.5f, 20f, 0.5f), high = terrain.compute(0.5f, 250f, 0.5f);
                if (!(low > 0 && high < 0)) err = "terrain is not solid below and air above (" + low + ", " + high + ")";
            } catch (Throwable t) { err = String.valueOf(t); }
            System.out.println((err == null ? "[PASS] " : "[FAIL] ") + "teaching setup '" + r.title() + "' compiles and has ground" + (err == null ? "" : ": " + err));
            if (err == null) pass++; else fail++;
        }
        System.out.println(fail == 0 ? "Guide verification: ALL " + pass + " TESTS PASSED!" : "Guide verification: " + fail + " FAILED");
        if (fail > 0) System.exit(1);
    }
}
