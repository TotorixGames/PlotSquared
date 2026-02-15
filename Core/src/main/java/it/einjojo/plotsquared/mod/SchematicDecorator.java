package it.einjojo.plotsquared.mod;

import com.plotsquared.core.location.Location;
import com.plotsquared.core.plot.PlotId;
import com.plotsquared.core.queue.ZeroedDelegateScopedQueueCoordinator;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Decorates plots with schematics from multiple categories (trees, stones, etc.).
 * Optimized for chunk-based world generation with deterministic placement.
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
        File baseDir = new File("plugins/PlotSquared/pfoliage");

        // Trees: ~25% spawn chance, max 2 per plot, no translation (placed on surface)
        categories.add(SchematicCategory.load(
                new File(baseDir, "tree"),
                64, 2,
                PlacementTranslation.NONE
        ));

        // Stones: ~40% spawn chance, max 4 per plot, embedded 2-4 blocks into ground
        categories.add(SchematicCategory.load(
                new File(baseDir, "stone"),
                102, 4,
                PlacementTranslation.embedInGround(2, 4)
        ));

        int totalSchematics = categories.stream().mapToInt(SchematicCategory::size).sum();
        log.info("SchematicDecorator initialized with {} categories, {} total schematics",
                categories.size(), totalSchematics);
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
            if (category.isEmpty()) continue;

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
                    pasteSchematic(result, placement, chunkMinX, chunkMinZ);
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
            if (schematic == null) continue;

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
     * Pastes the portion of a schematic that intersects the current chunk.
     * Blocks replace existing terrain (important for embedded stones).
     */
    private void pasteSchematic(
            ZeroedDelegateScopedQueueCoordinator result,
            PlacementInfo placement,
            int chunkMinX, int chunkMinZ
    ) {
        LoadedSchematic schem = placement.schematic;
        int anchorX = placement.anchorX;
        int anchorZ = placement.anchorZ;
        int baseY = placement.baseY;

        int schemMinX = anchorX - schem.origin().getX();
        int schemMinZ = anchorZ - schem.origin().getZ();

        BlockVector3 clipboardMin = schem.minPoint();
        int originY = schem.origin().getY();

        // Calculate intersection bounds to minimize iterations
        int startDx = Math.max(0, chunkMinX - schemMinX);
        int endDx = Math.min(schem.width(), chunkMinX + 16 - schemMinX);
        int startDz = Math.max(0, chunkMinZ - schemMinZ);
        int endDz = Math.min(schem.length(), chunkMinZ + 16 - schemMinZ);

        for (int dx = startDx; dx < endDx; dx++) {
            int localX = schemMinX + dx - chunkMinX;

            for (int dz = startDz; dz < endDz; dz++) {
                int localZ = schemMinZ + dz - chunkMinZ;

                for (int dy = 0; dy < schem.height(); dy++) {
                    BlockVector3 schemPos = clipboardMin.add(dx, dy, dz);
                    BaseBlock block = schem.clipboard().getFullBlock(schemPos);

                    if (!block.getBlockType().getMaterial().isAir()) {
                        int worldY = baseY + dy - originY;
                        result.setBlock(localX, worldY, localZ, block);
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
        final int anchorX;
        final int anchorZ;
        final int baseY;
        final int minX, maxX, minZ, maxZ;

        PlacementInfo(LoadedSchematic schematic, int anchorX, int anchorZ, int baseY) {
            this.schematic = schematic;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
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
