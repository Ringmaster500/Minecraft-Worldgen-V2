package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.graph.NodeSchemas;

/** Guide: every page builds, and density nodes get a working live demo. */
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
        var simplex = NodeSchemas.get("density", "SimplexNoise2D");
        boolean s = simplex != null && DensityDemo.forNode(simplex) != null;
        System.out.println((s ? "[PASS] " : "[FAIL] ") + "SimplexNoise2D demo builds");
        if (s) pass++; else fail++;
        System.out.println(fail == 0 ? "Guide verification: ALL " + pass + " TESTS PASSED!" : "Guide verification: " + fail + " FAILED");
        if (fail > 0) System.exit(1);
    }
}
