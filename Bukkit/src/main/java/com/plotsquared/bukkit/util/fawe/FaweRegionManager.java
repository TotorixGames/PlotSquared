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
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class FaweRegionManager extends BukkitRegionManager {

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
            final List<Entity> originals = new ArrayList<>();
            final Consumer<Entity> collector = entity -> {
                if (!(entity instanceof Player)) {
                    originals.add(entity);
                }
            };
            if (world != null && swapWorld != null) {
                RegionEntities.forEach(world, pos1.getX(), pos1.getZ(), pos2.getX(), pos2.getZ(), collector);
                RegionEntities.forEach(
                        swapWorld,
                        swapPos.getX(),
                        swapPos.getZ(),
                        swapPos.getX() + pos2.getX() - pos1.getX(),
                        swapPos.getZ() + pos2.getZ() - pos1.getZ(),
                        collector
                );
            }
            delegate.swap(pos1, pos2, swapPos, () -> {
                int removed = 0;
                for (Entity entity : originals) {
                    if (entity.isValid()) {
                        entity.remove();
                        removed++;
                    }
                }
                if (Settings.Region_Debug.OPERATIONS) {
                    RegionOperationTracer.onSwapOriginalsRemoved(originals.size(), removed);
                }
                if (whenDone != null) {
                    whenDone.run();
                }
            });
        });
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

}
