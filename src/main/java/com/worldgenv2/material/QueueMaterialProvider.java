package com.worldgenv2.material;

import com.worldgenv2.api.VoxelSpace;
import com.worldgenv2.material.patterns.Pattern;
import java.util.List;
import java.util.Objects;

/**
 * Priority fallback chain of conditional material providers.
 * Evaluates a sequence of (Pattern, MaterialProvider) rules in order and delegates to the first matching rule.
 * If no rule matches, falls back to the default MaterialProvider.
 */
public final class QueueMaterialProvider<T> implements MaterialProvider<T> {

    public record ConditionalRule<T>(Pattern<T> pattern, MaterialProvider<T> provider) {
        public ConditionalRule {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(provider, "provider");
        }
    }

    private final List<ConditionalRule<T>> rules;
    private final MaterialProvider<T> fallbackProvider;

    public QueueMaterialProvider(List<ConditionalRule<T>> rules, MaterialProvider<T> fallbackProvider) {
        this.rules = List.copyOf(rules);
        this.fallbackProvider = Objects.requireNonNull(fallbackProvider, "fallbackProvider");
    }

    public List<ConditionalRule<T>> rules() {
        return rules;
    }

    public MaterialProvider<T> fallbackProvider() {
        return fallbackProvider;
    }

    @Override
    public T getMaterial(int x, int y, int z, int depth, int space, float slopeAngle, VoxelSpace<T> spaceRef) {
        for (int i = 0, size = rules.size(); i < size; i++) {
            ConditionalRule<T> rule = rules.get(i);
            if (rule.pattern().matches(x, y, z, depth, space, slopeAngle, spaceRef)) {
                T result = rule.provider().getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
                if (result != null) {
                    return result;
                }
            }
        }
        return fallbackProvider.getMaterial(x, y, z, depth, space, slopeAngle, spaceRef);
    }
}
