package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.recipe.DriveCraftingRecipe;
import com.researchcube.registry.ModItems;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.util.NbtUtil;
import com.researchcube.util.RecipeOutputResolver;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import java.util.List;

/**
 * JEI recipe category for Drive Crafting recipes.
 * Shows the drive requirement, ingredients, and the recipe output.
 * Supports both shaped (matching the pattern grid) and shapeless (flat 4-wide) layouts.
 *
 * <p>Slot backgrounds and the arrow are drawn with JEI's own drawables so the layout
 * matches vanilla recipe categories; only the panel, labels and the research-completion
 * indicator are drawn manually.
 */
public class DriveCraftingCategory implements IRecipeCategory<DriveCraftingRecipe> {

    public static final ResourceLocation UID = ResearchCubeMod.rl("drive_crafting");
    public static final RecipeType<DriveCraftingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, DriveCraftingRecipe.class);

    private static final int WIDTH = 150;
    private static final int HEIGHT = 72;

    // Fixed slot anchors (top-left of the ingredient, matching setStandardSlotBackground's -1,-1 offset).
    private static final int DRIVE_X = 1;
    private static final int DRIVE_Y = 1;
    private static final int GRID_X = 22;
    private static final int GRID_Y = 1;
    private static final int SLOT_STRIDE = 18;
    private static final int SHAPELESS_COLUMNS = 4;
    private static final int OUTPUT_X = 128;
    private static final int OUTPUT_Y = 18;
    private static final int ARROW_X = 100;
    private static final int ARROW_Y = 24;

    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private final Component title;

    public DriveCraftingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.METADATA_RECLAIMED.get()));
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.researchcube.category.drive_crafting");
    }

    @Override
    public RecipeType<DriveCraftingRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void draw(DriveCraftingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // "Drive" label above the drive slot.
        g.drawString(font, Component.translatable("jei.researchcube.drive_crafting.slot.drive"),
                DRIVE_X, DRIVE_Y - 11, 0xFFAAAAAA, false);

        // Arrow from ingredients to output (JEI's own drawable, vertically centred on the row).
        arrow.draw(g, ARROW_X, ARROW_Y + (SLOT_STRIDE - arrow.getHeight()) / 2);

        // Research-completion indicator in the bottom-right corner.
        JeiRenderHelper.drawCompletionIndicator(g, font, recipe.getRequiredRecipeId(),
                WIDTH - 11, HEIGHT - 12);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DriveCraftingRecipe recipe, IFocusGroup focuses) {
        // Resolve which research unlocks this recipe and pick the matching drive tier.
        String requiredRecipeId = recipe.getRequiredRecipeId();
        List<ResearchDefinition> unlockingResearch = RecipeOutputResolver.findResearchForRecipe(requiredRecipeId);

        // A drive pre-loaded with this recipe_id, so "show uses" on an imprinted drive finds it.
        ItemStack preloadedDrive = unlockingResearch.isEmpty()
                ? new ItemStack(ModItems.METADATA_RECLAIMED.get())
                : RecipeOutputResolver.getDriveForTier(unlockingResearch.getFirst().getTier());
        NbtUtil.addRecipe(preloadedDrive, requiredRecipeId);

        builder.addSlot(RecipeIngredientRole.INPUT, DRIVE_X, DRIVE_Y)
                .setStandardSlotBackground()
                .addItemStack(preloadedDrive)
                .addRichTooltipCallback((slotView, tooltip) ->
                        JeiRenderHelper.addDriveTooltip(tooltip, requiredRecipeId, unlockingResearch));

        if (recipe.isShaped() && recipe.getShapedPattern() != null) {
            addShapedSlots(builder, recipe.getShapedPattern());
        } else {
            addShapelessSlots(builder, recipe.getIngredients());
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, OUTPUT_Y)
                .setOutputSlotBackground()
                .addItemStack(recipe.getResultItem(null));
    }

    /** Lay out shaped ingredients in the pattern's own grid, skipping empty (air) cells. */
    private void addShapedSlots(IRecipeLayoutBuilder builder, ShapedRecipePattern pattern) {
        int width = pattern.width();
        int height = pattern.height();
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                Ingredient ingredient = pattern.ingredients().get(row * width + col);
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                builder.addSlot(RecipeIngredientRole.INPUT,
                                GRID_X + col * SLOT_STRIDE, GRID_Y + row * SLOT_STRIDE)
                        .setStandardSlotBackground()
                        .addIngredients(ingredient);
            }
        }
    }

    /** Lay out shapeless ingredients left-to-right, wrapping every {@link #SHAPELESS_COLUMNS}. */
    private void addShapelessSlots(IRecipeLayoutBuilder builder, List<Ingredient> ingredients) {
        int placed = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            int col = placed % SHAPELESS_COLUMNS;
            int row = placed / SHAPELESS_COLUMNS;
            builder.addSlot(RecipeIngredientRole.INPUT,
                            GRID_X + col * SLOT_STRIDE, GRID_Y + row * SLOT_STRIDE)
                    .setStandardSlotBackground()
                    .addIngredients(ingredient);
            placed++;
        }
    }
}
