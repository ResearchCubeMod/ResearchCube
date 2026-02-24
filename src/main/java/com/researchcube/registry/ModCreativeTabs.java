package com.researchcube.registry;

import com.researchcube.ResearchCubeMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ResearchCubeMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RESEARCH_CUBE_TAB =
            CREATIVE_TABS.register("researchcubetab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.researchcubetab"))
                    .icon(() -> new ItemStack(ModItems.METADATA_SELF_AWARE.get()))
                    .displayItems((params, output) -> {
                        // Drives
                        output.accept(ModItems.METADATA_IRRECOVERABLE.get());
                        output.accept(ModItems.METADATA_UNSTABLE.get());
                        output.accept(ModItems.METADATA_RECLAIMED.get());
                        output.accept(ModItems.METADATA_ENHANCED.get());
                        output.accept(ModItems.METADATA_ELABORATE.get());
                        output.accept(ModItems.METADATA_CYBERNETIC.get());
                        output.accept(ModItems.METADATA_SELF_AWARE.get());
                        // Cubes
                        output.accept(ModItems.CUBE_UNSTABLE.get());
                        output.accept(ModItems.CUBE_BASIC.get());
                        output.accept(ModItems.CUBE_ADVANCED.get());
                        output.accept(ModItems.CUBE_PRECISE.get());
                        output.accept(ModItems.CUBE_FLAWLESS.get());
                        output.accept(ModItems.CUBE_SELF_AWARE.get());
                        // Research Station
                        output.accept(ModItems.RESEARCH_STATION_ITEM.get());
                    })
                    .build());
}
