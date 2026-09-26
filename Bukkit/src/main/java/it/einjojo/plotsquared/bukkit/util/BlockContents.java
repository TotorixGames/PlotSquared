package it.einjojo.plotsquared.bukkit.util;

import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Empties block entities before PlotSquared overwrites them.
 * <p>
 * Since Minecraft 1.21.5 replacing a block entity runs {@code BlockEntity#preRemoveSideEffects}, which drops the
 * contents of containers, jukeboxes, lecterns, campfires and so on into the world - even with every WorldEdit side
 * effect disabled. When PlotSquared clears a plot whose contents were copied elsewhere just before (move), those drops
 * are duplicates. Emptying the block entity first leaves nothing to drop.
 */
public final class BlockContents {

    private BlockContents() {
    }

    /**
     * Empties the given block entity.
     *
     * @param state a live (non snapshot) state, see {@link org.bukkit.block.Block#getState(boolean)}
     * @return the number of items that were removed
     */
    public static int clear(final BlockState state) {
        if (state instanceof Chest chest) {
            // getInventory() of a double chest spans both halves, only this half is being replaced
            return clear(chest.getBlockInventory());
        }
        if (state instanceof InventoryHolder holder) {
            // Container, Jukebox, Lectern, ChiseledBookshelf, DecoratedPot, ...
            return clear(holder.getInventory());
        }
        if (state instanceof Campfire campfire) {
            int removed = 0;
            for (int slot = 0; slot < campfire.getSize(); slot++) {
                ItemStack item = campfire.getItem(slot);
                if (item != null && !item.isEmpty()) {
                    removed += item.getAmount();
                    campfire.setItem(slot, null);
                }
            }
            return removed;
        }
        return 0;
    }

    /**
     * Counts the items a block entity holds, without changing it.
     *
     * @param state the block entity
     * @return the number of items, 0 for blocks without contents
     */
    public static int count(final BlockState state) {
        if (state instanceof Chest chest) {
            return count(chest.getBlockInventory());
        }
        if (state instanceof InventoryHolder holder) {
            return count(holder.getInventory());
        }
        if (state instanceof Campfire campfire) {
            int count = 0;
            for (int slot = 0; slot < campfire.getSize(); slot++) {
                ItemStack item = campfire.getItem(slot);
                if (item != null && !item.isEmpty()) {
                    count += item.getAmount();
                }
            }
            return count;
        }
        return 0;
    }

    /**
     * Whether the block entity can hold items at all.
     */
    public static boolean holdsItems(final BlockState state) {
        return state instanceof InventoryHolder || state instanceof Campfire;
    }

    private static int clear(final Inventory inventory) {
        int removed = count(inventory);
        if (removed > 0) {
            inventory.clear();
        }
        return removed;
    }

    private static int count(final Inventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.isEmpty()) {
                count += item.getAmount();
            }
        }
        return count;
    }

}
