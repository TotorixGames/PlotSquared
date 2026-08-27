/*
 * PlotSquared, a land and world management plugin for Minecraft.
 * Copyright (C) IntellectualSites <https://intellectualsites.com>
 * Copyright (C) IntellectualSites team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package it.einjojo.plotsquared.mod;

/**
 * Fast binary noise generator using hash-based turbulence.
 * Produces deterministic 0/1 patterns for world decoration.
 * <p>
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
