package com.researchcube.client;

import com.researchcube.menu.ProcessingStationMenu;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client-only bridge that applies a {@link com.researchcube.network.SyncTankPacket} to the
 * open menu. Isolated in a client class so the packet record itself carries no hard reference
 * to {@link Minecraft}.
 */
public final class ClientTankSync {

    private ClientTankSync() {}

    /**
     * Apply a synced tank update to the open menu, if it matches the given container id.
     */
    public static void apply(int containerId, int tankIndex, FluidStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.containerMenu instanceof ProcessingStationMenu menu
                && menu.containerId == containerId) {
            menu.setClientTank(tankIndex, stack);
        }
    }
}
