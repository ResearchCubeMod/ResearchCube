package com.researchcube.menu;

import com.researchcube.block.DriveCraftingTableBlockEntity;
import com.researchcube.item.DriveItem;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.registry.ModMenus;
import com.researchcube.registry.ModRecipeTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Menu for the Drive Crafting Table.
 * Layout:
 *   - Slot 0: Drive input (dedicated slot, separate from grid)
 *   - Slots 1-9: 3x3 crafting grid
 *   - Slot 10: Result output (virtual, from ResultContainer)
 *   - Slots 11-46: Player inventory + hotbar
 *
 * Recipe matching creates a combined CraftingInput that includes the drive
 * alongside the grid items, allowing reuse of existing DriveCraftingRecipe.matches().
 */
public class DriveCraftingTableMenu extends AbstractContainerMenu {

    private final DriveCraftingTableBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ResultContainer resultContainer = new ResultContainer();
    private final Player player;

    @Nullable
    private RecipeHolder<DriveCraftingRecipe> currentRecipe = null;

    // ── Slot layout constants ──
    // Drive slot pixel position
    public static final int DRIVE_X = 34;
    public static final int DRIVE_Y = 38;

    // 3x3 grid top-left
    public static final int GRID_X = 88;
    public static final int GRID_Y = 30;

    // Result slot position
    public static final int RESULT_X = 196;
    public static final int RESULT_Y = 38;

    // Player inventory top-left
    public static final int INV_X = 43;
    public static final int INV_Y = 106;

    // ── Server-side constructor (with real BE) ──
    public DriveCraftingTableMenu(int containerId, Inventory playerInv, DriveCraftingTableBlockEntity be) {
        super(ModMenus.DRIVE_CRAFTING_TABLE.get(), containerId);
        this.blockEntity = be;
        this.player = playerInv.player;
        this.access = ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        ItemStackHandler inv = be.getInventory();

        // Slot 0: Drive
        addSlot(new NotifyingSlotItemHandler(inv, DriveCraftingTableBlockEntity.SLOT_DRIVE, DRIVE_X, DRIVE_Y, this) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DriveItem;
            }
        });

        // Slots 1-9: 3x3 crafting grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = DriveCraftingTableBlockEntity.GRID_SLOT_START + row * 3 + col;
                addSlot(new NotifyingSlotItemHandler(inv, slotIndex, GRID_X + col * 18, GRID_Y + row * 18, this));
            }
        }

        // Slot 10: Result (virtual output)
        addSlot(new DriveCraftingResultSlot(this, resultContainer, 0, RESULT_X, RESULT_Y));

        // Player Inventory (27 slots) — slots 11-37
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }

        // Player Hotbar (9 slots) — slots 38-46
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, INV_Y + 58));
        }

        // Initial recipe check
        updateResult();
    }

    // ── Client-side constructor (from FriendlyByteBuf) ──
    public DriveCraftingTableMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, getBlockEntity(playerInv, buf));
    }

    private static DriveCraftingTableBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf buf) {
        BlockEntity be = playerInv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof DriveCraftingTableBlockEntity dcbe) {
            return dcbe;
        }
        throw new IllegalStateException("Block entity at position is not a DriveCraftingTableBlockEntity");
    }

    // ── Recipe matching ──

    /**
     * Called whenever a grid or drive slot changes. Builds a combined CraftingInput
     * including the drive, then searches for a matching DriveCraftingRecipe.
     *
     * For shaped recipes, the 3×3 grid is passed as a proper 3×3 CraftingInput so that
     * ShapedRecipePattern.matches() can slide patterns and apply offset logic correctly.
     * The drive is included as an extra slot at position 9 (conceptually a 4th column).
     */
    void updateResult() {
        Level level = player.level();
        if (level == null) return;

        ItemStackHandler inv = blockEntity.getInventory();

        // Build a CraftingInput that includes the drive:
        // Layout: 4×3 grid where columns 0-2 are the crafting grid, column 3 is the drive
        // This preserves the 3×3 positions for shaped matching while including the drive for detection.
        // Row 0: [grid0, grid1, grid2, drive]
        // Row 1: [grid3, grid4, grid5, EMPTY]
        // Row 2: [grid6, grid7, grid8, EMPTY]
        List<ItemStack> combined = new ArrayList<>(12);
        // Row 0
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 0));
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 1));
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 2));
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.SLOT_DRIVE)); // Drive in 4th column
        // Row 1
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 3));
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 4));
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 5));
        combined.add(ItemStack.EMPTY);
        // Row 2
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 6));
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 7));
        combined.add(inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + 8));
        combined.add(ItemStack.EMPTY);

        CraftingInput craftingInput = CraftingInput.of(4, 3, combined);

        // Search for matching recipe
        Optional<RecipeHolder<DriveCraftingRecipe>> match = level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.DRIVE_CRAFTING.get(), craftingInput, level);

        if (match.isPresent()) {
            currentRecipe = match.get();
            resultContainer.setItem(0, currentRecipe.value().assemble(craftingInput, level.registryAccess()));
        } else {
            currentRecipe = null;
            resultContainer.setItem(0, ItemStack.EMPTY);
        }
        broadcastChanges();
    }

    /**
     * Called when the player takes the result item.
     * Consumes ingredients from the grid; the drive is left intact so the
     * same recipe can be crafted again without re-researching.
     */
    void onResultTaken(Player player) {
        if (currentRecipe == null) return;

        DriveCraftingRecipe recipe = currentRecipe.value();

        // Drive stays in the slot unchanged — recipe_id is deliberately kept

        // Consume ingredients from the grid
        if (recipe.isShaped()) {
            consumeGridIngredientsShaped(recipe);
        } else {
            consumeGridIngredientsShapeless(recipe);
        }

        // Re-check recipe after consumption
        updateResult();
    }

    /**
     * Consumes ingredients for a shaped recipe. Each ingredient position in the pattern
     * matches a specific grid slot. Uses shapedPattern offset matching.
     */
    private void consumeGridIngredientsShaped(DriveCraftingRecipe recipe) {
        ItemStackHandler inv = blockEntity.getInventory();
        ShapedRecipePattern pattern = recipe.getShapedPattern();
        if (pattern == null) return;

        // Determine where the pattern matched by sliding it over the grid
        // This replicates ShapedRecipePattern.matches() offset finding logic
        int patternW = pattern.width();
        int patternH = pattern.height();

        for (int offsetX = 0; offsetX <= 3 - patternW; offsetX++) {
            for (int offsetY = 0; offsetY <= 3 - patternH; offsetY++) {
                if (checkPatternMatch(inv, pattern, offsetX, offsetY, false) ||
                    checkPatternMatch(inv, pattern, offsetX, offsetY, true)) {
                    // Found the match, consume at this offset
                    consumeAtOffset(inv, pattern, offsetX, offsetY, false);
                    return;
                }
            }
        }
    }

    private boolean checkPatternMatch(ItemStackHandler inv, ShapedRecipePattern pattern, int offsetX, int offsetY, boolean mirrored) {
        for (int py = 0; py < pattern.height(); py++) {
            for (int px = 0; px < pattern.width(); px++) {
                int gridX = offsetX + (mirrored ? pattern.width() - 1 - px : px);
                int gridY = offsetY + py;
                int gridSlot = gridY * 3 + gridX;
                int patternSlot = py * pattern.width() + px;

                Ingredient ingredient = pattern.ingredients().get(patternSlot);
                ItemStack gridStack = inv.getStackInSlot(DriveCraftingTableBlockEntity.GRID_SLOT_START + gridSlot);

                if (ingredient == null || ingredient.isEmpty()) {
                    if (!gridStack.isEmpty()) return false;
                } else {
                    if (!ingredient.test(gridStack)) return false;
                }
            }
        }
        return true;
    }

    private void consumeAtOffset(ItemStackHandler inv, ShapedRecipePattern pattern, int offsetX, int offsetY, boolean mirrored) {
        for (int py = 0; py < pattern.height(); py++) {
            for (int px = 0; px < pattern.width(); px++) {
                int gridX = offsetX + (mirrored ? pattern.width() - 1 - px : px);
                int gridY = offsetY + py;
                int gridSlot = gridY * 3 + gridX;
                int patternSlot = py * pattern.width() + px;

                Ingredient ingredient = pattern.ingredients().get(patternSlot);
                if (ingredient != null && !ingredient.isEmpty()) {
                    int slotIndex = DriveCraftingTableBlockEntity.GRID_SLOT_START + gridSlot;
                    ItemStack gridStack = inv.getStackInSlot(slotIndex);
                    ItemStack remainder = gridStack.getCraftingRemainingItem();
                    gridStack.shrink(1);
                    if (gridStack.isEmpty() && !remainder.isEmpty()) {
                        inv.setStackInSlot(slotIndex, remainder);
                    }
                }
            }
        }
    }

    /**
     * Consumes one item from each grid slot that contributed to the recipe (shapeless matching).
     * Uses the same shapeless matching logic as DriveCraftingRecipe.
     */
    private void consumeGridIngredientsShapeless(DriveCraftingRecipe recipe) {
        ItemStackHandler inv = blockEntity.getInventory();
        var ingredients = recipe.getIngredients();
        boolean[] consumed = new boolean[DriveCraftingTableBlockEntity.GRID_SIZE];

        for (var ingredient : ingredients) {
            for (int i = 0; i < DriveCraftingTableBlockEntity.GRID_SIZE; i++) {
                if (!consumed[i]) {
                    int slotIndex = DriveCraftingTableBlockEntity.GRID_SLOT_START + i;
                    ItemStack gridStack = inv.getStackInSlot(slotIndex);
                    if (!gridStack.isEmpty() && ingredient.test(gridStack)) {
                        // Check for container items (e.g., buckets)
                        ItemStack remainder = gridStack.getCraftingRemainingItem();
                        gridStack.shrink(1);
                        if (gridStack.isEmpty() && !remainder.isEmpty()) {
                            inv.setStackInSlot(slotIndex, remainder);
                        }
                        consumed[i] = true;
                        break;
                    }
                }
            }
        }
    }

    // ── Standard menu ──

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            int beSlots = DriveCraftingTableBlockEntity.TOTAL_SLOTS; // 10
            int resultSlotIndex = beSlots; // slot 10
            int playerStart = resultSlotIndex + 1; // slot 11
            int playerEnd = playerStart + 36; // slot 47

            if (index == resultSlotIndex) {
                // Shift-click result → player inventory
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
                    if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Try grid slots
                    if (!this.moveItemStackTo(slotStack, DriveCraftingTableBlockEntity.GRID_SLOT_START,
                            DriveCraftingTableBlockEntity.TOTAL_SLOTS, false)) {
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

    public DriveCraftingTableBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Nullable
    public DriveCraftingRecipe getCurrentRecipe() {
        return currentRecipe != null ? currentRecipe.value() : null;
    }

    // ── Custom SlotItemHandler that notifies menu on change ──

    private static class NotifyingSlotItemHandler extends SlotItemHandler {
        private final DriveCraftingTableMenu menu;

        public NotifyingSlotItemHandler(ItemStackHandler handler, int index, int x, int y, DriveCraftingTableMenu menu) {
            super(handler, index, x, y);
            this.menu = menu;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            menu.updateResult();
        }
    }

    // ── Result slot that handles crafting on take ──

    private static class DriveCraftingResultSlot extends Slot {
        private final DriveCraftingTableMenu menu;
        private int removeCount;

        public DriveCraftingResultSlot(DriveCraftingTableMenu menu, Container container, int slot, int x, int y) {
            super(container, slot, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // Cannot place items into the result slot
        }

        @Override
        public ItemStack remove(int amount) {
            if (this.hasItem()) {
                this.removeCount += Math.min(amount, this.getItem().getCount());
            }
            return super.remove(amount);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            this.checkTakeAchievements(stack);
            menu.onResultTaken(player);
            super.onTake(player, stack);
        }

        @Override
        protected void onQuickCraft(ItemStack stack, int amount) {
            this.removeCount += amount;
            this.checkTakeAchievements(stack);
        }

        @Override
        protected void checkTakeAchievements(ItemStack stack) {
            stack.onCraftedBy(menu.player.level(), menu.player, this.removeCount);
            this.removeCount = 0;
        }
    }
}
