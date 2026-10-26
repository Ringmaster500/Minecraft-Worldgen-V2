package com.worldgenv2.scanner;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.vector.Vec3d;
import java.util.List;
import java.util.function.Predicate;

/**
 * Returns only the origin anchor point without any probing.
 */
public final class OriginScanner implements Scanner {

    public static final OriginScanner INSTANCE = new OriginScanner();

    @Override
    public <T> List<Vec3d> scan(Vec3d anchor, VoxelSpace<T> space, Predicate<T> solidPredicate) {
        return List.of(anchor);
    }
}
