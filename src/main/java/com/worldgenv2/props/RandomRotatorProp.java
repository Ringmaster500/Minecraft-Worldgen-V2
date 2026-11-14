package com.worldgenv2.props;

import com.worldgenv2.props.StaticRotatorProp.CardinalRotation;
import java.util.List;
import java.util.Objects;

/**
 * Rotates the child Prop by a pseudo-random rotation selected from an allowed rotation set.
 */
public final class RandomRotatorProp<T> implements Prop<T> {

    private final Prop<T> childProp;
    private final List<CardinalRotation> allowedRotations;
    private final String seedSalt;

    public RandomRotatorProp(Prop<T> childProp) {
        this(childProp, List.of(CardinalRotation.DEG_0, CardinalRotation.DEG_90, CardinalRotation.DEG_180, CardinalRotation.DEG_270), "rot");
    }

    public RandomRotatorProp(Prop<T> childProp, boolean horizontalRotations, String seedSalt) {
        this(
            childProp,
            horizontalRotations
                ? List.of(CardinalRotation.DEG_0, CardinalRotation.DEG_90, CardinalRotation.DEG_180, CardinalRotation.DEG_270)
                : List.of(CardinalRotation.DEG_0),
            seedSalt
        );
    }

    public RandomRotatorProp(Prop<T> childProp, List<CardinalRotation> allowedRotations, String seedSalt) {
        this.childProp = Objects.requireNonNull(childProp, "childProp");
        if (allowedRotations == null || allowedRotations.isEmpty()) {
            this.allowedRotations = List.of(CardinalRotation.DEG_0, CardinalRotation.DEG_90, CardinalRotation.DEG_180, CardinalRotation.DEG_270);
        } else {
            this.allowedRotations = List.copyOf(allowedRotations);
        }
        this.seedSalt = seedSalt != null ? seedSalt : "rot";
    }

    public Prop<T> childProp() {
        return childProp;
    }

    public List<CardinalRotation> allowedRotations() {
        return allowedRotations;
    }

    public String seedSalt() {
        return seedSalt;
    }

    @Override
    public boolean generate(Context<T> context) {
        long h = context.seed() ^ ((long) seedSalt.hashCode() * 0x9E3779B97F4A7C15L)
            ^ ((long) context.worldX() * 0xBF58476D1CE4E5B9L)
            ^ ((long) context.worldZ() * 0x94D049BB133111EBL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        int idx = (int) Math.floorMod(h, allowedRotations.size());
        CardinalRotation rotation = allowedRotations.get(idx);

        if (rotation == CardinalRotation.DEG_0) {
            return childProp.generate(context);
        }

        Prop<T> rotator = new StaticRotatorProp<>(childProp, rotation);
        return rotator.generate(context);
    }

    @Override
    public int horizontalReach() {
        return childProp.horizontalReach();
    }
}
