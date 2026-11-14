package com.worldgenv2.props;

import java.util.Objects;

/**
 * Runs the child prop only when a placed structure (the spawn structure, a landmark, a point of interest, a bridge...)
 * lies near the position, optionally above or below it. This is how "a tree grows here only if there is a buried
 * structure underneath" is expressed: put this around the tree inside an assignment.
 *
 * <p>{@code structure} filters by placement id (substring, empty = any structure). {@code radius} widens the structure's
 * footprint sideways. {@code yMin}/{@code yMax} are offsets from the prop's own Y: the structure's vertical extent must
 * overlap {@code [y + yMin, y + yMax]}. With {@code invert} the child runs only when no structure is near.
 */
public final class StructureNearProp<T> implements Prop<T> {

    /** Answers "is a matching structure near this point?". Bound after the world structure is built. */
    @FunctionalInterface
    public interface Query {
        boolean near(int x, int y, int z, String filter, int radius, int yMin, int yMax);
    }

    /** Late-bound handle, because biomes compile before the structure list exists. */
    public static final class Ref {
        private volatile Query query;
        public void bind(Query q) { this.query = q; }
        Query get() { return query; }
    }

    private static final ThreadLocal<Ref> COMPILING = new ThreadLocal<>();

    /** Called by the project compiler around a compile so props created during it share one handle. */
    public static Ref beginCompile() {
        Ref r = new Ref();
        COMPILING.set(r);
        return r;
    }

    public static void endCompile() { COMPILING.remove(); }

    public static Ref current() { return COMPILING.get(); }

    private final Ref ref;
    private final String filter;
    private final int radius, yMin, yMax;
    private final boolean invert;
    private final Prop<T> child;

    public StructureNearProp(Ref ref, String filter, int radius, int yMin, int yMax, boolean invert, Prop<T> child) {
        this.ref = ref;
        this.filter = filter == null ? "" : filter;
        this.radius = Math.max(0, radius);
        this.yMin = yMin;
        this.yMax = yMax;
        this.invert = invert;
        this.child = Objects.requireNonNull(child, "child");
    }

    @Override
    public boolean generate(Context<T> c) {
        Query q = ref == null ? null : ref.get();
        boolean near = q != null && q.near(c.worldX(), c.worldY(), c.worldZ(), filter, radius, yMin, yMax);
        if (near == invert) return false;
        return child.generate(c);
    }

    @Override
    public int horizontalReach() {
        return child.horizontalReach();
    }
}
