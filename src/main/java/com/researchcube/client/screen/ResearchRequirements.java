package com.researchcube.client.screen;

import com.researchcube.block.ResearchTableBlockEntity;
import com.researchcube.menu.ResearchTableMenu;
import com.researchcube.registry.ModFluids;
import com.researchcube.research.FluidCost;
import com.researchcube.research.ItemCost;
import com.researchcube.research.ResearchDefinition;
import com.researchcube.util.IdeaChipMatcher;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side checks for whether a research can be started, based on the menu's
 * synced slots and data. Shared by {@link ResearchTableScreen} (list/progress),
 * its embedded tree view, and node coloring in {@link ResearchGraphView}.
 *
 * <p>These mirror the authoritative server-side validation; the server always has
 * the final say when a {@code StartResearchPacket} is received.
 */
public final class ResearchRequirements {

    private ResearchRequirements() {}

    /** True if all prerequisites, the idea chip, item costs, and fluid cost are satisfied. */
    public static boolean canStart(ResearchTableMenu menu, ResearchDefinition def) {
        return prereqMet(menu, def)
                && ideaChipSatisfied(menu, def)
                && hasItems(menu, def)
                && hasFluid(menu, def);
    }

    /** True if the research's prerequisites are met by the player's completed research. */
    public static boolean prereqMet(ResearchTableMenu menu, ResearchDefinition def) {
        return def.getPrerequisites().isSatisfied(menu.getCompletedResearch());
    }

    /** True if the idea chip slot holds an item matching the research's required chip (or none required). */
    public static boolean ideaChipSatisfied(ResearchTableMenu menu, ResearchDefinition def) {
        if (def.getIdeaChip().isEmpty()) return true;
        ItemStack required = def.getIdeaChip().get();
        ItemStack candidate = menu.getSlot(ResearchTableBlockEntity.SLOT_IDEA_CHIP).getItem();
        return IdeaChipMatcher.matches(required, candidate);
    }

    /** True if the required item costs are present across the cost slots. */
    public static boolean hasItems(ResearchTableMenu menu, ResearchDefinition def) {
        if (def.getItemCosts().isEmpty()) return true;

        Map<Item, Integer> available = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            int slotIndex = ResearchTableBlockEntity.COST_SLOT_START + i;
            ItemStack stack = menu.getSlot(slotIndex).getItem();
            if (!stack.isEmpty()) {
                available.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        for (ItemCost cost : def.getItemCosts()) {
            if (available.getOrDefault(cost.getItem(), 0) < cost.count()) return false;
        }
        return true;
    }

    /** True if the tank holds enough of the correct fluid (or none required). */
    public static boolean hasFluid(ResearchTableMenu menu, ResearchDefinition def) {
        FluidCost fluidCost = def.getFluidCost();
        if (fluidCost == null) return true;

        int requiredType = ModFluids.getFluidIndex(fluidCost.getFluid());
        if (menu.getFluidType() != requiredType) return false;
        return menu.getFluidAmount() >= fluidCost.amount();
    }
}
