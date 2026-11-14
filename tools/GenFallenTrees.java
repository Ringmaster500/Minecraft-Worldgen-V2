import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generates the shipped fallen-tree prefabs used by "Natural bridges": a broken stump with roots, a trunk that tapers
 * towards a leafy crown, a couple of branches, and moss / ferns / mushrooms on top. The long side runs along X.
 *
 * Run:  java tools/GenFallenTrees.java   (from the repository root)
 */
public class GenFallenTrees {

    static int hash(int x, int y, int z, int seed) {
        long v = ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L) ^ ((long) seed * 2654435761L);
        return (int) (Math.abs(v) % 1000);
    }

    static final int AIR = 0, LOG_X = 1, LOG_Y = 2, LOG_Z = 3, LEAVES = 4, MOSS = 5, RED = 6, BROWN = 7, FERN = 8;

    public static void main(String[] args) throws IOException {
        make("fallen_oak_a", 15, 11);
        make("fallen_oak_b", 19, 23);
        make("fallen_oak_c", 23, 37);
        makeTall("tall_oak_a", 18, 5);
        makeTall("tall_oak_b", 24, 17);
        makeTall("tall_oak_c", 30, 29);
        makeTall("tall_oak_d", 36, 41);
    }

    /** A tall standing oak: bare trunk below, a broad canopy with a few side lobes near the top. */
    static void makeTall(String name, int height, int seed) throws IOException {
        final int W = 13, C = 6;
        boolean thick = height >= 24;
        Map<String, int[]> set = new LinkedHashMap<>();
        java.util.function.Consumer<int[]> put = v -> {
            if (v[0] >= 0 && v[0] < W && v[1] >= 0 && v[1] < height && v[2] >= 0 && v[2] < W) set.put(v[0] + "," + v[1] + "," + v[2], v);
        };
        // trunk (leaves added first so logs always win)
        double top = height - 6.0;
        double rx = thick ? 5.2 : 4.4, ry = thick ? 4.6 : 4.0;
        double[][] lobes = new double[4][];
        for (int i = 0; i < 4; i++) {
            double ang = (hash(i, 1, 1, seed) % 628) / 100.0;
            double r = 3.0 + hash(i, 2, 2, seed) % 30 / 10.0;
            lobes[i] = new double[]{C + Math.cos(ang) * r, top - 3 - hash(i, 3, 3, seed) % 40 / 10.0, C + Math.sin(ang) * r, 2.4};
        }
        for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) for (int y = Math.max(0, (int) (top - 9)); y < height; y++) {
            double main = Math.pow((x - C) / rx, 2) + Math.pow((y - top) / ry, 2) + Math.pow((z - C) / rx, 2);
            boolean leaf = main <= 1.0;
            for (double[] l : lobes) {
                if (Math.pow((x - l[0]) / l[3], 2) + Math.pow((y - l[1]) / (l[3] * 0.8), 2) + Math.pow((z - l[2]) / l[3], 2) <= 1.0) leaf = true;
            }
            if (leaf && hash(x, y, z, seed) % 100 < 82) put.accept(new int[]{x, y, z, LEAVES});
        }
        int[][] trunk = thick ? new int[][]{{C, C}, {C + 1, C}, {C, C + 1}, {C + 1, C + 1}} : new int[][]{{C, C}};
        for (int[] t : trunk) for (int y = 0; y < height - 3; y++) put.accept(new int[]{t[0], y, t[1], LOG_Y});
        // a few branch stubs inside the canopy
        for (int i = 0; i < 3; i++) {
            int by = (int) (top - 2 - i * 2);
            put.accept(new int[]{C + 1 + (thick ? 1 : 0), by, C, LOG_X});
            put.accept(new int[]{C - 1, by - 1, C, LOG_X});
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"worldgenv2:structures/").append(name).append("\",\"version\":2,");
        sb.append("\"size\":{\"x\":").append(W).append(",\"y\":").append(height).append(",\"z\":").append(W).append("},");
        sb.append("\"anchor\":{\"x\":").append(C).append(",\"y\":0,\"z\":").append(C).append("},");
        sb.append("\"palette\":[{\"id\":0,\"name\":\"minecraft:air\"},");
        sb.append("{\"id\":1,\"name\":\"minecraft:oak_log\",\"properties\":{\"axis\":\"x\"}},");
        sb.append("{\"id\":2,\"name\":\"minecraft:oak_log\",\"properties\":{\"axis\":\"y\"}},");
        sb.append("{\"id\":3,\"name\":\"minecraft:oak_log\",\"properties\":{\"axis\":\"z\"}},");
        sb.append("{\"id\":4,\"name\":\"minecraft:oak_leaves\",\"properties\":{\"persistent\":\"true\"}}],");
        sb.append("\"blocks\":[");
        boolean first = true;
        for (int[] v : set.values()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"x\":").append(v[0]).append(",\"y\":").append(v[1]).append(",\"z\":").append(v[2]).append(",\"paletteIndex\":").append(v[3]).append('}');
        }
        sb.append("],\"sockets\":[],\"adaptiveFoundations\":[{\"minX\":").append(C - 1).append(",\"minZ\":").append(C - 1)
          .append(",\"maxX\":").append(C + 2).append(",\"maxZ\":").append(C + 2).append(",\"fillMaterial\":\"minecraft:dirt\",\"maxDepth\":4}]}");
        Path out = Path.of("src/main/resources/data/worldgenv2/prefabs/" + name + ".prefab.json");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println(name + ": height " + height + ", " + set.size() + " blocks");
    }

    static void make(String name, int len, int seed) throws IOException {
        final int SY = 6, SZ = 9;
        Map<String, int[]> set = new LinkedHashMap<>();
        java.util.function.Consumer<int[]> put = v -> {
            if (v[0] >= 0 && v[0] < len && v[1] >= 0 && v[1] < SY && v[2] >= 0 && v[2] < SZ) set.put(v[0] + "," + v[1] + "," + v[2], v);
        };
        // broken stump with roots
        int sh = 3 + hash(0, 0, 0, seed) % 2;
        for (int z : new int[]{3, 4}) for (int x : new int[]{0, 1}) {
            int h = sh - hash(x, 1, z, seed) % 2;
            for (int y = 0; y < h; y++) put.accept(new int[]{x, y, z, LOG_Y});
        }
        for (int[] r : new int[][]{{0, 0, 2}, {0, 0, 5}, {1, 0, 2}, {1, 0, 5}}) put.accept(new int[]{r[0], r[1], r[2], LOG_Z});
        // trunk, thick near the stump, thinner towards the crown
        int thickEnd = (int) (len * 0.5), thinStart = len - 5;
        for (int x = 2; x < len; x++) for (int y = 0; y < 2; y++) {
            if (x < thickEnd) { put.accept(new int[]{x, y, 3, LOG_X}); put.accept(new int[]{x, y, 4, LOG_X}); }
            else if (x < thinStart) { put.accept(new int[]{x, y, 3, LOG_X}); if (y == 0) put.accept(new int[]{x, y, 4, LOG_X}); }
            else put.accept(new int[]{x, y, 3, LOG_X});
        }
        // two branches, each ending in a leaf blob
        for (int bx : new int[]{(int) (len * 0.42), (int) (len * 0.62)}) {
            int side = hash(bx, 0, 0, seed) % 2 == 0 ? 1 : -1;
            int z0 = side > 0 ? 5 : 2;
            for (int k = 0; k < 3; k++) put.accept(new int[]{bx, 1, z0 + side * k, LOG_Z});
            int ez = z0 + side * 3;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 2; dy++) {
                if (hash(bx + dx, dy, ez + dz, seed) % 100 < 72 && !set.containsKey((bx + dx) + "," + (dy + 1) + "," + (ez + dz)))
                    put.accept(new int[]{bx + dx, dy + 1, ez + dz, LEAVES});
            }
        }
        // crown
        double cx = len - 3.0;
        for (int x = len - 7; x < len; x++) for (int z = 0; z < SZ; z++) for (int y = 0; y < SY; y++) {
            double d = Math.pow((x - cx) / 3.6, 2) + Math.pow((y - 2.2) / 2.6, 2) + Math.pow((z - 3.5) / 3.8, 2);
            if (d <= 1.0 && hash(x, y, z, seed) % 100 < 78 && !set.containsKey(x + "," + y + "," + z)) put.accept(new int[]{x, y, z, LEAVES});
        }
        // forest-floor life on top of the trunk
        for (int x = 3; x < len - 7; x++) for (int z : new int[]{3, 4}) {
            if (set.containsKey(x + ",1," + z) && !set.containsKey(x + ",2," + z)) {
                int r = hash(x, 2, z, seed) % 100;
                if (r < 28) put.accept(new int[]{x, 2, z, MOSS});
                else if (r < 36) put.accept(new int[]{x, 2, z, FERN});
                else if (r < 42) put.accept(new int[]{x, 2, z, RED});
                else if (r < 47) put.accept(new int[]{x, 2, z, BROWN});
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"name\":\"worldgenv2:structures/").append(name).append("\",\"version\":2,");
        sb.append("\"size\":{\"x\":").append(len).append(",\"y\":").append(SY).append(",\"z\":").append(SZ).append("},");
        sb.append("\"anchor\":{\"x\":").append(len / 2).append(",\"y\":0,\"z\":4},");
        sb.append("\"palette\":[{\"id\":0,\"name\":\"minecraft:air\"},");
        sb.append("{\"id\":1,\"name\":\"minecraft:oak_log\",\"properties\":{\"axis\":\"x\"}},");
        sb.append("{\"id\":2,\"name\":\"minecraft:oak_log\",\"properties\":{\"axis\":\"y\"}},");
        sb.append("{\"id\":3,\"name\":\"minecraft:oak_log\",\"properties\":{\"axis\":\"z\"}},");
        sb.append("{\"id\":4,\"name\":\"minecraft:oak_leaves\",\"properties\":{\"persistent\":\"true\"}},");
        sb.append("{\"id\":5,\"name\":\"minecraft:moss_carpet\"},{\"id\":6,\"name\":\"minecraft:red_mushroom\"},");
        sb.append("{\"id\":7,\"name\":\"minecraft:brown_mushroom\"},{\"id\":8,\"name\":\"minecraft:fern\"}],");
        sb.append("\"blocks\":[");
        boolean first = true;
        for (int[] v : set.values()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"x\":").append(v[0]).append(",\"y\":").append(v[1]).append(",\"z\":").append(v[2]).append(",\"paletteIndex\":").append(v[3]).append('}');
        }
        sb.append("],\"sockets\":[{\"name\":\"root\",\"type\":\"MARKER\",\"gender\":\"ANY\",\"position\":{\"x\":1,\"y\":0,\"z\":4},\"facing\":\"WEST\",\"compatibleTags\":[]}],\"adaptiveFoundations\":[");
        sb.append("{\"minX\":0,\"minZ\":2,\"maxX\":4,\"maxZ\":5,\"fillMaterial\":\"minecraft:dirt\",\"maxDepth\":5},");
        sb.append("{\"minX\":").append(len - 5).append(",\"minZ\":2,\"maxX\":").append(len - 1).append(",\"maxZ\":5,\"fillMaterial\":\"minecraft:dirt\",\"maxDepth\":5}]}");
        Path out = Path.of("src/main/resources/data/worldgenv2/prefabs/" + name + ".prefab.json");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println(name + ": " + set.size() + " blocks");
    }
}
