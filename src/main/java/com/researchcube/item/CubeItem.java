package com.researchcube.item;

import com.researchcube.research.ResearchTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A Cube item used for tier validation in the Research Table.
 * Cubes define the maximum research tier allowed.
 * They have no NBT data—only a fixed tier.
 */
public class CubeItem extends Item {

    private final ResearchTier tier;

    public CubeItem(ResearchTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ResearchTier getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.literal("Tier: " + tier.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Max Research Level: " + tier.getLevel())
                .withStyle(ChatFormatting.YELLOW));
    }
}
