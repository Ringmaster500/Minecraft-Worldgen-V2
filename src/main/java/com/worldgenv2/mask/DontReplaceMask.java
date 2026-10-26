package com.worldgenv2.mask;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Rejects placement if the existing destination material matches the {@code dontReplace} filter.
 */
public final class DontReplaceMask<T> implements BlockMask<T> {

    private final Predicate<T> dontReplace;

    public DontReplaceMask(Predicate<T> dontReplace) {
        this.dontReplace = Objects.requireNonNull(dontReplace, "dontReplace");
    }

    public DontReplaceMask(Collection<T> dontReplace) {
        Set<T> set = Set.copyOf(dontReplace);
        this.dontReplace = m -> m != null && set.contains(m);
    }

    public Predicate<T> dontReplace() {
        return dontReplace;
    }

    @Override
    public boolean canPlace(T source, T destination) {
        if (destination == null) {
            return true;
        }
        return !dontReplace.test(destination);
    }
}
