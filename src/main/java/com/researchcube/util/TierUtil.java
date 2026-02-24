package com.researchcube.util;

import com.researchcube.research.ResearchTier;

/**
 * Validation utility for tier-based research rules.
 */
public final class TierUtil {

    private TierUtil() {}

    /**
     * Validates whether a research operation is allowed based on tier rules:
     *   - cube.tier >= research.tier
     *   - drive.tier == research.tier
     *
     * @param cubeTier     the tier of the cube in the Research Table
     * @param driveTier    the tier of the drive in the Research Table
     * @param researchTier the tier required by the research definition
     * @return true if the research can proceed
     */
    public static boolean canResearch(ResearchTier cubeTier, ResearchTier driveTier, ResearchTier researchTier) {
        if (cubeTier == null || driveTier == null || researchTier == null) {
            return false;
        }
        // Broken drives can never be used
        if (!driveTier.isFunctional()) {
            return false;
        }
        // Cube must be at least the research tier
        if (!cubeTier.isAtLeast(researchTier)) {
            return false;
        }
        // Drive must exactly match the research tier
        return driveTier == researchTier;
    }
}
