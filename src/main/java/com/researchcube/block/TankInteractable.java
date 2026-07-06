package com.researchcube.block;

import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Implemented by block entities whose fluid tanks can be filled or drained by
 * clicking their on-screen gauge with a fluid container (bucket, etc.).
 *
 * <p>Server-side only: {@link com.researchcube.network.InteractTankPacket} resolves
 * the clicked block entity through this interface, so any machine that exposes its
 * tanks by index can reuse the same click-to-fill/drain flow (Processing Station now,
 * Research Table later).
 */
public interface TankInteractable {

    /**
     * Resolve the tank a gauge index maps to, or {@code null} if the index is out of
     * range for this block entity. Indices are stable per machine and shared with the
     * screen's gauge layout (e.g. the Processing Station uses 0 = fluid input 1,
     * 1 = fluid input 2, 2 = fluid output).
     *
     * @param index the gauge index sent by the client
     * @return the backing tank, or {@code null} if the index is invalid
     */
    @Nullable
    FluidTank getInteractableTank(int index);

    /**
     * Hook fired after a click successfully moved fluid into or out of a tank, so the
     * machine can react (re-scan for a recipe, mark dirty, etc.). Tank content changes
     * already flag their own dirty/recheck state, so the default is a no-op.
     */
    default void onTankInteracted() {}
}
