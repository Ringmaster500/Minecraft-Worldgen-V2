package com.worldgenv2.mask;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filter predicate representing existing destination materials in the voxel space.
 */
@FunctionalInterface
public interface DestinationMaterial<T> {

    boolean matches(T material);

    static <T> DestinationMaterial<T> of(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return predicate::test;
    }

    @SafeVarargs
    static <T> DestinationMaterial<T> of(T... materials) {
        Set<T> set = Set.of(materials);
        return m -> m != null && set.contains(m);
    }

    static <T> DestinationMaterial<T> of(Collection<T> materials) {
        Set<T> set = Set.copyOf(materials);
        return m -> m != null && set.contains(m);
    }

    static <T> DestinationMaterial<T> any() {
        return m -> true;
    }
}
