package com.researchcube.menu;

import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu for the Processing Station.
 * 
 * Layout:
 *   4×4 input grid (16 slots)
 *   2×4 output grid (8 slots)
 *   2 fluid input tanks
 *   1 fluid output tank
 *   Progress bar
 */
public class ProcessingStationMenu extends AbstractContainerMenu {

    private final ProcessingStationBlockEntity blockEntity;
    private final ContainerData data;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_IS_PROCESSING = 1;
    public static final int DATA_FLUID_IN1_AMOUNT = 2;
    public static final int DATA_FLUID_IN2_AMOUNT = 3;
    public static final int DATA_FLUID_OUT_AMOUNT = 4;
    public static final int DATA_COUNT = 5;

    // Layout coordinates shared with screen rendering
    public static final int INPUT_GRID_X = 24;
    public static final int INPUT_GRID_Y = 36;
    public static final int OUTPUT_GRID_X = 232;
    public static final int OUTPUT_GRID_Y = 36;
    public static final int PLAYER_INV_X = 72;
    public static final int PLAYER_INV_Y = 124;
    public static final int HOTBAR_X = 72;
    public static final int HOTBAR_Y = 182;

    // Constructor for server-side
    public ProcessingStationMenu(int containerId, Inventory playerInv, ProcessingStationBlockEntity be) {
        super(ModMenus.PROCESSING_STATION.get(), containerId);
        this.blockEntity = be;

        IItemHandler inventory = be.getInventory();

        // Input slots (4×4 grid)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int slotIndex = ProcessingStationBlockEntity.INPUT_SLOT_START + row * 4 + col;
                addSlot(new SlotItemHandler(inventory, slotIndex, INPUT_GRID_X + col * 18, INPUT_GRID_Y + row * 18));
            }
        }

        // Output slots (2×4 grid)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int slotIndex = ProcessingStationBlockEntity.OUTPUT_SLOT_START + row * 2 + col;
                addSlot(new OutputSlot(inventory, slotIndex, OUTPUT_GRID_X + col * 18, OUTPUT_GRID_Y + row * 18));
            }
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, HOTBAR_X + col * 18, HOTBAR_Y));
        }

        // ContainerData for syncing
        SimpleContainerData storage = new SimpleContainerData(DATA_COUNT);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                    return switch (index) {
                        case DATA_PROGRESS -> (int) (be.getProgress() * 1000);
                        case DATA_IS_PROCESSING -> be.isProcessing() ? 1 : 0;
                        case DATA_FLUID_IN1_AMOUNT -> be.getFluidInput1().getFluidAmount();
                        case DATA_FLUID_IN2_AMOUNT -> be.getFluidInput2().getFluidAmount();
                        case DATA_FLUID_OUT_AMOUNT -> be.getFluidOutput().getFluidAmount();
                        default -> 0;
                    };
                }
                return storage.get(index);
            }

            @Override
            public void set(int index, int value) {
                storage.set(index, value);
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(this.data);
    }

    // Constructor for client-side (from network)
    public ProcessingStationMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntity(playerInv, buf));
    }

    private static ProcessingStationBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        BlockEntity be = playerInv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof ProcessingStationBlockEntity psbe) {
            return psbe;
        }
        throw new IllegalStateException("Block entity is not a ProcessingStationBlockEntity");
    }

    public ProcessingStationBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isProcessing() {
        return data.get(DATA_IS_PROCESSING) == 1;
    }

    public float getProgress() {
        return data.get(DATA_PROGRESS) / 1000f;
    }

    public int getFluidInput1Amount() {
        return data.get(DATA_FLUID_IN1_AMOUNT);
    }

    public int getFluidInput2Amount() {
        return data.get(DATA_FLUID_IN2_AMOUNT);
    }

    public int getFluidOutputAmount() {
        return data.get(DATA_FLUID_OUT_AMOUNT);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            int beSlots = ProcessingStationBlockEntity.TOTAL_SLOTS; // 24
            int playerStart = beSlots;
            int playerEnd = playerStart + 36;

            if (index < beSlots) {
                // From BE to player
                if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // From player to input slots (0-15)
                if (!this.moveItemStackTo(slotStack, 0, ProcessingStationBlockEntity.INPUT_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, blockEntity.getBlockState().getBlock());
    }

    /**
     * Output-only slot that doesn't accept items via placement.
     */
    private static class OutputSlot extends SlotItemHandler {
        public OutputSlot(IItemHandler itemHandler, int index, int x, int y) {
            super(itemHandler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
