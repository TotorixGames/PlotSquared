package it.einjojo.plotsquared.bukkit.debug;

import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.util.task.TaskManager;
import com.plotsquared.core.util.task.TaskTime;
import com.sk89q.worldedit.regions.CuboidRegion;
import it.einjojo.plotsquared.bukkit.util.BlockContents;
import it.einjojo.plotsquared.mod.debug.RegionTrace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Console diagnostics for plot move, swap, clear, delete and copy, enabled with
 * {@link Settings.Region_Debug#OPERATIONS}.
 * <p>
 * Every operation gets an id. Its regions are snapshotted per stage (containers, items inside them, entities per type,
 * dropped items), and every entity spawned inside them is logged with the stack trace that spawned it - which tells
 * whether a drop came from PlotSquared's queue, from FastAsyncWorldEdit or from somewhere else.
 */
public final class RegionOperationTracer {

    private static final Logger LOGGER = LogManager.getLogger("PlotSquared/RegionDebug");
    /**
     * Upper bound for traces whose operation never calls {@link RegionTrace#end()}, e.g. because it failed.
     */
    private static final long MAX_TRACE_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int MAX_STACK_FRAMES = 30;
    private static final String[] RELEVANT_FRAMES = {
            "net.minecraft.", "com.fastasyncworldedit.", "com.sk89q.", "com.plotsquared.", "it.einjojo.",
            "org.bukkit.craftbukkit."
    };

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final List<Trace> ACTIVE = new CopyOnWriteArrayList<>();

    private RegionOperationTracer() {
    }

    public static RegionTrace start(final String operation) {
        Trace trace = new Trace(NEXT_ID.getAndIncrement(), operation);
        ACTIVE.add(trace);
        LOGGER.info("[RegionDebug #{} {}] started", trace.id, operation);
        return trace;
    }

    /**
     * Logs a spawned entity if it is inside a watched region.
     */
    public static void onSpawn(final EntitySpawnEvent event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        Location location = event.getLocation();
        for (Trace trace : ACTIVE) {
            if (trace.isExpired()) {
                ACTIVE.remove(trace);
                continue;
            }
            Watched watched = trace.find(location);
            if (watched == null) {
                continue;
            }
            StringBuilder message = new StringBuilder()
                    .append(trace.prefix())
                    .append(" spawn ").append(entity.getType())
                    .append(" in ").append(watched.label)
                    .append(" at ").append(location.getBlockX()).append(',').append(location.getBlockY()).append(',')
                    .append(location.getBlockZ());
            if (entity instanceof Item item) {
                ItemStack stack = item.getItemStack();
                message.append(" item=").append(stack.getAmount()).append('x').append(stack.getType());
            }
            if (event instanceof CreatureSpawnEvent creatureSpawnEvent) {
                message.append(" reason=").append(creatureSpawnEvent.getSpawnReason());
            }
            message.append(" cancelled=").append(event.isCancelled());
            appendStack(message);
            LOGGER.warn(message.toString());
            return;
        }
    }

    /**
     * Logs a block entity that PlotSquared emptied before overwriting it.
     */
    public static void onContentsCleared(final BlockState state, final int items) {
        LOGGER.info(
                "[RegionDebug] emptied {} at {},{},{} ({} items) before overwriting it",
                state.getType(), state.getX(), state.getY(), state.getZ(), items
        );
    }

    /**
     * Logs the outcome of {@link com.plotsquared.bukkit.util.BukkitRegionManager#clearAllEntities}.
     */
    public static void onEntitiesCleared(
            final String world, final int minX, final int minZ, final int maxX, final int maxZ, final int removed,
            final int chunksNotLoaded
    ) {
        LOGGER.info(
                "[RegionDebug] clearAllEntities {} {},{}..{},{}: removed={} chunksWithEntitiesNotLoaded={}",
                world, minX, minZ, maxX, maxZ, removed, chunksNotLoaded
        );
    }

    /**
     * Logs how many original entities a swap removed after FastAsyncWorldEdit pasted their copies.
     */
    public static void onSwapOriginalsRemoved(final int collected, final int removed) {
        LOGGER.info("[RegionDebug] swap: collected {} original entities, removed {} after the swap", collected, removed);
    }

    private static void appendStack(final StringBuilder message) {
        int frames = 0;
        for (StackTraceElement element : new Throwable().getStackTrace()) {
            String className = element.getClassName();
            if (className.startsWith(RegionOperationTracer.class.getPackageName())) {
                continue;
            }
            if (!isRelevant(className)) {
                continue;
            }
            message.append("\n    at ").append(element);
            if (++frames >= MAX_STACK_FRAMES) {
                message.append("\n    ...");
                break;
            }
        }
    }

    private static boolean isRelevant(final String className) {
        for (String prefix : RELEVANT_FRAMES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void runOnMain(final Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            TaskManager.runTask(runnable);
        }
    }

    private record Watched(String label, String world, int minX, int minZ, int maxX, int maxZ) {

        boolean contains(final Location location) {
            return location.getWorld() != null && location.getWorld().getName().equals(world)
                    && location.getX() >= minX && location.getX() < maxX + 1
                    && location.getZ() >= minZ && location.getZ() < maxZ + 1;
        }

        @Override
        public String toString() {
            return label + "(" + world + " " + minX + "," + minZ + ".." + maxX + "," + maxZ + ")";
        }

    }

    private static final class Trace implements RegionTrace {

        private final int id;
        private final String operation;
        private final long startedAt = System.currentTimeMillis();
        private final List<Watched> watched = new CopyOnWriteArrayList<>();
        private volatile long watchUntil = Long.MAX_VALUE;

        private Trace(final int id, final String operation) {
            this.id = id;
            this.operation = operation;
        }

        private String prefix() {
            return "[RegionDebug #" + id + " " + operation + "]";
        }

        private boolean isExpired() {
            long now = System.currentTimeMillis();
            return now > watchUntil || now - startedAt > MAX_TRACE_MILLIS;
        }

        private Watched find(final Location location) {
            for (Watched region : watched) {
                if (region.contains(location)) {
                    return region;
                }
            }
            return null;
        }

        @Override
        public RegionTrace watch(final String label, final String world, final Collection<CuboidRegion> regions) {
            for (CuboidRegion region : new ArrayList<>(regions)) {
                watched.add(new Watched(
                        label,
                        world,
                        region.getMinimumPoint().getX(),
                        region.getMinimumPoint().getZ(),
                        region.getMaximumPoint().getX(),
                        region.getMaximumPoint().getZ()
                ));
            }
            return this;
        }

        @Override
        public void stage(final String stage) {
            // Loading chunks in the middle of an operation would load their entities and hide exactly the kind of
            // problem this is meant to find, so only the stages outside of the operation load chunks.
            snapshot(stage, "before".equals(stage));
        }

        @Override
        public void end() {
            snapshot("end", false);
            int seconds = Math.max(0, Settings.Region_Debug.WATCH_SECONDS);
            watchUntil = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
            TaskManager.runTaskLater(() -> {
                snapshot("settled", true);
                ACTIVE.remove(this);
                LOGGER.info("{} finished after {} ms", prefix(), System.currentTimeMillis() - startedAt);
            }, TaskTime.seconds(seconds));
        }

        private void snapshot(final String stage, final boolean loadChunks) {
            runOnMain(() -> {
                for (Watched region : watched) {
                    LOGGER.info("{} stage={} {}: {}", prefix(), stage, region, count(region, loadChunks));
                }
            });
        }

        private String count(final Watched region, final boolean loadChunks) {
            World world = Bukkit.getWorld(region.world);
            if (world == null) {
                return "world not loaded";
            }
            int containers = 0;
            int containerItems = 0;
            int droppedStacks = 0;
            int droppedItems = 0;
            int chunksSkipped = 0;
            int entitiesNotLoaded = 0;
            Map<EntityType, Integer> entities = new TreeMap<>();
            for (int cx = region.minX >> 4; cx <= region.maxX >> 4; cx++) {
                for (int cz = region.minZ >> 4; cz <= region.maxZ >> 4; cz++) {
                    if (!loadChunks && !world.isChunkLoaded(cx, cz)) {
                        chunksSkipped++;
                        continue;
                    }
                    Chunk chunk = world.getChunkAt(cx, cz);
                    for (BlockState state : chunk.getTileEntities(false)) {
                        int x = state.getX();
                        int z = state.getZ();
                        if (x < region.minX || x > region.maxX || z < region.minZ || z > region.maxZ
                                || !BlockContents.holdsItems(state)) {
                            continue;
                        }
                        containers++;
                        containerItems += BlockContents.count(state);
                    }
                    if (!chunk.isEntitiesLoaded()) {
                        entitiesNotLoaded++;
                        if (!loadChunks) {
                            continue;
                        }
                    }
                    for (Entity entity : chunk.getEntities()) {
                        if (!region.contains(entity.getLocation())) {
                            continue;
                        }
                        if (entity instanceof Item item) {
                            droppedStacks++;
                            droppedItems += item.getItemStack().getAmount();
                        } else {
                            entities.merge(entity.getType(), 1, Integer::sum);
                        }
                    }
                }
            }
            return "containers=" + containers
                    + " containerItems=" + containerItems
                    + " entities=" + entities
                    + " droppedItems=" + droppedItems + " (" + droppedStacks + " stacks)"
                    + " chunksSkipped=" + chunksSkipped
                    + " chunksWithEntitiesNotLoaded=" + entitiesNotLoaded;
        }

    }

}
