package com.researchcube.client;

import java.util.Set;

/**
 * Client-side cache for the player's completed research and active research HUD data.
 * Populated from network packets and menu buffers; read by JEI/EMI tooltips and the HUD overlay.
 *
 * All access is on the Minecraft render/main thread — no synchronization needed.
 */
public final class ClientResearchData {

    private static Set<String> completedResearch = Set.of();

    // Active research HUD state
    private static String activeResearchName = null;
    private static float activeProgress = 0f;
    private static int remainingSeconds = 0;
    private static int activeTierColor = 0xFFFFFF;

    private ClientResearchData() {}

    // ── Completed Research ──

    public static void updateCompleted(Set<String> completed) {
        completedResearch = Set.copyOf(completed);
    }

    public static boolean isCompleted(String researchId) {
        return completedResearch.contains(researchId);
    }

    public static Set<String> getCompleted() {
        return completedResearch;
    }

    // ── Active Research HUD ──

    public static void updateActiveResearch(String name, float progress, int remaining, int tierColor) {
        activeResearchName = name;
        activeProgress = progress;
        remainingSeconds = remaining;
        activeTierColor = tierColor;
    }

    public static void clearActiveResearch() {
        activeResearchName = null;
        activeProgress = 0f;
        remainingSeconds = 0;
    }

    public static String getActiveResearchName() {
        return activeResearchName;
    }

    public static float getActiveProgress() {
        return activeProgress;
    }

    public static int getRemainingSeconds() {
        return remainingSeconds;
    }

    public static int getActiveTierColor() {
        return activeTierColor;
    }

    public static boolean hasActiveResearch() {
        return activeResearchName != null;
    }
}
