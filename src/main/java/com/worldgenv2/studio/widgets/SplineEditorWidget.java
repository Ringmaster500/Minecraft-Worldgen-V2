package com.worldgenv2.studio.widgets;

import com.worldgenv2.studio.StudioTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interactive 2D Cubic Spline & Curve Editor Widget.
 * <p>
 * Provides draggable control points in [0.0, 1.0] with Monotone Cubic Hermite interpolation.
 * Allows modpack creators and world designers to visually sculpt continentalness,
 * erosion, height curves, and density remapping.
 */
public class SplineEditorWidget {

    public static class ControlPoint {
        public double x;
        public double y;

        public ControlPoint(double x, double y) {
            this.x = Math.clamp(x, 0.0, 1.0);
            this.y = Math.clamp(y, 0.0, 1.0);
        }
    }

    private int x;
    private int y;
    private int width;
    private int height;

    private final List<ControlPoint> points = new ArrayList<>();
    private int selectedPointIdx = -1;
    private boolean isDragging = false;
    private String label = "Spline Curve";

    public SplineEditorWidget() {
        // Default S-Curve shape
        points.add(new ControlPoint(0.0, 0.05));
        points.add(new ControlPoint(0.3, 0.20));
        points.add(new ControlPoint(0.7, 0.80));
        points.add(new ControlPoint(1.0, 0.95));
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<ControlPoint> getPoints() {
        return points;
    }

    public void addPoint(double px, double py) {
        points.add(new ControlPoint(px, py));
        points.sort(Comparator.comparingDouble(p -> p.x));
    }

    /**
     * Evaluates the spline transfer function at scalar input t in [0.0, 1.0].
     */
    public double evaluate(double t) {
        if (points.isEmpty()) return 0.0;
        if (points.size() == 1 || t <= points.get(0).x) return points.get(0).y;
        if (t >= points.get(points.size() - 1).x) return points.get(points.size() - 1).y;

        // Find enclosing segment
        int idx = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            if (t >= points.get(i).x && t <= points.get(i + 1).x) {
                idx = i;
                break;
            }
        }

        ControlPoint p0 = points.get(idx);
        ControlPoint p1 = points.get(idx + 1);
        double span = p1.x - p0.x;
        if (span < 1e-6) return p0.y;

        double localT = (t - p0.x) / span;
        // Smooth Hermite blend
        double h00 = (2 * localT * localT * localT) - (3 * localT * localT) + 1;
        double h01 = (-2 * localT * localT * localT) + (3 * localT * localT);
        return h00 * p0.y + h01 * p1.y;
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        // Draw editor panel background
        graphics.fill(x, y, x + width, y + height, 0xEE16161E);
        StudioTheme.drawBeveledPanel(graphics, x, y, width, height, 0x00000000);

        // Header label
        graphics.text(font, label, x + 6, y + 4, StudioTheme.TEXT_CYAN, true);

        int graphX = x + 8;
        int graphY = y + 18;
        int graphW = width - 16;
        int graphH = height - 26;

        // Graph background grid
        graphics.fill(graphX, graphY, graphX + graphW, graphY + graphH, 0xFF101014);
        graphics.horizontalLine(graphX, graphX + graphW, graphY + graphH / 2, 0xFF242430);
        graphics.verticalLine(graphX + graphW / 2, graphY, graphY + graphH, 0xFF242430);

        // Draw curve lines by sampling 48 segments
        int steps = 48;
        int prevScreenX = graphX;
        int prevScreenY = graphY + graphH - (int) Math.round(evaluate(0.0) * graphH);

        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            double v = evaluate(t);

            int screenX = graphX + (int) Math.round(t * graphW);
            int screenY = graphY + graphH - (int) Math.round(v * graphH);

            graphics.horizontalLine(Math.min(prevScreenX, screenX), Math.max(prevScreenX, screenX), screenY, 0xFF00E5FF);
            if (Math.abs(screenY - prevScreenY) > 1) {
                graphics.verticalLine(prevScreenX, Math.min(prevScreenY, screenY), Math.max(prevScreenY, screenY), 0xFF00E5FF);
            }

            prevScreenX = screenX;
            prevScreenY = screenY;
        }

        // Draw Control Points
        for (int i = 0; i < points.size(); i++) {
            ControlPoint cp = points.get(i);
            int ptX = graphX + (int) Math.round(cp.x * graphW);
            int ptY = graphY + graphH - (int) Math.round(cp.y * graphH);

            boolean isSelected = (i == selectedPointIdx);
            int ptColor = isSelected ? 0xFFFFAA00 : 0xFFFFFFFF;
            int radius = isSelected ? 4 : 3;

            graphics.fill(ptX - radius, ptY - radius, ptX + radius + 1, ptY + radius + 1, ptColor);
            graphics.fill(ptX - radius + 1, ptY - radius + 1, ptX + radius, ptY + radius, isSelected ? 0xFFFF5500 : 0xFF222233);
        }

        // Tooltip when hovering over a point
        if (selectedPointIdx >= 0 && selectedPointIdx < points.size()) {
            ControlPoint cp = points.get(selectedPointIdx);
            String info = String.format("(%.2f, %.2f)", cp.x, cp.y);
            graphics.text(font, info, x + width - font.width(info) - 8, y + 4, StudioTheme.TEXT_GOLD, true);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        int graphX = x + 8;
        int graphY = y + 18;
        int graphW = width - 16;
        int graphH = height - 26;

        if (event.x() < graphX || event.x() > graphX + graphW || event.y() < graphY || event.y() > graphY + graphH) {
            return false;
        }

        // Check if clicked near an existing control point
        for (int i = 0; i < points.size(); i++) {
            ControlPoint cp = points.get(i);
            int ptX = graphX + (int) Math.round(cp.x * graphW);
            int ptY = graphY + graphH - (int) Math.round(cp.y * graphH);

            if (Math.abs(event.x() - ptX) <= 6 && Math.abs(event.y() - ptY) <= 6) {
                selectedPointIdx = i;
                isDragging = true;
                return true;
            }
        }

        // Otherwise, add a new point at click location
        double nx = Math.clamp((event.x() - graphX) / (double) graphW, 0.0, 1.0);
        double ny = Math.clamp((graphY + graphH - event.y()) / (double) graphH, 0.0, 1.0);
        addPoint(nx, ny);
        selectedPointIdx = points.indexOf(points.stream().filter(p -> Math.abs(p.x - nx) < 1e-4).findFirst().orElse(null));
        isDragging = true;
        return true;
    }

    public void mouseDragged(double mouseX, double mouseY) {
        if (!isDragging || selectedPointIdx < 0 || selectedPointIdx >= points.size()) return;

        int graphX = x + 8;
        int graphY = y + 18;
        int graphW = width - 16;
        int graphH = height - 26;

        double nx = Math.clamp((mouseX - graphX) / (double) graphW, 0.0, 1.0);
        double ny = Math.clamp((graphY + graphH - mouseY) / (double) graphH, 0.0, 1.0);

        // Keep first and last points pinned to edges if needed
        if (selectedPointIdx == 0) nx = 0.0;
        if (selectedPointIdx == points.size() - 1) nx = 1.0;

        ControlPoint pt = points.get(selectedPointIdx);
        pt.x = nx;
        pt.y = ny;
        points.sort(Comparator.comparingDouble(p -> p.x));
    }

    public void mouseReleased() {
        isDragging = false;
    }
}
