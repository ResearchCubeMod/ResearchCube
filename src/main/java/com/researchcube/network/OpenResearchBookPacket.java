package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.ClientResearchData;
import com.researchcube.client.screen.ResearchBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Packet sent from server → client when the player right-clicks the Research Book.
 * Contains the player's completed research IDs so the client screen can display progress.
 */
public record OpenResearchBookPacket(Set<ResourceLocation> completedResearch) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenResearchBookPacket> TYPE =
            new CustomPacketPayload.Type<>(ResearchCubeMod.rl("open_research_book"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenResearchBookPacket> STREAM_CODEC =
            StreamCodec.of(OpenResearchBookPacket::encode, OpenResearchBookPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, OpenResearchBookPacket packet) {
        buf.writeVarInt(packet.completedResearch.size());
        for (ResourceLocation rl : packet.completedResearch) {
            buf.writeResourceLocation(rl);
        }
    }

    private static OpenResearchBookPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Set<ResourceLocation> completed = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            completed.add(buf.readResourceLocation());
        }
        return new OpenResearchBookPacket(completed);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Client-side handler. Opens the Research Book screen.
     */
    public static void handle(OpenResearchBookPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Set<String> completedStrings = new HashSet<>(packet.completedResearch.size());
            for (ResourceLocation rl : packet.completedResearch) {
                completedStrings.add(rl.toString());
            }
            ClientResearchData.updateCompleted(completedStrings);
            Minecraft.getInstance().setScreen(new ResearchBookScreen(completedStrings));
        });
    }
}
