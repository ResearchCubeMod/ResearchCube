package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge common config for ResearchCube.
 * Settings apply to both client and server.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Research Settings ──

    private static final ModConfigSpec.DoubleValue RESEARCH_DURATION_MULTIPLIER = BUILDER
            .comment("Multiplier for research duration. Higher values = longer research times.")
            .comment("Example: 2.0 means research takes twice as long.")
            .defineInRange("researchDurationMultiplier", 1.0, 0.1, 10.0);

    private static final ModConfigSpec.DoubleValue RESEARCH_COST_MULTIPLIER = BUILDER
            .comment("Multiplier for item cost amounts in research requirements.")
            .comment("Example: 2.0 means costs are doubled (4 iron becomes 8).")
            .comment("Note: This only affects costs >= 2, with a minimum of 1 after scaling.")
            .defineInRange("researchCostMultiplier", 1.0, 0.1, 10.0);

    private static final ModConfigSpec.BooleanValue ENABLE_TEAM_SHARING = BUILDER
            .comment("Whether players in the same scoreboard team share research progress.")
            .comment("If false, all players have isolated research pools.")
            .define("enableTeamSharing", true);

    // ── Processing Settings ──

    private static final ModConfigSpec.DoubleValue PROCESSING_DURATION_MULTIPLIER = BUILDER
            .comment("Multiplier for processing recipe duration. Higher values = longer processing times.")
            .defineInRange("processingDurationMultiplier", 1.0, 0.1, 10.0);

    // ── HUD Settings ──

    private static final ModConfigSpec.BooleanValue SHOW_RESEARCH_HUD = BUILDER
            .comment("Whether to show the research progress HUD overlay when research is active.")
            .define("showResearchHUD", true);

    private static final ModConfigSpec.IntValue RESEARCH_HUD_CORNER = BUILDER
            .comment("HUD corner position: 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right.")
            .defineInRange("researchHUDCorner", 1, 0, 3);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Cached values (updated on config load/reload) ──

    private static double researchDurationMultiplier = 1.0;
    private static double researchCostMultiplier = 1.0;
    private static boolean enableTeamSharing = true;
    private static double processingDurationMultiplier = 1.0;
    private static boolean showResearchHUD = true;
    private static int researchHUDCorner = 1;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            researchDurationMultiplier = RESEARCH_DURATION_MULTIPLIER.get();
            researchCostMultiplier = RESEARCH_COST_MULTIPLIER.get();
            enableTeamSharing = ENABLE_TEAM_SHARING.get();
            processingDurationMultiplier = PROCESSING_DURATION_MULTIPLIER.get();
            showResearchHUD = SHOW_RESEARCH_HUD.get();
            researchHUDCorner = RESEARCH_HUD_CORNER.get();

            ResearchCubeMod.LOGGER.info("[ResearchCube] Config loaded: durationMult={}, costMult={}, teamSharing={}, processingMult={}",
                    researchDurationMultiplier, researchCostMultiplier, enableTeamSharing, processingDurationMultiplier);
        }
    }

    // ── Public Accessors ──

    /**
     * Get the research duration multiplier.
     * Applied to ResearchDefinition.getDuration() before comparing with elapsed ticks.
     */
    public static double getResearchDurationMultiplier() {
        return researchDurationMultiplier;
    }

    /**
     * Get the research cost multiplier.
     * Applied to ItemCost.count() before validation and consumption.
     */
    public static double getResearchCostMultiplier() {
        return researchCostMultiplier;
    }

    /**
     * Apply the cost multiplier to a given count, returning at least 1.
     */
    public static int applyResearchCostMultiplier(int count) {
        if (count <= 0) return 0;
        int scaled = (int) Math.round(count * researchCostMultiplier);
        return Math.max(1, scaled);
    }

    /**
     * Check if team sharing is enabled.
     */
    public static boolean isTeamSharingEnabled() {
        return enableTeamSharing;
    }

    /**
     * Get the processing duration multiplier.
     */
    public static double getProcessingDurationMultiplier() {
        return processingDurationMultiplier;
    }

    /** Whether the research progress HUD overlay should be shown. */
    public static boolean isShowResearchHUD() {
        return showResearchHUD;
    }

    /** HUD corner: 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right. */
    public static int getResearchHUDCorner() {
        return researchHUDCorner;
    }
}
