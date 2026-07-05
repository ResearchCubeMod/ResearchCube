package com.researchcube.sideio;

import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * Binds one of a block entity's fluid tanks to the {@link IOChannel} that governs it.
 * A block entity returns an ordered list of these so {@link SideIOCapabilities} can assemble
 * a {@link SidedFluidHandler} whose tanks each follow their own channel's per-side mode.
 *
 * @param channelId the {@link IOChannel#id()} controlling this tank's per-side mode
 * @param tank      the backing tank
 */
public record FluidChannelSpec(String channelId, FluidTank tank) {
}
