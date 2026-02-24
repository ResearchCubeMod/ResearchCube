package com.researchcube.item;

import com.researchcube.research.ResearchTier;
import com.researchcube.util.NbtUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A Drive item that belongs to a specific research tier.
 * Drives store recipe IDs in their custom data (NBT).
 * Drives are consumed during crafting.
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
     * Returns true if this drive is functional (not broken/IRRECOVERABLE).
     */
    public boolean isFunctional() {
        return tier.isFunctional();
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
            tooltipComponents.add(Component.literal("Stored Recipes: " + recipes.size())
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasRecipes(stack);
    }
}
