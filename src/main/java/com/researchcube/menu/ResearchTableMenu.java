package com.researchcube.menu;

import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.item.CubeItem;
import com.researchcube.item.DriveItem;
import com.researchcube.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Menu (container) for the Research Table.
 *
 * Layout:
 *   Slot 0: Drive (filtered to DriveItem)
 *   Slot 1: Cube (filtered to CubeItem)
 *   Slots 2-7: Item cost inputs (6 slots)
 *   Player inventory: standard 27 + 9 hotbar
 *
 * Data slots sync research progress and state from server to client.
 */
public class ResearchTableMenu extends AbstractContainerMenu {

    private final ResearchTableBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    // Data slots for syncing BE state to client
    private final ContainerData data;

    public static final int DATA_PROGRESS = 0;     // 0-1000 (progress * 1000)
    public static final int DATA_IS_RESEARCHING = 1; // 0 or 1
    public static final int DATA_COUNT = 2;

    // ── Constructor from server (block entity available) ──
    public ResearchTableMenu(int containerId, Inventory playerInv, ResearchTableBlockEntity be) {
        super(ModMenus.RESEARCH_TABLE.get(), containerId);
        this.blockEntity = be;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        ItemStackHandler inv = be.getInventory();

        // Data sync
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> (int) (be.getProgress() * 1000);
                    case DATA_IS_RESEARCHING -> be.isResearching() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Read-only from client side
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(this.data);

        // ── Block Entity Slots ──

        // Slot 0: Drive
        addSlot(new SlotItemHandler(inv, ResearchTableBlockEntity.SLOT_DRIVE, 17, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DriveItem;
            }
        });

        // Slot 1: Cube
        addSlot(new SlotItemHandler(inv, ResearchTableBlockEntity.SLOT_CUBE, 17, 50) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CubeItem;
            }
        });

        // Slots 2-7: Cost inputs (3x2 grid)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = ResearchTableBlockEntity.COST_SLOT_START + row * 3 + col;
                addSlot(new SlotItemHandler(inv, slotIndex, 62 + col * 18, 20 + row * 18));
            }
        }

        // ── Player Inventory (27 slots) ──
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // ── Player Hotbar (9 slots) ──
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    // ── Constructor from client (FriendlyByteBuf) ──
    public ResearchTableMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntity(playerInv, buf));
    }

    private static ResearchTableBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        BlockEntity be = playerInv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof ResearchTableBlockEntity rtbe) {
            return rtbe;
        }
        throw new IllegalStateException("Block entity at position is not a ResearchTableBlockEntity");
    }

    public ResearchTableBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /**
     * Returns research progress as 0.0 to 1.0 (synced via data slots).
     */
    public float getScaledProgress() {
        return data.get(DATA_PROGRESS) / 1000.0f;
    }

    /**
     * Whether research is currently active (synced via data slots).
     */
    public boolean isResearching() {
        return data.get(DATA_IS_RESEARCHING) == 1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            int beSlots = ResearchTableBlockEntity.TOTAL_SLOTS; // 8
            int playerStart = beSlots;
            int playerEnd = playerStart + 36;

            if (index < beSlots) {
                // Moving from BE slots → player inventory
                if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from player inventory → BE slots
                if (slotStack.getItem() instanceof DriveItem) {
                    if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotStack.getItem() instanceof CubeItem) {
                    if (!this.moveItemStackTo(slotStack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Try cost slots
                    if (!this.moveItemStackTo(slotStack, ResearchTableBlockEntity.COST_SLOT_START, beSlots, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, blockEntity.getBlockState().getBlock());
    }
}
