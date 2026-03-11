package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers all custom fluids for the ResearchCube mod.
 *
 * Four tiers of research fluid:
 *   1. Thinking Fluid  (cyan)   — UNSTABLE/BASIC research
 *   2. Pondering Fluid  (purple) — ADVANCED research
 *   3. Reasoning Fluid  (gold)   — PRECISE/FLAWLESS research
 *   4. Imagination Fluid (pink)  — SELF_AWARE research
 *
 * Each fluid has a FluidType, Source, and Flowing variant.
 * No LiquidBlock is registered — these fluids cannot be placed in the world.
 */
public class ModFluids {

    // ── Deferred Registers ──

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, ResearchCubeMod.MOD_ID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, ResearchCubeMod.MOD_ID);

    // ══════════════════════════════════════════════════════════════
    //  Thinking Fluid (Tier 1 — cyan)
    // ══════════════════════════════════════════════════════════════

    public static final DeferredHolder<FluidType, FluidType> THINKING_FLUID_TYPE =
            FLUID_TYPES.register("thinking_fluid", () -> new FluidType(
                    FluidType.Properties.create()
                            .density(1050)
                            .viscosity(1200)
                            .temperature(300)
                            .descriptionId("fluid_type.researchcube.thinking_fluid")));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> THINKING_FLUID =
            FLUIDS.register("thinking_fluid",
                    () -> new BaseFlowingFluid.Source(thinkingProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> THINKING_FLUID_FLOWING =
            FLUIDS.register("flowing_thinking_fluid",
                    () -> new BaseFlowingFluid.Flowing(thinkingProperties()));

    private static BaseFlowingFluid.Properties thinkingProperties() {
        return new BaseFlowingFluid.Properties(THINKING_FLUID_TYPE, THINKING_FLUID, THINKING_FLUID_FLOWING)
                .bucket(ModItems.THINKING_FLUID_BUCKET);
    }

    // ══════════════════════════════════════════════════════════════
    //  Pondering Fluid (Tier 2 — purple)
    // ══════════════════════════════════════════════════════════════

    public static final DeferredHolder<FluidType, FluidType> PONDERING_FLUID_TYPE =
            FLUID_TYPES.register("pondering_fluid", () -> new FluidType(
                    FluidType.Properties.create()
                            .density(1100)
                            .viscosity(1400)
                            .temperature(310)
                            .descriptionId("fluid_type.researchcube.pondering_fluid")));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PONDERING_FLUID =
            FLUIDS.register("pondering_fluid",
                    () -> new BaseFlowingFluid.Source(ponderingProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> PONDERING_FLUID_FLOWING =
            FLUIDS.register("flowing_pondering_fluid",
                    () -> new BaseFlowingFluid.Flowing(ponderingProperties()));

    private static BaseFlowingFluid.Properties ponderingProperties() {
        return new BaseFlowingFluid.Properties(PONDERING_FLUID_TYPE, PONDERING_FLUID, PONDERING_FLUID_FLOWING)
                .bucket(ModItems.PONDERING_FLUID_BUCKET);
    }

    // ══════════════════════════════════════════════════════════════
    //  Reasoning Fluid (Tier 3 — gold)
    // ══════════════════════════════════════════════════════════════

    public static final DeferredHolder<FluidType, FluidType> REASONING_FLUID_TYPE =
            FLUID_TYPES.register("reasoning_fluid", () -> new FluidType(
                    FluidType.Properties.create()
                            .density(1200)
                            .viscosity(1600)
                            .temperature(320)
                            .descriptionId("fluid_type.researchcube.reasoning_fluid")));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> REASONING_FLUID =
            FLUIDS.register("reasoning_fluid",
                    () -> new BaseFlowingFluid.Source(reasoningProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> REASONING_FLUID_FLOWING =
            FLUIDS.register("flowing_reasoning_fluid",
                    () -> new BaseFlowingFluid.Flowing(reasoningProperties()));

    private static BaseFlowingFluid.Properties reasoningProperties() {
        return new BaseFlowingFluid.Properties(REASONING_FLUID_TYPE, REASONING_FLUID, REASONING_FLUID_FLOWING)
                .bucket(ModItems.REASONING_FLUID_BUCKET);
    }

    // ══════════════════════════════════════════════════════════════
    //  Imagination Fluid (Tier 4 — pink)
    // ══════════════════════════════════════════════════════════════

    public static final DeferredHolder<FluidType, FluidType> IMAGINATION_FLUID_TYPE =
            FLUID_TYPES.register("imagination_fluid", () -> new FluidType(
                    FluidType.Properties.create()
                            .density(1300)
                            .viscosity(1800)
                            .temperature(340)
                            .descriptionId("fluid_type.researchcube.imagination_fluid")));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> IMAGINATION_FLUID =
            FLUIDS.register("imagination_fluid",
                    () -> new BaseFlowingFluid.Source(imaginationProperties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> IMAGINATION_FLUID_FLOWING =
            FLUIDS.register("flowing_imagination_fluid",
                    () -> new BaseFlowingFluid.Flowing(imaginationProperties()));

    private static BaseFlowingFluid.Properties imaginationProperties() {
        return new BaseFlowingFluid.Properties(IMAGINATION_FLUID_TYPE, IMAGINATION_FLUID, IMAGINATION_FLUID_FLOWING)
                .bucket(ModItems.IMAGINATION_FLUID_BUCKET);
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers for fluid ↔ index mapping (used by ContainerData sync)
    // ══════════════════════════════════════════════════════════════

    /** Fluid colors (ARGB) for rendering the fluid gauge in the UI. */
    public static final int COLOR_THINKING    = 0xFF55CCFF; // cyan
    public static final int COLOR_PONDERING   = 0xFFAA55FF; // purple
    public static final int COLOR_REASONING   = 0xFFFFAA00; // gold
    public static final int COLOR_IMAGINATION = 0xFFFF5599; // pink

    /**
     * Map a Fluid to an integer index for ContainerData sync.
     * 0 = empty/unknown, 1 = thinking, 2 = pondering, 3 = reasoning, 4 = imagination.
     */
    public static int getFluidIndex(Fluid fluid) {
        if (fluid == THINKING_FLUID.get()) return 1;
        if (fluid == PONDERING_FLUID.get()) return 2;
        if (fluid == REASONING_FLUID.get()) return 3;
        if (fluid == IMAGINATION_FLUID.get()) return 4;
        return 0;
    }

    /**
     * Map an integer index back to a Fluid.
     */
    public static Fluid getFluidByIndex(int index) {
        return switch (index) {
            case 1 -> THINKING_FLUID.get();
            case 2 -> PONDERING_FLUID.get();
            case 3 -> REASONING_FLUID.get();
            case 4 -> IMAGINATION_FLUID.get();
            default -> Fluids.EMPTY;
        };
    }

    /**
     * Get the ARGB tint color for a fluid index (for UI rendering).
     */
    public static int getFluidColor(int fluidIndex) {
        return switch (fluidIndex) {
            case 1 -> COLOR_THINKING;
            case 2 -> COLOR_PONDERING;
            case 3 -> COLOR_REASONING;
            case 4 -> COLOR_IMAGINATION;
            default -> 0xFF444444;
        };
    }

    /**
     * Get the display name of a fluid by index.
     */
    public static String getFluidName(int fluidIndex) {
        return switch (fluidIndex) {
            case 1 -> "Thinking Fluid";
            case 2 -> "Pondering Fluid";
            case 3 -> "Reasoning Fluid";
            case 4 -> "Imagination Fluid";
            default -> "Empty";
        };
    }
}
