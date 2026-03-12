package com.researchcube.item;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.ResearchSavedData;
import com.researchcube.util.NbtUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Set;

/**
 * Research Export Chip — a single-use item that transfers research progress.
 *
 * Workflow:
 * 1. Right-click a Research Station while holding an empty chip
 *    → Opens a screen to select one completed research to encode onto the chip
 * 2. Right-click a Research Station with a loaded chip
 *    → The research is transferred to the player's (or team's) completed research pool
 *    → The chip becomes blank again (or is consumed entirely)
 *
 * NBT stored in CustomData:
 *   "ResearchId": string of the imprinted research ID
 */
public class ResearchChipItem extends Item {

    private static final String TAG_RESEARCH_ID = "ResearchId";

    public ResearchChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide() || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ResearchTableBlockEntity)) {
            return InteractionResult.PASS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        String storedId = getStoredResearchId(stack);

        if (storedId == null || storedId.isEmpty()) {
            // Empty chip — encode from player's completed research
            return encodeChip(serverPlayer, stack);
        } else {
            // Loaded chip — transfer to player
            return transferResearch(serverPlayer, stack, storedId);
        }
    }

    /**
     * Encode a research onto this chip from the player's completed research.
     * For simplicity, this uses the most recently completed research.
     * (A proper implementation would open a selection screen.)
     */
    private InteractionResult encodeChip(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        String researchKey = ResearchSavedData.getResearchKey(player);
        Set<ResourceLocation> completed = ResearchSavedData.get(serverLevel).getCompletedResearch(researchKey);

        if (completed.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("You have no completed research to encode.").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        // For now, pick the first one (in a full implementation, this would open a GUI)
        ResourceLocation toEncode = completed.iterator().next();

        setStoredResearchId(stack, toEncode.toString());
        player.displayClientMessage(
                Component.literal("Encoded: ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(toEncode.toString()).withStyle(ChatFormatting.YELLOW)),
                true
        );

        ResearchCubeMod.LOGGER.info("[ResearchCube] {} encoded research '{}' onto chip", player.getName().getString(), toEncode);
        return InteractionResult.CONSUME;
    }

    /**
     * Transfer the stored research to the player's research pool.
     */
    private InteractionResult transferResearch(ServerPlayer player, ItemStack stack, String researchIdStr) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        ResourceLocation researchId = ResourceLocation.tryParse(researchIdStr);
        if (researchId == null) {
            player.displayClientMessage(
                    Component.literal("Invalid research ID on chip.").withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        // Verify the research exists
        ResearchDefinition def = ResearchRegistry.get(researchId.toString());
        if (def == null) {
            player.displayClientMessage(
                    Component.literal("Research not found: " + researchId).withStyle(ChatFormatting.RED),
                    true
            );
            return InteractionResult.FAIL;
        }

        // Add to player's completed research
        String researchKey = ResearchSavedData.getResearchKey(player);
        ResearchSavedData savedData = ResearchSavedData.get(serverLevel);

        if (savedData.hasCompleted(researchKey, researchId)) {
            player.displayClientMessage(
                    Component.literal("You already have this research.").withStyle(ChatFormatting.YELLOW),
                    true
            );
            return InteractionResult.FAIL;
        }

        savedData.addCompleted(researchKey, researchId);
        player.displayClientMessage(
                Component.literal("Research acquired: ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(def.getDisplayName()).withStyle(ChatFormatting.YELLOW)),
                true
        );

        ResearchCubeMod.LOGGER.info("[ResearchCube] {} acquired research '{}' from chip", player.getName().getString(), researchId);

        // Clear the chip (make it reusable) or shrink the stack
        clearStoredResearchId(stack);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        String storedId = getStoredResearchId(stack);
        if (storedId == null || storedId.isEmpty()) {
            tooltipComponents.add(Component.literal("Empty").withStyle(ChatFormatting.GRAY));
            tooltipComponents.add(Component.literal("Right-click Research Station to encode").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltipComponents.add(Component.literal("Contains: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(storedId).withStyle(ChatFormatting.YELLOW)));
            
            // Try to show display name
            ResearchDefinition def = ResearchRegistry.get(storedId);
            if (def != null) {
                tooltipComponents.add(Component.literal("  → " + def.getDisplayName()).withStyle(ChatFormatting.GREEN));
            }
            
            tooltipComponents.add(Component.literal("Right-click Research Station to transfer").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        String storedId = getStoredResearchId(stack);
        return storedId != null && !storedId.isEmpty();
    }

    // ── NBT helpers via CustomData ──

    private static String getStoredResearchId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        return tag.getString(TAG_RESEARCH_ID);
    }

    private static void setStoredResearchId(ItemStack stack, String researchId) {
        CustomData existingData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = existingData.copyTag();
        tag.putString(TAG_RESEARCH_ID, researchId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearStoredResearchId(ItemStack stack) {
        CustomData existingData = stack.get(DataComponents.CUSTOM_DATA);
        if (existingData == null) return;
        CompoundTag tag = existingData.copyTag();
        tag.remove(TAG_RESEARCH_ID);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
