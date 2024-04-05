package net.mrsilly.researchcube.item.client;

import net.minecraft.resources.ResourceLocation;
import net.mrsilly.researchcube.ResearchCube;
import net.mrsilly.researchcube.block.entity.custom.ResearchStationBlockEntity;
import net.mrsilly.researchcube.item.custom.ResearchStationBlockItem;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class ResearchStationBlockItemModel extends AnimatedGeoModel<ResearchStationBlockItem> {
    @Override
    public ResourceLocation getModelLocation(ResearchStationBlockItem object) {
        return new ResourceLocation(ResearchCube.MOD_ID, "geo/research_station.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(ResearchStationBlockItem object) {
        return new ResourceLocation(ResearchCube.MOD_ID, "textures/research_station/research_station.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(ResearchStationBlockItem animation) {
        return new ResourceLocation(ResearchCube.MOD_ID, "animations/research_station.animation.json");
    }
}
