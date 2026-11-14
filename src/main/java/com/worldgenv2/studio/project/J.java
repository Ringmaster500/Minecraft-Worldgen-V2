package com.worldgenv2.studio.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Null-safe JSON accessors so editors never have to repeat defensive parsing. */
public final class J {
    private J() {}

    public static boolean has(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull();
    }

    public static String str(JsonObject o, String k, String def) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    public static double num(JsonObject o, String k, double def) {
        JsonElement e = o == null ? null : o.get(k);
        if (e != null && e.isJsonPrimitive()) {
            try { return e.getAsDouble(); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public static int integer(JsonObject o, String k, int def) {
        return (int) Math.round(num(o, k, def));
    }

    public static boolean bool(JsonObject o, String k, boolean def) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
    }

    public static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Gets the child object, creating it when absent. */
    public static JsonObject objOrCreate(JsonObject o, String k) {
        JsonObject c = obj(o, k);
        if (c == null) { c = new JsonObject(); o.add(k, c); }
        return c;
    }

    public static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    public static JsonArray arrOrCreate(JsonObject o, String k) {
        JsonArray a = arr(o, k);
        if (a == null) { a = new JsonArray(); o.add(k, a); }
        return a;
    }

    /** Sets a number as an integer when it is whole, so files stay tidy (64 not 64.0). */
    public static void setNum(JsonObject o, String k, double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) o.add(k, new JsonPrimitive((long) v));
        else o.add(k, new JsonPrimitive(Math.round(v * 10000.0) / 10000.0));
    }
}
