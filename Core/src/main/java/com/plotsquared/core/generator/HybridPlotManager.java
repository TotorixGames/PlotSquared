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
package com.plotsquared.core.generator;

import com.google.common.collect.Sets;
import com.plotsquared.core.PlotSquared;
import com.plotsquared.core.command.Template;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.inject.factory.ProgressSubscriberFactory;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotAreaTerrainType;
import com.plotsquared.core.plot.PlotAreaType;
import com.plotsquared.core.plot.PlotId;
import com.plotsquared.core.queue.QueueCoordinator;
import com.plotsquared.core.util.FileBytes;
import com.plotsquared.core.util.FileUtils;
import com.plotsquared.core.util.MathMan;
import com.plotsquared.core.util.RegionManager;
import com.plotsquared.core.util.WorldUtil;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.einjojo.plotsquared.mod.FoliageDecorator;
import it.einjojo.plotsquared.mod.SchematicDecorator;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Objects;

public class HybridPlotManager extends ClassicPlotManager {

    public static boolean REGENERATIVE_CLEAR = true;

    private final HybridPlotWorld hybridPlotWorld;
    private final ProgressSubscriberFactory subscriberFactory;
    private final FoliageDecorator foliageDecorator;
    private final SchematicDecorator schematicDecorator;

    public HybridPlotManager(
            final @NonNull HybridPlotWorld hybridPlotWorld,
            final @NonNull RegionManager regionManager,
            @NonNull ProgressSubscriberFactory subscriberFactory,
            final @NonNull FoliageDecorator foliageDecorator,
            final @NonNull SchematicDecorator schematicDecorator
    ) {
        super(hybridPlotWorld, regionManager);
        this.hybridPlotWorld = hybridPlotWorld;
        this.subscriberFactory = subscriberFactory;
        this.foliageDecorator = foliageDecorator;
        this.schematicDecorator = schematicDecorator;
    }

    @Override
    public void exportTemplate() throws IOException {
        HashSet<FileBytes> files = Sets.newHashSet(new FileBytes(
                Settings.Paths.TEMPLATES + "/tmp-data.yml",
                Template.getBytes(hybridPlotWorld)
        ));
        String dir =
                Settings.Paths.SCHEMATICS + File.separator + "GEN_ROAD_SCHEMATIC" + File.separator + hybridPlotWorld.getWorldName() + File.separator;
        try {
            File sideRoad = FileUtils.getFile(PlotSquared.platform().getDirectory(), dir + "sideroad.schem");
            String newDir =
                    Settings.Paths.SCHEMATICS + File.separator + "GEN_ROAD_SCHEMATIC" + File.separator + "__TEMP_DIR__" + File.separator;
            if (sideRoad.exists()) {
                files.add(new FileBytes(newDir + "sideroad.schem", Files.readAllBytes(sideRoad.toPath())));
            }
            File intersection = FileUtils.getFile(PlotSquared.platform().getDirectory(), dir + "intersection.schem");
            if (intersection.exists()) {
                files.add(new FileBytes(newDir + "intersection.schem", Files.readAllBytes(intersection.toPath())));
            }
            File plot = FileUtils.getFile(PlotSquared.platform().getDirectory(), dir + "plot.schem");
            if (plot.exists()) {
                files.add(new FileBytes(newDir + "plot.schem", Files.readAllBytes(plot.toPath())));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Template.zipAll(hybridPlotWorld.getWorldName(), files);
    }

    @Override
    public boolean createRoadEast(final @NonNull Plot plot, @Nullable QueueCoordinator queue) {
        boolean enqueue = false;
        if (queue == null) {
            queue = hybridPlotWorld.getQueue();
            enqueue = true;
        }
        super.createRoadEast(plot, queue);
        PlotId id = plot.getId();
        PlotId id2 = PlotId.of(id.getX() + 1, id.getY());
        Location bot = getPlotBottomLocAbs(id2);
        Location top = getPlotTopLocAbs(id);
        Location pos1 = Location.at(
                hybridPlotWorld.getWorldName(),
                top.getX() + 1,
                hybridPlotWorld.getMinGenHeight(),
                bot.getZ() - 1
        );
        Location pos2 = Location.at(
                hybridPlotWorld.getWorldName(),
                bot.getX(),
                hybridPlotWorld.getMaxGenHeight(),
                top.getZ() + 1
        );
        this.resetBiome(hybridPlotWorld, pos1, pos2);
        if (!hybridPlotWorld.ROAD_SCHEMATIC_ENABLED) {
            return true;
        }
        createSchemAbs(queue, pos1, pos2, true);
        return !enqueue || queue.enqueue();
    }

    private void resetBiome(
            final @NonNull HybridPlotWorld hybridPlotWorld,
            final @NonNull Location pos1,
            final @NonNull Location pos2
    ) {
        BiomeType biome = hybridPlotWorld.getPlotBiome();
        if (!Objects.equals(PlotSquared.platform().worldUtil()
                .getBiomeSynchronous(
                        hybridPlotWorld.getWorldName(),
                        (pos1.getX() + pos2.getX()) / 2,
                        (pos1.getZ() + pos2.getZ()) / 2
                ), biome)) {
            WorldUtil.setBiome(
                    hybridPlotWorld.getWorldName(),
                    new CuboidRegion(pos1.getBlockVector3(), pos2.getBlockVector3()),
                    biome
            );
        }
    }

    private void createSchemAbs(
            final @NonNull QueueCoordinator queue,
            final @NonNull Location pos1,
            final @NonNull Location pos2,
            boolean isRoad
    ) {
        int size = hybridPlotWorld.SIZE;
        int minY;
        if ((isRoad && Settings.Schematics.PASTE_ROAD_ON_TOP) || (!isRoad && Settings.Schematics.PASTE_ON_TOP)) {
            minY = hybridPlotWorld.SCHEM_Y;
        } else {
            minY = hybridPlotWorld.getMinBuildHeight();
        }
        int schemYDiff = (isRoad ? hybridPlotWorld.getRoadYStart() : hybridPlotWorld.getPlotYStart()) - minY;
        BaseBlock airBlock = BlockTypes.AIR.getDefaultState().toBaseBlock();
        for (int x = pos1.getX(); x <= pos2.getX(); x++) {
            short absX = (short) ((x - hybridPlotWorld.ROAD_OFFSET_X) % size);
            if (absX < 0) {
                absX += size;
            }
            for (int z = pos1.getZ(); z <= pos2.getZ(); z++) {
                short absZ = (short) ((z - hybridPlotWorld.ROAD_OFFSET_Z) % size);
                if (absZ < 0) {
                    absZ += size;
                }
                BaseBlock[] blocks = hybridPlotWorld.G_SCH.get(MathMan.pair(absX, absZ));
                if (blocks != null) {
                    for (int y = 0; y < blocks.length; y++) {
                        if (blocks[y] != null) {
                            queue.setBlock(x, minY + y, z, blocks[y]);
                        } else if (y > schemYDiff) {
                            // This is necessary, otherwise any blocks not specified in the schematic will remain after a clear.
                            // This should only be done where the schematic has actually "started"
                            queue.setBlock(x, minY + y, z, airBlock);
                        } else if (isRoad) {
                            queue.setBlock(x, minY + y, z, hybridPlotWorld.ROAD_BLOCK.toPattern());
                        } else {
                            queue.setBlock(x, minY + y, z, hybridPlotWorld.MAIN_BLOCK.toPattern());
                        }
                    }
                }
                BiomeType biome = hybridPlotWorld.G_SCH_B.get(MathMan.pair(absX, absZ));
                if (biome != null) {
                    queue.setBiome(x, z, biome);
                } else {
                    queue.setBiome(x, z, hybridPlotWorld.getPlotBiome());
                }
            }
        }
    }

    @Override
    public boolean createRoadSouth(final @NonNull Plot plot, @Nullable QueueCoordinator queue) {
        boolean enqueue = false;
        if (queue == null) {
            enqueue = true;
            queue = hybridPlotWorld.getQueue();
        }
        super.createRoadSouth(plot, queue);
        PlotId id = plot.getId();
        PlotId id2 = PlotId.of(id.getX(), id.getY() + 1);
        Location bot = getPlotBottomLocAbs(id2);
        Location top = getPlotTopLocAbs(id);
        Location pos1 = Location.at(
                hybridPlotWorld.getWorldName(),
                bot.getX() - 1,
                hybridPlotWorld.getMinGenHeight(),
                top.getZ() + 1
        );
        Location pos2 = Location.at(
                hybridPlotWorld.getWorldName(),
                top.getX() + 1,
                hybridPlotWorld.getMaxGenHeight(),
                bot.getZ()
        );
        this.resetBiome(hybridPlotWorld, pos1, pos2);
        if (!hybridPlotWorld.ROAD_SCHEMATIC_ENABLED) {
            return true;
        }
        createSchemAbs(queue, pos1, pos2, true);
        return !enqueue || queue.enqueue();
    }

    @Override
    public boolean createRoadSouthEast(final @NonNull Plot plot, @Nullable QueueCoordinator queue) {
        boolean enqueue = false;
        if (queue == null) {
            enqueue = true;
            queue = hybridPlotWorld.getQueue();
        }
        super.createRoadSouthEast(plot, queue);
        PlotId id = plot.getId();
        PlotId id2 = PlotId.of(id.getX() + 1, id.getY() + 1);
        Location pos1 = getPlotTopLocAbs(id).add(1, 0, 1);
        Location pos2 = getPlotBottomLocAbs(id2);
        createSchemAbs(queue, pos1, pos2, true);
        if (hybridPlotWorld.ROAD_SCHEMATIC_ENABLED) {
            createSchemAbs(queue, pos1, pos2, true);
        }
        return !enqueue || queue.enqueue();
    }

    @Override
    public boolean clearPlot(
            final @NonNull Plot plot,
            final @Nullable Runnable whenDone,
            @Nullable PlotPlayer<?> actor,
            @Nullable QueueCoordinator queue
    ) {
        // The clear is deliberately not handed to RegionManager#handleClear (FastAsyncWorldEdit): its delegate does not
        // know about FoliageDecorator/SchematicDecorator and would leave a bare plot behind. The blocks are still
        // written through the QueueCoordinator, which FAWE provides, so the clear stays fast.
        final Location pos1 = plot.getBottomAbs();
        final Location pos2 = plot.getExtendedTopAbs();
        // If augmented
        final boolean canRegen =
                (hybridPlotWorld.getType() == PlotAreaType.AUGMENTED) && (hybridPlotWorld.getTerrain() != PlotAreaTerrainType.NONE) && REGENERATIVE_CLEAR;
        // The component blocks
        final Pattern plotfloor = hybridPlotWorld.TOP_BLOCK.toPattern();
        final Pattern filling = hybridPlotWorld.MAIN_BLOCK.toPattern();

        final Pattern bedrock;
        if (hybridPlotWorld.PLOT_BEDROCK) {
            bedrock = BlockTypes.BEDROCK.getDefaultState();
        } else {
            bedrock = hybridPlotWorld.MAIN_BLOCK.toPattern();
        }

        final BiomeType biome = hybridPlotWorld.getPlotBiome();
        boolean enqueue = false;
        if (queue == null) {
            enqueue = true;
            queue = hybridPlotWorld.getQueue();
        }
        if (actor != null && Settings.QUEUE.NOTIFY_PROGRESS) {
            queue.addProgressSubscriber(subscriberFactory.createWithActor(actor));
        }
        if (whenDone != null) {
            queue.setCompleteTask(whenDone);
        }
        if (!canRegen) {
            if (hybridPlotWorld.getMinBuildHeight() < hybridPlotWorld.getMinGenHeight()) {
                queue.setCuboid(
                        pos1.withY(hybridPlotWorld.getMinBuildHeight()),
                        pos2.withY(hybridPlotWorld.getMinGenHeight()),
                        BlockTypes.AIR.getDefaultState()
                );
            }
            queue.setCuboid(
                    pos1.withY(hybridPlotWorld.getMinGenHeight()),
                    pos2.withY(hybridPlotWorld.getMinGenHeight()),
                    hybridPlotWorld.PLOT_BEDROCK ? bedrock : filling
            );
            // Each component has a different layer
            queue.setCuboid(
                    pos1.withY(hybridPlotWorld.getMinGenHeight() + 1),
                    pos2.withY(hybridPlotWorld.PLOT_HEIGHT - 1),
                    filling
            );
            queue.setCuboid(pos1.withY(hybridPlotWorld.PLOT_HEIGHT), pos2.withY(hybridPlotWorld.PLOT_HEIGHT), plotfloor);
            queue.setCuboid(
                    pos1.withY(hybridPlotWorld.PLOT_HEIGHT + 1),
                    pos2.withY(hybridPlotWorld.getMaxGenHeight()),
                    BlockTypes.AIR.getDefaultState()
            );
            if (hybridPlotWorld.getMaxGenHeight() < hybridPlotWorld.getMaxBuildHeight() - 1) {
                queue.setCuboid(
                        pos1.withY(hybridPlotWorld.getMaxGenHeight()),
                        pos2.withY(hybridPlotWorld.getMaxBuildHeight() - 1),
                        BlockTypes.AIR.getDefaultState()
                );
            }
            queue.setBiomeCuboid(pos1, pos2, biome);
        } else {
            queue.setRegenRegion(new CuboidRegion(pos1.getBlockVector3(), pos2.getBlockVector3()));
        }
        pastePlotSchematic(queue, pos1, pos2);
        if (!canRegen) {
            // A regenerated region is rebuilt by the generator, which applies the decorators itself.
            decorateClearedPlot(queue, plot, pos1, pos2);
        }
        return !enqueue || queue.enqueue();
    }

    public void pastePlotSchematic(
            final @NonNull QueueCoordinator queue,
            final @NonNull Location bottom,
            final @NonNull Location top
    ) {
        if (!hybridPlotWorld.PLOT_SCHEMATIC) {
            return;
        }
        createSchemAbs(queue, bottom, top, false);
    }

    /**
     * Re-applies the deterministic decorators that {@link HybridGen} uses while generating a plot, so a cleared plot
     * ends up looking exactly like a freshly generated one.
     *
     * @param pos1 minimum corner of the area {@link #clearPlot} has just rebuilt
     * @param pos2 maximum corner of that area
     */
    private void decorateClearedPlot(
            final @NonNull QueueCoordinator queue,
            final @NonNull Plot plot,
            final @NonNull Location pos1,
            final @NonNull Location pos2
    ) {
        // Foliage covers everything that was rebuilt, including the seam towards a still merged neighbour. Columns the
        // plot schematic occupies are left out, because HybridGen pastes that schematic over the foliage as well.
        this.foliageDecorator.decorateRegion(
                queue,
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getZ(), pos2.getZ()),
                hybridPlotWorld.PLOT_HEIGHT,
                hybridPlotWorld.PLOT_SCHEMATIC ? this::plotSchematicLeavesFoliageColumnFree : null
        );
        // Schematics are anchored to the plot cell, exactly as in HybridGen#generateChunk.
        Location bottom = getPlotBottomLocAbs(plot.getId());
        Location top = getPlotTopLocAbs(plot.getId());
        this.schematicDecorator.decoratePlot(
                queue,
                bottom.getX(),
                bottom.getZ(),
                top.getX(),
                top.getZ(),
                hybridPlotWorld.PLOT_HEIGHT,
                plot.getId()
        );
    }

    /**
     * Whether the plot schematic leaves the foliage layer of a column empty. {@link #createSchemAbs} fills everything
     * above the schematic with air, so foliage has to be written after it - but only where the schematic itself does
     * not place a block, which is the same precedence {@link HybridGen#generateChunk} produces.
     *
     * @param x absolute world x
     * @param z absolute world z
     */
    private boolean plotSchematicLeavesFoliageColumnFree(final int x, final int z) {
        int size = hybridPlotWorld.SIZE;
        short relativeX = (short) Math.floorMod(x - hybridPlotWorld.ROAD_OFFSET_X, size);
        short relativeZ = (short) Math.floorMod(z - hybridPlotWorld.ROAD_OFFSET_Z, size);
        BaseBlock[] blocks = hybridPlotWorld.G_SCH.get(MathMan.pair(relativeX, relativeZ));
        if (blocks == null) {
            return true;
        }
        int minY = Settings.Schematics.PASTE_ON_TOP ? hybridPlotWorld.SCHEM_Y : hybridPlotWorld.getMinBuildHeight();
        int index = hybridPlotWorld.PLOT_HEIGHT + 1 - minY;
        return index < 0 || index >= blocks.length || blocks[index] == null;
    }

    /**
     * Applies foliage to a road strip that has just become plot surface through a merge. Bounds are inclusive and
     * must match the floor {@link ClassicPlotManager} writes in the corresponding {@code removeRoad} method.
     * <p>
     * Schematics are deliberately not placed here: {@link SchematicDecorator} anchors them inside the plot cell, so
     * merging and unlinking never change the schematic layout of the plots involved.
     */
    private void decorateReclaimedRoad(
            final @NonNull QueueCoordinator queue,
            final int minX,
            final int minZ,
            final int maxX,
            final int maxZ
    ) {
        this.foliageDecorator.decorateRegion(queue, minX, minZ, maxX, maxZ, hybridPlotWorld.PLOT_HEIGHT);
    }

    @Override
    public boolean removeRoadEast(final @NonNull Plot plot, @Nullable QueueCoordinator queue) {
        boolean enqueue = false;
        if (queue == null) {
            queue = hybridPlotWorld.getQueue();
            enqueue = true;
        }
        super.removeRoadEast(plot, queue);

        Location bottom = getPlotBottomLocAbs(plot.getId());
        Location top = getPlotTopLocAbs(plot.getId());
        decorateReclaimedRoad(
                queue,
                top.getX() + 1,
                bottom.getZ(),
                top.getX() + hybridPlotWorld.ROAD_WIDTH,
                top.getZ()
        );
        return !enqueue || queue.enqueue();
    }

    @Override
    public boolean removeRoadSouth(final @NonNull Plot plot, @Nullable QueueCoordinator queue) {
        boolean enqueue = false;
        if (queue == null) {
            queue = hybridPlotWorld.getQueue();
            enqueue = true;
        }
        super.removeRoadSouth(plot, queue);

        Location bottom = getPlotBottomLocAbs(plot.getId());
        Location top = getPlotTopLocAbs(plot.getId());
        decorateReclaimedRoad(
                queue,
                bottom.getX(),
                top.getZ() + 1,
                top.getX(),
                top.getZ() + hybridPlotWorld.ROAD_WIDTH
        );
        return !enqueue || queue.enqueue();
    }

    @Override
    public boolean removeRoadSouthEast(final @NonNull Plot plot, @Nullable QueueCoordinator queue) {
        boolean enqueue = false;
        if (queue == null) {
            queue = hybridPlotWorld.getQueue();
            enqueue = true;
        }
        super.removeRoadSouthEast(plot, queue);

        Location top = getPlotTopLocAbs(plot.getId());
        decorateReclaimedRoad(
                queue,
                top.getX() + 1,
                top.getZ() + 1,
                top.getX() + hybridPlotWorld.ROAD_WIDTH,
                top.getZ() + hybridPlotWorld.ROAD_WIDTH
        );
        return !enqueue || queue.enqueue();
    }

    /**
     * Retrieves the location of where a sign should be for a plot.
     *
     * @param plot The plot
     * @return The location where a sign should be
     */
    @Override
    public Location getSignLoc(final @NonNull Plot plot) {
        return hybridPlotWorld.getSignLocation(plot);
    }

    public HybridPlotWorld getHybridPlotWorld() {
        return this.hybridPlotWorld;
    }

}
