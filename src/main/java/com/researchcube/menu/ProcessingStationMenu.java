package com.researchcube.menu;

import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.item.DriveItem;
import com.researchcube.network.SyncTankPacket;
import com.researchcube.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Menu for the Processing Station.
 *
 * Layout:
 *   4×4 input grid (16 slots)
 *   2×4 output grid (8 slots)
 *   Drive slot (research unlock carrier, control column)
 *   2 fluid input tanks
 *   1 fluid output tank
 *   Progress bar
 */
public class ProcessingStationMenu extends AbstractContainerMenu {

    private final ProcessingStationBlockEntity blockEntity;
    private final ContainerData data;

    /** Number of fluid tanks synced to the client (input 1, input 2, output). */
    public static final int TANK_COUNT = 3;

    // ── Fluid sync ──
    // Progress/processing state ride the vanilla ContainerData dataslots below. Fluid tank
    // contents (type + amount) are synced separately as full FluidStacks via SyncTankPacket,
    // because a 16-bit dataslot cannot carry a fluid's registry id and external inserts must
    // show the real fluid, not just an amount.

    /** Server-side snapshot of each tank, compared in broadcastChanges() to detect changes. */
    private final FluidStack[] serverTankCache = { FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY };
    /** Client-side received tank contents, read by the screen for rendering + tooltips. */
    private final FluidStack[] clientTanks = { FluidStack.EMPTY, FluidStack.EMPTY, FluidStack.EMPTY };

    /** Non-null on the server; used to push tank sync packets to the viewing player. */
    private final ServerPlayer serverViewer;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_IS_PROCESSING = 1;
    public static final int DATA_COUNT = 2;

    // ══════════════════════════════════════════════════════════════
    // Layout coordinates (shared by screen + texture generator)
    //
    // Machine panel (8,16,240,104): 4x4 inputs | tanks/progress/button | 2x4 outputs
    // Inventory panel (8,124,240,96): 9x3 inventory + hotbar
    // ══════════════════════════════════════════════════════════════

    public static final int GUI_WIDTH = 256;
    public static final int GUI_HEIGHT = 226;

    public static final int INPUT_GRID_X = 20;
    public static final int INPUT_GRID_Y = 36;
    public static final int OUTPUT_GRID_X = 204;
    public static final int OUTPUT_GRID_Y = 36;
    // Drive slot: centered on the control column (CONTROL_CENTER_X=142 in the screen),
    // below the progress bar where the Start/Auto button row used to sit. An 18px slot
    // centered on x=142 starts at x=142-9=133. Clear of the tanks (y36-68), the flow
    // arrows (y66-74) and the status line (y>=107).
    public static final int DRIVE_SLOT_X = 133;
    public static final int DRIVE_SLOT_Y = 88;
    public static final int PLAYER_INV_X = 47;
    public static final int PLAYER_INV_Y = 140;
    public static final int HOTBAR_X = 47;
    public static final int HOTBAR_Y = 198;

    // Constructor for server-side
    public ProcessingStationMenu(int containerId, Inventory playerInv, ProcessingStationBlockEntity be) {
        super(ModMenus.PROCESSING_STATION.get(), containerId);
        this.blockEntity = be;
        // The viewing player is a ServerPlayer only when this menu was built server-side; on the
        // client it is a local player and tank sync is driven by incoming packets instead.
        this.serverViewer = (playerInv.player instanceof ServerPlayer sp) ? sp : null;

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

        // Drive slot (only accepts drives; gates which recipes may start)
        addSlot(new SlotItemHandler(inventory, ProcessingStationBlockEntity.SLOT_DRIVE, DRIVE_SLOT_X, DRIVE_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DriveItem;
            }
        });

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

        // ContainerData for syncing progress / processing state (fluid tanks sync separately).
        SimpleContainerData storage = new SimpleContainerData(DATA_COUNT);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                    return switch (index) {
                        case DATA_PROGRESS -> (int) (be.getProgress() * 1000);
                        case DATA_IS_PROCESSING -> be.isProcessing() ? 1 : 0;
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

    // ── Client-visible tank contents (kept live by SyncTankPacket) ──

    /**
     * Client-side view of a tank's full contents (type + amount + components).
     * Index 0 = input 1, 1 = input 2, 2 = output. Never returns null.
     */
    public FluidStack getClientTank(int tankIndex) {
        if (tankIndex < 0 || tankIndex >= TANK_COUNT) return FluidStack.EMPTY;
        return clientTanks[tankIndex];
    }

    public FluidStack getFluidInput1Stack() {
        return clientTanks[0];
    }

    public FluidStack getFluidInput2Stack() {
        return clientTanks[1];
    }

    public FluidStack getFluidOutputStack() {
        return clientTanks[2];
    }

    public int getFluidInput1Amount() {
        return clientTanks[0].getAmount();
    }

    public int getFluidInput2Amount() {
        return clientTanks[1].getAmount();
    }

    public int getFluidOutputAmount() {
        return clientTanks[2].getAmount();
    }

    /** Client-side: store a tank's synced contents (called from the SyncTankPacket handler). */
    public void setClientTank(int tankIndex, FluidStack stack) {
        if (tankIndex < 0 || tankIndex >= TANK_COUNT) return;
        clientTanks[tankIndex] = stack;
    }

    /** Live tank contents on the server, indexed as in {@link #getClientTank(int)}. */
    private FluidStack serverTank(int tankIndex) {
        return switch (tankIndex) {
            case 0 -> blockEntity.getFluidInput1().getFluid();
            case 1 -> blockEntity.getFluidInput2().getFluid();
            case 2 -> blockEntity.getFluidOutput().getFluid();
            default -> FluidStack.EMPTY;
        };
    }

    /**
     * Push fluid tanks to the viewing player alongside the usual slot/data sync. Runs every
     * tick the menu is open; a per-tank cache keeps this to one packet only when a tank
     * actually changes — however the fluid got there (GUI, recipe, or external pipe).
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (serverViewer == null) return;

        for (int i = 0; i < TANK_COUNT; i++) {
            FluidStack current = serverTank(i);
            if (!FluidStack.matches(serverTankCache[i], current)) {
                serverTankCache[i] = current.copy();
                PacketDistributor.sendToPlayer(serverViewer, new SyncTankPacket(containerId, i, current.copy()));
            }
        }
    }

    /**
     * Send the initial tank state when a viewer starts watching. The client's tanks default to
     * empty, so only non-empty tanks need an up-front packet; matching empty tanks stay in sync
     * and subsequent changes flow through {@link #broadcastChanges()}.
     */
    @Override
    public void addSlotListener(ContainerListener listener) {
        super.addSlotListener(listener);
        if (serverViewer == null) return;
        for (int i = 0; i < TANK_COUNT; i++) {
            FluidStack current = serverTank(i);
            serverTankCache[i] = current.copy();
            if (!current.isEmpty()) {
                PacketDistributor.sendToPlayer(serverViewer, new SyncTankPacket(containerId, i, current.copy()));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            int beSlots = ProcessingStationBlockEntity.TOTAL_SLOTS; // 25 (incl. drive slot at menu index 24)
            int playerStart = beSlots;
            int playerEnd = playerStart + 36;
            int driveSlotIndex = ProcessingStationBlockEntity.SLOT_DRIVE; // menu index matches BE index

            if (index < beSlots) {
                // From BE to player (works for inputs, outputs and the drive slot)
                if (!this.moveItemStackTo(slotStack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotStack.getItem() instanceof DriveItem) {
                // Drives go to the drive slot first; fall back to the input grid if occupied
                if (!this.moveItemStackTo(slotStack, driveSlotIndex, driveSlotIndex + 1, false)
                        && !this.moveItemStackTo(slotStack, 0, ProcessingStationBlockEntity.INPUT_SLOT_COUNT, false)) {
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

            // Every branch above moves items into or out of a BE-backed slot. moveItemStackTo's
            // in-place partial-merge path only calls Slot.setChanged() — a no-op for
            // SlotItemHandler — so the handler's onContentsChanged never fires and the merge is
            // neither persisted (rollback on crash) nor picked up by the auto-scan. Mark the BE
            // dirty and flag a recheck here so partial shift-click merges behave like any other
            // input change.
            blockEntity.setChanged();
            blockEntity.markRecheckNeeded();
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
