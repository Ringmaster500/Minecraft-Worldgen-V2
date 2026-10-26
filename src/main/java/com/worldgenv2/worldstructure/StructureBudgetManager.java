package com.worldgenv2.worldstructure;

import java.util.*;

/**
 * Finite & Distance-Budgeted Structure Engine.
 * <p>
 * Controls macro POI populations across infinite worlds to prevent repetitive procedural clutter.
 * Enforces strict world quota caps (e.g., exactly 4 to 6 ruined cities between radius 1,200 and 5,000 blocks),
 * minimum distance separation buffers, biome filters, and custom loot table assignments.
 */
public final class StructureBudgetManager {

    /**
     * Declarative rule for a distance-budgeted POI group.
     */
    public record BudgetedFeatureRule(
        String id,
        String prefabId,
        float minRadius,
        float maxRadius,
        float minSeparation,
        int minQuota,
        int maxQuota,
        Set<String> allowedBiomes,
        String lootTableOverride
    ) {
        public BudgetedFeatureRule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prefabId, "prefabId");
            minRadius = Math.max(0.0f, minRadius);
            maxRadius = Math.max(minRadius + 64.0f, maxRadius);
            minSeparation = Math.max(32.0f, minSeparation);
            minQuota = Math.max(1, minQuota);
            maxQuota = Math.max(minQuota, maxQuota);
            allowedBiomes = allowedBiomes != null ? Set.copyOf(allowedBiomes) : Set.of();
        }
    }

    /**
     * Concretely placed instance of a budgeted feature.
     */
    public record PlacedBudgetedFeature(
        String ruleId,
        String prefabId,
        int x,
        int y,
        int z,
        float yaw,
        String biomeName,
        String lootTableOverride
    ) {}

    private final List<BudgetedFeatureRule> rules = new ArrayList<>();
    private final List<PlacedBudgetedFeature> placedFeatures = new ArrayList<>();
    private final Map<Long, List<PlacedBudgetedFeature>> chunkIndex = new HashMap<>();

    public StructureBudgetManager() {}

    public void addRule(BudgetedFeatureRule rule) {
        rules.add(Objects.requireNonNull(rule, "rule"));
    }

    public List<BudgetedFeatureRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public List<PlacedBudgetedFeature> getPlacedFeatures() {
        return Collections.unmodifiableList(placedFeatures);
    }

    public List<PlacedBudgetedFeature> getFeaturesInChunk(int chunkX, int chunkZ) {
        long key = (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
        List<PlacedBudgetedFeature> list = chunkIndex.get(key);
        return list != null ? list : List.of();
    }

    /**
     * Builds and locks concrete placements for all budgeted rules against the given world structure and seed.
     */
    public void buildBudgets(WorldStructure worldStructure, int minY, int maxY, int seaLevel, long seed) {
        placedFeatures.clear();
        chunkIndex.clear();

        for (int rIdx = 0; rIdx < rules.size(); rIdx++) {
            BudgetedFeatureRule rule = rules.get(rIdx);
            long ruleSeed = seed ^ (rule.id().hashCode() * 31L + rIdx * 1009L);

            // Determine quota between [minQuota, maxQuota]
            int quota = rule.minQuota();
            if (rule.maxQuota() > rule.minQuota()) {
                int diff = rule.maxQuota() - rule.minQuota() + 1;
                quota = rule.minQuota() + (int) (Math.abs(ruleSeed % diff));
            }

            List<PlacedBudgetedFeature> rulePlacements = new ArrayList<>();
            int maxAttempts = quota * 64;

            for (int attempt = 0; attempt < maxAttempts && rulePlacements.size() < quota; attempt++) {
                long h = hash(ruleSeed, attempt);
                float u = ((h & 0xFFFF) / 65535.0f);
                float v = (((h >>> 16) & 0xFFFF) / 65535.0f);

                float radius = rule.minRadius() + u * (rule.maxRadius() - rule.minRadius());
                double angle = v * 2.0 * Math.PI;

                int wx = (int) Math.round(Math.cos(angle) * radius);
                int wz = (int) Math.round(Math.sin(angle) * radius);

                // Check distance separation against all already placed features
                boolean tooClose = false;
                float sepSq = rule.minSeparation() * rule.minSeparation();
                for (PlacedBudgetedFeature existing : placedFeatures) {
                    float dx = existing.x() - wx;
                    float dz = existing.z() - wz;
                    if (dx * dx + dz * dz < sepSq) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) continue;

                // Check biome validity
                WorldStructure.BiomeQueryResult query = worldStructure.queryBiome(wx, wz);
                String biomeName = query.primaryTileBiome().name();
                if (!rule.allowedBiomes().isEmpty() && !matchesBiome(biomeName, rule.allowedBiomes())) {
                    continue;
                }

                // Sample surface elevation
                int surfaceY = sampleSurface(worldStructure, wx, wz, minY, maxY);
                if (surfaceY <= seaLevel) {
                    continue; // Skip waterlogged ocean placements
                }

                float yaw = (((h >>> 32) & 3) * 90.0f);
                PlacedBudgetedFeature placed = new PlacedBudgetedFeature(
                    rule.id(),
                    rule.prefabId(),
                    wx,
                    surfaceY + 1,
                    wz,
                    yaw,
                    biomeName,
                    rule.lootTableOverride()
                );

                rulePlacements.add(placed);
                placedFeatures.add(placed);

                // Index by chunk
                int cx = wx >> 4;
                int cz = wz >> 4;
                long cKey = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                chunkIndex.computeIfAbsent(cKey, k -> new ArrayList<>()).add(placed);
            }
        }
    }

    private static boolean matchesBiome(String biomeName, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return true;
        for (String a : allowed) {
            if (biomeName.equalsIgnoreCase(a) || biomeName.endsWith(":" + a)) return true;
        }
        return false;
    }

    private static int sampleSurface(WorldStructure structure, int x, int z, int minY, int maxY) {
        int height = maxY - minY;
        float[] col = new float[height];
        float[] scratch = new float[height];
        WorldStructure.BiomeQueryResult query = structure.queryBiome(x, z);
        query.computeBlendedColumn(x, z, minY, maxY, col, scratch);
        structure.applyTerrainModifiers(x, z, minY, maxY, col);

        for (int y = maxY - 1; y >= minY; y--) {
            if (col[y - minY] > 0.0f) {
                return y;
            }
        }
        return minY + 64;
    }

    private static long hash(long seed, int attempt) {
        long h = seed ^ (attempt * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
