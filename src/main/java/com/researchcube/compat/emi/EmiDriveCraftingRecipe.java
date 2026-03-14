package com.researchcube.compat.emi;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.ClientResearchData;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.RecipeOutputResolver;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * EMI recipe wrapper for DriveCraftingRecipe.
 * Supports both shaped (3x3 grid) and shapeless (4x2 flat) layouts,
 * with a pre-loaded drive as the first input ingredient.
 */
public class EmiDriveCraftingRecipe extends BasicEmiRecipe {

    private final DriveCraftingRecipe recipe;
    private final List<ResearchDefinition> unlockingResearch;

    public EmiDriveCraftingRecipe(DriveCraftingRecipe recipe, ResourceLocation recipeId) {
        super(ResearchCubeEMIPlugin.DRIVE_CRAFTING,
                ResearchCubeMod.rl("/drive_crafting/" + recipeId.getPath()),
                150, 72);
        this.recipe = recipe;

        String requiredRecipeId = recipe.getRequiredRecipeId();
        this.unlockingResearch = RecipeOutputResolver.findResearchForRecipe(requiredRecipeId);

        // Build pre-loaded drive with recipe_id baked in
        ItemStack preloadedDrive;
        if (!unlockingResearch.isEmpty()) {
            preloadedDrive = RecipeOutputResolver.getDriveForTier(unlockingResearch.getFirst().getTier());
        } else {
            preloadedDrive = RecipeOutputResolver.getDriveForTier(
                    com.researchcube.research.ResearchTier.BASIC);
        }
        NbtUtil.addRecipe(preloadedDrive, requiredRecipeId);

        this.inputs.add(EmiStack.of(preloadedDrive));

        // Add ingredients
        if (recipe.isShaped() && recipe.getShapedPattern() != null) {
            for (Ingredient ing : recipe.getShapedPattern().ingredients()) {
                if (ing != null && !ing.isEmpty()) {
                    this.inputs.add(EmiIngredient.of(ing));
                } else {
                    this.inputs.add(EmiStack.EMPTY);
                }
            }
        } else {
            for (Ingredient ing : recipe.getIngredients()) {
                this.inputs.add(EmiIngredient.of(ing));
            }
        }

        this.outputs.add(EmiStack.of(recipe.getResultItem(null)));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // Drive slot at (1, 1)
        EmiIngredient driveInput = inputs.get(0);
        widgets.addSlot(driveInput, 1, 1).appendTooltip(buildDriveTooltip());

        if (recipe.isShaped()) {
            addShapedWidgets(widgets);
        } else {
            addShapelessWidgets(widgets);
        }

        // Arrow
        widgets.addFillingArrow(100, 24, 10000);

        // Output slot
        widgets.addSlot(outputs.get(0), 128, 18).recipeContext(this);
    }

    private void addShapedWidgets(WidgetHolder widgets) {
        ShapedRecipePattern pattern = recipe.getShapedPattern();
        if (pattern == null) {
            addShapelessWidgets(widgets);
            return;
        }
        int patternW = pattern.width();
        int patternH = pattern.height();
        int inputIdx = 1; // Skip the drive at index 0
        for (int row = 0; row < patternH; row++) {
            for (int col = 0; col < patternW; col++) {
                if (inputIdx < inputs.size()) {
                    widgets.addSlot(inputs.get(inputIdx), 22 + col * 18, 1 + row * 18);
                }
                inputIdx++;
            }
        }
    }

    private void addShapelessWidgets(WidgetHolder widgets) {
        int idx = 0;
        for (int i = 1; i < inputs.size(); i++) { // Skip drive at index 0
            int col = idx % 4;
            int row = idx / 4;
            widgets.addSlot(inputs.get(i), 22 + col * 18, 1 + row * 18);
            idx++;
        }
    }

    private Component buildDriveTooltip() {
        StringBuilder sb = new StringBuilder();
        sb.append("\u00A77Requires recipe: \u00A7e").append(recipe.getRequiredRecipeId());
        if (!unlockingResearch.isEmpty()) {
            for (ResearchDefinition def : unlockingResearch) {
                sb.append("\n");
                // Check completion status
                String status = ClientResearchData.isCompleted(def.getId().toString())
                        ? "\u00A7a\u2714 " : "\u00A7c\u2718 ";
                sb.append(status).append("\u00A7aUnlocked by: \u00A7f")
                        .append(def.getDisplayName())
                        .append(" \u00A77(").append(def.getTier().getDisplayName()).append(")");
            }
        }
        return Component.literal(sb.toString());
    }
}
