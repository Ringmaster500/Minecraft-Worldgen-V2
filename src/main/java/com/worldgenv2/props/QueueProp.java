package com.worldgenv2.props;

import java.util.List;
import java.util.Objects;

/**
 * Executes an ordered fallback list of Props, placing the first one that successfully generates
 * and skipping the remainder.
 */
public final class QueueProp<T> implements Prop<T> {

    private final List<Prop<T>> props;

    public QueueProp(List<Prop<T>> props) {
        this.props = props == null ? List.of() : List.copyOf(props);
    }

    @SafeVarargs
    public QueueProp(Prop<T>... props) {
        this.props = List.of(props);
    }

    public List<Prop<T>> props() {
        return props;
    }

    @Override
    public boolean generate(Context<T> context) {
        for (Prop<T> prop : props) {
            if (prop.generate(context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int horizontalReach() {
        int r = 0;
        for (Prop<T> p : props) r = Math.max(r, p.horizontalReach());
        return r;
    }
}
