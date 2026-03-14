package com.researchcube.compat.jade;

import com.researchcube.block.ProcessingStationBlock;
import com.researchcube.block.ProcessingStationBlockEntity;
import com.researchcube.block.ResearchTableBlock;
import com.researchcube.block.ResearchTableBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade (WAILA) plugin for ResearchCube.
 * Registers block component and server data providers for the Research Table and Processing Station.
 * Discovered by Jade's annotation-based loading; never referenced from main mod code.
 */
@WailaPlugin
public class ResearchCubeJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ResearchStationProvider.INSTANCE, ResearchTableBlockEntity.class);
        registration.registerBlockDataProvider(ProcessingStationProvider.INSTANCE, ProcessingStationBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ResearchStationProvider.INSTANCE, ResearchTableBlock.class);
        registration.registerBlockComponent(ProcessingStationProvider.INSTANCE, ProcessingStationBlock.class);
    }
}
