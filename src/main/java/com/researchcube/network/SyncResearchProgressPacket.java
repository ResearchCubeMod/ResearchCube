package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.ClientResearchData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Packet sent server → client to update the research progress HUD overlay.
 * Sent periodically (every 20 ticks) while a player has active research.
 * Also sent with isActive=false when research completes or is cancelled.
 */
public record SyncResearchProgressPacket(
        String researchName,
        float progress,
        int remainingSeconds,
        int tierColor,
        boolean isActive
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncResearchProgressPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("sync_research_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResearchProgressPacket> STREAM_CODEC =
            StreamCodec.of(SyncResearchProgressPacket::encode, SyncResearchProgressPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SyncResearchProgressPacket packet) {
        buf.writeUtf(packet.researchName);
        buf.writeFloat(packet.progress);
        buf.writeVarInt(packet.remainingSeconds);
        buf.writeInt(packet.tierColor);
        buf.writeBoolean(packet.isActive);
    }

    private static SyncResearchProgressPacket decode(RegistryFriendlyByteBuf buf) {
        return new SyncResearchProgressPacket(
                buf.readUtf(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler. Updates the ClientResearchData HUD state.
     */
    public static void handle(SyncResearchProgressPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.isActive) {
                ClientResearchData.updateActiveResearch(
                        packet.researchName,
                        packet.progress,
                        packet.remainingSeconds,
                        packet.tierColor
                );
            } else {
                ClientResearchData.clearActiveResearch();
            }
        });
    }
}
