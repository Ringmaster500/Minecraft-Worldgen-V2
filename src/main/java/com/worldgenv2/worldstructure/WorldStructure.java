package com.worldgenv2.worldstructure;

import com.worldgenv2.density.DensityFunction;
import com.worldgenv2.material.MaterialProvider;
import com.worldgenv2.props.Prop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Root world structure manager.
 * Coordinates macro TileBiome Voronoi partitioning (BiCarta) with priority-ordered
 * CustomBiome smooth noise overrides, per-biome MaterialProviders, and Props.
 */
public final class WorldStructure {

    /**
     * Complete resolved biome context for a world column (x, z), including
     * multi-biome Voronoi neighbor blending (primary, secondary, tertiary)
     * and CustomBiome smooth override weights.
     */
    public record BiomeQueryResult(
        int effectiveBiomeId,
        TileBiome primaryTileBiome,
        TileBiome secondaryTileBiome,
        TileBiome tertiaryTileBiome,
        float distanceToEdge,
        float tileBlendFactor,
        float tertiaryBlendFactor,
        CustomBiome customBiome,
        float customBlendWeight
    ) {
        public BiomeQueryResult(
            int effectiveBiomeId,
            TileBiome primaryTileBiome,
            TileBiome secondaryTileBiome,
            float distanceToEdge,
            float tileBlendFactor,
            CustomBiome customBiome,
            float customBlendWeight
        ) {
            this(
                effectiveBiomeId,
                primaryTileBiome,
                secondaryTileBiome,
                primaryTileBiome,
                distanceToEdge,
                tileBlendFactor,
                0.0f,
                customBiome,
                customBlendWeight
            );
        }

        /**
         * Evaluates the composite blended terrain density column [minY, maxY) into `out`.
         * Propagates column batching down to primary, secondary, tertiary, and custom biome density DAGs.
         */
        public void computeBlendedColumn(
            float x,
            float z,
            int minY,
            int maxY,
            float[] out,
            float[] scratch
        ) {
            int height = maxY - minY;
            if (height <= 0) return;

            boolean hasSecondary = tileBlendFactor > 0.001f
                && secondaryTileBiome != null
                && secondaryTileBiome.id() != primaryTileBiome.id();
            boolean hasTertiary = tertiaryBlendFactor > 0.001f
                && tertiaryTileBiome != null
                && tertiaryTileBiome.id() != primaryTileBiome.id()
                && (secondaryTileBiome == null || tertiaryTileBiome.id() != secondaryTileBiome.id());

            // Fast-path only when CustomBiome completely overrides AND no Voronoi neighbor is blending
            if (customBiome != null && customBlendWeight >= 0.999f && !hasSecondary && !hasTertiary) {
                customBiome.terrainDensity().computeColumn(x, z, minY, maxY, out);
                return;
            }

            // Evaluate primary TileBiome column
            primaryTileBiome.terrainDensity().computeColumn(x, z, minY, maxY, out);

            // Blend CustomBiome into its parent primary TileBiome first so Voronoi borders remain C1 continuous
            if (customBiome != null && customBlendWeight > 0.001f) {
                customBiome.terrainDensity().computeColumn(x, z, minY, maxY, scratch);
                float invCustom = 1.0f - customBlendWeight;
                for (int i = 0; i < height; i++) {
                    out[i] = out[i] * invCustom + scratch[i] * customBlendWeight;
                }
            }

            if (hasSecondary || hasTertiary) {
                float w2 = hasSecondary ? tileBlendFactor : 0.0f;
                float w3 = hasTertiary ? tertiaryBlendFactor : 0.0f;
                float w1 = Math.max(0.0f, 1.0f - w2 - w3);

                for (int i = 0; i < height; i++) {
                    out[i] *= w1;
                }

                if (hasSecondary) {
                    secondaryTileBiome.terrainDensity().computeColumn(x, z, minY, maxY, scratch);
                    for (int i = 0; i < height; i++) {
                        out[i] += scratch[i] * w2;
                    }
                }

                if (hasTertiary) {
                    tertiaryTileBiome.terrainDensity().computeColumn(x, z, minY, maxY, scratch);
                    for (int i = 0; i < height; i++) {
                        out[i] += scratch[i] * w3;
                    }
                }
            }
        }

        /**
         * Evaluates scalar composite density at a single (x, y, z) point.
         */
        public float computeBlendedPoint(float x, float y, float z) {
            boolean hasSecondary = tileBlendFactor > 0.001f
                && secondaryTileBiome != null
                && secondaryTileBiome.id() != primaryTileBiome.id();
            boolean hasTertiary = tertiaryBlendFactor > 0.001f
                && tertiaryTileBiome != null
                && tertiaryTileBiome.id() != primaryTileBiome.id()
                && (secondaryTileBiome == null || tertiaryTileBiome.id() != secondaryTileBiome.id());

            if (customBiome != null && customBlendWeight >= 0.999f && !hasSecondary && !hasTertiary) {
                return customBiome.terrainDensity().compute(x, y, z);
            }

            float d = primaryTileBiome.terrainDensity().compute(x, y, z);
            if (customBiome != null && customBlendWeight > 0.001f) {
                float dc = customBiome.terrainDensity().compute(x, y, z);
                d = d * (1.0f - customBlendWeight) + dc * customBlendWeight;
            }

            if (hasSecondary || hasTertiary) {
                float w2 = hasSecondary ? tileBlendFactor : 0.0f;
                float w3 = hasTertiary ? tertiaryBlendFactor : 0.0f;
                float w1 = Math.max(0.0f, 1.0f - w2 - w3);
                d *= w1;
                if (hasSecondary) {
                    d += secondaryTileBiome.terrainDensity().compute(x, y, z) * w2;
                }
                if (hasTertiary) {
                    d += tertiaryTileBiome.terrainDensity().compute(x, y, z) * w3;
                }
            }
            return d;
        }

        /**
         * Resolves the active MaterialProvider for this column.
         * Synchronized 1:1 with {@link #effectiveBiomeId()} so Minecraft's biome colormap and feature
         * placement always match the underlying surface material without cross-biome tint artifacts.
         */
        public <T> MaterialProvider<T> resolveMaterialProvider(int x, int z, MaterialProvider<T> fallback) {
            if (customBiome != null && effectiveBiomeId == customBiome.id()) {
                MaterialProvider<T> customMat = customBiome.typedMaterialProvider();
                if (customMat != null) {
                    return customMat;
                }
            }

            if (secondaryTileBiome != null && effectiveBiomeId == secondaryTileBiome.id()) {
                MaterialProvider<T> secondaryMat = secondaryTileBiome.typedMaterialProvider();
                if (secondaryMat != null) {
                    return secondaryMat;
                }
            }

            MaterialProvider<T> primaryMat = primaryTileBiome.typedMaterialProvider();
            return primaryMat != null ? primaryMat : fallback;
        }

        /**
         * Resolves the active surface Prop list for this column, synchronized 1:1 with {@link #effectiveBiomeId()}.
         */
        public <T> List<Prop<T>> resolveProps(List<Prop<T>> fallback) {
            if (customBiome != null && effectiveBiomeId == customBiome.id()) {
                List<Prop<T>> customProps = customBiome.typedProps();
                if (!customProps.isEmpty()) {
                    return customProps;
                }
            }

            if (secondaryTileBiome != null && effectiveBiomeId == secondaryTileBiome.id()) {
                List<Prop<T>> secondaryProps = secondaryTileBiome.typedProps();
                if (!secondaryProps.isEmpty()) {
                    return secondaryProps;
                }
            }

            List<Prop<T>> primaryProps = primaryTileBiome.typedProps();
            return !primaryProps.isEmpty() ? primaryProps : fallback;
        }

        /**
         * Evaluates 3-octave coherent 2D value noise (28-block macro lobes + 10-block patches + 3-block grain)
         * in [0.0, 1.0] so biome material borders form organic fingers and clearings instead of straight lines.
         */
        private static float coherentPatchNoise(int x, int z) {
            float lobes = smoothGridNoise(x * 0.036f, z * 0.036f, 987654321);
            float medium = smoothGridNoise(x * 0.10f, z * 0.10f, 1013904223);
            float detail = smoothGridNoise(x * 0.30f, z * 0.30f, 1664525);
            return Math.clamp(lobes * 0.58f + medium * 0.30f + detail * 0.12f, 0.0f, 1.0f);
        }

        private static float smoothGridNoise(float fx, float fz, int salt) {
            int ix = (int) Math.floor(fx);
            int iz = (int) Math.floor(fz);
            float tx = fx - ix;
            float tz = fz - iz;
            float ux = tx * tx * (3.0f - 2.0f * tx);
            float uz = tz * tz * (3.0f - 2.0f * tz);

            float v00 = cellHashUnit(ix, iz, salt);
            float v10 = cellHashUnit(ix + 1, iz, salt);
            float v01 = cellHashUnit(ix, iz + 1, salt);
            float v11 = cellHashUnit(ix + 1, iz + 1, salt);

            float nx0 = v00 + (v10 - v00) * ux;
            float nx1 = v01 + (v11 - v01) * ux;
            return nx0 + (nx1 - nx0) * uz;
        }

        private static float cellHashUnit(int x, int z, int salt) {
            int h = salt + x * 374761393 + z * 668265263;
            h = (h ^ (h >> 13)) * 1274126177;
            return ((h ^ (h >> 16)) & 0x7FFF) / 32768.0f;
        }
    }

    private final BiCarta<TileBiome> biCarta;
    private final List<CustomBiome> customBiomes;
    private volatile ConditionalSpawnProvider conditionalSpawnProvider;
    private volatile MacroFeatureGraph macroFeatureGraph;
    private volatile SplineRoadEngine splineRoadEngine;
    private volatile CaveCarver caveCarver;
    private volatile RiverCarver riverCarver;
    private volatile MaterialProvider<?> riverMaterialProvider;
    private volatile UndergroundBiomeMap undergroundBiomeMap;
    private volatile DimensionLayoutConfig dimensionLayout;
    private volatile SpawnAnchorManager spawnAnchorManager = new SpawnAnchorManager();
    private volatile StructureBudgetManager structureBudgetManager = new StructureBudgetManager();

    private final long worldSeed;
    private volatile java.util.List<OreRule> oreRules = java.util.List.of();
    private volatile ZoneLayout zoneLayout;

    public long seed() { return worldSeed; }

    public WorldStructure withOreRules(java.util.List<OreRule> rules) {
        this.oreRules = rules == null ? java.util.List.of() : java.util.List.copyOf(rules);
        return this;
    }

    public java.util.List<OreRule> getOreRules() { return oreRules; }

    public ZoneLayout getZoneLayout() { return zoneLayout; }

    /** Restricts biome selection per zone (Hytale-style macro zones). Pass null to remove. */
    public WorldStructure withZoneLayout(ZoneLayout layout) {
        this.zoneLayout = layout;
        if (layout == null) {
            biCarta.setZoneFilter(null);
        } else {
            layout.bind(biCarta.getTileBiomes());
            biCarta.setZoneFilter(layout::eligibleIds);
        }
        return this;
    }

    public WorldStructure(List<TileBiome> tileBiomes, List<CustomBiome> customBiomes, int cellSize, long seed) {
        this.worldSeed = seed;
        this.biCarta = new BiCarta<>(tileBiomes, cellSize, seed);
        this.customBiomes = new ArrayList<>(customBiomes);
        // Sort custom biomes by descending priority
        this.customBiomes.sort(Comparator.comparingInt(CustomBiome::priority).reversed());
    }

    public WorldStructure withDimensionLayout(DimensionLayoutConfig layout) {
        this.dimensionLayout = layout;
        if (layout != null && !layout.biomePoolEntries().isEmpty()) {
            for (DimensionLayoutConfig.BiomePoolEntry entry : layout.biomePoolEntries()) {
                TileBiome existing = findTileBiomeByName(entry.biomeName());
                if (existing != null) {
                    TileBiome updated = new TileBiome(
                        existing.id(),
                        existing.name(),
                        Math.max(0.01f, entry.weight()),
                        Math.clamp(entry.sizeModifier(), 0.5f, 1.8f),
                        Math.max(8.0f, entry.transitionDistance()),
                        existing.terrainDensity(),
                        existing.materialProvider(),
                        existing.props(),
                        existing.climate(),
                        existing.defaultBlock(),
                        existing.fluidBlock(),
                        existing.seaLevel()
                    );
                    updateTileBiome(updated);
                }
            }
        }
        return this;
    }

    public DimensionLayoutConfig getDimensionLayout() {
        return dimensionLayout;
    }

    public boolean isVoidColumn(float x, float z) {
        return dimensionLayout != null && dimensionLayout.isVoidColumn(x, z);
    }

    public WorldStructure withConditionalSpawn(ConditionalSpawnProvider spawnProvider) {
        this.conditionalSpawnProvider = spawnProvider;
        return this;
    }

    public WorldStructure withMacroFeatureGraph(MacroFeatureGraph graph) {
        this.macroFeatureGraph = graph;
        return this;
    }

    public WorldStructure withSplineRoadEngine(SplineRoadEngine roadEngine) {
        this.splineRoadEngine = roadEngine;
        return this;
    }

    public ConditionalSpawnProvider getConditionalSpawnProvider() {
        return conditionalSpawnProvider;
    }

    public MacroFeatureGraph getMacroFeatureGraph() {
        return macroFeatureGraph;
    }

    public SplineRoadEngine getSplineRoadEngine() {
        return splineRoadEngine;
    }

    public WorldStructure withCaveCarver(CaveCarver carver) {
        this.caveCarver = carver;
        return this;
    }

    public WorldStructure withRiverCarver(RiverCarver carver) {
        this.riverCarver = carver;
        return this;
    }

    public WorldStructure withRiverMaterialProvider(MaterialProvider<?> provider) {
        this.riverMaterialProvider = provider;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> MaterialProvider<T> getRiverMaterialProvider() {
        return (MaterialProvider<T>) riverMaterialProvider;
    }

    public WorldStructure withUndergroundBiomes(List<UndergroundBiome> biomes, long seed) {
        this.undergroundBiomeMap = new UndergroundBiomeMap(biomes, seed);
        return this;
    }

    public RiverCarver getRiverCarver() {
        return riverCarver;
    }

    public CaveCarver getCaveCarver() {
        return caveCarver;
    }

    public boolean hasUndergroundBiomes() {
        return undergroundBiomeMap != null && !undergroundBiomeMap.isEmpty();
    }

    public int undergroundBiomeMaxY() {
        return hasUndergroundBiomes() ? undergroundBiomeMap.maxY() : Integer.MIN_VALUE;
    }

    /**
     * Applies the global terrain modifiers (river valleys, volumetric caves, and macro dimension layout
     * radial zones / outside-bounds fallbacks) to an already biome-blended density column.
     */
    public void applyTerrainModifiers(float x, float z, int minY, int maxY, float[] column) {
        if (riverCarver != null) {
            riverCarver.carveColumn(x, z, minY, maxY, column);
        }
        if (caveCarver != null) {
            caveCarver.carveColumn(x, z, minY, maxY, column);
        }
        if (dimensionLayout != null) {
            dimensionLayout.applyMacroZonesAndBounds(x, z, minY, maxY, column);
        }
        if (zoneLayout != null) {
            zoneLayout.applyTerrain(x, z, minY, maxY, column);
        }
        if (spawnAnchorManager != null && spawnAnchorManager.hasActiveRule()) {
            spawnAnchorManager.carveCraterDensity(x, z, minY, maxY, column);
        }
    }

    public SpawnAnchorManager getSpawnAnchorManager() {
        return spawnAnchorManager;
    }

    public WorldStructure withSpawnAnchorManager(SpawnAnchorManager spawnAnchorManager) {
        this.spawnAnchorManager = spawnAnchorManager;
        return this;
    }

    private volatile java.util.List<StructurePlacement> structurePlacements = java.util.List.of();

    public WorldStructure withStructurePlacements(java.util.List<StructurePlacement> placements) {
        this.structurePlacements = placements == null ? java.util.List.of() : java.util.List.copyOf(placements);
        return this;
    }

    public java.util.List<StructurePlacement> getStructurePlacements() {
        return structurePlacements;
    }

    private volatile SpanPlacer spanPlacer;

    public WorldStructure withSpanPlacer(SpanPlacer placer) {
        this.spanPlacer = placer == null || placer.isEmpty() ? null : placer;
        return this;
    }

    public SpanPlacer getSpanPlacer() {
        return spanPlacer;
    }

    /** Fixed placements plus gap-spanning placements (fallen-log bridges and the like) that touch the chunk. */
    public java.util.List<StructurePlacement> placementsForChunk(int chunkX, int chunkZ) {
        SpanPlacer sp = spanPlacer;
        if (sp == null) return structurePlacements;
        java.util.List<StructurePlacement> all = new java.util.ArrayList<>(structurePlacements);
        all.addAll(sp.forChunk(this, chunkX, chunkZ));
        return all;
    }

    /**
     * Surface height at (x, z) exactly as chunk generation will produce it: blended biome density, then the global
     * terrain modifiers (rivers, caves, zones, spawn crater). Returns minY when the column is empty.
     */
    public int sampleSurfaceY(float x, float z, int minY, int maxY) {
        float[] col = new float[Math.max(1, maxY - minY)];
        float[] scratch = new float[col.length];
        queryBiome(x, z).computeBlendedColumn(x, z, minY, maxY, col, scratch);
        applyTerrainModifiers(x, z, minY, maxY, col);
        for (int y = maxY - 1; y >= minY; y--) {
            if (col[y - minY] > 0.0f) return y;
        }
        return minY;
    }

    public StructureBudgetManager getStructureBudgetManager() {
        return structureBudgetManager;
    }

    public WorldStructure withStructureBudgetManager(StructureBudgetManager structureBudgetManager) {
        this.structureBudgetManager = structureBudgetManager;
        return this;
    }

    /**
     * True 3D biome query: resolves the underground biome volume at (x, y, z), or null for plain rock.
     */
    public UndergroundBiome getUndergroundBiome(int x, int y, int z) {
        if (undergroundBiomeMap == null) {
            return null;
        }
        return undergroundBiomeMap.query(x, y, z, () -> queryBiome(x, z).primaryTileBiome().id());
    }

    /**
     * True when (x, z) lies inside a river channel.
     */
    public boolean isRiverChannel(float x, float z) {
        return riverCarver != null && riverCarver.isChannel(x, z);
    }

    /**
     * Resolves the complete biome query result (including CustomBiome smooth overrides and macro radial zone overrides) for (x, z).
     */
    public BiomeQueryResult queryBiome(float x, float z) {
        BiCarta.CellQueryResult<TileBiome> cell = biCarta.query(x, z);
        TileBiome primary = cell.primary();
        if (dimensionLayout != null) {
            String zoneBiomeName = dimensionLayout.resolveZoneBiomeOverride(x, z);
            if (zoneBiomeName != null) {
                TileBiome overrideBiome = biCarta.findTileBiomeByName(zoneBiomeName);
                if (overrideBiome != null) {
                    primary = overrideBiome;
                }
            }
        }
        int primaryId = primary.id();

        CustomBiome matchedCustom = null;
        float customWeight = 0.0f;

        for (CustomBiome custom : customBiomes) {
            float w = custom.computeBlendWeight(primaryId, x, z);
            if (w > 0.0f) {
                // If the neighboring Voronoi biome is NOT a valid parent for this CustomBiome,
                // smoothly attenuate customWeight down to 0.0 before reaching the Voronoi border
                // so the CustomBiome never gets sliced off by a cliff when primaryId flips.
                TileBiome secondary = cell.secondary();
                if (secondary != null
                    && secondary.id() != primaryId
                    && !custom.parentBiomeIds().isEmpty()
                    && !custom.parentBiomeIds().contains(secondary.id())) {
                    float borderT = Math.clamp(
                        cell.distanceToEdge() / Math.max(16.0f, primary.defaultTransitionDistance()),
                        0.0f,
                        1.0f
                    );
                    float borderSmooth = borderT * borderT * (3.0f - 2.0f * borderT);
                    w *= borderSmooth;
                }
                if (w > 0.0f) {
                    matchedCustom = custom;
                    customWeight = w;
                    break;
                }
            }
        }

        int effectiveId = primaryId;
        float patch = BiomeQueryResult.coherentPatchNoise(Math.round(x), Math.round(z));

        // 1. Organic Voronoi boundary dappling: when near a biome transition corridor (blendFactor > 0.10),
        // the two biomes interlock with organic noise fingers and clearings instead of a harsh geometric line.
        if (cell.secondary() != null && cell.secondary().id() != primaryId && cell.blendFactor() > 0.10f) {
            float secondaryChance = (cell.blendFactor() - 0.10f) / 0.80f; // smoothly reaches ~0.50 at the exact border
            if (patch < secondaryChance) {
                effectiveId = cell.secondary().id();
            }
        }

        // 2. CustomBiome smooth noise overlay override
        if (matchedCustom != null && customWeight > 0.18f) {
            float remappedCustom = Math.clamp((customWeight - 0.18f) / 0.64f, 0.0f, 1.0f);
            if (remappedCustom > patch) {
                effectiveId = matchedCustom.id();
            }
        }

        return new BiomeQueryResult(
            effectiveId,
            primary,
            cell.secondary(),
            cell.tertiary(),
            cell.distanceToEdge(),
            cell.blendFactor(),
            cell.tertiaryBlendFactor(),
            matchedCustom,
            customWeight
        );
    }

    /**
     * Resolves the effective biome ID for world coordinate (x, z).
     */
    public int getBiomeId(float x, float z) {
        return queryBiome(x, z).effectiveBiomeId();
    }

    public BiCarta<TileBiome> getBiCarta() {
        return biCarta;
    }

    public List<CustomBiome> getCustomBiomes() {
        return List.copyOf(customBiomes);
    }

    public TileBiome findTileBiome(int id) {
        return biCarta.findTileBiome(id);
    }

    public TileBiome findTileBiomeByName(String name) {
        return biCarta.findTileBiomeByName(name);
    }

    public CustomBiome findCustomBiomeByName(String name) {
        if (name == null) return null;
        String clean = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        String norm = clean.replace("_", "").toLowerCase(java.util.Locale.ROOT);
        for (CustomBiome cb : customBiomes) {
            String cn = cb.name().replace("_", "").toLowerCase(java.util.Locale.ROOT);
            if (cn.equals(norm)) return cb;
        }
        return null;
    }

    public void updateTileBiome(TileBiome updated) {
        biCarta.updateTileBiome(updated);
    }

    public void updateCustomBiome(CustomBiome updated) {
        for (int i = 0; i < customBiomes.size(); i++) {
            if (customBiomes.get(i).id() == updated.id() || customBiomes.get(i).name().equalsIgnoreCase(updated.name())) {
                customBiomes.set(i, updated);
                return;
            }
        }
    }
}
