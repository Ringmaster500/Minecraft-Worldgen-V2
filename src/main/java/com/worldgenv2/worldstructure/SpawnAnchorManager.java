package com.worldgenv2.worldstructure;

import com.worldgenv2.density.FastNoiseLite;
import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * Manages guaranteed fixed-origin spawn features (such as a crash-landed colony ship at spawn),
 * impact crater excavation density carving, scorched ground blending, and player spawn point synchronization.
 */
public final class SpawnAnchorManager {

    /**
     * Declarative rule defining a guaranteed spawn structure and its environmental impact.
     */
    public record SpawnAnchorRule(
        String id,
        String prefabId,
        int originX,
        int originZ,
        float craterRadius,
        float craterDepth,
        float craterRimHeight,
        boolean lockPlayerSpawn,
        int spawnOffsetX,
        int spawnOffsetY,
        int spawnOffsetZ,
        String scorchBlockId,
        String secondaryScorchBlockId
    ) {
        public SpawnAnchorRule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prefabId, "prefabId");
            craterRadius = Math.max(0.0f, craterRadius);
            craterDepth = Math.max(0.0f, craterDepth);
        }

        public static SpawnAnchorRule empty() {
            return new SpawnAnchorRule(
                "empty",
                "",
                0,
                0,
                0.0f,
                0.0f,
                0.0f,
                false,
                0,
                0,
                0,
                "minecraft:stone",
                "minecraft:dirt"
            );
        }
    }

    private SpawnAnchorRule activeRule;
    private final FastNoiseLite craterWarpNoise;
    private BlockPos resolvedSpawnPos;

    public SpawnAnchorManager() {
        this(null);
    }

    public SpawnAnchorManager(SpawnAnchorRule rule) {
        this.activeRule = rule;
        this.craterWarpNoise = new FastNoiseLite();
        this.craterWarpNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.craterWarpNoise.setFrequency(0.05f);
    }

    public void setActiveRule(SpawnAnchorRule rule) {
        this.activeRule = rule;
        this.resolvedSpawnPos = null;
    }

    public SpawnAnchorRule getActiveRule() {
        return activeRule;
    }

    public boolean hasActiveRule() {
        return activeRule != null && activeRule.craterRadius() > 0.0f;
    }

    /**
     * Applies impact crater excavation to a terrain density column.
     * Carves out an organic bowl shape with a raised rim around the anchor origin.
     */
    public void carveCraterDensity(float x, float z, int minY, int maxY, float[] column) {
        if (!hasActiveRule()) return;

        float dx = x - activeRule.originX();
        float dz = z - activeRule.originZ();
        float distSq = dx * dx + dz * dz;
        float radius = activeRule.craterRadius();
        float rimRadius = radius * 1.25f;

        if (distSq > rimRadius * rimRadius) {
            return;
        }

        float dist = (float) Math.sqrt(distSq);
        float noise = craterWarpNoise.getNoise(x * 1.5f, z * 1.5f) * 3.5f;
        float effectiveDist = Math.max(0.0f, dist + noise);

        int height = maxY - minY;
        float depth = activeRule.craterDepth();
        float rimHeight = activeRule.craterRimHeight();

        for (int i = 0; i < height; i++) {
            int y = minY + i;

            if (effectiveDist < radius) {
                // Inside crater bowl: parabolic excavation
                float normalizedDist = effectiveDist / radius;
                float excavation = depth * (1.0f - (normalizedDist * normalizedDist));
                // Negative density carves air
                column[i] -= excavation * 1.2f;
            } else if (effectiveDist <= rimRadius && rimHeight > 0.0f) {
                // On crater rim: slight positive density ridge
                float rimT = (effectiveDist - radius) / (rimRadius - radius);
                float rimBump = (float) Math.sin(rimT * Math.PI) * rimHeight;
                column[i] += rimBump * 0.6f;
            }
        }
    }

    /**
     * Checks if a world block column is within the scorched crater blast perimeter.
     */
    public boolean isWithinScorchRadius(int x, int z) {
        if (!hasActiveRule()) return false;
        int dx = x - activeRule.originX();
        int dz = z - activeRule.originZ();
        float distSq = dx * dx + dz * dz;
        float maxRadius = activeRule.craterRadius() * 1.15f;
        return distSq <= maxRadius * maxRadius;
    }

    public BlockPos getResolvedSpawnPos() {
        return resolvedSpawnPos;
    }

    public void setResolvedSpawnPos(BlockPos pos) {
        this.resolvedSpawnPos = pos;
    }
}
