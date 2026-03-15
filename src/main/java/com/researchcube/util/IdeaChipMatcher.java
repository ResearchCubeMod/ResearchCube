package com.researchcube.util;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Partial-match utility for idea chip validation.
 * Compares a required ItemStack against a candidate using partial matching:
 * the item type must match, and every component declared on the required stack
 * must have the same value on the candidate. Components present only on the
 * candidate but not on the required stack are ignored.
 */
public final class IdeaChipMatcher {

    private IdeaChipMatcher() {}

    /**
     * Returns true if the candidate matches the required idea chip.
     * <ul>
     *   <li>Item registry name must be equal.</li>
     *   <li>For each DataComponentType present in required's components,
     *       the candidate must have the same value.</li>
     *   <li>Components present only in the candidate are ignored (partial match).</li>
     * </ul>
     */
    public static boolean matches(ItemStack required, ItemStack candidate) {
        if (required.isEmpty() || candidate.isEmpty()) return false;
        if (!ItemStack.isSameItem(required, candidate)) return false;

        DataComponentMap candidateComponents = candidate.getComponents();
        for (TypedDataComponent<?> typed : required.getComponents()) {
            if (!componentEquals(typed, candidateComponents)) {
                return false;
            }
        }
        return true;
    }

    private static <T> boolean componentEquals(TypedDataComponent<T> typed,
                                                DataComponentMap candidate) {
        T requiredValue = typed.value();
        T candidateValue = candidate.get(typed.type());
        if (requiredValue == null) return true;
        return requiredValue.equals(candidateValue);
    }
}
