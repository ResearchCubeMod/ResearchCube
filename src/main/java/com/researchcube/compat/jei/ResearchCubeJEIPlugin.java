package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.item.DriveItem;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.registry.ModItems;
import com.researchcube.registry.ModRecipeTypes;
import com.researchcube.util.NbtUtil;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.List;
import java.util.stream.Collectors;

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
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Drives with different stored recipes should be treated as different subtypes
        IIngredientSubtypeInterpreter<ItemStack> driveInterpreter = (stack, context) -> {
            if (!(stack.getItem() instanceof DriveItem)) return IIngredientSubtypeInterpreter.NONE;
            List<String> recipes = NbtUtil.readRecipes(stack);
            if (recipes.isEmpty()) return IIngredientSubtypeInterpreter.NONE;
            return recipes.stream().sorted().collect(Collectors.joining(","));
        };

        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.METADATA_IRRECOVERABLE.get(), driveInterpreter);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.METADATA_UNSTABLE.get(), driveInterpreter);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.METADATA_RECLAIMED.get(), driveInterpreter);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.METADATA_ENHANCED.get(), driveInterpreter);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.METADATA_ELABORATE.get(), driveInterpreter);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.METADATA_CYBERNETIC.get(), driveInterpreter);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.METADATA_SELF_AWARE.get(), driveInterpreter);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new DriveCraftingCategory(registration.getJeiHelpers().getGuiHelper()),
                new ProcessingCategory(registration.getJeiHelpers().getGuiHelper())
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

        // Collect all processing recipes
        List<ProcessingRecipe> processingRecipes = recipeManager
                .getAllRecipesFor(ModRecipeTypes.PROCESSING.get())
                .stream()
                .map(RecipeHolder::value)
                .toList();

        registration.addRecipes(ProcessingCategory.RECIPE_TYPE, processingRecipes);

        // Multi-page info for the Research Station
        registration.addIngredientInfo(
                new ItemStack(ModItems.RESEARCH_STATION_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.literal("Research Workflow"),
                Component.literal("1. Place a Cube and Drive of matching tier."),
                Component.literal("2. Add item costs to cost slots."),
                Component.literal("3. Fill the tank with the required research fluid."),
                Component.literal("4. Click Start — research progresses over time."),
                Component.literal("5. On completion, a recipe is imprinted onto the Drive."),
                Component.literal("6. Use the Drive in a Drive Crafting Table."),
                Component.literal(""),
                Component.literal("Fluid Tiers:"),
                Component.literal("  Thinking (cyan) — Basic tier"),
                Component.literal("  Pondering (purple) — Advanced tier"),
                Component.literal("  Reasoning (gold) — Precise/Flawless tier"),
                Component.literal("  Imagination (pink) — Self-Aware tier"),
                Component.literal(""),
                Component.literal("Drive Capacity (max recipes):"),
                Component.literal("  Unstable: 2, Reclaimed: 4, Enhanced: 8"),
                Component.literal("  Elaborate: 12, Cybernetic: 16"),
                Component.literal("  Self-Aware: unlimited")
        );

        // Multi-page info for the Processing Station
        registration.addIngredientInfo(
                new ItemStack(ModItems.PROCESSING_STATION_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.literal("Processing Station"),
                Component.literal("A general-purpose machine for complex recipes."),
                Component.literal(""),
                Component.literal("Capacity:"),
                Component.literal("  Up to 16 item inputs"),
                Component.literal("  Up to 2 fluid inputs (8000 mB each)"),
                Component.literal("  Up to 8 item outputs"),
                Component.literal("  Up to 1 fluid output (8000 mB)"),
                Component.literal(""),
                Component.literal("Recipes are defined via datapack."),
                Component.literal("Pipes can insert/extract fluids from any side.")
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The Research Station is the \"machine\" that creates the drive recipes
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.RESEARCH_STATION_ITEM.get()),
                DriveCraftingCategory.RECIPE_TYPE
        );

        // The Drive Crafting Table is the dedicated crafting station for drive recipes
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.DRIVE_CRAFTING_TABLE_ITEM.get()),
                DriveCraftingCategory.RECIPE_TYPE
        );

        // The Processing Station handles processing recipes
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.PROCESSING_STATION_ITEM.get()),
                ProcessingCategory.RECIPE_TYPE
        );
    }
}
