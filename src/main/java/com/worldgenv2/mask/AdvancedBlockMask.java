package com.worldgenv2.mask;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Advanced composite block mask supporting:
 * <ol>
 *   <li>{@code dontPlace}: source materials that can never be placed</li>
 *   <li>{@code dontReplace}: destination materials that are protected by default</li>
 *   <li>{@code advanced}: rule list overriding default behavior for specific source materials</li>
 * </ol>
 *
 * <p>Evaluation logic:
 * <ol>
 *   <li>If source is null, allow.</li>
 *   <li>If {@code dontPlace.test(source)} returns true, reject immediately.</li>
 *   <li>If source matches any advanced rule:
 *       <ul>
 *         <li>If destination matches the rule's {@code canReplace}, accept.</li>
 *         <li>Otherwise (if matched source but no rule permits the destination), reject.</li>
 *       </ul>
 *   </li>
 *   <li>If destination is null, accept.</li>
 *   <li>Otherwise, accept if and only if {@code !dontReplace.test(destination)}.</li>
 * </ol>
 */
public final class AdvancedBlockMask<T> implements BlockMask<T> {

    public record Rule<T>(SourceMaterial<T> source, DestinationMaterial<T> canReplace) {
        public Rule {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(canReplace, "canReplace");
        }
    }

    private final Predicate<T> dontPlace;
    private final Predicate<T> dontReplace;
    private final List<Rule<T>> rules;

    public AdvancedBlockMask(Predicate<T> dontPlace, Predicate<T> dontReplace, List<Rule<T>> rules) {
        this.dontPlace = dontPlace != null ? dontPlace : m -> false;
        this.dontReplace = dontReplace != null ? dontReplace : m -> false;
        this.rules = rules != null ? List.copyOf(rules) : List.of();
    }

    public Predicate<T> dontPlace() {
        return dontPlace;
    }

    public Predicate<T> dontReplace() {
        return dontReplace;
    }

    public List<Rule<T>> rules() {
        return rules;
    }

    @Override
    public boolean canPlace(T source, T destination) {
        if (source != null && dontPlace.test(source)) {
            return false;
        }

        boolean matchedRule = false;
        for (Rule<T> rule : rules) {
            if (rule.source().matches(source)) {
                matchedRule = true;
                if (rule.canReplace().matches(destination)) {
                    return true;
                }
            }
        }

        if (matchedRule) {
            return false;
        }

        if (destination == null) {
            return true;
        }

        return !dontReplace.test(destination);
    }
}
