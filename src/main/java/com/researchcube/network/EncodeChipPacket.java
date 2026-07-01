package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.ResearchChipItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server packet sent when the player selects a research in the chip encoder screen.
 */
public record EncodeChipPacket(ResourceLocation researchId, boolean mainHand) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EncodeChipPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("encode_chip"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncodeChipPacket> STREAM_CODEC =
            StreamCodec.of(EncodeChipPacket::encode, EncodeChipPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, EncodeChipPacket packet) {
        buf.writeResourceLocation(packet.researchId);
        buf.writeBoolean(packet.mainHand);
    }

    private static EncodeChipPacket decode(RegistryFriendlyByteBuf buf) {
        return new EncodeChipPacket(buf.readResourceLocation(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EncodeChipPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            InteractionHand hand = packet.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = serverPlayer.getItemInHand(hand);

            if (!(stack.getItem() instanceof ResearchChipItem chipItem)) {
                ResearchCubeMod.LOGGER.warn("[ResearchCube] {} sent EncodeChipPacket but is not holding a chip", serverPlayer.getName().getString());
                return;
            }

            chipItem.serverEncodeChip(serverPlayer, stack, packet.researchId);
        });
    }
}
