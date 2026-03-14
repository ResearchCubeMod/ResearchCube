package com.researchcube.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.researchcube.block.ResearchTableBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Set;

/**
 * GeckoLib block entity renderer for the Research Station.
 * <p>
 * The brain bone assembly (Brain, center, b1-b8) is always hidden.
 * When a cube is inserted, {@link CubeOverlayLayer} renders the
 * tier-appropriate Rubik's Cube model at the Brain bone's animated position.
 */
public class ResearchStationRenderer extends GeoBlockRenderer<ResearchTableBlockEntity> {

    /** Names of bones that belong to the brain/cube assembly (always hidden). */
    private static final Set<String> BRAIN_BONES = Set.of(
            "Brain", "center", "b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8"
    );

    public ResearchStationRenderer() {
        super(new ResearchStationModel());
        addRenderLayer(new CubeOverlayLayer(this));
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ResearchTableBlockEntity animatable,
                                  GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        if (BRAIN_BONES.contains(bone.getName())) {
            bone.setHidden(true);
        }

        super.renderRecursively(poseStack, animatable, bone, renderType,
                bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }
}
