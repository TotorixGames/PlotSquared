package it.einjojo.plotsquared.bukkit.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

/**
 * Finds entities by region, including entities of chunks that are not loaded.
 * <p>
 * {@link World#getEntities()} only returns entities that are currently loaded. Paper loads entities separately from
 * (and after) the chunk itself, and PlotSquared's chunk coordinator unloads chunks once it is done with them, so a
 * world wide scan misses entities that were just copied away. Going through {@link Chunk#getEntities()} loads the
 * chunk and waits for its entities.
 * <p>
 * Must be called on the main thread.
 */
public final class RegionEntities {

    private RegionEntities() {
    }

    /**
     * Visits every entity inside the given block bounds (inclusive, all heights).
     *
     * @param world    the world
     * @param minX     minimum block x
     * @param minZ     minimum block z
     * @param maxX     maximum block x
     * @param maxZ     maximum block z
     * @param consumer receives every entity inside the bounds
     * @return the number of chunks whose entities were not loaded before this call
     */
    public static int forEach(
            final World world,
            final int minX,
            final int minZ,
            final int maxX,
            final int maxZ,
            final Consumer<Entity> consumer
    ) {
        int notLoaded = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                if (!chunk.isEntitiesLoaded()) {
                    notLoaded++;
                }
                for (Entity entity : chunk.getEntities()) {
                    Location location = entity.getLocation();
                    if (location.getX() >= minX && location.getX() < maxX + 1
                            && location.getZ() >= minZ && location.getZ() < maxZ + 1) {
                        consumer.accept(entity);
                    }
                }
            }
        }
        return notLoaded;
    }

}
