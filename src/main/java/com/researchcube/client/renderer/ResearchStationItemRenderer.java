package com.researchcube.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

/**
 * BEWLR that renders the Research Station's GeckoLib model as a block item.
 * Renders a static (non-animated) snapshot of the station model with brain bones hidden.
 */
public class ResearchStationItemRenderer extends BlockEntityWithoutLevelRenderer {

    private final ResearchStationModel model = new ResearchStationModel();

    public ResearchStationItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext,
                             PoseStack poseStack, MultiBufferSource bufferSource,
                             int packedLight, int packedOverlay) {
        ResourceLocation modelRes = model.getModelResource(null);
        BakedGeoModel bakedModel = model.getBakedModel(modelRes);

        ResourceLocation texture = model.getTextureResource(null);
        RenderType renderType = RenderType.entityTranslucentCull(texture);
        VertexConsumer buffer = bufferSource.getBuffer(renderType);

        // Hide brain bones (replaced by cube overlay in-world)
        bakedModel.getBone("Brain").ifPresent(brain -> brain.setHidden(true));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));

        for (GeoBone bone : bakedModel.topLevelBones()) {
            renderBoneRecursive(poseStack, bone, buffer, packedLight, packedOverlay, 0xFFFFFFFF);
        }

        poseStack.popPose();
    }

    private void renderBoneRecursive(PoseStack poseStack, GeoBone bone,
                                     VertexConsumer buffer,
                                     int packedLight, int packedOverlay, int colour) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);

        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                poseStack.pushPose();
                renderCube(poseStack, cube, buffer, packedLight, packedOverlay, colour);
                poseStack.popPose();
            }
        }

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                renderBoneRecursive(poseStack, child, buffer, packedLight, packedOverlay, colour);
            }
        }

        poseStack.popPose();
    }

    private void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer,
                            int packedLight, int packedOverlay, int colour) {
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);

        Matrix3f normalMatrix = poseStack.last().normal();
        Matrix4f poseMatrix = new Matrix4f(poseStack.last().pose());

        for (GeoQuad quad : cube.quads()) {
            if (quad == null) continue;

            Vector3f normal = normalMatrix.transform(new Vector3f(quad.normal()));
            RenderUtil.fixInvertedFlatCube(cube, normal);

            for (GeoVertex vertex : quad.vertices()) {
                Vector3f pos = vertex.position();
                Vector4f transformed = poseMatrix.transform(new Vector4f(pos.x(), pos.y(), pos.z(), 1.0f));
                buffer.addVertex(transformed.x(), transformed.y(), transformed.z(), colour,
                        vertex.texU(), vertex.texV(), packedOverlay, packedLight,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }
}
