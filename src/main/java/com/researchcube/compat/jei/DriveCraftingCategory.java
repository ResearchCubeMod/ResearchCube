package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.DriveItem;
import com.researchcube.recipe.DriveCraftingRecipe;
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

/**
 * JEI recipe category for Drive Crafting recipes.
 * Shows the drive requirement, ingredients, and the recipe output.
 */
public class DriveCraftingCategory implements IRecipeCategory<DriveCraftingRecipe> {

    public static final ResourceLocation UID = ResearchCubeMod.rl("drive_crafting");
    public static final RecipeType<DriveCraftingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, DriveCraftingRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;

    public DriveCraftingCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(150, 60);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.METADATA_RECLAIMED.get()));
        this.title = Component.literal("Drive Crafting");
    }

    @Override
    public RecipeType<DriveCraftingRecipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, DriveCraftingRecipe recipe, IFocusGroup focuses) {
        // Drive slot (any drive item as a hint — the required recipe_id is shown in tooltip)
        // Create an array of all functional drive items as possible inputs
        ItemStack[] driveStacks = new ItemStack[]{
                new ItemStack(ModItems.METADATA_UNSTABLE.get()),
                new ItemStack(ModItems.METADATA_RECLAIMED.get()),
                new ItemStack(ModItems.METADATA_ENHANCED.get()),
                new ItemStack(ModItems.METADATA_ELABORATE.get()),
                new ItemStack(ModItems.METADATA_CYBERNETIC.get()),
                new ItemStack(ModItems.METADATA_SELF_AWARE.get())
        };
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .addItemStacks(java.util.List.of(driveStacks))
                .addTooltipCallback((recipeSlotView, tooltip) ->
                        tooltip.add(Component.literal("§7Requires recipe: §e" + recipe.getRequiredRecipeId())));

        // Ingredient slots (up to 8, arranged in a 4x2 grid)
        int idx = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            int col = idx % 4;
            int row = idx / 4;
            builder.addSlot(RecipeIngredientRole.INPUT, 22 + col * 18, 1 + row * 18)
                    .addIngredients(ingredient);
            idx++;
        }

        // Output slot
        builder.addSlot(RecipeIngredientRole.OUTPUT, 128, 10)
                .addItemStack(recipe.getResultItem(null));
    }
}
