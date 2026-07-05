package com.researchcube.sideio;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Immutable description of a block entity's item IO for the framework: which inner handler
 * backs it, which channel governs it, and which slots external automation may touch.
 *
 * <p>A block entity returns one of these from {@code SideConfigurable} so
 * {@link SideIOCapabilities} can build a {@link SidedItemHandlerWrapper} without any
 * block-specific knowledge.
 *
 * @param channelId        the {@link IOChannel#id()} of the item channel (mode source per side)
 * @param inner            the backing item handler
 * @param insertableSlots  slot indices external automation may insert into
 * @param extractableSlots slot indices external automation may extract from
 * @param insertFilter     optional per-slot insert predicate; may be null
 */
public record ItemChannelSpec(String channelId,
                              IItemHandler inner,
                              Set<Integer> insertableSlots,
                              Set<Integer> extractableSlots,
                              @Nullable BiPredicate<Integer, ItemStack> insertFilter) {
}
