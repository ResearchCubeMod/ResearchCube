package com.researchcube.util;

import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.recipe.ProcessingFluidStack;
import com.researchcube.recipe.ProcessingRecipe;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.research.ResearchRegistry;
import com.researchcube.research.ResearchTier;
import com.researchcube.research.WeightedRecipe;
import com.researchcube.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            // Malformed recipe ID or other issue; fall back to empty
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

    // ── Input / output tooltip lines ──

    // Colors for the drive inspector's IO tooltip lines.
    private static final int COLOR_IN_LABEL = 0xFFAA88;   // soft amber "In:" line
    private static final int COLOR_OUT_LABEL = 0x88FF88;  // green "Out:" line

    /**
     * Build the "In:" / "Out:" tooltip lines for a stored recipe id, resolving both
     * {@link DriveCraftingRecipe} and {@link ProcessingRecipe} against the given level's
     * {@link RecipeManager}. Item and fluid stacks are aggregated by display name into
     * "Nx Name" fragments joined on one line each, e.g.
     * {@code In: 2x Iron Ingot, 1x Redstone} / {@code Out: 1x Observer}.
     *
     * Returns an empty list when the level is null or the recipe cannot be resolved to a
     * supported type; callers should fall back to their existing display in that case.
     */
    public static List<Component> resolveIoTooltip(@Nullable Level level, String recipeId) {
        RecipeManager rm = getRecipeManager(level);
        if (rm == null) return List.of();

        try {
            ResourceLocation rl = ResourceLocation.parse(recipeId);
            Optional<RecipeHolder<?>> holder = rm.byKey(rl);
            if (holder.isEmpty()) return List.of();

            Object recipe = holder.get().value();
            if (recipe instanceof DriveCraftingRecipe dcr) {
                return driveCraftingIo(dcr);
            }
            if (recipe instanceof ProcessingRecipe pr) {
                return processingIo(pr);
            }
        } catch (Exception e) {
            // Malformed recipe id or resolution failure; fall back to no IO lines.
        }
        return List.of();
    }

    private static List<Component> driveCraftingIo(DriveCraftingRecipe recipe) {
        List<Component> lines = new ArrayList<>();

        List<String> inputs = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ing : recipe.getIngredients()) {
            String name = ingredientName(ing);
            if (name == null) continue; // empty pattern slot
            counts.merge(name, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            inputs.add(e.getValue() + "x " + e.getKey());
        }

        List<String> outputs = new ArrayList<>();
        ItemStack result = recipe.getResultItem(null);
        if (!result.isEmpty()) {
            outputs.add(result.getCount() + "x " + result.getHoverName().getString());
        }

        addIoLines(lines, inputs, outputs);
        return lines;
    }

    private static List<Component> processingIo(ProcessingRecipe recipe) {
        List<Component> lines = new ArrayList<>();

        List<String> inputs = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ing : recipe.getIngredients()) {
            String name = ingredientName(ing);
            if (name == null) continue;
            counts.merge(name, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            inputs.add(e.getValue() + "x " + e.getKey());
        }
        for (ProcessingFluidStack fluid : recipe.getFluidInputs()) {
            inputs.add(fluid.amount() + "mB " + fluidName(fluid));
        }

        List<String> outputs = new ArrayList<>();
        for (ItemStack out : recipe.getResults()) {
            if (!out.isEmpty()) {
                outputs.add(out.getCount() + "x " + out.getHoverName().getString());
            }
        }
        if (recipe.hasFluidOutput()) {
            ProcessingFluidStack fluid = recipe.getFluidOutput();
            outputs.add(fluid.amount() + "mB " + fluidName(fluid));
        }

        addIoLines(lines, inputs, outputs);
        return lines;
    }

    /**
     * Append the "In:" and "Out:" lines to {@code lines} from pre-formatted stack fragments.
     * Uses translatable prefixes so the labels are localizable; each side collapses to one
     * comma-joined line, kept compact for the inspector tooltip.
     */
    private static void addIoLines(List<Component> lines, List<String> inputs, List<String> outputs) {
        if (!inputs.isEmpty()) {
            lines.add(Component.translatable("gui.researchcube.drive_inspector.io.inputs", String.join(", ", inputs))
                    .withStyle(s -> s.withColor(COLOR_IN_LABEL)));
        }
        if (!outputs.isEmpty()) {
            lines.add(Component.translatable("gui.researchcube.drive_inspector.io.outputs", String.join(", ", outputs))
                    .withStyle(s -> s.withColor(COLOR_OUT_LABEL)));
        }
    }

    /**
     * Human-readable name for an ingredient's first matching stack, or null for an empty slot.
     * Ingredients can match multiple items (tags); the first option is representative for a
     * compact tooltip.
     */
    @Nullable
    private static String ingredientName(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return null;
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return null;
        return items[0].getHoverName().getString();
    }

    private static String fluidName(ProcessingFluidStack fluid) {
        FluidStack stack = fluid.toFluidStack();
        if (!stack.isEmpty()) {
            return stack.getHoverName().getString();
        }
        Fluid f = fluid.getFluid();
        return f != null ? f.getFluidType().getDescription().getString() : fluid.fluidId().toString();
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
