package com.researchcube.client.renderer;

import com.researchcube.ResearchCubeMod;
import com.researchcube.block.ResearchTableBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model definition for the Research Station block entity.
 * References the existing geo, animation, and texture assets.
 */
public class ResearchStationModel extends GeoModel<ResearchTableBlockEntity> {

    private static final ResourceLocation GEO =
            ResearchCubeMod.rl("geo/research_station.geo.json");
    private static final ResourceLocation TEXTURE =
            ResearchCubeMod.rl("textures/research_station/research_station.png");
    private static final ResourceLocation ANIMATION =
            ResearchCubeMod.rl("animations/research_station.animation.json");

    @Override
    public ResourceLocation getModelResource(ResearchTableBlockEntity animatable) {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(ResearchTableBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ResearchTableBlockEntity animatable) {
        return ANIMATION;
    }
}
