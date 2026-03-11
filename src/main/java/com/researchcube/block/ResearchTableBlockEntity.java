package com.researchcube.block;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.CubeItem;
import com.researchcube.item.DriveItem;
import com.researchcube.registry.ModBlockEntities;
import com.researchcube.registry.ModFluids;
import com.researchcube.registry.ModItems;
import com.researchcube.research.*;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.TierUtil;
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
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Set;

/**
 * BlockEntity for the Research Table.
 * Manages active research state, item storage slots, and tick-based progress.
 *
 * Slots:
 *   0 = Drive
 *   1 = Cube
 *   2+ = Item cost inputs (expandable)
 *
 * NBT:
 *   ActiveResearch: ResourceLocation string of current research
 *   StartTime: game tick when research started
 *   Inventory: item handler contents
 */
public class ResearchTableBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final int SLOT_DRIVE = 0;
    public static final int SLOT_CUBE = 1;
    public static final int COST_SLOT_START = 2;
    public static final int SLOT_BUCKET_IN = 8;
    public static final int SLOT_BUCKET_OUT = 9;
    public static final int TOTAL_SLOTS = 10; // 2 fixed + 6 cost + 2 bucket slots
    public static final int TANK_CAPACITY = 8000; // 8 buckets (in mB)

    // GeckoLib animation
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.researchstation.idle");

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            // Override to prevent resizing when loading from older saves with fewer slots.
            // Always maintain TOTAL_SLOTS regardless of the saved "Size" value.
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

    /** Internal fluid tank for research fluids. Accepts only the 4 research fluids. */
    private final FluidTank fluidTank = new FluidTank(TANK_CAPACITY, stack -> {
        Fluid fluid = stack.getFluid();
        return fluid == ModFluids.THINKING_FLUID.get() ||
               fluid == ModFluids.PONDERING_FLUID.get() ||
               fluid == ModFluids.REASONING_FLUID.get() ||
               fluid == ModFluids.IMAGINATION_FLUID.get();
    });

    @Nullable
    private String activeResearchId = null;
    private long startTime = -1;
    @Nullable
    private String researchKey = null; // team name or UUID string of the researcher
    // Snapshot of item costs consumed when research started (for refund on cancel)
    @Nullable
    private List<ItemCost> consumedCosts = null;
    // Snapshot of fluid cost consumed when research started (for refund on cancel)
    @Nullable
    private FluidCost consumedFluidCost = null;

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_STATION.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Nullable
    public String getActiveResearchId() {
        return activeResearchId;
    }

    /**
     * Returns the ResearchTier of the cube currently in the cube slot, or null if empty/invalid.
     * Works on both client and server since inventory is synced via getUpdateTag.
     */
    @Nullable
    public ResearchTier getCubeTier() {
        ItemStack cubeStack = inventory.getStackInSlot(SLOT_CUBE);
        if (!cubeStack.isEmpty() && cubeStack.getItem() instanceof CubeItem cube) {
            return cube.getTier();
        }
        return null;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isResearching() {
        return activeResearchId != null && startTime >= 0;
    }

    /** Expose the fluid tank for capability registration and menu sync. */
    public FluidTank getFluidTank() {
        return fluidTank;
    }

    /**
     * Void all fluid in the tank. Called from WipeTankPacket handler.
     */
    public void wipeTank() {
        fluidTank.drain(fluidTank.getFluidAmount(), IFluidHandler.FluidAction.EXECUTE);
        setChanged();
    }

    /**
     * Map a bucket item to its corresponding research fluid, or null if not a research fluid bucket.
     */
    @Nullable
    private Fluid getFluidFromBucket(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ModItems.THINKING_FLUID_BUCKET.get()) return ModFluids.THINKING_FLUID.get();
        if (item == ModItems.PONDERING_FLUID_BUCKET.get()) return ModFluids.PONDERING_FLUID.get();
        if (item == ModItems.REASONING_FLUID_BUCKET.get()) return ModFluids.REASONING_FLUID.get();
        if (item == ModItems.IMAGINATION_FLUID_BUCKET.get()) return ModFluids.IMAGINATION_FLUID.get();
        return null;
    }

    /**
     * Process the bucket input slot: if it holds a research fluid bucket and the tank
     * has capacity, drain the bucket into the tank and output an empty bucket.
     */
    private void processBucketSlot() {
        ItemStack bucketIn = inventory.getStackInSlot(SLOT_BUCKET_IN);
        ItemStack bucketOut = inventory.getStackInSlot(SLOT_BUCKET_OUT);

        if (bucketIn.isEmpty()) return;

        Fluid fluid = getFluidFromBucket(bucketIn);
        if (fluid == null) return;

        // Check output slot can receive an empty bucket
        if (!bucketOut.isEmpty() && (!bucketOut.is(Items.BUCKET) || bucketOut.getCount() >= Items.BUCKET.getDefaultMaxStackSize())) {
            return;
        }

        // Try filling the tank
        FluidStack toFill = new FluidStack(fluid, 1000);
        int filled = fluidTank.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
        if (filled < 1000) return; // Not enough tank capacity

        // Execute the fill
        fluidTank.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
        bucketIn.shrink(1);

        if (bucketOut.isEmpty()) {
            inventory.setStackInSlot(SLOT_BUCKET_OUT, new ItemStack(Items.BUCKET));
        } else {
            bucketOut.grow(1);
        }
        setChanged();
    }

    // ── Research Start ──

    /**
     * Attempt to start a research. Validates all rules before starting.
     * Called from the network packet handler.
     *
     * @param researchId        the research definition ID to start
     * @param completedResearch set of research IDs the player has already completed (for prereqs)
     * @param researchKey       the research key (team name or UUID string) for progress tracking
     * @return true if research was started, false if validation failed
     */
    public boolean tryStartResearch(String researchId, Set<String> completedResearch, String researchKey) {
        if (level == null || level.isClientSide()) return false;
        if (isResearching()) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': already researching '{}'", researchId, activeResearchId);
            return false;
        }

        ResearchDefinition definition = ResearchRegistry.get(researchId);
        if (definition == null) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start unknown research: {}", researchId);
            return false;
        }

        // Validate drive & cube
        ItemStack driveStack = inventory.getStackInSlot(SLOT_DRIVE);
        ItemStack cubeStack = inventory.getStackInSlot(SLOT_CUBE);

        if (driveStack.isEmpty() || !(driveStack.getItem() instanceof DriveItem drive)) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': no drive in drive slot", researchId);
            return false;
        }
        if (cubeStack.isEmpty() || !(cubeStack.getItem() instanceof CubeItem cube)) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': no cube in cube slot", researchId);
            return false;
        }

        // Tier rules: cube.tier >= research.tier && drive.tier == research.tier
        if (!TierUtil.canResearch(cube.getTier(), drive.getTier(), definition.getTier())) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': tier mismatch. cube={}, drive={}, required={}",
                    researchId, cube.getTier(), drive.getTier(), definition.getTier());
            return false;
        }

        // Check drive capacity
        if (drive.isFull(driveStack)) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': drive is full", researchId);
            return false;
        }

        // Prerequisites
        if (!definition.getPrerequisites().isSatisfied(completedResearch)) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': prerequisites not met. completed={}", researchId, completedResearch);
            return false;
        }

        // Validate item costs (check availability before consuming)
        if (!validateItemCosts(definition.getItemCosts())) {
            ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': item costs not met. required={}", researchId, definition.getItemCosts());
            return false;
        }

        // Validate fluid cost (if defined)
        FluidCost fluidCost = definition.getFluidCost();
        if (fluidCost != null) {
            FluidStack tankContents = fluidTank.getFluid();
            if (tankContents.isEmpty() ||
                !BuiltInRegistries.FLUID.getKey(tankContents.getFluid()).equals(fluidCost.fluidId()) ||
                tankContents.getAmount() < fluidCost.amount()) {
                ResearchCubeMod.LOGGER.warn("[ResearchCube] Cannot start '{}': fluid cost not met. required={} {} mB, tank has {} {} mB",
                        researchId, fluidCost.fluidId(), fluidCost.amount(),
                        tankContents.isEmpty() ? "empty" : BuiltInRegistries.FLUID.getKey(tankContents.getFluid()),
                        tankContents.getAmount());
                return false;
            }
        }

        // All checks passed — snapshot costs for potential refund, then consume
        this.consumedCosts = definition.getItemCosts();
        this.consumedFluidCost = fluidCost;
        consumeItemCosts(definition.getItemCosts());

        // Consume fluid cost
        if (fluidCost != null) {
            FluidStack toDrain = new FluidStack(fluidCost.getFluid(), fluidCost.amount());
            fluidTank.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
        }

        // Start research
        this.activeResearchId = researchId;
        this.startTime = level.getGameTime();
        this.researchKey = researchKey;
        setChanged();

        // Play start sound (subtle click/buzz)
        level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6f, 1.2f);

        ResearchCubeMod.LOGGER.info("[ResearchCube] Started research '{}' for key {}", researchId, researchKey);
        return true;
    }

    // ── Research Ticking ──

    /**
     * Server-side tick. Checks research completion against duration.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ResearchTableBlockEntity be) {
        // Process bucket input → tank fill every tick
        be.processBucketSlot();

        if (!be.isResearching()) {
            return;
        }

        ResearchDefinition definition = ResearchRegistry.get(be.activeResearchId);
        if (definition == null) {
            // Definition was removed (datapack reload?) — cancel research
            ResearchCubeMod.LOGGER.warn("Active research '{}' no longer exists, cancelling.", be.activeResearchId);
            be.clearResearch();
            return;
        }

        // Check if the drive is still present (player might have removed it)
        ItemStack driveStack = be.inventory.getStackInSlot(SLOT_DRIVE);
        if (driveStack.isEmpty() || !(driveStack.getItem() instanceof DriveItem)) {
            ResearchCubeMod.LOGGER.debug("Drive removed during research, cancelling.");
            be.clearResearch();
            return;
        }

        long elapsed = level.getGameTime() - be.startTime;
        if (elapsed >= definition.getDuration()) {
            be.completeResearch(definition);
        }
    }

    /**
     * Complete the research: randomly select a recipe from the pool, imprint it on the drive.
     * Also records the research as completed in per-player SavedData.
     */
    private void completeResearch(ResearchDefinition definition) {
        if (level == null) return;

        ItemStack driveStack = inventory.getStackInSlot(SLOT_DRIVE);
        if (driveStack.isEmpty() || !(driveStack.getItem() instanceof DriveItem drive)) {
            ResearchCubeMod.LOGGER.error("Cannot complete research — drive missing!");
            clearResearch();
            return;
        }

        // Double-check capacity (edge case: another research completed in the meantime)
        if (drive.isFull(driveStack)) {
            ResearchCubeMod.LOGGER.warn("Drive is full at completion time for research '{}'. Recipe lost.", definition.getId());
            clearResearch();
            return;
        }

        if (definition.hasRecipePool()) {
            // Weighted random selection from recipe pool
            ResourceLocation selectedRecipe = definition.pickWeightedRecipe(level.random);

            if (selectedRecipe != null) {
                // Imprint recipe into drive
                NbtUtil.addRecipe(driveStack, selectedRecipe.toString());

                ResearchCubeMod.LOGGER.debug("Research '{}' complete. Imprinted recipe: {}",
                        definition.getId(), selectedRecipe);
            } else {
                ResearchCubeMod.LOGGER.warn("Research '{}' weighted pick returned null.", definition.getId());
            }
        } else {
            ResearchCubeMod.LOGGER.warn("Research '{}' completed but has no recipe pool.", definition.getId());
        }

        // Record completed research in SavedData (keyed by team or player UUID)
        if (researchKey != null && level instanceof ServerLevel serverLevel) {
            ResearchSavedData savedData = ResearchSavedData.get(serverLevel);
            savedData.addCompleted(researchKey, definition.getId());
            ResearchCubeMod.LOGGER.debug("Recorded research '{}' as completed for key {}",
                    definition.getId(), researchKey);
        }

        // Play completion sound and spawn particles
        BlockPos pos = worldPosition;
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    20, 0.5, 0.5, 0.5, 0.02);
            sl.sendParticles(ParticleTypes.COMPOSTER,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.05);
        }

        clearResearch();
    }

    // ── Item Cost Validation & Consumption ──

    /**
     * Check that all item costs can be satisfied from the cost input slots (slots 2-7).
     */
    private boolean validateItemCosts(List<ItemCost> costs) {
        for (ItemCost cost : costs) {
            int remaining = cost.count();
            for (int i = COST_SLOT_START; i < TOTAL_SLOTS; i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == cost.getItem()) {
                    remaining -= stack.getCount();
                    if (remaining <= 0) break;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    /**
     * Consume item costs from the cost input slots.
     * Must only be called after validateItemCosts returns true.
     */
    private void consumeItemCosts(List<ItemCost> costs) {
        for (ItemCost cost : costs) {
            int remaining = cost.count();
            for (int i = COST_SLOT_START; i < TOTAL_SLOTS && remaining > 0; i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == cost.getItem()) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                    if (stack.isEmpty()) {
                        inventory.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        }
    }

    // ── Progress Query (for UI) ──

    /**
     * Returns the research progress as a float 0.0 to 1.0.
     * Returns 0 if no research is active.
     */
    public float getProgress() {
        if (!isResearching() || level == null) return 0f;
        ResearchDefinition definition = ResearchRegistry.get(activeResearchId);
        if (definition == null) return 0f;
        long elapsed = level.getGameTime() - startTime;
        return Math.min(1.0f, (float) elapsed / definition.getDuration());
    }

    /**
     * Returns the active research definition, or null if none.
     */
    @Nullable
    public ResearchDefinition getActiveDefinition() {
        if (activeResearchId == null) return null;
        return ResearchRegistry.get(activeResearchId);
    }

    // ── State Management ──

    /**
     * Clears the active research (used on completion or cancellation).
     */
    public void clearResearch() {
        this.activeResearchId = null;
        this.startTime = -1;
        this.researchKey = null;
        this.consumedCosts = null;
        this.consumedFluidCost = null;
        setChanged();
    }

    /**
     * Cancel active research and refund item costs back into cost slots.
     * Called from CancelResearchPacket handler.
     */
    public void cancelResearchWithRefund() {
        if (!isResearching()) return;

        // Refund consumed costs into the cost slots
        if (consumedCosts != null) {
            for (ItemCost cost : consumedCosts) {
                int remaining = cost.count();
                ItemStack refundStack = new ItemStack(cost.getItem(), remaining);

                for (int i = COST_SLOT_START; i < SLOT_BUCKET_IN && !refundStack.isEmpty(); i++) {
                    refundStack = inventory.insertItem(i, refundStack, false);
                }

                // If slots are full, items are lost (edge case — player filled slots)
                if (!refundStack.isEmpty()) {
                    ResearchCubeMod.LOGGER.warn("Could not fully refund {} x{} during cancel — {} lost",
                            cost.itemId(), cost.count(), refundStack.getCount());
                }
            }
        }

        // Refund consumed fluid cost back into the tank
        if (consumedFluidCost != null) {
            FluidStack refund = new FluidStack(consumedFluidCost.getFluid(), consumedFluidCost.amount());
            int refunded = fluidTank.fill(refund, IFluidHandler.FluidAction.EXECUTE);
            if (refunded < consumedFluidCost.amount()) {
                ResearchCubeMod.LOGGER.warn("Could not fully refund fluid {} — {} mB lost",
                        consumedFluidCost.fluidId(), consumedFluidCost.amount() - refunded);
            }
        }

        ResearchCubeMod.LOGGER.debug("Research '{}' cancelled with refund.", activeResearchId);
        clearResearch();
    }

    /**
     * Drop all inventory contents when block is broken.
     */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(i));
        }
    }

    // ── NBT Persistence ──

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("FluidTank", fluidTank.writeToNBT(registries, new CompoundTag()));
        if (activeResearchId != null) {
            tag.putString("ActiveResearch", activeResearchId);
            tag.putLong("StartTime", startTime);
            if (researchKey != null) {
                tag.putString("ResearchKey", researchKey);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("FluidTank")) {
            fluidTank.readFromNBT(registries, tag.getCompound("FluidTank"));
        }
        if (tag.contains("ActiveResearch")) {
            activeResearchId = tag.getString("ActiveResearch");
            startTime = tag.getLong("StartTime");
            // Support both new "ResearchKey" and legacy "ResearcherUUID" fields
            if (tag.contains("ResearchKey")) {
                researchKey = tag.getString("ResearchKey");
            } else if (tag.hasUUID("ResearcherUUID")) {
                researchKey = tag.getUUID("ResearcherUUID").toString();
            } else {
                researchKey = null;
            }
        } else {
            activeResearchId = null;
            startTime = -1;
            researchKey = null;
        }
        // consumedCosts/consumedFluidCost are not persisted — on reload, cancel will not refund
        consumedCosts = null;
        consumedFluidCost = null;
    }

    // ── Client Sync ──

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

    // ── GeckoLib Animation ──

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> {
            state.setAnimation(IDLE_ANIM);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
