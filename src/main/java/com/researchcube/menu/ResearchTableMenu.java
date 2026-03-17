package com.researchcube.menu;

import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.client.ClientResearchData;
import com.researchcube.item.CubeItem;
import com.researchcube.item.DriveItem;
import com.researchcube.item.ResearchFluidBucketItem;
import com.researchcube.registry.ModFluids;
import com.researchcube.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * Menu (container) for the Research Table.
 *
 * Layout:
 *   Slot 0: Drive (filtered to DriveItem)
 *   Slot 1: Cube (filtered to CubeItem)
 *   Slots 2-7: Item cost inputs (6 slots)
 *   Slot 8: Bucket input (accepts fluid buckets)
 *   Slot 9: Bucket output (output only — empty buckets)
 *   Slot 10: Idea chip (unrestricted)
 *   Player inventory: standard 27 + 9 hotbar
 *
 * Data slots sync research progress, state, and fluid info from server to client.
 */
public class ResearchTableMenu extends AbstractContainerMenu {

    private final ResearchTableBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    // Data slots for syncing BE state to client
    private final ContainerData data;

    /** Player's completed research IDs (synced to client via menu buffer). */
    private final Set<String> completedResearch;

    public static final int DATA_PROGRESS = 0;       // 0-1000 (progress * 1000)
    public static final int DATA_IS_RESEARCHING = 1;   // 0 or 1
    public static final int DATA_FLUID_AMOUNT = 2;     // 0-8000 (millibuckets)
    public static final int DATA_FLUID_TYPE = 3;       // 0=empty, 1=thinking, 2=pondering, 3=reasoning, 4=imagination
    public static final int DATA_COUNT = 4;

    // Layout coordinates shared by screens
    public static final int DRIVE_X = 66;
    public static final int DRIVE_Y = 79;
    public static final int CUBE_X = 66;
    public static final int CUBE_Y = 113;
    public static final int COST_X = 113;
    public static final int COST_Y = 79;
    public static final int BUCKET_IN_X = 185;
    public static final int BUCKET_IN_Y = 79;
    public static final int BUCKET_OUT_X = 185;
    public static final int BUCKET_OUT_Y = 97;
    public static final int IDEA_CHIP_X = 167;
    public static final int IDEA_CHIP_Y = 113;
    public static final int PLAYER_INV_X = 245;
    public static final int PLAYER_INV_Y = 191;
    public static final int HOTBAR_X = 245;
    public static final int HOTBAR_Y = 266;

    // ── Constructor from server (block entity available) ──
    public ResearchTableMenu(int containerId, Inventory playerInv, ResearchTableBlockEntity be) {
        this(containerId, playerInv, be, Set.of());
    }

    // ── Base constructor (shared by server and client) ──
    private ResearchTableMenu(int containerId, Inventory playerInv, ResearchTableBlockEntity be, Set<String> completed) {
        super(ModMenus.RESEARCH_TABLE.get(), containerId);
        this.blockEntity = be;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.completedResearch = completed;

        // Update client-side research cache for JEI/EMI tooltip integration
        if (playerInv.player.level().isClientSide() && !completed.isEmpty()) {
            ClientResearchData.updateCompleted(completed);
        }

        ItemStackHandler inv = be.getInventory();

        // Data sync
        // SimpleContainerData stores received values so the client actually sees them.
        // On the server the anonymous subclass overrides get() to read live BE state each tick.
        SimpleContainerData storage = new SimpleContainerData(DATA_COUNT);
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                // Server reads live values; client reads whatever set() stored
                if (be.getLevel() != null && !be.getLevel().isClientSide()) {
                    return switch (index) {
                        case DATA_PROGRESS -> (int) (be.getProgress() * 1000);
                        case DATA_IS_RESEARCHING -> be.isResearching() ? 1 : 0;
                        case DATA_FLUID_AMOUNT -> be.getFluidTank().getFluidAmount();
                        case DATA_FLUID_TYPE -> {
                            FluidStack fs = be.getFluidTank().getFluid();
                            yield fs.isEmpty() ? 0 : ModFluids.getFluidIndex(fs.getFluid());
                        }
                        default -> 0;
                    };
                }
                return storage.get(index);
            }

            @Override
            public void set(int index, int value) {
                storage.set(index, value); // client stores the synced values here
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        addDataSlots(this.data);

        // ── Block Entity Slots ──

        // Slot 0: Drive
        addSlot(new SlotItemHandler(inv, ResearchTableBlockEntity.SLOT_DRIVE, DRIVE_X, DRIVE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DriveItem;
            }
        });

        // Slot 1: Cube
        addSlot(new SlotItemHandler(inv, ResearchTableBlockEntity.SLOT_CUBE, CUBE_X, CUBE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CubeItem;
            }
        });

        // Slots 2-7: Cost inputs (3x2 grid)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = ResearchTableBlockEntity.COST_SLOT_START + row * 3 + col;
                addSlot(new SlotItemHandler(inv, slotIndex, COST_X + col * 18, COST_Y + row * 18));
            }
        }

        // Slot 8: Bucket input (accepts research fluid buckets)
        addSlot(new SlotItemHandler(inv, ResearchTableBlockEntity.SLOT_BUCKET_IN, BUCKET_IN_X, BUCKET_IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ResearchFluidBucketItem;
            }
        });

        // Slot 9: Bucket output (output only — receives empty buckets)
        addSlot(new SlotItemHandler(inv, ResearchTableBlockEntity.SLOT_BUCKET_OUT, BUCKET_OUT_X, BUCKET_OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false; // output only
            }
        });

        // Slot 10: Idea chip (unrestricted — any item may be placed)
        addSlot(new SlotItemHandler(inv, ResearchTableBlockEntity.SLOT_IDEA_CHIP, IDEA_CHIP_X, IDEA_CHIP_Y));

        // ── Player Inventory (27 slots) ──
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // ── Player Hotbar (9 slots) ──
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, HOTBAR_X + col * 18, HOTBAR_Y));
        }
    }

    // ── Constructor from client (FriendlyByteBuf) ──
    public ResearchTableMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntity(playerInv, buf),
             readCompletedResearch(buf));
    }

    private static Set<String> readCompletedResearch(FriendlyByteBuf buf) {
        return buf.readCollection(HashSet::new, b -> b.readResourceLocation().toString());
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
     * Returns the set of completed research IDs (as strings) for the opening player.
     * Populated on the client from the menu buffer. Empty on the server.
     */
    public Set<String> getCompletedResearch() {
        return completedResearch;
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

    /** Fluid amount in millibuckets (0-8000), synced via data slots. */
    public int getFluidAmount() {
        return data.get(DATA_FLUID_AMOUNT);
    }

    /** Fluid type index (0=empty, 1-4=research fluids), synced via data slots. */
    public int getFluidType() {
        return data.get(DATA_FLUID_TYPE);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            int beSlots = ResearchTableBlockEntity.TOTAL_SLOTS; // 11
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
                } else if (slotStack.getItem() instanceof ResearchFluidBucketItem) {
                    // Fluid bucket → bucket input slot
                    if (!this.moveItemStackTo(slotStack, ResearchTableBlockEntity.SLOT_BUCKET_IN, ResearchTableBlockEntity.SLOT_BUCKET_IN + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Try cost slots (2-7 only, not bucket slots)
                    if (!this.moveItemStackTo(slotStack, ResearchTableBlockEntity.COST_SLOT_START, ResearchTableBlockEntity.SLOT_BUCKET_IN, false)) {
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
