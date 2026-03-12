package com.researchcube.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

/**
 * A bucket item for research fluids that cannot be placed in the world.
 * These fluids are only used inside the Research Station's internal tank.
 * Prevents accidental loss of fluid by blocking world placement.
 */
public class ResearchFluidBucketItem extends BucketItem {

    public ResearchFluidBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Research fluids cannot be placed in the world.
        // They are only used in the Research Station's internal fluid tank.
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
