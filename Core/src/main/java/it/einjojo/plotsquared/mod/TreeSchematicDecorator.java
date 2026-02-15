package it.einjojo.plotsquared.mod;

import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.PlotId;
import com.plotsquared.core.queue.ZeroedDelegateScopedQueueCoordinator;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

public final class TreeSchematicDecorator {

    private static final Logger log = LogManager.getLogger(TreeSchematicDecorator.class);

    // Hash primes for deterministic placement
    private static final int PRIME_X = 73856093;
    private static final int PRIME_Z = 19349663;
    private static final int TREE_SEED = 98765;

    private final Clipboard treeSchematic;
    private final int schemWidth;
    private final int schemHeight;
    private final int schemLength;
    private final BlockVector3 schemOrigin;

    public TreeSchematicDecorator() {
        Clipboard temp = null;
        int width = 0, height = 0, length = 0;
        BlockVector3 origin = BlockVector3.ZERO;

        try {
            File schematicFile = new File("plugins/FastAsyncWorldEdit/schematics/baum.schem");
            if (schematicFile.exists()) {
                try (FileInputStream fis = new FileInputStream(schematicFile);
                     ClipboardReader reader = ClipboardFormats.findByFile(schematicFile).getReader(fis)) {
                    temp = reader.read();
                    width = temp.getDimensions().getX();
                    height = temp.getDimensions().getY();
                    length = temp.getDimensions().getZ();
                    origin = temp.getOrigin().subtract(temp.getMinimumPoint());
                    log.info("Tree schematic loaded: {}x{}x{} (origin offset: {})", width, height, length, origin);
                }
            } else {
                log.warn("No schematic baum.schem file found at {}", schematicFile.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Failed to load tree schematic", e);
        }

        this.treeSchematic = temp;
        this.schemWidth = width;
        this.schemHeight = height;
        this.schemLength = length;
        this.schemOrigin = origin;
    }

    /**
     * Called once per chunk during generation.
     * Determines which trees intersect this chunk and places the relevant blocks.
     */
    public void decorateChunk(
            ZeroedDelegateScopedQueueCoordinator result,
            int plotBottomX,
            int plotBottomZ,
            int plotTopX,
            int plotTopZ,
            int plotHeight,
            PlotId plotId
    ) {
        if (treeSchematic == null) {
            return;
        }

        Location chunkMin = result.getMin();
        int chunkMinX = chunkMin.getX();
        int chunkMinZ = chunkMin.getZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        // Calculate plot dimensions (inner area, excluding walls)
        int plotWidth = plotTopX - plotBottomX + 1;
        int plotLength = plotTopZ - plotBottomZ + 1;

        // Check if schematic can fit at all
        if (schemWidth > plotWidth || schemLength > plotLength) {
            return;
        }

        // Calculate valid anchor range within plot
        // Anchor is where schematic origin lands
        int minAnchorX = plotBottomX + schemOrigin.getX();
        int maxAnchorX = plotTopX - (schemWidth - 1 - schemOrigin.getX());
        int minAnchorZ = plotBottomZ + schemOrigin.getZ();
        int maxAnchorZ = plotTopZ - (schemLength - 1 - schemOrigin.getZ());

        if (minAnchorX > maxAnchorX || minAnchorZ > maxAnchorZ) {
            return;
        }

        // Get deterministic tree positions for this plot
        Set<long[]> treePositions = getTreePositionsForPlot(
                plotId, minAnchorX, maxAnchorX, minAnchorZ, maxAnchorZ
        );

        // For each tree, check if it intersects this chunk and paste relevant blocks
        for (long[] pos : treePositions) {
            int anchorX = (int) pos[0];
            int anchorZ = (int) pos[1];

            // Calculate schematic world bounds
            int schemMinX = anchorX - schemOrigin.getX();
            int schemMaxX = schemMinX + schemWidth - 1;
            int schemMinZ = anchorZ - schemOrigin.getZ();
            int schemMaxZ = schemMinZ + schemLength - 1;

            // Check if schematic intersects this chunk
            boolean intersectsX = schemMaxX >= chunkMinX && schemMinX <= chunkMaxX;
            boolean intersectsZ = schemMaxZ >= chunkMinZ && schemMinZ <= chunkMaxZ;

            if (!intersectsX || !intersectsZ) {
                continue;
            }


            // Paste the intersecting portion
            pasteSchematicIntersection(
                    result,
                    anchorX, anchorZ,
                    plotHeight + 1,
                    chunkMinX, chunkMinZ
            );
        }
    }

    /**
     * Generates deterministic tree positions for a plot based on PlotId hash.
     * Trees are spaced apart and distributed pseudo-randomly within valid anchor range.
     */
    private Set<long[]> getTreePositionsForPlot(
            PlotId plotId,
            int minAnchorX, int maxAnchorX,
            int minAnchorZ, int maxAnchorZ
    ) {
        Set<long[]> positions = new HashSet<>();

        int anchorRangeX = maxAnchorX - minAnchorX + 1;
        int anchorRangeZ = maxAnchorZ - minAnchorZ + 1;

        if (anchorRangeX <= 0 || anchorRangeZ <= 0) {
            return positions;
        }

        // Hash plot ID for deterministic seed
        int plotHash = hashPlotId(plotId);

        // Calculate tree spacing based on schematic size
        // For small plots, use tighter spacing to ensure at least one tree
        int spacingX = Math.max(schemWidth + 2, 8);
        int spacingZ = Math.max(schemLength + 2, 8);

        // Calculate grid of potential tree positions
        int treesX = Math.max(1, anchorRangeX / spacingX);
        int treesZ = Math.max(1, anchorRangeZ / spacingZ);

        for (int gx = 0; gx < treesX; gx++) {
            for (int gz = 0; gz < treesZ; gz++) {
                // Hash grid cell for this tree
                int cellHash = hashCoords(gx, gz, plotHash);

                // ~25% chance to place tree in this cell (64/256)
                // This creates natural variation: some plots have trees, some don't
                if ((cellHash & 0xFF) > 64) {
                    continue;
                }

                // Calculate base position for this grid cell
                int baseX = minAnchorX + (gx * spacingX);
                int baseZ = minAnchorZ + (gz * spacingZ);

                // Add jitter within cell (but stay within valid range)
                int maxJitterX = Math.max(1, Math.min(spacingX / 2, anchorRangeX - (gx * spacingX)));
                int maxJitterZ = Math.max(1, Math.min(spacingZ / 2, anchorRangeZ - (gz * spacingZ)));
                int jitterX = Math.abs((cellHash >> 8) & 0x7) % maxJitterX;
                int jitterZ = Math.abs((cellHash >> 16) & 0x7) % maxJitterZ;

                int treeX = Math.min(baseX + jitterX, maxAnchorX);
                int treeZ = Math.min(baseZ + jitterZ, maxAnchorZ);

                positions.add(new long[]{treeX, treeZ});
            }
        }

        return positions;
    }

    /**
     * Pastes only the portion of the schematic that intersects the current chunk.
     */
    private void pasteSchematicIntersection(
            ZeroedDelegateScopedQueueCoordinator result,
            int anchorX, int anchorZ,
            int baseY,
            int chunkMinX, int chunkMinZ
    ) {
        int schemMinX = anchorX - schemOrigin.getX();
        int schemMinZ = anchorZ - schemOrigin.getZ();

        // Get clipboard's minimum point for reading blocks
        BlockVector3 clipboardMin = treeSchematic.getMinimumPoint();

        for (int dx = 0; dx < schemWidth; dx++) {
            int worldX = schemMinX + dx;
            int localX = worldX - chunkMinX;

            // Skip if outside chunk X bounds
            if (localX < 0 || localX >= 16) {
                continue;
            }

            for (int dz = 0; dz < schemLength; dz++) {
                int worldZ = schemMinZ + dz;
                int localZ = worldZ - chunkMinZ;

                // Skip if outside chunk Z bounds
                if (localZ < 0 || localZ >= 16) {
                    continue;
                }

                for (int dy = 0; dy < schemHeight; dy++) {
                    // Read from clipboard using absolute coordinates (min + offset)
                    BlockVector3 schemPos = clipboardMin.add(dx, dy, dz);
                    BaseBlock block = treeSchematic.getFullBlock(schemPos);

                    if (!block.getBlockType().getMaterial().isAir()) {
                        int worldY = baseY + dy - schemOrigin.getY();
                        result.setBlock(localX, worldY, localZ, block);
                    }
                }
            }
        }
    }

    private int hashPlotId(PlotId plotId) {
        int hash = plotId.getX() * PRIME_X;
        hash ^= plotId.getY() * PRIME_Z;
        hash += TREE_SEED;
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        return hash;
    }

    private int hashCoords(int x, int z, int seed) {
        int hash = x * PRIME_X;
        hash ^= z * PRIME_Z;
        hash += seed;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        return hash;
    }

}
