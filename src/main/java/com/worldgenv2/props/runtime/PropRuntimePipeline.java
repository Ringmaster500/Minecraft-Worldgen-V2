package com.worldgenv2.props.runtime;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.engine.chunkgenerator.ChunkDependencyEnvelope;
import com.worldgenv2.engine.chunkgenerator.ImmutableChunkSnapshot;
import com.worldgenv2.engine.chunkgenerator.MultiStageVoxelSpace;
import com.worldgenv2.engine.chunkgenerator.StageContextCache;
import com.worldgenv2.props.Prop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Manages and executes ordered, multi-stage {@link PropRuntime} generation passes with dynamic
 * context precision and immutable stage snapshot caching.
 */
public final class PropRuntimePipeline<T> {

    private final List<PropRuntime<T>> runtimes;
    private final Map<Integer, List<PropRuntime<T>>> stages = new TreeMap<>();
    private final List<Integer> stageIndices = new ArrayList<>();
    private final StageContextCache<T> contextCache;
    private final T airBlock;

    public PropRuntimePipeline(List<PropRuntime<T>> runtimes, StageContextCache<T> contextCache, T airBlock) {
        this.runtimes = runtimes != null ? List.copyOf(runtimes) : List.of();
        this.contextCache = contextCache != null ? contextCache : new StageContextCache<>();
        this.airBlock = airBlock;

        for (PropRuntime<T> runtime : this.runtimes) {
            stages.computeIfAbsent(runtime.getRuntimeIndex(), k -> new ArrayList<>()).add(runtime);
        }
        stageIndices.addAll(stages.keySet());
    }

    public List<PropRuntime<T>> getRuntimes() {
        return runtimes;
    }

    public List<Integer> getStageIndices() {
        return Collections.unmodifiableList(stageIndices);
    }

    public List<PropRuntime<T>> getRuntimesInStage(int stageIndex) {
        return stages.getOrDefault(stageIndex, List.of());
    }

    public StageContextCache<T> getContextCache() {
        return contextCache;
    }

    /**
     * Calculates the minimal dependency envelope for this chunk at a specific runtime stage.
     */
    public ChunkDependencyEnvelope computeStageEnvelope(int chunkX, int chunkZ, int stageIndex, long worldSeed) {
        List<PropRuntime<T>> stageRuntimes = stages.get(stageIndex);
        if (stageRuntimes == null || stageRuntimes.isEmpty()) {
            return new ChunkDependencyEnvelope(chunkX, chunkZ, 0);
        }

        int maxReach = 0;
        for (PropRuntime<T> r : stageRuntimes) {
            int reach = r.computeContextReach(chunkX, chunkZ, worldSeed);
            if (reach > maxReach) {
                maxReach = reach;
            }
        }
        return new ChunkDependencyEnvelope(chunkX, chunkZ, maxReach);
    }

    /**
     * Executes a specific runtime stage in order.
     */
    public void executeStage(
        int stageIndex,
        int chunkX,
        int chunkZ,
        VoxelSpace<T> voxelSpace,
        long chunkSeed,
        float[] surfaceNormals
    ) {
        List<PropRuntime<T>> stageRuntimes = stages.get(stageIndex);
        if (stageRuntimes == null || stageRuntimes.isEmpty()) {
            return;
        }

        MultiStageVoxelSpace<T> stageSpace = new MultiStageVoxelSpace<>(
            chunkX, chunkZ, stageIndex, voxelSpace, contextCache, airBlock
        );

        int originX = chunkX * 16;
        int originZ = chunkZ * 16;
        int minY = voxelSpace.getMinY();
        int maxY = voxelSpace.getMaxY();

        for (PropRuntime<T> runtime : stageRuntimes) {
            Prop<T> prop = runtime.getProp();
            for (int lx = 0; lx < 16; lx++) {
                int wx = originX + lx;
                for (int lz = 0; lz < 16; lz++) {
                    int wz = originZ + lz;

                    for (int y = maxY - 2; y >= minY + 1; y--) {
                        if (!stageSpace.isAir(wx, y, wz) && stageSpace.isAir(wx, y + 1, wz)) {
                            float nx = 0.0f, ny = 1.0f, nz = 0.0f;
                            long surfaceSeed = chunkSeed ^ ((long) wx * 3129871L) ^ ((long) wz * 116129781L) ^ y;
                            Prop.Context<T> ctx = new Prop.Context<>(
                                wx, y + 1, wz,
                                nx, ny, nz,
                                stageSpace,
                                surfaceSeed
                            );
                            prop.generate(ctx);
                        }
                    }
                }
            }
        }

        // Cache immutable snapshot of this chunk at stageIndex
        ImmutableChunkSnapshot<T> snapshot = ImmutableChunkSnapshot.capture(chunkX, chunkZ, voxelSpace, airBlock);
        contextCache.putSnapshot(chunkX, chunkZ, stageIndex, snapshot);
    }
}
