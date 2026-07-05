package com.researchcube.sideio;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Wraps an inner {@link IItemHandler} and gates insertion/extraction by a per-side
 * {@link IOMode} plus structural slot rules, without restricting the block entity's own
 * GUI access (which touches the inner handler directly).
 *
 * <p>The full inner slot range stays visible read-only via {@link #getSlots()} and
 * {@link #getStackInSlot(int)} so external mods observe consistent indices; only mutation
 * is filtered.
 */
public class SidedItemHandlerWrapper implements IItemHandler {

    private final IItemHandler inner;
    private final Supplier<IOMode> modeSupplier;
    private final Set<Integer> insertableSlots;
    private final Set<Integer> extractableSlots;
    @Nullable
    private final BiPredicate<Integer, ItemStack> insertFilter;

    /**
     * @param inner            the block entity's backing item handler
     * @param modeSupplier     supplies the current {@link IOMode} for the accessed side's item channel
     * @param insertableSlots  slot indices external automation may insert into (subject to mode + filter)
     * @param extractableSlots slot indices external automation may extract from (subject to mode)
     * @param insertFilter     optional per-slot insert predicate (slot index, stack) → allowed; may be null
     */
    public SidedItemHandlerWrapper(IItemHandler inner,
                                   Supplier<IOMode> modeSupplier,
                                   Set<Integer> insertableSlots,
                                   Set<Integer> extractableSlots,
                                   @Nullable BiPredicate<Integer, ItemStack> insertFilter) {
        this.inner = inner;
        this.modeSupplier = modeSupplier;
        this.insertableSlots = insertableSlots;
        this.extractableSlots = extractableSlots;
        this.insertFilter = insertFilter;
    }

    @Override
    public int getSlots() {
        return inner.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inner.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!modeSupplier.get().canInsert()) return stack;
        if (!insertableSlots.contains(slot)) return stack;
        if (insertFilter != null && !insertFilter.test(slot, stack)) return stack;
        return inner.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!modeSupplier.get().canExtract()) return ItemStack.EMPTY;
        if (!extractableSlots.contains(slot)) return ItemStack.EMPTY;
        return inner.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inner.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!modeSupplier.get().canInsert()) return false;
        if (!insertableSlots.contains(slot)) return false;
        if (insertFilter != null && !insertFilter.test(slot, stack)) return false;
        return inner.isItemValid(slot, stack);
    }
}
