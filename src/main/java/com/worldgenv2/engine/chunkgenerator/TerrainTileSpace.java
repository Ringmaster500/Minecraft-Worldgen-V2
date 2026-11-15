package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;

/**
 * A scratch chunk used to compute terrain on demand. Reads outside the chunk are air, exactly as in the real chunk space, so a
 * tile and the real chunk it stands for come out identical.
 *
 * <p>Each column is kept as runs of equal blocks, top run first. The terrain stage writes a column from the top down, so a
 * write almost always extends the last run; any other write splits or merges runs. That builds the finished
 * {@link ImmutableChunkSnapshot} without ever holding (or scanning) a block per voxel.
 */
final class TerrainTileSpace<T> implements VoxelSpace<T> {
    private final int originX, originZ, minY, maxY, height;
    private final T air;
    /** Per column, the runs from the top: run i covers [lo[i], lo[i-1] - 1], run 0 reaches the top. The last run starts at 0. */
    private final Object[][] runBlocks = new Object[256][];
    private final int[][] runLo = new int[256][];
    private final int[] runCount = new int[256];
    /** While true, a column has only been written from the top down without gaps: runs hold [lastY, top] and everything below is unwritten. */
    private final boolean[] sequential = new boolean[256];
    private final int[] lastY = new int[256];

    TerrainTileSpace(int chunkX, int chunkZ, int minY, int maxY, T air) {
        this.originX = chunkX << 4;
        this.originZ = chunkZ << 4;
        this.minY = minY;
        this.maxY = maxY;
        this.height = maxY - minY;
        this.air = air;
        for (int c = 0; c < 256; c++) {
            runBlocks[c] = new Object[6];
            runLo[c] = new int[6];
            sequential[c] = true;
            lastY[c] = height;
            runCount[c] = 0;
        }
    }

    /** Switches a column to the general layout: the unwritten part below the written runs becomes an explicit run of nothing. */
    private void generalize(int col) {
        if (!sequential[col]) return;
        sequential[col] = false;
        int n = runCount[col];
        if (lastY[col] > 0 || n == 0) {
            if (n + 1 > runBlocks[col].length) { runBlocks[col] = java.util.Arrays.copyOf(runBlocks[col], n + 4); runLo[col] = java.util.Arrays.copyOf(runLo[col], n + 4); }
            runBlocks[col][n] = null;
            runLo[col][n] = 0;
            runCount[col] = n + 1;
        }
    }

    private int runAt(int col, int yIdx) {
        int[] lo = runLo[col];
        int i = runCount[col] - 1;
        while (i > 0 && lo[i - 1] <= yIdx) i--;
        return i;
    }

    @Override @SuppressWarnings("unchecked")
    public T getBlock(int x, int y, int z) {
        int lx = x - originX, lz = z - originZ;
        if (y < minY || y >= maxY || lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return air;
        int col = (lx << 4) | lz;
        if (sequential[col] && y - minY < lastY[col]) return air;
        Object b = runBlocks[col][runAt(col, y - minY)];
        return b == null ? air : (T) b;
    }

    @Override
    public void setBlock(int x, int y, int z, T block) {
        int lx = x - originX, lz = z - originZ;
        if (block == null || y < minY || y >= maxY || lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return;
        int col = (lx << 4) | lz, yIdx = y - minY;
        if (sequential[col]) {
            if (yIdx == lastY[col] - 1) {
                int cnt = runCount[col];
                Object[] srb = runBlocks[col];
                if (cnt > 0 && srb[cnt - 1] == block) {
                    runLo[col][cnt - 1] = yIdx;
                } else {
                    if (cnt == srb.length) { srb = runBlocks[col] = java.util.Arrays.copyOf(srb, cnt * 2); runLo[col] = java.util.Arrays.copyOf(runLo[col], cnt * 2); }
                    srb[cnt] = block;
                    runLo[col][cnt] = yIdx;
                    runCount[col] = cnt + 1;
                }
                lastY[col] = yIdx;
                return;
            }
            generalize(col);
        }
        Object[] rb = runBlocks[col];
        int[] lo = runLo[col];
        int n = runCount[col];
        int i = n - 1;
        while (i > 0 && lo[i - 1] <= yIdx) i--;
        if (rb[i] == block) return;
        int hi = i == 0 ? height - 1 : lo[i - 1] - 1;
        // pieces: [yIdx+1, hi] keeps the old block (if any), [yIdx] is the new block, [lo[i], yIdx-1] keeps the old block (if any)
        boolean above = yIdx < hi, below = yIdx > lo[i];
        Object old = rb[i];
        int oldLo = lo[i];
        int extra = (above ? 1 : 0) + (below ? 1 : 0);
        if (n + extra > rb.length) {
            rb = runBlocks[col] = java.util.Arrays.copyOf(rb, rb.length * 2);
            lo = runLo[col] = java.util.Arrays.copyOf(lo, lo.length * 2);
        }
        if (extra > 0) {
            System.arraycopy(rb, i + 1, rb, i + 1 + extra, n - i - 1);
            System.arraycopy(lo, i + 1, lo, i + 1 + extra, n - i - 1);
        }
        int at = i;
        if (above) { rb[at] = old; lo[at] = yIdx + 1; at++; }
        rb[at] = block; lo[at] = yIdx;
        if (below) { at++; rb[at] = old; lo[at] = oldLo; }
        n += extra;
        runCount[col] = n;
        // merge with equal neighbours: [first, last] is the range of runs just touched
        int first = Math.max(0, i - 1), last = Math.min(n - 1, i + extra + 1);
        for (int j = last; j > first; j--) {
            if (j < n && rb[j] == rb[j - 1]) {
                // runs j-1 (upper) and j (lower) are the same block: the lower run's start wins, drop the upper entry
                System.arraycopy(rb, j, rb, j - 1, n - j);
                System.arraycopy(lo, j, lo, j - 1, n - j);
                n--;
            }
        }
        runCount[col] = n;
    }

    @Override
    public void setBlockRun(int x, int z, int yFrom, int yTo, T block) {
        int lx = x - originX, lz = z - originZ;
        if (block == null || lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return;
        yFrom = Math.max(yFrom, minY);
        yTo = Math.min(yTo, maxY);
        if (yFrom >= yTo) return;
        int col = (lx << 4) | lz;
        if (sequential[col] && yTo - minY == lastY[col]) {
            int cnt = runCount[col];
            Object[] srb = runBlocks[col];
            if (cnt > 0 && srb[cnt - 1] == block) {
                runLo[col][cnt - 1] = yFrom - minY;
            } else {
                if (cnt == srb.length) { srb = runBlocks[col] = java.util.Arrays.copyOf(srb, cnt * 2); runLo[col] = java.util.Arrays.copyOf(runLo[col], cnt * 2); }
                srb[cnt] = block;
                runLo[col][cnt] = yFrom - minY;
                runCount[col] = cnt + 1;
            }
            lastY[col] = yFrom - minY;
            return;
        }
        for (int y = yTo - 1; y >= yFrom; y--) setBlock(x, y, z, block);
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        T b = getBlock(x, y, z);
        return b == null || b.equals(air);
    }

    @Override public int getMinY() { return minY; }

    @Override public int getMaxY() { return maxY; }

    /** The finished tile: the runs become a snapshot as they are. */
    ImmutableChunkSnapshot<T> toSnapshot(int chunkX, int chunkZ) {
        Object[][] blocks = new Object[256][];
        short[][] ends = new short[256][];
        for (int col = 0; col < 256; col++) {
            generalize(col);
            int n = runCount[col];
            Object[] rb = runBlocks[col];
            int[] lo = runLo[col];
            Object[] ob = new Object[n];
            short[] oe = new short[n];
            for (int k = 0; k < n; k++) {
                int src = n - 1 - k;                       // bottom run first
                ob[k] = rb[src];
                oe[k] = (short) (src == 0 ? height : lo[src - 1]);
            }
            blocks[col] = ob;
            ends[col] = oe;
        }
        return ImmutableChunkSnapshot.fromRuns(chunkX, chunkZ, minY, maxY, blocks, ends, air);
    }
}
