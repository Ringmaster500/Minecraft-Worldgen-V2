package com.worldgenv2.studio.gui;

import com.worldgenv2.studio.WorldGenStudioClient;
import com.worldgenv2.studio.projects.StudioProjectManager;
import com.worldgenv2.studio.projects.StudioProjectManager.ProjectEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Minimalist Fullscreen Studio Hub for WorldGen V2.
 * <p>
 * Clean, distraction-free canvas with a top navigation bar.
 * Default selected workspace is the current active world.
 * Clicking the workspace selector drops down to switch projects or create a new one.
 */
public class WorldGenStudioScreen extends Screen {

    private static WorldGenStudioScreen instance;

    public static WorldGenStudioScreen getInstance() {
        if (instance == null) {
            instance = new WorldGenStudioScreen();
        }
        return instance;
    }

    public static void resetSession() {
        instance = null;
    }

    // Active project state
    private ProjectEntry activeProject = null;

    // Dropdown state
    private boolean projectDropdownOpen = false;

    // Creation modal state
    private boolean showNewProjectModal = false;
    private String newProjectName = "";

    // Sleek minimal dark theme
    private static final int CANVAS_BG       = 0xFF0D0E12; // Pitch blank canvas
    private static final int TOPBAR_BG       = 0xFF12141A; // Clean dark bar
    private static final int BORDER_COLOR    = 0xFF1F222C; // Subtle hairline
    private static final int BUTTON_BG       = 0xFF171A23; // Sleek button
    private static final int BUTTON_HOVER    = 0xFF212532; // Hovered button
    private static final int BUTTON_BORDER   = 0xFF292E3E; // Button outline
    private static final int POPUP_BG        = 0xFF151720; // Dropdown panel
    private static final int POPUP_BORDER    = 0xFF2D3244; // Dropdown border
    private static final int TEXT_PRIMARY    = 0xFFE4E7ED; // Crisp text
    private static final int TEXT_MUTED      = 0xFF838896; // Muted label
    private static final int ACCENT_COLOR    = 0xFFFFAA00; // Subtle warm accent
    private static final int ACCENT_BLUE     = 0xFF4FA3E3; // New project accent

    public WorldGenStudioScreen() {
        super(Component.literal("WorldGen Studio"));
    }

    @Override
    protected void init() {
        super.init();
        if (activeProject == null) {
            activeProject = StudioProjectManager.createCurrentWorldProject();
            StudioProjectManager.setActiveProject(activeProject);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // 1. Clean Blank Canvas
        graphics.fill(0, 0, width, height, CANVAS_BG);

        // 2. Top Navigation Bar
        int barH = 32;
        graphics.fill(0, 0, width, barH, TOPBAR_BG);
        graphics.horizontalLine(0, width, barH - 1, BORDER_COLOR);

        // 3. Workspace Dropdown Selector Button (Left)
        String worldName = (activeProject != null) ? activeProject.name() : "Current World";
        String displayLabel = (activeProject != null && !activeProject.isCurrentWorld())
                ? "📁 " + worldName + "  ▾"
                : "🌍 " + worldName + "  ▾";

        int btnX = 10;
        int btnY = 5;
        int btnH = 22;
        int btnW = font.width(displayLabel) + 16;

        boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, (projectDropdownOpen || btnHov) ? BUTTON_HOVER : BUTTON_BG);
        drawHollowRect(graphics, btnX, btnY, btnW, btnH, (projectDropdownOpen || btnHov) ? 0xFF3D445C : BUTTON_BORDER);
        graphics.text(font, displayLabel, btnX + 8, btnY + 7, TEXT_PRIMARY, false);

        // 4. Close Button (Right)
        int closeW = 22;
        int closeX = width - 10 - closeW;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(closeX, btnY, closeX + closeW, btnY + btnH, closeHov ? 0xFF331A1A : BUTTON_BG);
        drawHollowRect(graphics, closeX, btnY, closeW, btnH, closeHov ? 0xFFFF5555 : BUTTON_BORDER);
        graphics.centeredText(font, "✕", closeX + closeW / 2, btnY + 6, closeHov ? 0xFFFF6666 : TEXT_MUTED);

        // 5. Project Dropdown Menu Overlay
        if (projectDropdownOpen) {
            renderProjectDropdown(graphics, mouseX, mouseY, btnX, btnY + btnH + 3, btnW);
        }

        // 6. New Project Modal Dialog
        if (showNewProjectModal) {
            renderNewProjectModal(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    private void renderProjectDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int startX, int startY, int minWidth) {
        List<ProjectEntry> projects = StudioProjectManager.listProjects();
        int itemH = 22;
        int totalItems = 1 + projects.size() + 1; // Current World + external projects + New Project
        int menuW = Math.max(minWidth + 30, 220);
        int menuH = (totalItems * itemH) + 6; // includes divider line

        // Dropdown shadow & background
        graphics.fill(startX + 2, startY + 2, startX + menuW + 2, startY + menuH + 2, 0x55000000);
        graphics.fill(startX, startY, startX + menuW, startY + menuH, POPUP_BG);
        drawHollowRect(graphics, startX, startY, menuW, menuH, POPUP_BORDER);

        int currentY = startY + 2;

        // Item 1: Current World
        String currentWorldName = (Minecraft.getInstance().getSingleplayerServer() != null)
                ? Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName()
                : "Current World";
        boolean isCurrentWorldActive = (activeProject != null && activeProject.isCurrentWorld());
        boolean hovWorld = mouseX >= startX && mouseX <= startX + menuW && mouseY >= currentY && mouseY < currentY + itemH;

        if (hovWorld) {
            graphics.fill(startX + 1, currentY, startX + menuW - 1, currentY + itemH, 0xFF202433);
        }
        graphics.text(font, "🌍 " + currentWorldName, startX + 8, currentY + 6, isCurrentWorldActive ? ACCENT_COLOR : TEXT_PRIMARY, false);
        if (isCurrentWorldActive) {
            graphics.text(font, "✓", startX + menuW - 16, currentY + 6, ACCENT_COLOR, false);
        }
        currentY += itemH;

        // Item 2..N: Saved Projects
        for (ProjectEntry p : projects) {
            boolean isThisActive = (activeProject != null && !activeProject.isCurrentWorld() && p.name().equals(activeProject.name()));
            boolean hovProj = mouseX >= startX && mouseX <= startX + menuW && mouseY >= currentY && mouseY < currentY + itemH;

            if (hovProj) {
                graphics.fill(startX + 1, currentY, startX + menuW - 1, currentY + itemH, 0xFF202433);
            }
            graphics.text(font, "📁 " + p.name(), startX + 8, currentY + 6, isThisActive ? ACCENT_COLOR : TEXT_PRIMARY, false);
            if (isThisActive) {
                graphics.text(font, "✓", startX + menuW - 16, currentY + 6, ACCENT_COLOR, false);
            }
            currentY += itemH;
        }

        // Divider
        graphics.horizontalLine(startX + 6, startX + menuW - 6, currentY + 1, 0xFF252937);
        currentY += 3;

        // Bottom Item: + New Project
        boolean hovNew = mouseX >= startX && mouseX <= startX + menuW && mouseY >= currentY && mouseY < currentY + itemH;
        if (hovNew) {
            graphics.fill(startX + 1, currentY, startX + menuW - 1, currentY + itemH, 0xFF202433);
        }
        graphics.text(font, "+ New Project...", startX + 8, currentY + 6, hovNew ? 0xFF70BFFF : ACCENT_BLUE, false);
    }

    private void renderNewProjectModal(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x88000000);

        int modalW = 280;
        int modalH = 96;
        int modalX = width / 2 - modalW / 2;
        int modalY = height / 2 - modalH / 2;

        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF14161F);
        drawHollowRect(graphics, modalX, modalY, modalW, modalH, POPUP_BORDER);

        graphics.text(font, "New Project Workspace", modalX + 14, modalY + 12, TEXT_PRIMARY, true);

        // Input Field
        int inputX = modalX + 14;
        int inputY = modalY + 30;
        int inputW = modalW - 28;
        int inputH = 22;

        graphics.fill(inputX, inputY, inputX + inputW, inputY + inputH, 0xFF0D0E13);
        drawHollowRect(graphics, inputX, inputY, inputW, inputH, 0xFF2B2F3E);

        String display = newProjectName.isEmpty() ? "Enter project name..." : newProjectName;
        int txtColor = newProjectName.isEmpty() ? 0xFF525768 : TEXT_PRIMARY;
        graphics.text(font, display + (newProjectName.isEmpty() ? "" : "_"), inputX + 6, inputY + 6, txtColor, false);

        // Action Buttons: [ Cancel ] [ Create ]
        int btnW = 68;
        int btnH = 20;
        int btnY = modalY + 62;

        int createX = modalX + modalW - 14 - btnW;
        boolean createHov = mouseX >= createX && mouseX <= createX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(createX, btnY, createX + btnW, btnY + btnH, createHov ? 0xFF00B4D8 : 0xFF0077B6);
        graphics.centeredText(font, "Create", createX + btnW / 2, btnY + 5, TEXT_PRIMARY);

        int cancelX = createX - btnW - 6;
        boolean cancelHov = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, cancelHov ? 0xFF262A37 : 0xFF1A1C24);
        drawHollowRect(graphics, cancelX, btnY, btnW, btnH, BUTTON_BORDER);
        graphics.centeredText(font, "Cancel", cancelX + btnW / 2, btnY + 5, TEXT_MUTED);
    }

    private void drawHollowRect(GuiGraphicsExtractor graphics, int rx, int ry, int rw, int rh, int color) {
        graphics.horizontalLine(rx, rx + rw - 1, ry, color);
        graphics.horizontalLine(rx, rx + rw - 1, ry + rh - 1, color);
        graphics.verticalLine(rx, ry, ry + rh - 1, color);
        graphics.verticalLine(rx + rw - 1, ry, ry + rh - 1, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 1. Modal clicks intercept everything
        if (showNewProjectModal) {
            int modalW = 280;
            int modalH = 96;
            int modalX = width / 2 - modalW / 2;
            int modalY = height / 2 - modalH / 2;

            int btnW = 68;
            int btnH = 20;
            int btnY = modalY + 62;
            int createX = modalX + modalW - 14 - btnW;
            int cancelX = createX - btnW - 6;

            // Create button
            if (event.x() >= createX && event.x() <= createX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
                if (!newProjectName.isBlank()) {
                    activeProject = StudioProjectManager.createNewProject(newProjectName);
                    StudioProjectManager.setActiveProject(activeProject);
                    showNewProjectModal = false;
                }
                return true;
            }

            // Cancel button
            if (event.x() >= cancelX && event.x() <= cancelX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
                showNewProjectModal = false;
                return true;
            }

            // Click outside closes modal
            if (event.x() < modalX || event.x() > modalX + modalW || event.y() < modalY || event.y() > modalY + modalH) {
                showNewProjectModal = false;
                return true;
            }
            return true;
        }

        // 2. Dropdown menu clicks
        String worldName = (activeProject != null) ? activeProject.name() : "Current World";
        String displayLabel = (activeProject != null && !activeProject.isCurrentWorld())
                ? "📁 " + worldName + "  ▾"
                : "🌍 " + worldName + "  ▾";
        int btnX = 10;
        int btnY = 5;
        int btnH = 22;
        int btnW = font.width(displayLabel) + 16;

        if (projectDropdownOpen) {
            int startX = btnX;
            int startY = btnY + btnH + 3;
            List<ProjectEntry> projects = StudioProjectManager.listProjects();
            int itemH = 22;
            int totalItems = 1 + projects.size() + 1;
            int menuW = Math.max(btnW + 30, 220);
            int menuH = (totalItems * itemH) + 6;

            if (event.x() >= startX && event.x() <= startX + menuW && event.y() >= startY && event.y() <= startY + menuH) {
                int clickedY = (int) event.y() - startY - 2;
                int clickedIdx = clickedY / itemH;

                if (clickedIdx == 0) {
                    // Clicked Current World
                    activeProject = StudioProjectManager.createCurrentWorldProject();
                    StudioProjectManager.setActiveProject(activeProject);
                    projectDropdownOpen = false;
                    return true;
                } else if (clickedIdx >= 1 && clickedIdx <= projects.size()) {
                    // Clicked an external project
                    activeProject = projects.get(clickedIdx - 1);
                    StudioProjectManager.setActiveProject(activeProject);
                    projectDropdownOpen = false;
                    return true;
                } else {
                    // Clicked + New Project
                    projectDropdownOpen = false;
                    newProjectName = "Pack_" + (projects.size() + 1);
                    showNewProjectModal = true;
                    return true;
                }
            } else {
                // Clicked outside dropdown
                projectDropdownOpen = false;
                // If clicked toggle button again, keep it closed (handled)
                if (event.x() >= btnX && event.x() <= btnX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
                    return true;
                }
            }
        }

        // 3. Dropdown toggle button click
        if (event.x() >= btnX && event.x() <= btnX + btnW && event.y() >= btnY && event.y() <= btnY + btnH) {
            projectDropdownOpen = !projectDropdownOpen;
            return true;
        }

        // 4. Close button click
        int closeW = 22;
        int closeX = width - 10 - closeW;
        if (event.x() >= closeX && event.x() <= closeX + closeW && event.y() >= btnY && event.y() <= btnY + btnH) {
            this.onClose();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (showNewProjectModal) {
            if (event.key() == 259 && !newProjectName.isEmpty()) { // Backspace
                newProjectName = newProjectName.substring(0, newProjectName.length() - 1);
                return true;
            }
            if (event.key() == 257 && !newProjectName.isBlank()) { // Enter
                activeProject = StudioProjectManager.createNewProject(newProjectName);
                StudioProjectManager.setActiveProject(activeProject);
                showNewProjectModal = false;
                return true;
            }
            if (event.key() == 256) { // Escape
                showNewProjectModal = false;
                return true;
            }
        }

        if (projectDropdownOpen) {
            if (event.key() == 256) { // Escape
                projectDropdownOpen = false;
                return true;
            }
        }

        if (event.key() == 256 || WorldGenStudioClient.isStudioKey(event)) { // Escape or G key
            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (showNewProjectModal) {
            char ch = (char) event.codepoint();
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                newProjectName += ch;
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
