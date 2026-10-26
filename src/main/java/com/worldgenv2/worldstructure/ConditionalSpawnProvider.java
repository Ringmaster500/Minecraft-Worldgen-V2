package com.worldgenv2.worldstructure;

import com.worldgenv2.density.DensityFunction;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative Conditional Spawn Provider (Doc 10 §2).
 * <p>
 * Enforces macro spawn predicates such as isolated island starts (via a radial smoothstep
 * falloff mask {@code M(r) = 1.0 - smoothstep(r1, r2, r)} around the spawn origin),
 * biome whitelists, and surface elevation bounds.
 */
public final class ConditionalSpawnProvider {

    public enum LandmassType {
        CONTINENT,
        ISLAND
    }

    public enum FallbackStrategy {
        FORCE_GENERATION,
        NEAREST_VALID
    }

    public record SpawnPoint(
        int x,
        int y,
        int z,
        String biomeName,
        boolean islandMaskActive
    ) {}

    private final LandmassType landmassType;
    private final float originX;
    private final float originZ;
    private final float maxIslandRadius;
    private final float oceanBuffer;
    private final Set<String> biomeWhitelist;
    private final int minElevationY;
    private final int maxElevationY;
    private final FallbackStrategy fallbackStrategy;

    public ConditionalSpawnProvider(
        LandmassType landmassType,
        float originX,
        float originZ,
        float maxIslandRadius,
        float oceanBuffer,
        Set<String> biomeWhitelist,
        int minElevationY,
        int maxElevationY,
        FallbackStrategy fallbackStrategy
    ) {
        this.landmassType = Objects.requireNonNull(landmassType, "landmassType");
        this.originX = originX;
        this.originZ = originZ;
        this.maxIslandRadius = Math.max(16.0f, maxIslandRadius);
        this.oceanBuffer = Math.max(8.0f, oceanBuffer);
        this.biomeWhitelist = biomeWhitelist != null ? Set.copyOf(biomeWhitelist) : Set.of();
        this.minElevationY = minElevationY;
        this.maxElevationY = maxElevationY;
        this.fallbackStrategy = Objects.requireNonNull(fallbackStrategy, "fallbackStrategy");
    }

    /**
     * Factory for a curated "Isolated Island Start" configuration centered at (0, 0).
     */
    public static ConditionalSpawnProvider islandStart(
        float islandRadius,
        float oceanBuffer,
        Set<String> allowedBiomes,
        int minY,
        int maxY
    ) {
        return new ConditionalSpawnProvider(
            LandmassType.ISLAND,
            0.0f,
            0.0f,
            islandRadius,
            oceanBuffer,
            allowedBiomes,
            minY,
            maxY,
            FallbackStrategy.FORCE_GENERATION
        );
    }

    public LandmassType landmassType() {
        return landmassType;
    }

    public float maxIslandRadius() {
        return maxIslandRadius;
    }

    public float oceanBuffer() {
        return oceanBuffer;
    }

    /**
     * Evaluates the radial island mask M(r) in [0.0, 1.0]:
     * <ul>
     *   <li>{@code r <= maxIslandRadius}: {@code M(r) = 1.0} (full natural island terrain)</li>
     *   <li>{@code maxIslandRadius < r < maxIslandRadius + oceanBuffer}: C1 cubic smoothstep falloff</li>
     *   <li>{@code r >= maxIslandRadius + oceanBuffer}: {@code M(r) = 0.0} (surrounding ocean basin)</li>
     * </ul>
     */
    public float computeIslandMask(float x, float z) {
        if (landmassType != LandmassType.ISLAND) {
            return 1.0f;
        }
        float dx = x - originX;
        float dz = z - originZ;
        float r = (float) Math.sqrt(dx * dx + dz * dz);
        if (r <= maxIslandRadius) {
            return 1.0f;
        }
        float outerRadius = maxIslandRadius + oceanBuffer;
        if (r >= outerRadius) {
            return 0.0f;
        }
        float t = (r - maxIslandRadius) / oceanBuffer;
        float smooth = t * t * (3.0f - 2.0f * t);
        return 1.0f - smooth;
    }

    /**
     * Wraps a terrain {@link DensityFunction} with the radial island mask so terrain outside
     * {@code maxIslandRadius} smoothly dips below sea level into an ocean ring, while the central
     * origin is guaranteed to rise above sea level when {@code FORCE_GENERATION} is enabled.
     */
    public DensityFunction wrapDensityWithIslandMask(DensityFunction baseTerrain, float oceanDepressionStrength) {
        Objects.requireNonNull(baseTerrain, "baseTerrain");
        if (landmassType != LandmassType.ISLAND) {
            return baseTerrain;
        }
        float depression = Math.max(0.25f, oceanDepressionStrength);

        return new DensityFunction() {
            @Override
            public float compute(float x, float y, float z) {
                float base = baseTerrain.compute(x, y, z);
                return applyMaskToValue(x, y, z, base);
            }

            @Override
            public void computeColumn(float x, float z, int minY, int maxY, float[] out) {
                baseTerrain.computeColumn(x, z, minY, maxY, out);
                float mask = computeIslandMask(x, z);
                float coreBoost = computeCoreUplift(x, z);
                int height = maxY - minY;
                for (int i = 0; i < height; i++) {
                    int y = minY + i;
                    float val = out[i];
                    if (coreBoost > 0.0f && y <= maxElevationY) {
                        val += coreBoost * ((minElevationY + 6.0f - y) / 24.0f);
                    }
                    // Depress terrain outside island radius into an ocean ring
                    out[i] = val - (1.0f - mask) * depression;
                }
            }

            private float applyMaskToValue(float x, float y, float z, float base) {
                float mask = computeIslandMask(x, z);
                float coreBoost = computeCoreUplift(x, z);
                float val = base;
                if (coreBoost > 0.0f && y <= maxElevationY) {
                    val += coreBoost * ((minElevationY + 6.0f - y) / 24.0f);
                }
                return val - (1.0f - mask) * depression;
            }
        };
    }

    private float computeCoreUplift(float x, float z) {
        if (fallbackStrategy != FallbackStrategy.FORCE_GENERATION) {
            return 0.0f;
        }
        float dx = x - originX;
        float dz = z - originZ;
        float coreRadius = Math.min(48.0f, maxIslandRadius * 0.4f);
        float r = (float) Math.sqrt(dx * dx + dz * dz);
        if (r >= coreRadius) {
            return 0.0f;
        }
        float t = 1.0f - (r / coreRadius);
        return t * t * (3.0f - 2.0f * t) * 0.45f;
    }

    /**
     * Resolves a valid player spawn coordinate satisfying the configured landmass, biome, and elevation predicates.
     */
    public SpawnPoint resolveSpawnPoint(WorldStructure worldStructure, int minY, int maxY, int seaLevel) {
        int height = maxY - minY;
        float[] colBuffer = new float[height];
        float[] scratch = new float[height];

        int searchRadius = landmassType == LandmassType.ISLAND
            ? Math.round(maxIslandRadius * 0.85f)
            : 512;
        int step = 8;

        SpawnPoint bestFallback = null;

        for (int radius = 0; radius <= searchRadius; radius += step) {
            int samples = Math.max(1, (radius == 0) ? 1 : (int) Math.ceil((2 * Math.PI * radius) / step));
            for (int s = 0; s < samples; s++) {
                double angle = (2.0 * Math.PI * s) / samples;
                int wx = Math.round(originX + (float) (Math.cos(angle) * radius));
                int wz = Math.round(originZ + (float) (Math.sin(angle) * radius));

                WorldStructure.BiomeQueryResult query = worldStructure.queryBiome(wx, wz);
                String biomeName = query.primaryTileBiome().name();
                boolean biomeMatch = matchesBiomeWhitelist(biomeName);

                query.computeBlendedColumn(wx, wz, minY, maxY, colBuffer, scratch);
                float mask = computeIslandMask(wx, wz);

                int surfaceY = findTopSolidY(colBuffer, minY, maxY, mask);
                if (surfaceY > seaLevel && surfaceY >= minElevationY && surfaceY <= maxElevationY) {
                    SpawnPoint candidate = new SpawnPoint(
                        wx,
                        surfaceY + 1,
                        wz,
                        biomeName,
                        landmassType == LandmassType.ISLAND
                    );
                    if (biomeMatch) {
                        return candidate;
                    }
                    if (bestFallback == null) {
                        bestFallback = candidate;
                    }
                }
            }
        }

        if (bestFallback != null) {
            return bestFallback;
        }

        // FORCE_GENERATION fallback at origin
        WorldStructure.BiomeQueryResult originQuery = worldStructure.queryBiome(originX, originZ);
        int forcedY = Math.clamp(seaLevel + 4, minElevationY, maxElevationY);
        return new SpawnPoint(
            Math.round(originX),
            forcedY,
            Math.round(originZ),
            originQuery.primaryTileBiome().name(),
            landmassType == LandmassType.ISLAND
        );
    }

    private boolean matchesBiomeWhitelist(String biomeName) {
        if (biomeWhitelist.isEmpty()) {
            return true;
        }
        if (biomeWhitelist.contains(biomeName)) {
            return true;
        }
        if (biomeName.contains(":")) {
            String shortName = biomeName.substring(biomeName.indexOf(':') + 1);
            return biomeWhitelist.contains(shortName);
        }
        return false;
    }

    private int findTopSolidY(float[] colBuffer, int minY, int maxY, float islandMask) {
        float depression = (landmassType == LandmassType.ISLAND) ? (1.0f - islandMask) * 0.85f : 0.0f;
        for (int y = maxY - 1; y >= minY; y--) {
            if (colBuffer[y - minY] - depression > 0.0f) {
                return y;
            }
        }
        return minY;
    }
}
