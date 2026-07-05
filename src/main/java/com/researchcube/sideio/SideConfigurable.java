package com.researchcube.sideio;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implemented by block entities that expose side-configurable item/fluid IO.
 *
 * <p>An implementation declares its {@link IOChannel}s once, keeps a persistent
 * {@link SideIOConfig}, and reports its current horizontal facing (from the blockstate)
 * so relative sides can be mapped to absolute directions. {@link #onSideConfigChanged()}
 * is the single hook the framework and network handlers call after any mutation.
 */
public interface SideConfigurable {

    /** The live per-side IO configuration. Never null. */
    SideIOConfig getSideIOConfig();

    /** The block entity's declared IO channels. Order defines UI tab order. Never null. */
    List<IOChannel> getIOChannels();

    /**
     * The block's horizontal facing, used to translate {@link RelativeSide} to absolute
     * {@link Direction}. Read from the blockstate FACING property.
     */
    Direction getIOFacing();

    /**
     * Called after the side configuration changes. Implementations must:
     * <ul>
     *   <li>{@code setChanged()} so the block entity persists,</li>
     *   <li>sync the update tag to clients (e.g. {@code level.sendBlockUpdated(...)}),</li>
     *   <li>{@code level.invalidateCapabilities(worldPosition)} so NeoForge refreshes the
     *       cached capability handlers for neighbouring pipes.</li>
     * </ul>
     */
    void onSideConfigChanged();

    /**
     * Describe the block entity's externally-exposed item IO, or {@code null} if it has no
     * item channel. Consumed by {@link SideIOCapabilities#itemHandler} to build a sided
     * wrapper generically.
     */
    @Nullable
    default ItemChannelSpec getItemChannelSpec() {
        return null;
    }

    /**
     * Describe the block entity's fluid tanks bound to their controlling channels, in the
     * order they should be filled. Empty (the default) means no fluid IO. Consumed by
     * {@link SideIOCapabilities#fluidHandler}.
     */
    default List<FluidChannelSpec> getFluidChannelSpecs() {
        return List.of();
    }

    /** Convenience lookup of a channel by id, or {@code null} if not declared. */
    default IOChannel getChannel(String channelId) {
        for (IOChannel channel : getIOChannels()) {
            if (channel.id().equals(channelId)) {
                return channel;
            }
        }
        return null;
    }
}
