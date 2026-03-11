package com.researchcube.util;

import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.ResearchTier;
import com.researchcube.research.WeightedRecipe;
import com.researchcube.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client-side utility for resolving recipe IDs to their output ItemStacks
 * and finding which research definitions unlock a given recipe.
 *
 * All methods in this class reference Minecraft.getInstance() and must only
 * be called from the client side (screens, tooltips, JEI, etc.).
 */
public final class RecipeOutputResolver {

    private RecipeOutputResolver() {}

    /**
     * Resolve a recipe ID string to its output ItemStack.
     * Returns ItemStack.EMPTY if the recipe cannot be found or the client is not connected.
     */
    public static ItemStack resolveOutput(String recipeId) {
        RecipeManager rm = getRecipeManager();
        if (rm == null) return ItemStack.EMPTY;

        try {
            ResourceLocation rl = ResourceLocation.parse(recipeId);
            Optional<RecipeHolder<?>> holder = rm.byKey(rl);
            if (holder.isPresent() && holder.get().value() instanceof DriveCraftingRecipe dcr) {
                return dcr.getResultItem(null);
            }
        } catch (Exception e) {
            // Malformed recipe ID or other issue — fall back to empty
        }
        return ItemStack.EMPTY;
    }

    /**
     * Format a recipe output as a human-readable string.
     * Returns e.g. "Repeater ×4" or falls back to the raw recipe ID.
     */
    public static String formatOutput(String recipeId) {
        ItemStack output = resolveOutput(recipeId);
        if (!output.isEmpty()) {
            String name = output.getHoverName().getString();
            return output.getCount() > 1 ? name + " \u00d7" + output.getCount() : name;
        }
        return recipeId; // fallback to raw ID
    }

    /**
     * Find all ResearchDefinitions whose recipe pool contains the given recipe ID.
     */
    public static List<ResearchDefinition> findResearchForRecipe(String recipeId) {
        ResourceLocation target;
        try {
            target = ResourceLocation.parse(recipeId);
        } catch (Exception e) {
            return List.of();
        }

        List<ResearchDefinition> results = new ArrayList<>();
        for (ResearchDefinition def : ResearchRegistry.getAll()) {
            for (WeightedRecipe wr : def.getWeightedRecipePool()) {
                if (wr.id().equals(target)) {
                    results.add(def);
                    break;
                }
            }
        }
        return results;
    }

    /**
     * Get the appropriate drive item for a given research tier.
     * Returns a new ItemStack of the matching drive, or ItemStack.EMPTY if no drive maps to the tier.
     */
    public static ItemStack getDriveForTier(ResearchTier tier) {
        return switch (tier) {
            case IRRECOVERABLE -> new ItemStack(ModItems.METADATA_IRRECOVERABLE.get());
            case UNSTABLE -> new ItemStack(ModItems.METADATA_UNSTABLE.get());
            case BASIC -> new ItemStack(ModItems.METADATA_RECLAIMED.get());
            case ADVANCED -> new ItemStack(ModItems.METADATA_ENHANCED.get());
            case PRECISE -> new ItemStack(ModItems.METADATA_ELABORATE.get());
            case FLAWLESS -> new ItemStack(ModItems.METADATA_CYBERNETIC.get());
            case SELF_AWARE -> new ItemStack(ModItems.METADATA_SELF_AWARE.get());
        };
    }

    @Nullable
    private static RecipeManager getRecipeManager() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            return mc.level.getRecipeManager();
        }
        return null;
    }
}
