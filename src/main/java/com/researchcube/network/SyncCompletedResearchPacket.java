package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.ClientResearchData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Packet sent server → client with the player's full set of completed research.
 *
 * Sent on join, after datapack reloads, whenever a research completes and after
 * admin commands change progress — so JEI/EMI tooltips, the research book and the
 * HUD always reflect the current state without having to open a menu first.
 */
public record SyncCompletedResearchPacket(Set<ResourceLocation> completedResearch) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncCompletedResearchPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("sync_completed_research"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCompletedResearchPacket> STREAM_CODEC =
            StreamCodec.of(SyncCompletedResearchPacket::encode, SyncCompletedResearchPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, SyncCompletedResearchPacket packet) {
        buf.writeVarInt(packet.completedResearch.size());
        for (ResourceLocation rl : packet.completedResearch) {
            buf.writeResourceLocation(rl);
        }
    }

    private static SyncCompletedResearchPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Set<ResourceLocation> completed = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            completed.add(buf.readResourceLocation());
        }
        return new SyncCompletedResearchPacket(completed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler. Updates the client research cache.
     */
    public static void handle(SyncCompletedResearchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Set<String> completedStrings = new HashSet<>(packet.completedResearch.size());
            for (ResourceLocation rl : packet.completedResearch) {
                completedStrings.add(rl.toString());
            }
            ClientResearchData.updateCompleted(completedStrings);
        });
    }
}
