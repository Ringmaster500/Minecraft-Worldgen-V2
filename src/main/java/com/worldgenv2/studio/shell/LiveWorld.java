package com.worldgenv2.studio.shell;

import com.worldgenv2.minecraft.WorldGenV2BiomeSource;
import com.worldgenv2.minecraft.WorldGenV2ChunkGenerator;
import com.worldgenv2.pack.ProjectRuntime;
import com.worldgenv2.studio.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bridge between the Studio and the running game: apply the project to the open world, regenerate nearby
 * chunks, and move the player to a spot picked on the map. Everything world-touching runs on the server thread.
 */
final class LiveWorld {
    private LiveWorld() {}

    static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    static boolean inWorld() {
        return server() != null && Minecraft.getInstance().player != null;
    }

    /** Saves, marks the project active, and re-compiles it into every project-driven generator in the open world. */
    static void apply(StudioApp app) {
        if (app.project == null) return;
        app.project.saveAll();
        try {
            ProjectRuntime.setActiveProject(app.project.name);
        } catch (IOException e) {
            app.say("Could not write active.json: " + e.getMessage(), Ui.RED);
            return;
        }
        MinecraftServer server = server();
        if (server == null) {
            app.say("Saved and set '" + app.project.name + "' as the active setup. Create a world with the 'WorldGen V2' world type to use it.", Ui.GREEN);
            return;
        }
        AtomicInteger applied = new AtomicInteger();
        server.execute(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                var gen = level.getChunkSource().getGenerator();
                if (gen instanceof WorldGenV2ChunkGenerator v2 && gen.getBiomeSource() instanceof WorldGenV2BiomeSource bs
                        && bs.getLayoutPreset().startsWith(ProjectRuntime.REF_PREFIX)) {
                    v2.reload();
                    applied.incrementAndGet();
                }
            }
            Minecraft.getInstance().execute(() -> {
                if (applied.get() > 0) {
                    app.say("Applied to the open world. New chunks use the updated project; use Regen to rebuild nearby ones.", Ui.GREEN);
                } else {
                    app.say("Saved and set active. This world was not created with the 'WorldGen V2' world type, so it is unchanged.", Ui.YELLOW);
                }
            });
        });
    }

    /**
     * Live preview: saves the setup, reloads the generators and rebuilds just the chosen square of chunks (centre chunk and radius),
     * so an edit shows up in front of you without touching the rest of the world. {@code done} is told how long it took.
     */
    static void liveRefresh(StudioApp app, int cx, int cz, int radius, Runnable done) {
        if (app.project == null) { done.run(); return; }
        MinecraftServer server = server();
        if (server == null) { done.run(); return; }
        long t0 = System.currentTimeMillis();
        app.project.saveAll();
        try { ProjectRuntime.setActiveProject(app.project.name); } catch (IOException e) { app.say("Live: could not write active.json: " + e.getMessage(), Ui.RED); done.run(); return; }
        server.execute(() -> {
            int chunks = 0;
            try {
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getChunkSource().getGenerator() instanceof WorldGenV2ChunkGenerator v2 && level.getChunkSource().getGenerator().getBiomeSource() instanceof WorldGenV2BiomeSource bs
                            && bs.getLayoutPreset().startsWith(ProjectRuntime.REF_PREFIX)) {
                        v2.reload();
                        if (level == server.overworld()) {
                            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) { v2.regenerateLiveChunk(level, new ChunkPos(cx + dx, cz + dz)); chunks++; }
                        }
                    }
                }
            } catch (RuntimeException e) {
                Minecraft.getInstance().execute(() -> app.say("Live preview failed: " + e.getMessage(), Ui.RED));
            }
            final int n = chunks;
            Minecraft.getInstance().execute(() -> {
                if (n > 0) app.say("Live: rebuilt " + n + " chunks in " + (System.currentTimeMillis() - t0) + " ms", Ui.GREEN);
                done.run();
            });
        });
    }

    /** The chunk the player stands in, or null outside a world. */
    static int[] playerChunk() {
        var p = Minecraft.getInstance().player;
        if (p == null) return null;
        return new int[]{p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4};
    }

    /** Regenerates loaded chunks within {@code radius} chunks of the player so edits appear without flying away. */
    static void regenAround(StudioApp app, int radius) {
        MinecraftServer server = server();
        if (server == null || Minecraft.getInstance().player == null) {
            app.say("Open a world to regenerate chunks.", Ui.YELLOW);
            return;
        }
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            if (p == null) return;
            ServerLevel level = (ServerLevel) p.level();
            if (!(level.getChunkSource().getGenerator() instanceof WorldGenV2ChunkGenerator v2)) {
                Minecraft.getInstance().execute(() -> app.say("This dimension is not generated by Worldgen V2.", Ui.YELLOW));
                return;
            }
            ChunkPos c = p.chunkPosition();
            int n = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    v2.regenerateLiveChunk(level, new ChunkPos(c.x() + dx, c.z() + dz));
                    n++;
                }
            }
            final int count = n;
            Minecraft.getInstance().execute(() -> app.say("Regenerated " + count + " chunks around you.", Ui.GREEN));
        });
    }

    /** Describes the terrain column at (x, z) and the structure placements near it; used by dev tooling to verify generation. */
    /** Surface heights along a line of columns (dx,dz per step), for finding terrain steps. */
    static String heights(int x, int z, int dx, int dz, int n) {
        MinecraftServer server = server();
        if (server == null) return "no world";
        try {
            return server.submit(() -> {
                ServerLevel level = server.overworld();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    int px = x + dx * i, pz = z + dz * i;
                    level.getChunk(px >> 4, pz >> 4);
                    sb.append(level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz)).append(i + 1 < n ? "," : "");
                }
                return sb.toString();
            }).get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            return "error: " + t;
        }
    }

    static String probe(int x, int z, int radius) {
        MinecraftServer server = server();
        if (server == null) return "no world";
        try {
            return server.submit(() -> {
                ServerLevel level = server.overworld();
                level.getChunk(x >> 4, z >> 4);
                StringBuilder sb = new StringBuilder();
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                sb.append("surfaceY=").append(top).append(" column(top->down): ");
                for (int y = top; y > top - 6; y--) {
                    sb.append(y).append("=").append(level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).getBlock().builtInRegistryHolder().key().identifier().getPath()).append(" ");
                }
                if (level.getChunkSource().getGenerator() instanceof WorldGenV2ChunkGenerator v2) {
                    var ws = v2.getActiveWorldStructure();
                    sb.append("\nplacements=").append(ws.getStructurePlacements().size());
                    for (var p : ws.getStructurePlacements()) {
                        int solid = 0, total = 0;
                        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) for (int dy = 0; dy < 12; dy++) {
                            if (p.x() + dx != x + dx && false) continue;
                            level.getChunk((p.x() + dx) >> 4, (p.z() + dz) >> 4);
                            var bs = level.getBlockState(new net.minecraft.core.BlockPos(p.x() + dx, p.y() + dy, p.z() + dz));
                            total++;
                            if (!bs.isAir() && !bs.getFluidState().isSource()) solid++;
                        }
                        sb.append("\n  ").append(p.id()).append(" at ").append(p.x()).append(",").append(p.y()).append(",").append(p.z())
                          .append(" prefabSize=").append(p.prefab().size()).append(" solidBlocksInBox=").append(solid).append("/").append(total);
                        if (sb.length() > 1500) break;
                    }
                }
                return sb.toString();
            }).get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            return "probe failed: " + t;
        }
    }

    /** Runs a server command as the server (dev tooling: set the time, move the camera, change game mode). */
    static String command(String cmd) {
        MinecraftServer server = server();
        if (server == null) return "no world";
        try {
            return server.submit(() -> {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
                return "ran: " + cmd;
            }).get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            return "command failed: " + t;
        }
    }

    /** Lists the gap-spanning placements (log bridges etc.) found within {@code radius} blocks of (x, z). */
    static String spans(int x, int z, int radius) {
        MinecraftServer server = server();
        if (server == null) return "no world";
        try {
            return server.submit(() -> {
                ServerLevel level = server.overworld();
                if (!(level.getChunkSource().getGenerator() instanceof WorldGenV2ChunkGenerator v2)) return "not a worldgenv2 world";
                var ws = v2.getActiveWorldStructure();
                var sp = ws.getPlacementEngine();
                if (sp == null) return "no placement rules";
                java.util.Map<String, String> found = new java.util.TreeMap<>();
                for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++)
                    for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++)
                        for (var p : sp.forChunk(ws, cx, cz))
                            found.put(p.id(), p.id() + " at " + p.x() + "," + p.y() + "," + p.z() + " size=" + p.prefab().size());
                return found.size() + " spans\n" + String.join("\n", found.values());
            }).get(120, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            return "spans failed: " + t;
        }
    }

    /**
     * A fingerprint of every block in the (2r+1)^2 chunks around (x, z), generating them if needed: two builds (or two runs) that
     * generate the same world give the same numbers. Returns the overall hash and a short hash per chunk.
     */
    static String hashArea(int x, int z, int r) {
        MinecraftServer server = server();
        if (server == null) return "no world";
        try {
            return server.submit(() -> {
                ServerLevel level = server.overworld();
                int minY = level.getMinY(), maxY = level.getMaxY();
                var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
                long all = 1469598103934665603L;
                StringBuilder per = new StringBuilder();
                for (int cx = (x >> 4) - r; cx <= (x >> 4) + r; cx++) {
                    for (int cz = (z >> 4) - r; cz <= (z >> 4) + r; cz++) {
                        var chunk = level.getChunk(cx, cz);
                        long h = 1469598103934665603L;
                        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = minY; y < maxY; y++) {
                            pos.set((cx << 4) + dx, y, (cz << 4) + dz);
                            h = (h ^ net.minecraft.world.level.block.Block.getId(chunk.getBlockState(pos))) * 1099511628211L;
                        }
                        all = (all ^ h) * 1099511628211L;
                        per.append(Long.toHexString(h), 0, 6).append(' ');
                    }
                }
                return Long.toHexString(all) + " | " + per.toString().trim();
            }).get(600, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            return "hash failed: " + t;
        }
    }

    /** Counts every block type in the chunk containing (x, z): proof of what generation actually produced. */
    static String census(int x, int z) {
        MinecraftServer server = server();
        if (server == null) return "no world";
        try {
            return server.submit(() -> {
                ServerLevel level = server.overworld();
                var chunk = level.getChunk(x >> 4, z >> 4);
                java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
                int minY = level.getMinY(), maxY = level.getMaxY();
                var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = minY; y < maxY; y++) {
                    pos.set((chunk.getPos().x() << 4) + dx, y, (chunk.getPos().z() << 4) + dz);
                    var bs = chunk.getBlockState(pos);
                    if (bs.isAir()) continue;
                    counts.merge(bs.getBlock().builtInRegistryHolder().key().identifier().getPath(), 1, Integer::sum);
                }
                StringBuilder sb = new StringBuilder("chunk " + chunk.getPos().x() + "," + chunk.getPos().z() + " Y " + minY + ".." + maxY);
                if (level.getChunkSource().getGenerator() instanceof WorldGenV2ChunkGenerator v2) {
                    var q = v2.getActiveWorldStructure().queryBiome(x, z);
                    var tb = q.primaryTileBiome();
                    sb.append(" biome=").append(tb == null ? "?" : tb.name()).append(" props=").append(tb == null ? 0 : tb.props().size());
                    var zl = v2.getActiveWorldStructure().getZoneLayout();
                    var zone = zl == null ? null : zl.zoneAt(x, z);
                    if (zone != null) sb.append(" zone=").append(zone.id);
                }
                sb.append(": ");
                counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(24)
                    .forEach(e -> sb.append(e.getKey()).append("=").append(e.getValue()).append(" "));
                return sb.toString();
            }).get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            return "census failed: " + t;
        }
    }

    /** Teleports the player to the surface at world (x, z), generating the chunk if needed. */
    static void teleport(StudioApp app, int x, int z) { teleport(app, x, z, 0); }

    static void teleport(StudioApp app, int x, int z, int upOffset) {
        MinecraftServer server = server();
        if (server == null) {
            app.say("Open a world to teleport.", Ui.YELLOW);
            return;
        }
        server.execute(() -> {
            if (server.getPlayerList().getPlayers().isEmpty()) return;
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            level.getChunk(x >> 4, z >> 4); // forces generation so the height is real
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1 + upOffset;
            p.teleportTo(x + 0.5, y, z + 0.5);
            Minecraft.getInstance().execute(() -> app.say("Teleported to " + x + ", " + y + ", " + z, Ui.GREEN));
        });
    }
}
