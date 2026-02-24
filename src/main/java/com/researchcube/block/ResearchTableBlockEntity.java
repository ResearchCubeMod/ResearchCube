package com.researchcube.block;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.CubeItem;
import com.researchcube.item.DriveItem;
import com.researchcube.registry.ModBlockEntities;
import com.researchcube.research.*;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.TierUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
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
    public static final int TOTAL_SLOTS = 8; // 2 fixed + 6 cost input slots

    // GeckoLib animation
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.researchstation.idle");

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    @Nullable
    private String activeResearchId = null;
    private long startTime = -1;

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

    public long getStartTime() {
        return startTime;
    }

    public boolean isResearching() {
        return activeResearchId != null && startTime >= 0;
    }

    // ── Research Start ──

    /**
     * Attempt to start a research. Validates all rules before starting.
     * Called from the menu/screen when the player clicks "Start".
     *
     * @param researchId    the research definition ID to start
     * @param completedResearch set of research IDs the player has already completed (for prereqs)
     * @return true if research was started, false if validation failed
     */
    public boolean tryStartResearch(String researchId, Set<String> completedResearch) {
        if (level == null || level.isClientSide()) return false;
        if (isResearching()) return false;

        ResearchDefinition definition = ResearchRegistry.get(researchId);
        if (definition == null) {
            ResearchCubeMod.LOGGER.warn("Cannot start unknown research: {}", researchId);
            return false;
        }

        // Validate drive & cube
        ItemStack driveStack = inventory.getStackInSlot(SLOT_DRIVE);
        ItemStack cubeStack = inventory.getStackInSlot(SLOT_CUBE);

        if (driveStack.isEmpty() || !(driveStack.getItem() instanceof DriveItem drive)) return false;
        if (cubeStack.isEmpty() || !(cubeStack.getItem() instanceof CubeItem cube)) return false;

        // Tier rules: cube.tier >= research.tier && drive.tier == research.tier
        if (!TierUtil.canResearch(cube.getTier(), drive.getTier(), definition.getTier())) {
            return false;
        }

        // Prerequisites
        if (!definition.getPrerequisites().isSatisfied(completedResearch)) {
            return false;
        }

        // Validate item costs (check availability before consuming)
        if (!validateItemCosts(definition.getItemCosts())) {
            return false;
        }

        // All checks passed — consume item costs
        consumeItemCosts(definition.getItemCosts());

        // Start research
        this.activeResearchId = researchId;
        this.startTime = level.getGameTime();
        setChanged();

        ResearchCubeMod.LOGGER.debug("Started research '{}' at tick {}", researchId, startTime);
        return true;
    }

    // ── Research Ticking ──

    /**
     * Server-side tick. Checks research completion against duration.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ResearchTableBlockEntity be) {
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
     */
    private void completeResearch(ResearchDefinition definition) {
        if (level == null) return;

        ItemStack driveStack = inventory.getStackInSlot(SLOT_DRIVE);
        if (driveStack.isEmpty() || !(driveStack.getItem() instanceof DriveItem)) {
            ResearchCubeMod.LOGGER.error("Cannot complete research — drive missing!");
            clearResearch();
            return;
        }

        if (definition.hasRecipePool()) {
            // Uniform random selection from recipe pool
            List<ResourceLocation> pool = definition.getRecipePool();
            ResourceLocation selectedRecipe = pool.get(level.random.nextInt(pool.size()));

            // Imprint recipe into drive
            NbtUtil.addRecipe(driveStack, selectedRecipe.toString());

            ResearchCubeMod.LOGGER.debug("Research '{}' complete. Imprinted recipe: {}",
                    definition.getId(), selectedRecipe);
        } else {
            ResearchCubeMod.LOGGER.warn("Research '{}' completed but has no recipe pool.", definition.getId());
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
        setChanged();
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
        if (activeResearchId != null) {
            tag.putString("ActiveResearch", activeResearchId);
            tag.putLong("StartTime", startTime);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("ActiveResearch")) {
            activeResearchId = tag.getString("ActiveResearch");
            startTime = tag.getLong("StartTime");
        } else {
            activeResearchId = null;
            startTime = -1;
        }
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
