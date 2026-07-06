package com.researchcube.network;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.TankInteractable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client→Server packet: the player clicked a fluid gauge in a machine screen while
 * holding a fluid container on the cursor, asking to fill or drain the tank.
 *
 * <p>The block entity is resolved through {@link TankInteractable} so any machine that
 * exposes its tanks by index reuses the same handling. The carried (cursor) stack is
 * used as the container because a menu is open: a filled container empties into the tank,
 * an empty container fills from it. Stackable containers are handled vanilla-style by
 * NeoForge {@link FluidUtil}: one unit is transferred and the resulting container is stowed
 * in the player inventory (or given/dropped) while the cursor stack shrinks by one.
 *
 * @param pos       the block entity position
 * @param tankIndex the gauge index (machine-specific; e.g. 0/1 = fluid inputs, 2 = output)
 */
public record InteractTankPacket(BlockPos pos, int tankIndex) implements CustomPacketPayload {

    public static final Type<InteractTankPacket> TYPE =
            new Type<>(ResearchCubeMod.rl("interact_tank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InteractTankPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, InteractTankPacket::pos,
                    ByteBufCodecs.VAR_INT, InteractTankPacket::tankIndex,
                    InteractTankPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InteractTankPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            // Validate before touching the world: reject if too far away (vanilla
            // 8-block container reach) or if the chunk isn't loaded (a getBlockEntity
            // lookup would otherwise force-load it).
            if (player.distanceToSqr(packet.pos().getX() + 0.5, packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64.0) {
                return;
            }
            if (!player.level().isLoaded(packet.pos())) {
                return;
            }

            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos());
            if (!(blockEntity instanceof TankInteractable interactable)) return;

            FluidTank tank = interactable.getInteractableTank(packet.tankIndex());
            if (tank == null) return;

            // The cursor stack (a menu is open) is the container to fill/drain.
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.isEmpty()) return;

            // Stow target for the resulting container when the cursor holds a stack (count > 1):
            // FluidUtil transfers one unit and puts the emptied/filled container here, giving it to
            // the player or dropping it at their feet if the inventory is full.
            IItemHandler playerInv = new PlayerMainInvWrapper(player.getInventory());

            // Try to empty a filled container into the tank first, then to fill an empty container
            // from it, mirroring vanilla FluidUtil.interactWithFluidHandler. The AndStow variants
            // handle stack-size > 1, creative mode, overflow, and the bucket fill/empty sound.
            FluidActionResult result =
                    FluidUtil.tryEmptyContainerAndStow(carried, tank, playerInv, Integer.MAX_VALUE, player, true);
            if (!result.isSuccess()) {
                result = FluidUtil.tryFillContainerAndStow(carried, tank, playerInv, Integer.MAX_VALUE, player, true);
            }
            if (!result.isSuccess()) {
                return;
            }

            // Put the resulting cursor stack back. For count == 1 this is the emptied/filled
            // container; for count > 1 the original stack shrank by one and the transferred
            // container was already stowed above.
            player.containerMenu.setCarried(result.getResult());

            interactable.onTankInteracted();
            player.containerMenu.broadcastChanges();
        });
    }
}
