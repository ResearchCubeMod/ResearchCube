package com.researchcube.compat.jade;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ProcessingStationBlockEntity;
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
 * Jade provider for the Processing Station block.
 * Shows processing status, progress, and fluid tank info.
 */
public enum ProcessingStationProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResearchCubeMod.rl("processing_station");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ProcessingStationBlockEntity be) {
            data.putBoolean("isProcessing", be.isProcessing());
            data.putFloat("progress", be.getProgress());

            FluidStack fluid1 = be.getFluidInput1().getFluid();
            data.putInt("fluidInput1Amount", be.getFluidInput1().getFluidAmount());
            if (!fluid1.isEmpty()) {
                data.putString("fluidInput1Type",
                        BuiltInRegistries.FLUID.getKey(fluid1.getFluid()).getPath());
            }

            FluidStack fluid2 = be.getFluidInput2().getFluid();
            data.putInt("fluidInput2Amount", be.getFluidInput2().getFluidAmount());
            if (!fluid2.isEmpty()) {
                data.putString("fluidInput2Type",
                        BuiltInRegistries.FLUID.getKey(fluid2.getFluid()).getPath());
            }

            FluidStack fluidOut = be.getFluidOutput().getFluid();
            data.putInt("fluidOutputAmount", be.getFluidOutput().getFluidAmount());
            if (!fluidOut.isEmpty()) {
                data.putString("fluidOutputType",
                        BuiltInRegistries.FLUID.getKey(fluidOut.getFluid()).getPath());
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();

        boolean processing = data.getBoolean("isProcessing");
        if (processing) {
            int percent = (int) (data.getFloat("progress") * 100);
            tooltip.add(Component.literal("Processing (" + percent + "%)")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Idle").withStyle(ChatFormatting.GRAY));
        }

        int tankCapacity = ProcessingStationBlockEntity.TANK_CAPACITY;

        int fluid1Amount = data.getInt("fluidInput1Amount");
        if (fluid1Amount > 0) {
            String type = data.getString("fluidInput1Type");
            tooltip.add(Component.literal("In 1: " + fluid1Amount + "/" + tankCapacity + " mB (" + type + ")")
                    .withStyle(ChatFormatting.AQUA));
        }

        int fluid2Amount = data.getInt("fluidInput2Amount");
        if (fluid2Amount > 0) {
            String type = data.getString("fluidInput2Type");
            tooltip.add(Component.literal("In 2: " + fluid2Amount + "/" + tankCapacity + " mB (" + type + ")")
                    .withStyle(ChatFormatting.AQUA));
        }

        int fluidOutAmount = data.getInt("fluidOutputAmount");
        if (fluidOutAmount > 0) {
            String type = data.getString("fluidOutputType");
            tooltip.add(Component.literal("Out: " + fluidOutAmount + "/" + tankCapacity + " mB (" + type + ")")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
