package com.worldgenv2.studio.project;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** One JSON file inside a project. {@code path} is project-relative with forward slashes. */
public final class Asset {
    public static final String WORLD_PATH = "world.json";

    public record Problem(Severity severity, String assetPath, String message) {}

    public enum Severity { ERROR, WARNING, INFO }

    public final String path;
    public final AssetType type;
    public JsonObject json;
    public boolean dirty;
    /** Non-null when the file on disk could not be parsed; the raw text is preserved until the user edits it. */
    public String parseError;
    public String rawText;
    public final List<Problem> problems = new ArrayList<>();

    public Asset(String path, AssetType type, JsonObject json) {
        this.path = path;
        this.type = type;
        this.json = json;
    }

    /** File stem, e.g. "alpine_peaks". */
    public String id() {
        if (type == AssetType.WORLD) return "world";
        String n = path.substring(path.lastIndexOf('/') + 1);
        int dot = n.indexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    public String displayName() {
        if (type == AssetType.WORLD) return "World Settings";
        String id = id();
        StringBuilder sb = new StringBuilder();
        boolean up = true;
        for (char c : id.toCharArray()) {
            if (c == '_' || c == '-') { sb.append(' '); up = true; }
            else { sb.append(up ? Character.toUpperCase(c) : c); up = false; }
        }
        return sb.toString();
    }

    public int worstSeverityColor() {
        boolean warn = false;
        for (Problem p : problems) {
            if (p.severity() == Severity.ERROR) return 0xFFE5534B;
            if (p.severity() == Severity.WARNING) warn = true;
        }
        return warn ? 0xFFE3B341 : 0;
    }
}
