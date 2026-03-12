package com.researchcube.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a fluid input or output for a ProcessingRecipe.
 */
public record ProcessingFluidStack(
        ResourceLocation fluidId,
        int amount
) {
    /**
     * Get the actual Fluid from the registry.
     */
    @Nullable
    public Fluid getFluid() {
        return net.minecraft.core.registries.BuiltInRegistries.FLUID.get(fluidId);
    }

    /**
     * Create a FluidStack from this definition.
     */
    public FluidStack toFluidStack() {
        Fluid fluid = getFluid();
        if (fluid == null) return FluidStack.EMPTY;
        return new FluidStack(fluid, amount);
    }

    /**
     * Check if a FluidStack matches this requirement.
     */
    public boolean matches(FluidStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation stackFluidId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return fluidId.equals(stackFluidId) && stack.getAmount() >= amount;
    }
}
