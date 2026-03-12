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

/**
 * Block entity for the Drive Crafting Table.
 * Persists a drive slot (0) and a 3x3 crafting grid (slots 1-9) between interactions.
 * No ticking required — recipe matching is handled in the menu.
 */
public class DriveCraftingTableBlockEntity extends BlockEntity {

    public static final int SLOT_DRIVE = 0;
    public static final int GRID_SLOT_START = 1;
    public static final int GRID_SIZE = 9;
    public static final int TOTAL_SLOTS = 1 + GRID_SIZE; // 10

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

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
