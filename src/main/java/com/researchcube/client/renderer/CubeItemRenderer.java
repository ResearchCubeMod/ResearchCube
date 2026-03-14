package com.researchcube.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.researchcube.item.CubeItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * GeoItemRenderer for CubeItem. Applies the tier's RGB color as a vertex tint
 * with a subtle pulsing brightness effect and 80% alpha translucency.
 * Uses entityTranslucentCull render type for the alpha to take effect.
 */
public class CubeItemRenderer extends GeoItemRenderer<CubeItem> {

    public CubeItemRenderer() {
        super(new CubeItemModel());
    }

    /**
     * Set up internal state for rendering outside the normal item pipeline
     * (e.g. inside the Research Station). Must be called before actuallyRender().
     */
    public void prepareForStationRender(CubeItem cubeItem, ItemStack stack) {
        this.animatable = cubeItem;
        this.currentItemStack = stack;
    }

    @Override
    public RenderType getRenderType(CubeItem animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentCull(texture);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, CubeItem animatable,
                                  GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        int tierRgb = animatable.getTier().getColor();

        // Pulsing brightness: sine wave oscillates between 0.88 and 1.0
        double time = System.currentTimeMillis() / 800.0;
        float pulse = (float) (Math.sin(time) * 0.06 + 0.94);

        int r = Math.min(255, (int) (((tierRgb >> 16) & 0xFF) * pulse));
        int g = Math.min(255, (int) (((tierRgb >> 8) & 0xFF) * pulse));
        int b = Math.min(255, (int) ((tierRgb & 0xFF) * pulse));

        // 80% alpha (0xCC = 204)
        colour = 0xCC000000 | (r << 16) | (g << 8) | b;

        super.renderRecursively(poseStack, animatable, bone, renderType,
                bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }
}
