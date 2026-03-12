package com.researchcube.research;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Represents a fluid cost for research.
 * Parsed from JSON: { "fluid": "researchcube:thinking_fluid", "amount": 1000 }
 *
 * @param fluidId the registry ID of the required fluid
 * @param amount  the amount in millibuckets (1 bucket = 1000 mB)
 */
public record FluidCost(ResourceLocation fluidId, int amount) {

    /**
     * Resolve the Fluid from the registry. Returns Fluids.EMPTY if not found.
     */
    public Fluid getFluid() {
        if (BuiltInRegistries.FLUID.containsKey(fluidId)) {
            return BuiltInRegistries.FLUID.get(fluidId);
        }
        return Fluids.EMPTY;
    }

    /**
     * Returns true if this fluid cost references a valid registered fluid.
     */
    public boolean isValid() {
        return BuiltInRegistries.FLUID.containsKey(fluidId) &&
               BuiltInRegistries.FLUID.get(fluidId) != Fluids.EMPTY;
    }

    /**
     * Returns the fluid name for display purposes.
     */
    public String getFluidName() {
        return fluidId.getPath().replace('_', ' ');
    }
}
