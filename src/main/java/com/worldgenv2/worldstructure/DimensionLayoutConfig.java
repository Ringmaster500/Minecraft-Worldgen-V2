package com.worldgenv2.worldstructure;

import com.worldgenv2.density.FastNoiseLite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Node-driven Macro Dimension Layout & Spatial Bounds Engine.
 * <p>
 * Evaluates dimension-wide spatial rules compiled from the Studio Dimension Macro Graph or World Presets:
 * <ul>
 *   <li><b>Concentric Radial Zones ({@link RadialZoneRule}):</b> Sculpts central islands, void abyss moats,
 *       outer floating island archipelagos (e.g. The End), or regional biome overrides.</li>
 *   <li><b>Circular Landmark Rings ({@link RingLandmarkRule}):</b> Places deterministic rings of procedural
 *       pillars or towers (e.g. 10 Obsidian Pillars around the central End island).</li>
 *   <li><b>Bounded Modifications & Outside-Bounds Fallback ({@link OutsideBoundsFallback}):</b> Defines what
 *       happens beyond {@code boundsRadius} ({@code DEFAULT_BIOMES}, {@code VOID}, {@code OCEAN}, or
 *       {@code VANILLA_DEFAULT}) with a C1-continuous {@code blendFalloff} transition.</li>
 * </ul>
 */
public final class DimensionLayoutConfig {

    /**
     * Behavior for all world columns outside {@link #boundsRadius()} when {@code boundsRadius > 0}.
     */
    public enum OutsideBoundsFallback {
        /** Seamlessly blends back into the dimension's infinite weighted Voronoi biome pool. */
        DEFAULT_BIOMES,
        /** Drops terrain density into open void air (Skyblock, floating realms, bounded arenas). */
        VOID,
        /** Depresses terrain below sea level into an endless ocean basin. */
        OCEAN,
        /** Transitions smoothly to standard continental baseline terrain. */
        VANILLA_DEFAULT
    }

    /**
     * Spatial operation performed by a {@link RadialZoneRule} within {@code [innerRadius, outerRadius]}.
     */
    public enum ZoneMode {
        /** Sculpts a solid grounded or floating island plateau (e.g. Spawn Island or Central End Island). */
        ISLAND_PLATEAU,
        /** Carves terrain to empty void air within the ring (e.g. 800-block End void moat). */
        VOID_MOAT,
        /** Generates 3D volumetric floating archipelago islands separated by void gaps (e.g. Outer End Islands). */
        OUTER_ISLANDS,
        /** Overrides the primary biome within the radial zone without forcing custom density geometry. */
        BIOME_OVERRIDE
    }

    /**
     * A radial disk or concentric ring rule around {@code (centerX, centerZ)}.
     */
    public record RadialZoneRule(
        String id,
        float centerX,
        float centerZ,
        float innerRadius,
        float outerRadius,
        float falloff,
        ZoneMode mode,
        String targetBiomeName,
        float targetElevation,
        float thickness,
        boolean floatingUnderside
    ) {
        public RadialZoneRule {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(mode, "mode");
            innerRadius = Math.max(0.0f, innerRadius);
            outerRadius = Math.max(innerRadius + 8.0f, outerRadius);
            falloff = Math.max(4.0f, falloff);
            targetBiomeName = targetBiomeName != null ? targetBiomeName : "";
            thickness = Math.max(8.0f, thickness);
        }

        /**
         * Evaluates the C1-continuous smoothstep zone mask in {@code [0.0, 1.0]} at {@code (x, z)},
         * incorporating subtle coastline noise so rings and islands have organic shorelines.
         */
        public float computeWeight(float x, float z, float coastlineWarp) {
            float dx = x - centerX;
            float dz = z - centerZ;
            float rawDist = (float) Math.sqrt(dx * dx + dz * dz);
            float effectiveDist = Math.max(0.0f, rawDist + coastlineWarp);

            if (effectiveDist < innerRadius - falloff || effectiveDist > outerRadius + falloff) {
                return 0.0f;
            }

            float inWeight = 1.0f;
            if (innerRadius > 0.001f) {
                if (effectiveDist <= innerRadius - falloff) {
                    return 0.0f;
                } else if (effectiveDist < innerRadius + falloff) {
                    float t = (effectiveDist - (innerRadius - falloff)) / (2.0f * falloff);
                    inWeight = smoothstep(t);
                }
            }

            float outWeight = 1.0f;
            if (effectiveDist >= outerRadius + falloff) {
                return 0.0f;
            } else if (effectiveDist > outerRadius - falloff) {
                float t = (effectiveDist - (outerRadius - falloff)) / (2.0f * falloff);
                outWeight = 1.0f - smoothstep(t);
            }

            return Math.clamp(inWeight * outWeight, 0.0f, 1.0f);
        }
    }

    /**
     * Declarative circular array of procedural pillars/landmarks (e.g. End Obsidian Pillars).
     */
    public record RingLandmarkRule(
        String id,
        float centerX,
        float centerZ,
        float ringRadius,
        int count,
        int pillarRadius,
        int minHeight,
        int maxHeight,
        String pillarBlockId,
        String capBlockId
    ) {
        public RingLandmarkRule {
            Objects.requireNonNull(id, "id");
            ringRadius = Math.max(8.0f, ringRadius);
            count = Math.clamp(count, 1, 64);
            pillarRadius = Math.clamp(pillarRadius, 1, 8);
            minHeight = Math.max(16, minHeight);
            maxHeight = Math.max(minHeight, maxHeight);
            pillarBlockId = (pillarBlockId != null && !pillarBlockId.isBlank()) ? pillarBlockId : "minecraft:obsidian";
            capBlockId = (capBlockId != null && !capBlockId.isBlank()) ? capBlockId : "minecraft:bedrock";
        }

        /**
         * Resolves the concrete world coordinates and heights of every pillar in the ring.
         */
        public List<PlacedRingPillar> resolvePillars() {
            List<PlacedRingPillar> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                double angle = (2.0 * Math.PI * i) / count;
                int px = Math.round(centerX + (float) (Math.cos(angle) * ringRadius));
                int pz = Math.round(centerZ + (float) (Math.sin(angle) * ringRadius));
                int topY = (count <= 1)
                    ? maxHeight
                    : minHeight + Math.round(((float) i / (count - 1)) * (maxHeight - minHeight));
                int rad = Math.clamp(pillarRadius + (i % 3) - 1, 2, 6);
                list.add(new PlacedRingPillar(id + "_" + i, px, pz, rad, topY, pillarBlockId, capBlockId));
            }
            return list;
        }
    }

    /**
     * Concrete instantiated pillar from a {@link RingLandmarkRule}.
     */
    public record PlacedRingPillar(
        String id,
        int centerX,
        int centerZ,
        int radius,
        int topY,
        String pillarBlockId,
        String capBlockId
    ) {}

    /**
     * Custom biome weight/size override entry compiled from the Dimension Macro Graph.
     */
    public record BiomePoolEntry(
        String biomeName,
        float weight,
        float sizeModifier,
        float transitionDistance
    ) {}

    private final String presetName;
    private final float boundsRadius;
    private final float blendFalloff;
    private final OutsideBoundsFallback outsideFallback;
    private final int seaLevelOverride;
    private final String defaultBlockId;
    private final String defaultFluidId;
    private final List<RadialZoneRule> radialZones;
    private final List<RingLandmarkRule> ringLandmarks;
    private final List<PlacedRingPillar> resolvedPillars;
    private final List<BiomePoolEntry> biomePoolEntries;

    private final FastNoiseLite coastlineNoise;
    private final FastNoiseLite islandSurfaceNoise;
    private final FastNoiseLite outerArchipelagoNoise;
    private final FastNoiseLite outerDetailNoise;

    public DimensionLayoutConfig(
        String presetName,
        float boundsRadius,
        float blendFalloff,
        OutsideBoundsFallback outsideFallback,
        int seaLevelOverride,
        String defaultBlockId,
        String defaultFluidId,
        List<RadialZoneRule> radialZones,
        List<RingLandmarkRule> ringLandmarks,
        List<BiomePoolEntry> biomePoolEntries,
        long seed
    ) {
        this.presetName = presetName != null ? presetName : "CustomDimensionLayout";
        this.boundsRadius = Math.max(0.0f, boundsRadius);
        this.blendFalloff = Math.max(8.0f, blendFalloff);
        this.outsideFallback = Objects.requireNonNullElse(outsideFallback, OutsideBoundsFallback.DEFAULT_BIOMES);
        this.seaLevelOverride = seaLevelOverride;
        this.defaultBlockId = defaultBlockId != null ? defaultBlockId : "minecraft:stone";
        this.defaultFluidId = defaultFluidId != null ? defaultFluidId : "minecraft:water";
        this.radialZones = radialZones != null ? List.copyOf(radialZones) : List.of();
        this.ringLandmarks = ringLandmarks != null ? List.copyOf(ringLandmarks) : List.of();
        this.biomePoolEntries = biomePoolEntries != null ? List.copyOf(biomePoolEntries) : List.of();

        List<PlacedRingPillar> pillars = new ArrayList<>();
        for (RingLandmarkRule rule : this.ringLandmarks) {
            pillars.addAll(rule.resolvePillars());
        }
        this.resolvedPillars = Collections.unmodifiableList(pillars);

        int s = (int) (seed ^ (seed >>> 32));
        this.coastlineNoise = new FastNoiseLite(s + 701);
        this.coastlineNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.coastlineNoise.setFrequency(0.008f);
        this.coastlineNoise.setFractalType(FastNoiseLite.FractalType.FBm);
        this.coastlineNoise.setFractalOctaves(2);

        this.islandSurfaceNoise = new FastNoiseLite(s + 702);
        this.islandSurfaceNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.islandSurfaceNoise.setFrequency(0.012f);
        this.islandSurfaceNoise.setFractalType(FastNoiseLite.FractalType.FBm);
        this.islandSurfaceNoise.setFractalOctaves(3);

        this.outerArchipelagoNoise = new FastNoiseLite(s + 703);
        this.outerArchipelagoNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.outerArchipelagoNoise.setFrequency(0.0045f);
        this.outerArchipelagoNoise.setFractalType(FastNoiseLite.FractalType.FBm);
        this.outerArchipelagoNoise.setFractalOctaves(3);

        this.outerDetailNoise = new FastNoiseLite(s + 704);
        this.outerDetailNoise.setNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        this.outerDetailNoise.setFrequency(0.018f);
    }

    /**
     * Factory: Standard infinite Overworld layout with default biome fallback.
     */
    public static DimensionLayoutConfig overworldDefault(long seed) {
        return new DimensionLayoutConfig(
            "OverworldContinent",
            0.0f,
            64.0f,
            OutsideBoundsFallback.DEFAULT_BIOMES,
            63,
            "minecraft:stone",
            "minecraft:water",
            List.of(),
            List.of(),
            List.of(),
            seed
        );
    }

    /**
     * Factory: Isolated Spawn Island at (0,0) with an ocean moat and configurable Outside-Bounds Fallback.
     */
    public static DimensionLayoutConfig spawnIslandLayout(
        float islandRadius,
        float moatOuterRadius,
        float boundsRadius,
        OutsideBoundsFallback outsideFallback,
        String spawnBiomeName,
        long seed
    ) {
        List<RadialZoneRule> zones = new ArrayList<>();
        zones.add(new RadialZoneRule(
            "spawn_island_core",
            0.0f,
            0.0f,
            0.0f,
            islandRadius,
            36.0f,
            ZoneMode.ISLAND_PLATEAU,
            spawnBiomeName != null ? spawnBiomeName : "TemperateForest",
            72.0f,
            40.0f,
            false
        ));
        if (moatOuterRadius > islandRadius + 16.0f) {
            zones.add(new RadialZoneRule(
                "spawn_ocean_ring",
                0.0f,
                0.0f,
                islandRadius,
                moatOuterRadius,
                40.0f,
                ZoneMode.BIOME_OVERRIDE,
                "Ocean",
                45.0f,
                24.0f,
                false
            ));
        }
        return new DimensionLayoutConfig(
            "SpawnIslandAndLandmarks",
            boundsRadius,
            64.0f,
            outsideFallback,
            63,
            "minecraft:stone",
            "minecraft:water",
            zones,
            List.of(),
            List.of(),
            seed
        );
    }

    /**
     * Factory: The End Dimension Macro Layout (Central Island + 10 Obsidian Towers + 800-block Void Moat + Outer Archipelago Islands).
     */
    public static DimensionLayoutConfig endDimensionLayout(long seed) {
        List<RadialZoneRule> zones = List.of(
            new RadialZoneRule(
                "central_end_island",
                0.0f,
                0.0f,
                0.0f,
                190.0f,
                32.0f,
                ZoneMode.ISLAND_PLATEAU,
                "StonyPeaks",
                62.0f,
                38.0f,
                true
            ),
            new RadialZoneRule(
                "end_void_moat",
                0.0f,
                0.0f,
                190.0f,
                960.0f,
                36.0f,
                ZoneMode.VOID_MOAT,
                "",
                0.0f,
                16.0f,
                true
            ),
            new RadialZoneRule(
                "outer_end_islands",
                0.0f,
                0.0f,
                960.0f,
                30000.0f,
                64.0f,
                ZoneMode.OUTER_ISLANDS,
                "AridDunes",
                60.0f,
                28.0f,
                true
            )
        );
        List<RingLandmarkRule> rings = List.of(
            new RingLandmarkRule(
                "obsidian_towers",
                0.0f,
                0.0f,
                43.0f,
                10,
                3,
                76,
                103,
                "minecraft:obsidian",
                "minecraft:bedrock"
            )
        );
        return new DimensionLayoutConfig(
            "EndRingsAndArchipelago",
            0.0f,
            64.0f,
            OutsideBoundsFallback.VOID,
            0,
            "minecraft:end_stone",
            "minecraft:air",
            zones,
            rings,
            List.of(),
            seed
        );
    }

    public String presetName() { return presetName; }
    public float boundsRadius() { return boundsRadius; }
    public float blendFalloff() { return blendFalloff; }
    public OutsideBoundsFallback outsideFallback() { return outsideFallback; }
    public int seaLevelOverride() { return seaLevelOverride; }
    public String defaultBlockId() { return defaultBlockId; }
    public String defaultFluidId() { return defaultFluidId; }
    public List<RadialZoneRule> radialZones() { return radialZones; }
    public List<RingLandmarkRule> ringLandmarks() { return ringLandmarks; }
    public List<PlacedRingPillar> resolvedPillars() { return resolvedPillars; }
    public List<BiomePoolEntry> biomePoolEntries() { return biomePoolEntries; }

    /**
     * Resolves an optional biome name override for world coordinate {@code (x, z)} if inside a radial zone
     * with a configured {@code targetBiomeName}.
     */
    public String resolveZoneBiomeOverride(float x, float z) {
        if (radialZones.isEmpty()) {
            return null;
        }
        float coastWarp = coastlineNoise.getNoise(x, z) * 22.0f;
        String bestBiome = null;
        float bestWeight = 0.35f;
        for (RadialZoneRule zone : radialZones) {
            if (zone.targetBiomeName() == null || zone.targetBiomeName().isEmpty()) {
                continue;
            }
            float w = zone.computeWeight(x, z, coastWarp);
            if (w > bestWeight) {
                bestWeight = w;
                bestBiome = zone.targetBiomeName();
            }
        }
        return bestBiome;
    }

    /**
     * Returns {@code true} if column {@code (x, z)} is dominated by a {@link ZoneMode#VOID_MOAT},
     * empty space between {@link ZoneMode#OUTER_ISLANDS}, or outside {@link #boundsRadius} with
     * {@link OutsideBoundsFallback#VOID} — suppressing bedrock floor and sea-level water filling.
     */
    public boolean isVoidColumn(float x, float z) {
        if (boundsRadius > 0.0f && outsideFallback == OutsideBoundsFallback.VOID) {
            float r = (float) Math.sqrt(x * x + z * z);
            if (r > boundsRadius + blendFalloff * 0.5f) {
                return true;
            }
        }
        if (radialZones.isEmpty()) {
            return false;
        }
        float coastWarp = coastlineNoise.getNoise(x, z) * 22.0f;
        for (RadialZoneRule zone : radialZones) {
            float w = zone.computeWeight(x, z, coastWarp);
            if (w > 0.5f) {
                if (zone.mode() == ZoneMode.VOID_MOAT || zone.floatingUnderside()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Applies all configured {@link RadialZoneRule}s and the {@link OutsideBoundsFallback} to the
     * density column {@code [minY, maxY)}.
     */
    public void applyMacroZonesAndBounds(float x, float z, int minY, int maxY, float[] column) {
        int height = maxY - minY;
        if (height <= 0) {
            return;
        }

        float coastWarp = coastlineNoise.getNoise(x, z) * 22.0f;

        // 1. Evaluate Radial Zones in order
        for (RadialZoneRule zone : radialZones) {
            float w = zone.computeWeight(x, z, coastWarp);
            if (w <= 0.001f) {
                continue;
            }

            switch (zone.mode()) {
                case VOID_MOAT -> {
                    for (int i = 0; i < height; i++) {
                        column[i] = column[i] * (1.0f - w) + (-6.0f) * w;
                    }
                }
                case ISLAND_PLATEAU -> {
                    float dx = x - zone.centerX();
                    float dz = z - zone.centerZ();
                    float r = (float) Math.sqrt(dx * dx + dz * dz);
                    float normR = Math.clamp(r / Math.max(1.0f, zone.outerRadius()), 0.0f, 1.2f);
                    float radialProfile = Math.max(0.0f, 1.0f - normR * normR);
                    float surfRipple = islandSurfaceNoise.getNoise(x, z) * 6.5f;
                    float topY = zone.targetElevation() + surfRipple * radialProfile;

                    if (zone.floatingUnderside()) {
                        // Floating island SDF (like The End Central Island): solid between [bottomY, topY]
                        float halfThick = (zone.thickness() * 0.5f) * (0.25f + 0.75f * radialProfile);
                        float midY = topY - halfThick;
                        for (int i = 0; i < height; i++) {
                            int y = minY + i;
                            float dy = Math.abs(y - midY);
                            float islandDen = (halfThick - dy) / 10.0f;
                            column[i] = column[i] * (1.0f - w) + islandDen * w;
                        }
                    } else {
                        // Grounded starter island: raises core above sea level while tapering edges
                        for (int i = 0; i < height; i++) {
                            int y = minY + i;
                            float islandDen = (topY - y) / 20.0f;
                            column[i] = column[i] * (1.0f - w) + Math.max(column[i], islandDen) * w;
                        }
                    }
                }
                case OUTER_ISLANDS -> {
                    float macro = outerArchipelagoNoise.getNoise(x, z);
                    float detail = outerDetailNoise.getNoise(x, z) * 0.25f;
                    // Islands form where macro + detail > 0.08
                    float islandMask = (macro + detail - 0.08f) * 2.2f;
                    for (int i = 0; i < height; i++) {
                        int y = minY + i;
                        float dy = (y - zone.targetElevation()) / (zone.thickness() * 0.5f);
                        float verticalEnvelope = 1.0f - (dy * dy);
                        float archDen = islandMask + verticalEnvelope - 0.65f;
                        column[i] = column[i] * (1.0f - w) + archDen * w;
                    }
                }
                case BIOME_OVERRIDE -> {
                    // If targetElevation is below sea level (e.g. ocean moat ring), gently depress column
                    if (zone.targetElevation() < 55.0f) {
                        for (int i = 0; i < height; i++) {
                            int y = minY + i;
                            float seaBasin = (zone.targetElevation() - y) / 22.0f;
                            column[i] = column[i] * (1.0f - w) + seaBasin * w;
                        }
                    }
                }
            }
        }

        // 2. Evaluate Finite Bounds & OutsideBoundsFallback (when boundsRadius > 0)
        if (boundsRadius > 0.0f && outsideFallback != OutsideBoundsFallback.DEFAULT_BIOMES) {
            float rawDist = (float) Math.sqrt(x * x + z * z) + coastWarp * 0.5f;
            if (rawDist > boundsRadius - blendFalloff) {
                float t = Math.clamp((rawDist - (boundsRadius - blendFalloff)) / (2.0f * blendFalloff), 0.0f, 1.0f);
                float outsideWeight = smoothstep(t);

                if (outsideFallback == OutsideBoundsFallback.VOID) {
                    for (int i = 0; i < height; i++) {
                        column[i] = column[i] * (1.0f - outsideWeight) + (-8.0f) * outsideWeight;
                    }
                } else if (outsideFallback == OutsideBoundsFallback.OCEAN) {
                    float oceanFloorY = Math.max(minY + 12, seaLevelOverride - 20);
                    for (int i = 0; i < height; i++) {
                        int y = minY + i;
                        float oceanDen = (oceanFloorY - y) / 20.0f;
                        column[i] = column[i] * (1.0f - outsideWeight) + oceanDen * outsideWeight;
                    }
                } else if (outsideFallback == OutsideBoundsFallback.VANILLA_DEFAULT) {
                    float stdY = 68.0f + islandSurfaceNoise.getNoise(x, z) * 14.0f;
                    for (int i = 0; i < height; i++) {
                        int y = minY + i;
                        float vanillaDen = (stdY - y) / 24.0f;
                        column[i] = column[i] * (1.0f - outsideWeight) + vanillaDen * outsideWeight;
                    }
                }
            }
        }
    }

    private static float smoothstep(float t) {
        float c = Math.clamp(t, 0.0f, 1.0f);
        return c * c * (3.0f - 2.0f * c);
    }
}
