package it.einjojo.plotsquared.mod.debug;

import com.sk89q.worldedit.regions.CuboidRegion;

import java.util.Collection;

/**
 * Diagnostic handle for one region operation (move, swap, clear, delete, copy).
 * <p>
 * Watched regions are snapshotted at every {@link #stage(String)}: containers and their item count, entities per type
 * and dropped items. While an operation is running (and a short while after {@link #end()}) every entity spawned
 * inside a watched region is logged together with the stack trace that spawned it.
 * <p>
 * The platform decides whether tracing is active, see
 * {@link com.plotsquared.core.util.RegionManager#startTrace(String)}. When it is not, {@link #NONE} is handed out and
 * every call is a no-op, so call sites never need to check the setting themselves.
 */
public interface RegionTrace {

    RegionTrace NONE = new RegionTrace() {
        @Override
        public RegionTrace watch(final String label, final String world, final Collection<CuboidRegion> regions) {
            return this;
        }

        @Override
        public void stage(final String stage) {
        }

        @Override
        public void end() {
        }
    };

    /**
     * Adds regions to the snapshots and to the spawn watch of this trace.
     *
     * @param label   name the regions show up with in the log, e.g. "origin" or "destination"
     * @param world   world the regions are in
     * @param regions regions with absolute coordinates
     * @return this trace
     */
    RegionTrace watch(String label, String world, Collection<CuboidRegion> regions);

    /**
     * Logs a snapshot of every watched region. Safe to call from any thread, the snapshot itself runs on the main thread.
     *
     * @param stage name of the stage, e.g. "before" or "after-copy"
     */
    void stage(String stage);

    /**
     * Logs a final snapshot and keeps watching for spawned entities for a while, because drops can appear a few ticks
     * after the blocks were changed. A second snapshot is logged once the watch ends.
     */
    void end();

}
