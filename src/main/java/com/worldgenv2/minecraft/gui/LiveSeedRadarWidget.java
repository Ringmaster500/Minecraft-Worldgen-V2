package com.worldgenv2.minecraft.gui;

import com.worldgenv2.density.FastNoiseLite;
import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Real-time 2D continental & biome radar widget for the World Creation Customize screen.
 * Evaluates macro terrain elevation and climate noise in milliseconds before world creation,
 * giving a clean procedural preview canvas.
 */
public class LiveSeedRadarWidget {

    private int x;
    private int y;
    private int width;
    private int height;

    private long seed = 1337L;
    private float zoom = 1.0f; // 1.0 = standard overview
    private float panX = 0.0f;
    private float panZ = 0.0f;

    private final FastNoiseLite continentNoise;
    private final FastNoiseLite temperatureNoise;

    // Fast color palette
    private static final int COLOR_DEEP_OCEAN  = 0xFF142444;
    private static final int COLOR_SHALLOW_SEA = 0xFF284878;
    private static final int COLOR_COAST_SAND  = 0xFFD4C88A;
    private static final int COLOR_LOWLAND     = 0xFF4B7336;
    private static final int COLOR_FOREST      = 0xFF2A5922;
    private static final int COLOR_HIGHLAND    = 0xFF635848;
    private static final int COLOR_PEAKS       = 0xFFDDDDEE;

    public LiveSeedRadarWidget() {
        this.continentNoise = new FastNoiseLite();
        this.continentNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.continentNoise.setFrequency(0.0035f);
        this.continentNoise.setFractalType(FastNoiseLite.FractalType.FBm);
        this.continentNoise.setFractalOctaves(3);

        this.temperatureNoise = new FastNoiseLite();
        this.temperatureNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.temperatureNoise.setFrequency(0.002f);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void updateSeed(long newSeed, float biomeScale) {
        this.seed = newSeed;
        this.continentNoise.setSeed((int) newSeed);
        this.continentNoise.setFrequency(0.0035f / Math.max(0.2f, biomeScale));
        this.temperatureNoise.setSeed((int) (newSeed ^ 0x9E3779B9L));
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        // Draw beveled frame & dark canvas
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0xEE12141C);

        int mapX = x + 3;
        int mapY = y + 3;
        int mapW = width - 6;
        int mapH = height - 6;

        // Render pixel grid sampling (step by 3 pixels for high performance)
        int step = 3;
        int halfW = mapW / 2;
        int halfH = mapH / 2;
        float worldSpan = 4096.0f / zoom;
        float blockPerPixel = worldSpan / Math.max(1, mapW);

        for (int py = 0; py < mapH; py += step) {
            float wz = (py - halfH) * blockPerPixel + panZ;
            for (int px = 0; px < mapW; px += step) {
                float wx = (px - halfW) * blockPerPixel + panX;

                float elev = continentNoise.getNoise(wx, wz);
                int color;
                if (elev < -0.15f) {
                    color = COLOR_DEEP_OCEAN;
                } else if (elev < 0.0f) {
                    color = COLOR_SHALLOW_SEA;
                } else if (elev < 0.08f) {
                    color = COLOR_COAST_SAND;
                } else if (elev < 0.35f) {
                    color = COLOR_LOWLAND;
                } else if (elev < 0.60f) {
                    color = COLOR_FOREST;
                } else if (elev < 0.78f) {
                    color = COLOR_HIGHLAND;
                } else {
                    color = COLOR_PEAKS;
                }

                graphics.fill(mapX + px, mapY + py, mapX + Math.min(px + step, mapW), mapY + Math.min(py + step, mapH), color);
            }
        }

        // Crosshairs at world origin (0, 0)
        int originSx = mapX + halfW - (int) Math.round(panX / blockPerPixel);
        int originSy = mapY + halfH - (int) Math.round(panZ / blockPerPixel);
        if (originSx >= mapX && originSx < mapX + mapW && originSy >= mapY && originSy < mapY + mapH) {
            graphics.fill(originSx - 4, originSy, originSx + 5, originSy + 1, 0x88FFAA00);
            graphics.fill(originSx, originSy - 4, originSx + 1, originSy + 5, 0x88FFAA00);
        }

        // Mini HUD overlay: Coordinates and Scale
        String hudText = "Seed: " + seed + " | Zoom: " + String.format("%.1fx", zoom);
        graphics.fill(x + 4, y + 4, x + 4 + font.width(hudText) + 8, y + 18, 0xCC111116);
        graphics.text(font, hudText, x + 8, y + 7, StudioTheme.TEXT_WHITE, true);

        // Hover coordinate tooltip
        if (mouseX >= mapX && mouseX < mapX + mapW && mouseY >= mapY && mouseY < mapY + mapH) {
            float hx = (mouseX - mapX - halfW) * blockPerPixel + panX;
            float hz = (mouseY - mapY - halfH) * blockPerPixel + panZ;
            String hoverText = "X: " + Math.round(hx) + ", Z: " + Math.round(hz);
            int tw = font.width(hoverText);
            graphics.fill(mouseX + 8, mouseY + 4, mouseX + 12 + tw, mouseY + 17, 0xDD000000);
            graphics.text(font, hoverText, mouseX + 10, mouseY + 6, StudioTheme.TEXT_CYAN, true);
        }
    }

    public void handleMouseScroll(double delta) {
        if (delta > 0) {
            zoom = Math.min(8.0f, zoom * 1.25f);
        } else if (delta < 0) {
            zoom = Math.max(0.25f, zoom / 1.25f);
        }
    }

    public void handlePan(float dx, float dy) {
        float worldSpan = 4096.0f / zoom;
        float blockPerPixel = worldSpan / Math.max(1, width);
        panX -= dx * blockPerPixel;
        panZ -= dy * blockPerPixel;
    }
}
