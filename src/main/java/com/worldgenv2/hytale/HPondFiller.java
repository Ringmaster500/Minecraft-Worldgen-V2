package com.worldgenv2.hytale;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.props.Prop;

import java.util.PriorityQueue;
import java.util.Set;

/**
 * Hytale's PondFiller (docs.hytale.com/hytale-generator/prop): finds the depressions that already exist in the terrain inside
 * Bounds and fills them with FillMaterial. Walls are the blocks of BarrierBlockSet; water cannot seep through them. The bounding
 * box is the reach: only ponds that fit inside it are filled, because a basin that leaks out of the box over its lowest rim at the
 * box edge cannot hold anything.
 *
 * <p>Each column's resting level is found by a priority flood from the box's edge: a column fills to the lowest rim any path to the
 * edge has to cross.
 */
public final class HPondFiller<T> implements Prop<T> {

    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Set<T> barrier;
    private final MaterialProvider<T> fill;

    public HPondFiller(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Set<T> barrier, MaterialProvider<T> fill) {
        this.minX = Math.min(minX, maxX); this.maxX = Math.max(minX, maxX);
        this.minY = Math.min(minY, maxY); this.maxY = Math.max(minY, maxY);
        this.minZ = Math.min(minZ, maxZ); this.maxZ = Math.max(minZ, maxZ);
        this.barrier = barrier;
        this.fill = fill;
    }

    @Override
    public boolean generate(Context<T> ctx) {
        VoxelSpace<T> space = ctx.voxelSpace();
        int x0 = ctx.worldX() + minX, x1 = ctx.worldX() + maxX, z0 = ctx.worldZ() + minZ, z1 = ctx.worldZ() + maxZ;
        int y0 = Math.max(space.getMinY(), ctx.worldY() + minY), y1 = Math.min(space.getMaxY() - 1, ctx.worldY() + maxY);
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        if (w < 3 || d < 3 || y1 < y0) return false;

        int[] ground = new int[w * d];
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                int g = y0 - 1;
                for (int y = y1; y >= y0; y--) if (barrier.contains(space.getBlock(x0 + i, y, z0 + k))) { g = y; break; }
                ground[i * d + k] = g;
            }
        }

        int[] level = new int[w * d];
        java.util.Arrays.fill(level, Integer.MAX_VALUE);
        PriorityQueue<int[]> queue = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                if (i == 0 || k == 0 || i == w - 1 || k == d - 1) { level[i * d + k] = ground[i * d + k]; queue.add(new int[]{level[i * d + k], i, k}); }
            }
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            if (cur[0] > level[cur[1] * d + cur[2]]) continue;
            for (int[] a : around) {
                int ni = cur[1] + a[0], nk = cur[2] + a[1];
                if (ni < 0 || nk < 0 || ni >= w || nk >= d) continue;
                int nl = Math.max(ground[ni * d + nk], cur[0]);
                if (nl < level[ni * d + nk]) { level[ni * d + nk] = nl; queue.add(new int[]{nl, ni, nk}); }
            }
        }

        boolean any = false;
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                int g = ground[i * d + k], l = level[i * d + k];
                if (l <= g || g < y0) continue;            // not a depression (or no floor inside the box to hold water)
                for (int y = g + 1; y <= l && y <= y1; y++) {
                    if (barrier.contains(space.getBlock(x0 + i, y, z0 + k))) continue;
                    T m = fill.getMaterial(x0 + i, y, z0 + k, l - y, 0, 0f, space);
                    if (m != null) { space.setBlock(x0 + i, y, z0 + k, m); any = true; }
                }
            }
        }
        return any;
    }

    @Override
    public int horizontalReach() { return Math.max(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(minZ), Math.abs(maxZ))); }
}