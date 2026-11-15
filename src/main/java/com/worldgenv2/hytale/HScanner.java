package com.worldgenv2.hytale;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Hytale's Scanner: enumerates the positions around an anchor to try. A scanner never judges a position; a Pattern does.
 * Origin, Linear, Random, Radial and Queue, each optionally feeding a child scanner for every position it yields.
 */
public interface HScanner {

    /** Positions {x, y, z} in scan order. {@code seed} orders the Random scanner. */
    List<int[]> scan(int ax, int ay, int az, long seed);

    /** How many validated positions a prop may be placed at (Hytale's ResultCap, release schema); 0 when the scanner does not say. */
    default int resultCap() { return 0; }

    /** How far horizontally from the anchor this scanner reaches. */
    default int reach() { return 0; }

    HScanner ORIGIN = new HScanner() {
        public List<int[]> scan(int ax, int ay, int az, long seed) { return List.of(new int[]{ax, ay, az}); }
    };

    /** Blocks min..max along an axis (0 = X, 1 = Y, 2 = Z) around the anchor, in order. */
    static HScanner linear(int axis, int min, int max, HScanner child) {
        return new HScanner() {
            public List<int[]> scan(int ax, int ay, int az, long seed) {
                List<int[]> out = new ArrayList<>();
                for (int o = Math.min(min, max); o <= Math.max(min, max); o++) emit(out, ax, ay, az, axis, o, seed, child);
                return out;
            }
            public int reach() { return (axis == 1 ? 0 : Math.max(Math.abs(min), Math.abs(max))) + (child == null ? 0 : child.reach()); }
        };
    }

    /** The same blocks as {@link #linear} in a pseudo-random order determined by the seed (and the anchor). */
    static HScanner random(int axis, int min, int max, long seedSalt, HScanner child) {
        return new HScanner() {
            public List<int[]> scan(int ax, int ay, int az, long seed) {
                List<Integer> offs = new ArrayList<>();
                for (int o = Math.min(min, max); o <= Math.max(min, max); o++) offs.add(o);
                Collections.shuffle(offs, new Random(seedSalt ^ seed ^ (ax * 31L + az * 17L + ay)));
                List<int[]> out = new ArrayList<>();
                for (int o : offs) emit(out, ax, ay, az, axis, o, seed, child);
                return out;
            }
            public int reach() { return (axis == 1 ? 0 : Math.max(Math.abs(min), Math.abs(max))) + (child == null ? 0 : child.reach()); }
        };
    }

    /** Every position within the box around the anchor, nearest first (the anchor itself first). */
    static HScanner radial(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, HScanner child) {
        return new HScanner() {
            public List<int[]> scan(int ax, int ay, int az, long seed) {
                List<int[]> all = new ArrayList<>();
                for (int dx = minX; dx <= maxX; dx++) for (int dy = minY; dy <= maxY; dy++) for (int dz = minZ; dz <= maxZ; dz++) all.add(new int[]{dx, dy, dz});
                all.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1] + a[2] * a[2], b[0] * b[0] + b[1] * b[1] + b[2] * b[2]));
                List<int[]> out = new ArrayList<>();
                for (int[] d : all) {
                    if (child == null) out.add(new int[]{ax + d[0], ay + d[1], az + d[2]});
                    else out.addAll(child.scan(ax + d[0], ay + d[1], az + d[2], seed));
                }
                return out;
            }
            public int reach() { return Math.max(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(minZ), Math.abs(maxZ))) + (child == null ? 0 : child.reach()); }
        };
    }

    /** Runs the scanners one after another. */
    static HScanner queue(List<HScanner> scanners) {
        return new HScanner() {
            public List<int[]> scan(int ax, int ay, int az, long seed) {
                List<int[]> out = new ArrayList<>();
                for (HScanner s : scanners) out.addAll(s.scan(ax, ay, az, seed));
                return out;
            }
            public int reach() { int r = 0; for (HScanner s : scanners) r = Math.max(r, s.reach()); return r; }
        };
    }

    private static void emit(List<int[]> out, int ax, int ay, int az, int axis, int o, long seed, HScanner child) {
        int x = ax + (axis == 0 ? o : 0), y = ay + (axis == 1 ? o : 0), z = az + (axis == 2 ? o : 0);
        if (child == null) out.add(new int[]{x, y, z}); else out.addAll(child.scan(x, y, z, seed));
    }
}
