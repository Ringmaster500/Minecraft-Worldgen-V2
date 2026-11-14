package com.worldgenv2.studio.shell;

import com.worldgenv2.studio.project.Asset;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.studio.ui.*;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Read-only, syntax-coloured view of the asset's JSON, exactly as it will be written to disk. */
final class JsonView extends Widget {
    private static final int LINE = 11;

    private final StudioApp app;
    private final String assetPath;
    private List<String> lines = new ArrayList<>();
    private String fullText = "";
    private long builtRev = -1;
    private int scroll;
    private final Button copy;

    JsonView(StudioApp app, Asset a) {
        this.app = app;
        this.assetPath = a.path;
        copy = add(new Button("Copy", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(fullText);
            app.say("Copied JSON to clipboard");
        }));
    }

    private void refresh() {
        Asset a = app.project.get(assetPath);
        if (a == null) return;
        fullText = a.parseError != null && a.rawText != null ? a.rawText : StudioProject.pretty(a.json);
        lines = new ArrayList<>(List.of(fullText.split("\n", -1)));
        builtRev = app.project.revision;
    }

    @Override
    public void layout() {
        copy.setBounds(x + w - 56, y + 4, 50, 16);
    }

    @Override
    protected void render(Gfx g, int mx, int my) {
        if (builtRev != app.project.revision) refresh();
        g.fill(x, y, w, h, Ui.BG_INPUT);
        int gutter = 30;
        g.fill(x, y, gutter, h, Ui.BG_PANEL);
        g.vline(x + gutter, y, h, Ui.LINE);
        int maxScroll = Math.max(0, lines.size() * LINE - h + 8);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
        g.clip(x, y, w, h);
        int first = scroll / LINE;
        for (int i = first; i < lines.size(); i++) {
            int ly = y + 4 + i * LINE - scroll;
            if (ly > y + h) break;
            g.text(String.valueOf(i + 1), x + gutter - 4 - g.width(String.valueOf(i + 1)), ly, Ui.TEXT_FAINT);
            drawColored(g, lines.get(i), x + gutter + 6, ly, w - gutter - 70);
        }
        g.unclip();
        if (lines.size() * LINE > h) {
            int th = Math.max(14, h * h / (lines.size() * LINE));
            int ty = y + (int) ((h - th) * (scroll / (double) Math.max(1, maxScroll)));
            g.fill(x + w - 3, ty, 2, th, Ui.LINE_STRONG);
        }
    }

    /** Colours one line: keys cyan, strings green, numbers orange, literals purple, punctuation dim. */
    private void drawColored(Gfx g, String line, int x0, int y0, int maxW) {
        int x = x0;
        int i = 0, n = line.length();
        while (i < n && x - x0 < maxW) {
            char c = line.charAt(i);
            int j = i;
            int color;
            if (c == '"') {
                j = i + 1;
                while (j < n && line.charAt(j) != '"') { if (line.charAt(j) == '\\') j++; j++; }
                j = Math.min(n, j + 1);
                boolean isKey = j < n && line.charAt(j) == ':';
                color = isKey ? 0xFF7FD4F0 : 0xFF8FD694;
            } else if (Character.isDigit(c) || (c == '-' && i + 1 < n && Character.isDigit(line.charAt(i + 1)))) {
                j = i + 1;
                while (j < n && (Character.isDigit(line.charAt(j)) || ".eE+-".indexOf(line.charAt(j)) >= 0)) j++;
                color = 0xFFE8A96B;
            } else if (Character.isLetter(c)) {
                while (j < n && Character.isLetter(line.charAt(j))) j++;
                color = 0xFFC49BEB;
            } else if (c == ' ') {
                x += g.width(" ");
                i++;
                continue;
            } else {
                j = i + 1;
                color = Ui.TEXT_DIM;
            }
            String tok = line.substring(i, j);
            g.text(tok, x, y0Adj(y0), color);
            x += g.width(tok);
            i = j;
        }
    }

    private static int y0Adj(int y) { return y; }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy) * LINE * 4;
        return true;
    }
}
