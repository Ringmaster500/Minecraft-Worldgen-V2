package com.worldgenv2.studio.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural edits on a biome's JSON AST. A node is addressed by a path of object keys and array indices
 * from the biome root, e.g. ["terrain", "inputs", 1, "input"]. All methods mutate the given root; callers
 * run them inside {@code StudioProject.edit} so they are undoable.
 */
public final class GraphOps {
    private GraphOps() {}

    public static List<Object> child(List<Object> path, Object step) {
        List<Object> p = new ArrayList<>(path);
        p.add(step);
        return p;
    }

    public static String id(List<Object> path) {
        StringBuilder sb = new StringBuilder();
        for (Object o : path) sb.append('/').append(o);
        return sb.toString();
    }

    public static JsonElement at(JsonObject root, List<Object> path) {
        JsonElement cur = root;
        for (Object step : path) {
            if (cur == null) return null;
            if (step instanceof Integer i) {
                if (!cur.isJsonArray() || i >= cur.getAsJsonArray().size()) return null;
                cur = cur.getAsJsonArray().get(i);
            } else {
                if (!cur.isJsonObject()) return null;
                cur = cur.getAsJsonObject().get((String) step);
            }
        }
        return cur;
    }

    public static JsonObject nodeAt(JsonObject root, List<Object> path) {
        JsonElement e = at(root, path);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /** Puts {@code node} into the slot of the node at {@code parentPath}; a single slot is replaced, a list appended. */
    public static List<Object> attach(JsonObject root, List<Object> parentPath, String slotKey, boolean multi, JsonObject node) {
        JsonObject parent = nodeAt(root, parentPath);
        if (parent == null) return parentPath;
        if (multi || (parent.get(slotKey) != null && parent.get(slotKey).isJsonArray())) {
            JsonArray arr;
            if (parent.get(slotKey) != null && parent.get(slotKey).isJsonArray()) arr = parent.getAsJsonArray(slotKey);
            else { arr = new JsonArray(); parent.add(slotKey, arr); }
            arr.add(node);
            return child(child(parentPath, slotKey), arr.size() - 1);
        }
        parent.add(slotKey, node);
        return child(parentPath, slotKey);
    }

    /** Removes the node (and its whole subtree). Returns the parent path, which is the sensible new selection. */
    public static List<Object> remove(JsonObject root, List<Object> path) {
        if (path.isEmpty()) return path;
        List<Object> parentPath = new ArrayList<>(path.subList(0, path.size() - 1));
        Object last = path.get(path.size() - 1);
        JsonElement parent = at(root, parentPath);
        if (parent == null) return path;
        if (last instanceof Integer i && parent.isJsonArray()) {
            parent.getAsJsonArray().remove(i.intValue());
            // the array itself is a slot of the grandparent; select the owning node
            return parentPath.size() > 0 ? new ArrayList<>(parentPath.subList(0, parentPath.size() - 1)) : parentPath;
        }
        if (parent.isJsonObject()) parent.getAsJsonObject().remove((String) last);
        return parentPath;
    }

    /** Replaces the node at {@code path} with {@code replacement} in place. */
    public static void replace(JsonObject root, List<Object> path, JsonObject replacement) {
        if (path.isEmpty()) return;
        List<Object> parentPath = new ArrayList<>(path.subList(0, path.size() - 1));
        Object last = path.get(path.size() - 1);
        JsonElement parent = at(root, parentPath);
        if (parent == null) return;
        if (last instanceof Integer i && parent.isJsonArray()) parent.getAsJsonArray().set(i, replacement);
        else if (parent.isJsonObject()) parent.getAsJsonObject().add((String) last, replacement);
    }

    /**
     * Wraps the node at {@code path} inside a new node of type {@code wrapper}, using the wrapper's first
     * child slot that accepts the node's kind. Returns false when the wrapper has no compatible slot.
     */
    public static boolean wrap(JsonObject root, List<Object> path, String nodeKind, NodeSchemas.NodeType wrapper) {
        NodeSchemas.Slot target = null;
        for (NodeSchemas.Slot s : wrapper.slots()) {
            if (s.kind().equals(nodeKind) || s.kind().equals("any")) { target = s; break; }
        }
        if (target == null) return false;
        JsonObject inner = nodeAt(root, path);
        if (inner == null) return false;
        JsonObject outer = NodeSchemas.newNode(wrapper);
        if (target.multi()) {
            JsonArray arr = new JsonArray();
            arr.add(inner);
            outer.add(target.key(), arr);
        } else {
            outer.add(target.key(), inner);
        }
        replace(root, path, outer);
        return true;
    }

    public static boolean duplicate(JsonObject root, List<Object> path) {
        if (path.isEmpty() || !(path.get(path.size() - 1) instanceof Integer i)) return false;
        List<Object> parentPath = new ArrayList<>(path.subList(0, path.size() - 1));
        JsonElement parent = at(root, parentPath);
        JsonElement self = at(root, path);
        if (parent == null || !parent.isJsonArray() || self == null) return false;
        parent.getAsJsonArray().add(self.deepCopy());
        return true;
    }

    public static void setType(JsonObject root, List<Object> path, String type) {
        JsonObject n = nodeAt(root, path);
        if (n != null) n.addProperty("type", type);
    }
}
