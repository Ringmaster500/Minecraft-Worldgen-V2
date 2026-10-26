package com.worldgenv2.mask;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filter predicate representing candidate source materials being placed.
 */
@FunctionalInterface
public interface SourceMaterial<T> {

    boolean matches(T material);

    static <T> SourceMaterial<T> of(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return predicate::test;
    }

    @SafeVarargs
    static <T> SourceMaterial<T> of(T... materials) {
        Set<T> set = Set.of(materials);
        return m -> m != null && set.contains(m);
    }

    static <T> SourceMaterial<T> of(Collection<T> materials) {
        Set<T> set = Set.copyOf(materials);
        return m -> m != null && set.contains(m);
    }

    static <T> SourceMaterial<T> any() {
        return m -> true;
    }
}
