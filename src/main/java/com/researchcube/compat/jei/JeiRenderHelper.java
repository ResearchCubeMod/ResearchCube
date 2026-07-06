package com.researchcube.compat.jei;

import com.researchcube.client.ClientResearchData;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.util.RecipeOutputResolver;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shared rendering/tooltip helpers for the ResearchCube JEI categories.
 *
 * <p>Both {@link DriveCraftingCategory} and {@link ProcessingCategory} render a research-locked
 * drive slot and a completion indicator in exactly the same way; this class holds that logic in
 * one place. All strings resolve through translatable keys ({@code jei.researchcube.*}).
 *
 * <p>Client-only: it reads live research-completion state via {@link ClientResearchData}, so it
 * must only be touched from JEI's client-side draw/setRecipe callbacks (as it always is).
 */
final class JeiRenderHelper {

    private static final int COLOR_DONE = 0xFF22CC55;
    private static final int COLOR_TODO = 0xFFCC3333;

    private JeiRenderHelper() {}

    /**
     * Draw a ✔/✘ indicator for the research that unlocks {@code requiredRecipeId}: green when at
     * least one unlocking research is complete, red otherwise. Draws nothing when no research
     * references the recipe (e.g. an unbound or datapack-only recipe).
     */
    static void drawCompletionIndicator(GuiGraphics g, Font font, String requiredRecipeId, int x, int y) {
        List<ResearchDefinition> unlocking = RecipeOutputResolver.findResearchForRecipe(requiredRecipeId);
        if (unlocking.isEmpty()) {
            return;
        }
        boolean completed = isAnyCompleted(unlocking);
        g.drawString(font, completed ? "✔" : "✘", x, y, completed ? COLOR_DONE : COLOR_TODO, false);
    }

    /**
     * Append the drive-requirement tooltip: the required recipe id followed by one line per
     * unlocking research, each prefixed with its completion status.
     */
    static void addDriveTooltip(ITooltipBuilder tooltip, String requiredRecipeId, List<ResearchDefinition> unlockingResearch) {
        tooltip.add(Component.translatable("jei.researchcube.tooltip.requires_recipe",
                        Component.literal(requiredRecipeId).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));

        for (ResearchDefinition def : unlockingResearch) {
            boolean completed = ClientResearchData.isCompleted(def.getId().toString());
            Component status = Component.literal(completed ? "✔ " : "✘ ")
                    .withStyle(completed ? ChatFormatting.GREEN : ChatFormatting.RED);
            Component line = Component.translatable("jei.researchcube.tooltip.unlocked_by",
                            Component.literal(def.getDisplayName()).withStyle(ChatFormatting.WHITE),
                            def.getTier().getDisplayName())
                    .withStyle(ChatFormatting.GREEN);
            tooltip.add(Component.empty().append(status).append(line));
        }
    }

    private static boolean isAnyCompleted(List<ResearchDefinition> unlocking) {
        for (ResearchDefinition def : unlocking) {
            if (ClientResearchData.isCompleted(def.getId().toString())) {
                return true;
            }
        }
        return false;
    }
}
