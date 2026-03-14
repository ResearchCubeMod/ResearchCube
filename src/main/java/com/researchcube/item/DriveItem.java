package com.researchcube.item;

import com.researchcube.research.ResearchTier;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.RecipeOutputResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Drive item that belongs to a specific research tier.
 * Drives store recipe IDs in their custom data (NBT).
 * Drives persist after crafting — the used recipe ID is stripped from the drive's NBT.
 * IRRECOVERABLE tier drives are decorative only.
 */
public class DriveItem extends Item {

    private final ResearchTier tier;

    public DriveItem(ResearchTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ResearchTier getTier() {
        return tier;
    }

    /**
     * Returns true if this drive has at least one recipe stored.
     */
    public boolean hasRecipes(ItemStack stack) {
        return !NbtUtil.readRecipes(stack).isEmpty();
    }

    /**
     * Returns true if this drive is at max recipe capacity.
     */
    public boolean isFull(ItemStack stack) {
        if (!tier.hasRecipeLimit()) return false;
        return NbtUtil.readRecipes(stack).size() >= tier.getMaxRecipes();
    }

    /**
     * Returns the max number of recipes this drive can hold, or -1 for unlimited.
     */
    public int getMaxRecipes() {
        return tier.getMaxRecipes();
    }

    /**
     * Returns true if this drive is functional (not broken/IRRECOVERABLE).
     */
    public boolean isFunctional() {
        return tier.isFunctional();
    }

    /**
     * Right-click a filled drive in hand (not on a block) to open the Drive Inspector screen.
     * Only works client-side when the drive has recipes.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && hasRecipes(stack)) {
            openInspectorScreen(stack);
            return InteractionResultHolder.success(stack);
        }
        if (hasRecipes(stack)) {
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** Client-only method to open the inspector screen. */
    private void openInspectorScreen(ItemStack stack) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.researchcube.client.screen.DriveInspectorScreen(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.literal("Tier: " + tier.getDisplayName())
                .withStyle(ChatFormatting.GRAY));

        if (!tier.isFunctional()) {
            tooltipComponents.add(Component.literal("Broken - Decorative Only")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }

        List<String> recipes = NbtUtil.readRecipes(stack);
        if (recipes.isEmpty()) {
            tooltipComponents.add(Component.literal("Empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            String capacityStr = tier.hasRecipeLimit()
                    ? recipes.size() + "/" + tier.getMaxRecipes()
                    : recipes.size() + " (unlimited)";
            tooltipComponents.add(Component.literal("Stored Recipes: " + capacityStr)
                    .withStyle(ChatFormatting.AQUA));
            for (String recipe : recipes) {
                String resolved = RecipeOutputResolver.formatOutput(recipe);
                if (!resolved.equals(recipe)) {
                    // Resolved to an output item name
                    tooltipComponents.add(Component.literal("  \u2022 " + resolved)
                            .withStyle(ChatFormatting.GREEN));
                    if (tooltipFlag.isAdvanced()) {
                        tooltipComponents.add(Component.literal("    (" + recipe + ")")
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                } else {
                    // Fallback to raw ID
                    tooltipComponents.add(Component.literal("  \u2022 " + recipe)
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }

        if (isFull(stack)) {
            tooltipComponents.add(Component.literal("FULL")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasRecipes(stack);
    }
}
