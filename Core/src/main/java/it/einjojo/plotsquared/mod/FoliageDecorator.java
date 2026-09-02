package it.einjojo.plotsquared.mod;

import com.plotsquared.core.queue.QueueCoordinator;
import com.plotsquared.core.queue.ZeroedDelegateScopedQueueCoordinator;
import com.plotsquared.core.util.BlockUtil;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Places a weighted vegetation layer one block above the plot floor.
 *
 * <p>Selection is a pure function of the world coordinates, so the same column always resolves to the same block.
 * This is what allows {@link com.plotsquared.core.generator.HybridPlotManager} to reproduce exactly what
 * {@link com.plotsquared.core.generator.HybridGen} generated when a plot is cleared or a road is merged away.
 * A WorldEdit {@code RandomPattern} cannot be used for this: it draws from an unseeded {@link java.util.Random}
 * and ignores the position it is given.</p>
 */
public final class FoliageDecorator {

    private static final Logger LOGGER = LogManager.getLogger(FoliageDecorator.class);

    private static final String PALETTE =
            "60%air,2%oak_leaves[persistent=true],20%short_grass,0.5%dead_bush,0.2%azalea,1%azure_bluet,0.5%dark_oak_sapling,3%fern";

    /**
     * Percentages are scaled by this factor so fractional weights such as {@code 0.2%} stay exact integers.
     */
    private static final int WEIGHT_SCALE = 1000;

    private static final int PRIME_X = 73856093;
    private static final int PRIME_Z = 19349663;
    private static final int BASE_SEED = 0x9E3779B9;

    private volatile Palette palette;

    /**
     * Resolves the foliage block for a column.
     *
     * @param worldX absolute world x
     * @param worldZ absolute world z
     * @return the block to place, or {@code null} when this column stays empty
     */
    public @Nullable BaseBlock foliageAt(int worldX, int worldZ) {
        Palette current = palette();
        if (current.totalWeight() <= 0) {
            return null;
        }
        int roll = Math.floorMod(hash(worldX, worldZ), current.totalWeight());
        int[] cumulative = current.cumulative();
        for (int i = 0; i < cumulative.length; i++) {
            if (roll < cumulative[i]) {
                return current.blocks()[i];
            }
        }
        return null;
    }

    /**
     * Chunk-scoped variant used during world generation.
     */
    public void decorate(
            @NonNull ZeroedDelegateScopedQueueCoordinator result,
            int chunkLocalX,
            int chunkLocalZ,
            int worldX,
            int worldZ,
            int plotHeight
    ) {
        BaseBlock block = foliageAt(worldX, worldZ);
        if (block != null) {
            result.setBlock(chunkLocalX, plotHeight + 1, chunkLocalZ, block);
        }
    }

    /**
     * Absolute-coordinate variant used when a plot is cleared or a road is merged away. Bounds are inclusive.
     */
    public void decorateRegion(
            @NonNull QueueCoordinator queue,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int plotHeight
    ) {
        decorateRegion(queue, minX, minZ, maxX, maxZ, plotHeight, null);
    }

    /**
     * Absolute-coordinate variant that leaves out columns rejected by {@code filter}. Bounds are inclusive.
     *
     * @param filter decides per column whether foliage may be placed, or {@code null} to place it everywhere
     */
    public void decorateRegion(
            @NonNull QueueCoordinator queue,
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int plotHeight,
            @Nullable ColumnFilter filter
    ) {
        int y = plotHeight + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (filter != null && !filter.allows(x, z)) {
                    continue;
                }
                BaseBlock block = foliageAt(x, z);
                if (block != null) {
                    queue.setBlock(x, y, z, block);
                }
            }
        }
    }

    /**
     * Decides for a single column whether it may receive foliage. Used to keep foliage off columns that something
     * else already occupies, such as the plot schematic.
     */
    @FunctionalInterface
    public interface ColumnFilter {

        /**
         * @param worldX absolute world x
         * @param worldZ absolute world z
         * @return {@code true} when this column may receive foliage
         */
        boolean allows(int worldX, int worldZ);

    }

    private Palette palette() {
        Palette current = this.palette;
        if (current != null) {
            return current;
        }
        // Compiled lazily: the block registry is not necessarily populated when this decorator is constructed.
        synchronized (this) {
            if (this.palette == null) {
                this.palette = compile();
            }
            return this.palette;
        }
    }

    private static Palette compile() {
        List<BaseBlock> blocks = new ArrayList<>();
        List<Integer> cumulative = new ArrayList<>();
        int total = 0;
        for (String entry : splitEntries(PALETTE)) {
            int split = entry.indexOf('%');
            if (split < 0) {
                LOGGER.warn("Ignoring foliage palette entry without a weight: {}", entry);
                continue;
            }
            int weight;
            try {
                weight = (int) Math.round(Double.parseDouble(entry.substring(0, split).trim()) * WEIGHT_SCALE);
            } catch (NumberFormatException e) {
                LOGGER.warn("Ignoring foliage palette entry with an unparsable weight: {}", entry);
                continue;
            }
            if (weight <= 0) {
                continue;
            }
            String id = entry.substring(split + 1).trim();
            BlockState state = BlockUtil.get(id);
            if (state == null) {
                LOGGER.warn("Ignoring unknown block in foliage palette: {}", id);
                continue;
            }
            total += weight;
            // Air entries keep their weight - they are what makes the layer sparse - but place nothing.
            blocks.add(state.getBlockType().getMaterial().isAir() ? null : state.toBaseBlock());
            cumulative.add(total);
        }
        int[] cumulativeWeights = new int[cumulative.size()];
        for (int i = 0; i < cumulativeWeights.length; i++) {
            cumulativeWeights[i] = cumulative.get(i);
        }
        return new Palette(blocks.toArray(new BaseBlock[0]), cumulativeWeights, total);
    }

    /**
     * Splits the palette on commas, ignoring commas inside a block state such as {@code oak_leaves[persistent=true]}.
     */
    private static List<String> splitEntries(String palette) {
        List<String> entries = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < palette.length(); i++) {
            char c = palette.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth == 0) {
                entries.add(palette.substring(start, i));
                start = i + 1;
            }
        }
        entries.add(palette.substring(start));
        return entries;
    }

    private static int hash(int x, int z) {
        int hash = x * PRIME_X;
        hash ^= z * PRIME_Z;
        hash += BASE_SEED;
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        hash ^= hash >>> 13;
        hash *= 0xc2b2ae35;
        hash ^= hash >>> 16;
        return hash;
    }

    /**
     * @param blocks      one entry per palette slot, {@code null} where the slot is air
     * @param cumulative  running weight totals, parallel to {@code blocks}
     * @param totalWeight sum of all weights
     */
    private record Palette(BaseBlock[] blocks, int[] cumulative, int totalWeight) {

    }

}
