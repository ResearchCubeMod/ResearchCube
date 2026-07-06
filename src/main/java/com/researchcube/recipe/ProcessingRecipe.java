package com.researchcube.recipe;

import com.researchcube.registry.ModRecipeSerializers;
import com.researchcube.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Recipe type for the Processing Station.
 * Supports up to 16 item inputs, 2 fluid inputs, 8 item outputs, and 1 fluid output.
 *
 * <p>Like {@link DriveCraftingRecipe}, every processing recipe is research-locked: the
 * Processing Station only starts it when the inserted drive carries
 * {@link #getRequiredRecipeId()} (checked by the block entity, since fluid/drive state
 * lives outside the {@link RecipeInput}).
 */
public class ProcessingRecipe implements Recipe<RecipeInput> {

    /**
     * The recipe ID a drive must carry for this recipe to run.
     * Usually empty in JSON ("recipe_id" is optional) and bound to the recipe's own
     * ID after datapack load via {@link #bindId}; declaring it explicitly is only
     * needed when several recipe files should share one unlock ID.
     */
    private String recipeId;
    private final String group;
    private final List<Ingredient> ingredients;
    private final List<ProcessingFluidStack> fluidInputs;
    private final List<ItemStack> results;
    private final ProcessingFluidStack fluidOutput;
    private final int duration;

    public ProcessingRecipe(
            String recipeId,
            String group,
            List<Ingredient> ingredients,
            List<ProcessingFluidStack> fluidInputs,
            List<ItemStack> results,
            ProcessingFluidStack fluidOutput,
            int duration
    ) {
        this.recipeId = recipeId != null ? recipeId : "";
        this.group = group;
        this.ingredients = ingredients;
        this.fluidInputs = fluidInputs;
        this.results = results;
        this.fluidOutput = fluidOutput;
        this.duration = duration;
    }

    public String getRequiredRecipeId() {
        return recipeId;
    }

    /**
     * Bind this recipe to its own registry ID if the JSON omitted "recipe_id".
     * Called by ResearchManager after every datapack (re)load, before recipes are
     * synced to clients; the network codec always transmits the resolved ID.
     */
    public void bindId(net.minecraft.resources.ResourceLocation ownId) {
        if (this.recipeId.isEmpty()) {
            this.recipeId = ownId.toString();
        }
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        // Check that all ingredients are present (shapeless matching)
        // This is a simplified check - the block entity does full validation
        if (input.size() < ingredients.size()) {
            return false;
        }

        // Create a list to track which slots have been matched
        boolean[] matched = new boolean[input.size()];

        for (Ingredient ingredient : ingredients) {
            boolean found = false;
            for (int i = 0; i < input.size(); i++) {
                if (!matched[i] && ingredient.test(input.getItem(i))) {
                    matched[i] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        // Return the first result item
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.PROCESSING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.PROCESSING.get();
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(ingredients);
        return list;
    }

    // ── Custom Accessors ──

    public List<ProcessingFluidStack> getFluidInputs() {
        return fluidInputs;
    }

    public List<ItemStack> getResults() {
        return results;
    }

    public ProcessingFluidStack getFluidOutput() {
        return fluidOutput;
    }

    public int getDuration() {
        return duration;
    }

    /**
     * Check if this recipe has any fluid inputs.
     */
    public boolean hasFluidInputs() {
        return !fluidInputs.isEmpty();
    }

    /**
     * Check if this recipe has a fluid output.
     */
    public boolean hasFluidOutput() {
        return fluidOutput != null && fluidOutput.amount() > 0;
    }
}
