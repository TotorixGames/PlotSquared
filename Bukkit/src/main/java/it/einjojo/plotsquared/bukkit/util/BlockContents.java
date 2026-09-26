package it.einjojo.plotsquared.bukkit.util;

import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;

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
     * Empties the given block entity, including a loot table that has not been rolled yet - vanilla would roll it
     * while dropping the contents.
     *
     * @param state a live (non snapshot) state, see {@link org.bukkit.block.Block#getState(boolean)}
     * @return whether anything was removed
     */
    public static boolean clear(final BlockState state) {
        boolean changed = false;
        if (state instanceof Lootable lootable && lootable.getLootTable() != null) {
            lootable.setLootTable(null);
            changed = true;
        }
        Inventory inventory = inventoryOf(state);
        if (inventory != null) {
            if (count(inventory) > 0) {
                inventory.clear();
                changed = true;
            }
        } else if (state instanceof Campfire campfire && count(campfire) > 0) {
            for (int slot = 0; slot < campfire.getSize(); slot++) {
                campfire.setItem(slot, null);
            }
            changed = true;
        }
        return changed;
    }

    /**
     * Counts the items a block entity holds, without changing it. Loot tables that have not been rolled yet are not
     * counted.
     *
     * @param state the block entity
     * @return the number of items, 0 for blocks without contents
     */
    public static int count(final BlockState state) {
        Inventory inventory = inventoryOf(state);
        if (inventory != null) {
            return count(inventory);
        }
        if (state instanceof Campfire campfire) {
            return count(campfire);
        }
        return 0;
    }

    /**
     * Whether the block entity can hold items at all.
     */
    public static boolean holdsItems(final BlockState state) {
        return state instanceof InventoryHolder || state instanceof Campfire;
    }

    private static Inventory inventoryOf(final BlockState state) {
        if (state instanceof Chest chest) {
            // getInventory() of a double chest spans both halves, only this half is being replaced
            return chest.getBlockInventory();
        }
        if (state instanceof InventoryHolder holder) {
            // Container, Jukebox, Lectern, ChiseledBookshelf, DecoratedPot, ...
            return holder.getInventory();
        }
        return null;
    }

    private static int count(final Inventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            count += amount(item);
        }
        return count;
    }

    private static int count(final Campfire campfire) {
        int count = 0;
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            count += amount(campfire.getItem(slot));
        }
        return count;
    }

    private static int amount(final ItemStack item) {
        return item == null || item.isEmpty() ? 0 : item.getAmount();
    }

}
