package com.researchcube.compat.jade;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade provider for the Research Station block.
 * Shows research status, progress, and fluid tank info in the block tooltip.
 */
public enum ResearchStationProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResearchCubeMod.rl("research_station");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ResearchTableBlockEntity be) {
            data.putBoolean("isResearching", be.isResearching());

            if (be.isResearching() && be.getActiveResearchId() != null) {
                ResearchDefinition def = ResearchRegistry.get(be.getActiveResearchId());
                data.putString("researchName", def != null ? def.getDisplayName() : be.getActiveResearchId());
                data.putFloat("progress", be.getProgress());
            }

            FluidStack fluid = be.getFluidTank().getFluid();
            data.putInt("fluidAmount", be.getFluidTank().getFluidAmount());
            if (!fluid.isEmpty()) {
                data.putString("fluidType",
                        BuiltInRegistries.FLUID.getKey(fluid.getFluid()).getPath());
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();

        boolean researching = data.getBoolean("isResearching");
        if (researching) {
            String name = data.getString("researchName");
            int percent = (int) (data.getFloat("progress") * 100);
            tooltip.add(Component.literal("Researching: " + name + " (" + percent + "%)")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Idle").withStyle(ChatFormatting.GRAY));
        }

        int fluidAmount = data.getInt("fluidAmount");
        if (fluidAmount > 0) {
            String type = data.getString("fluidType");
            tooltip.add(Component.literal("Fluid: " + fluidAmount + "/"
                    + ResearchTableBlockEntity.TANK_CAPACITY + " mB (" + type + ")")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
