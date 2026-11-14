package com.worldgenv2.hytale;

import com.worldgenv2.props.Prop;
import com.worldgenv2.props.StaticRotatorProp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hytale's search props. A <b>Locator</b> scans positions around its anchor and generates its child at every position the
 * Pattern validates (up to {@code PlacementCap}). An <b>Orienter</b> does the same but also tries the Pattern turned to each
 * rotation, and generates the child turned to match.
 */
public final class HProps {
    private HProps() {}

    public static final class Locator<T> implements Prop<T> {
        private final Prop<T> child;
        private final HPattern<T> pattern;
        private final HScanner scanner;
        private final int placementCap;

        public Locator(Prop<T> child, HPattern<T> pattern, HScanner scanner, int placementCap) {
            this.child = child;
            this.pattern = pattern;
            this.scanner = scanner;
            this.placementCap = Math.max(0, placementCap);
        }

        @Override
        public boolean generate(Context<T> ctx) {
            HPattern.Probe<T> probe = new HPattern.Probe<>(ctx.voxelSpace());
            int placed = 0;
            for (int[] p : scanner.scan(ctx.worldX(), ctx.worldY(), ctx.worldZ(), ctx.seed())) {
                if (!pattern.test(probe, p[0], p[1], p[2], 0)) continue;
                long seed = ctx.seed() ^ (p[0] * 0x9E3779B97F4A7C15L) ^ (p[2] * 0xC2B2AE3D27D4EB4FL) ^ p[1];
                if (child.generate(new Context<>(p[0], p[1], p[2], ctx.normalX(), ctx.normalY(), ctx.normalZ(), ctx.voxelSpace(), seed))) {
                    placed++;
                    if (placementCap > 0 && placed >= placementCap) break;
                }
            }
            return placed > 0;
        }

        @Override
        public int horizontalReach() { return scanner.reach() + pattern.reach() + child.horizontalReach(); }

        @Override
        public int contentReach() { return child.horizontalReach(); }
    }

    public enum Selection { FIRST_VALID, ALL_VALID, RANDOM_VALID }

    public static final class Orienter<T> implements Prop<T> {
        private final Prop<T> child;
        private final List<Integer> rotations;     // quarter turns
        private final HPattern<T> pattern;
        private final HScanner scanner;
        private final Selection mode;
        private final long seedSalt;

        public Orienter(Prop<T> child, List<Integer> rotations, HPattern<T> pattern, HScanner scanner, Selection mode, long seedSalt) {
            this.child = child;
            this.rotations = rotations.isEmpty() ? List.of(0) : rotations;
            this.pattern = pattern;
            this.scanner = scanner;
            this.mode = mode;
            this.seedSalt = seedSalt;
        }

        /** Places the child at the first scanned position where some rotation validates, according to the selection mode. */
        @Override
        public boolean generate(Context<T> ctx) {
            HPattern.Probe<T> probe = new HPattern.Probe<>(ctx.voxelSpace());
            for (int[] p : scanner.scan(ctx.worldX(), ctx.worldY(), ctx.worldZ(), ctx.seed())) {
                List<Integer> valid = new ArrayList<>();
                for (int r : rotations) if (pattern.test(probe, p[0], p[1], p[2], r)) valid.add(r);
                if (valid.isEmpty()) continue;
                List<Integer> chosen;
                switch (mode) {
                    case ALL_VALID -> chosen = valid;
                    case RANDOM_VALID -> chosen = List.of(valid.get(new Random(seedSalt ^ ctx.seed() ^ (p[0] * 31L + p[2] * 17L + p[1])).nextInt(valid.size())));
                    default -> chosen = List.of(valid.get(0));
                }
                boolean any = false;
                for (int r : chosen) {
                    long seed = ctx.seed() ^ (p[0] * 0x9E3779B97F4A7C15L) ^ (p[2] * 0xC2B2AE3D27D4EB4FL) ^ p[1] ^ r;
                    Prop<T> turned = r == 0 ? child : new StaticRotatorProp<>(child, StaticRotatorProp.CardinalRotation.fromQuarterTurns(r));
                    any |= turned.generate(new Context<>(p[0], p[1], p[2], ctx.normalX(), ctx.normalY(), ctx.normalZ(), ctx.voxelSpace(), seed));
                }
                return any;
            }
            return false;
        }

        @Override
        public int horizontalReach() { return scanner.reach() + pattern.reach() + child.horizontalReach(); }

        @Override
        public int contentReach() { return child.horizontalReach(); }
    }
}
