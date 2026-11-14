package com.worldgenv2.hytale;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.assignments.Assignments;
import com.worldgenv2.positions.PositionsProvider;
import com.worldgenv2.props.Distribution;
import com.worldgenv2.props.Prop;
import com.worldgenv2.vector.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Hytale's Prop Distribution: a Positions field paired with Assignments. Each position gets the prop the assignments choose,
 * and the prop is generated <em>at that position, as given</em>. Nothing here looks for the ground: a 2D grid yields positions
 * at its own height and it is the prop (a Locator, an Orienter) that scans and validates with a Pattern.
 */
public final class HytaleDistribution<T> implements Distribution<T> {

    private final PositionsProvider positions;
    private final Assignments<T> assignments;

    public HytaleDistribution(PositionsProvider positions, Assignments<T> assignments) {
        this.positions = positions;
        this.assignments = assignments;
    }

    @Override
    public boolean generate(Context<T> ctx) {
        // Used as a plain prop (outside a runtime): distribute around the anchor's own 16-block neighbourhood.
        int x = ctx.worldX() - 8, z = ctx.worldZ() - 8;
        return generateInRegion(x, z, x + 16, z + 16, ctx.voxelSpace(), ctx.seed(), null, b -> b != null) > 0;
    }

    @Override
    public int generateInRegion(int minX, int minZ, int maxX, int maxZ, VoxelSpace<T> space, long seedBase,
                                BiPredicate<Integer, Integer> accept, Predicate<T> solid) {
        List<Vec3d> points = new ArrayList<>(positions.getPositions(minX, space.getMinY(), minZ, maxX, space.getMaxY(), maxZ));
        points.sort((a, b) -> {
            int c = Double.compare(a.x(), b.x());
            if (c != 0) return c;
            c = Double.compare(a.z(), b.z());
            return c != 0 ? c : Double.compare(a.y(), b.y());
        });
        int placed = 0;
        for (Vec3d p : points) {
            int px = (int) Math.floor(p.x()), py = (int) Math.floor(p.y()), pz = (int) Math.floor(p.z());
            if (px < minX || px >= maxX || pz < minZ || pz >= maxZ) continue;
            if (accept != null && !accept.test(px, pz)) continue;
            long seed = seedBase ^ (px * 0x9E3779B97F4A7C15L) ^ (pz * 0xC2B2AE3D27D4EB4FL) ^ (py * 0x165667B19E3779F9L);
            seed ^= seed >>> 29;
            Prop<T> chosen = assignments.assign(p, seed);
            if (chosen != null && chosen.generate(new Context<>(px, py, pz, 0.0f, 1.0f, 0.0f, space, seed))) placed++;
        }
        return placed;
    }

    /** Everything a position's prop can reach: what it scans and tests (Pattern, Scanner) and what it builds. */
    @Override
    public int horizontalReach() { return assignments.horizontalReach(); }
}
