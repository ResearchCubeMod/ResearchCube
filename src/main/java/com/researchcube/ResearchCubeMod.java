package com.researchcube;

import com.researchcube.registry.*;
import com.researchcube.sideio.SideIOCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(ResearchCubeMod.MOD_ID)
public class ResearchCubeMod {

    public static final String MOD_ID = "researchcube";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public ResearchCubeMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("ResearchCube initializing...");

        // Register config
        modContainer.registerConfig(Type.COMMON, ModConfig.SPEC);

        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModCriterionTriggers.CRITERION_TRIGGERS.register(modEventBus);

        // Register fluid handler capability for the Research Station block entity
        modEventBus.addListener(this::registerCapabilities);

        LOGGER.info("ResearchCube registries queued.");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Both machines expose side-configurable item + fluid handlers through the
        // reusable sideio framework. Providers return null for closed sides so pipes
        // correctly see "nothing" there.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RESEARCH_STATION.get(),
                SideIOCapabilities::itemHandler
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.RESEARCH_STATION.get(),
                SideIOCapabilities::fluidHandler
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.PROCESSING_STATION.get(),
                SideIOCapabilities::itemHandler
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.PROCESSING_STATION.get(),
                SideIOCapabilities::fluidHandler
        );

        // The Auto Drive Crafting Table exposes only a side-configurable item handler
        // (grid inputs + output slot); it has no fluid channels.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.AUTO_DRIVE_CRAFTING_TABLE.get(),
                SideIOCapabilities::itemHandler
        );
    }
}
