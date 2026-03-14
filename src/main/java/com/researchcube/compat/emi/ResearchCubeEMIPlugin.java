package com.researchcube.compat.emi;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.registry.ModItems;
import com.researchcube.registry.ModRecipeTypes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * EMI plugin for ResearchCube — full parity with the JEI plugin.
 * Loaded by EMI's annotation-based discovery; never referenced from main mod code.
 */
@EmiEntrypoint
public class ResearchCubeEMIPlugin implements EmiPlugin {

    public static final EmiRecipeCategory DRIVE_CRAFTING = new EmiRecipeCategory(
            ResearchCubeMod.rl("drive_crafting"),
            EmiStack.of(ModItems.METADATA_RECLAIMED.get())
    );

    public static final EmiRecipeCategory PROCESSING = new EmiRecipeCategory(
            ResearchCubeMod.rl("processing"),
            EmiStack.of(ModItems.PROCESSING_STATION_ITEM.get())
    );

    @Override
    public void register(EmiRegistry registry) {
        // Categories
        registry.addCategory(DRIVE_CRAFTING);
        registry.addCategory(PROCESSING);

        // Workstations (catalysts)
        registry.addWorkstation(DRIVE_CRAFTING, EmiStack.of(ModItems.RESEARCH_STATION_ITEM.get()));
        registry.addWorkstation(DRIVE_CRAFTING, EmiStack.of(ModItems.DRIVE_CRAFTING_TABLE_ITEM.get()));
        registry.addWorkstation(PROCESSING, EmiStack.of(ModItems.PROCESSING_STATION_ITEM.get()));

        // Drive Crafting recipes
        for (RecipeHolder<DriveCraftingRecipe> holder : registry.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.DRIVE_CRAFTING.get())) {
            registry.addRecipe(new EmiDriveCraftingRecipe(holder.value(), holder.id()));
        }

        // Processing recipes
        for (RecipeHolder<ProcessingRecipe> holder : registry.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.PROCESSING.get())) {
            registry.addRecipe(new EmiProcessingRecipe(holder.value(), holder.id()));
        }

        // Info pages
        EmiStack researchStation = EmiStack.of(ModItems.RESEARCH_STATION_ITEM.get());
        registry.addRecipe(new EmiInfoRecipe(
                List.of(researchStation),
                List.of(
                        Component.literal("Research Workflow"),
                        Component.literal("1. Place a Drive and Cube of matching tier in the Research Station."),
                        Component.literal("2. Provide item costs in the cost slots and fill the fluid tank."),
                        Component.literal("3. Click Start to begin research."),
                        Component.literal("4. On completion, a recipe is imprinted onto the Drive."),
                        Component.literal("5. Use the filled Drive in a Drive Crafting Table with ingredients."),
                        Component.literal(""),
                        Component.literal("Fluid Tiers:"),
                        Component.literal("  Thinking (cyan) — Basic research"),
                        Component.literal("  Pondering (purple) — Advanced research"),
                        Component.literal("  Reasoning (gold) — Precise/Flawless research"),
                        Component.literal("  Imagination (pink) — Self-Aware research"),
                        Component.literal(""),
                        Component.literal("Drive Capacity:"),
                        Component.literal("  Unstable: 2, Reclaimed: 4, Enhanced: 8"),
                        Component.literal("  Elaborate: 12, Cybernetic: 16, Self-Aware: unlimited")
                ),
                ResearchCubeMod.rl("info/research_station")
        ));

        EmiStack processingStation = EmiStack.of(ModItems.PROCESSING_STATION_ITEM.get());
        registry.addRecipe(new EmiInfoRecipe(
                List.of(processingStation),
                List.of(
                        Component.literal("The Processing Station is a general-purpose machine."),
                        Component.literal("It accepts up to 16 item inputs, 2 fluid inputs,"),
                        Component.literal("and produces up to 8 item outputs and 1 fluid output."),
                        Component.literal("Processing recipes are defined via datapack.")
                ),
                ResearchCubeMod.rl("info/processing_station")
        ));
    }
}
