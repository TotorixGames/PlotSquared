package it.einjojo.plotsquared.bukkit.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

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

    /**
     * Metadata PlotSquared sets on entities it is temporarily teleporting; they must survive region operations.
     */
    private static final String TEMPORARY_TELEPORT_METADATA = "ps-tmp-teleport";

    private RegionEntities() {
    }

    /**
     * Whether a region operation may remove the entity: everything except players and entities PlotSquared is
     * currently teleporting.
     */
    public static boolean isRemovable(final Entity entity) {
        return !(entity instanceof Player) && !entity.hasMetadata(TEMPORARY_TELEPORT_METADATA);
    }

    /**
     * Whether a location lies inside the given block bounds (inclusive, all heights). A location inside block
     * {@code maxX} may have any fraction, e.g. {@code maxX + 0.9}.
     */
    public static boolean contains(
            final Location location,
            final int minX,
            final int minZ,
            final int maxX,
            final int maxZ
    ) {
        return location.getX() >= minX && location.getX() < maxX + 1
                && location.getZ() >= minZ && location.getZ() < maxZ + 1;
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
                forEachInChunk(chunk, minX, minZ, maxX, maxZ, consumer);
            }
        }
        return notLoaded;
    }

    /**
     * Visits the entities of one chunk that lie inside the given block bounds.
     */
    public static void forEachInChunk(
            final Chunk chunk,
            final int minX,
            final int minZ,
            final int maxX,
            final int maxZ,
            final Consumer<Entity> consumer
    ) {
        for (Entity entity : chunk.getEntities()) {
            if (contains(entity.getLocation(), minX, minZ, maxX, maxZ)) {
                consumer.accept(entity);
            }
        }
    }

}
