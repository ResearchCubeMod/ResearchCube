package com.researchcube.block;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.DriveItem;
import com.researchcube.recipe.ProcessingFluidStack;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.registry.ModBlockEntities;
import com.researchcube.registry.ModConfig;
import com.researchcube.registry.ModRecipeTypes;
import com.researchcube.sideio.FluidChannelSpec;
import com.researchcube.sideio.IOChannel;
import com.researchcube.sideio.IOMode;
import com.researchcube.sideio.ItemChannelSpec;
import com.researchcube.sideio.SideConfigurable;
import com.researchcube.sideio.SideIOConfig;
import com.researchcube.util.NbtUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BlockEntity for the Processing Station.
 *
 * Slot layout:
 *   0-15: Item inputs (16 slots)
 *   16-23: Item outputs (8 slots)
 *   24: Drive (research unlock carrier — recipes only start when the inserted
 *       drive carries the recipe's required recipe_id; never consumed)
 *
 * Fluid tanks:
 *   fluidInput1: First fluid input tank (8000 mB)
 *   fluidInput2: Second fluid input tank (8000 mB)
 *   fluidOutput: Fluid output tank (8000 mB)
 */
public class ProcessingStationBlockEntity extends BlockEntity implements SideConfigurable {

    public static final int INPUT_SLOT_START = 0;
    public static final int INPUT_SLOT_COUNT = 16;
    public static final int OUTPUT_SLOT_START = 16;
    public static final int OUTPUT_SLOT_COUNT = 8;
    // Drive slot appended AFTER the original 24 slots so existing world NBT keeps its indices.
    public static final int SLOT_DRIVE = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT; // 24
    public static final int TOTAL_SLOTS = SLOT_DRIVE + 1; // 25
    public static final int TANK_CAPACITY = 8000;

    /**
     * Low-frequency fallback poll interval (in ticks). The station auto-scans immediately
     * whenever its contents change (see {@link #recheckNeeded}); this poll is only a safety
     * net for changes that bypass the dirty flag, e.g. externally edited drive NBT.
     */
    private static final int FALLBACK_POLL_INTERVAL = 20;

    // ── Side IO Configuration ──

    public static final String CHANNEL_ITEMS = "items";
    public static final String CHANNEL_FLUID_IN_1 = "fluid_input_1";
    public static final String CHANNEL_FLUID_IN_2 = "fluid_input_2";
    public static final String CHANNEL_FLUID_OUT = "fluid_output";

    private static final List<IOChannel> IO_CHANNELS = List.of(
            new IOChannel(CHANNEL_ITEMS, "gui.researchcube.channel.items", IOChannel.Type.ITEM,
                    EnumSet.of(IOMode.NONE, IOMode.INPUT, IOMode.OUTPUT, IOMode.BOTH), IOMode.BOTH),
            new IOChannel(CHANNEL_FLUID_IN_1, "gui.researchcube.channel.fluid_input_1", IOChannel.Type.FLUID,
                    EnumSet.of(IOMode.NONE, IOMode.INPUT, IOMode.OUTPUT, IOMode.BOTH), IOMode.INPUT),
            new IOChannel(CHANNEL_FLUID_IN_2, "gui.researchcube.channel.fluid_input_2", IOChannel.Type.FLUID,
                    EnumSet.of(IOMode.NONE, IOMode.INPUT, IOMode.OUTPUT, IOMode.BOTH), IOMode.INPUT),
            new IOChannel(CHANNEL_FLUID_OUT, "gui.researchcube.channel.fluid_output", IOChannel.Type.FLUID,
                    EnumSet.of(IOMode.NONE, IOMode.OUTPUT), IOMode.OUTPUT)
    );

    /** Externally insertable item slots: the 16 input slots. */
    private static final Set<Integer> INSERTABLE_ITEM_SLOTS;
    /** Externally extractable item slots: the 8 output slots. */
    private static final Set<Integer> EXTRACTABLE_ITEM_SLOTS;
    static {
        Set<Integer> insertable = new HashSet<>();
        for (int i = INPUT_SLOT_START; i < INPUT_SLOT_START + INPUT_SLOT_COUNT; i++) {
            insertable.add(i);
        }
        INSERTABLE_ITEM_SLOTS = Set.copyOf(insertable);

        Set<Integer> extractable = new HashSet<>();
        for (int i = OUTPUT_SLOT_START; i < OUTPUT_SLOT_START + OUTPUT_SLOT_COUNT; i++) {
            extractable.add(i);
        }
        EXTRACTABLE_ITEM_SLOTS = Set.copyOf(extractable);
    }

    private final SideIOConfig sideConfig = new SideIOConfig(IO_CHANNELS);

    /**
     * Set whenever inputs change (item slots, drive slot, or fluid tanks) so the next server
     * tick re-scans for a matching recipe while idle. A plain server-side flag — never saved.
     */
    private boolean recheckNeeded = true;

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            recheckNeeded = true;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < getSlots(); i++) {
                stacks.set(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < tagList.size(); i++) {
                CompoundTag itemTag = tagList.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < getSlots()) {
                    stacks.set(slot, ItemStack.parseOptional(provider, itemTag));
                }
            }
            onLoad();
        }
    };

    /** FluidTank that flags a recheck on every content change (fill/drain/load). */
    private final class RecheckFluidTank extends FluidTank {
        RecheckFluidTank(int capacity) {
            super(capacity);
        }

        @Override
        protected void onContentsChanged() {
            recheckNeeded = true;
            // Persist tank changes that arrive outside the GUI/recipe path (e.g. an external
            // pipe filling the tank). The menu handles pushing contents to open screens itself.
            setChanged();
        }
    }

    private final FluidTank fluidInput1 = new RecheckFluidTank(TANK_CAPACITY);
    private final FluidTank fluidInput2 = new RecheckFluidTank(TANK_CAPACITY);
    private final FluidTank fluidOutput = new RecheckFluidTank(TANK_CAPACITY);

    @Nullable
    private ResourceLocation activeRecipeId = null;
    private long startTime = -1;
    private int recipeDuration = 0;

    public ProcessingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROCESSING_STATION.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public FluidTank getFluidInput1() {
        return fluidInput1;
    }

    public FluidTank getFluidInput2() {
        return fluidInput2;
    }

    public FluidTank getFluidOutput() {
        return fluidOutput;
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
        return getBlockState().getValue(ProcessingStationBlock.FACING);
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
        return new ItemChannelSpec(
                CHANNEL_ITEMS,
                inventory,
                INSERTABLE_ITEM_SLOTS,
                EXTRACTABLE_ITEM_SLOTS,
                null // no per-slot item filter on the processing station
        );
    }

    @Override
    public List<FluidChannelSpec> getFluidChannelSpecs() {
        return List.of(
                new FluidChannelSpec(CHANNEL_FLUID_IN_1, fluidInput1),
                new FluidChannelSpec(CHANNEL_FLUID_IN_2, fluidInput2),
                new FluidChannelSpec(CHANNEL_FLUID_OUT, fluidOutput)
        );
    }

    /**
     * Flag that inputs may have changed so the next server tick re-scans for a matching recipe.
     * Exposed for callers that mutate the backing inventory in a way that bypasses
     * {@link ItemStackHandler#onContentsChanged} — notably a menu's in-place shift-click merge,
     * which shrinks/grows the backing stack directly and only fires {@code Slot.setChanged()}
     * (a no-op for {@link net.neoforged.neoforge.items.SlotItemHandler}).
     */
    public void markRecheckNeeded() {
        this.recheckNeeded = true;
    }

    public boolean isProcessing() {
        return activeRecipeId != null && startTime >= 0;
    }

    public float getProgress() {
        if (!isProcessing() || level == null || recipeDuration <= 0) return 0f;
        long elapsed = level.getGameTime() - startTime;
        int adjustedDuration = (int) (recipeDuration * ModConfig.getProcessingDurationMultiplier());
        if (adjustedDuration <= 0) return 0f;
        return Math.min(1.0f, (float) elapsed / adjustedDuration);
    }

    @Nullable
    public ResourceLocation getActiveRecipeId() {
        return activeRecipeId;
    }

    // ── Recipe Matching ──

    /**
     * Find a matching processing recipe for the current inputs.
     */
    @Nullable
    public RecipeHolder<ProcessingRecipe> findMatchingRecipe() {
        if (level == null) return null;

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ProcessingRecipe>> allRecipes = recipeManager.getAllRecipesFor(ModRecipeTypes.PROCESSING.get());

        for (RecipeHolder<ProcessingRecipe> holder : allRecipes) {
            ProcessingRecipe recipe = holder.value();
            if (matchesRecipe(recipe)) {
                return holder;
            }
        }
        return null;
    }

    /**
     * Whether the drive in {@link #SLOT_DRIVE} unlocks the given recipe.
     * Mirrors {@code DriveCraftingRecipe.matches}: an unbound (empty) recipe_id never
     * matches, so a mis-configured recipe can't accidentally run drive-free.
     */
    private boolean hasUnlockedDrive(ProcessingRecipe recipe) {
        String requiredId = recipe.getRequiredRecipeId();
        if (requiredId.isEmpty()) {
            return false; // not bound yet — never match
        }
        ItemStack driveStack = inventory.getStackInSlot(SLOT_DRIVE);
        return !driveStack.isEmpty()
                && driveStack.getItem() instanceof DriveItem
                && NbtUtil.hasRecipe(driveStack, requiredId);
    }

    private boolean matchesRecipe(ProcessingRecipe recipe) {
        // Research lock: the inserted drive must carry this recipe's ID.
        // Checked at start only — the drive is never consumed, and pulling it
        // mid-process does not abort a running job.
        if (!hasUnlockedDrive(recipe)) {
            return false;
        }

        // Check item inputs (shapeless, count-based first-fit). Track remaining counts per
        // slot so several ingredients can draw from the same stack — e.g. a recipe needing
        // 2x iron matches a single slot holding a stack of 2+ iron ingots.
        List<Ingredient> ingredients = recipe.getIngredients();
        int[] remaining = new int[INPUT_SLOT_COUNT];
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            remaining[i] = inventory.getStackInSlot(INPUT_SLOT_START + i).getCount();
        }

        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
                if (remaining[i] > 0) {
                    ItemStack stack = inventory.getStackInSlot(INPUT_SLOT_START + i);
                    if (ingredient.test(stack)) {
                        remaining[i]--;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return false;
        }

        // Check fluid inputs (order-tolerant — see matchFluidAssignment). A null assignment
        // means the recipe's fluids can't be satisfied by the current tanks in either order.
        if (matchFluidAssignment(recipe) == null) {
            return false;
        }

        // Check output space
        if (!canFitOutputs(recipe)) {
            return false;
        }

        return true;
    }

    /**
     * Resolve which input tank supplies each of the recipe's fluid inputs, order-tolerant.
     *
     * <p>The two input tanks are interchangeable, so a recipe must match regardless of which
     * physical tank holds which fluid. This returns a mapping {@code assignment[i] = tankIndex}
     * (0 = {@link #fluidInput1}, 1 = {@link #fluidInput2}) for each recipe fluid input {@code i},
     * such that every fluid matches its assigned tank (correct type and {@code >=} the required
     * amount, per {@link ProcessingFluidStack#matches}). {@link #consumeInputs} drains this exact
     * mapping so matching and consumption can never disagree.
     *
     * <ul>
     *   <li>0 fluids → empty assignment (always satisfiable).</li>
     *   <li>1 fluid → tank1 if it matches, else tank2, else {@code null}.</li>
     *   <li>2 fluids → try (fluid0→tank1, fluid1→tank2); if that fails try the swapped
     *       assignment (fluid0→tank2, fluid1→tank1); else {@code null}. A fluid input is never
     *       assigned to both tanks, so two recipe fluids always draw from distinct tanks.</li>
     * </ul>
     *
     * @return the tank index per fluid input, or {@code null} if no assignment satisfies the recipe
     */
    @Nullable
    private int[] matchFluidAssignment(ProcessingRecipe recipe) {
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        FluidStack tank1 = fluidInput1.getFluid();
        FluidStack tank2 = fluidInput2.getFluid();

        if (fluidInputs.isEmpty()) {
            return new int[0];
        }

        if (fluidInputs.size() == 1) {
            ProcessingFluidStack f0 = fluidInputs.get(0);
            if (f0.matches(tank1)) return new int[]{0};
            if (f0.matches(tank2)) return new int[]{1};
            return null;
        }

        // Two fluid inputs: try both tank assignments so swapped tanks still match.
        ProcessingFluidStack f0 = fluidInputs.get(0);
        ProcessingFluidStack f1 = fluidInputs.get(1);
        if (f0.matches(tank1) && f1.matches(tank2)) {
            return new int[]{0, 1};
        }
        if (f0.matches(tank2) && f1.matches(tank1)) {
            return new int[]{1, 0};
        }
        return null;
    }

    /** Resolve one of the two input tanks by index (0 = input 1, 1 = input 2). */
    private FluidTank fluidTankByIndex(int tankIndex) {
        return tankIndex == 0 ? fluidInput1 : fluidInput2;
    }

    private boolean canFitOutputs(ProcessingRecipe recipe) {
        // Check item outputs
        List<ItemStack> outputs = recipe.getResults();
        int outputSlotIndex = 0;
        for (ItemStack output : outputs) {
            if (outputSlotIndex >= OUTPUT_SLOT_COUNT) return false;
            
            ItemStack existingStack = inventory.getStackInSlot(OUTPUT_SLOT_START + outputSlotIndex);
            if (!existingStack.isEmpty()) {
                if (!ItemStack.isSameItemSameComponents(existingStack, output)) {
                    return false;
                }
                if (existingStack.getCount() + output.getCount() > existingStack.getMaxStackSize()) {
                    return false;
                }
            }
            outputSlotIndex++;
        }

        // Check fluid output
        if (recipe.hasFluidOutput()) {
            ProcessingFluidStack fluidOut = recipe.getFluidOutput();
            FluidStack existingFluid = fluidOutput.getFluid();
            if (!existingFluid.isEmpty()) {
                if (!BuiltInRegistries.FLUID.getKey(existingFluid.getFluid()).equals(fluidOut.fluidId())) {
                    return false;
                }
                if (existingFluid.getAmount() + fluidOut.amount() > TANK_CAPACITY) {
                    return false;
                }
            }
        }

        return true;
    }

    // ── Processing Control ──

    /**
     * Try to start processing with the current recipe (logs failures loudly).
     */
    public boolean tryStartProcessing() {
        return tryStartProcessing(false);
    }

    /**
     * Try to start processing with the current recipe.
     *
     * @param quiet when true (automatic scans), failures are silent — no warn log — so idle
     *              machines re-scanning every content change do not spam the log.
     * @return true if processing started
     */
    public boolean tryStartProcessing(boolean quiet) {
        if (level == null || level.isClientSide()) return false;
        if (isProcessing()) {
            if (!quiet) ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start processing: already processing");
            return false;
        }

        RecipeHolder<ProcessingRecipe> holder = findMatchingRecipe();
        if (holder == null) {
            if (!quiet) ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start processing: no matching recipe");
            return false;
        }

        ProcessingRecipe recipe = holder.value();

        // Consume inputs
        consumeInputs(recipe);

        // Start processing
        this.activeRecipeId = holder.id();
        this.startTime = level.getGameTime();
        this.recipeDuration = recipe.getDuration();
        setChanged();

        // No start sound: under automation this fires constantly. Kept at debug so the
        // automatic scan loop does not spam the log on every craft.
        ResearchCubeMod.LOGGER.debug("[ResearchCube] Started processing recipe '{}'", holder.id());
        return true;
    }

    private void consumeInputs(ProcessingRecipe recipe) {
        // Consume item inputs. Mirrors matchesRecipe: count-based first-fit so multiple
        // ingredients can draw from the same stack. Track remaining counts per slot so we
        // never over-draw a slot that has already been partially consumed this call.
        List<Ingredient> ingredients = recipe.getIngredients();
        int[] remaining = new int[INPUT_SLOT_COUNT];
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            remaining[i] = inventory.getStackInSlot(INPUT_SLOT_START + i).getCount();
        }

        for (Ingredient ingredient : ingredients) {
            for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
                if (remaining[i] > 0) {
                    ItemStack stack = inventory.getStackInSlot(INPUT_SLOT_START + i);
                    if (ingredient.test(stack)) {
                        remaining[i]--;
                        stack.shrink(1);
                        if (stack.isEmpty()) {
                            inventory.setStackInSlot(INPUT_SLOT_START + i, ItemStack.EMPTY);
                        }
                        break;
                    }
                }
            }
        }

        // Consume fluid inputs from the SAME tanks that matched. Recompute the assignment from
        // the current tank state (still the matched state — no fluid has been drained yet); it
        // must resolve because consumeInputs only runs after findMatchingRecipe() matched. If it
        // somehow doesn't (e.g. tanks changed between the match and here), skip fluid draining
        // rather than drain the wrong tanks.
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        int[] assignment = matchFluidAssignment(recipe);
        if (assignment != null) {
            for (int i = 0; i < fluidInputs.size(); i++) {
                fluidTankByIndex(assignment[i]).drain(fluidInputs.get(i).amount(), IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    // ── Server Tick ──

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessingStationBlockEntity be) {
        if (!be.isProcessing()) {
            // Auto-scan: attempt to start a matching recipe when a content change flagged a
            // recheck, or periodically as a fallback for changes that bypass the flag (e.g.
            // externally edited drive NBT). Attempts are quiet — no warn logs, no failure sfx.
            boolean fallbackDue = level.getGameTime() % FALLBACK_POLL_INTERVAL == 0;
            if (be.recheckNeeded || fallbackDue) {
                be.recheckNeeded = false;
                be.tryStartProcessing(true);
            }
            return;
        }

        long elapsed = level.getGameTime() - be.startTime;
        int adjustedDuration = (int) (be.recipeDuration * ModConfig.getProcessingDurationMultiplier());

        if (elapsed >= adjustedDuration) {
            be.completeProcessing();
            // Re-scan next tick so back-to-back crafts chain automatically once outputs/inputs
            // have changed. (completeProcessing already flags via inventory changes, but a
            // recipe with no net inventory-slot change would otherwise stall.)
            be.recheckNeeded = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void completeProcessing() {
        if (level == null || activeRecipeId == null) {
            clearProcessing();
            return;
        }

        // Find the recipe again to get outputs. Cast the recipe value (a concrete class)
        // rather than the RecipeHolder's type parameter, so the downcast is checked.
        RecipeManager recipeManager = level.getRecipeManager();
        ProcessingRecipe recipe = recipeManager.byKey(activeRecipeId)
                .map(RecipeHolder::value)
                .filter(ProcessingRecipe.class::isInstance)
                .map(ProcessingRecipe.class::cast)
                .orElse(null);

        if (recipe == null) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Processing recipe '{}' no longer exists", activeRecipeId);
            clearProcessing();
            return;
        }

        // Produce item outputs. Re-validate every target slot at completion — canFitOutputs
        // only checked at start, and a /reload can swap the recipe mid-run. Without this recheck
        // a blind grow() could transmute a different item now sitting in the slot, or overflow
        // past maxStackSize. For each output: empty slot → drop a copy in; same item+components
        // with room → grow within maxStackSize; anything that no longer fits (wrong item, or
        // over capacity) is dropped in the world rather than voided or corrupted.
        List<ItemStack> outputs = recipe.getResults();
        int outputSlotIndex = 0;
        for (ItemStack output : outputs) {
            if (outputSlotIndex >= OUTPUT_SLOT_COUNT) {
                // No slot left for this output — drop it so it is neither voided nor lost.
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                        worldPosition.getZ(), output.copy());
                outputSlotIndex++;
                continue;
            }

            int slot = OUTPUT_SLOT_START + outputSlotIndex;
            ItemStack existingStack = inventory.getStackInSlot(slot);
            if (existingStack.isEmpty()) {
                inventory.setStackInSlot(slot, output.copy());
            } else if (ItemStack.isSameItemSameComponents(existingStack, output)
                    && existingStack.getCount() + output.getCount() <= existingStack.getMaxStackSize()) {
                existingStack.grow(output.getCount());
                inventory.setStackInSlot(slot, existingStack); // flag the change (grow() mutates in place)
            } else {
                // Slot now holds a different item, or growing would overflow the stack: drop the
                // output in the world instead of transmuting/overflowing the existing stack.
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                        worldPosition.getZ(), output.copy());
            }
            outputSlotIndex++;
        }

        // Produce fluid output. fill() already enforces tank capacity, and it no-ops when the
        // incoming fluid does not match a non-empty tank's current fluid (FluidTank.fill returns
        // 0 for a mismatched fluid). So a recipe swapped mid-run to a different output fluid is
        // never silently voided into the tank — it just fails to fill. Guard it explicitly and
        // warn so the mismatch is diagnosable rather than a silent no-op.
        if (recipe.hasFluidOutput()) {
            FluidStack fluidOut = recipe.getFluidOutput().toFluidStack();
            FluidStack existingFluid = fluidOutput.getFluid();
            if (!existingFluid.isEmpty() && !FluidStack.isSameFluidSameComponents(existingFluid, fluidOut)) {
                // Different fluid already in the output tank: fill() would no-op anyway. Skip and
                // warn rather than pretend it succeeded (the output fluid for this craft is lost).
                ResearchCubeMod.LOGGER.warn(
                        "[ResearchCube] Fluid output for recipe '{}' ({}) does not match tank contents ({}); skipping",
                        activeRecipeId, BuiltInRegistries.FLUID.getKey(fluidOut.getFluid()),
                        BuiltInRegistries.FLUID.getKey(existingFluid.getFluid()));
            } else {
                fluidOutput.fill(fluidOut, IFluidHandler.FluidAction.EXECUTE);
            }
        }

        // Effects
        BlockPos effPos = worldPosition;
        level.playSound(null, effPos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5f, 1.2f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    effPos.getX() + 0.5, effPos.getY() + 1.0, effPos.getZ() + 0.5,
                    10, 0.4, 0.3, 0.4, 0.02);
        }

        ResearchCubeMod.LOGGER.debug("[ResearchCube] Completed processing recipe '{}'", activeRecipeId);
        clearProcessing();
    }

    private void clearProcessing() {
        this.activeRecipeId = null;
        this.startTime = -1;
        this.recipeDuration = 0;
        setChanged();
    }

    // ── Drop Contents ──

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
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
        
        // Fluid tanks
        tag.put("FluidInput1", fluidInput1.writeToNBT(registries, new CompoundTag()));
        tag.put("FluidInput2", fluidInput2.writeToNBT(registries, new CompoundTag()));
        tag.put("FluidOutput", fluidOutput.writeToNBT(registries, new CompoundTag()));

        // Processing state
        tag.putString("ActiveRecipeId", activeRecipeId != null ? activeRecipeId.toString() : "");
        tag.putLong("StartTime", startTime);
        tag.putInt("RecipeDuration", recipeDuration);

        // Side IO
        tag.put("SideConfig", sideConfig.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));

        // Fluid tanks
        fluidInput1.readFromNBT(registries, tag.getCompound("FluidInput1"));
        fluidInput2.readFromNBT(registries, tag.getCompound("FluidInput2"));
        fluidOutput.readFromNBT(registries, tag.getCompound("FluidOutput"));

        // Processing state
        String recipeIdStr = tag.getString("ActiveRecipeId");
        activeRecipeId = recipeIdStr.isEmpty() ? null : ResourceLocation.tryParse(recipeIdStr);
        startTime = tag.getLong("StartTime");
        recipeDuration = tag.getInt("RecipeDuration");

        // Side IO. (Old worlds may still carry an "AutoMode" tag from a removed feature —
        // it is simply ignored.)
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
