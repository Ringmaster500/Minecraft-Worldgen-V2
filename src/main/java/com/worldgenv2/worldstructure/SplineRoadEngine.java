package com.worldgenv2.worldstructure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Continuous 3D Cubic Spline Road, Bridge & Tunnel Engine (Doc 10 §4).
 * <p>
 * Connects topological {@link MacroFeatureGraph.RoadEdge} pairs using 3D Catmull-Rom splines
 * {@code C(t) = (x(t), y(t), z(t))}. During chunk generation:
 * <ul>
 *   <li><b>Surface Grade Flattening:</b> Interpolates terrain column density toward the road spine
 *       elevation {@code y(t*)} across the road ribbon and shoulder transition zone.</li>
 *   <li><b>Bridges over Ravines/Water:</b> When natural terrain beneath {@code y(t*)} drops away
 *       ({@code D < 0}), stamps a suspended bridge deck with periodic vertical support piers.</li>
 *   <li><b>Tunnels through Mountains:</b> When natural terrain rises high above {@code y(t*)}
 *       ({@code D >> 0}), carves an arched tunnel corridor through the mountain mass.</li>
 * </ul>
 */
public final class SplineRoadEngine {

    public enum RoadSegmentMode {
        NONE,
        SURFACE_ROAD,
        BRIDGE,
        TUNNEL
    }

    public record ControlPoint(float x, float y, float z) {}

    public record RoadQueryResult(
        boolean active,
        float distanceToCenter,
        float roadElevation,
        float arcLength,
        float halfWidth,
        float shoulderWidth,
        float gradeWeight,
        boolean isRoadDeck,
        boolean isShoulder
    ) {
        public static final RoadQueryResult NONE = new RoadQueryResult(
            false, Float.MAX_VALUE, 64.0f, 0.0f, 0.0f, 0.0f, 0.0f, false, false
        );
    }

    private record BakedSegment(
        float x0, float y0, float z0, float s0,
        float x1, float y1, float z1, float s1,
        float minX, float minZ, float maxX, float maxZ
    ) {}

    private static final class RoadSpline {
        final String id;
        final List<ControlPoint> controlPoints;
        final float halfWidth;
        final float shoulderWidth;
        final float totalRadius;
        final float minX, minZ, maxX, maxZ;
        final List<BakedSegment> segments;

        RoadSpline(
            String id,
            List<ControlPoint> controlPoints,
            float roadWidth,
            float shoulderWidth,
            int subdivisionsPerSpan
        ) {
            this.id = id;
            this.controlPoints = List.copyOf(controlPoints);
            this.halfWidth = Math.max(1.0f, roadWidth * 0.5f);
            this.shoulderWidth = Math.max(1.0f, shoulderWidth);
            this.totalRadius = this.halfWidth + this.shoulderWidth;
            this.segments = bakeCatmullRomSegments(controlPoints, Math.max(4, subdivisionsPerSpan), this.totalRadius);

            float bMinX = Float.POSITIVE_INFINITY;
            float bMinZ = Float.POSITIVE_INFINITY;
            float bMaxX = Float.NEGATIVE_INFINITY;
            float bMaxZ = Float.NEGATIVE_INFINITY;
            for (BakedSegment seg : this.segments) {
                bMinX = Math.min(bMinX, seg.minX);
                bMinZ = Math.min(bMinZ, seg.minZ);
                bMaxX = Math.max(bMaxX, seg.maxX);
                bMaxZ = Math.max(bMaxZ, seg.maxZ);
            }
            this.minX = bMinX;
            this.minZ = bMinZ;
            this.maxX = bMaxX;
            this.maxZ = bMaxZ;
        }
    }

    private final List<RoadSpline> roads = new ArrayList<>();

    /**
     * Builds continuous 3D Catmull-Rom road splines for all {@link MacroFeatureGraph.RoadEdge}
     * connections in the given {@link MacroFeatureGraph}.
     */
    public void buildFromMacroGraph(
        MacroFeatureGraph graph,
        WorldStructure worldStructure,
        int minY,
        int maxY,
        int seaLevel
    ) {
        Objects.requireNonNull(graph, "graph");
        int height = Math.max(1, maxY - minY);
        float[] colBuffer = new float[height];
        float[] scratch = new float[height];

        for (MacroFeatureGraph.RoadEdge edge : graph.getRoadEdges()) {
            List<ControlPoint> waypoints = buildContourWaypoints(
                edge.from(),
                edge.to(),
                worldStructure,
                minY,
                maxY,
                seaLevel,
                colBuffer,
                scratch
            );
            addRoadSpline(
                edge.fromLandmarkId() + "_to_" + edge.toLandmarkId(),
                waypoints,
                edge.roadWidth(),
                edge.shoulderWidth()
            );
        }
    }

    /**
     * Registers a custom 3D road spline directly from ordered control points.
     */
    public void addRoadSpline(
        String id,
        List<ControlPoint> controlPoints,
        float roadWidth,
        float shoulderWidth
    ) {
        if (controlPoints == null || controlPoints.size() < 2) {
            throw new IllegalArgumentException("Road spline requires at least 2 control points");
        }
        roads.add(new RoadSpline(id, controlPoints, roadWidth, shoulderWidth, 16));
    }

    /**
     * Builds 3D Catmull-Rom road splines for all edges in a {@link com.worldgenv2.graph.ProceduralGraph}.
     */
    public void buildFromProceduralGraph(
        com.worldgenv2.graph.ProceduralGraph graph,
        float roadWidth,
        float shoulderWidth
    ) {
        Objects.requireNonNull(graph, "graph");
        for (com.worldgenv2.graph.GraphEdge edge : graph.getEdges()) {
            com.worldgenv2.graph.GraphNode a = graph.getNode(edge.nodeA());
            com.worldgenv2.graph.GraphNode b = graph.getNode(edge.nodeB());
            if (a != null && b != null) {
                List<ControlPoint> pts = List.of(
                    new ControlPoint((float) a.getPosition().x(), (float) a.getPosition().y(), (float) a.getPosition().z()),
                    new ControlPoint((float) b.getPosition().x(), (float) b.getPosition().y(), (float) b.getPosition().z())
                );
                addRoadSpline("graph_edge_" + edge.nodeA() + "_" + edge.nodeB(), pts, roadWidth, shoulderWidth);
            }
        }
    }

    public int getRoadCount() {
        return roads.size();
    }

    public List<ControlPoint> getControlPoints(int roadIndex) {
        return Collections.unmodifiableList(roads.get(roadIndex).controlPoints);
    }

    /**
     * Evaluates the nearest road spline at world coordinate {@code (x, z)}.
     */
    public RoadQueryResult queryRoad(float x, float z) {
        if (roads.isEmpty()) {
            return RoadQueryResult.NONE;
        }

        RoadSpline bestSpline = null;
        float bestDistSq = Float.MAX_VALUE;
        float bestY = 64.0f;
        float bestArc = 0.0f;

        for (int r = 0, rSize = roads.size(); r < rSize; r++) {
            RoadSpline road = roads.get(r);
            if (x < road.minX || x > road.maxX || z < road.minZ || z > road.maxZ) {
                continue;
            }
            float maxDistSq = road.totalRadius * road.totalRadius;
            List<BakedSegment> segs = road.segments;
            for (int i = 0, sSize = segs.size(); i < sSize; i++) {
                BakedSegment seg = segs.get(i);
                if (x < seg.minX || x > seg.maxX || z < seg.minZ || z > seg.maxZ) {
                    continue;
                }

                float dx = seg.x1 - seg.x0;
                float dz = seg.z1 - seg.z0;
                float lenSq = dx * dx + dz * dz;
                float t = 0.0f;
                if (lenSq > 1e-6f) {
                    t = Math.clamp(((x - seg.x0) * dx + (z - seg.z0) * dz) / lenSq, 0.0f, 1.0f);
                }

                float projX = seg.x0 + t * dx;
                float projZ = seg.z0 + t * dz;
                float distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);

                if (distSq <= maxDistSq && distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestSpline = road;
                    bestY = seg.y0 + t * (seg.y1 - seg.y0);
                    bestArc = seg.s0 + t * (seg.s1 - seg.s0);
                }
            }
        }

        if (bestSpline == null) {
            return RoadQueryResult.NONE;
        }

        float dist = (float) Math.sqrt(bestDistSq);
        float halfW = bestSpline.halfWidth;
        float shoulderW = bestSpline.shoulderWidth;

        boolean isDeck = dist <= halfW;
        boolean isShoulder = !isDeck && dist <= (halfW + shoulderW);

        float gradeWeight;
        if (isDeck) {
            gradeWeight = 1.0f;
        } else {
            float u = Math.clamp(1.0f - ((dist - halfW) / shoulderW), 0.0f, 1.0f);
            gradeWeight = u * u * (3.0f - 2.0f * u);
        }

        return new RoadQueryResult(
            true,
            dist,
            bestY,
            bestArc,
            halfW,
            shoulderW,
            gradeWeight,
            isDeck,
            isShoulder
        );
    }

    /**
     * Applies road grade flattening, suspended bridge decks/piers, and mountain tunnel carving
     * to a pre-computed vertical density column {@code [minY, maxY)}.
     */
    public RoadSegmentMode applyRoadToColumn(
        float x,
        float z,
        int minY,
        int maxY,
        float[] densityCol
    ) {
        RoadQueryResult q = queryRoad(x, z);
        if (!q.active()) {
            return RoadSegmentMode.NONE;
        }

        int height = maxY - minY;
        int roadBlockY = Math.round(q.roadElevation());
        int roadIdx = Math.clamp(roadBlockY - minY, 0, height - 1);
        int aboveIdx = Math.clamp(roadBlockY + 5 - minY, 0, height - 1);
        int belowIdx = Math.clamp(roadBlockY - 2 - minY, 0, height - 1);

        float naturalAbove = densityCol[aboveIdx];
        float naturalBelow = densityCol[belowIdx];

        // 1. Tunnel Mode: Natural terrain 5 blocks above road deck is solid mountain rock
        if (naturalAbove > 0.15f && q.distanceToCenter() <= q.halfWidth() + 1.0f) {
            // Ensure solid roadbed beneath the tunnel floor
            for (int y = Math.max(minY, roadBlockY - 2); y <= Math.min(maxY - 1, roadBlockY); y++) {
                densityCol[y - minY] = Math.max(densityCol[y - minY], 0.85f);
            }
            // Carve arched 4-block-high tunnel clearance above roadBlockY
            float normR = q.distanceToCenter() / Math.max(1.0f, q.halfWidth() + 1.0f);
            int archHeight = Math.max(3, Math.round(4.5f * (1.0f - normR * normR * 0.45f)));
            for (int dy = 1; dy <= archHeight; dy++) {
                int y = roadBlockY + dy;
                if (y >= minY && y < maxY) {
                    densityCol[y - minY] = -0.85f;
                }
            }
            return RoadSegmentMode.TUNNEL;
        }

        // 2. Bridge Mode: Natural terrain below road deck is open ravine/water
        if (naturalBelow < -0.05f && q.isRoadDeck()) {
            for (int y = Math.max(minY, roadBlockY - 1); y <= Math.min(maxY - 1, roadBlockY); y++) {
                densityCol[y - minY] = Math.max(densityCol[y - minY], 0.90f);
            }
            // Clear air above bridge deck
            for (int dy = 1; dy <= 4; dy++) {
                int y = roadBlockY + dy;
                if (y >= minY && y < maxY) {
                    densityCol[y - minY] = Math.min(densityCol[y - minY], -0.80f);
                }
            }
            // Periodic vertical support pier every 12 blocks along the spline spine
            float pierPhase = Math.abs((q.arcLength() % 12.0f) - 6.0f);
            if (pierPhase <= 1.1f && q.distanceToCenter() <= 1.25f) {
                for (int y = roadBlockY - 2; y >= minY; y--) {
                    if (densityCol[y - minY] > 0.25f) {
                        break;
                    }
                    densityCol[y - minY] = 0.90f;
                }
            }
            return RoadSegmentMode.BRIDGE;
        }

        // 3. Surface Road Mode: Smoothly flatten terrain grade toward y(t*)
        float w = q.gradeWeight();
        float invW = 1.0f - w;
        float targetSurface = q.roadElevation() + 0.25f;
        for (int i = 0; i < height; i++) {
            int y = minY + i;
            float roadGrad = (targetSurface - y) / 14.0f;
            densityCol[i] = densityCol[i] * invW + roadGrad * w;
        }
        return RoadSegmentMode.SURFACE_ROAD;
    }

    /**
     * Resolves surface block material override when a voxel lies on a road deck or shoulder
     * at the road's actual elevation (preventing mountain roofs above tunnels from being paved).
     */
    public <T> T resolveRoadMaterial(
        int x,
        int y,
        int z,
        int depth,
        T naturalMaterial,
        T roadDeckMaterial,
        T shoulderMaterial
    ) {
        if (depth > 1 || roads.isEmpty()) {
            return naturalMaterial;
        }
        RoadQueryResult q = queryRoad(x, z);
        if (!q.active()) {
            return naturalMaterial;
        }
        // Only pave surfaces near the road spine's vertical elevation y(t*), never the mountain top above a tunnel!
        int roadY = Math.round(q.roadElevation());
        if (Math.abs(y - roadY) > 3) {
            return naturalMaterial;
        }
        if (q.isRoadDeck()) {
            return roadDeckMaterial != null ? roadDeckMaterial : naturalMaterial;
        }
        if (q.isShoulder() && depth == 0 && shoulderMaterial != null) {
            // Dither shoulder edge organically into natural terrain
            int h = x * 374761393 + z * 668265263;
            h = (h ^ (h >> 13)) * 1274126177;
            float dither = ((h ^ (h >> 16)) & 0x7FFF) / 32768.0f;
            if (q.gradeWeight() > dither * 0.85f) {
                return shoulderMaterial;
            }
        }
        return naturalMaterial;
    }

    public <T> T resolveRoadMaterial(
        int x,
        int z,
        int depth,
        T naturalMaterial,
        T roadDeckMaterial,
        T shoulderMaterial
    ) {
        if (depth > 1 || roads.isEmpty()) {
            return naturalMaterial;
        }
        RoadQueryResult q = queryRoad(x, z);
        if (!q.active()) {
            return naturalMaterial;
        }
        return resolveRoadMaterial(
            x,
            Math.round(q.roadElevation()),
            z,
            depth,
            naturalMaterial,
            roadDeckMaterial,
            shoulderMaterial
        );
    }

    private static List<ControlPoint> buildContourWaypoints(
        MacroFeatureGraph.PlacedLandmark from,
        MacroFeatureGraph.PlacedLandmark to,
        WorldStructure worldStructure,
        int minY,
        int maxY,
        int seaLevel,
        float[] colBuffer,
        float[] scratch
    ) {
        float dx = to.x() - from.x();
        float dz = to.z() - from.z();
        float totalDist = (float) Math.sqrt(dx * dx + dz * dz);
        int numSpans = Math.clamp(Math.round(totalDist / 40.0f), 3, 16);

        float perpX = totalDist > 1e-4f ? (-dz / totalDist) : 0.0f;
        float perpZ = totalDist > 1e-4f ? (dx / totalDist) : 0.0f;

        List<ControlPoint> rawPoints = new ArrayList<>(numSpans + 1);
        rawPoints.add(new ControlPoint(from.x(), Math.max(seaLevel + 1, from.y() - 1), from.z()));

        for (int i = 1; i < numSpans; i++) {
            float t = (float) i / numSpans;
            float baseX = from.x() + dx * t;
            float baseZ = from.z() + dz * t;
            float prevY = rawPoints.getLast().y();

            // Local contour search perpendicular to the straight line to prefer gentler elevation grades
            float bestX = baseX;
            float bestZ = baseZ;
            float bestY = prevY;
            float bestCost = Float.MAX_VALUE;

            float[] offsets = {-16.0f, -8.0f, 0.0f, 8.0f, 16.0f};
            for (float lateral : offsets) {
                float cx = baseX + perpX * lateral;
                float cz = baseZ + perpZ * lateral;
                WorldStructure.BiomeQueryResult query = worldStructure.queryBiome(cx, cz);
                int surfY = MacroFeatureGraph.sampleSurfaceElevation(query, cx, cz, minY, maxY, colBuffer, scratch);
                float clampedY = Math.max(seaLevel + 2.0f, (float) surfY);
                float gradeCost = Math.abs(clampedY - prevY) * 2.0f + Math.abs(lateral) * 0.25f;
                if (surfY <= seaLevel) {
                    gradeCost += 25.0f; // Penalize open water crossings so bridges stay short
                }
                if (gradeCost < bestCost) {
                    bestCost = gradeCost;
                    bestX = cx;
                    bestZ = cz;
                    bestY = clampedY;
                }
            }
            rawPoints.add(new ControlPoint(bestX, bestY, bestZ));
        }

        rawPoints.add(new ControlPoint(to.x(), Math.max(seaLevel + 1, to.y() - 1), to.z()));

        // Smooth waypoint elevations with a 3-tap box filter so road grade transitions gently
        List<ControlPoint> smoothed = new ArrayList<>(rawPoints.size());
        smoothed.add(rawPoints.getFirst());
        for (int i = 1; i < rawPoints.size() - 1; i++) {
            ControlPoint prev = rawPoints.get(i - 1);
            ControlPoint curr = rawPoints.get(i);
            ControlPoint next = rawPoints.get(i + 1);
            float smoothY = (prev.y() + curr.y() * 2.0f + next.y()) * 0.25f;
            smoothed.add(new ControlPoint(curr.x(), smoothY, curr.z()));
        }
        smoothed.add(rawPoints.getLast());
        return smoothed;
    }

    private static List<BakedSegment> bakeCatmullRomSegments(
        List<ControlPoint> pts,
        int subdivisionsPerSpan,
        float margin
    ) {
        int n = pts.size();
        List<BakedSegment> segments = new ArrayList<>((n - 1) * subdivisionsPerSpan);
        float cumulativeArc = 0.0f;

        for (int i = 0; i < n - 1; i++) {
            ControlPoint p0 = pts.get(Math.max(0, i - 1));
            ControlPoint p1 = pts.get(i);
            ControlPoint p2 = pts.get(i + 1);
            ControlPoint p3 = pts.get(Math.min(n - 1, i + 2));

            float prevX = p1.x();
            float prevY = p1.y();
            float prevZ = p1.z();

            for (int step = 1; step <= subdivisionsPerSpan; step++) {
                float t = (float) step / subdivisionsPerSpan;
                float nextX = catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), t);
                float nextY = catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), t);
                float nextZ = catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), t);

                float ds = (float) Math.sqrt(
                    (nextX - prevX) * (nextX - prevX) + (nextZ - prevZ) * (nextZ - prevZ)
                );
                float nextArc = cumulativeArc + ds;

                segments.add(new BakedSegment(
                    prevX, prevY, prevZ, cumulativeArc,
                    nextX, nextY, nextZ, nextArc,
                    Math.min(prevX, nextX) - margin,
                    Math.min(prevZ, nextZ) - margin,
                    Math.max(prevX, nextX) + margin,
                    Math.max(prevZ, nextZ) + margin
                ));

                prevX = nextX;
                prevY = nextY;
                prevZ = nextZ;
                cumulativeArc = nextArc;
            }
        }
        return segments;
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
            (2.0f * p1)
                + (-p0 + p2) * t
                + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
                + (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3
        );
    }
}
