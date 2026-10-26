package com.worldgenv2.mask;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Strategy contract determining whether a source material is allowed to replace
 * an existing destination material during prop and feature placement.
 *
 * @param <T> Block state or material type
 */
@FunctionalInterface
public interface BlockMask<T> {

    /**
     * Tests whether {@code source} material can be placed onto {@code destination}.
     *
     * @param source The candidate material being placed
     * @param destination The material currently existing at the voxel (null if air or empty)
     * @return true if placement is allowed; false if blocked
     */
    boolean canPlace(T source, T destination);

    default BlockMask<T> and(BlockMask<T> other) {
        Objects.requireNonNull(other, "other");
        return (source, destination) -> this.canPlace(source, destination) && other.canPlace(source, destination);
    }

    default BlockMask<T> or(BlockMask<T> other) {
        Objects.requireNonNull(other, "other");
        return (source, destination) -> this.canPlace(source, destination) || other.canPlace(source, destination);
    }

    default BlockMask<T> negate() {
        return (source, destination) -> !this.canPlace(source, destination);
    }

    static <T> BlockMask<T> always() {
        return (s, d) -> true;
    }

    static <T> BlockMask<T> never() {
        return (s, d) -> false;
    }
}
