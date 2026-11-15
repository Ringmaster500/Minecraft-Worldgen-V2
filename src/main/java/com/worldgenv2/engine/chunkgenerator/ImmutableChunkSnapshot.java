package com.worldgenv2.engine.chunkgenerator;

import com.worldgenv2.api.VoxelSpace;

import java.util.Objects;

/**
 * Thread-safe, completely immutable block snapshot of a single chunk volume at a specific pipeline stage.
 * Can be safely read by subsequent stages and neighboring chunks concurrently without locks.
 */
public final class ImmutableChunkSnapshot<T> implements VoxelSpace<T> {

    public static final int CHUNK_SIZE_X = 16;
    public static final int CHUNK_SIZE_Z = 16;

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int maxY;
    private final int height;
    /** Each column is stored as runs of equal blocks: runBlocks[col][i] fills the heights below runEnds[col][i] (exclusive, relative to minY). */
    private final Object[][] runBlocks = new Object[CHUNK_SIZE_X * CHUNK_SIZE_Z][];
    private final short[][] runEnds = new short[CHUNK_SIZE_X * CHUNK_SIZE_Z][];
    private final T airBlock;

    @SuppressWarnings("unchecked")
    public ImmutableChunkSnapshot(int chunkX, int chunkZ, int minY, int maxY, Object[] blocks, T airBlock) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.maxY = maxY;
        this.height = Math.max(1, maxY - minY);
        Objects.requireNonNull(blocks, "blocks");
        this.airBlock = airBlock;
        Object[] tmpBlocks = new Object[height];
        short[] tmpEnds = new short[height];
        for (int col = 0; col < CHUNK_SIZE_X * CHUNK_SIZE_Z; col++) {
            int base = col * height, n = 0;
            for (int y = 0; y < height; y++) {
                Object b = blocks[base + y];
                if (n > 0 && tmpBlocks[n - 1] == b) { tmpEnds[n - 1] = (short) (y + 1); }
                else { tmpBlocks[n] = b; tmpEnds[n] = (short) (y + 1); n++; }
            }
            runBlocks[col] = java.util.Arrays.copyOf(tmpBlocks, n);
            runEnds[col] = java.util.Arrays.copyOf(tmpEnds, n);
        }
    }

    private ImmutableChunkSnapshot(int chunkX, int chunkZ, int minY, int maxY, Object[][] blocks, short[][] ends, T airBlock) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.maxY = maxY;
        this.height = Math.max(1, maxY - minY);
        this.airBlock = airBlock;
        System.arraycopy(blocks, 0, runBlocks, 0, runBlocks.length);
        System.arraycopy(ends, 0, runEnds, 0, runEnds.length);
    }

    /** A snapshot taken over from per-column runs (bottom to top; runEnds are exclusive heights relative to minY). */
    static <T> ImmutableChunkSnapshot<T> fromRuns(int chunkX, int chunkZ, int minY, int maxY, Object[][] blocks, short[][] ends, T airBlock) {
        return new ImmutableChunkSnapshot<>(chunkX, chunkZ, minY, maxY, blocks, ends, airBlock);
    }

    /** Number of runs in column (lx, lz); runs go bottom to top. */
    public int runCount(int lx, int lz) { return runEnds[(lx << 4) | lz].length; }

    /** Exclusive top of run i of column (lx, lz), relative to minY (the run starts where the previous one ends, or at 0). */
    public int runEnd(int lx, int lz, int i) { return runEnds[(lx << 4) | lz][i]; }

    /** The block of run i of column (lx, lz); null means nothing was stored there (air). */
    @SuppressWarnings("unchecked")
    public T runBlock(int lx, int lz, int i) { Object b = runBlocks[(lx << 4) | lz][i]; return b == null ? airBlock : (T) b; }

    /** A rough size in bytes, for the cache budget. */
    public int approxBytes() {
        int n = 64;
        for (int c = 0; c < runBlocks.length; c++) n += 40 + 4 * runBlocks[c].length + 2 * runEnds[c].length;
        return n;
    }

    /** Writes every block that is not air into {@code dst}, a run at a time. */
    public void copyTo(VoxelSpace<T> dst) {
        int ox = chunkX << 4, oz = chunkZ << 4;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int col = (lx << 4) | lz;
                Object[] rb = runBlocks[col];
                short[] re = runEnds[col];
                // top run first: the first solid run sets the heightmaps, the ones below it then leave them alone at once
                for (int i = re.length - 1; i >= 0; i--) {
                    int to = re[i], from = i == 0 ? 0 : re[i - 1];
                    @SuppressWarnings("unchecked") T b = (T) rb[i];
                    if (b != null && !b.equals(airBlock)) dst.setBlockRun(ox + lx, oz + lz, minY + from, minY + to, b);
                }
            }
        }
    }

    public static <T> ImmutableChunkSnapshot<T> capture(int chunkX, int chunkZ, VoxelSpace<T> source, T airBlock) {
        int minY = source.getMinY();
        int maxY = source.getMaxY();
        int height = maxY - minY;
        Object[] blocks = new Object[CHUNK_SIZE_X * CHUNK_SIZE_Z * height];

        int originX = chunkX * CHUNK_SIZE_X;
        int originZ = chunkZ * CHUNK_SIZE_Z;

        for (int lx = 0; lx < CHUNK_SIZE_X; lx++) {
            int wx = originX + lx;
            for (int lz = 0; lz < CHUNK_SIZE_Z; lz++) {
                int wz = originZ + lz;
                int columnOffset = ((lx << 4) | lz) * height;
                for (int y = minY; y < maxY; y++) {
                    T state = source.getBlock(wx, y, wz);
                    blocks[columnOffset + (y - minY)] = state;
                }
            }
        }
        return new ImmutableChunkSnapshot<>(chunkX, chunkZ, minY, maxY, blocks, airBlock);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getBlock(int x, int y, int z) {
        if (y < minY || y >= maxY) {
            return airBlock;
        }
        int col = ((x & 15) << 4) | (z & 15);
        int yIdx = y - minY;
        short[] ends = runEnds[col];
        int lo = 0, hi = ends.length - 1;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (ends[mid] > yIdx) hi = mid; else lo = mid + 1; }
        T state = (T) runBlocks[col][lo];
        return state != null ? state : airBlock;
    }

    /** The stored block at a position, null when nothing was stored (the snapshot's air). The position must be inside the height range. */
    public Object getRaw(int x, int y, int z) {
        int col = ((x & 15) << 4) | (z & 15);
        int yIdx = y - minY;
        short[] ends = runEnds[col];
        int lo = 0, hi = ends.length - 1;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (ends[mid] > yIdx) hi = mid; else lo = mid + 1; }
        return runBlocks[col][lo];
    }

    @Override
    public void setBlock(int x, int y, int z, T block) {
        throw new UnsupportedOperationException("Cannot mutate ImmutableChunkSnapshot at (" + x + "," + y + "," + z + ")");
    }

    @Override
    public boolean isAir(int x, int y, int z) {
        T b = getBlock(x, y, z);
        return b == null || Objects.equals(b, airBlock);
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getMaxY() {
        return maxY;
    }

    /** Copies one column (all heights) into dst starting at dstOffset; used to assemble context regions without per-block calls. */
    public void copyColumn(int lx, int lz, Object[] dst, int dstOffset, int count) {
        int col = (lx << 4) | lz, n = Math.min(count, height), from = 0;
        short[] ends = runEnds[col];
        for (int i = 0; i < ends.length && from < n; i++) {
            int to = Math.min(n, ends[i]);
            java.util.Arrays.fill(dst, dstOffset + from, dstOffset + to, runBlocks[col][i]);
            from = to;
        }
    }
}
