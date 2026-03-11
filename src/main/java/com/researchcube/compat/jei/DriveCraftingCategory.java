package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.registry.ModItems;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.RecipeOutputResolver;
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

import java.util.List;

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
        // Resolve which research unlocks this recipe and determine the correct drive tier
        String requiredRecipeId = recipe.getRequiredRecipeId();
        List<ResearchDefinition> unlockingResearch = RecipeOutputResolver.findResearchForRecipe(requiredRecipeId);

        // Create a pre-loaded drive with the recipe_id already imprinted.
        // This lets players use JEI's "show uses" on a filled drive to find matching recipes.
        ItemStack preloadedDrive;
        if (!unlockingResearch.isEmpty()) {
            // Use the tier of the first research that unlocks this recipe
            preloadedDrive = RecipeOutputResolver.getDriveForTier(unlockingResearch.getFirst().getTier());
        } else {
            // Fallback: use a Basic (reclaimed) drive
            preloadedDrive = new ItemStack(ModItems.METADATA_RECLAIMED.get());
        }
        NbtUtil.addRecipe(preloadedDrive, requiredRecipeId);

        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .addItemStack(preloadedDrive)
                .addTooltipCallback((recipeSlotView, tooltip) -> {
                    tooltip.add(Component.literal("\u00A77Requires recipe: \u00A7e" + requiredRecipeId));
                    // Show which research unlocks this recipe
                    if (!unlockingResearch.isEmpty()) {
                        for (ResearchDefinition def : unlockingResearch) {
                            int color = def.getTier().getColor();
                            String hexColor = String.format("%06X", color);
                            tooltip.add(Component.literal("\u00A7aUnlocked by: \u00A7f" + def.getDisplayName()
                                    + " \u00A77(" + def.getTier().getDisplayName() + ")"));
                        }
                    }
                });

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
