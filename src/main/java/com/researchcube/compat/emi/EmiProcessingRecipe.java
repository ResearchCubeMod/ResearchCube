package com.researchcube.compat.emi;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.ProcessingFluidStack;
import com.researchcube.recipe.ProcessingRecipe;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * EMI recipe wrapper for ProcessingRecipe.
 * Shows 4x4 input grid, fluid inputs, 2x4 output grid, fluid output, and processing arrow.
 */
public class EmiProcessingRecipe extends BasicEmiRecipe {

    private final ProcessingRecipe recipe;

    public EmiProcessingRecipe(ProcessingRecipe recipe, ResourceLocation recipeId) {
        super(ResearchCubeEMIPlugin.PROCESSING,
                ResearchCubeMod.rl("/processing/" + recipeId.getPath()),
                176, 100);
        this.recipe = recipe;

        // Item inputs
        for (Ingredient ing : recipe.getIngredients()) {
            this.inputs.add(EmiIngredient.of(ing));
        }

        // Fluid inputs
        for (ProcessingFluidStack pfs : recipe.getFluidInputs()) {
            FluidStack fs = pfs.toFluidStack();
            if (!fs.isEmpty()) {
                this.inputs.add(EmiStack.of(fs.getFluid(), fs.getAmount()));
            }
        }

        // Item outputs
        for (ItemStack result : recipe.getResults()) {
            if (!result.isEmpty()) {
                this.outputs.add(EmiStack.of(result));
            }
        }

        // Fluid output
        if (recipe.hasFluidOutput()) {
            FluidStack fluidOut = recipe.getFluidOutput().toFluidStack();
            if (!fluidOut.isEmpty()) {
                this.outputs.add(EmiStack.of(fluidOut.getFluid(), fluidOut.getAmount()));
            }
        }
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Item inputs: 4x4 grid
        List<Ingredient> ingredients = recipe.getIngredients();
        int ingredientIndex = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int x = 1 + col * 18;
                int y = 1 + row * 18;
                if (ingredientIndex < ingredients.size()) {
                    Ingredient ing = ingredients.get(ingredientIndex);
                    if (!ing.isEmpty()) {
                        widgets.addSlot(EmiIngredient.of(ing), x, y);
                    } else {
                        widgets.addSlot(EmiStack.EMPTY, x, y);
                    }
                }
                ingredientIndex++;
            }
        }

        // Fluid inputs (up to 2, below item grid)
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        if (!fluidInputs.isEmpty()) {
            FluidStack fluid1 = fluidInputs.get(0).toFluidStack();
            if (!fluid1.isEmpty()) {
                widgets.addSlot(EmiStack.of(fluid1.getFluid(), fluid1.getAmount()), 1, 76)
                        .appendTooltip(Component.literal(fluid1.getAmount() + " mB"));
            }
        }
        if (fluidInputs.size() > 1) {
            FluidStack fluid2 = fluidInputs.get(1).toFluidStack();
            if (!fluid2.isEmpty()) {
                widgets.addSlot(EmiStack.of(fluid2.getFluid(), fluid2.getAmount()), 19, 76)
                        .appendTooltip(Component.literal(fluid2.getAmount() + " mB"));
            }
        }

        // Processing arrow
        widgets.addFillingArrow(82, 36, recipe.getDuration() * 50);

        // Item outputs: 2x4 grid
        List<ItemStack> results = recipe.getResults();
        int resultIndex = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int x = 114 + col * 18;
                int y = 1 + row * 18;
                if (resultIndex < results.size()) {
                    ItemStack result = results.get(resultIndex);
                    if (!result.isEmpty()) {
                        widgets.addSlot(EmiStack.of(result), x, y).recipeContext(this);
                    }
                }
                resultIndex++;
            }
        }

        // Fluid output
        if (recipe.hasFluidOutput()) {
            FluidStack fluidOut = recipe.getFluidOutput().toFluidStack();
            if (!fluidOut.isEmpty()) {
                widgets.addSlot(EmiStack.of(fluidOut.getFluid(), fluidOut.getAmount()), 156, 76)
                        .appendTooltip(Component.literal(fluidOut.getAmount() + " mB"))
                        .recipeContext(this);
            }
        }

        // Duration tooltip on the arrow area
        widgets.addTooltipText(
                List.of(Component.literal("Duration: " + (recipe.getDuration() / 20.0f) + "s")),
                82, 36, 22, 16
        );
    }
}
