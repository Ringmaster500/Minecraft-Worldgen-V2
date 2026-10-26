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
public final class AssignmentsProp<T> implements Prop<T> {

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

    public PositionsProvider getPositionsProvider() {
        return positionsProvider;
    }

    public Scanner getScanner() {
        return scanner;
    }

    public Assignments<T> getAssignments() {
        return assignments;
    }
}
