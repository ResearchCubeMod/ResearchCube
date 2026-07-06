package com.researchcube.block;

import com.researchcube.item.DriveItem;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.registry.ModBlockEntities;
import com.researchcube.registry.ModRecipeTypes;
import com.researchcube.sideio.IOChannel;
import com.researchcube.sideio.IOMode;
import com.researchcube.sideio.ItemChannelSpec;
import com.researchcube.sideio.SideConfigurable;
import com.researchcube.sideio.SideIOConfig;
import com.researchcube.util.NbtUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BlockEntity for the Auto Drive Crafting Table — an automation-friendly variant of the
 * manual {@link DriveCraftingTableBlockEntity}.
 *
 * <p>Slot layout:
 * <ul>
 *   <li>0: Drive (GUI-only research-unlock carrier; never touched by pipes, never consumed)</li>
 *   <li>1-9: 3x3 crafting grid (pipe-insertable)</li>
 *   <li>10: Output (pipe-extractable)</li>
 * </ul>
 *
 * <p>The block ticks on the server: when the drive unlocks a {@link DriveCraftingRecipe}
 * whose pattern matches the grid and the output can accept the result, it crafts one item
 * per cooldown, consuming ingredients (respecting container remainders) and stacking the
 * result into the output slot. Crafts chain automatically while ingredients remain.
 */
public class AutoDriveCraftingTableBlockEntity extends BlockEntity implements SideConfigurable {

    public static final int SLOT_DRIVE = 0;
    public static final int GRID_SLOT_START = 1;
    public static final int GRID_SIZE = 9;
    public static final int SLOT_OUTPUT = GRID_SLOT_START + GRID_SIZE; // 10
    public static final int TOTAL_SLOTS = SLOT_OUTPUT + 1; // 11

    /** Minimum ticks between crafts — throttles automation to a sane rate. */
    private static final int CRAFT_COOLDOWN = 8;

    /**
     * Low-frequency fallback poll (in ticks). The table auto-scans immediately whenever its
     * contents change (see {@link #recheckNeeded}); this poll is only a safety net for
     * changes that bypass the dirty flag, e.g. externally edited drive NBT.
     */
    private static final int FALLBACK_POLL_INTERVAL = 20;

    // ── Side IO Configuration ──

    public static final String CHANNEL_ITEMS = "items";

    private static final List<IOChannel> IO_CHANNELS = List.of(
            new IOChannel(CHANNEL_ITEMS, "gui.researchcube.channel.items", IOChannel.Type.ITEM,
                    EnumSet.of(IOMode.NONE, IOMode.INPUT, IOMode.OUTPUT, IOMode.BOTH), IOMode.BOTH)
    );

    /** Externally insertable item slots: the 3x3 grid (drive & output excluded). */
    private static final Set<Integer> INSERTABLE_ITEM_SLOTS;
    /** Externally extractable item slots: the single output slot. */
    private static final Set<Integer> EXTRACTABLE_ITEM_SLOTS = Set.of(SLOT_OUTPUT);
    static {
        Set<Integer> insertable = new HashSet<>();
        for (int i = GRID_SLOT_START; i < GRID_SLOT_START + GRID_SIZE; i++) {
            insertable.add(i);
        }
        INSERTABLE_ITEM_SLOTS = Set.copyOf(insertable);
    }

    private final SideIOConfig sideConfig = new SideIOConfig(IO_CHANNELS);

    /**
     * Set whenever inputs change (grid or drive slot) so the next server tick re-scans for a
     * matching recipe. A plain server-side flag — never saved.
     */
    private boolean recheckNeeded = true;

    /**
     * Ticks remaining before the next craft is allowed; counts down each server tick and gates
     * {@link #CRAFT_COOLDOWN}. A plain countdown (rather than an absolute game-time stamp) is
     * immune to {@code getGameTime()} overflow arithmetic and behaves correctly across world
     * reloads without needing to be persisted — it simply starts at 0 (ready) on load.
     */
    private int cooldownRemaining = 0;

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            recheckNeeded = true;
        }
    };

    public AutoDriveCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_DRIVE_CRAFTING_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /**
     * Request a recipe re-scan on the next server tick. For callers that mutate inventory
     * stacks in place (e.g. menu quick-move merges) where the handler's onContentsChanged
     * never fires.
     */
    public void markRecheckNeeded() {
        this.recheckNeeded = true;
    }

    // ── Side IO Configuration ──

    @Override
    public SideIOConfig getSideIOConfig() {
        return sideConfig;
    }

    @Override
    public List<IOChannel> getIOChannels() {
        return IO_CHANNELS;
    }

    @Override
    public Direction getIOFacing() {
        return getBlockState().getValue(AutoDriveCraftingTableBlock.FACING);
    }

    @Override
    public void onSideConfigChanged() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.invalidateCapabilities(worldPosition);
        }
    }

    @Override
    public ItemChannelSpec getItemChannelSpec() {
        // Pipe insertion is locked to the current drive's recipe layout (see canPipeInsert) so
        // hoppers can only fill cells the recipe actually uses — preventing junk from jamming a
        // shaped pattern or an empty machine with no bound drive.
        return new ItemChannelSpec(
                CHANNEL_ITEMS,
                inventory,
                INSERTABLE_ITEM_SLOTS,
                EXTRACTABLE_ITEM_SLOTS,
                this::canPipeInsert
        );
    }

    /**
     * Decide whether external automation may insert {@code stack} into grid {@code slot}.
     *
     * <p>Rules, in order:
     * <ol>
     *   <li>Topping up an existing matching stack is always allowed — that stack could only have
     *       been placed by a valid recipe layout, so refilling it keeps automation flowing.</li>
     *   <li>Otherwise a drive with at least one bound, unlocked recipe must be present; with no
     *       (or only unbound) drive, all fresh insertion is rejected so junk cannot accumulate.</li>
     *   <li>The item must be accepted at this position by at least one unlocked recipe — for a
     *       shaped recipe, some valid pattern placement (offset slide + mirror, matching
     *       {@code matchesGridOnly}) must map this cell to an ingredient that accepts the item;
     *       for a shapeless recipe, the item must satisfy some ingredient not already covered by
     *       the other grid slots.</li>
     * </ol>
     */
    private boolean canPipeInsert(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Rule 1: always allow stacking onto an existing matching item.
        ItemStack existing = inventory.getStackInSlot(slot);
        if (!existing.isEmpty()) {
            return ItemStack.isSameItemSameComponents(existing, stack);
        }

        if (level == null) return false;

        // Rule 2: a drive carrying at least one bound, unlocked recipe must be present.
        ItemStack driveStack = inventory.getStackInSlot(SLOT_DRIVE);
        if (driveStack.isEmpty() || !(driveStack.getItem() instanceof DriveItem)) {
            return false;
        }

        int gridIndex = slot - GRID_SLOT_START;
        if (gridIndex < 0 || gridIndex >= GRID_SIZE) return false;

        // Rule 3: accept if any recipe this drive unlocks would take the item at this position.
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<DriveCraftingRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.DRIVE_CRAFTING.get())) {
            DriveCraftingRecipe recipe = holder.value();
            String requiredId = recipe.getRequiredRecipeId();
            if (requiredId.isEmpty()) continue; // unbound — never governs a layout
            if (!NbtUtil.hasRecipe(driveStack, requiredId)) continue;

            if (recipe.isShaped()) {
                if (recipe.getShapedPattern() != null
                        && shapedAcceptsAt(recipe.getShapedPattern(), gridIndex, stack)) {
                    return true;
                }
            } else if (shapelessAcceptsAt(recipe, gridIndex, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a shaped pattern, placed somewhere on the 3x3 grid, maps grid cell
     * {@code gridIndex} to a non-empty ingredient that accepts {@code stack}. Tries every valid
     * offset and both mirror orientations, mirroring {@link ShapedRecipePattern#matches} (and the
     * manual table's consumption) so anything pipes fill can actually be crafted.
     */
    private static boolean shapedAcceptsAt(net.minecraft.world.item.crafting.ShapedRecipePattern pattern,
                                           int gridIndex, ItemStack stack) {
        int patternW = pattern.width();
        int patternH = pattern.height();
        int targetX = gridIndex % 3;
        int targetY = gridIndex / 3;

        for (int offsetX = 0; offsetX <= 3 - patternW; offsetX++) {
            for (int offsetY = 0; offsetY <= 3 - patternH; offsetY++) {
                for (boolean mirrored : MIRROR_OPTIONS) {
                    int px = targetX - offsetX;
                    int py = targetY - offsetY;
                    if (px < 0 || px >= patternW || py < 0 || py >= patternH) {
                        continue; // this cell is outside the pattern at this placement
                    }
                    int patternSlot = py * patternW + (mirrored ? patternW - 1 - px : px);
                    net.minecraft.world.item.crafting.Ingredient ingredient =
                            pattern.ingredients().get(patternSlot);
                    if (ingredient != null && !ingredient.isEmpty() && ingredient.test(stack)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final boolean[] MIRROR_OPTIONS = {false, true};

    /**
     * Whether a shapeless recipe would accept {@code stack} into the (empty) grid cell
     * {@code gridIndex}: the item must satisfy some ingredient that the other non-empty grid slots
     * do not already cover, so an insert always makes progress toward completing the recipe.
     */
    private boolean shapelessAcceptsAt(DriveCraftingRecipe recipe, int gridIndex, ItemStack stack) {
        NonNullList<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.getIngredients();
        boolean[] satisfied = new boolean[ingredients.size()];

        // Greedily assign the current grid contents (excluding the target cell) to ingredients.
        for (int i = 0; i < GRID_SIZE; i++) {
            if (i == gridIndex) continue;
            ItemStack gridStack = inventory.getStackInSlot(GRID_SLOT_START + i);
            if (gridStack.isEmpty()) continue;
            for (int k = 0; k < ingredients.size(); k++) {
                if (!satisfied[k] && ingredients.get(k).test(gridStack)) {
                    satisfied[k] = true;
                    break;
                }
            }
        }

        // The incoming item is acceptable only if it fills a still-unsatisfied ingredient.
        for (int k = 0; k < ingredients.size(); k++) {
            if (!satisfied[k] && ingredients.get(k).test(stack)) {
                return true;
            }
        }
        return false;
    }

    // ── Auto-crafting ──

    /**
     * Find a drive-unlocked crafting recipe whose pattern matches the current 3x3 grid.
     * Returns {@code null} if the drive is missing/unqualified or no pattern matches.
     */
    private RecipeHolder<DriveCraftingRecipe> findMatchingRecipe() {
        if (level == null) return null;

        ItemStack driveStack = inventory.getStackInSlot(SLOT_DRIVE);
        if (driveStack.isEmpty() || !(driveStack.getItem() instanceof DriveItem)) {
            return null;
        }

        if (isGridEmpty()) return null;
        CraftingInput gridInput = buildGridInput();

        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<DriveCraftingRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.DRIVE_CRAFTING.get())) {
            DriveCraftingRecipe recipe = holder.value();
            String requiredId = recipe.getRequiredRecipeId();
            if (requiredId.isEmpty()) continue; // unbound — never match
            if (!NbtUtil.hasRecipe(driveStack, requiredId)) continue;
            if (recipe.matchesGridOnly(gridInput)) {
                return holder;
            }
        }
        return null;
    }

    /** Build a 3x3 {@link CraftingInput} from the grid slots (drive excluded). */
    private CraftingInput buildGridInput() {
        List<ItemStack> items = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            items.add(inventory.getStackInSlot(GRID_SLOT_START + i));
        }
        return CraftingInput.of(3, 3, items);
    }

    /** Whether every grid slot is empty (a cheap early-out before recipe scanning). */
    private boolean isGridEmpty() {
        for (int i = 0; i < GRID_SIZE; i++) {
            if (!inventory.getStackInSlot(GRID_SLOT_START + i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Try to craft once with the current grid + drive. Silent on failure so idle tables
     * re-scanning every content change do not spam anything.
     *
     * @return true if a craft was performed
     */
    private boolean tryCraft() {
        if (level == null || level.isClientSide()) return false;

        RecipeHolder<DriveCraftingRecipe> holder = findMatchingRecipe();
        if (holder == null) return false;

        DriveCraftingRecipe recipe = holder.value();
        CraftingInput gridInput = buildGridInput();
        ItemStack result = recipe.assemble(gridInput, level.registryAccess());
        if (result.isEmpty()) return false;

        // Verify the output slot can accept the result before consuming anything.
        ItemStack outputStack = inventory.getStackInSlot(SLOT_OUTPUT);
        if (!outputStack.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(outputStack, result)) return false;
            if (outputStack.getCount() + result.getCount() > outputStack.getMaxStackSize()) return false;
        }

        // Consume ingredients. recipe.getRemainingItems() is queried for parity with the recipe
        // contract, but DriveCraftingRecipe only preserves the drive (which is not in this grid),
        // so those remainders are all empty. Container items (e.g. buckets) are handled via each
        // stack's own getCraftingRemainingItem(), matching the manual table's behaviour.
        NonNullList<ItemStack> recipeRemainders = recipe.getRemainingItems(gridInput);
        for (int i = 0; i < GRID_SIZE; i++) {
            int slot = GRID_SLOT_START + i;
            ItemStack gridStack = inventory.getStackInSlot(slot);
            if (gridStack.isEmpty()) continue;

            ItemStack recipeRemainder = i < recipeRemainders.size() ? recipeRemainders.get(i) : ItemStack.EMPTY;
            ItemStack containerRemainder = gridStack.getCraftingRemainingItem();
            // Prefer an explicit recipe remainder; otherwise fall back to the item's container item.
            ItemStack remainder = !recipeRemainder.isEmpty() ? recipeRemainder : containerRemainder;

            gridStack.shrink(1);
            if (gridStack.isEmpty()) {
                inventory.setStackInSlot(slot, remainder.isEmpty() ? ItemStack.EMPTY : remainder.copy());
            } else if (!remainder.isEmpty()) {
                // Slot still holds items but the ingredient left a remainder — drop it into the
                // world so it isn't silently lost (rare; only container items in a stacked slot).
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1,
                        worldPosition.getZ(), remainder.copy());
            }
        }

        // Insert the result into the output slot.
        if (outputStack.isEmpty()) {
            inventory.setStackInSlot(SLOT_OUTPUT, result.copy());
        } else {
            outputStack.grow(result.getCount());
        }

        setChanged();
        return true;
    }

    // ── Server Tick ──

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoDriveCraftingTableBlockEntity be) {
        // Always tick down the craft cooldown, even on ticks with no pending work, so it can never
        // get stuck (a countdown starting at CRAFT_COOLDOWN reaches 0 after that many ticks).
        if (be.cooldownRemaining > 0) {
            be.cooldownRemaining--;
            return;
        }

        boolean fallbackDue = level.getGameTime() % FALLBACK_POLL_INTERVAL == 0;
        if (!be.recheckNeeded && !fallbackDue) {
            return;
        }

        be.recheckNeeded = false;
        if (be.tryCraft()) {
            // Throttle automation to a sane rate: block further crafts for CRAFT_COOLDOWN ticks.
            be.cooldownRemaining = CRAFT_COOLDOWN;
            // Re-scan once the cooldown lapses so back-to-back crafts chain while ingredients last.
            be.recheckNeeded = true;
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

    // ── NBT Persistence ──

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("SideConfig", sideConfig.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        if (tag.contains("SideConfig")) {
            sideConfig.load(tag.getCompound("SideConfig"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
