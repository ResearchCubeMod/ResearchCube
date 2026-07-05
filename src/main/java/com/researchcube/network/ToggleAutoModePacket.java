package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ProcessingStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet toggling the Processing Station's auto-start mode.
 */
public record ToggleAutoModePacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ToggleAutoModePacket> TYPE =
            new Type<>(ResearchCubeMod.rl("toggle_auto_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleAutoModePacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ToggleAutoModePacket::pos,
                    ToggleAutoModePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleAutoModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Distance guard against out-of-range spoofed packets.
            if (player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64 * 64) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos()) instanceof ProcessingStationBlockEntity be) {
                be.setAutoMode(!be.isAutoMode());
                be.getLevel().sendBlockUpdated(packet.pos(), be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }
}
