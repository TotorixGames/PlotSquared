package it.einjojo.plotsquared.mod.schematic;

/**
 * Defines a vertical translation range for schematic placement.
 * Used to embed objects (like stones) into the ground.
 *
 * Immutable and lightweight - no allocations during placement.
 */
public final class PlacementTranslation {

    public static final PlacementTranslation NONE = new PlacementTranslation(0, 0);

    private final int minY;
    private final int maxY;
    private final int range;

    private PlacementTranslation(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
        this.range = maxY - minY + 1;
    }

    /**
     * Creates a translation that shifts placement downward (negative Y).
     *
     * @param minDepth Minimum blocks to shift down (positive value, e.g., 2)
     * @param maxDepth Maximum blocks to shift down (positive value, e.g., 4)
     */
    public static PlacementTranslation embedInGround(int minDepth, int maxDepth) {
        if (minDepth <= 0 && maxDepth <= 0) {
            return NONE;
        }
        // Convert to negative Y offsets
        return new PlacementTranslation(-maxDepth, -minDepth);
    }

    /**
     * Creates a translation with explicit Y offset range.
     */
    public static PlacementTranslation of(int minY, int maxY) {
        if (minY == 0 && maxY == 0) {
            return NONE;
        }
        return new PlacementTranslation(Math.min(minY, maxY), Math.max(minY, maxY));
    }

    /**
     * Calculates the Y offset for a placement using a hash for determinism.
     */
    public int calculateOffset(int hash) {
        if (this == NONE) {
            return 0;
        }
        return minY + (Math.abs(hash) % range);
    }

    public boolean hasTranslation() {
        return this != NONE;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

}

