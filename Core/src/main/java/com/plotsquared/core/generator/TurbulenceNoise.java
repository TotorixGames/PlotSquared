package com.plotsquared.core.generator;

/**
 * Fast binary noise generator using hash-based turbulence.
 * Produces deterministic 0/1 patterns for world decoration.
 *
 * Why hash-based: Avoids expensive floating-point math while maintaining spatial coherence.
 * Why binary: Simplifies decision-making (place vs skip) without threshold tuning.
 */
public final class TurbulenceNoise {

    // Prime multipliers create pseudo-random distribution without correlation
    private static final int PRIME_X = 1619;
    private static final int PRIME_Z = 31337;
    private static final int PRIME_SEED = 6971;

    private final int seed;
    private final int scale;

    /**
     * @param seed  Global seed for reproducibility across server restarts
     * @param scale Larger values = more scattered patterns (every Nth block considered)
     */
    public TurbulenceNoise(int seed, int scale) {
        this.seed = seed;
        this.scale = Math.max(1, scale);
    }

    /**
     * Evaluates noise at world coordinates.
     *
     * @return 1 if grass should spawn, 0 otherwise
     */
    public int sample(int worldX, int worldZ) {
        // Scale reduces sampling frequency, creating clustered patterns
        int sx = Math.floorDiv(worldX, scale);
        int sz = Math.floorDiv(worldZ, scale);

        // Hash coordinates with primes to break grid alignment
        int hash = sx * PRIME_X;
        hash ^= sz * PRIME_Z;
        hash += seed * PRIME_SEED;

        // Avalanche bits to improve distribution
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;

        // Extract binary result from least significant bit
        // Creates ~50% density naturally balanced
        return hash & 1;
    }

}

