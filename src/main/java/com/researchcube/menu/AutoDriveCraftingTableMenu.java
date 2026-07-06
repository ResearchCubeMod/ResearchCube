package com.researchcube.menu;

import com.researchcube.block.AutoDriveCraftingTableBlockEntity;
import com.researchcube.item.DriveItem;
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
 * Menu for the Auto Drive Crafting Table.
 *
 * Layout:
 *   - Slot 0: Drive input (dedicated, GUI-only)
 *   - Slots 1-9: 3x3 crafting grid
 *   - Slot 10: Output (extraction only: crafting is performed by the block entity)
 *   - Slots 11-46: Player inventory + hotbar
 *
 * Unlike the manual Drive Crafting Table, this menu does no recipe matching: the block
 * entity ticks and crafts on the server. The menu is a plain inventory view.
 */
public class AutoDriveCraftingTableMenu extends AbstractContainerMenu {

    private final AutoDriveCraftingTableBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    // ══════════════════════════════════════════════════════════════
    // Layout coordinates (shared by screen)
    //
    // Machine panel: drive | 3x3 grid | arrow | output
    // Inventory panel: 9x3 inventory + hotbar
    // ══════════════════════════════════════════════════════════════

    public static final int GUI_WIDTH = 200;
    public static final int GUI_HEIGHT = 208;

    // Section label row inside the machine panel
    public static final int LABEL_Y = 24;

    // Drive slot pixel position
    public static final int DRIVE_X = 28;
    public static final int DRIVE_Y = 54;

    // 3x3 grid top-left
    public static final int GRID_X = 72;
    public static final int GRID_Y = 36;

    // Output slot position
    public static final int OUTPUT_X = 154;
    public static final int OUTPUT_Y = 54;

    // Player inventory top-left
    public static final int INV_X = 19;
    public static final int INV_Y = 120;

    // ── Server-side constructor (with real BE) ──
    public AutoDriveCraftingTableMenu(int containerId, Inventory playerInv, AutoDriveCraftingTableBlockEntity be) {
        super(ModMenus.AUTO_DRIVE_CRAFTING_TABLE.get(), containerId);
        this.blockEntity = be;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        IItemHandler inv = be.getInventory();

        // Slot 0: Drive (accepts only drives)
        addSlot(new SlotItemHandler(inv, AutoDriveCraftingTableBlockEntity.SLOT_DRIVE, DRIVE_X, DRIVE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DriveItem;
            }
        });

        // Slots 1-9: 3x3 crafting grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = AutoDriveCraftingTableBlockEntity.GRID_SLOT_START + row * 3 + col;
                addSlot(new SlotItemHandler(inv, slotIndex, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }

        // Slot 10: Output (extraction only)
        addSlot(new OutputSlot(inv, AutoDriveCraftingTableBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));

        // Player Inventory (27 slots): slots 11-37
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }

        // Player Hotbar (9 slots): slots 38-46
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    // ── Client-side constructor (from FriendlyByteBuf) ──
    public AutoDriveCraftingTableMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntity(playerInv, buf));
    }

    private static AutoDriveCraftingTableBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        BlockEntity be = playerInv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof AutoDriveCraftingTableBlockEntity adcbe) {
            return adcbe;
        }
        throw new IllegalStateException("Block entity at position is not an AutoDriveCraftingTableBlockEntity");
    }

    // ── Standard menu ──

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            int beSlots = AutoDriveCraftingTableBlockEntity.TOTAL_SLOTS; // 11
            int playerStart = beSlots; // slot 11
            int playerEnd = playerStart + 36; // slot 47
            int outputIndex = AutoDriveCraftingTableBlockEntity.SLOT_OUTPUT; // slot 10

            if (index == outputIndex) {
                // Shift-click output → player inventory
                if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(slotStack, result);
            } else if (index < beSlots) {
                // Shift-click from drive/grid → player inventory
                if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Shift-click from player inventory → drive slot or grid
                if (slotStack.getItem() instanceof DriveItem) {
                    if (!this.moveItemStackTo(slotStack, AutoDriveCraftingTableBlockEntity.SLOT_DRIVE,
                            AutoDriveCraftingTableBlockEntity.SLOT_DRIVE + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Try grid slots (1-9)
                    if (!this.moveItemStackTo(slotStack, AutoDriveCraftingTableBlockEntity.GRID_SLOT_START,
                            AutoDriveCraftingTableBlockEntity.GRID_SLOT_START + AutoDriveCraftingTableBlockEntity.GRID_SIZE,
                            false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            // Persist BE-backed changes: moveItemStackTo's in-place partial-merge path only calls
            // Slot.setChanged(), a no-op for SlotItemHandler, so the merge never fires the
            // handler's onContentsChanged and would be lost on a crash. Every branch above touches
            // a BE-backed slot, so mark the BE dirty after any successful move.
            blockEntity.setChanged();
            blockEntity.markRecheckNeeded();
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, blockEntity.getBlockState().getBlock());
    }

    public AutoDriveCraftingTableBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** Output-only slot: cannot accept items via placement. */
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
