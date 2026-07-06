package com.researchcube.util;

import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.ResearchTier;
import com.researchcube.research.WeightedRecipe;
import com.researchcube.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility for resolving recipe IDs to their output ItemStacks and finding which
 * research definitions unlock a given recipe.
 *
 * Recipe resolution needs a {@link RecipeManager}, which only exists on a connected
 * level. Callers pass the relevant {@link Level} (nullable); a null level yields no
 * output resolution. This keeps the class free of client-only references so it stays
 * safe to reference from common code (e.g. item tooltips).
 */
public final class RecipeOutputResolver {

    private RecipeOutputResolver() {}

    /**
     * Resolve a recipe ID string to its output ItemStack.
     * Handles both drive_crafting and processing recipes (both are research-locked
     * unlock targets); other recipe types fall back to EMPTY.
     * Returns ItemStack.EMPTY if the level is null or the recipe cannot be found.
     */
    public static ItemStack resolveOutput(@Nullable Level level, String recipeId) {
        RecipeManager rm = getRecipeManager(level);
        if (rm == null) return ItemStack.EMPTY;

        try {
            ResourceLocation rl = ResourceLocation.parse(recipeId);
            Optional<RecipeHolder<?>> holder = rm.byKey(rl);
            if (holder.isPresent()) {
                if (holder.get().value() instanceof DriveCraftingRecipe dcr) {
                    return dcr.getResultItem(null);
                }
                if (holder.get().value() instanceof ProcessingRecipe pr) {
                    // First item output represents the unlock in list/tooltip displays
                    return pr.getResultItem(null);
                }
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
    public static String formatOutput(@Nullable Level level, String recipeId) {
        ItemStack output = resolveOutput(level, recipeId);
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
    private static RecipeManager getRecipeManager(@Nullable Level level) {
        return level != null ? level.getRecipeManager() : null;
    }
}
