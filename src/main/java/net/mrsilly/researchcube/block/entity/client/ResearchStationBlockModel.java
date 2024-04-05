package net.mrsilly.researchcube.block.entity.client;

import net.minecraft.resources.ResourceLocation;
import net.mrsilly.researchcube.ResearchCube;
import net.mrsilly.researchcube.block.entity.custom.ResearchStationBlockEntity;
import software.bernie.geckolib3.model.AnimatedGeoModel;

public class ResearchStationBlockModel extends AnimatedGeoModel<ResearchStationBlockEntity> {
    @Override
    public ResourceLocation getModelLocation(ResearchStationBlockEntity object) {
        return new ResourceLocation(ResearchCube.MOD_ID, "geo/research_station.geo.json");
    }

    @Override
    public ResourceLocation getTextureLocation(ResearchStationBlockEntity object) {
        return new ResourceLocation(ResearchCube.MOD_ID, "textures/research_station/research_station.png");
    }

    @Override
    public ResourceLocation getAnimationFileLocation(ResearchStationBlockEntity animation) {
        return new ResourceLocation(ResearchCube.MOD_ID, "animations/research_station.animation.json");
    }
}
