package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.recipe.DriveCraftingRecipeSerializer;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.recipe.ProcessingRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ResearchCubeMod.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, DriveCraftingRecipeSerializer> DRIVE_CRAFTING =
            RECIPE_SERIALIZERS.register("drive_crafting", DriveCraftingRecipeSerializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, ProcessingRecipeSerializer> PROCESSING =
            RECIPE_SERIALIZERS.register("processing", ProcessingRecipeSerializer::new);
}
