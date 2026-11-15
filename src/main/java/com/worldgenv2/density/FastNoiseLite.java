package com.worldgenv2.density;

/**
 * Pure Java 25 port of FastNoiseLite for Hytale WorldGen V2.
 * Supports OpenSimplex2, Cellular (Worley), Perlin, and Value noise in 2D and 3D,
 * with FBm, Ridged Multifractal, and PingPong fractal octaves.
 * Thread-safe for concurrent multi-threaded chunk generation after configuration.
 */
public final class FastNoiseLite {

    public enum NoiseType {
        OpenSimplex2,
        OpenSimplex2S,
        Cellular,
        Perlin,
        Value
    }

    public enum FractalType {
        None,
        FBm,
        Ridged,
        PingPong
    }

    public enum CellularDistanceFunction {
        Euclidean,
        EuclideanSq,
        Manhattan,
        Hybrid
    }

    public enum CellularReturnType {
        CellValue,
        Distance,
        Distance2,
        Distance2Add,
        Distance2Sub,
        Distance2Mul,
        Distance2Div,
        CellWallDistance
    }

    private int seed = 1337;
    private float frequency = 0.01f;
    private NoiseType noiseType = NoiseType.OpenSimplex2;
    private FractalType fractalType = FractalType.None;
    private int octaves = 3;
    private float lacunarity = 2.0f;
    private float gain = 0.5f;
    private float weightedStrength = 0.0f;
    private float pingPongStrength = 2.0f;
    private float fractalBounding = 1.0f / 1.75f;

    private CellularDistanceFunction cellularDistanceFunction = CellularDistanceFunction.EuclideanSq;
    private CellularReturnType cellularReturnType = CellularReturnType.Distance;
    private float cellularJitterModifier = 1.0f;

    /** Everything that decides this noise's values: two noises with equal signatures give the same number everywhere. */
    public record Signature(int seed, float frequency, NoiseType noiseType, FractalType fractalType, int octaves, float lacunarity,
                            float gain, float weightedStrength, float pingPongStrength, float fractalBounding,
                            CellularDistanceFunction cellularDistanceFunction, CellularReturnType cellularReturnType,
                            float cellularJitterModifier) {}

    public Signature signature() {
        return new Signature(seed, frequency, noiseType, fractalType, octaves, lacunarity, gain, weightedStrength, pingPongStrength,
            fractalBounding, cellularDistanceFunction, cellularReturnType, cellularJitterModifier);
    }

    public FastNoiseLite() {
        updateFractalBounding();
    }

    public FastNoiseLite(int seed) {
        this.seed = seed;
        updateFractalBounding();
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public int getSeed() {
        return seed;
    }

    public void setFrequency(float frequency) {
        this.frequency = frequency;
    }

    public float getFrequency() {
        return frequency;
    }

    public void setNoiseType(NoiseType noiseType) {
        this.noiseType = noiseType;
    }

    public NoiseType getNoiseType() {
        return noiseType;
    }

    public void setFractalType(FractalType fractalType) {
        this.fractalType = fractalType;
    }

    public FractalType getFractalType() {
        return fractalType;
    }

    public void setFractalOctaves(int octaves) {
        this.octaves = Math.max(1, octaves);
        updateFractalBounding();
    }

    public int getFractalOctaves() {
        return octaves;
    }

    public void setFractalLacunarity(float lacunarity) {
        this.lacunarity = lacunarity;
    }

    public void setFractalGain(float gain) {
        this.gain = gain;
        updateFractalBounding();
    }

    public void setCellularDistanceFunction(CellularDistanceFunction fn) {
        this.cellularDistanceFunction = fn;
    }

    public void setCellularReturnType(CellularReturnType returnType) {
        this.cellularReturnType = returnType;
    }

    public void setCellularJitter(float jitterModifier) {
        this.cellularJitterModifier = jitterModifier;
    }

    private void updateFractalBounding() {
        float g = Math.abs(gain);
        float amp = g;
        float ampFractal = 1.0f;
        for (int i = 1; i < octaves; i++) {
            ampFractal += amp;
            amp *= g;
        }
        fractalBounding = 1.0f / ampFractal;
    }

    // =========================================================================
    // Public 2D & 3D Noise Entrypoints
    // =========================================================================

    public float getNoise(float x, float y) {
        x *= frequency;
        y *= frequency;

        return switch (fractalType) {
            case None -> genNoiseSingle2D(seed, x, y);
            case FBm -> genFractalFBm2D(x, y);
            case Ridged -> genFractalRidged2D(x, y);
            case PingPong -> genFractalPingPong2D(x, y);
        };
    }

    public float getNoise(float x, float y, float z) {
        x *= frequency;
        y *= frequency;
        z *= frequency;

        return switch (fractalType) {
            case None -> genNoiseSingle3D(seed, x, y, z);
            case FBm -> genFractalFBm3D(x, y, z);
            case Ridged -> genFractalRidged3D(x, y, z);
            case PingPong -> genFractalPingPong3D(x, y, z);
        };
    }

    // =========================================================================
    // Fractal Combinators
    // =========================================================================

    private float genFractalFBm2D(float x, float y) {
        int s = seed;
        float sum = 0.0f;
        float amp = fractalBounding;

        for (int i = 0; i < octaves; i++) {
            float noise = genNoiseSingle2D(s++, x, y);
            sum += noise * amp;
            amp *= lerp(1.0f, Math.min(noise + 1.0f, 2.0f) * 0.5f, weightedStrength);
            x *= lacunarity;
            y *= lacunarity;
            amp *= gain;
        }
        return sum;
    }

    private float genFractalRidged2D(float x, float y) {
        int s = seed;
        float sum = 0.0f;
        float amp = fractalBounding;

        for (int i = 0; i < octaves; i++) {
            float noise = Math.abs(genNoiseSingle2D(s++, x, y));
            sum += (noise * -2.0f + 1.0f) * amp;
            amp *= lerp(1.0f, 1.0f - noise, weightedStrength);
            x *= lacunarity;
            y *= lacunarity;
            amp *= gain;
        }
        return sum;
    }

    private float genFractalPingPong2D(float x, float y) {
        int s = seed;
        float sum = 0.0f;
        float amp = fractalBounding;

        for (int i = 0; i < octaves; i++) {
            float noise = pingPong((genNoiseSingle2D(s++, x, y) + 1.0f) * pingPongStrength);
            sum += (noise - 0.5f) * 2.0f * amp;
            amp *= lerp(1.0f, noise, weightedStrength);
            x *= lacunarity;
            y *= lacunarity;
            amp *= gain;
        }
        return sum;
    }

    private float genFractalFBm3D(float x, float y, float z) {
        int s = seed;
        float sum = 0.0f;
        float amp = fractalBounding;

        for (int i = 0; i < octaves; i++) {
            float noise = genNoiseSingle3D(s++, x, y, z);
            sum += noise * amp;
            amp *= lerp(1.0f, (noise + 1.0f) * 0.5f, weightedStrength);
            x *= lacunarity;
            y *= lacunarity;
            z *= lacunarity;
            amp *= gain;
        }
        return sum;
    }

    private float genFractalRidged3D(float x, float y, float z) {
        int s = seed;
        float sum = 0.0f;
        float amp = fractalBounding;

        for (int i = 0; i < octaves; i++) {
            float noise = Math.abs(genNoiseSingle3D(s++, x, y, z));
            sum += (noise * -2.0f + 1.0f) * amp;
            amp *= lerp(1.0f, 1.0f - noise, weightedStrength);
            x *= lacunarity;
            y *= lacunarity;
            z *= lacunarity;
            amp *= gain;
        }
        return sum;
    }

    private float genFractalPingPong3D(float x, float y, float z) {
        int s = seed;
        float sum = 0.0f;
        float amp = fractalBounding;

        for (int i = 0; i < octaves; i++) {
            float noise = pingPong((genNoiseSingle3D(s++, x, y, z) + 1.0f) * pingPongStrength);
            sum += (noise - 0.5f) * 2.0f * amp;
            amp *= lerp(1.0f, noise, weightedStrength);
            x *= lacunarity;
            y *= lacunarity;
            z *= lacunarity;
            amp *= gain;
        }
        return sum;
    }

    // =========================================================================
    // Single Octave Dispatch
    // =========================================================================

    private float genNoiseSingle2D(int s, float x, float y) {
        return switch (noiseType) {
            case OpenSimplex2, OpenSimplex2S -> singleOpenSimplex2D(s, x, y);
            case Cellular -> singleCellular2D(s, x, y);
            case Perlin -> singlePerlin2D(s, x, y);
            case Value -> singleValue2D(s, x, y);
        };
    }

    private float genNoiseSingle3D(int s, float x, float y, float z) {
        return switch (noiseType) {
            case OpenSimplex2, OpenSimplex2S -> singleOpenSimplex3D(s, x, y, z);
            case Cellular -> singleCellular3D(s, x, y, z);
            case Perlin -> singlePerlin3D(s, x, y, z);
            case Value -> singleValue3D(s, x, y, z);
        };
    }

    // =========================================================================
    // OpenSimplex2 2D & 3D
    // =========================================================================

    private static final float SQRT3 = 1.7320508075688772935274463415059f;
    private static final float F2 = 0.5f * (SQRT3 - 1.0f);
    private static final float G2 = (3.0f - SQRT3) / 6.0f;

    private float singleOpenSimplex2D(int s, float x, float y) {
        float t = (x + y) * F2;
        int i = fastFloor(x + t);
        int j = fastFloor(y + t);

        float xi = x - (i - (i + j) * G2);
        float yi = y - (j - (i + j) * G2);

        int i1 = xi > yi ? 1 : 0;
        int j1 = 1 - i1;

        float x1 = xi - i1 + G2;
        float y1 = yi - j1 + G2;
        float x2 = xi - 1.0f + 2.0f * G2;
        float y2 = yi - 1.0f + 2.0f * G2;

        int ix = i * PRIME_X;
        int jy = j * PRIME_Y;

        float n0 = 0.0f, n1 = 0.0f, n2 = 0.0f;

        float a0 = 0.5f - xi * xi - yi * yi;
        if (a0 > 0.0f) {
            a0 *= a0;
            n0 = a0 * a0 * gradCoord2D(s, ix, jy, xi, yi);
        }

        float a1 = 0.5f - x1 * x1 - y1 * y1;
        if (a1 > 0.0f) {
            a1 *= a1;
            n1 = a1 * a1 * gradCoord2D(s, ix + i1 * PRIME_X, jy + j1 * PRIME_Y, x1, y1);
        }

        float a2 = 0.5f - x2 * x2 - y2 * y2;
        if (a2 > 0.0f) {
            a2 *= a2;
            n2 = a2 * a2 * gradCoord2D(s, ix + PRIME_X, jy + PRIME_Y, x2, y2);
        }

        return Math.clamp(99.83685446303647f * (n0 + n1 + n2), -1.0f, 1.0f);
    }

    private float singleOpenSimplex3D(int s, float x, float y, float z) {
        // 3D BCC lattice OpenSimplex2 evaluation
        float r = (2.0f / 3.0f) * (x + y + z);
        float xr = r - x;
        float yr = r - y;
        float zr = r - z;

        int i = fastRound(xr);
        int j = fastRound(yr);
        int k = fastRound(zr);

        float x0 = xr - i;
        float y0 = yr - j;
        float z0 = zr - k;

        int xNSign = (int) (-1.0f - x0) | 1;
        int yNSign = (int) (-1.0f - y0) | 1;
        int zNSign = (int) (-1.0f - z0) | 1;

        float ax0 = xNSign * -x0;
        float ay0 = yNSign * -y0;
        float az0 = zNSign * -z0;

        int ix = i * PRIME_X;
        int jy = j * PRIME_Y;
        int kz = k * PRIME_Z;

        float value = 0.0f;
        float a = (0.6f - x0 * x0) - (y0 * y0 + z0 * z0);
        for (int l = 0; ; l++) {
            if (a > 0.0f) {
                float a2 = a * a;
                value += a2 * a2 * gradCoord3D(s, ix, jy, kz, x0, y0, z0);
            }

            if (ax0 >= ay0 && ax0 >= az0) {
                float b = a + ax0 + ax0;
                if (b > 1.0f) {
                    b -= 1.0f;
                    float b2 = b * b;
                    value += b2 * b2 * gradCoord3D(s, ix - xNSign * PRIME_X, jy, kz, x0 + xNSign, y0, z0);
                }
            } else if (ay0 > ax0 && ay0 >= az0) {
                float b = a + ay0 + ay0;
                if (b > 1.0f) {
                    b -= 1.0f;
                    float b2 = b * b;
                    value += b2 * b2 * gradCoord3D(s, ix, jy - yNSign * PRIME_Y, kz, x0, y0 + yNSign, z0);
                }
            } else {
                float b = a + az0 + az0;
                if (b > 1.0f) {
                    b -= 1.0f;
                    float b2 = b * b;
                    value += b2 * b2 * gradCoord3D(s, ix, jy, kz - zNSign * PRIME_Z, x0, y0, z0 + zNSign);
                }
            }

            if (l == 1) break;

            ax0 = 0.5f - ax0;
            ay0 = 0.5f - ay0;
            az0 = 0.5f - az0;

            x0 = xNSign * ax0;
            y0 = yNSign * ay0;
            z0 = zNSign * az0;

            a += (0.75f - ax0) - (ay0 + az0);

            ix += (xNSign >> 1) & PRIME_X;
            jy += (yNSign >> 1) & PRIME_Y;
            kz += (zNSign >> 1) & PRIME_Z;

            xNSign = -xNSign;
            yNSign = -yNSign;
            zNSign = -zNSign;
            s = ~s;
        }

        return Math.clamp(32.69428253173828125f * value, -1.0f, 1.0f);
    }

    // =========================================================================
    // Perlin & Value 2D / 3D
    // =========================================================================

    private float singlePerlin2D(int s, float x, float y) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);

        float xd0 = x - x0;
        float yd0 = y - y0;
        float xd1 = xd0 - 1.0f;
        float yd1 = yd0 - 1.0f;

        float xs = quintic(xd0);
        float ys = quintic(yd0);

        x0 *= PRIME_X;
        y0 *= PRIME_Y;
        int x1 = x0 + PRIME_X;
        int y1 = y0 + PRIME_Y;

        float xf0 = lerp(gradCoord2D(s, x0, y0, xd0, yd0), gradCoord2D(s, x1, y0, xd1, yd0), xs);
        float xf1 = lerp(gradCoord2D(s, x0, y1, xd0, yd1), gradCoord2D(s, x1, y1, xd1, yd1), xs);

        return lerp(xf0, xf1, ys) * 1.4247691104677813f;
    }

    private float singlePerlin3D(int s, float x, float y, float z) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);
        int z0 = fastFloor(z);

        float xd0 = x - x0;
        float yd0 = y - y0;
        float zd0 = z - z0;
        float xd1 = xd0 - 1.0f;
        float yd1 = yd0 - 1.0f;
        float zd1 = zd0 - 1.0f;

        float xs = quintic(xd0);
        float ys = quintic(yd0);
        float zs = quintic(zd0);

        x0 *= PRIME_X;
        y0 *= PRIME_Y;
        z0 *= PRIME_Z;
        int x1 = x0 + PRIME_X;
        int y1 = y0 + PRIME_Y;
        int z1 = z0 + PRIME_Z;

        float xf00 = lerp(gradCoord3D(s, x0, y0, z0, xd0, yd0, zd0), gradCoord3D(s, x1, y0, z0, xd1, yd0, zd0), xs);
        float xf10 = lerp(gradCoord3D(s, x0, y1, z0, xd0, yd1, zd0), gradCoord3D(s, x1, y1, z0, xd1, yd1, zd0), xs);
        float xf01 = lerp(gradCoord3D(s, x0, y0, z1, xd0, yd0, zd1), gradCoord3D(s, x1, y0, z1, xd1, yd0, zd1), xs);
        float xf11 = lerp(gradCoord3D(s, x0, y1, z1, xd0, yd1, zd1), gradCoord3D(s, x1, y1, z1, xd1, yd1, zd1), xs);

        float yf0 = lerp(xf00, xf10, ys);
        float yf1 = lerp(xf01, xf11, ys);

        return lerp(yf0, yf1, zs) * 0.964921414852142333984375f;
    }

    private float singleValue2D(int s, float x, float y) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);

        float xs = hermite(x - x0);
        float ys = hermite(y - y0);

        x0 *= PRIME_X;
        y0 *= PRIME_Y;
        int x1 = x0 + PRIME_X;
        int y1 = y0 + PRIME_Y;

        float xf0 = lerp(valCoord2D(s, x0, y0), valCoord2D(s, x1, y0), xs);
        float xf1 = lerp(valCoord2D(s, x0, y1), valCoord2D(s, x1, y1), xs);

        return lerp(xf0, xf1, ys);
    }

    private float singleValue3D(int s, float x, float y, float z) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);
        int z0 = fastFloor(z);

        float xs = hermite(x - x0);
        float ys = hermite(y - y0);
        float zs = hermite(z - z0);

        x0 *= PRIME_X;
        y0 *= PRIME_Y;
        z0 *= PRIME_Z;
        int x1 = x0 + PRIME_X;
        int y1 = y0 + PRIME_Y;
        int z1 = z0 + PRIME_Z;

        float xf00 = lerp(valCoord3D(s, x0, y0, z0), valCoord3D(s, x1, y0, z0), xs);
        float xf10 = lerp(valCoord3D(s, x0, y1, z0), valCoord3D(s, x1, y1, z0), xs);
        float xf01 = lerp(valCoord3D(s, x0, y0, z1), valCoord3D(s, x1, y0, z1), xs);
        float xf11 = lerp(valCoord3D(s, x0, y1, z1), valCoord3D(s, x1, y1, z1), xs);

        float yf0 = lerp(xf00, xf10, ys);
        float yf1 = lerp(xf01, xf11, ys);

        return lerp(yf0, yf1, zs);
    }

    // =========================================================================
    // Cellular (Worley) 2D & 3D
    // =========================================================================

    private float singleCellular2D(int s, float x, float y) {
        int xr = fastRound(x);
        int yr = fastRound(y);

        float distance0 = Float.MAX_VALUE;
        float distance1 = Float.MAX_VALUE;
        int closestHash = 0;

        float cellularJitter = 0.43701595f * cellularJitterModifier;

        int xPrimed = (xr - 1) * PRIME_X;
        int yPrimedBase = (yr - 1) * PRIME_Y;

        for (int xi = xr - 1; xi <= xr + 1; xi++) {
            int yPrimed = yPrimedBase;
            for (int yi = yr - 1; yi <= yr + 1; yi++) {
                int hash = hash2D(s, xPrimed, yPrimed);
                int idx = hash & (255 << 1);

                float vecX = (xi - x) + RAND_VECS_2D[idx] * cellularJitter;
                float vecY = (yi - y) + RAND_VECS_2D[idx | 1] * cellularJitter;

                float newDistance = computeCellularDist2D(vecX, vecY);
                if (newDistance < distance0) {
                    distance1 = distance0;
                    distance0 = newDistance;
                    closestHash = hash;
                } else if (newDistance < distance1) {
                    distance1 = newDistance;
                }
                yPrimed += PRIME_Y;
            }
            xPrimed += PRIME_X;
        }

        return formatCellularReturn(distance0, distance1, closestHash);
    }

    private float singleCellular3D(int s, float x, float y, float z) {
        int xr = fastRound(x);
        int yr = fastRound(y);
        int zr = fastRound(z);

        float distance0 = Float.MAX_VALUE;
        float distance1 = Float.MAX_VALUE;
        int closestHash = 0;

        float cellularJitter = 0.39614353f * cellularJitterModifier;

        int xPrimed = (xr - 1) * PRIME_X;
        int yPrimedBase = (yr - 1) * PRIME_Y;
        int zPrimedBase = (zr - 1) * PRIME_Z;

        for (int xi = xr - 1; xi <= xr + 1; xi++) {
            int yPrimed = yPrimedBase;
            for (int yi = yr - 1; yi <= yr + 1; yi++) {
                int zPrimed = zPrimedBase;
                for (int zi = zr - 1; zi <= zr + 1; zi++) {
                    int hash = hash3D(s, xPrimed, yPrimed, zPrimed);
                    int idx = (hash & 255) * 3;

                    float vecX = (xi - x) + RAND_VECS_3D[idx] * cellularJitter;
                    float vecY = (yi - y) + RAND_VECS_3D[idx + 1] * cellularJitter;
                    float vecZ = (zi - z) + RAND_VECS_3D[idx + 2] * cellularJitter;

                    float newDistance = computeCellularDist3D(vecX, vecY, vecZ);
                    if (newDistance < distance0) {
                        distance1 = distance0;
                        distance0 = newDistance;
                        closestHash = hash;
                    } else if (newDistance < distance1) {
                        distance1 = newDistance;
                    }
                    zPrimed += PRIME_Z;
                }
                yPrimed += PRIME_Y;
            }
            xPrimed += PRIME_X;
        }

        return formatCellularReturn(distance0, distance1, closestHash);
    }

    private float computeCellularDist2D(float vx, float vy) {
        return switch (cellularDistanceFunction) {
            case Euclidean, EuclideanSq -> vx * vx + vy * vy;
            case Manhattan -> Math.abs(vx) + Math.abs(vy);
            case Hybrid -> (Math.abs(vx) + Math.abs(vy)) + (vx * vx + vy * vy);
        };
    }

    private float computeCellularDist3D(float vx, float vy, float vz) {
        return switch (cellularDistanceFunction) {
            case Euclidean, EuclideanSq -> vx * vx + vy * vy + vz * vz;
            case Manhattan -> Math.abs(vx) + Math.abs(vy) + Math.abs(vz);
            case Hybrid -> (Math.abs(vx) + Math.abs(vy) + Math.abs(vz)) + (vx * vx + vy * vy + vz * vz);
        };
    }

    private float formatCellularReturn(float distance0, float distance1, int closestHash) {
        if (cellularDistanceFunction == CellularDistanceFunction.Euclidean
                && cellularReturnType != CellularReturnType.CellValue) {
            distance0 = (float) Math.sqrt(distance0);
            if (cellularReturnType != CellularReturnType.Distance) {
                distance1 = (float) Math.sqrt(distance1);
            }
        }

        return switch (cellularReturnType) {
            case CellValue -> closestHash * (1.0f / 2147483648.0f);
            case Distance -> distance0 - 1.0f;
            case Distance2 -> distance1 - 1.0f;
            case Distance2Add -> (distance1 + distance0) * 0.5f - 1.0f;
            case Distance2Sub -> distance1 - distance0 - 1.0f;
            case Distance2Mul -> distance1 * distance0 * 0.5f - 1.0f;
            case Distance2Div -> distance0 / Math.max(1e-5f, distance1) - 1.0f;
            case CellWallDistance -> distance1 - distance0;
        };
    }

    // =========================================================================
    // Primes, Hashing, Gradients & Lookup Tables
    // =========================================================================

    private static final int PRIME_X = 501125321;
    private static final int PRIME_Y = 1136930381;
    private static final int PRIME_Z = 1720413743;

    private static int fastFloor(float f) {
        return f >= 0.0f ? (int) f : (int) f - 1;
    }

    private static int fastRound(float f) {
        return f >= 0.0f ? (int) (f + 0.5f) : (int) (f - 0.5f);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    private static float hermite(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static float quintic(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float pingPong(float t) {
        t -= (int) (t * 0.5f) * 2.0f;
        return t < 1.0f ? t : 2.0f - t;
    }

    private static int hash2D(int seed, int xPrimed, int yPrimed) {
        int hash = seed ^ xPrimed ^ yPrimed;
        hash *= 0x27d4eb2d;
        return hash;
    }

    private static int hash3D(int seed, int xPrimed, int yPrimed, int zPrimed) {
        int hash = seed ^ xPrimed ^ yPrimed ^ zPrimed;
        hash *= 0x27d4eb2d;
        return hash;
    }

    private static float valCoord2D(int seed, int xPrimed, int yPrimed) {
        int hash = hash2D(seed, xPrimed, yPrimed);
        hash *= hash;
        hash ^= hash << 19;
        return hash * (1.0f / 2147483648.0f);
    }

    private static float valCoord3D(int seed, int xPrimed, int yPrimed, int zPrimed) {
        int hash = hash3D(seed, xPrimed, yPrimed, zPrimed);
        hash *= hash;
        hash ^= hash << 19;
        return hash * (1.0f / 2147483648.0f);
    }

    private static float gradCoord2D(int seed, int xPrimed, int yPrimed, float xd, float yd) {
        int hash = hash2D(seed, xPrimed, yPrimed);
        hash ^= hash >> 15;
        hash &= 127 << 1;
        return xd * GRADIENTS_2D[hash] + yd * GRADIENTS_2D[hash | 1];
    }

    private static float gradCoord3D(int seed, int xPrimed, int yPrimed, int zPrimed, float xd, float yd, float zd) {
        int hash = hash3D(seed, xPrimed, yPrimed, zPrimed);
        hash ^= hash >> 15;
        hash &= 63;
        int idx = hash * 3;
        return xd * GRADIENTS_3D[idx] + yd * GRADIENTS_3D[idx + 1] + zd * GRADIENTS_3D[idx + 2];
    }

    private static final float[] GRADIENTS_2D = new float[256];
    private static final float[] GRADIENTS_3D = new float[64 * 3];
    private static final float[] RAND_VECS_2D = new float[512];
    private static final float[] RAND_VECS_3D = new float[256 * 3];

    static {
        for (int i = 0; i < 128; i++) {
            double angle = (i / 128.0) * Math.PI * 2.0;
            GRADIENTS_2D[i * 2] = (float) Math.cos(angle);
            GRADIENTS_2D[i * 2 + 1] = (float) Math.sin(angle);
        }

        float[] baseGrad3D = {
            1, 1, 0,  -1, 1, 0,  1, -1, 0,  -1, -1, 0,
            1, 0, 1,  -1, 0, 1,  1, 0, -1,  -1, 0, -1,
            0, 1, 1,  0, -1, 1,  0, 1, -1,  0, -1, -1,
            1, 1, 0,  -1, 1, 0,  0, -1, 1,  0, -1, -1
        };
        for (int i = 0; i < 64; i++) {
            int b = (i & 15) * 3;
            GRADIENTS_3D[i * 3] = baseGrad3D[b];
            GRADIENTS_3D[i * 3 + 1] = baseGrad3D[b + 1];
            GRADIENTS_3D[i * 3 + 2] = baseGrad3D[b + 2];
        }

        // Deterministic unit-circle and unit-sphere jitter tables
        long rng = 0x5DEECE66DL;
        for (int i = 0; i < 256; i++) {
            rng = rng * 6364136223846793005L + 1442695040888963407L;
            double a = ((rng >>> 16) / (double) (1L << 48)) * Math.PI * 2.0;
            RAND_VECS_2D[i * 2] = (float) Math.cos(a);
            RAND_VECS_2D[i * 2 + 1] = (float) Math.sin(a);

            rng = rng * 6364136223846793005L + 1442695040888963407L;
            double z = ((rng >>> 16) / (double) (1L << 48)) * 2.0 - 1.0;
            double r = Math.sqrt(Math.max(0.0, 1.0 - z * z));
            RAND_VECS_3D[i * 3] = (float) (r * Math.cos(a));
            RAND_VECS_3D[i * 3 + 1] = (float) (r * Math.sin(a));
            RAND_VECS_3D[i * 3 + 2] = (float) z;
        }
    }
}
