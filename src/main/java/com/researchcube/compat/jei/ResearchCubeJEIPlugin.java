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
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
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
        // Drives with different stored recipes should be treated as different subtypes.
        // Subtype data is the sorted, comma-joined recipe ids (null = no distinct subtype).
        ISubtypeInterpreter<ItemStack> driveInterpreter = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, UidContext context) {
                if (!(stack.getItem() instanceof DriveItem)) return null;
                List<String> recipes = NbtUtil.readRecipes(stack);
                if (recipes.isEmpty()) return null;
                return recipes.stream().sorted().collect(Collectors.joining(","));
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                Object data = getSubtypeData(stack, context);
                return data == null ? "" : data.toString();
            }
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

        // Multi-page info for the Research Station.
        // The Research Station still has an explicit Start action (see ResearchTableScreen),
        // so the "Click Start" step is intentionally retained.
        registration.addIngredientInfo(
                new ItemStack(ModItems.RESEARCH_STATION_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("jei.researchcube.research_station.title"),
                Component.translatable("jei.researchcube.research_station.step1"),
                Component.translatable("jei.researchcube.research_station.step2"),
                Component.translatable("jei.researchcube.research_station.step3"),
                Component.translatable("jei.researchcube.research_station.step4"),
                Component.translatable("jei.researchcube.research_station.step5"),
                Component.translatable("jei.researchcube.research_station.step6"),
                Component.empty(),
                Component.translatable("jei.researchcube.research_station.fluids_header"),
                Component.translatable("jei.researchcube.research_station.fluid_thinking"),
                Component.translatable("jei.researchcube.research_station.fluid_pondering"),
                Component.translatable("jei.researchcube.research_station.fluid_reasoning"),
                Component.translatable("jei.researchcube.research_station.fluid_imagination"),
                Component.empty(),
                Component.translatable("jei.researchcube.research_station.capacity_header"),
                Component.translatable("jei.researchcube.research_station.capacity_line1"),
                Component.translatable("jei.researchcube.research_station.capacity_line2"),
                Component.translatable("jei.researchcube.research_station.capacity_line3")
        );

        // Multi-page info for the Processing Station.
        // The station auto-starts once valid inputs and the unlocking drive are present
        // (the old Start button and Auto toggle were removed), and item/fluid I/O is now
        // configured per side via the in-GUI side-config panel.
        registration.addIngredientInfo(
                new ItemStack(ModItems.PROCESSING_STATION_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("jei.researchcube.processing_station.title"),
                Component.translatable("jei.researchcube.processing_station.summary"),
                Component.empty(),
                Component.translatable("jei.researchcube.processing_station.capacity_header"),
                Component.translatable("jei.researchcube.processing_station.capacity_item_in"),
                Component.translatable("jei.researchcube.processing_station.capacity_fluid_in"),
                Component.translatable("jei.researchcube.processing_station.capacity_item_out"),
                Component.translatable("jei.researchcube.processing_station.capacity_fluid_out"),
                Component.empty(),
                Component.translatable("jei.researchcube.processing_station.drive"),
                Component.translatable("jei.researchcube.processing_station.auto_start"),
                Component.translatable("jei.researchcube.processing_station.side_io"),
                Component.translatable("jei.researchcube.processing_station.datapack")
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The Drive Crafting Table is the dedicated crafting station for drive recipes
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.DRIVE_CRAFTING_TABLE_ITEM.get()),
                DriveCraftingCategory.RECIPE_TYPE
        );

        // The Auto Drive Crafting Table crafts the same drive recipes automatically
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.AUTO_DRIVE_CRAFTING_TABLE_ITEM.get()),
                DriveCraftingCategory.RECIPE_TYPE
        );

        // The Processing Station handles processing recipes
        registration.addRecipeCatalyst(
                new ItemStack(ModItems.PROCESSING_STATION_ITEM.get()),
                ProcessingCategory.RECIPE_TYPE
        );
    }
}
