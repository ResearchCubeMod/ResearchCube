package com.researchcube.sideio;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic capability providers for {@link SideConfigurable} block entities. Register these
 * from {@code RegisterCapabilitiesEvent} as one-liners:
 *
 * <pre>{@code
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MY_BE.get(), SideIOCapabilities::itemHandler);
 * event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MY_BE.get(), SideIOCapabilities::fluidHandler);
 * }</pre>
 *
 * <p>Semantics:
 * <ul>
 *   <li>A {@code null} side is internal access and receives a fully-permissive handler
 *       (all structural slots, all tanks fill+drain).</li>
 *   <li>A side where every relevant channel resolves to {@link IOMode#NONE} returns
 *       {@code null}, so neighbouring pipes correctly see "nothing here".</li>
 * </ul>
 */
public final class SideIOCapabilities {

    private SideIOCapabilities() {}

    /**
     * Item-handler capability provider. {@code be} must be a {@link SideConfigurable}.
     *
     * @param be   the block entity
     * @param side the absolute face being queried, or {@code null} for internal access
     */
    @Nullable
    public static IItemHandler itemHandler(BlockEntity be, @Nullable Direction side) {
        if (!(be instanceof SideConfigurable configurable)) return null;
        ItemChannelSpec spec = configurable.getItemChannelSpec();
        if (spec == null) return null;

        if (side == null) {
            // Internal access: fully permissive over the declared insertable/extractable slots.
            return new SidedItemHandlerWrapper(
                    spec.inner(),
                    () -> IOMode.BOTH,
                    spec.insertableSlots(),
                    spec.extractableSlots(),
                    spec.insertFilter());
        }

        RelativeSide relative = RelativeSide.fromAbsolute(configurable.getIOFacing(), side);
        IOMode mode = configurable.getSideIOConfig().getMode(spec.channelId(), relative);
        if (mode == IOMode.NONE) return null;

        return new SidedItemHandlerWrapper(
                spec.inner(),
                () -> configurable.getSideIOConfig().getMode(spec.channelId(), relative),
                spec.insertableSlots(),
                spec.extractableSlots(),
                spec.insertFilter());
    }

    /**
     * Fluid-handler capability provider. {@code be} must be a {@link SideConfigurable}.
     *
     * @param be   the block entity
     * @param side the absolute face being queried, or {@code null} for internal access
     */
    @Nullable
    public static IFluidHandler fluidHandler(BlockEntity be, @Nullable Direction side) {
        if (!(be instanceof SideConfigurable configurable)) return null;
        List<FluidChannelSpec> specs = configurable.getFluidChannelSpecs();
        if (specs.isEmpty()) return null;

        if (side == null) {
            // Internal access: every tank permits fill + drain.
            List<SidedFluidHandler.TankEntry> entries = new ArrayList<>(specs.size());
            for (FluidChannelSpec spec : specs) {
                entries.add(new SidedFluidHandler.TankEntry(spec.tank(), () -> IOMode.BOTH));
            }
            return new SidedFluidHandler(entries);
        }

        RelativeSide relative = RelativeSide.fromAbsolute(configurable.getIOFacing(), side);
        SideIOConfig config = configurable.getSideIOConfig();

        List<SidedFluidHandler.TankEntry> entries = new ArrayList<>(specs.size());
        boolean anyOpen = false;
        for (FluidChannelSpec spec : specs) {
            if (config.getMode(spec.channelId(), relative) != IOMode.NONE) {
                anyOpen = true;
            }
            entries.add(new SidedFluidHandler.TankEntry(
                    spec.tank(),
                    () -> config.getMode(spec.channelId(), relative)));
        }

        // Every tank closed on this side → expose nothing.
        if (!anyOpen) return null;
        return new SidedFluidHandler(entries);
    }
}
