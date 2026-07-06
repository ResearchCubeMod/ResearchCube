package com.researchcube.compat.jei;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.recipe.ProcessingFluidStack;
import com.researchcube.recipe.ProcessingRecipe;
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
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * JEI recipe category for Processing Station recipes.
 * Shows item inputs (up to 16, 4×4 grid), fluid inputs (up to 2), the research-locked drive,
 * item outputs (up to 8, 2×4 grid), and the fluid output, along with the processing duration.
 *
 * <p>The layout mirrors the real Processing Station GUI arrangement players see: the 4x4 item
 * input grid on the left, a center control column holding the three fluid gauges (input 1,
 * input 2, output) above the arrow, duration and research-locked drive, and the 2x4 item
 * output grid on the right, matching
 * {@link com.researchcube.client.screen.ProcessingStationScreen}. The exact GUI pixel spans are
 * scaled down to a tight 18px slot pitch, keeping the arrangement recognizable within JEI bounds.
 *
 * <p>Slot backgrounds, the arrow and fluid rendering use JEI's own drawables/helpers; only the
 * headers, duration label and the research-completion indicator are drawn manually.
 */
public class ProcessingCategory implements IRecipeCategory<ProcessingRecipe> {

    public static final ResourceLocation UID = ResearchCubeMod.rl("processing");
    public static final RecipeType<ProcessingRecipe> RECIPE_TYPE =
            new RecipeType<>(UID, ProcessingRecipe.class);

    private static final int WIDTH = 178;
    private static final int HEIGHT = 100;

    private static final int SLOT_STRIDE = 18;
    private static final int FLUID_SIZE = 16;

    // Header label row baseline, above the slots/gauges.
    private static final int LABEL_Y = 1;

    // Item input grid (4×4) on the left: ingredient top-left corners.
    private static final int INPUT_GRID_X = 1;
    private static final int INPUT_GRID_Y = 12;
    private static final int INPUT_COLS = 4;
    private static final int INPUT_ROWS = 4;

    // Item output grid (2×4) on the right.
    private static final int OUTPUT_GRID_X = 138;
    private static final int OUTPUT_GRID_Y = 12;
    private static final int OUTPUT_COLS = 2;
    private static final int OUTPUT_ROWS = 4;

    // Center control column: three fluid gauges in a row (input 1, input 2, output) at the top,
    // then the arrow, duration and drive slot stacked below, mirroring the machine's tank row
    // above its progress bar and drive slot.
    private static final int FLUID_Y = 12;
    private static final int FLUID_IN1_X = 78;
    private static final int FLUID_IN2_X = 96;
    private static final int FLUID_OUT_X = 114;

    private static final int DRIVE_X = 96;
    private static final int DRIVE_Y = 72;

    // Arrow centered on the control column, below the fluid gauges.
    private static final int ARROW_X = 92;
    private static final int ARROW_Y = 40;
    // Horizontal center of the control column (used to center the duration text and labels).
    private static final int CONTROL_CENTER_X = 104;

    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private final Component title;

    public ProcessingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModItems.PROCESSING_STATION_ITEM.get()));
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.researchcube.category.processing");
    }

    @Override
    public RecipeType<ProcessingRecipe> getRecipeType() {
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
    public void draw(ProcessingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // Section headers, centered over their sections (mirrors the machine GUI's labels).
        int inputCenterX = INPUT_GRID_X + (INPUT_COLS * SLOT_STRIDE) / 2 - SLOT_STRIDE / 2;
        int outputCenterX = OUTPUT_GRID_X + (OUTPUT_COLS * SLOT_STRIDE) / 2 - SLOT_STRIDE / 2;
        drawCentered(g, font, Component.translatable("jei.researchcube.processing.header.inputs"),
                inputCenterX, LABEL_Y, 0xFFAAAAAA);
        drawCentered(g, font, Component.translatable("jei.researchcube.processing.header.outputs"),
                outputCenterX, LABEL_Y, 0xFFAAAAAA);

        // Fluid gauge labels (input 1 / input 2 / output), centered over each gauge.
        drawCentered(g, font, Component.literal("I1"), FLUID_IN1_X + 8, LABEL_Y, 0xFF4F9BFF);
        drawCentered(g, font, Component.literal("I2"), FLUID_IN2_X + 8, LABEL_Y, 0xFFB16CFF);
        drawCentered(g, font, Component.literal("O"), FLUID_OUT_X + 8, LABEL_Y, 0xFFFFB547);

        // Arrow, centered in the control column below the gauges.
        arrow.draw(g, ARROW_X, ARROW_Y + (SLOT_STRIDE - arrow.getHeight()) / 2);

        // Duration below the arrow, centered on the control column.
        String durationStr = Component.translatable("jei.researchcube.processing.duration",
                String.format("%.1f", recipe.getDuration() / 20.0f)).getString();
        g.drawString(font, durationStr, CONTROL_CENTER_X - font.width(durationStr) / 2,
                ARROW_Y + 20, 0xFF888888, false);

        // Research-completion indicator under the drive slot.
        JeiRenderHelper.drawCompletionIndicator(g, font, recipe.getRequiredRecipeId(),
                DRIVE_X + 5, DRIVE_Y + SLOT_STRIDE + 1);
    }

    private void drawCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProcessingRecipe recipe, IFocusGroup focuses) {
        // Research-locked drive (mirrors DriveCraftingCategory): a drive pre-loaded with this
        // recipe_id so "show uses" on an imprinted drive finds this recipe.
        String requiredRecipeId = recipe.getRequiredRecipeId();
        List<ResearchDefinition> unlockingResearch = RecipeOutputResolver.findResearchForRecipe(requiredRecipeId);

        ItemStack preloadedDrive = unlockingResearch.isEmpty()
                ? new ItemStack(ModItems.METADATA_RECLAIMED.get())
                : RecipeOutputResolver.getDriveForTier(unlockingResearch.getFirst().getTier());
        NbtUtil.addRecipe(preloadedDrive, requiredRecipeId);

        builder.addSlot(RecipeIngredientRole.INPUT, DRIVE_X, DRIVE_Y)
                .setStandardSlotBackground()
                .addItemStack(preloadedDrive)
                .addRichTooltipCallback((slotView, tooltip) ->
                        JeiRenderHelper.addDriveTooltip(tooltip, requiredRecipeId, unlockingResearch));

        // Item inputs (4×4).
        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < INPUT_COLS * INPUT_ROWS && i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            int x = INPUT_GRID_X + (i % INPUT_COLS) * SLOT_STRIDE;
            int y = INPUT_GRID_Y + (i / INPUT_COLS) * SLOT_STRIDE;
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .setStandardSlotBackground()
                    .addIngredients(ingredient);
        }

        // Fluid inputs (up to 2), rendered against the real tank capacity so the bar fills sensibly.
        List<ProcessingFluidStack> fluidInputs = recipe.getFluidInputs();
        if (!fluidInputs.isEmpty()) {
            addFluidSlot(builder, RecipeIngredientRole.INPUT, FLUID_IN1_X, FLUID_Y, fluidInputs.get(0).toFluidStack());
        }
        if (fluidInputs.size() > 1) {
            addFluidSlot(builder, RecipeIngredientRole.INPUT, FLUID_IN2_X, FLUID_Y, fluidInputs.get(1).toFluidStack());
        }

        // Item outputs (2×4).
        List<ItemStack> results = recipe.getResults();
        for (int i = 0; i < OUTPUT_COLS * OUTPUT_ROWS && i < results.size(); i++) {
            ItemStack result = results.get(i);
            if (result.isEmpty()) {
                continue;
            }
            int x = OUTPUT_GRID_X + (i % OUTPUT_COLS) * SLOT_STRIDE;
            int y = OUTPUT_GRID_Y + (i / OUTPUT_COLS) * SLOT_STRIDE;
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y)
                    .setStandardSlotBackground()
                    .addItemStack(result);
        }

        // Fluid output.
        if (recipe.hasFluidOutput()) {
            addFluidSlot(builder, RecipeIngredientRole.OUTPUT, FLUID_OUT_X, FLUID_Y, recipe.getFluidOutput().toFluidStack());
        }
    }

    /**
     * Add a fluid slot rendered against the station's tank capacity (so the fill level is
     * meaningful) with the capacity shown in JEI's own fluid tooltip. Skips empty fluids.
     */
    private void addFluidSlot(IRecipeLayoutBuilder builder, RecipeIngredientRole role, int x, int y, FluidStack fluid) {
        if (fluid.isEmpty()) {
            return;
        }
        builder.addSlot(role, x, y)
                .setStandardSlotBackground()
                .setFluidRenderer(ProcessingStationBlockEntity.TANK_CAPACITY, true, FLUID_SIZE, FLUID_SIZE)
                .addFluidStack(fluid.getFluid(), fluid.getAmount());
    }
}
