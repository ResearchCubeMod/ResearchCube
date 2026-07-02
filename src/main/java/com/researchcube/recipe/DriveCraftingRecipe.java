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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom crafting recipe that requires:
 *   - A Drive item in the crafting grid containing a specific recipe_id in its NBT
 *   - Shapeless OR shaped ingredients
 *
 * When "pattern" + "key" are present in JSON, the recipe is shaped (position-sensitive).
 * When "ingredients" is present, the recipe is shapeless (order-independent).
 * The Drive persists after crafting — it is returned intact.
 */
public class DriveCraftingRecipe implements CraftingRecipe {

    /**
     * The recipe ID a drive must carry for this recipe to match.
     * Usually empty in JSON ("recipe_id" is optional) and bound to the recipe's own
     * ID after datapack load via {@link #bindId} — declaring it explicitly is only
     * needed when several recipe files should share one unlock ID.
     */
    private String recipeId;
    private final NonNullList<Ingredient> ingredients;
    @Nullable
    private final ShapedRecipePattern shapedPattern;
    private final ItemStack result;
    private final String group;

    /** Shapeless constructor. */
    public DriveCraftingRecipe(String recipeId, NonNullList<Ingredient> ingredients, ItemStack result, String group) {
        this.recipeId = recipeId != null ? recipeId : "";
        this.ingredients = ingredients;
        this.shapedPattern = null;
        this.result = result;
        this.group = group != null ? group : "";
    }

    /** Shaped constructor. */
    public DriveCraftingRecipe(String recipeId, ShapedRecipePattern pattern, ItemStack result, String group) {
        this.recipeId = recipeId != null ? recipeId : "";
        this.ingredients = NonNullList.create();
        this.shapedPattern = pattern;
        this.result = result;
        this.group = group != null ? group : "";
    }

    public String getRequiredRecipeId() {
        return recipeId;
    }

    /**
     * Bind this recipe to its own registry ID if the JSON omitted "recipe_id".
     * Called by ResearchManager after every datapack (re)load, before recipes are
     * synced to clients — the network codec always transmits the resolved ID.
     */
    public void bindId(net.minecraft.resources.ResourceLocation ownId) {
        if (this.recipeId.isEmpty()) {
            this.recipeId = ownId.toString();
        }
    }

    public boolean isShaped() {
        return shapedPattern != null;
    }

    @Nullable
    public ShapedRecipePattern getShapedPattern() {
        return shapedPattern;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (recipeId.isEmpty()) {
            // Not bound yet (should not happen in practice) — never match rather than
            // matching drives with unrelated content.
            return false;
        }
        if (isShaped()) {
            return matchesShapedMode(input);
        }
        return matchesShapelessMode(input);
    }

    /**
     * Shaped matching: find the drive, remove it from the grid, then delegate
     * to ShapedRecipePattern.matches() which handles offset sliding and mirroring.
     */
    private boolean matchesShapedMode(CraftingInput input) {
        int driveSlot = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof DriveItem && NbtUtil.hasRecipe(stack, recipeId)) {
                driveSlot = i;
                break;
            }
        }
        if (driveSlot < 0) return false;

        // Create a modified input with the drive replaced by empty
        List<ItemStack> modified = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            modified.add(i == driveSlot ? ItemStack.EMPTY : input.getItem(i));
        }
        CraftingInput modifiedInput = CraftingInput.of(input.width(), input.height(), modified);

        return shapedPattern.matches(modifiedInput);
    }

    /**
     * Shapeless matching: find the drive, then match remaining items against ingredients.
     */
    private boolean matchesShapelessMode(CraftingInput input) {
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
        return matchesShapeless(nonDriveItems, ingredients);
    }

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
        if (isShaped()) {
            // Pattern must fit AND there must be at least one extra slot for the drive
            return width >= shapedPattern.width() && height >= shapedPattern.height()
                    && width * height > shapedPattern.width() * shapedPattern.height();
        }
        return width * height >= ingredients.size() + 1;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        if (isShaped()) {
            NonNullList<Ingredient> list = NonNullList.create();
            for (Ingredient ing : shapedPattern.ingredients()) {
                list.add(ing != null ? ing : Ingredient.EMPTY);
            }
            return list;
        }
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
