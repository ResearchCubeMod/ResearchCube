package com.researchcube.block;

import com.researchcube.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Block entity for the Drive Crafting Table.
 * Persists a drive slot (0) and a 3x3 crafting grid (slots 1-9) between interactions.
 * No ticking required; recipe matching is handled in the menu.
 */
public class DriveCraftingTableBlockEntity extends BlockEntity {

    public static final int SLOT_DRIVE = 0;
    public static final int GRID_SLOT_START = 1;
    public static final int GRID_SIZE = 9;
    public static final int TOTAL_SLOTS = 1 + GRID_SIZE; // 10

    /**
     * Listeners notified whenever the shared inventory changes. Every open menu registers
     * itself here (and unregisters on close) so that all viewers re-evaluate the recipe when
     * the grid is mutated; the grid is shared BE state but each menu keeps its own result slot,
     * so a single-menu notification would leave other viewers looking at a stale result.
     * CopyOnWriteArrayList keeps iteration safe if a listener mutates the list during dispatch.
     */
    private final List<Runnable> inventoryListeners = new CopyOnWriteArrayList<>();

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            notifyInventoryListeners();
        }
    };

    /** Register a listener fired on every inventory change. Called by menus on open. */
    public void addInventoryListener(Runnable listener) {
        inventoryListeners.add(listener);
    }

    /** Remove a previously registered inventory listener. Called by menus on close. */
    public void removeInventoryListener(Runnable listener) {
        inventoryListeners.remove(listener);
    }

    /**
     * Fire all registered inventory listeners. Normally invoked automatically via
     * {@code onContentsChanged}, but menus also call this after in-place stack merges
     * (which mutate the backing stack without going through {@code setStackInSlot}) so that
     * every open viewer re-evaluates the shared grid.
     */
    public void notifyInventoryListeners() {
        for (Runnable listener : inventoryListeners) {
            listener.run();
        }
    }

    public DriveCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRIVE_CRAFTING_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    // ── NBT Persistence ──

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
    }

    // ── Drop contents on block break ──

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
