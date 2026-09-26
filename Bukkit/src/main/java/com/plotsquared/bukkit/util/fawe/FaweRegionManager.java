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
package com.plotsquared.bukkit.util.fawe;

import com.fastasyncworldedit.bukkit.regions.plotsquared.FaweDelegateRegionManager;
import com.google.inject.Inject;
import com.plotsquared.bukkit.util.BukkitRegionManager;
import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.generator.HybridPlotManager;
import com.plotsquared.core.inject.factory.ProgressSubscriberFactory;
import com.plotsquared.core.location.Location;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.plot.PlotArea;
import com.plotsquared.core.plot.PlotManager;
import com.plotsquared.core.queue.GlobalBlockQueue;
import com.plotsquared.core.queue.QueueCoordinator;
import com.plotsquared.core.util.WorldUtil;
import com.plotsquared.core.util.task.TaskManager;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.biome.BiomeType;
import it.einjojo.plotsquared.bukkit.debug.RegionOperationTracer;
import it.einjojo.plotsquared.bukkit.util.RegionEntities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class FaweRegionManager extends BukkitRegionManager {

    private static final Logger LOGGER = LogManager.getLogger("PlotSquared/" + FaweRegionManager.class.getSimpleName());

    private final FaweDelegateRegionManager delegate = new FaweDelegateRegionManager();

    @Inject
    public FaweRegionManager(WorldUtil worldUtil, GlobalBlockQueue blockQueue, ProgressSubscriberFactory subscriberFactory) {
        super(worldUtil, blockQueue, subscriberFactory);
    }

    @Override
    public boolean setCuboids(
            final @NonNull PlotArea area,
            final @NonNull Set<CuboidRegion> regions,
            final @NonNull Pattern blocks,
            int minY,
            int maxY,
            @Nullable PlotPlayer<?> actor,
            @Nullable QueueCoordinator queue
    ) {
        return delegate.setCuboids(
                area, regions, blocks, minY, maxY,
                Objects.requireNonNullElseGet(queue, area::getQueue).getCompleteTask()
        );
    }

    @Override
    public boolean notifyClear(PlotManager manager) {
        if (!Settings.FAWE_Components.CLEAR || !(manager instanceof HybridPlotManager)) {
            return false;
        }
        return delegate.notifyClear(manager);
    }

    @Override
    public boolean handleClear(
            @NonNull Plot plot,
            @Nullable Runnable whenDone,
            @NonNull PlotManager manager,
            final @Nullable PlotPlayer<?> player
    ) {
        if (!Settings.FAWE_Components.CLEAR || !(manager instanceof HybridPlotManager)) {
            return false;
        }
        return delegate.handleClear(plot, whenDone, manager);
    }

    @Override
    public void swap(
            Location pos1,
            Location pos2,
            Location swapPos,
            final @Nullable PlotPlayer<?> player,
            final Runnable whenDone
    ) {
        // FastAsyncWorldEdit copies the entities of both regions into the other one, but never removes the originals,
        // so every swap duplicates them. Remember the originals and remove them once FAWE is done.
        TaskManager.runTask(() -> {
            final World world = BukkitUtil.getWorld(pos1.getWorldName());
            final World swapWorld = BukkitUtil.getWorld(swapPos.getWorldName());
            if (world == null || swapWorld == null) {
                delegate.swap(pos1, pos2, swapPos, whenDone);
                return;
            }
            final Bounds first = new Bounds(world, pos1.getX(), pos1.getZ(), pos2.getX(), pos2.getZ());
            final Bounds second = new Bounds(
                    swapWorld,
                    swapPos.getX(),
                    swapPos.getZ(),
                    swapPos.getX() + pos2.getX() - pos1.getX(),
                    swapPos.getZ() + pos2.getZ() - pos1.getZ()
            );
            final Set<UUID> firstOriginals = first.removableEntityIds();
            final Set<UUID> secondOriginals = second.removableEntityIds();
            delegate.swap(pos1, pos2, swapPos, () -> {
                removeSwappedOriginals(first, firstOriginals, second, secondOriginals);
                if (whenDone != null) {
                    whenDone.run();
                }
            });
        });
    }

    /**
     * Removes the originals of a FastAsyncWorldEdit swap - but only for a side whose copies demonstrably arrived on the
     * other side. FAWE also completes when the paste failed, and deleting the originals then would lose them for good,
     * whereas keeping them at worst leaves duplicates.
     * <p>
     * The originals are looked up by UUID after loading the regions again: the chunks may have been unloaded while FAWE
     * was working, which would invalidate references taken before the swap.
     */
    private void removeSwappedOriginals(
            final Bounds first,
            final Set<UUID> firstOriginals,
            final Bounds second,
            final Set<UUID> secondOriginals
    ) {
        final List<Entity> found = new ArrayList<>();
        first.forEachRemovable(found::add);
        second.forEachRemovable(found::add);
        final Set<UUID> originals = new HashSet<>(firstOriginals);
        originals.addAll(secondOriginals);
        // Copies FAWE pasted into a region are the entities there that did not exist before the swap
        final int copiesInFirst = (int) found.stream()
                .filter(entity -> !originals.contains(entity.getUniqueId()) && first.contains(entity))
                .count();
        final int copiesInSecond = (int) found.stream()
                .filter(entity -> !originals.contains(entity.getUniqueId()) && second.contains(entity))
                .count();
        final boolean removeFirst = copiesInSecond >= firstOriginals.size();
        final boolean removeSecond = copiesInFirst >= secondOriginals.size();
        int removed = 0;
        for (Entity entity : found) {
            UUID id = entity.getUniqueId();
            if ((removeFirst && firstOriginals.contains(id)) || (removeSecond && secondOriginals.contains(id))) {
                entity.remove();
                removed++;
            }
        }
        if (!removeFirst) {
            LOGGER.warn(
                    "Swap {}: FastAsyncWorldEdit pasted only {} of {} entities, keeping the originals",
                    first, copiesInSecond, firstOriginals.size()
            );
        }
        if (!removeSecond) {
            LOGGER.warn(
                    "Swap {}: FastAsyncWorldEdit pasted only {} of {} entities, keeping the originals",
                    second, copiesInFirst, secondOriginals.size()
            );
        }
        if (Settings.Region_Debug.OPERATIONS) {
            RegionOperationTracer.onSwapOriginalsRemoved(firstOriginals.size() + secondOriginals.size(), removed);
        }
    }

    @Override
    public void setBiome(CuboidRegion region, int extendBiome, BiomeType biome, PlotArea area, Runnable whenDone) {
        delegate.setBiome(region, extendBiome, biome, area.getWorldName(), whenDone);
    }

    @Override
    public boolean copyRegion(
            final @NonNull Location pos1,
            final @NonNull Location pos2,
            final @NonNull Location pos3,
            final @Nullable PlotPlayer<?> player,
            final @NonNull Runnable whenDone
    ) {
        return delegate.copyRegion(pos1, pos2, pos3, whenDone);
    }

    @Override
    public boolean regenerateRegion(final @NotNull Location pos1, final @NotNull Location pos2, boolean ignore, final Runnable whenDone) {
        return delegate.regenerateRegion(pos1, pos2, ignore, whenDone);
    }

    private record Bounds(World world, int minX, int minZ, int maxX, int maxZ) {

        Set<UUID> removableEntityIds() {
            Set<UUID> ids = new HashSet<>();
            forEachRemovable(entity -> ids.add(entity.getUniqueId()));
            return ids;
        }

        void forEachRemovable(final Consumer<Entity> consumer) {
            RegionEntities.forEach(world, minX, minZ, maxX, maxZ, entity -> {
                if (RegionEntities.isRemovable(entity)) {
                    consumer.accept(entity);
                }
            });
        }

        boolean contains(final Entity entity) {
            return entity.getWorld().equals(world) && RegionEntities.contains(entity.getLocation(), minX, minZ, maxX, maxZ);
        }

        @Override
        public String toString() {
            return world.getName() + " " + minX + "," + minZ + ".." + maxX + "," + maxZ;
        }

    }

}
