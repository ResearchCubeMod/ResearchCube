package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.screen.ChipEncoderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Server -> Client packet that opens the chip encoder selection screen.
 * Contains the player's completed research IDs and the hand holding the chip.
 */
public record OpenChipEncoderPacket(Set<ResourceLocation> completedResearch, boolean mainHand) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenChipEncoderPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("open_chip_encoder"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChipEncoderPacket> STREAM_CODEC =
            StreamCodec.of(OpenChipEncoderPacket::encode, OpenChipEncoderPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, OpenChipEncoderPacket packet) {
        buf.writeVarInt(packet.completedResearch.size());
        for (ResourceLocation rl : packet.completedResearch) {
            buf.writeResourceLocation(rl);
        }
        buf.writeBoolean(packet.mainHand);
    }

    private static OpenChipEncoderPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Set<ResourceLocation> completed = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            completed.add(buf.readResourceLocation());
        }
        boolean mainHand = buf.readBoolean();
        return new OpenChipEncoderPacket(completed, mainHand);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenChipEncoderPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new ChipEncoderScreen(packet.completedResearch, packet.mainHand));
        });
    }
}
