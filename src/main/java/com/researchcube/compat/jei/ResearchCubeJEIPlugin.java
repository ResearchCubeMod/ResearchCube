package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.registry.ModItems;
import com.researchcube.registry.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.List;

/**
 * JEI plugin for ResearchCube.
 * Registers the Drive Crafting recipe category and populates it with loaded recipes.
 * Also adds info pages for research concepts.
 *
 * This class is loaded via JEI's annotation-based discovery.
 * Since JEI is a compileOnly dependency, this class is only loaded when JEI is present.
 */
@JeiPlugin
public class ResearchCubeJEIPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = ResearchCubeMod.rl("jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new DriveCraftingCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RecipeManager recipeManager = mc.level.getRecipeManager();

        // Collect all drive_crafting recipes
        List<DriveCraftingRecipe> driveCraftingRecipes = recipeManager
                .getAllRecipesFor(ModRecipeTypes.DRIVE_CRAFTING.get())
                .stream()
                .map(RecipeHolder::value)
                .toList();

        registration.addRecipes(DriveCraftingCategory.RECIPE_TYPE, driveCraftingRecipes);

        // Add info page for the Research Station
        registration.addIngredientInfo(
                new ItemStack(ModItems.RESEARCH_STATION_ITEM.get()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                net.minecraft.network.chat.Component.literal(
                        "The Research Station is used to research new recipes. " +
                        "Insert a Drive and a Cube of the appropriate tier, provide item costs, " +
                        "then start research. On completion, a recipe ID is imprinted onto the Drive. " +
                        "Use the Drive in a crafting grid with the required materials to craft the result."
                )
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The Research Station is the \"machine\" that creates the drive recipes
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.RESEARCH_STATION_ITEM.get()),
                DriveCraftingCategory.RECIPE_TYPE
        );        // The Drive Crafting Table is the dedicated crafting station for drive recipes
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.DRIVE_CRAFTING_TABLE_ITEM.get()),
                DriveCraftingCategory.RECIPE_TYPE
        );    }
}
