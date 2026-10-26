package com.worldgenv2.props;

import java.util.List;
import java.util.Objects;

/**
 * Executes placement of all child Props at the same location, one after another.
 */
public final class UnionProp<T> implements Prop<T> {

    private final List<Prop<T>> props;

    public UnionProp(List<Prop<T>> props) {
        this.props = props == null ? List.of() : List.copyOf(props);
    }

    @SafeVarargs
    public UnionProp(Prop<T>... props) {
        this.props = List.of(props);
    }

    public List<Prop<T>> props() {
        return props;
    }

    @Override
    public boolean generate(Context<T> context) {
        boolean placedAny = false;
        for (Prop<T> prop : props) {
            if (prop.generate(context)) {
                placedAny = true;
            }
        }
        return placedAny;
    }
}
