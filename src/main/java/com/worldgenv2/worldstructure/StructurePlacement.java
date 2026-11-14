package com.worldgenv2.worldstructure;

import com.worldgenv2.prefab.Prefab;

/**
 * A prefab pinned to world coordinates. {@code (x, y, z)} is where the prefab's anchor goes; the chunk generator
 * stamps the part of the prefab that falls inside each chunk it generates.
 */
public record StructurePlacement(String id, Prefab prefab, int x, int y, int z, String lootTableOverride) {

    public StructurePlacement(String id, Prefab prefab, int x, int y, int z) {
        this(id, prefab, x, y, z, null);
    }

    /** True when any part of this prefab can fall inside the chunk (with a margin for foundations). */
    public boolean touchesChunk(int chunkX, int chunkZ) {
        int baseX = x - prefab.anchor().x();
        int baseZ = z - prefab.anchor().z();
        int margin = 4;
        int minX = baseX - margin, maxX = baseX + prefab.size().x() + margin;
        int minZ = baseZ - margin, maxZ = baseZ + prefab.size().z() + margin;
        int cMinX = chunkX << 4, cMaxX = cMinX + 15, cMinZ = chunkZ << 4, cMaxZ = cMinZ + 15;
        return maxX >= cMinX && minX <= cMaxX && maxZ >= cMinZ && minZ <= cMaxZ;
    }
}
