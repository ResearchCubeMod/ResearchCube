package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.ProcessingFluidStack;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.registry.ModItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * JEI recipe category for Processing Station recipes.
 * Shows item inputs (up to 16), fluid inputs (up to 2), item outputs (up to 8), 
 * and fluid output (1), along with processing duration.
 */
public class ProcessingCategory implements IRecipeCategory<ProcessingRecipe> {

    public static final ResourceLocation UID = ResearchCubeMod.rl("processing");
    public static final RecipeType<ProcessingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, ProcessingRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;

    public ProcessingCategory(IGuiHelper guiHelper) {
        // Layout: inputs on left (4x4 grid = 72px), arrow, outputs on right (2x4 grid = 36px)
        // Plus fluids underneath
        this.background = guiHelper.createBlankDrawable(176, 100);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.PROCESSING_STATION_ITEM.get()));
        this.title = Component.literal("Processing Station");
    }

    @Override
    public RecipeType<ProcessingRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessingRecipe recipe, IFocusGroup focuses) {
        // Item inputs in a 4x4 grid (slots 0-15)
        List<Ingredient> ingredients = recipe.getIngredients();
        int ingredientIndex = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int x = 1 + col * 18;
                int y = 1 + row * 18;
                if (ingredientIndex < ingredients.size()) {
                    Ingredient ingredient = ingredients.get(ingredientIndex);
                    if (!ingredient.isEmpty()) {
                        builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                                .addIngredients(ingredient);
                    }
                }
                ingredientIndex++;
            }
        }

        // Fluid inputs underneath the item input grid (up to 2)
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        if (fluidInputs.size() > 0) {
            FluidStack fluid1 = fluidInputs.get(0).toFluidStack();
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 76)
                    .setFluidRenderer(8000, false, 16, 16)
                    .addFluidStack(fluid1.getFluid(), fluid1.getAmount())
                    .addTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.literal(fluid1.getAmount() + " mB"));
                    });
        }
        if (fluidInputs.size() > 1) {
            FluidStack fluid2 = fluidInputs.get(1).toFluidStack();
            builder.addSlot(RecipeIngredientRole.INPUT, 19, 76)
                    .setFluidRenderer(8000, false, 16, 16)
                    .addFluidStack(fluid2.getFluid(), fluid2.getAmount())
                    .addTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.literal(fluid2.getAmount() + " mB"));
                    });
        }

        // Item outputs in a 2x4 grid (slots 16-23)
        List<ItemStack> results = recipe.getResults();
        int resultIndex = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int x = 114 + col * 18;
                int y = 1 + row * 18;
                if (resultIndex < results.size()) {
                    ItemStack result = results.get(resultIndex);
                    if (!result.isEmpty()) {
                        builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                                .addItemStack(result);
                    }
                }
                resultIndex++;
            }
        }

        // Fluid output
        if (recipe.hasFluidOutput()) {
            FluidStack fluidOut = recipe.getFluidOutput().toFluidStack();
            builder.addSlot(RecipeIngredientRole.OUTPUT, 156, 76)
                    .setFluidRenderer(8000, false, 16, 16)
                    .addFluidStack(fluidOut.getFluid(), fluidOut.getAmount())
                    .addTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.literal(fluidOut.getAmount() + " mB"));
                    });
        }
    }
}
