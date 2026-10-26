package com.worldgenv2.engine.chunkgenerator;

/**
 * Calculates and bounds the minimal 2D chunk dependency window required to generate a stage
 * without runaway cascading generation.
 */
public record ChunkDependencyEnvelope(int targetChunkX, int targetChunkZ, int reachBlocks, int haloChunkRadius) {

    public ChunkDependencyEnvelope(int targetChunkX, int targetChunkZ, int reachBlocks) {
        this(
            targetChunkX,
            targetChunkZ,
            Math.max(0, reachBlocks),
            (int) Math.ceil(Math.max(0, reachBlocks) / 16.0)
        );
    }

    public int minChunkX() {
        return targetChunkX - haloChunkRadius;
    }

    public int maxChunkX() {
        return targetChunkX + haloChunkRadius;
    }

    public int minChunkZ() {
        return targetChunkZ - haloChunkRadius;
    }

    public int maxChunkZ() {
        return targetChunkZ + haloChunkRadius;
    }

    public int chunkRadius() {
        return haloChunkRadius;
    }

    public boolean containsChunk(int cx, int cz) {
        return cx >= minChunkX() && cx <= maxChunkX() && cz >= minChunkZ() && cz <= maxChunkZ();
    }

    public int totalChunkCount() {
        int side = 2 * haloChunkRadius + 1;
        return side * side;
    }

    public ChunkDependencyEnvelope union(ChunkDependencyEnvelope other) {
        if (other == null) return this;
        int minX = Math.min(this.minChunkX(), other.minChunkX());
        int maxX = Math.max(this.maxChunkX(), other.maxChunkX());
        int minZ = Math.min(this.minChunkZ(), other.minChunkZ());
        int maxZ = Math.max(this.maxChunkZ(), other.maxChunkZ());

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int radiusX = Math.max(Math.abs(minX - centerX), Math.abs(maxX - centerX));
        int radiusZ = Math.max(Math.abs(minZ - centerZ), Math.abs(maxZ - centerZ));
        int radius = Math.max(radiusX, radiusZ);
        return new ChunkDependencyEnvelope(centerX, centerZ, radius * 16, radius);
    }
}
