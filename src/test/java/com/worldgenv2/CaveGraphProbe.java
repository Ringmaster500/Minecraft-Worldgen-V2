package com.worldgenv2;

import com.worldgenv2.biome.JsonAstCompiler;
import com.worldgenv2.worldstructure.GraphCaveCarver;

import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Dev probe: slices of the shipped cave graph (and timing). */
public final class CaveGraphProbe {
    public static void main(String[] a) throws Exception {
        JsonAstCompiler.registerBuiltInTypes();
        String json = Files.readString(Path.of("src/main/resources/assets/worldgenv2/default_cave_graph.json"));
        var gen = JsonAstCompiler.compileGraphGenerator(new JsonAstCompiler.SimpleJsonParser(json).parseValue(), new JsonAstCompiler.CompileContext(1337L), id -> id);
        GraphCaveCarver cc = new GraphCaveCarver(gen, "caves", 1337L, 7);
        var cell = gen.getGraphForCell(1337L, 0, -1, 0);
        System.out.println("CAVEPROBE cell nodes " + cell.nodeCount() + " edges " + cell.edgeCount() + " segs " + cell.getSegments().size());
        int shown = 0;
        for (var n : cell.getNodes()) { if (shown++ < 3) System.out.println("CAVEPROBE node " + n.getPosition() + " content " + n.getContent().getDensityContent().size() + " conns " + n.getConnectionCount()); }
        var ents = cell.getNodes().iterator().next().getContent().getDensityContent();
        if (!ents.isEmpty()) System.out.println("CAVEPROBE entry range " + ents.get(0).range() + " value at node " + ents.get(0).density().compute((float) cell.getNodes().iterator().next().getPosition().x(), 0f, 0f));
        var rd = new com.worldgenv2.density.GraphDensity(gen, "caves", 1.0f, 1337L).withTransition(1.0, 0.0, 0.0);
        int dbg = 0;
        for (var n : cell.getNodes()) { if (dbg++ >= 4) break; var np = n.getPosition(); var near0 = gen.nodesNearColumn(1337L, np.x(), np.z(), 64, -64, 320); System.out.println("CAVEPROBE blend at node " + rd.blend(near0, (float) np.x(), (float) np.y(), (float) np.z()) + " near " + near0.size() + " entry " + (n.getContent().getFirstDensity("caves").isPresent())); }
        int minY = -64, maxY = 320, h = maxY - minY;
        int size = 96, step = 4;
        BufferedImage top = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BufferedImage side = new BufferedImage(size, h, BufferedImage.TYPE_INT_RGB);
        long t0 = System.nanoTime();
        long carvedTotal = 0; double bestD = 1e18; float bx = 0, bz = 0; int by = 0;
        for (int i = 0; i < size; i++) for (int k = 0; k < size; k++) {
            float x = (i - size / 2) * step, z = (k - size / 2) * step;
            float[] col = new float[h];
            java.util.Arrays.fill(col, 1f);
            cc.carveColumn(x, z, minY, maxY, col);
            int n = 0;
            for (float v : col) if (v < 0) n++;
            carvedTotal += n;
            if (n > 0 && x * x + z * z < bestD) { bestD = x * x + z * z; bx = x; bz = z; for (int y = 0; y < h; y++) if (col[y] < 0) { by = minY + y; break; } }
            top.setRGB(i, k, n == 0 ? 0x203020 : 0x20 | Math.min(255, 60 + n * 3) << 8 | 0xFF << 16 & 0);
            if (k == size / 2) for (int y = 0; y < h; y++) side.setRGB(i, h - 1 - y, col[y] < 0 ? 0xE0E0E0 : 0x303030);
        }
        System.out.println("CAVEPROBE fraction " + (carvedTotal / (double) ((long) size * size * 108)));
        System.out.println("CAVEPROBE " + size * size + " columns in " + (System.nanoTime() - t0) / 1_000_000 + " ms, carved cells " + carvedTotal);
        System.out.println("CAVEPROBE nearest cave column " + bx + " " + bz + " y " + by);
        javax.imageio.ImageIO.write(top, "png", new java.io.File("run/devbridge/cave_top.png"));
        javax.imageio.ImageIO.write(side, "png", new java.io.File("run/devbridge/cave_side.png"));
    }
}