package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import com.researchcube.menu.DriveCraftingTableMenu;
import com.researchcube.menu.ProcessingStationMenu;
import com.researchcube.menu.ResearchTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ResearchCubeMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchTableMenu>> RESEARCH_TABLE =
            MENUS.register("research_table", () ->
                    IMenuTypeExtension.create(ResearchTableMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<DriveCraftingTableMenu>> DRIVE_CRAFTING_TABLE =
            MENUS.register("drive_crafting_table", () ->
                    IMenuTypeExtension.create(DriveCraftingTableMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ProcessingStationMenu>> PROCESSING_STATION =
            MENUS.register("processing_station", () ->
                    IMenuTypeExtension.create(ProcessingStationMenu::new));
}
