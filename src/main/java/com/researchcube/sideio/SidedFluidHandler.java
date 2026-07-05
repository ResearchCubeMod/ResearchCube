package com.researchcube.sideio;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import java.util.function.Supplier;

/**
 * Composes a block entity's fluid tanks into a single {@link IFluidHandler} whose
 * fill/drain behaviour is gated per tank by a per-side {@link IOMode} supplier.
 *
 * <p>Each tank is paired with the mode of its own channel on the accessed side. This makes
 * multi-tank piping deterministic: a side that sets only {@code fluid_input_2 = INPUT}
 * fills tank 2 exclusively, because tank 1's mode reports {@code canInsert() == false}.
 */
public class SidedFluidHandler implements IFluidHandler {

    /** A tank plus the supplier of its {@link IOMode} for the side this handler serves. */
    public record TankEntry(FluidTank tank, Supplier<IOMode> mode) {}

    private final List<TankEntry> entries;

    public SidedFluidHandler(List<TankEntry> entries) {
        this.entries = entries;
    }

    @Override
    public int getTanks() {
        return entries.size();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank < 0 || tank >= entries.size()) return FluidStack.EMPTY;
        return entries.get(tank).tank().getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank < 0 || tank >= entries.size()) return 0;
        return entries.get(tank).tank().getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        if (tank < 0 || tank >= entries.size()) return false;
        TankEntry entry = entries.get(tank);
        return entry.mode().get().canInsert() && entry.tank().isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        int totalFilled = 0;
        for (TankEntry entry : entries) {
            if (!entry.mode().get().canInsert()) continue;
            FluidStack remaining = resource.copyWithAmount(resource.getAmount() - totalFilled);
            if (remaining.isEmpty()) break;
            totalFilled += entry.tank().fill(remaining, action);
            if (totalFilled >= resource.getAmount()) break;
        }
        return totalFilled;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        for (TankEntry entry : entries) {
            if (!entry.mode().get().canExtract()) continue;
            FluidStack drained = entry.tank().drain(resource, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        for (TankEntry entry : entries) {
            if (!entry.mode().get().canExtract()) continue;
            FluidStack drained = entry.tank().drain(maxDrain, action);
            if (!drained.isEmpty()) {
                return drained;
            }
        }
        return FluidStack.EMPTY;
    }
}
