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

import com.plotsquared.core.queue.ZeroedDelegateScopedQueueCoordinator;
import com.sk89q.worldedit.world.block.BlockTypes;

/**
 * Efficiently places grass decoration on plots using turbulence patterns.
 * <p>
 * Replaced by palettes. Keeping for future reference.
 *
 * @see FoliageDecorator
 */
public final class GrassDecorator {

    private final TurbulenceNoise noise;

    /**
     * @param noise The turbulence pattern to follow
     */
    public GrassDecorator(TurbulenceNoise noise) {
        this.noise = noise;
    }

    /**
     * Places grass on a single plot coordinate if noise permits.
     * <p>
     * Why above PLOT_HEIGHT: Grass belongs on top of terrain, not embedded.
     * Why noise check first: Avoids block placement overhead when skipping.
     *
     * @param result     World modification queue
     * @param x          Chunk-local X coordinate [0-15]
     * @param z          Chunk-local Z coordinate [0-15]
     * @param worldX     Absolute world X for noise sampling
     * @param worldZ     Absolute world Z for noise sampling
     * @param plotHeight The configured plot surface level
     */
    public void decorate(
            ZeroedDelegateScopedQueueCoordinator result,
            int x,
            int z,
            int worldX,
            int worldZ,
            int plotHeight
    ) {
        // Sample noise using world coordinates to maintain pattern continuity across chunks
        if (noise.sample(worldX, worldZ) == 1) {
            // Place one block above surface - grass naturally sits on top
            result.setBlock(x, plotHeight + 1, z, BlockTypes.SHORT_GRASS.getDefaultState());
        }
    }

}

