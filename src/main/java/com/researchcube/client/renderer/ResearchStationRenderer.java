package com.researchcube.client.renderer;

import com.researchcube.block.ResearchTableBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib block entity renderer for the Research Station.
 * Renders the animated model with the rotating brain.
 */
public class ResearchStationRenderer extends GeoBlockRenderer<ResearchTableBlockEntity> {

    public ResearchStationRenderer() {
        super(new ResearchStationModel());
    }
}
