package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.recipe.ProcessingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ResearchCubeMod.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<DriveCraftingRecipe>> DRIVE_CRAFTING =
            RECIPE_TYPES.register("drive_crafting", () -> RecipeType.simple(ResearchCubeMod.rl("drive_crafting")));

    public static final DeferredHolder<RecipeType<?>, RecipeType<ProcessingRecipe>> PROCESSING =
            RECIPE_TYPES.register("processing", () -> RecipeType.simple(ResearchCubeMod.rl("processing")));
}
