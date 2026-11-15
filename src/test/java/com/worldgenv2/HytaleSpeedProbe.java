package com.worldgenv2;

import com.worldgenv2.biome.HytaleImport;
import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.density.DensityFunction;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Dev probe: how long a 16x16 chunk of each real Hytale biome's terrain takes, with a sampling profile of the slowest. */
public final class HytaleSpeedProbe {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        JsonAstCompiler.registerBuiltInTypes();
        Path root = Path.of("hytale_local/Server/HytaleGenerator");
        Map<String, Map<String, Object>> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".json"))::iterator) {
                try { files.put(root.relativize(p).toString().replace('\\', '/'), (Map<String, Object>) HytaleAssetConformanceProbe.strip(new JsonAstCompiler.SimpleJsonParser(Files.readString(p)).parseValue())); } catch (Throwable t) { }
            }
        }
        JsonAstCompiler.CompileContext ctx = new JsonAstCompiler.CompileContext(1L);
        for (var e : files.entrySet()) JsonAstCompiler.collectExports(e.getValue(), ctx);
        ctx.decimalConstants().put("Base", 110.0); ctx.decimalConstants().put("Water", 100.0); ctx.decimalConstants().put("Bedrock", 0.0);
        try {
            @SuppressWarnings("unchecked") var bm = JsonAstCompiler.compileDensity((Map<String, Object>) HytaleAssetConformanceProbe.strip(new JsonAstCompiler.SimpleJsonParser("{\"Type\":\"Imported\",\"Name\":\"Biome-Map\"}").parseValue()), ctx);
            for (int i = 0; i < 20; i++) bm.compute(i, 0, i);
            long t0 = System.nanoTime();
            for (int x = 0; x < 18; x++) for (int z = 0; z < 18; z++) for (int k = 0; k < 5; k++) bm.compute(3000 + x + k, 0, 3000 + z);
            System.out.println("SPEED Biome-Map 1620 evals " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        } catch (Throwable t) { System.out.println("SPEED Biome-Map fail " + t); }        String only = args.length > 0 ? args[0] : null;
        String slowest = null; long slowMs = 0; DensityFunction slowD = null;
        for (var e : files.entrySet()) {
            if (!e.getKey().startsWith("Biomes/")) continue;
            if (only != null && !e.getKey().contains(only)) continue;
            var form = HytaleImport.biomeToEngineForm(e.getValue());
            if (!form.containsKey("terrain")) continue;
            try {
                var d = JsonAstCompiler.<String>compileBiome(2, form, ctx, new HashMap<>(), id -> id).tileBiome().terrainDensity();
                try { var cb2 = JsonAstCompiler.<String>compileBiome(2, form, ctx, new HashMap<>(), id -> id); for (var pr : cb2.tileBiome().<String>typedProps()) { var u = com.worldgenv2.props.runtime.PropRuntime.unwrap(pr); if (u instanceof com.worldgenv2.props.Distribution<?>) System.out.println("REACH " + e.getKey().replace("Biomes/", "") + " stage " + com.worldgenv2.props.runtime.PropRuntime.stageOf(pr) + " horiz " + pr.horizontalReach() + " content " + pr.contentReach()); } } catch (Throwable ex) { System.out.println("REACH fail " + ex); }
                float[] col = new float[320];
                for (int i = 0; i < 4; i++) d.computeColumn(i, i, 0, 320, col);
                long t0 = System.nanoTime();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) d.computeColumn(1000 + x, 1000 + z, 0, 320, col);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.println("SPEED " + e.getKey().replace("Biomes/", "") + " " + ms + " ms/chunk");
                if (ms > slowMs) { slowMs = ms; slowest = e.getKey(); slowD = d; }
            } catch (Throwable t) { }
        }
        if (slowD == null) return;
        System.out.println("PROFILING " + slowest);
        Thread main = Thread.currentThread();
        Map<String, Integer> hot = new HashMap<>(), self = new HashMap<>();
        boolean[] run = {true};
        Thread sampler = new Thread(() -> {
            while (run[0]) {
                StackTraceElement[] st = main.getStackTrace();
                Set<String> seen = new HashSet<>();
                boolean first = true;
                for (StackTraceElement f : st) {
                    if (!f.getClassName().startsWith("com.worldgenv2")) continue;
                    String k = f.getClassName().replace("com.worldgenv2.", "") + "." + f.getMethodName();
                    if (first) { self.merge(k, 1, Integer::sum); first = false; }
                    if (seen.add(k)) hot.merge(k, 1, Integer::sum);
                }
                try { Thread.sleep(2); } catch (InterruptedException ex) { return; }
            }
        });
        sampler.setDaemon(true); sampler.start();
        float[] col = new float[320];
        for (int rep = 0; rep < 6; rep++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) slowD.computeColumn(2000 + x, 2000 + z, 0, 320, col);
        run[0] = false;
        hot.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(14).forEach(e -> System.out.println("INCL " + e.getValue() + " " + e.getKey()));
        self.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(14).forEach(e -> System.out.println("SELF " + e.getValue() + " " + e.getKey()));
    }
}