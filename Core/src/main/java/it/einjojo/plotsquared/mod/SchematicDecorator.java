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

import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.PlotId;
import com.plotsquared.core.queue.QueueCoordinator;
import com.plotsquared.core.queue.ZeroedDelegateScopedQueueCoordinator;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import it.einjojo.plotsquared.mod.schematic.LoadedSchematic;
import it.einjojo.plotsquared.mod.schematic.PlacementTranslation;
import it.einjojo.plotsquared.mod.schematic.SchematicCategory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorates plots with schematics from multiple categories (trees, stones, etc.).
 * Optimized for chunk-based world generation with deterministic placement.
 * <p>
 */
public final class SchematicDecorator {

    private static final Logger log = LogManager.getLogger(SchematicDecorator.class);

    // Hash primes for deterministic placement
    private static final int PRIME_X = 73856093;
    private static final int PRIME_Z = 19349663;
    private static final int PRIME_CATEGORY = 83492791;
    private static final int PRIME_TRANSLATION = 48611;
    private static final int BASE_SEED = 98765;

    private final List<SchematicCategory> categories;

    public SchematicDecorator() {
        this.categories = new ArrayList<>();
        loadCategories();
    }

    private void loadCategories() {
        File baseDir = new File("plugins/PlotSquared/schematics");

        categories.add(SchematicCategory.load(
                new File(baseDir, "busch"),
                128, 4, // 38% +1/3 chance (96 -> 128)
                PlacementTranslation.NONE
        ));

        categories.add(SchematicCategory.load(
                new File(baseDir, "haus"),
                30, 1, // ~11% spawn chance
                PlacementTranslation.NONE
        ));

        categories.add(SchematicCategory.load(
                new File(baseDir, "hill"),
                38, 1, // ~15% spawn chance
                PlacementTranslation.NONE
        ));

        categories.add(SchematicCategory.load(
                new File(baseDir, "kleinkram"),
                30, 2, // ~11% spawn chance
                PlacementTranslation.NONE
        ));
        categories.add(SchematicCategory.load(
                new File(baseDir, "stein"),
                101, 4, // 30% +1/3 chance (76 -> 101)
                PlacementTranslation.NONE
        ));
        categories.add(SchematicCategory.load(
                new File(baseDir, "stein_medium"),
                51, 3, // Stones: ~20% spawn chance
                PlacementTranslation.NONE
        ));

        categories.add(SchematicCategory.load(
                new File(baseDir, "stein_big"),
                25, 2, // Stones: ~10% spawn chance
                PlacementTranslation.NONE
        ));

        categories.add(SchematicCategory.load(
                new File(baseDir, "steinkreis"),
                16, 1, // 6%
                PlacementTranslation.NONE
        ));

        categories.add(SchematicCategory.load(
                new File(baseDir, "tree"),
                64, 3, // 25%
                PlacementTranslation.NONE
        ));

        categories.add(SchematicCategory.load(
                new File(baseDir, "wall"),
                51, 2, // 20%
                PlacementTranslation.NONE
        ));


        int totalSchematics = categories.stream().mapToInt(SchematicCategory::size).sum();
        log.info(
                "SchematicDecorator initialized with {} categories, {} total schematics",
                categories.size(), totalSchematics
        );
    }

    /**
     * Decorates a chunk with schematics for a specific plot.
     * Called once per plot-chunk intersection during world generation.
     */
    public void decorateChunk(
            ZeroedDelegateScopedQueueCoordinator result,
            int plotBottomX, int plotBottomZ,
            int plotTopX, int plotTopZ,
            int plotHeight,
            PlotId plotId
    ) {
        decorateChunk(result, plotBottomX, plotBottomZ, plotTopX, plotTopZ, plotHeight, plotId, false);
    }

    public void decorateChunk(
            ZeroedDelegateScopedQueueCoordinator result,
            int plotBottomX, int plotBottomZ,
            int plotTopX, int plotTopZ,
            int plotHeight,
            PlotId plotId,
            boolean populatingOnly
    ) {
        Location chunkMin = result.getMin();
        int chunkMinX = chunkMin.getX();
        int chunkMinZ = chunkMin.getZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        int plotWidth = plotTopX - plotBottomX + 1;
        int plotLength = plotTopZ - plotBottomZ + 1;

        // Process each category
        for (int catIndex = 0; catIndex < categories.size(); catIndex++) {
            SchematicCategory category = categories.get(catIndex);
            if (category.isEmpty()) {
                continue;
            }

            // Early rejection if no schematic can fit
            if (category.maxWidth() > plotWidth || category.maxLength() > plotLength) {
                continue;
            }

            // Generate placements for this category
            List<PlacementInfo> placements = generatePlacements(
                    plotId, category, catIndex,
                    plotBottomX, plotBottomZ, plotTopX, plotTopZ,
                    plotHeight
            );

            // Place schematics that intersect this chunk
            for (PlacementInfo placement : placements) {
                if (placement.intersectsChunk(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)) {
                    pastePlacement(
                            result, placement,
                            chunkMinX, chunkMinZ,
                            chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ,
                            populatingOnly
                    );
                }
            }
        }
    }

    /**
     * Generates deterministic placement positions for a category within a plot.
     */
    private List<PlacementInfo> generatePlacements(
            PlotId plotId, SchematicCategory category, int categoryIndex,
            int plotBottomX, int plotBottomZ, int plotTopX, int plotTopZ,
            int plotHeight
    ) {
        List<PlacementInfo> placements = new ArrayList<>();

        int plotHash = hashPlotId(plotId, categoryIndex);
        int maxPlacements = category.maxPerPlot();
        PlacementTranslation translation = category.translation();

        for (int i = 0; i < maxPlacements; i++) {
            int instanceHash = hashInstance(plotHash, i);

            // Check spawn chance
            if ((instanceHash & 0xFF) >= category.spawnChance()) {
                continue;
            }

            // Select schematic
            LoadedSchematic schematic = category.select(instanceHash >>> 8);
            if (schematic == null) {
                continue;
            }

            // Calculate valid anchor range for this specific schematic
            int minAnchorX = plotBottomX + schematic.origin().getX();
            int maxAnchorX = plotTopX - (schematic.width() - 1 - schematic.origin().getX());
            int minAnchorZ = plotBottomZ + schematic.origin().getZ();
            int maxAnchorZ = plotTopZ - (schematic.length() - 1 - schematic.origin().getZ());

            if (minAnchorX > maxAnchorX || minAnchorZ > maxAnchorZ) {
                continue;
            }

            // Calculate position within valid range
            int rangeX = maxAnchorX - minAnchorX + 1;
            int rangeZ = maxAnchorZ - minAnchorZ + 1;

            int anchorX = minAnchorX + (Math.abs(instanceHash >>> 16) % rangeX);
            int anchorZ = minAnchorZ + (Math.abs(instanceHash >>> 24 ^ instanceHash) % rangeZ);

            // Calculate Y with translation (if any) - optimized to skip hash when no translation
            int baseY = plotHeight + 1;
            if (translation.hasTranslation()) {
                int translationHash = hashTranslation(instanceHash);
                baseY += translation.calculateOffset(translationHash);
            }

            placements.add(new PlacementInfo(schematic, anchorX, anchorZ, baseY));
        }

        return placements;
    }

    /**
     * Decorates a whole plot in absolute world coordinates, e.g. after the plot has been cleared.
     * <p>
     * Placements come from the same {@link #generatePlacements} call the chunk based generation uses, so the plot
     * ends up with exactly the schematics it was generated with. A placement is always fully contained in the plot
     * (see the anchor range in {@code generatePlacements}), so no clipping against the plot bounds is needed.
     */
    public void decoratePlot(
            QueueCoordinator queue,
            int plotBottomX, int plotBottomZ,
            int plotTopX, int plotTopZ,
            int plotHeight,
            PlotId plotId
    ) {
        int plotWidth = plotTopX - plotBottomX + 1;
        int plotLength = plotTopZ - plotBottomZ + 1;

        for (int catIndex = 0; catIndex < categories.size(); catIndex++) {
            SchematicCategory category = categories.get(catIndex);
            if (category.isEmpty()) {
                continue;
            }

            // Early rejection if no schematic can fit
            if (category.maxWidth() > plotWidth || category.maxLength() > plotLength) {
                continue;
            }

            List<PlacementInfo> placements = generatePlacements(
                    plotId, category, catIndex,
                    plotBottomX, plotBottomZ, plotTopX, plotTopZ,
                    plotHeight
            );

            for (PlacementInfo placement : placements) {
                pastePlacement(
                        queue, placement,
                        0, 0,
                        placement.minX, placement.minZ, placement.maxX, placement.maxZ,
                        false
                );
            }
        }
    }

    /**
     * Pastes the part of a placement that lies within the given inclusive clip bounds.
     * Blocks replace existing terrain (important for embedded stones).
     *
     * @param offsetX subtracted from the world x before writing: the chunk minimum for a
     *                {@link ZeroedDelegateScopedQueueCoordinator}, {@code 0} for a queue in world coordinates
     * @param offsetZ subtracted from the world z before writing
     */
    private void pastePlacement(
            QueueCoordinator queue,
            PlacementInfo placement,
            int offsetX, int offsetZ,
            int clipMinX, int clipMinZ,
            int clipMaxX, int clipMaxZ,
            boolean populatingOnly
    ) {
        LoadedSchematic schem = placement.schematic;
        int schemMinX = placement.minX;
        int schemMinZ = placement.minZ;
        int baseY = placement.baseY;

        BlockVector3 clipboardMin = schem.minPoint();
        int originY = schem.origin().getY();

        // Calculate intersection bounds to minimize iterations
        int startDx = Math.max(0, clipMinX - schemMinX);
        int endDx = Math.min(schem.width(), clipMaxX + 1 - schemMinX);
        int startDz = Math.max(0, clipMinZ - schemMinZ);
        int endDz = Math.min(schem.length(), clipMaxZ + 1 - schemMinZ);

        for (int dx = startDx; dx < endDx; dx++) {
            int x = schemMinX + dx - offsetX;

            for (int dz = startDz; dz < endDz; dz++) {
                int z = schemMinZ + dz - offsetZ;

                for (int dy = 0; dy < schem.height(); dy++) {
                    BlockVector3 schemPos = clipboardMin.add(dx, dy, dz);
                    BaseBlock block = schem.clipboard().getFullBlock(schemPos);

                    if (!block.getBlockType().getMaterial().isAir() && (!populatingOnly || block.hasNbtData())) {
                        queue.setBlock(x, baseY + dy - originY, z, block);
                    }
                }
            }
        }
    }

    private int hashPlotId(PlotId plotId, int categoryIndex) {
        int hash = plotId.getX() * PRIME_X;
        hash ^= plotId.getY() * PRIME_Z;
        hash += categoryIndex * PRIME_CATEGORY;
        hash += BASE_SEED;
        hash ^= hash >>> 16;
        hash *= 0x85ebca6b;
        return hash;
    }

    private int hashInstance(int plotHash, int instanceIndex) {
        int hash = plotHash ^ (instanceIndex * PRIME_X);
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        return hash;
    }

    private int hashTranslation(int instanceHash) {
        return instanceHash * PRIME_TRANSLATION;
    }

    /**
     * Holds placement info with precomputed bounds for fast chunk intersection.
     */
    private static final class PlacementInfo {

        final LoadedSchematic schematic;
        final int baseY;
        final int minX, maxX, minZ, maxZ;

        PlacementInfo(LoadedSchematic schematic, int anchorX, int anchorZ, int baseY) {
            this.schematic = schematic;
            this.baseY = baseY;

            // Precompute world bounds
            this.minX = anchorX - schematic.origin().getX();
            this.maxX = minX + schematic.width() - 1;
            this.minZ = anchorZ - schematic.origin().getZ();
            this.maxZ = minZ + schematic.length() - 1;
        }

        boolean intersectsChunk(int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
            return maxX >= chunkMinX && minX <= chunkMaxX &&
                    maxZ >= chunkMinZ && minZ <= chunkMaxZ;
        }

    }

}
