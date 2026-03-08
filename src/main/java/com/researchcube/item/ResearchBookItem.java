package com.researchcube.item;

import com.researchcube.ResearchCubeMod;
import com.researchcube.network.OpenResearchBookPacket;
import com.researchcube.research.ResearchSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

/**
 * A read-only research encyclopedia item.
 * Right-click opens a screen showing all research definitions and player progress.
 * The server sends the player's completed research to the client when used.
 */
public class ResearchBookItem extends Item {

    public ResearchBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Look up completed research and send to client
            if (level instanceof ServerLevel sl) {
                ResearchSavedData savedData = ResearchSavedData.get(sl);
                String researchKey = ResearchSavedData.getResearchKey(serverPlayer);
                Set<ResourceLocation> completed = savedData.getCompletedResearch(researchKey);
                PacketDistributor.sendToPlayer(serverPlayer, new OpenResearchBookPacket(completed));
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.literal("Right-click to view all research")
                .withStyle(s -> s.withColor(0xAAAAAA).withItalic(true)));
    }
}
