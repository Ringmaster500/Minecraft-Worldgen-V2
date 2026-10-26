package com.worldgenv2.studio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Real-time 3D in-world chunk highlighter for WorldGen V2 Studio.
 * Uses Minecraft 26.1's native {@link Gizmos} hardware-accelerated 3D line and cuboid pipeline
 * to highlight selected live-testing ("dev") chunks directly in the 3D world.
 * Can be toggled on/off at any time from the Studio UI or via hotkey ('H'), and automatically
 * hides when the user clicks "Confirm Chunks".
 */
public final class StudioChunkHighlightRenderer implements DebugRenderer.SimpleDebugRenderer {

    private static final int STROKE_CYAN = 0xFF00FFAA;
    private static final int STROKE_BRIGHT_CYAN = 0xFF55FFFF;
    private static final int FILL_CYAN = 0x2200FFAA;
    private static final int STROKE_HOVER_GOLD = 0xFFFFCC00;
    private static final int FILL_HOVER_GOLD = 0x30FFCC00;

    @Override
    public void emitGizmos(
        double camX,
        double camY,
        double camZ,
        DebugValueAccess debugValues,
        Frustum frustum,
        float partialTicks
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        StudioState state = StudioState.get();
        boolean showSelected = state.isChunkHighlightActive();
        ChunkPos hovered = state.getHoveredChunk();

        if (!showSelected && hovered == null) {
            return;
        }

        int worldMinY = mc.level.getMinY();
        int worldMaxY = mc.level.getMaxY();
        double playerY = mc.player.getY();

        if (showSelected) {
            Set<ChunkPos> selected = state.getSelectedChunks();
            for (ChunkPos pos : selected) {
                int cx = pos.x();
                int cz = pos.z();
                double minX = (cx << 4);
                double minZ = (cz << 4);
                double maxX = minX + 16.0;
                double maxZ = minZ + 16.0;

                // Determine local surface height in this chunk so the 3D highlight wraps the actual terrain
                int surfY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (cx << 4) + 8, (cz << 4) + 8);
                if (surfY <= worldMinY) {
                    surfY = (int) playerY;
                }

                double boxMinY = Math.max(worldMinY, Math.min(playerY - 24.0, surfY - 16.0));
                double boxMaxY = Math.min(worldMaxY, Math.max(playerY + 32.0, surfY + 32.0));

                // 1. Translucent 3D volumetric highlight cuboid around the terrain/player band
                AABB chunkBox = new AABB(minX + 0.04, boxMinY, minZ + 0.04, maxX - 0.04, boxMaxY, maxZ - 0.04);
                Gizmos.cuboid(chunkBox, GizmoStyle.strokeAndFill(STROKE_CYAN, 2.0f, FILL_CYAN));

                // 2. Crisp surface-level 16x16 marker ring right at terrain surface Y + 0.1
                double ringY = surfY + 0.15;
                Gizmos.line(new Vec3(minX, ringY, minZ), new Vec3(maxX, ringY, minZ), STROKE_BRIGHT_CYAN, 3.0f);
                Gizmos.line(new Vec3(maxX, ringY, minZ), new Vec3(maxX, ringY, maxZ), STROKE_BRIGHT_CYAN, 3.0f);
                Gizmos.line(new Vec3(maxX, ringY, maxZ), new Vec3(minX, ringY, maxZ), STROKE_BRIGHT_CYAN, 3.0f);
                Gizmos.line(new Vec3(minX, ringY, maxZ), new Vec3(minX, ringY, minZ), STROKE_BRIGHT_CYAN, 3.0f);

                // 3. Full-height vertical corner beams on outer perimeter corners of the selection
                boolean northSel = state.isChunkSelected(cx, cz - 1);
                boolean southSel = state.isChunkSelected(cx, cz + 1);
                boolean westSel = state.isChunkSelected(cx - 1, cz);
                boolean eastSel = state.isChunkSelected(cx + 1, cz);

                if (!northSel || !westSel) {
                    Gizmos.line(new Vec3(minX, worldMinY, minZ), new Vec3(minX, worldMaxY, minZ), STROKE_CYAN, 2.5f);
                }
                if (!northSel || !eastSel) {
                    Gizmos.line(new Vec3(maxX, worldMinY, minZ), new Vec3(maxX, worldMaxY, minZ), STROKE_CYAN, 2.5f);
                }
                if (!southSel || !westSel) {
                    Gizmos.line(new Vec3(minX, worldMinY, maxZ), new Vec3(minX, worldMaxY, maxZ), STROKE_CYAN, 2.5f);
                }
                if (!southSel || !eastSel) {
                    Gizmos.line(new Vec3(maxX, worldMinY, maxZ), new Vec3(maxX, worldMaxY, maxZ), STROKE_CYAN, 2.5f);
                }

                // 4. Horizontal section lines every 16 blocks on exposed outer faces
                int startSecY = ((int) Math.floor(boxMinY / 16.0)) * 16;
                int endSecY = ((int) Math.ceil(boxMaxY / 16.0)) * 16;
                for (int sy = startSecY; sy <= endSecY; sy += 16) {
                    if (sy < worldMinY || sy > worldMaxY) continue;
                    if (!northSel) {
                        Gizmos.line(new Vec3(minX, sy, minZ), new Vec3(maxX, sy, minZ), 0x8800FFAA, 1.5f);
                    }
                    if (!southSel) {
                        Gizmos.line(new Vec3(minX, sy, maxZ), new Vec3(maxX, sy, maxZ), 0x8800FFAA, 1.5f);
                    }
                    if (!westSel) {
                        Gizmos.line(new Vec3(minX, sy, minZ), new Vec3(minX, sy, maxZ), 0x8800FFAA, 1.5f);
                    }
                    if (!eastSel) {
                        Gizmos.line(new Vec3(maxX, sy, minZ), new Vec3(maxX, sy, maxZ), 0x8800FFAA, 1.5f);
                    }
                }
            }
        }

        // 5. Render hovered preview chunk (bright gold) when inspecting/selecting chunks
        if (hovered != null) {
            int hcx = hovered.x();
            int hcz = hovered.z();
            double minX = (hcx << 4);
            double minZ = (hcz << 4);
            double maxX = minX + 16.0;
            double maxZ = minZ + 16.0;

            int surfY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, (hcx << 4) + 8, (hcz << 4) + 8);
            if (surfY <= worldMinY) {
                surfY = (int) playerY;
            }
            double boxMinY = Math.max(worldMinY, Math.min(playerY - 20.0, surfY - 12.0));
            double boxMaxY = Math.min(worldMaxY, Math.max(playerY + 28.0, surfY + 28.0));

            AABB hoverBox = new AABB(minX - 0.05, boxMinY, minZ - 0.05, maxX + 0.05, boxMaxY, maxZ + 0.05);
            Gizmos.cuboid(hoverBox, GizmoStyle.strokeAndFill(STROKE_HOVER_GOLD, 3.0f, FILL_HOVER_GOLD));
        }
    }
}
