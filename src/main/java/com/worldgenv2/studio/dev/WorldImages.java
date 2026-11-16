package com.worldgenv2.studio.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pictures of the world that a first-person screenshot cannot give: a top-down map, a vertical cut, and an isometric-style view
 * of the terrain. Drawn straight from the world's blocks (generating chunks as needed), so what you see is what was generated.
 *
 * <pre>
 *   GET /topdown?x=&z=&size=256&step=1      map seen from above (x, z = centre; default: the player). Colours are the blocks' map colours,
 *                                           shaded by slope, water darker with depth, grid lines every 64 blocks, red dot = player.
 *   GET /side?x=&z=&dir=x|z&len=128&y0=&y1=&scale=4    a vertical cut starting at (x, z) running along X or Z.
 *   GET /iso?x=&z=&size=192&step=2          the terrain drawn as columns in an isometric-style view.
 *   All three take dim=minecraft:the_nether (or any dimension id) to draw another dimension.
 * </pre>
 */
final class WorldImages {
    private WorldImages() {}

    static byte[] render(String path, Map<String, String> q) throws Exception {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || Minecraft.getInstance().player == null) throw new IllegalStateException("open a world first");
        double px = Minecraft.getInstance().player.getX(), pz = Minecraft.getInstance().player.getZ();
        BufferedImage img = server.submit(() -> {
            ServerLevel level = dimension(server, q.getOrDefault("dim", "minecraft:overworld"));
            return switch (path) {
                case "/topdown" -> topdown(level, q, px, pz);
                case "/side" -> side(level, q, px, pz);
                default -> iso(level, q, px, pz);
            };
        }).get(240, TimeUnit.SECONDS);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static ServerLevel dimension(MinecraftServer server, String id) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(id));
        ServerLevel l = server.getLevel(key);
        return l != null ? l : server.overworld();
    }

    private static int geti(Map<String, String> q, String k, int def) {
        try { return q.containsKey(k) ? (int) Math.round(Double.parseDouble(q.get(k))) : def; } catch (NumberFormatException e) { return def; }
    }

    private static int mapColor(ServerLevel level, BlockState s, BlockPos pos) {
        if (s.isAir()) return 0x000000;
        MapColor c = s.getMapColor(level, pos);
        return c == null || c == MapColor.NONE ? 0x808080 : c.col;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) * f)), g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) * f)), b = (int) Math.max(0, Math.min(255, (rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** The topmost non-air block of a column (leaves and trees included), with its Y. */
    private static int surfaceY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    private static BufferedImage topdown(ServerLevel level, Map<String, String> q, double ppx, double ppz) {
        int size = Math.max(16, Math.min(640, geti(q, "size", 256)));
        int step = Math.max(1, geti(q, "step", Math.max(1, size / 512)));
        int cx = geti(q, "x", (int) ppx), cz = geti(q, "z", (int) ppz);
        int n = size / step;
        int scale = Math.max(1, 768 / n);
        int x0 = cx - size / 2, z0 = cz - size / 2;
        BufferedImage img = new BufferedImage(n * scale, n * scale, BufferedImage.TYPE_INT_RGB);
        int[] prevRow = new int[n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int wx = x0 + i * step, wz = z0 + j * step;
                level.getChunk(wx >> 4, wz >> 4);
                int y = surfaceY(level, wx, wz);
                BlockPos pos = new BlockPos(wx, y, wz);
                BlockState s = level.getBlockState(pos);
                int rgb = mapColor(level, s, pos);
                if (!s.getFluidState().isEmpty()) {
                    int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, wx, wz) - 1;
                    double depth = Math.max(0, y - floor);
                    rgb = shade(0x3F76E4, Math.max(0.35, 1.0 - depth * 0.03));
                } else {
                    double slope = j == 0 ? 0 : (y - prevRow[i]);
                    rgb = shade(rgb, Math.max(0.7, Math.min(1.3, 1.0 + slope * 0.06)));
                }
                prevRow[i] = y;
                int argb = 0xFF000000 | rgb;
                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) img.setRGB(i * scale + dx, j * scale + dy, argb);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 255, 255, 70));
        for (int gx = (x0 / 64 + 1) * 64; gx < x0 + size; gx += 64) g.drawLine((gx - x0) / step * scale, 0, (gx - x0) / step * scale, n * scale);
        for (int gz = (z0 / 64 + 1) * 64; gz < z0 + size; gz += 64) g.drawLine(0, (gz - z0) / step * scale, n * scale, (gz - z0) / step * scale);
        g.setColor(Color.RED);
        int ppx2 = ((int) ppx - x0) / step * scale, ppz2 = ((int) ppz - z0) / step * scale;
        g.fillOval(ppx2 - 4, ppz2 - 4, 9, 9);
        g.setColor(Color.WHITE);
        g.drawString("x " + x0 + " .. " + (x0 + size) + "   z " + z0 + " .. " + (z0 + size) + "   (grid every 64)", 6, 14);
        g.dispose();
        return img;
    }

    private static BufferedImage side(ServerLevel level, Map<String, String> q, double ppx, double ppz) {
        int len = Math.max(8, Math.min(384, geti(q, "len", 128)));
        int cx = geti(q, "x", (int) ppx), cz = geti(q, "z", (int) ppz);
        boolean alongX = !"z".equalsIgnoreCase(q.getOrDefault("dir", "x"));
        int y0 = geti(q, "y0", level.getMinY()), y1 = geti(q, "y1", Math.min(level.getMinY() + level.getHeight(), y0 + 200));
        if (y1 <= y0) y1 = y0 + 64;
        int scale = Math.max(1, geti(q, "scale", Math.max(1, Math.min(6, 900 / len))));
        int hgt = y1 - y0;
        BufferedImage img = new BufferedImage(len * scale, hgt * scale, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < len; i++) {
            int wx = alongX ? cx + i : cx, wz = alongX ? cz : cz + i;
            level.getChunk(wx >> 4, wz >> 4);
            for (int y = y0; y < y1; y++) {
                BlockPos pos = new BlockPos(wx, y, wz);
                BlockState s = level.getBlockState(pos);
                int rgb;
                if (s.isAir()) {
                    double t = (double) (y - y0) / hgt;
                    rgb = ((int) (135 + 60 * (1 - t)) << 16) | ((int) (180 + 40 * (1 - t)) << 8) | 235;
                } else if (!s.getFluidState().isEmpty()) rgb = 0x3F76E4;
                else rgb = shade(mapColor(level, s, pos), 1.0 - 0.1 * ((wx + y + wz) & 1));
                int py = (y1 - 1 - y) * scale;
                for (int dy = 0; dy < scale; dy++) for (int dx = 0; dx < scale; dx++) img.setRGB(i * scale + dx, py + dy, 0xFF000000 | rgb);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 255, 255, 90));
        for (int y = (y0 / 16 + 1) * 16; y < y1; y += 16) g.drawLine(0, (y1 - 1 - y) * scale, len * scale, (y1 - 1 - y) * scale);
        g.setColor(Color.WHITE);
        g.drawString((alongX ? "x " + cx + " .. " + (cx + len) + "  at z " + cz : "z " + cz + " .. " + (cz + len) + "  at x " + cx) + "   y " + y0 + " .. " + y1 + "  (lines every 16)", 6, 14);
        g.dispose();
        return img;
    }

    private static BufferedImage iso(ServerLevel level, Map<String, String> q, double ppx, double ppz) {
        int size = Math.max(32, Math.min(384, geti(q, "size", 192)));
        int step = Math.max(1, geti(q, "step", Math.max(1, size / 96)));
        int cx = geti(q, "x", (int) ppx), cz = geti(q, "z", (int) ppz);
        int n = size / step;
        int x0 = cx - size / 2, z0 = cz - size / 2;
        int cell = Math.max(3, Math.min(12, 700 / (2 * n)));
        int[][] h = new int[n][n];
        int[][] col = new int[n][n];
        int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
        for (int j = 0; j < n; j++) for (int i = 0; i < n; i++) {
            int wx = x0 + i * step, wz = z0 + j * step;
            level.getChunk(wx >> 4, wz >> 4);
            int y = surfaceY(level, wx, wz);
            BlockPos pos = new BlockPos(wx, y, wz);
            BlockState s = level.getBlockState(pos);
            h[j][i] = y;
            col[j][i] = !s.getFluidState().isEmpty() ? 0x3F76E4 : mapColor(level, s, pos);
            minH = Math.min(minH, y);
            maxH = Math.max(maxH, y);
        }
        double vs = Math.max(0.8, Math.min(3.0, 260.0 / Math.max(1, maxH - minH)));
        int width = 2 * n * cell + 24, height = (int) (n * cell + (maxH - minH) * vs) + 60;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(150, 195, 240));
        g.fillRect(0, 0, width, height);
        for (int d = 0; d < 2 * n - 1; d++) {          // back to front along the diagonals
            for (int j = 0; j < n; j++) {
                int i = d - j;
                if (i < 0 || i >= n) continue;
                int sx = (i - j) * cell + n * cell + 12;
                int sy = (int) ((i + j) * cell / 2.0 + (maxH - h[j][i]) * vs) + 20;
                int base = (int) ((i + j) * cell / 2.0 + (maxH - minH + 8) * vs) + 20;
                int c = col[j][i];
                g.setColor(new Color(shade(c, 0.62)));
                g.fillRect(sx - cell, sy, 2 * cell, Math.max(1, base - sy));
                g.setColor(new Color(shade(c, 1.0)));
                g.fillRect(sx - cell, sy - cell / 2, 2 * cell, cell);
            }
        }
        g.setColor(Color.WHITE);
        g.drawString("isometric view  x " + x0 + " .. " + (x0 + size) + "  z " + z0 + " .. " + (z0 + size) + "  height " + minH + " .. " + maxH + "  (north is away, east is right)", 6, 14);
        g.dispose();
        return img;
    }
}
