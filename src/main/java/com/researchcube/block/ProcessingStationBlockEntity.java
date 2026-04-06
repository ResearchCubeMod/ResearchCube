package com.researchcube.block;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.ProcessingFluidStack;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.registry.ModBlockEntities;
import com.researchcube.registry.ModConfig;
import com.researchcube.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
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

import java.util.List;
import java.util.Optional;

/**
 * BlockEntity for the Processing Station.
 * 
 * Slot layout:
 *   0-15: Item inputs (16 slots)
 *   16-23: Item outputs (8 slots)
 * 
 * Fluid tanks:
 *   fluidInput1: First fluid input tank (8000 mB)
 *   fluidInput2: Second fluid input tank (8000 mB)
 *   fluidOutput: Fluid output tank (8000 mB)
 */
public class ProcessingStationBlockEntity extends BlockEntity {

    public static final int INPUT_SLOT_START = 0;
    public static final int INPUT_SLOT_COUNT = 16;
    public static final int OUTPUT_SLOT_START = 16;
    public static final int OUTPUT_SLOT_COUNT = 8;
    public static final int TOTAL_SLOTS = INPUT_SLOT_START + INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT; // 24
    public static final int TANK_CAPACITY = 8000;

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
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

    private final FluidTank fluidInput1 = new FluidTank(TANK_CAPACITY);
    private final FluidTank fluidInput2 = new FluidTank(TANK_CAPACITY);
    private final FluidTank fluidOutput = new FluidTank(TANK_CAPACITY);

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

    /**
     * Returns a combined fluid handler that exposes all three tanks for pipe interactions.
     * Tank 0: Input 1, Tank 1: Input 2, Tank 2: Output
     */
    public IFluidHandler getCombinedFluidHandler() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 3;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return switch (tank) {
                    case 0 -> fluidInput1.getFluid();
                    case 1 -> fluidInput2.getFluid();
                    case 2 -> fluidOutput.getFluid();
                    default -> FluidStack.EMPTY;
                };
            }

            @Override
            public int getTankCapacity(int tank) {
                return TANK_CAPACITY;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return tank < 2; // Only input tanks accept fluid
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                // Try to fill input tanks
                int filled = fluidInput1.fill(resource, action);
                if (filled < resource.getAmount()) {
                    FluidStack remainder = resource.copy();
                    remainder.setAmount(resource.getAmount() - filled);
                    filled += fluidInput2.fill(remainder, action);
                }
                return filled;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                // Drain from output tank only
                return fluidOutput.drain(resource, action);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                // Drain from output tank only
                return fluidOutput.drain(maxDrain, action);
            }
        };
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

    private boolean matchesRecipe(ProcessingRecipe recipe) {
        // Check item inputs (shapeless)
        List<Ingredient> ingredients = recipe.getIngredients();
        boolean[] slotUsed = new boolean[INPUT_SLOT_COUNT];

        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
                if (!slotUsed[i]) {
                    ItemStack stack = inventory.getStackInSlot(INPUT_SLOT_START + i);
                    if (ingredient.test(stack)) {
                        slotUsed[i] = true;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) return false;
        }

        // Check fluid inputs
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        if (fluidInputs.size() > 0) {
            if (!fluidInputs.get(0).matches(fluidInput1.getFluid())) {
                return false;
            }
        }
        if (fluidInputs.size() > 1) {
            if (!fluidInputs.get(1).matches(fluidInput2.getFluid())) {
                return false;
            }
        }

        // Check output space
        if (!canFitOutputs(recipe)) {
            return false;
        }

        return true;
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
     * Try to start processing with the current recipe.
     */
    public boolean tryStartProcessing() {
        if (level == null || level.isClientSide()) return false;
        if (isProcessing()) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start processing: already processing");
            return false;
        }

        RecipeHolder<ProcessingRecipe> holder = findMatchingRecipe();
        if (holder == null) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start processing: no matching recipe");
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

        level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, 1.0f);
        ResearchCubeMod.LOGGER.info("[ResearchCube] Started processing recipe '{}'", holder.id());
        return true;
    }

    private void consumeInputs(ProcessingRecipe recipe) {
        // Consume item inputs
        List<Ingredient> ingredients = recipe.getIngredients();
        boolean[] slotUsed = new boolean[INPUT_SLOT_COUNT];

        for (Ingredient ingredient : ingredients) {
            for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
                if (!slotUsed[i]) {
                    ItemStack stack = inventory.getStackInSlot(INPUT_SLOT_START + i);
                    if (ingredient.test(stack)) {
                        slotUsed[i] = true;
                        stack.shrink(1);
                        if (stack.isEmpty()) {
                            inventory.setStackInSlot(INPUT_SLOT_START + i, ItemStack.EMPTY);
                        }
                        break;
                    }
                }
            }
        }

        // Consume fluid inputs
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        if (fluidInputs.size() > 0) {
            fluidInput1.drain(fluidInputs.get(0).amount(), IFluidHandler.FluidAction.EXECUTE);
        }
        if (fluidInputs.size() > 1) {
            fluidInput2.drain(fluidInputs.get(1).amount(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // ── Server Tick ──

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessingStationBlockEntity be) {
        if (!be.isProcessing()) {
            return;
        }

        long elapsed = level.getGameTime() - be.startTime;
        int adjustedDuration = (int) (be.recipeDuration * ModConfig.getProcessingDurationMultiplier());
        
        if (elapsed >= adjustedDuration) {
            be.completeProcessing();
        }
    }

    @SuppressWarnings("unchecked")
    private void completeProcessing() {
        if (level == null || activeRecipeId == null) {
            clearProcessing();
            return;
        }

        // Find the recipe again to get outputs
        RecipeManager recipeManager = level.getRecipeManager();
        Optional<RecipeHolder<ProcessingRecipe>> holderOpt = recipeManager.byKey(activeRecipeId)
                .filter(h -> h.value() instanceof ProcessingRecipe)
                .map(h -> (RecipeHolder<ProcessingRecipe>) (RecipeHolder<?>) h);

        if (holderOpt.isEmpty()) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Processing recipe '{}' no longer exists", activeRecipeId);
            clearProcessing();
            return;
        }

        ProcessingRecipe recipe = holderOpt.get().value();

        // Produce item outputs
        List<ItemStack> outputs = recipe.getResults();
        int outputSlotIndex = 0;
        for (ItemStack output : outputs) {
            if (outputSlotIndex >= OUTPUT_SLOT_COUNT) break;
            
            ItemStack existingStack = inventory.getStackInSlot(OUTPUT_SLOT_START + outputSlotIndex);
            if (existingStack.isEmpty()) {
                inventory.setStackInSlot(OUTPUT_SLOT_START + outputSlotIndex, output.copy());
            } else {
                existingStack.grow(output.getCount());
            }
            outputSlotIndex++;
        }

        // Produce fluid output
        if (recipe.hasFluidOutput()) {
            FluidStack fluidOut = recipe.getFluidOutput().toFluidStack();
            fluidOutput.fill(fluidOut, IFluidHandler.FluidAction.EXECUTE);
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
