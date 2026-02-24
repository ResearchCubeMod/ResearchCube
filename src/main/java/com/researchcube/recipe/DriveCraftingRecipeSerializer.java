package com.researchcube.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Serializer for DriveCraftingRecipe.
 * Handles JSON (MapCodec) and network (StreamCodec) serialization.
 *
 * JSON format:
 * {
 *   "type": "researchcube:drive_crafting",
 *   "group": "",                           // optional
 *   "recipe_id": "researchcube:processor_recipe_1",
 *   "ingredients": [ { "item": "minecraft:iron_ingot" }, ... ],
 *   "result": { "id": "minecraft:diamond", "count": 1 }
 * }
 */
public class DriveCraftingRecipeSerializer implements RecipeSerializer<DriveCraftingRecipe> {

    public static final MapCodec<DriveCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("recipe_id").forGetter(DriveCraftingRecipe::getRequiredRecipeId),
                    Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(
                            list -> {
                                Ingredient[] ingredients = list.toArray(Ingredient[]::new);
                                if (ingredients.length == 0) {
                                    return DataResult.error(() -> "No ingredients for drive crafting recipe");
                                }
                                if (ingredients.length > 8) {
                                    return DataResult.error(() -> "Too many ingredients for drive crafting recipe (max 8, need 1 slot for drive)");
                                }
                                NonNullList<Ingredient> nonNullList = NonNullList.create();
                                for (Ingredient ing : ingredients) {
                                    nonNullList.add(ing);
                                }
                                return DataResult.success(nonNullList);
                            },
                            nonNullList -> DataResult.success(nonNullList.stream().toList())
                    ).forGetter(DriveCraftingRecipe::getIngredients),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.getResultItem(null)),
                    Codec.STRING.optionalFieldOf("group", "").forGetter(DriveCraftingRecipe::getGroup)
            ).apply(instance, DriveCraftingRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, DriveCraftingRecipe> STREAM_CODEC =
            StreamCodec.of(
                    DriveCraftingRecipeSerializer::toNetwork,
                    DriveCraftingRecipeSerializer::fromNetwork
            );

    private static DriveCraftingRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        String group = buf.readUtf();
        String recipeId = buf.readUtf();
        int ingredientCount = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(ingredientCount, Ingredient.EMPTY);
        for (int i = 0; i < ingredientCount; i++) {
            ingredients.set(i, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
        }
        ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
        return new DriveCraftingRecipe(recipeId, ingredients, result, group);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buf, DriveCraftingRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeUtf(recipe.getRequiredRecipeId());
        buf.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ingredient);
        }
        ItemStack.STREAM_CODEC.encode(buf, recipe.getResultItem(null));
    }

    @Override
    public MapCodec<DriveCraftingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DriveCraftingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
