package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.worldstructure.RiverCarver;

import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Dev probe: draws the river network of the shipped river graph (and times it). */
public final class RiverGraphProbe {
    public static void main(String[] a) throws Exception {
        JsonAstCompiler.registerBuiltInTypes();
        String json = Files.readString(Path.of(a.length > 0 ? a[0] : "src/main/resources/assets/worldgenv2/default_river_graph.json"));
        RiverCarver.Settings st = RiverCarver.Settings.defaults();
        RiverCarver rc = new RiverCarver(1337L, 63, st).useGraph(JsonAstCompiler.compileGraphGenerator(new JsonAstCompiler.SimpleJsonParser(json).parseValue(), new JsonAstCompiler.CompileContext(1337L), id -> id), 1337L, st);
        rc.setHeightSampler((x, z) -> 70f);
        int size = 1024, step = 8, half = size * step / 2;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        long t0 = System.nanoTime();
        for (int i = 0; i < size; i++) for (int k = 0; k < size; k++) {
            float x = i * step - half, z = k * step - half;
            float d = rc.riverDistance(x, z);
            int c = rc.isChannel(x, z) ? 0x2060FF : d < 0.999f ? 0x80A060 : 0x305030;
            img.setRGB(i, k, c);
        }
        System.out.println("RIVERPROBE " + (size * size) + " samples in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        javax.imageio.ImageIO.write(img, "png", new java.io.File("run/devbridge/river_graph_map.png"));
        double best = 1e18; float bx = 0, bz = 0;
        for (int i = 0; i < size; i++) for (int k = 0; k < size; k++) { float x = i * step - half, z = k * step - half; if (rc.isChannel(x, z) && x * x + z * z < best) { best = x * x + z * z; bx = x; bz = z; } }
        System.out.println("RIVERPROBE nearest channel to origin: " + bx + " " + bz);
        java.util.List<float[]> picks = new java.util.ArrayList<>();
        for (int i = 0; i < size && picks.size() < 14; i += 3) for (int k = 0; k < size && picks.size() < 14; k += 3) {
            float x = i * step - half, z = k * step - half;
            if (Math.abs(x) > 3000 || Math.abs(z) > 3000 || !rc.isChannel(x, z)) continue;
            boolean far = true;
            for (float[] p : picks) if (Math.hypot(p[0] - x, p[1] - z) < 700) far = false;
            if (far) picks.add(new float[]{x, z});
        }
        for (float[] p : picks) System.out.println("RIVERPROBE pick " + (int) p[0] + " " + (int) p[1]);
        long t1 = System.nanoTime();
        for (int i = 0; i < 256; i++) { rc.riverDistance(500 + i % 16, 800 + i / 16); rc.isChannel(500 + i % 16, 800 + i / 16); rc.levelAt(500 + i % 16, 800 + i / 16); }
        System.out.println("RIVERPROBE one chunk of columns: " + (System.nanoTime() - t1) / 1_000_000 + " ms");
    }
}