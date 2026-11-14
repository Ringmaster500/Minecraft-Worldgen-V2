package com.worldgenv2.props;

import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.scanner.OriginScanner;
import com.worldgenv2.scanner.Scanner;
import com.worldgenv2.vector.Vec3d;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * High-level composable prop generator connecting:
 * {@link PositionsProvider} -&gt; {@link Scanner} -&gt; {@link Assignments} -&gt; child {@link Prop}.
 */
public final class AssignmentsProp<T> implements Distribution<T> {

    private final PositionsProvider positionsProvider;
    private final Scanner scanner;
    private final Assignments<T> assignments;
    private final Predicate<T> solidPredicate;

    public AssignmentsProp(
        PositionsProvider positionsProvider,
        Scanner scanner,
        Assignments<T> assignments,
        Predicate<T> solidPredicate
    ) {
        this.positionsProvider = Objects.requireNonNull(positionsProvider, "positionsProvider");
        this.scanner = scanner != null ? scanner : OriginScanner.INSTANCE;
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.solidPredicate = solidPredicate != null ? solidPredicate : Objects::nonNull;
    }

    @Override
    public boolean generate(Context<T> ctx) {
        // Query positions within a 16x16 column or local 32-block bounding box around context anchor
        double minX = ctx.worldX() - 8.0;
        double maxX = ctx.worldX() + 8.0;
        double minY = Math.max(ctx.voxelSpace().getMinY(), ctx.worldY() - 16.0);
        double maxY = Math.min(ctx.voxelSpace().getMaxY(), ctx.worldY() + 16.0);
        double minZ = ctx.worldZ() - 8.0;
        double maxZ = ctx.worldZ() + 8.0;

        List<Vec3d> points = positionsProvider.getPositions(minX, minY, minZ, maxX, maxY, maxZ);
        boolean anyPlaced = false;

        for (Vec3d p : points) {
            List<Vec3d> scanned = scanner.scan(p, ctx.voxelSpace(), solidPredicate);
            for (Vec3d anchor : scanned) {
                int ax = (int) Math.floor(anchor.x());
                int ay = (int) Math.floor(anchor.y());
                int az = (int) Math.floor(anchor.z());

                Prop<T> chosenProp = assignments.assign(anchor, ctx.seed());
                if (chosenProp != null) {
                    Context<T> childCtx = new Context<>(
                        ax, ay, az,
                        ctx.normalX(), ctx.normalY(), ctx.normalZ(),
                        ctx.voxelSpace(),
                        ctx.seed() ^ (ax * 31L + az)
                    );
                    if (chosenProp.generate(childCtx)) {
                        anyPlaced = true;
                    }
                }
            }
        }
        return anyPlaced;
    }

    /**
     * Hytale-style distribution for one chunk: ask the positions provider for points over the chunk's footprint once,
     * snap 2D points (which providers emit at Y = 0) to the real surface, then scanner -> assignments -> child prop.
     * Anchors are processed only when they lie inside the chunk and {@code accept} says this prop's biome owns the
     * column, so the result does not depend on how often or in what order chunks are generated.
     * Parts of a prop that would fall outside the chunk are clipped by the voxel space.
     */
    public boolean generateInChunk(int originX, int originZ, com.worldgenv2.api.VoxelSpace<T> space, long chunkSeed,
                                   java.util.function.BiPredicate<Integer, Integer> accept, Predicate<T> solid) {
        int minWorldY = space.getMinY(), maxWorldY = space.getMaxY();
        List<Vec3d> points = positionsProvider.getPositions(
            originX, Math.min(0, minWorldY), originZ, originX + 16, maxWorldY, originZ + 16);

        // 2D providers return one point per (x, z) at Y = 0; 3D providers return many per column.
        java.util.Map<Long, Integer> perColumn = new java.util.HashMap<>();
        for (Vec3d p : points) {
            perColumn.merge(columnKey((int) Math.floor(p.x()), (int) Math.floor(p.z())), 1, Integer::sum);
        }

        boolean anyPlaced = false;
        for (Vec3d p : points) {
            int px = (int) Math.floor(p.x()), pz = (int) Math.floor(p.z());
            if ((px >> 4) != (originX >> 4) || (pz >> 4) != (originZ >> 4)) continue;
            if (accept != null && !accept.test(px, pz)) continue;

            Vec3d origin = p;
            if (Math.abs(p.y()) < 1e-6 && perColumn.getOrDefault(columnKey(px, pz), 1) == 1) {
                int surf = surfaceY(space, px, pz, minWorldY, maxWorldY);
                if (surf == Integer.MIN_VALUE) continue;
                origin = new Vec3d(p.x(), surf, p.z());
            }

            for (Vec3d anchor : scanner.scan(origin, space, solid)) {
                int ax = (int) Math.floor(anchor.x());
                int ay = (int) Math.floor(anchor.y());
                int az = (int) Math.floor(anchor.z());
                if ((ax >> 4) != (originX >> 4) || (az >> 4) != (originZ >> 4)) continue;
                long seed = chunkSeed ^ (ax * 0x9E3779B97F4A7C15L) ^ (az * 0xC2B2AE3D27D4EB4FL) ^ ay;
                Prop<T> chosen = assignments.assign(anchor, seed);
                if (chosen != null && chosen.generate(new Context<>(ax, ay, az, 0.0f, 1.0f, 0.0f, space, seed))) {
                    anyPlaced = true;
                }
            }
        }
        return anyPlaced;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    /** Highest block the predicate calls solid (with air above) in a column, or Integer.MIN_VALUE when there is none. */
    private int surfaceY(com.worldgenv2.api.VoxelSpace<T> space, int x, int z, int minY, int maxY, Predicate<T> solid) {
        for (int y = maxY - 2; y >= minY + 1; y--) {
            if (space.isAir(x, y + 1, z) && solid.test(space.getBlock(x, y, z))) return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Highest solid block (with air above) in a column, or Integer.MIN_VALUE when the column is empty. */
    private int surfaceY(com.worldgenv2.api.VoxelSpace<T> space, int x, int z, int minY, int maxY) {
        for (int y = maxY - 2; y >= minY + 1; y--) {
            if (!space.isAir(x, y, z) && space.isAir(x, y + 1, z)) return y;
        }
        return Integer.MIN_VALUE;
    }

    public PositionsProvider getPositionsProvider() {
        return positionsProvider;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public Assignments<T> getAssignments() {
        return assignments;
    }

    @Override
    public int horizontalReach() {
        return scanner.horizontalReach() + assignments.horizontalReach();
    }

    /**
     * Hytale-style distribution over a whole region: every position the provider yields inside
     * [minX, maxX) x [minZ, maxZ) is scanned and assigned, in a fixed global order (by x, then z, then y).
     *
     * <p>The seed for each anchor depends only on {@code seedBase} (world seed and a stable id for this prop) and the anchor's
     * own coordinates, never on the chunk doing the work. Two chunks that both evaluate an anchor therefore make the same
     * decision, which is what lets a prop that overlaps a chunk border be built identically from both sides.
     *
     * @param accept says whether this prop owns the column (its biome); null accepts every column
     */
    @Override
    public int generateInRegion(int minX, int minZ, int maxX, int maxZ, com.worldgenv2.api.VoxelSpace<T> space, long seedBase,
                                java.util.function.BiPredicate<Integer, Integer> accept, Predicate<T> solid) {
        int minWorldY = space.getMinY(), maxWorldY = space.getMaxY();
        List<Vec3d> points = new java.util.ArrayList<>(positionsProvider.getPositions(
            minX, Math.min(0, minWorldY), minZ, maxX, maxWorldY, maxZ));
        points.sort((a, b) -> {
            int c = Double.compare(a.x(), b.x());
            if (c != 0) return c;
            c = Double.compare(a.z(), b.z());
            return c != 0 ? c : Double.compare(a.y(), b.y());
        });

        java.util.Map<Long, Integer> perColumn = new java.util.HashMap<>();
        for (Vec3d p : points) {
            perColumn.merge(columnKey((int) Math.floor(p.x()), (int) Math.floor(p.z())), 1, Integer::sum);
        }

        int placed = 0;
        for (Vec3d p : points) {
            int px = (int) Math.floor(p.x()), pz = (int) Math.floor(p.z());
            if (px < minX || px >= maxX || pz < minZ || pz >= maxZ) continue;
            if (accept != null && !accept.test(px, pz)) continue;

            Vec3d origin = p;
            if (Math.abs(p.y()) < 1e-6 && perColumn.getOrDefault(columnKey(px, pz), 1) == 1) {
                int surf = surfaceY(space, px, pz, minWorldY, maxWorldY, solid);
                if (surf == Integer.MIN_VALUE) continue;
                origin = new Vec3d(p.x(), surf, p.z());
            }

            for (Vec3d anchor : scanner.scan(origin, space, solid)) {
                int ax = (int) Math.floor(anchor.x()), ay = (int) Math.floor(anchor.y()), az = (int) Math.floor(anchor.z());
                long seed = seedBase ^ (ax * 0x9E3779B97F4A7C15L) ^ (az * 0xC2B2AE3D27D4EB4FL) ^ (ay * 0x165667B19E3779F9L);
                seed ^= seed >>> 29;
                Prop<T> chosen = assignments.assign(anchor, seed);
                if (chosen != null && chosen.generate(new Context<>(ax, ay, az, 0.0f, 1.0f, 0.0f, space, seed))) placed++;
            }
        }
        return placed;
    }

    /** Reach of what this prop builds, without the scanner: how far its content extends from an anchor. */
    public int contentReach() {
        return assignments.horizontalReach();
    }
}
