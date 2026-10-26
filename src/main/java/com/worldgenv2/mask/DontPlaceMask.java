package com.worldgenv2.mask;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Rejects placement if the candidate source material matches the {@code dontPlace} filter.
 */
public final class DontPlaceMask<T> implements BlockMask<T> {

    private final Predicate<T> dontPlace;

    public DontPlaceMask(Predicate<T> dontPlace) {
        this.dontPlace = Objects.requireNonNull(dontPlace, "dontPlace");
    }

    public DontPlaceMask(Collection<T> dontPlace) {
        Set<T> set = Set.copyOf(dontPlace);
        this.dontPlace = m -> m != null && set.contains(m);
    }

    public Predicate<T> dontPlace() {
        return dontPlace;
    }

    @Override
    public boolean canPlace(T source, T destination) {
        if (source == null) {
            return true;
        }
        return !dontPlace.test(source);
    }
}
