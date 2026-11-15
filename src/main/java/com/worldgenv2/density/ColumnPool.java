package com.worldgenv2.density;

/**
 * Scratch float buffers for column evaluation, reused per thread instead of allocated for every node of every column.
 * Use as a stack: {@code float[] b = ColumnPool.take(n); ... ColumnPool.give(b);} in last-in-first-out order.
 */
public final class ColumnPool {
    private ColumnPool() {}

    private static final class Stack {
        float[][] free = new float[16][];
        int count;
    }

    private static final ThreadLocal<Stack> STACK = ThreadLocal.withInitial(Stack::new);

    /** A buffer with room for at least {@code n} floats; its contents are unspecified. */
    public static float[] take(int n) {
        Stack s = STACK.get();
        while (s.count > 0) {
            float[] b = s.free[--s.count];
            s.free[s.count] = null;
            if (b.length >= n) return b;
        }
        return new float[Math.max(n, 640)];
    }

    public static void give(float[] b) {
        Stack s = STACK.get();
        if (s.count == s.free.length) s.free = java.util.Arrays.copyOf(s.free, s.count * 2);
        s.free[s.count++] = b;
    }
}