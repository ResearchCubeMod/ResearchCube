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
 * Client→Server packet to start processing at a Processing Station.
 */
public record StartProcessingPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<StartProcessingPacket> TYPE =
            new Type<>(ResearchCubeMod.rl("start_processing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartProcessingPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StartProcessingPacket::pos,
                    StartProcessingPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StartProcessingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (player.level().getBlockEntity(packet.pos()) instanceof ProcessingStationBlockEntity be) {
                be.tryStartProcessing();
            }
        });
    }
}
