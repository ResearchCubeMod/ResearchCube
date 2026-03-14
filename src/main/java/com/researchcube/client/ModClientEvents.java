package com.researchcube.client;

import com.researchcube.ResearchCubeMod;
import com.researchcube.client.renderer.ResearchStationRenderer;
import com.researchcube.client.screen.DriveCraftingTableScreen;
import com.researchcube.client.screen.ProcessingStationScreen;
import com.researchcube.client.screen.ResearchTableScreen;
import com.researchcube.registry.ModBlockEntities;
import com.researchcube.registry.ModFluids;
import com.researchcube.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-side event handlers: registers screens, block entity renderers,
 * and fluid rendering extensions.
 */
@EventBusSubscriber(modid = ResearchCubeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RESEARCH_TABLE.get(), ResearchTableScreen::new);
        event.register(ModMenus.DRIVE_CRAFTING_TABLE.get(), DriveCraftingTableScreen::new);
        event.register(ModMenus.PROCESSING_STATION.get(), ProcessingStationScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.RESEARCH_STATION.get(),
                ctx -> new ResearchStationRenderer());
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        ResearchHudOverlay.register(event);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        // All four research fluids use vanilla water textures with a custom tint color.
        ResourceLocation waterStill = ResourceLocation.withDefaultNamespace("block/water_still");
        ResourceLocation waterFlow = ResourceLocation.withDefaultNamespace("block/water_flow");

        // Thinking Fluid — cyan
        event.registerFluidType(createFluidExtensions(waterStill, waterFlow, ModFluids.COLOR_THINKING),
                ModFluids.THINKING_FLUID_TYPE.get());

        // Pondering Fluid — purple
        event.registerFluidType(createFluidExtensions(waterStill, waterFlow, ModFluids.COLOR_PONDERING),
                ModFluids.PONDERING_FLUID_TYPE.get());

        // Reasoning Fluid — gold
        event.registerFluidType(createFluidExtensions(waterStill, waterFlow, ModFluids.COLOR_REASONING),
                ModFluids.REASONING_FLUID_TYPE.get());

        // Imagination Fluid — pink
        event.registerFluidType(createFluidExtensions(waterStill, waterFlow, ModFluids.COLOR_IMAGINATION),
                ModFluids.IMAGINATION_FLUID_TYPE.get());
    }

    private static IClientFluidTypeExtensions createFluidExtensions(
            ResourceLocation still, ResourceLocation flowing, int tintColor) {
        return new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowing;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }
        };
    }
}
