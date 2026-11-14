package com.worldgenv2.placement;

import java.util.HashMap;
import java.util.Map;

/**
 * The named numbers a placement rule builds up as it runs. A finder sets things like {@code span.length}; each prefab
 * action records {@code <name>.x}, {@code <name>.length}, {@code <name>.socket.<socket>.x}, ...; expressions read them.
 */
public final class Vars {
    private final Map<String, Double> values = new HashMap<>();

    public Vars set(String key, double value) {
        values.put(key, value);
        return this;
    }

    /** The value, or NaN when it was never set. */
    public double get(String key) {
        Double d = values.get(key);
        return d == null ? Double.NaN : d;
    }

    public boolean has(String key) { return values.containsKey(key); }

    public Vars copy() {
        Vars v = new Vars();
        v.values.putAll(values);
        return v;
    }

    @Override
    public String toString() { return values.toString(); }
}
