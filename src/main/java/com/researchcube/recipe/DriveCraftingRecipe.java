package com.researchcube.recipe;

import com.researchcube.item.DriveItem;
import com.researchcube.registry.ModRecipeSerializers;
import com.researchcube.registry.ModRecipeTypes;
import com.researchcube.util.NbtUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom crafting recipe that requires:
 *   - A Drive item in the crafting grid containing a specific recipe_id in its NBT
 *   - Standard shapeless ingredients
 *
 * The Drive persists after crafting — only the matched recipe_id is removed from its NBT.
 *
 * JSON format:
 * {
 *   "type": "researchcube:drive_crafting",
 *   "recipe_id": "researchcube:processor_recipe_1",
 *   "ingredients": [ { "item": "minecraft:iron_ingot" }, ... ],
 *   "result": { "id": "minecraft:diamond", "count": 1 }
 * }
 */
public class DriveCraftingRecipe implements CraftingRecipe {

    private final String recipeId;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;
    private final String group;

    public DriveCraftingRecipe(String recipeId, NonNullList<Ingredient> ingredients, ItemStack result, String group) {
        this.recipeId = recipeId;
        this.ingredients = ingredients;
        this.result = result;
        this.group = group != null ? group : "";
    }

    /**
     * The recipe ID that must be present on the drive's NBT.
     */
    public String getRequiredRecipeId() {
        return recipeId;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        // We need to find exactly one DriveItem with the correct recipe_id,
        // and the remaining items must match the ingredients (shapeless).

        boolean driveFound = false;
        List<ItemStack> nonDriveItems = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;

            if (!driveFound && stack.getItem() instanceof DriveItem && NbtUtil.hasRecipe(stack, recipeId)) {
                driveFound = true;
            } else {
                nonDriveItems.add(stack);
            }
        }

        if (!driveFound) return false;

        // Check that all non-drive items match the ingredients (shapeless matching)
        return matchesShapeless(nonDriveItems, ingredients);
    }

    /**
     * Shapeless matching: each ingredient must be satisfied by exactly one item.
     */
    private static boolean matchesShapeless(List<ItemStack> items, NonNullList<Ingredient> ingredients) {
        if (items.size() != ingredients.size()) return false;

        boolean[] used = new boolean[items.size()];
        for (Ingredient ingredient : ingredients) {
            boolean matched = false;
            for (int i = 0; i < items.size(); i++) {
                if (!used[i] && ingredient.test(items.get(i))) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        // ingredients + 1 for the drive
        return width * height >= ingredients.size() + 1;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DRIVE_CRAFTING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.DRIVE_CRAFTING.get();
    }

    /**
     * The drive is NOT consumed and its NBT is NOT modified — it is returned intact
     * so the same recipe can be crafted repeatedly without losing the stored recipe_id.
     * All other items are consumed normally.
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        boolean driveHandled = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!driveHandled && stack.getItem() instanceof DriveItem && NbtUtil.hasRecipe(stack, recipeId)) {
                // Return the drive unchanged — recipe_id is kept for future uses
                remaining.set(i, stack.copy());
                driveHandled = true;
            }
            // Non-drive items: remain EMPTY (consumed)
        }

        return remaining;
    }
}
