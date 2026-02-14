package it.einjojo.plotsquared.mod;

import com.plotsquared.core.plot.BlockBucket;
import com.plotsquared.core.queue.ZeroedDelegateScopedQueueCoordinator;
import com.plotsquared.core.util.PatternUtil;
import com.sk89q.worldedit.function.pattern.Pattern;

public final class FoliageDecorator {

    private final Pattern foliagePalette;

    public FoliageDecorator() {
        String paletteString = "60%air,2%oak_wood,6%oak_leaves,20%short_grass,1%dead_bush," +
                "0.2%azalea,1%azure_bluet,0.5%dark_oak_sapling,3%fern";

        BlockBucket bucket = new BlockBucket(paletteString);
        this.foliagePalette = bucket.toPattern();
    }

    public void decorate(
            ZeroedDelegateScopedQueueCoordinator result,
            int chunkLocalX,
            int chunkLocalZ,
            int worldX,
            int worldZ,
            int plotHeight
    ) {
        // WorldEdit patterns use world coordinates for consistent pseudo-random distribution
        // This ensures vegetation doesn't shift between chunks
        var block = PatternUtil.apply(
                foliagePalette,
                worldX,
                plotHeight + 1,
                worldZ
        );

        // Air blocks (60% of palette) are skipped to avoid unnecessary queue operations
        if (!block.getBlockType().getMaterial().isAir()) {
            result.setBlock(chunkLocalX, plotHeight + 1, chunkLocalZ, block);
        }
    }

}


