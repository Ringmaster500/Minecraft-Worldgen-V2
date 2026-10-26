package com.worldgenv2.worldstructure;

import java.util.*;

/**
 * Topological Macro Feature Graph & Relational Landmark Engine (Doc 10 §3).
 * <p>
 * Evaluates high-level world landmarks as an interconnected dependency network before chunk
 * sculpting begins:
 * <ul>
 *   <li><b>Primary Landmarks:</b> Placed within target distance bands and biomes, exporting
 *       directional {@link MacroAnchor} sockets (e.g., {@code ROAD_OUTLET_NORTH}, {@code DEFENSIVE_PERIMETER}).</li>
 *   <li><b>Relational Landmarks ({@code A => B}):</b> Subordinate landmarks (such as watchtowers or
 *       expedition camps) that trigger only when their parent landmark exists, claiming an available
 *       macro anchor within {@code [minDistance, maxDistance]}, orienting toward the parent landmark,
 *       and registering a topological {@link RoadEdge} for {@link SplineRoadEngine}.</li>
 * </ul>
 */
public final class MacroFeatureGraph {

    /**
     * Declarative macro socket exported by a landmark.
     */
    public record MacroAnchorDef(
        String name,
        String type,
        float dirX,
        float dirZ
    ) {
        public MacroAnchorDef {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (len > 1e-5f) {
                dirX /= len;
                dirZ /= len;
            } else {
                dirX = 0.0f;
                dirZ = -1.0f;
            }
        }
    }

    /**
     * Instantiated macro anchor socket on a placed landmark.
     */
    public static final class MacroAnchor {
        private final String name;
        private final String type;
        private final String ownerLandmarkId;
        private final float dirX;
        private final float dirZ;
        private String claimedByLandmarkId;

        public MacroAnchor(String name, String type, String ownerLandmarkId, float dirX, float dirZ) {
            this.name = name;
            this.type = type;
            this.ownerLandmarkId = ownerLandmarkId;
            this.dirX = dirX;
            this.dirZ = dirZ;
        }

        public String name() { return name; }
        public String type() { return type; }
        public String ownerLandmarkId() { return ownerLandmarkId; }
        public float dirX() { return dirX; }
        public float dirZ() { return dirZ; }
        public boolean isClaimed() { return claimedByLandmarkId != null; }
        public String claimedByLandmarkId() { return claimedByLandmarkId; }

        public void claim(String childLandmarkId) {
            this.claimedByLandmarkId = Objects.requireNonNull(childLandmarkId, "childLandmarkId");
        }
    }

    /**
     * Placement rule for a root/primary landmark (e.g., Sun Temple, Coastal Citadel, Ancient Ruin).
     */
    public record PrimaryLandmarkRule(
        String id,
        String prefabId,
        Set<String> allowedBiomes,
        float minRadiusFromOrigin,
        float maxRadiusFromOrigin,
        List<MacroAnchorDef> exportedAnchors
    ) {}

    /**
     * Relational placement rule ({@code parentLandmarkId => id}) with distance, biome, anchor,
     * orientation, and road connectivity constraints.
     */
    public record RelationalLandmarkRule(
        String id,
        String parentLandmarkId,
        String requiredAnchorType,
        String prefabId,
        Set<String> allowedBiomes,
        float minDistance,
        float maxDistance,
        boolean faceParent,
        boolean connectWithRoad,
        float roadWidth,
        float shoulderWidth
    ) {
        public RelationalLandmarkRule(
            String id,
            String parentLandmarkId,
            String requiredAnchorType,
            String prefabId,
            Set<String> allowedBiomes,
            float minDistance,
            float maxDistance,
            boolean faceParent,
            boolean connectWithRoad
        ) {
            this(
                id,
                parentLandmarkId,
                requiredAnchorType,
                prefabId,
                allowedBiomes,
                minDistance,
                maxDistance,
                faceParent,
                connectWithRoad,
                5.0f,
                3.0f
            );
        }
    }

    /**
     * Concrete landmark instance resolved into world coordinates.
     */
    public record PlacedLandmark(
        String id,
        String prefabId,
        String parentLandmarkId,
        int x,
        int y,
        int z,
        float facingYawDegrees,
        String biomeName,
        List<MacroAnchor> anchors
    ) {}

    /**
     * Directed connectivity edge linking two placed landmarks for road spline generation.
     */
    public record RoadEdge(
        String fromLandmarkId,
        String toLandmarkId,
        PlacedLandmark from,
        PlacedLandmark to,
        float roadWidth,
        float shoulderWidth
    ) {}

    private final List<PrimaryLandmarkRule> primaryRules = new ArrayList<>();
    private final List<RelationalLandmarkRule> relationalRules = new ArrayList<>();
    private final Map<String, PlacedLandmark> placedLandmarks = new LinkedHashMap<>();
    private final List<RoadEdge> roadEdges = new ArrayList<>();

    public MacroFeatureGraph addPrimaryLandmark(PrimaryLandmarkRule rule) {
        primaryRules.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    public MacroFeatureGraph addRelationalLandmark(RelationalLandmarkRule rule) {
        relationalRules.add(Objects.requireNonNull(rule, "rule"));
        return this;
    }

    /**
     * Evaluates all primary and relational landmark rules against the given {@link WorldStructure},
     * populating concrete landmark coordinates, claiming macro anchors, and generating road edges.
     */
    public void buildGraph(WorldStructure worldStructure, int minY, int maxY, int seaLevel, long seed) {
        placedLandmarks.clear();
        roadEdges.clear();

        int height = Math.max(1, maxY - minY);
        float[] colBuffer = new float[height];
        float[] scratch = new float[height];

        // 1. Place Primary Landmarks
        for (int i = 0; i < primaryRules.size(); i++) {
            PrimaryLandmarkRule rule = primaryRules.get(i);
            long ruleSeed = mixSeed(seed, rule.id().hashCode() + i * 31L);
            PlacedLandmark placed = placePrimary(rule, worldStructure, minY, maxY, seaLevel, ruleSeed, colBuffer, scratch);
            if (placed != null) {
                placedLandmarks.put(placed.id(), placed);
            }
        }

        // 2. Place Relational Landmarks (Landmark A => Landmark B)
        for (int i = 0; i < relationalRules.size(); i++) {
            RelationalLandmarkRule rule = relationalRules.get(i);
            PlacedLandmark parent = placedLandmarks.get(rule.parentLandmarkId());
            if (parent == null) {
                continue; // Precondition not met: parent landmark did not spawn
            }

            long ruleSeed = mixSeed(seed, rule.id().hashCode() + (i + 100) * 131L);
            PlacedLandmark child = placeRelational(
                rule, parent, worldStructure, minY, maxY, seaLevel, ruleSeed, colBuffer, scratch
            );
            if (child != null) {
                placedLandmarks.put(child.id(), child);
                if (rule.connectWithRoad()) {
                    roadEdges.add(new RoadEdge(
                        parent.id(),
                        child.id(),
                        parent,
                        child,
                        rule.roadWidth(),
                        rule.shoulderWidth()
                    ));
                }
            }
        }
    }

    public Map<String, PlacedLandmark> getPlacedLandmarks() {
        return Collections.unmodifiableMap(placedLandmarks);
    }

    public PlacedLandmark getLandmark(String id) {
        return placedLandmarks.get(id);
    }

    public List<RoadEdge> getRoadEdges() {
        return Collections.unmodifiableList(roadEdges);
    }

    private PlacedLandmark placePrimary(
        PrimaryLandmarkRule rule,
        WorldStructure worldStructure,
        int minY,
        int maxY,
        int seaLevel,
        long ruleSeed,
        float[] colBuffer,
        float[] scratch
    ) {
        float minR = Math.max(0.0f, rule.minRadiusFromOrigin());
        float maxR = Math.max(minR + 16.0f, rule.maxRadiusFromOrigin());

        PlacedLandmark fallbackCandidate = null;
        int attempts = 48;

        for (int attempt = 0; attempt < attempts; attempt++) {
            long h = mixSeed(ruleSeed, attempt);
            float u = ((h & 0xFFFF) / 65535.0f);
            float v = (((h >>> 16) & 0xFFFF) / 65535.0f);

            float radius = minR + u * (maxR - minR);
            double angle = v * 2.0 * Math.PI;
            int wx = (Math.round((float) (Math.cos(angle) * radius)) & ~15) + 8;
            int wz = (Math.round((float) (Math.sin(angle) * radius)) & ~15) + 8;

            WorldStructure.BiomeQueryResult query = worldStructure.queryBiome(wx, wz);
            String biomeName = query.primaryTileBiome().name();
            int surfaceY = sampleSurfaceElevation(query, wx, wz, minY, maxY, colBuffer, scratch);

            if (surfaceY > seaLevel) {
                List<MacroAnchor> anchors = instantiateAnchors(rule.id(), rule.exportedAnchors());
                float yaw = (((h >>> 32) & 3) * 90.0f);
                PlacedLandmark candidate = new PlacedLandmark(
                    rule.id(),
                    rule.prefabId(),
                    null,
                    wx,
                    surfaceY + 1,
                    wz,
                    yaw,
                    biomeName,
                    anchors
                );
                if (matchesBiome(biomeName, rule.allowedBiomes())) {
                    return candidate;
                }
                if (fallbackCandidate == null) {
                    fallbackCandidate = candidate;
                }
            }
        }

        if (fallbackCandidate != null) {
            return fallbackCandidate;
        }

        // Guaranteed deterministic fallback at mid-radius along seed angle
        float midR = (minR + maxR) * 0.5f;
        double angle = ((ruleSeed & 0xFFFF) / 65535.0) * 2.0 * Math.PI;
        int wx = (Math.round((float) (Math.cos(angle) * midR)) & ~15) + 8;
        int wz = (Math.round((float) (Math.sin(angle) * midR)) & ~15) + 8;
        WorldStructure.BiomeQueryResult query = worldStructure.queryBiome(wx, wz);
        int surfaceY = Math.max(seaLevel + 1, sampleSurfaceElevation(query, wx, wz, minY, maxY, colBuffer, scratch));
        return new PlacedLandmark(
            rule.id(),
            rule.prefabId(),
            null,
            wx,
            surfaceY + 1,
            wz,
            0.0f,
            query.primaryTileBiome().name(),
            instantiateAnchors(rule.id(), rule.exportedAnchors())
        );
    }

    private PlacedLandmark placeRelational(
        RelationalLandmarkRule rule,
        PlacedLandmark parent,
        WorldStructure worldStructure,
        int minY,
        int maxY,
        int seaLevel,
        long ruleSeed,
        float[] colBuffer,
        float[] scratch
    ) {
        // Locate an unclaimed MacroAnchor on parent matching requiredAnchorType (if requested)
        MacroAnchor targetAnchor = null;
        if (rule.requiredAnchorType() != null && !rule.requiredAnchorType().isEmpty()) {
            for (MacroAnchor anchor : parent.anchors()) {
                if (!anchor.isClaimed() && anchor.type().equalsIgnoreCase(rule.requiredAnchorType())) {
                    targetAnchor = anchor;
                    break;
                }
            }
            if (targetAnchor == null) {
                return null; // No available macro socket of the required type on parent landmark
            }
        }

        float minD = Math.max(16.0f, rule.minDistance());
        float maxD = Math.max(minD + 16.0f, rule.maxDistance());
        double baseAngle = targetAnchor != null
            ? Math.atan2(targetAnchor.dirZ(), targetAnchor.dirX())
            : (((ruleSeed & 0xFFFF) / 65535.0) * 2.0 * Math.PI);

        PlacedLandmark fallback = null;
        int attempts = 48;

        for (int attempt = 0; attempt < attempts; attempt++) {
            long h = mixSeed(ruleSeed, attempt);
            float u = (h & 0xFFFF) / 65535.0f;
            float v = (((h >>> 16) & 0xFFFF) / 65535.0f) - 0.5f;

            float dist = minD + u * (maxD - minD);
            // If guided by a directional MacroAnchor, search within a +/- 45 degree cone of the anchor's vector
            double angleSpread = targetAnchor != null ? (Math.PI * 0.5) : (2.0 * Math.PI);
            double angle = baseAngle + v * angleSpread;

            int wx = ((parent.x() + Math.round((float) (Math.cos(angle) * dist))) & ~15) + 8;
            int wz = ((parent.z() + Math.round((float) (Math.sin(angle) * dist))) & ~15) + 8;

            WorldStructure.BiomeQueryResult query = worldStructure.queryBiome(wx, wz);
            String biomeName = query.primaryTileBiome().name();
            int surfaceY = sampleSurfaceElevation(query, wx, wz, minY, maxY, colBuffer, scratch);

            if (surfaceY > seaLevel) {
                float yaw = rule.faceParent()
                    ? computeYawToward(wx, wz, parent.x(), parent.z())
                    : (((h >>> 32) & 3) * 90.0f);

                PlacedLandmark candidate = new PlacedLandmark(
                    rule.id(),
                    rule.prefabId(),
                    parent.id(),
                    wx,
                    surfaceY + 1,
                    wz,
                    yaw,
                    biomeName,
                    List.of()
                );

                if (matchesBiome(biomeName, rule.allowedBiomes())) {
                    if (targetAnchor != null) {
                        targetAnchor.claim(rule.id());
                    }
                    return candidate;
                }
                if (fallback == null) {
                    fallback = candidate;
                }
            }
        }

        if (fallback != null) {
            if (targetAnchor != null) {
                targetAnchor.claim(rule.id());
            }
            return fallback;
        }

        float midDist = (minD + maxD) * 0.5f;
        int wx = ((parent.x() + Math.round((float) (Math.cos(baseAngle) * midDist))) & ~15) + 8;
        int wz = ((parent.z() + Math.round((float) (Math.sin(baseAngle) * midDist))) & ~15) + 8;
        WorldStructure.BiomeQueryResult query = worldStructure.queryBiome(wx, wz);
        int surfaceY = Math.max(seaLevel + 1, sampleSurfaceElevation(query, wx, wz, minY, maxY, colBuffer, scratch));
        if (targetAnchor != null) {
            targetAnchor.claim(rule.id());
        }
        float yaw = rule.faceParent() ? computeYawToward(wx, wz, parent.x(), parent.z()) : 0.0f;
        return new PlacedLandmark(
            rule.id(),
            rule.prefabId(),
            parent.id(),
            wx,
            surfaceY + 1,
            wz,
            yaw,
            query.primaryTileBiome().name(),
            List.of()
        );
    }

    private static List<MacroAnchor> instantiateAnchors(String ownerId, List<MacroAnchorDef> defs) {
        if (defs == null || defs.isEmpty()) {
            return List.of();
        }
        List<MacroAnchor> list = new ArrayList<>(defs.size());
        for (MacroAnchorDef def : defs) {
            list.add(new MacroAnchor(def.name(), def.type(), ownerId, def.dirX(), def.dirZ()));
        }
        return list;
    }

    private static float computeYawToward(int fromX, int fromZ, int toX, int toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double deg = Math.toDegrees(Math.atan2(-dx, dz));
        return (float) ((deg % 360.0 + 360.0) % 360.0);
    }

    private static boolean matchesBiome(String biomeName, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (allowed.contains(biomeName)) {
            return true;
        }
        if (biomeName.contains(":")) {
            return allowed.contains(biomeName.substring(biomeName.indexOf(':') + 1));
        }
        return false;
    }

    static int sampleSurfaceElevation(
        WorldStructure.BiomeQueryResult query,
        float x,
        float z,
        int minY,
        int maxY,
        float[] colBuffer,
        float[] scratch
    ) {
        query.computeBlendedColumn(x, z, minY, maxY, colBuffer, scratch);
        for (int y = maxY - 1; y >= minY; y--) {
            if (colBuffer[y - minY] > 0.0f) {
                return y;
            }
        }
        return minY;
    }

    private static long mixSeed(long a, long b) {
        long h = a ^ (b * 0x9E3779B97F4A7C15L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
