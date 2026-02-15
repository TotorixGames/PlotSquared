package it.einjojo.plotsquared.mod;

import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
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
import java.util.Random;

public final class TreeSchematicDecorator {

    private static final Logger log = LogManager.getLogger(TreeSchematicDecorator.class);
    private final Clipboard treeSchematic;
    private final Random random = new Random();

    // Schematic dimensions (cached for performance)
    private final int schemWidth;
    private final int schemHeight;
    private final int schemLength;

    public TreeSchematicDecorator() {
        Clipboard temp = null;
        int width = 0, height = 0, length = 0;

        try {
            File schematicFile = new File("plugins/FastAsyncWorldEdit/schematics/baum.schem");
            if (schematicFile.exists()) {
                try (FileInputStream fis = new FileInputStream(schematicFile);
                     ClipboardReader reader = ClipboardFormats.findByFile(schematicFile).getReader(fis)) {
                    temp = reader.read();
                    width = temp.getDimensions().getX();
                    height = temp.getDimensions().getY();
                    length = temp.getDimensions().getZ();
                    reader.close();
                    log.info("Schematic file found: {}", schematicFile.getAbsolutePath());
                }


            } else {
                log.warn("No schematic baum.schem file found");
            }
        } catch (Exception e) {
            log.error("Failed to load tree schematic", e);
        }

        this.treeSchematic = temp;
        this.schemWidth = width;
        this.schemHeight = height;
        this.schemLength = length;
    }

    /**
     * Attempts to place a tree schematic if space permits.
     *
     * @return true if schematic was placed, false otherwise
     */
    public boolean tryPlaceTree(
            ZeroedDelegateScopedQueueCoordinator result,
            PlotArea plotArea,
            int worldX,
            int worldZ,
            int plotHeight
    ) {
        // Skip if schematic not loaded
        if (treeSchematic == null) {
            log.info("Skipping tree - No schematic found");
            return false;
        }

        // Random chance (configurable - currently 2%)
//        if (random.nextInt(100) >= 10) {
//            return false;
//        }

        // Get plot at this location
        Plot plot = plotArea.getPlot(
                com.plotsquared.core.location.Location.at(
                        plotArea.getWorldName(),
                        worldX,
                        plotHeight,
                        worldZ
                )
        );

        // Only place inside plots (not on roads/walls)
        if (plot == null) {
            return false;
        }

        // Check if schematic fits within plot boundaries
        if (!canFitSchematic(plot, worldX, worldZ, plotArea)) {
            log.info("Skipping tree at ({}, {}) - Not enough space within plot boundaries", worldX, worldZ);
            return false;
        }

        // Place the schematic
        placeSchematic(result, worldX, worldZ, plotHeight + 1);
        return true;
    }

    /**
     * Validates schematic fits entirely within plot boundaries.
     */
    private boolean canFitSchematic(Plot plot, int worldX, int worldZ, PlotArea area) {
        // Calculate schematic bounding box
        int minX = worldX - treeSchematic.getOrigin().getX();
        int maxX = minX + schemWidth - 1;
        int minZ = worldZ - treeSchematic.getOrigin().getZ();
        int maxZ = minZ + schemLength - 1;

        // Check all corners are within same plot
        for (int checkX : new int[]{minX, maxX}) {
            for (int checkZ : new int[]{minZ, maxZ}) {
                Plot checkPlot = area.getPlot(
                        com.plotsquared.core.location.Location.at(
                                area.getWorldName(),
                                checkX,
                                0,
                                checkZ
                        )
                );

                // Schematic extends outside plot or into different plot
                if (checkPlot == null || !checkPlot.getId().equals(plot.getId())) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Directly pastes schematic blocks into queue.
     */
    private void placeSchematic(
            ZeroedDelegateScopedQueueCoordinator result,
            int worldX,
            int worldZ,
            int baseY
    ) {
        BlockVector3 offset = treeSchematic.getOrigin();
        Location minLoc = result.getMin();
        BlockVector3 min = BlockVector3.at(minLoc.getX(), minLoc.getY(), minLoc.getZ());

        for (int dx = 0; dx < schemWidth; dx++) {
            for (int dy = 0; dy < schemHeight; dy++) {
                for (int dz = 0; dz < schemLength; dz++) {
                    BlockVector3 schematicPos = BlockVector3.at(dx, dy, dz);
                    BaseBlock block = treeSchematic.getFullBlock(schematicPos);
                    if (!block.getBlockType().getMaterial().isAir()) {
                        // Convert world coordinates to chunk-local coordinates
                        int blockWorldX = worldX + dx - offset.getX();
                        int blockWorldY = baseY + dy - offset.getY();
                        int blockWorldZ = worldZ + dz - offset.getZ();

                        int localX = blockWorldX - min.getX();
                        int localZ = blockWorldZ - min.getZ();

                        // Only set if within current chunk bounds
                        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
                            result.setBlock(localX, blockWorldY, localZ, block);
                        }
                    }
                }
            }
        }
    }

}
