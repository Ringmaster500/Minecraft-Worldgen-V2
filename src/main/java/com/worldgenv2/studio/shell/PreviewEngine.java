package com.worldgenv2.studio.shell;

import com.worldgenv2.pack.ProjectCompiler;
import com.worldgenv2.studio.project.StudioProject;
import com.worldgenv2.worldstructure.WorldStructure;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background worker for the map preview. Compiling a dimension and sampling a biome grid can take hundreds of
 * milliseconds, so it never runs on the render thread. Requests are generation-stamped: a newer request makes
 * any older in-flight result get discarded rather than shown.
 */
final class PreviewEngine {

    /** One finished frame of preview data. */
    static final class Frame {
        ProjectCompiler.Result compiled;
        int[] argb;            // biome colours, texW * texH
        int texW, texH;
        double centerX, centerZ, blocksPerSample;
        long generation;
        long computeMillis;
        String error;
    }

    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "worldgenv2-preview");
        t.setDaemon(true);
        return t;
    });
    private volatile long latestGeneration;
    private volatile Frame ready;
    private volatile boolean busy;
    /** When true, each zone's colour is blended over the biome colours. */
    volatile boolean zoneTint;
    private volatile ProjectCompiler.Result cachedCompile;
    private volatile String cachedKey = "";

    boolean busy() { return busy; }

    /** Returns the newest finished frame once, then null until another completes. */
    Frame take() {
        Frame f = ready;
        if (f != null && f.generation == latestGeneration) { ready = null; return f; }
        return null;
    }

    /**
     * @param compileKey changes whenever the project content (or seed) changed, forcing a recompile;
     *                   unchanged keys reuse the compiled world and only resample (pan/zoom is cheap).
     */
    void request(StudioProject project, String dimId, long seedOverride, String compileKey,
                 double cx, double cz, double blocksPerSample, int texW, int texH) {
        final long gen = ++latestGeneration;
        final boolean tint = zoneTint;
        busy = true;
        pool.execute(() -> {
            long t0 = System.currentTimeMillis();
            Frame f = new Frame();
            f.generation = gen;
            f.texW = texW;
            f.texH = texH;
            f.centerX = cx;
            f.centerZ = cz;
            f.blocksPerSample = blocksPerSample;
            try {
                if (gen != latestGeneration) return;
                ProjectCompiler.Result res = cachedCompile;
                if (res == null || !compileKey.equals(cachedKey)) {
                    res = ProjectCompiler.compile(project, dimId, seedOverride);
                    cachedCompile = res;
                    cachedKey = compileKey;
                }
                f.compiled = res;
                f.argb = new int[texW * texH];
                WorldStructure s = res.structure;
                if (s != null) {
                    for (int y = 0; y < texH; y++) {
                        if (gen != latestGeneration) return;
                        double wz = cz + (y - texH / 2.0) * blocksPerSample;
                        for (int x = 0; x < texW; x++) {
                            double wx = cx + (x - texW / 2.0) * blocksPerSample;
                            f.argb[y * texW + x] = colorAt(res, s, (float) wx, (float) wz, tint);
                        }
                    }
                }
            } catch (Throwable t) {
                f.error = String.valueOf(t);
            }
            f.computeMillis = System.currentTimeMillis() - t0;
            if (gen == latestGeneration) {
                ready = f;
                busy = false;
            }
        });
    }

    private static int colorAt(ProjectCompiler.Result res, WorldStructure s, float x, float z, boolean tint) {
        if (s.isVoidColumn(x, z)) return 0xFF05060A;
        int id = s.getBiomeId(x, z);
        ProjectCompiler.BiomeBinding b = res.bindingById(id);
        int base = b == null ? 0xFF555A66 : Cat.color(b.category());
        // slight per-biome variation so two biomes of one category are distinguishable
        int h = b == null ? 0 : (id * 2654435761L > 0 ? (int) ((id * 40503L) % 28) : 0) - 14;
        int r = clamp(((base >> 16) & 255) + h), g = clamp(((base >> 8) & 255) + h), bl = clamp((base & 255) + h);
        if (tint && res.zones != null) {
            var zone = res.zones.zoneAt(x, z);
            if (zone != null) {
                r = (int) (r * 0.45f + ((zone.color >> 16) & 255) * 0.55f);
                g = (int) (g * 0.45f + ((zone.color >> 8) & 255) * 0.55f);
                bl = (int) (bl * 0.45f + (zone.color & 255) * 0.55f);
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
