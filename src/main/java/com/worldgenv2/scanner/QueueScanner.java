package com.worldgenv2.scanner;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.vector.Vec3d;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Runs a fallback list of Scanners in priority order, returning the results of the first
 * scanner that locates valid positions.
 */
public final class QueueScanner implements Scanner {

    private final List<Scanner> scanners;

    public QueueScanner(List<Scanner> scanners) {
        Objects.requireNonNull(scanners, "scanners");
        this.scanners = List.copyOf(scanners);
    }

    public QueueScanner(Scanner... scanners) {
        this(List.of(scanners));
    }

    @Override
    public <T> List<Vec3d> scan(Vec3d anchor, VoxelSpace<T> space, Predicate<T> solidPredicate) {
        for (Scanner s : scanners) {
            List<Vec3d> results = s.scan(anchor, space, solidPredicate);
            if (!results.isEmpty()) {
                return results;
            }
        }
        return List.of();
    }

    public List<Scanner> getScanners() {
        return scanners;
    }

    @Override
    public int horizontalReach() {
        int r = 0;
        for (Scanner s : scanners) r = Math.max(r, s.horizontalReach());
        return r;
    }
}
