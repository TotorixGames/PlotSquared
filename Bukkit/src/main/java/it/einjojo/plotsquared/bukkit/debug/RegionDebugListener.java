package it.einjojo.plotsquared.bukkit.debug;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;

/**
 * Hands every entity spawn to the {@link RegionOperationTracer}. {@link EntitySpawnEvent} also delivers
 * {@link org.bukkit.event.entity.ItemSpawnEvent} and {@link org.bukkit.event.entity.CreatureSpawnEvent}.
 * <p>
 * Always registered: without an active trace (see
 * {@link com.plotsquared.core.configuration.Settings.Region_Debug#OPERATIONS}) it returns right away.
 */
public final class RegionDebugListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitySpawn(final EntitySpawnEvent event) {
        RegionOperationTracer.onSpawn(event);
    }

}
