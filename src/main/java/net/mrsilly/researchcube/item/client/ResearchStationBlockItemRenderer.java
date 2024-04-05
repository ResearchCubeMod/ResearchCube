package net.mrsilly.researchcube.item.client;

import net.mrsilly.researchcube.item.custom.ResearchStationBlockItem;
import software.bernie.geckolib3.renderers.geo.GeoItemRenderer;

public class ResearchStationBlockItemRenderer extends GeoItemRenderer<ResearchStationBlockItem> {
    public ResearchStationBlockItemRenderer() {
        super(new ResearchStationBlockItemModel());
    }
}
