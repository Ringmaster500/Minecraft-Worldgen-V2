package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.biome.WorldGenV2Registries;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Dev probe: which documented Hytale node names have no registered type. */
public final class HytaleCoverageProbe {
    public static void main(String[] a) throws Exception {
        JsonAstCompiler.registerBuiltInTypes();
        String d = "docs/research/hytale_generator_docs/";
        Object[][] fam = {{"density.md", WorldGenV2Registries.DENSITY_NODE_TYPE}, {"curve.md", WorldGenV2Registries.CURVE_TYPE}, {"vector-provider.md", WorldGenV2Registries.VECTOR_PROVIDER_TYPE},
                {"material-provider.md", WorldGenV2Registries.MATERIAL_PROVIDER_TYPE}, {"pattern.md", WorldGenV2Registries.PATTERN_TYPE}, {"prop.md", WorldGenV2Registries.PROP_TYPE},
                {"positions-provider.md", WorldGenV2Registries.POSITIONS_PROVIDER_TYPE}, {"scanner.md", WorldGenV2Registries.SCANNER_TYPE}, {"assignments.md", WorldGenV2Registries.ASSIGNMENTS_TYPE}};
        for (Object[] f : fam) {
            List<String> missing = new ArrayList<>();
            Matcher m = Pattern.compile("(?m)^### \\[([A-Za-z0-9]+)\\]").matcher(Files.readString(Path.of(d + f[0])));
            int n = 0;
            Object reg = f[1];
            var get = reg.getClass().getMethod("get", String.class);
            while (m.find()) { n++; if (get.invoke(reg, m.group(1)) == null) missing.add(m.group(1)); }
            System.out.println("COVERAGE " + f[0] + ": " + n + " documented, missing " + missing);
        }
    }
}