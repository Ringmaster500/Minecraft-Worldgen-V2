package com.worldgenv2.minecraft.gui;

import com.google.gson.JsonObject;
import com.worldgenv2.pack.ProjectRuntime;
import com.worldgenv2.pack.WorldGenV2Addons;
import com.worldgenv2.studio.StudioTheme;
import com.worldgenv2.studio.gui.WorldGenStudioScreen;
import com.worldgenv2.studio.project.ProjectStore;
import com.worldgenv2.studio.shell.StudioApp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Clean, spacious Worldgen V2 World Setup & Seed Preview Screen.
 * Opened when clicking [ Customize ] on Worldgen V2 in Minecraft's Create World screen.
 * Lets the player/pack-maker choose the active project or addon preset, preview the seed, or jump into Studio.
 */
public class WorldGenCustomizeScreen extends Screen {

    private record Entry(boolean isProject, String id, String label) {}

    private final Screen parentScreen;
    private final LiveSeedRadarWidget radarWidget;
    private final List<Entry> entries = new ArrayList<>();

    private int selectedIdx = 0;
    private long currentSeed = 133742L;
    private float biomeScale = 1.0f;

    public WorldGenCustomizeScreen(Screen parentScreen) {
        super(Component.literal("Worldgen V2: World Options"));
        this.parentScreen = parentScreen;
        this.radarWidget = new LiveSeedRadarWidget();
        this.currentSeed = new Random().nextLong() & 0x7FFFFFFFFFFFL;
        loadEntries();
    }

    private void loadEntries() {
        entries.clear();
        String activeProj = ProjectRuntime.activeProjectName();
        String activePreset = ProjectRuntime.activePresetId();
        for (ProjectStore.Entry p : ProjectStore.list()) {
            entries.add(new Entry(true, p.name(), "Project: " + p.name()));
            if (p.name().equals(activeProj)) selectedIdx = entries.size() - 1;
        }
        for (WorldGenV2Addons.Preset pr : WorldGenV2Addons.presets()) {
            if (pr.isPack()) continue;
            entries.add(new Entry(false, pr.id(), "Preset: " + pr.name()));
            if (activeProj == null && pr.id().equals(activePreset)) selectedIdx = entries.size() - 1;
        }
        if (entries.isEmpty()) {
            entries.add(new Entry(false, "worldgenv2:default", "Preset: Default"));
        }
    }

    private void applySelection() {
        if (entries.isEmpty()) return;
        Entry sel = entries.get(Math.max(0, Math.min(selectedIdx, entries.size() - 1)));
        try {
            if (sel.isProject()) {
                ProjectRuntime.setActiveProject(sel.id());
                // the new world gets the height the world setup asks for (Min Y and depth)
                int[] h = com.worldgenv2.minecraft.WorldHeightDimensions.heightOf(sel.id());
                if (h != null && parentScreen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen create) {
                    create.getUiState().updateDimensions((access, dims) ->
                        com.worldgenv2.minecraft.WorldHeightDimensions.apply(access, dims, h[0], h[1]));
                }
            } else {
                Files.createDirectories(ProjectRuntime.activeFile().getParent());
                JsonObject o = new JsonObject();
                o.addProperty("preset", sel.id());
                Files.writeString(ProjectRuntime.activeFile(), o.toString());
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        super.init();

        int centerX = width / 2;

        // Clean centered preview radar
        int previewW = Math.min(340, width - 40);
        int previewH = Math.min(145, height - 130);
        int previewX = centerX - previewW / 2;
        int previewY = 30;

        this.radarWidget.setBounds(previewX, previewY, previewW, previewH);
        this.radarWidget.updateSeed(currentSeed, biomeScale);

        int row1Y = previewY + previewH + 8;
        Entry cur = entries.get(Math.max(0, Math.min(selectedIdx, entries.size() - 1)));
        this.addRenderableWidget(Button.builder(Component.literal(cur.label()), btn -> {
            selectedIdx = (selectedIdx + 1) % entries.size();
            btn.setMessage(Component.literal(entries.get(selectedIdx).label()));
        }).bounds(centerX - 155, row1Y, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Open Studio"), btn -> {
            Entry sel = entries.get(Math.max(0, Math.min(selectedIdx, entries.size() - 1)));
            if (sel.isProject()) StudioApp.get().openProject(sel.id());
            Minecraft.getInstance().setScreen(WorldGenStudioScreen.getInstance());
        }).bounds(centerX + 55, row1Y, 100, 20).build());

        int row2Y = row1Y + 24;
        this.addRenderableWidget(Button.builder(Component.literal("Randomize Seed"), btn -> {
            this.currentSeed = new Random().nextLong() & 0x7FFFFFFFFFFFL;
            this.radarWidget.updateSeed(currentSeed, biomeScale);
        }).bounds(centerX - 105, row2Y, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Scale: " + String.format("%.1fx", biomeScale)), btn -> {
            if (biomeScale >= 2.0f) biomeScale = 0.5f;
            else biomeScale += 0.5f;
            btn.setMessage(Component.literal("Scale: " + String.format("%.1fx", biomeScale)));
            this.radarWidget.updateSeed(currentSeed, biomeScale);
        }).bounds(centerX + 5, row2Y, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            applySelection();
            Minecraft.getInstance().setScreen(parentScreen);
        }).bounds(centerX - 105, height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            Minecraft.getInstance().setScreen(parentScreen);
        }).bounds(centerX + 5, height - 28, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractMenuBackground(graphics);

        // Header Title (Clean & Centered)
        graphics.centeredText(font, "Worldgen V2 Configuration", width / 2, 12, StudioTheme.TEXT_GOLD);

        // Render Centered Live Seed Radar Widget
        this.radarWidget.render(graphics, font, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick);
    }
}
